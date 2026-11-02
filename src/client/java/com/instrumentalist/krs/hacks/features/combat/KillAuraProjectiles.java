package com.instrumentalist.krs.hacks.features.combat;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.utils.entity.CombatTargets;
import com.instrumentalist.krs.utils.entity.ItemUseHold;
import com.instrumentalist.krs.utils.entity.PlayerUtil;
import com.instrumentalist.krs.utils.math.Ballistics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

final class KillAuraProjectiles {
    private static final int THROW_INTERVAL_TICKS = 10;
    private static final int SHOT_INTERVAL_TICKS = 12;
    private static final int ROD_INTERVAL_TICKS = 20;
    private static final int AIM_TIMEOUT_TICKS = 20;
    private static final int ROD_TIMEOUT_TICKS = 25;
    private static final int ROD_MIN_FLIGHT_TICKS = 4;
    private static final double THROWABLE_SPEED = 1.5D;
    private static final double THROWABLE_MAX_DISTANCE = 16.0D;
    private static final double ROD_MAX_DISTANCE = 9.0D;
    private static final double ROD_GRAVITY = 0.03D;
    private static final double ROD_DRAG = 0.92D;
    private static final double BOW_MIN_DISTANCE = 5.0D;
    private static final double MELEE_MARGIN = 0.5D;
    private static final float AIM_TOLERANCE = 1.5F;
    private static final int MIN_CHARGE_TICKS = 3;
    private static final int MAX_CHARGE_TICKS = 40;
    private static final int SEARCH_STEPS = 24;
    private static final int MAX_FLIGHT_TICKS = 80;

    private enum Kind {
        THROWABLE,
        BOW,
        ROD
    }

    private enum Phase {
        IDLE,
        AIMING,
        CHARGING,
        FISHING
    }

    private final Minecraft mc = Minecraft.getInstance();
    private Phase phase = Phase.IDLE;
    private Kind kind;
    private LivingEntity target;
    private int itemSlot = -1;
    private int restoreSlot = -1;
    private int swapTick = Integer.MIN_VALUE;
    private int phaseStartTick;
    private int alignedTicks;
    private int lastThrowTick = Integer.MIN_VALUE / 2;
    private int lastShotTick = Integer.MIN_VALUE / 2;
    private int lastRodTick = Integer.MIN_VALUE / 2;
    private boolean rotating;

    boolean tick(boolean throwables, boolean bow, boolean rod, double range, int chargeTicks, double meleeReach,
                 boolean onlyPlayers, boolean throughWalls, float rotationSpeed) {
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null || mc.level == null) {
            reset(false);
            return false;
        }

