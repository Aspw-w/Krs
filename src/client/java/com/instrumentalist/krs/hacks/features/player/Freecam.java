package com.instrumentalist.krs.hacks.features.player;

import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import com.instrumentalist.krs.utils.input.Keys;

public class Freecam extends Module {

    @Setting
    private static final FloatValue horizontalSpeed = new FloatValue("Horizontal Speed", 2f, 0.1f, 4f);

    @Setting
    private static final FloatValue verticalSpeed = new FloatValue("Vertical Speed", 1f, 0.1f, 4f);

    private static boolean canFly = false;
    private static Vec3 camPos = Vec3.ZERO;
    private static Vec3 prevCamPos = Vec3.ZERO;
    private static float camYaw = 0f;
    private static float camPitch = 0f;

    public Freecam() {
        super("Freecam", ModuleCategory.Player, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onDisable() {
        if (canFly)
            reloadChunks();
        canFly = false;
    }

    @Override
    public void onEnable() {
        var player = mc.player;
        if (player == null) return;

        camPos = player.getEyePosition();
        prevCamPos = camPos;
        camYaw = player.getYRot();
        camPitch = player.getXRot();
        canFly = true;
        reloadChunks();
    }

    @Override
    public void onWorld(WorldEvent event) {
        toggle();
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null) return;

        if (!canFly || mc.gui.screen() != null) {
            prevCamPos = camPos;
            return;
        }

        Vec3 moveVector = getKeyboardMoveVector();
        double yawRad = Math.toRadians(camYaw);
        double sinYaw = Math.sin(yawRad);
        double cosYaw = Math.cos(yawRad);
        double offsetX = moveVector.x * cosYaw - moveVector.y * sinYaw;
        double offsetZ = moveVector.x * sinYaw + moveVector.y * cosYaw;

        double offsetY = 0.0;
        if (isKeyDown(mc.options.keyJump))
            offsetY += verticalSpeed.get();
        if (isKeyDown(mc.options.keyShift))
            offsetY -= verticalSpeed.get();

        Vec3 offset = new Vec3(offsetX, 0.0, offsetZ)
                .scale(horizontalSpeed.get())
                .add(0.0, offsetY, 0.0);

        prevCamPos = camPos;
        camPos = camPos.add(offset);
    }

    private Vec3 getKeyboardMoveVector() {
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

        return new Vec3(left, forward, 0.0);
    }

    private boolean isKeyDown(KeyMapping key) {
        return InputConstants.isKeyDown(InputConstants.getKey(key.saveString()).getValue()
        );
    }

    private void reloadChunks() {
        if (mc.level != null)
            mc.levelExtractor.allChanged();
    }

    public static boolean getCanFly() {
        return canFly;
    }

    public static void setCanFly(boolean value) {
        canFly = value;
    }

    public static Vec3 getCamPos(float partialTicks) {
        return new Vec3(
                Mth.lerp((double) partialTicks, prevCamPos.x, camPos.x),
                Mth.lerp((double) partialTicks, prevCamPos.y, camPos.y),
                Mth.lerp((double) partialTicks, prevCamPos.z, camPos.z)
        );
    }

    public static float getCamYaw() {
        return camYaw;
    }

    public static float getCamPitch() {
        return camPitch;
    }

    public static void turn(double deltaYaw, double deltaPitch) {
        camYaw += (float) (deltaYaw * 0.15);
        camPitch += (float) (deltaPitch * 0.15);
        camPitch = Mth.clamp(camPitch, -90f, 90f);
    }

    public static Vec3 getScaledCamDir(double scale) {
        return Vec3.directionFromRotation(camPitch, camYaw).scale(scale);
    }

}
