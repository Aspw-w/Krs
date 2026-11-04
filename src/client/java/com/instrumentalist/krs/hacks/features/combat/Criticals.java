package com.instrumentalist.krs.hacks.features.combat;

import com.instrumentalist.krs.events.features.AttackEvent;
import com.instrumentalist.krs.events.features.MotionEvent;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.events.features.SendPacketEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.movement.fly.FlyModule;
import com.instrumentalist.krs.utils.ChatUtil;
import com.instrumentalist.krs.utils.entity.CombatTargets;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.ListValue;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import com.instrumentalist.krs.utils.input.Keys;

public class Criticals extends Module {
    private static final float BASE_JUMP_POWER = 0.42F;
    private static final double JUMP_GRAVITY = 0.08D;

    @Setting
    private final ListValue mode = new ListValue("Mode", new String[]{"Packet", "Less Packet", "Cubecraft", "Jump"}, "Packet");

    @Setting
    private final BooleanValue cooldownCheck = new BooleanValue("Cooldown Check", false, () -> mode.get().equalsIgnoreCase("packet") || mode.get().equalsIgnoreCase("less packet"));

    @Setting
    private final FloatValue jumpHeight = new FloatValue("Jump Height", BASE_JUMP_POWER, 0.1F, BASE_JUMP_POWER, () -> isJumpMode());

    @Setting
    private final FloatValue jumpRange = new FloatValue("Jump Range", 4.0F, 1.0F, 6.0F, "m", () -> isJumpMode());

    @Setting
    private final BooleanValue smartTiming = new BooleanValue("Smart Timing", true, () -> isJumpMode());

    @Setting
    private final BooleanValue onlyWithKillAura = new BooleanValue("Only With Kill Aura", false, () -> isJumpMode());

    @Setting
    private final BooleanValue visibleOnly = new BooleanValue("Visible Only", true, () -> isJumpMode());

    private final double[] packetValues = new double[]{0.0625D, 0.0D, 0.05D, 0.0D};
    private final double[] lessPacketValues = new double[]{0.0625D, 0.0D};

    private double hoverOffset;
    private double serverFallDistance;
    private boolean holdingSpoofedFall;
    private double lastMotionY = Double.NaN;
    private long lastAttackMillis;

    public Criticals() {
        super("Criticals", ModuleCategory.Combat, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public String tag() {
        return mode.get();
    }

    @Override
    public void onDisable() {
        resetNoGround();
    }

    @Override
    public void onWorld(WorldEvent event) {
        resetNoGround();
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        if (!isJumpMode() || !isActive())
            return;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null)
            return;
        if (!allowsCriticalHit() || !player.onGround() || player.noJumpDelay > 0)
            return;
        if (smartTiming.get() && !hasGoodJumpTiming(player))
            return;

        LivingEntity target = findTarget(player);
        if (target == null || !canJump(player, target))
            return;

        performJump(player);
    }

    private void performJump(LocalPlayer player) {
        if (jumpHeight.get() >= BASE_JUMP_POWER - 1.0E-4F) {
            player.input.makeJump();
            return;
        }

        MovementUtil.tryJump(jumpHeight.get());
    }

    private float effectiveJumpPower(LocalPlayer player) {
        if (jumpHeight.get() >= BASE_JUMP_POWER - 1.0E-4F)
            return BASE_JUMP_POWER + player.getJumpBoostPower();

        return jumpHeight.get();
    }

    @Override
    public void onMotion(MotionEvent event) {
        if (event.post)
            return;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !mode.get().equalsIgnoreCase("cubecraft")) {
            resetNoGround();
            return;
        }

        boolean canSpoof = canSpoofGround(player);
        if (canSpoof && player.onGround() && isInCombat()
                && mc.level.noCollision(player, player.getBoundingBox().move(0.0D, 2.0, 0.0D))) {
            hoverOffset = hoverOffset < 1 + 0.1 ? 2.0 : hoverOffset - 0.1;
            event.y += hoverOffset;
            event.onGround = false;
            holdingSpoofedFall = true;
        } else {
            hoverOffset = 0.0D;
            if (canSpoof && holdingSpoofedFall && !event.onGround
                    && serverFallDistance + predictFallDistance(player, event.y) >= 2.5D)
                event.onGround = true;
        }

        trackServerFallDistance(player, event);
    }

    private void trackServerFallDistance(LocalPlayer player, MotionEvent event) {
        double deltaY = Double.isNaN(lastMotionY) ? 0.0D : event.y - lastMotionY;
        lastMotionY = event.y;
        if (event.onGround || player.isInWater() || player.isInLava() || player.onClimbable()) {
            serverFallDistance = 0.0D;
            holdingSpoofedFall = false;
        } else if (deltaY > 0.0D) {
            serverFallDistance = 0.0D;
        } else {
            serverFallDistance -= deltaY;
        }
    }

    private boolean canSpoofGround(LocalPlayer player) {
        return !player.isSpectator()
                && !player.getAbilities().flying
                && !player.isFallFlying()
                && !player.isPassenger()
                && !player.isInWater()
                && !player.isInLava()
                && !player.onClimbable()
                && !ModuleManager.getModuleState(FlyModule.class);
    }