        return switch (phase) {
            case AIMING -> continueAiming(player, chargeTicks, range, meleeReach, onlyPlayers, rotationSpeed);
            case CHARGING -> continueCharging(player, chargeTicks, range, meleeReach, onlyPlayers, rotationSpeed);
            case FISHING -> continueFishing(player, range, meleeReach, onlyPlayers);
            case IDLE -> startIfReady(player, throwables, bow, rod, range, meleeReach, onlyPlayers, throughWalls, rotationSpeed);
        };
    }

    private boolean startIfReady(LocalPlayer player, boolean throwables, boolean bow, boolean rod, double range, double meleeReach,
                                 boolean onlyPlayers, boolean throughWalls, float rotationSpeed) {
        restoreSlot(false);
        if (restoreSlot != -1)
            return false;

        stopRotating();
        if (!throwables && !bow && !rod || player.isUsingItem() || mc.gui.screen() != null)
            return false;

        LivingEntity candidate = CombatTargets.nearest(range, onlyPlayers);
        if (candidate == null || hasMeleeTarget(player, meleeReach, onlyPlayers))
            return false;
        if (!throughWalls && !player.hasLineOfSight(candidate))
            return false;

        double distance = distanceTo(player, candidate);
        int now = player.tickCount;
        Inventory inventory = player.getInventory();
        if (rod && distance <= ROD_MAX_DISTANCE && now - lastRodTick >= ROD_INTERVAL_TICKS && player.fishing == null) {
            int slot = findSlot(inventory, stack -> stack.getItem() instanceof FishingRodItem);
            if (slot != -1)
                return beginAiming(player, Kind.ROD, candidate, slot, rotationSpeed);
        }

        if (throwables && candidate.hurtTime == 0 && distance <= THROWABLE_MAX_DISTANCE && now - lastThrowTick >= THROW_INTERVAL_TICKS) {
            int slot = findSlot(inventory, stack -> isThrowable(stack));
            if (slot != -1)
                return beginAiming(player, Kind.THROWABLE, candidate, slot, rotationSpeed);
        }

        if (bow && distance >= BOW_MIN_DISTANCE && now - lastShotTick >= SHOT_INTERVAL_TICKS) {
            int slot = findSlot(inventory, stack -> stack.getItem() instanceof BowItem
                    && (player.getAbilities().instabuild || !player.getProjectile(stack).isEmpty()));
            if (slot != -1)
                return beginAiming(player, Kind.BOW, candidate, slot, rotationSpeed);
        }
        return false;
    }

    private boolean beginAiming(LocalPlayer player, Kind kind, LivingEntity candidate, int slot, float rotationSpeed) {
        this.kind = kind;
        target = candidate;
        itemSlot = slot;
        phase = Phase.AIMING;
        phaseStartTick = player.tickCount;
        alignedTicks = 0;
        aim(player, rotationSpeed, 0);
        return true;
    }

    private boolean continueAiming(LocalPlayer player, int chargeTicks, double range, double meleeReach, boolean onlyPlayers, float rotationSpeed) {
        if (!isValidTarget(player, range, onlyPlayers) || hasMeleeTarget(player, meleeReach, onlyPlayers)
                || player.tickCount - phaseStartTick > AIM_TIMEOUT_TICKS || player.isUsingItem()
                || !isExpectedItem(player.getInventory().getItem(itemSlot))) {
            abort();
            return false;
        }

        float[] wanted = aim(player, rotationSpeed, kind == Kind.BOW ? MIN_CHARGE_TICKS : 0);
        boolean aligned = Math.abs(Mth.wrapDegrees(Client.rotationManager.getRotationYaw() - wanted[0])) <= AIM_TOLERANCE
                && Math.abs(Client.rotationManager.getRotationPitch() - wanted[1]) <= AIM_TOLERANCE;
        alignedTicks = aligned ? alignedTicks + 1 : 0;
        if (alignedTicks < 2)
            return true;

        selectSlot(player, itemSlot);
        switch (kind) {
            case THROWABLE -> {
                useItem(player);
                lastThrowTick = player.tickCount;
                finish(player);
            }
            case BOW -> {
                ItemUseHold.hold(this);
                useItem(player);
                if (!player.isUsingItem()) {
                    ItemUseHold.release(this);
                    finish(player);
                    return false;
                }
                phase = Phase.CHARGING;
                phaseStartTick = player.tickCount;
            }
            case ROD -> {
                useItem(player);
                lastRodTick = player.tickCount;
                phase = Phase.FISHING;
                phaseStartTick = player.tickCount;
            }
        }
        return true;
    }

    private boolean continueCharging(LocalPlayer player, int chargeTicks, double range, double meleeReach, boolean onlyPlayers, float rotationSpeed) {
        boolean stillCharging = player.isUsingItem()
                && player.getUseItem().getItem() instanceof BowItem
                && player.getInventory().getSelectedSlot() == itemSlot;
        if (!stillCharging || !isValidTarget(player, range, onlyPlayers)) {
            releaseBow(player, false);
            return false;
        }

        int used = player.getTicksUsingItem();
        aim(player, rotationSpeed, used + 1);
        boolean meleeThreat = hasMeleeTarget(player, meleeReach, onlyPlayers);
        if (used >= Math.max(MIN_CHARGE_TICKS, chargeTicks) || meleeThreat && used >= MIN_CHARGE_TICKS || used >= MAX_CHARGE_TICKS) {
            releaseBow(player, true);
            return false;
        }
        return true;
    }

    private void releaseBow(LocalPlayer player, boolean shot) {
        ItemUseHold.release(this);
        if (player.isUsingItem() && mc.gameMode != null)
            ItemUseHold.forceRelease(() -> mc.gameMode.releaseUsingItem(player));
        if (shot)
            lastShotTick = player.tickCount;
        finish(player);
    }

    private boolean continueFishing(LocalPlayer player, double range, double meleeReach, boolean onlyPlayers) {
        FishingHook hook = player.fishing;
        boolean holdingRod = player.getInventory().getSelectedSlot() == itemSlot
                && player.getInventory().getItem(itemSlot).getItem() instanceof FishingRodItem;
        int elapsed = player.tickCount - phaseStartTick;
        if (!holdingRod) {
            finish(player);
            return false;
        }

        boolean hooked = hook != null && target != null && hook.getHookedIn() == target;
        boolean timedOut = elapsed > ROD_TIMEOUT_TICKS;
        boolean landed = hook == null && elapsed > ROD_MIN_FLIGHT_TICKS;
        boolean meleeThreat = hasMeleeTarget(player, meleeReach, onlyPlayers) && elapsed > ROD_MIN_FLIGHT_TICKS;
        if (hooked || timedOut || landed || meleeThreat || !isValidTarget(player, range, onlyPlayers)) {
            if (hook != null)
                useItem(player);
            finish(player);
            return false;
        }
        return true;
    }

    private float[] aim(LocalPlayer player, float rotationSpeed, int bowTicks) {
        Vec3 eye = player.getEyePosition();
        float yaw;
        float pitch;
        if (kind == Kind.ROD) {
            Vec3 center = target.getBoundingBox().getCenter();
            Vec3 velocity = new Vec3(target.getX() - target.xo, 0.0D, target.getZ() - target.zo);
            Vec3 aimPoint = center;
            int flight = 0;
            pitch = 0f;
            for (int pass = 0; pass < 3; pass++) {
                aimPoint = center.add(velocity.scale(flight));
                float[] solution = solveRodPitch(Math.hypot(aimPoint.x - eye.x, aimPoint.z - eye.z), aimPoint.y - eye.y);
                pitch = solution[0];
                flight = (int) solution[1];
            }
            yaw = (float) (Mth.atan2(aimPoint.z - eye.z, aimPoint.x - eye.x) * Mth.RAD_TO_DEG) - 90f;
        } else {
            boolean throwable = kind == Kind.THROWABLE;
            double speed = throwable ? THROWABLE_SPEED : Math.max(0.3D, BowItem.getPowerForTime(Math.max(1, bowTicks)) * 3.0D);
            Vec3 origin = throwable ? eye.subtract(0.0D, 0.1D, 0.0D) : eye;
            Ballistics.Solution solution = Ballistics.aim(origin, target, speed,
                    throwable ? Ballistics.THROWABLE_GRAVITY : Ballistics.ARROW_GRAVITY, throwable, true);
            yaw = solution.yaw();
            pitch = solution.pitch();
        }

        Client.rotationManager.startRotationUnclampedPitch(yaw, pitch, rotationSpeed);
        rotating = true;
        return new float[]{yaw, pitch};
    }

    private static float[] solveRodPitch(double horizontal, double height) {
        double low = -75.0D;
        double high = 75.0D;
        float[] best = {0f, 0f};
        double bestError = Double.MAX_VALUE;
        for (int step = 0; step < SEARCH_STEPS; step++) {
            double elevation = (low + high) / 2.0D;
            double[] result = rodHeightAt(elevation, horizontal);
            double error = Math.abs(result[0] - height);
            if (error < bestError) {
                bestError = error;
                best = new float[]{(float) -elevation, (float) result[1]};
            }
            if (result[0] < height)
                low = elevation;
            else
                high = elevation;
        }
        return best;
    }

    private static double[] rodHeightAt(double elevationDegrees, double horizontal) {
        double slope = Mth.clamp(Math.tan(Math.toRadians(elevationDegrees)), -5.0D, 5.0D);
        double factor = 0.6D / Math.sqrt(1.0D + slope * slope) + 0.5D;
        double vx = factor;
        double vy = slope * factor;
        double x = 0.0D;
        double y = 0.0D;
        for (int tick = 1; tick <= MAX_FLIGHT_TICKS; tick++) {
            vy -= ROD_GRAVITY;
            double nextX = x + vx;
            double nextY = y + vy;
            if (nextX >= horizontal) {
                double fraction = vx <= 1.0E-6D ? 0.0D : (horizontal - x) / vx;
                return new double[]{y + vy * fraction, tick};
            }
            x = nextX;
            y = nextY;
            vx *= ROD_DRAG;
            vy *= ROD_DRAG;
            if (vx < 1.0E-3D)
                break;
        }
        return new double[]{-1.0E9D, MAX_FLIGHT_TICKS};
    }

    private void useItem(LocalPlayer player) {
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        if (Client.rotationManager.isRotating()) {
            player.setYRot(Client.rotationManager.getRotationYaw());
            player.setXRot(Client.rotationManager.getRotationPitch());
        }
        try {
            if (mc.gameMode.useItem(player, InteractionHand.MAIN_HAND).consumesAction())
                PlayerUtil.INSTANCE.swingHand(InteractionHand.MAIN_HAND);
        } finally {
            player.setYRot(yaw);
            player.setXRot(pitch);
        }
    }

    private boolean isValidTarget(LocalPlayer player, double range, boolean onlyPlayers) {
        return target != null && CombatTargets.isEnemy(target, onlyPlayers) && target.distanceToSqr(player) <= range * range;
    }

    private boolean isExpectedItem(ItemStack stack) {
        return switch (kind) {
            case THROWABLE -> isThrowable(stack);
            case BOW -> stack.getItem() instanceof BowItem;
            case ROD -> stack.getItem() instanceof FishingRodItem;
        };
    }

    private static boolean isThrowable(ItemStack stack) {
        return stack.is(Items.SNOWBALL) || stack.is(Items.EGG) || stack.is(Items.BLUE_EGG) || stack.is(Items.BROWN_EGG);
    }

    private boolean hasMeleeTarget(LocalPlayer player, double meleeReach, boolean onlyPlayers) {
        double reach = meleeReach + MELEE_MARGIN;
        LivingEntity nearest = CombatTargets.nearest(reach + 2.0D, onlyPlayers);
        return nearest != null && nearest.getBoundingBox().distanceToSqr(player.getEyePosition()) <= reach * reach;
    }

    private static double distanceTo(LocalPlayer player, LivingEntity entity) {
        return Math.sqrt(entity.getBoundingBox().distanceToSqr(player.getEyePosition()));
    }

    private void finish(LocalPlayer player) {
        phase = Phase.IDLE;
        target = null;
        swapTick = player.tickCount;
    }

    private void abort() {
        phase = Phase.IDLE;
        target = null;
        stopRotating();
    }

    private void selectSlot(LocalPlayer player, int slot) {
        Inventory inventory = player.getInventory();
        int selected = inventory.getSelectedSlot();
        if (slot != selected) {
            if (restoreSlot == -1) {
                restoreSlot = selected;
                PlayerUtil.INSTANCE.doSpoof(selected);
            }
            inventory.setSelectedSlot(slot);
        }
        swapTick = player.tickCount;
    }

    private void restoreSlot(boolean force) {
        LocalPlayer player = mc.player;
        if (restoreSlot == -1 || player == null) {
            restoreSlot = -1;
            return;
        }
        if (!force && player.tickCount <= swapTick)
            return;

        Inventory inventory = player.getInventory();
        if (inventory.getSelectedSlot() == itemSlot)
            inventory.setSelectedSlot(restoreSlot);
        restoreSlot = -1;
        itemSlot = -1;
        PlayerUtil.INSTANCE.stopSpoof();
    }

    private void stopRotating() {
        if (rotating) {
            Client.rotationManager.stopRotation();
            rotating = false;
        }
    }

    void reset(boolean restore) {
        LocalPlayer player = mc.player;
        if (phase == Phase.CHARGING && player != null)
            releaseBow(player, false);
        if (phase == Phase.FISHING && player != null && player.fishing != null && mc.gameMode != null)
            useItem(player);
        ItemUseHold.release(this);
        phase = Phase.IDLE;
        target = null;
        if (restore)
            restoreSlot(true);
        else
            restoreSlot = -1;
        stopRotating();
    }

    private static int findSlot(Inventory inventory, Predicate<ItemStack> matcher) {
        if (matcher.test(inventory.getItem(inventory.getSelectedSlot())))
            return inventory.getSelectedSlot();
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            if (matcher.test(inventory.getItem(slot)))
                return slot;
        }
        return -1;
    }
}
