package com.instrumentalist.krs.hacks.features.dev;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.TickEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.features.combat.KillAura;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.entity.ItemUseHold;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.IntValue;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class AutoShield extends Module {
    private static final double ARROW_DRAG = 0.99D;
    private static final double MIN_SPEED_SQUARED = 0.04D;
    private static final int RELEASE_DELAY_TICKS = 4;

    @Setting
    private final FloatValue range = new FloatValue("Range", 24f, 6f, 64f, "m");

    @Setting
    private final IntValue predictTicks = new IntValue("Predict Ticks", 12, 6, 30, "ticks");

    @Setting
    private final BooleanValue rotate = new BooleanValue("Rotate", true);

    private InteractionHand raisedHand;
    private int lastThreatTick;
    private boolean rotating;

    public AutoShield() {
        super("Auto Shield", ModuleCategory.Dev, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onEnable() {
        raisedHand = null;
    }

    @Override
    public void onDisable() {
        lower();
    }

    @Override
    public void onWorld(WorldEvent event) {
        ItemUseHold.release(this);
        raisedHand = null;
        stopRotating();
    }

    @Override
    public void onTick(TickEvent event) {
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.gameMode == null || mc.gameMode.getPlayerMode() == GameType.SPECTATOR) {
            lower();
            return;
        }

        Entity threat = findThreat(player, level);
        if (threat != null) {
            lastThreatTick = player.tickCount;
            if (rotate.get() && !(ModuleManager.getModuleState(KillAura.class) && KillAura.closestEntity != null)) {
                Client.rotationManager.facePosition(threat.getX(), threat.getY(), threat.getZ(), 180f);
                rotating = true;
            }
            raise(player);
            return;
        }

        if (raisedHand != null && player.tickCount - lastThreatTick > RELEASE_DELAY_TICKS)
            lower();
        else if (raisedHand == null)
            stopRotating();
    }

    private void raise(LocalPlayer player) {
        if (raisedHand != null) {
            if (!player.isUsingItem())
                raisedHand = null;
            else
                return;
        }
        if (player.isUsingItem())
            return;

        InteractionHand hand = shieldHand(player);
        if (hand == null)
            return;

        ItemUseHold.hold(this);
        if (mc.gameMode.useItem(player, hand).consumesAction() || player.isUsingItem())
            raisedHand = hand;
        else
            ItemUseHold.release(this);
    }

    private void lower() {
        LocalPlayer player = mc.player;
        ItemUseHold.release(this);
        if (raisedHand != null && player != null && player.isUsingItem() && player.getUsedItemHand() == raisedHand && mc.gameMode != null
                && !mc.options.keyUse.isDown())
            ItemUseHold.forceRelease(() -> mc.gameMode.releaseUsingItem(player));
        raisedHand = null;
        stopRotating();
    }

    private void stopRotating() {
        if (rotating) {
            Client.rotationManager.stopRotation();
            rotating = false;
        }
    }

    private static InteractionHand shieldHand(LocalPlayer player) {
        if (blocks(player.getOffhandItem()))
            return InteractionHand.OFF_HAND;
        if (blocks(player.getMainHandItem()))
            return InteractionHand.MAIN_HAND;
        return null;
    }

    private static boolean blocks(ItemStack stack) {
        return stack.has(DataComponents.BLOCKS_ATTACKS) && !mc.player.getCooldowns().isOnCooldown(stack);
    }

    private Entity findThreat(LocalPlayer player, ClientLevel level) {
        if (shieldHand(player) == null)
            return null;

        double maxDistance = range.get() * range.get();
        Entity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof AbstractArrow) && !(entity instanceof AbstractHurtingProjectile))
                continue;
            if (((Projectile) entity).getOwner() == player)
                continue;

            double distance = entity.distanceToSqr(player);
            if (distance > maxDistance || distance >= nearestDistance)
                continue;
            if (willHit(player, entity)) {
                nearest = entity;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private boolean willHit(LocalPlayer player, Entity projectile) {
        Vec3 velocity = projectile.getDeltaMovement();
        if (velocity.lengthSqr() < MIN_SPEED_SQUARED)
            return false;

        boolean arrow = projectile instanceof AbstractArrow;
        double gravity = projectile.getGravity();
        double margin = projectile.getBbWidth() / 2.0D + 0.2D;
        AABB box = player.getBoundingBox().inflate(margin).expandTowards(player.getDeltaMovement().scale(predictTicks.get()));
        Vec3 position = projectile.position();
        for (int tick = 0; tick < predictTicks.get(); tick++) {
            Vec3 next = position.add(velocity);
            if (box.contains(position) || box.clip(position, next).isPresent())
                return true;

            position = next;
            if (arrow)
                velocity = velocity.scale(ARROW_DRAG);
            velocity = velocity.subtract(0.0D, gravity, 0.0D);
        }
        return false;
    }
}
