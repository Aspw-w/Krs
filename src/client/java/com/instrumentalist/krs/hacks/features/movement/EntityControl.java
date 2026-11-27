package com.instrumentalist.krs.hacks.features.movement;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.TickEvent;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.math.Interpolation;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ItemSteerable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PlayerRideable;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import com.instrumentalist.krs.utils.input.Keys;

public class EntityControl extends Module {

    public EntityControl() {
        super("Entity Control", ModuleCategory.Movement, Keys.KEY_UNKNOWN, false, true);
    }

    @Setting
    private static final BooleanValue control = new BooleanValue("Control", true);

    @Setting
    private static final BooleanValue entityFly = new BooleanValue("Entity Fly", false);

    @Setting
    private static final BooleanValue stopBoatPaddles = new BooleanValue("Stop Boat Paddles", true, entityFly::get);

    @Setting
    private static final BooleanValue twoBjpBoost = new BooleanValue("2b2tJP Boost", false, entityFly::get);

    @Setting
    private static final FloatValue hSpeed = new FloatValue("Fly Horizontal Speed", 2f, 0.1f, 4f, () -> entityFly.get() && !twoBjpBoost.get());

    @Setting
    private static final FloatValue vSpeed = new FloatValue("Fly Vertical Speed", 1.2f, 0.1f, 4f, () -> entityFly.get() && !twoBjpBoost.get());

    @Setting
    private static final BooleanValue perfectHorseJump = new BooleanValue("Perfect Horse Jump", false);

    public static LivingEntity hookControllingPassenger(LivingEntity original, Entity entity) {
        if (original != null || !shouldControl(entity))
            return original;

        return mc.player;
    }

    public static boolean shouldTreatAsSaddled(Mob entity) {
        return shouldControl(entity);
    }

    public static boolean shouldTreatAsTamed(Entity entity) {
        return shouldControl(entity);
    }

    private static boolean shouldControl(Entity entity) {
        var player = mc.player;
        return ModuleManager.getModuleState(EntityControl.class)
                && control.get()
                && player != null
                && entity != null
                && entity.isAlive()
                && player.isPassenger()
                && player.getVehicle() == entity
                && isSupported(entity);
    }

    private static boolean isSupported(Entity entity) {
        return entity instanceof PlayerRideable
                || entity instanceof ItemSteerable
                || entity instanceof HappyGhast;
    }

    public static float modifiedHorseJump(float original) {
        if (ModuleManager.getModuleState(EntityControl.class) && perfectHorseJump.get())
            return 1f;

        return original;
    }

    public static boolean hookUpdatePaddles(AbstractBoat boatEntity, boolean original) {
        if (shouldIgnoreBoatSteering(boatEntity) && stopBoatPaddles.get())
            return false;

        return original;
    }

    public static boolean shouldIgnoreBoatSteering(AbstractBoat boatEntity) {
        var player = mc.player;
        return ModuleManager.getModuleState(EntityControl.class) && entityFly.get() && player != null && player.isPassenger() && player.getVehicle() == boatEntity;
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onEnable() {
    }

    @Override
    public String description() {
        return entityFly.get() ? "Dismount with left alt" : null;
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        var player = mc.player;
        if (player == null || !entityFly.get()) return;

        if (player.isPassenger() && InputConstants.isKeyDown(Keys.KEY_LEFT_ALT)) {
            player.stopRiding();
            PacketUtil.sendPacket(new ServerboundPlayerInputPacket(new Input(false, false, false, false, false, true, false)));
            Client.notificationManager.addNotification("Success", "Dismounted");
            return;
        }

        var vehicle = player.getVehicle();
        if (!player.isPassenger() || vehicle == null) return;

        float yMotion = 0f;
        boolean boostMode = twoBjpBoost.get();
        float booster = boostMode ? 3f : vSpeed.get();

        if (InputConstants.isKeyDown(InputConstants.getKey(mc.options.keyJump.saveString()).getValue()))
            yMotion += booster;

        if (InputConstants.isKeyDown(InputConstants.getKey(mc.options.keyShift.saveString()).getValue()))
            yMotion -= booster;

        double yaw = Math.toRadians(MovementUtil.getPlayerDirection());
        boolean moving = MovementUtil.isMoving();
        float horizontalSpeed = boostMode ? 17f : hSpeed.get();
        double xMotion = moving ? -Math.sin(yaw) * horizontalSpeed : 0.0;
        double zMotion = moving ? Math.cos(yaw) * horizontalSpeed : 0.0;

        if (stopBoatPaddles.get()) {
            float lerpedYaw = Interpolation.INSTANCE.lerp(vehicle.getYRot(), player.getYRot(), 0.35f);
            vehicle.setYRot(lerpedYaw);
        }

        vehicle.setNoGravity(true);
        vehicle.setDeltaMovement(xMotion, yMotion, zMotion);
    }

    @Override
    public void onTick(TickEvent event) {
        var player = mc.player;
        if (!entityFly.get() || player == null || !player.isPassenger() || player.getVehicle() == null) return;

        mc.options.keyShift.setDown(false);
    }
}
