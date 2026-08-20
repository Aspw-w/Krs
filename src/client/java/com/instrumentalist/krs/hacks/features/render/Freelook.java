package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.value.FloatValue;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public class Freelook extends Module {

    @Setting
    public static final FloatValue distance = new FloatValue("Distance", 4f, 0.1f, 50f);

    private static boolean active = false;
    private static float camYaw = 0f;
    private static float camPitch = 0f;

    public Freelook() {
        super("Freelook", ModuleCategory.Render, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    @Override
    public void onEnable() {
        if (mc.player == null) return;

        camYaw = mc.player.getYRot();
        camPitch = mc.player.getXRot();
        active = true;
    }

    @Override
    public void onDisable() {
        active = false;
    }

    @Override
    public void onWorld(WorldEvent event) {
        toggle();
    }

    public static boolean isActive() {
        return active;
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

    public static Vec3 getCamDirection() {
        return Vec3.directionFromRotation(camPitch, camYaw);
    }
}
