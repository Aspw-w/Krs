package com.instrumentalist.krs.hacks.features.movement;

import com.instrumentalist.krs.events.features.TickEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import com.instrumentalist.krs.utils.input.Keys;

public class ElytraFly extends Module {

    @Setting
    private final FloatValue horizontalSpeed = new FloatValue("Horizontal Speed", 1.8f, 0.1f, 4f);

    @Setting
    private final FloatValue verticalSpeed = new FloatValue("Vertical Speed", 1.2f, 0.1f, 4f);

    public ElytraFly() {
        super("Elytra Fly", ModuleCategory.Movement, Keys.KEY_UNKNOWN, false, true);
    }

    public static Vec3 hookFallFlyingMovement(Vec3 original, LivingEntity entity) {
        if (!(entity instanceof LocalPlayer player)) return original;

        ElytraFly module = ModuleManager.getModule(ElytraFly.class);
        if (module == null || !module.tempEnabled)
            return original;

        return module.getControlledMotion(original, player);
    }

    private Vec3 getControlledMotion(Vec3 original, LocalPlayer player) {
        if (!player.isFallFlying() || player.isPassenger() || !InventoryMove.canMoveFreely())
            return original;

        double left = 0.0;
        double forward = 0.0;

        if (isKeyDown(mc.options.keyLeft)) left += 1.0;
        if (isKeyDown(mc.options.keyRight)) left -= 1.0;
        if (isKeyDown(mc.options.keyUp)) forward += 1.0;
        if (isKeyDown(mc.options.keyDown)) forward -= 1.0;

        double length = Math.sqrt(left * left + forward * forward);
        if (length > 1.0) {
            left /= length;
            forward /= length;
        }

        double yaw = Math.toRadians(player.getYRot());
        double x = left * Math.cos(yaw) - forward * Math.sin(yaw);
        double z = left * Math.sin(yaw) + forward * Math.cos(yaw);
        double y = 0.0;

        if (isKeyDown(mc.options.keyJump)) y += verticalSpeed.get();
        if (isKeyDown(mc.options.keyShift)) y -= verticalSpeed.get();

        return new Vec3(x * horizontalSpeed.get(), y, z * horizontalSpeed.get());
    }

    @Override
    public void onTick(TickEvent event) {
        if (mc.player != null && mc.player.isFallFlying() && InventoryMove.canMoveFreely())
            mc.options.keyShift.setDown(false);
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onEnable() {
    }

    private boolean isKeyDown(KeyMapping key) {
        return InputConstants.isKeyDown(InputConstants.getKey(key.saveString()).getValue()
        );
    }
}
