package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.events.features.KeyboardEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.KeyBindValue;
import net.minecraft.client.CameraType;
import net.minecraft.util.Mth;

public class Camera extends Module {
    public static final int TRANSITION_NONE = 0;
    public static final int TRANSITION_OPEN = 1;
    public static final int TRANSITION_CLOSE = 2;
    public static final int TRANSITION_FLIP = 3;
    public static final int TRANSITION_RETURN = 4;
    public static final float TRANSITION_DEPTH = 0.35F;
    private static final float TRANSITION_NANOS = 150_000_000f;
    private static final float CLOSE_TRANSITION_NANOS = 70_000_000f;

    @Setting
    public static final BooleanValue freeLook = new BooleanValue("Free Look", true);

    @Setting
    private final KeyBindValue freeLookKey = new KeyBindValue("Free Look Key (Bind config)", Keys.KEY_UNKNOWN, freeLook::get, KeyBindValue.ConfigStorage.BIND_CONFIG);

    @Setting
    public static final BooleanValue skipFrontView = new BooleanValue("Skip Front View", false);

    @Setting
    public static final BooleanValue smoothToggle = new BooleanValue("Smooth Toggle", true);

    private static boolean freeLooking;
    private static float lookYaw;
    private static float lookPitch;
    private static CameraType previousCameraType;
    private static int transitionMode = TRANSITION_NONE;
    private static long transitionStart;
    private static CameraType pendingCameraType;
    private static boolean returningFromFreeLook;
    private static float returnYaw;
    private static float returnPitch;

    public Camera() {
        super("Camera", ModuleCategory.Render, Keys.KEY_UNKNOWN, false, false);
    }

    @Override
    public void onEnable() {
        transitionMode = TRANSITION_NONE;
        pendingCameraType = null;
    }

    @Override
    public void onDisable() {
        stopFreeLook(false);
        finishTransition();
    }

    @Override
    public void onWorld(WorldEvent event) {
        stopFreeLook(false);
        finishTransition();
    }

    @Override
    public void onKey(KeyboardEvent event) {
        int key = freeLookKey.get();
        if (key == Keys.KEY_UNKNOWN || event.key != key) return;

        if (event.action == Keys.PRESS && !freeLooking && freeLook.get() && mc.player != null && mc.gui.screen() == null)
            startFreeLook();
        else if (event.action == Keys.RELEASE)
            stopFreeLook(true);
    }

    private void startFreeLook() {
        finishTransition();
        freeLooking = true;
        lookYaw = mc.player.getYRot();
        lookPitch = mc.player.getXRot();
        previousCameraType = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        if (!smoothToggle.get()) return;

        if (previousCameraType.isFirstPerson())
            startTransition(TRANSITION_OPEN);
        else if (previousCameraType.isMirrored())
            startTransition(TRANSITION_FLIP);
    }

    private static void stopFreeLook(boolean animate) {
        if (!freeLooking) return;

        freeLooking = false;
        CameraType previous = previousCameraType;
        previousCameraType = null;
        if (previous == null) return;

        if (!animate || !smoothToggle.get()) {
            mc.options.setCameraType(previous);
            return;
        }

        returningFromFreeLook = true;
        returnYaw = lookYaw;
        returnPitch = lookPitch;
        if (previous.isFirstPerson()) {
            startTransition(TRANSITION_CLOSE);
            pendingCameraType = previous;
            return;
        }

        mc.options.setCameraType(previous);
        startTransition(previous.isMirrored() ? TRANSITION_FLIP : TRANSITION_RETURN);
    }

    public static boolean isReturningFromFreeLook() {
        return returningFromFreeLook;
    }

    public static float getReturnYaw() {
        return returnYaw;
    }

    public static float getReturnPitch() {
        return returnPitch;
    }

    public static boolean isFreeLooking() {
        return freeLooking && ModuleManager.getModuleState(Camera.class);
    }

    public static void turn(double deltaYaw, double deltaPitch) {
        lookYaw += (float) deltaYaw * 0.15F;
        lookPitch = Mth.clamp(lookPitch + (float) deltaPitch * 0.15F, -90.0F, 90.0F);
    }

    public static float getLookYaw() {
        return lookYaw;
    }

    public static float getLookPitch() {
        return lookPitch;
    }

    public static CameraType nextCameraType(CameraType previous, CameraType next) {
        if (!ModuleManager.getModuleState(Camera.class))
            return next;

        if (next == CameraType.THIRD_PERSON_FRONT && skipFrontView.get())
            next = next.cycle();

        if (!smoothToggle.get() || freeLooking)
            return next;

        if (transitionMode == TRANSITION_CLOSE)
            return previous;

        if (!previous.isFirstPerson() && next.isFirstPerson()) {
            startTransition(TRANSITION_CLOSE);
            pendingCameraType = next;
            return previous;
        }

        if (previous.isFirstPerson() && !next.isFirstPerson())
            startTransition(TRANSITION_OPEN);
        else if (!previous.isMirrored() && next.isMirrored())
            startTransition(TRANSITION_FLIP);

        return next;
    }

    private static void startTransition(int mode) {
        transitionMode = mode;
        transitionStart = System.nanoTime();
    }

    public static int getTransitionMode() {
        return ModuleManager.getModuleState(Camera.class) ? transitionMode : TRANSITION_NONE;
    }

    public static float getTransitionProgress() {
        float progress = Math.clamp((System.nanoTime() - transitionStart) / (transitionMode == TRANSITION_CLOSE ? CLOSE_TRANSITION_NANOS : TRANSITION_NANOS), 0.0F, 1.0F);
        float inverse = 1.0F - progress;
        return 1.0F - inverse * inverse * inverse;
    }

    public static void finishTransition() {
        transitionMode = TRANSITION_NONE;
        returningFromFreeLook = false;
        if (pendingCameraType != null)
            mc.options.setCameraType(pendingCameraType);
        pendingCameraType = null;
    }
}
