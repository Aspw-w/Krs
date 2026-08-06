package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.value.FloatValue;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

public class FreeLook extends Module {

    @Setting
    private static final FloatValue cameraDistance = new FloatValue("Camera Distance", 4f, 1f, 12f);

    @Setting
    private static final FloatValue sensitivity = new FloatValue("Sensitivity", 1f, 0.1f, 2f);

    private static boolean active = false;
    private static float cameraYaw = 0f;
    private static float cameraPitch = 0f;

    public FreeLook() {
        super("FreeLook", ModuleCategory.Render, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    @Override
    public void onEnable() {
        var player = mc.player;
        if (player != null) {
            cameraYaw = player.getYRot();
            cameraPitch = player.getXRot();
        }
        active = true;
    }

    @Override
    public void onDisable() {
        active = false;
    }

    @Override
    public void onWorld(WorldEvent event) {
        if (active)
            this.toggle();
    }

    public static boolean shouldMoveCamera() {
        return ModuleManager.getModuleState(FreeLook.class) && active;
    }

    public static void turn(double deltaYaw, double deltaPitch) {
        cameraYaw += (float) (deltaYaw * sensitivity.get());
        cameraPitch += (float) (deltaPitch * sensitivity.get());
        cameraPitch = Mth.clamp(cameraPitch, -90f, 90f);
    }

    public static float getCameraYaw() {
        return cameraYaw;
    }

    public static float getCameraPitch() {
        return cameraPitch;
    }

    public static float getCameraDistance() {
        return cameraDistance.get();
    }
}