    private boolean isInCombat() {
        return System.currentTimeMillis() - lastAttackMillis < 10000L
                || ModuleManager.getModuleState(KillAura.class) && KillAura.closestEntity != null;
    }

    private double predictFallDistance(LocalPlayer player, double y) {
        Vec3 start = new Vec3(player.getX(), y, player.getZ());
        BlockHitResult hit = mc.level.clip(new ClipContext(start, start.subtract(0.0D, 64.0D, 0.0D),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
        double drop = hit.getType() == HitResult.Type.MISS ? 64.0D : y - hit.getLocation().y;
        double upwardVelocity = Math.max(0.0D, player.getDeltaMovement().y);
        return Math.max(0.0D, drop) + upwardVelocity * upwardVelocity / (2.0D * 0.08D);
    }

    private boolean isJumpMode() {
        return mode.get().equalsIgnoreCase("jump");
    }

    private boolean isActive() {
        if (!tempEnabled || !isJumpMode())
            return false;

        return !onlyWithKillAura.get() || ModuleManager.getModuleState(KillAura.class);
    }

    private boolean allowsCriticalHit() {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null)
            return false;

        return !player.isSpectator()
                && !player.getAbilities().flying
                && !player.isFallFlying()
                && !player.isPassenger()
                && !player.isInWater()
                && !player.isInLava()
                && !player.onClimbable()
                && !player.isNoGravity()
                && !player.hasEffect(MobEffects.LEVITATION)
                && !player.hasEffect(MobEffects.BLINDNESS)
                && !player.hasEffect(MobEffects.SLOW_FALLING)
                && !ModuleManager.getModuleState(FlyModule.class);
    }

    private LivingEntity findTarget(LocalPlayer player) {
        if (mc.level == null)
            return null;

        double rangeSquared = (double) jumpRange.get() * jumpRange.get();
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!CombatTargets.isEnemy(entity, false))
                continue;

            double distance = entity.distanceToSqr(player);
            if (distance > rangeSquared || distance >= bestDistance)
                continue;
            if (visibleOnly.get() && !player.hasLineOfSight(entity))
                continue;

            best = (LivingEntity) entity;
            bestDistance = distance;
        }

        return best;
    }

    private boolean canJump(LocalPlayer player, Entity target) {
        if (target == null || mc.level == null || !player.onGround() || player.noJumpDelay > 0)
            return false;
        if (target.distanceToSqr(player) > (double) jumpRange.get() * jumpRange.get())
            return false;

        double checkHeight = Math.max(BASE_JUMP_POWER, effectiveJumpPower(player));
        return mc.level.noCollision(player, player.getBoundingBox().move(0.0D, checkHeight, 0.0D));
    }

    private boolean hasGoodJumpTiming(LocalPlayer player) {
        float airTicks = (float) (2.0D * effectiveJumpPower(player) / JUMP_GRAVITY);
        float remainingCooldown = player.getCurrentItemAttackStrengthDelay() - player.attackStrengthTicker;
        return remainingCooldown <= airTicks;
    }

    public static boolean shouldDelayAttack() {
        Criticals criticals = ModuleManager.getModule(Criticals.class);
        return criticals != null && criticals.shouldWaitForCrit();
    }

    private boolean shouldWaitForCrit() {
        LocalPlayer player = mc.player;
        if (!isActive() || player == null || mc.level == null || !allowsCriticalHit())
            return false;

        if (!player.onGround())
            return player.fallDistance <= 0.0F;

        LivingEntity target = findTarget(player);
        return target != null && canJump(player, target);
    }

    private void resetNoGround() {
        hoverOffset = 0.0D;
        serverFallDistance = 0.0D;
        holdingSpoofedFall = false;
        lastMotionY = Double.NaN;
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
        if (mc.player == null) return;

        Packet<?> packet = event.packet;

    }

    @Override
    public void onAttack(AttackEvent event) {
        var player = mc.player;
        if (player == null) return;
        lastAttackMillis = System.currentTimeMillis();
        if (mode.get().equalsIgnoreCase("cubecraft")) return;
        if (!player.onGround()
                || player.getMainHandItem().getItem() instanceof MaceItem
                || ModuleManager.getModuleState(FlyModule.class)
                || (ModuleManager.getModuleState(KillAura.class)
                && KillAura.closestEntity != null
                && (KillAura.randomTp.get()
                || (KillAura.tpReach.get()
                && mc.player.distanceTo(KillAura.closestEntity) >= KillAura.attackRange.get()))))
            return;
        if (cooldownCheck.get() && player.getAttackStrengthScale(0f) < 0.8) return;

        Vec3 pos = player.position();
        boolean horizontalCollision = player.horizontalCollision;

        switch (mode.get().toLowerCase()) {
            case "packet":
                for (double critY : packetValues) {
                    PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Pos(pos.x, pos.y + critY, pos.z, false, horizontalCollision));
                }
                break;

            case "less packet":
                for (double critY : lessPacketValues) {
                    PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Pos(pos.x, pos.y + critY, pos.z, false, horizontalCollision));
                }
                break;
        }
    }
}
