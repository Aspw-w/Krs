package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.events.features.KeyboardEvent;
import com.instrumentalist.krs.events.features.MouseScrollEvent;
import com.instrumentalist.krs.events.features.RenderHudEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.math.Interpolation;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.krs.utils.value.KeyBindValue;
import net.minecraft.client.CameraType;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.Mth;

public class Camera extends Module {
    @Setting
    public static final BooleanValue freeLook = new BooleanValue("Free Look", true);

    @Setting
    private final KeyBindValue freeLookKey = new KeyBindValue("Free Look Key (Bind config)", Keys.KEY_UNKNOWN, freeLook::get, KeyBindValue.ConfigStorage.BIND_CONFIG);

    @Setting
    public static final BooleanValue skipFrontView = new BooleanValue("Skip Front View", false);

    @Setting
    public static final BooleanValue minimalBobbing = new BooleanValue("Minimal Bobbing", false);

    @Setting
    private static final BooleanValue noClip = new BooleanValue("No Clip", false);

    @Setting
    private static final FloatValue noClipDistance = new FloatValue("No Clip Distance", 0f, -4f, 100f, "m", noClip::get);

    @Setting
    private static final BooleanValue fpsBobbing = new BooleanValue("FPS Bobbing", false);

    @Setting
    private final KeyBindValue zoomKey = new KeyBindValue("Zoom Key (Bind config)", Keys.KEY_UNKNOWN, KeyBindValue.ConfigStorage.BIND_CONFIG);

    @Setting
    public static final FloatValue itemSize = new FloatValue("Item Size", 0f, -2f, 2f);

    @Setting
    public static final FloatValue itemFov = new FloatValue("Item Fov", 0f, -2f, 2f);

    @Setting
    public static final FloatValue itemPositionX = new FloatValue("Item Position X", 0f, -2f, 2f);

    @Setting
    public static final FloatValue itemPositionY = new FloatValue("Item Position Y", 0f, -2f, 2f);

    @Setting
    public static final FloatValue itemPositionZ = new FloatValue("Item Position Z", 0f, -2f, 2f);

    @Setting
    public static final BooleanValue noEquipAnimation = new BooleanValue("No Equip Animation", false);

    @Setting
    public static final BooleanValue fluxSwing = new BooleanValue("Flux Swing", false);

    @Setting
    public static final BooleanValue oldEatSwing = new BooleanValue("Old Eat Swing", false);

    @Setting
    public static final BooleanValue oldItemPosition = new BooleanValue("Old Item Position", false);

    @Setting
    public static final BooleanValue punchingABlockWhileDoingStuff = new BooleanValue("Punching a block while doing stuff", false);

    @Setting
    private static final BooleanValue slowSwing = new BooleanValue("Slow Swing", false);

    @Setting
    private static final IntValue setSwingSpeed = new IntValue("Set Swing Speed", 5, -6, 20, slowSwing::get);

    private static boolean zooming = false;
    private static Float prevFov = null;
    private static Float zoomedFov = null;
    private static Float baseFov = null;
    private static boolean zoomed = false;
    private static float zoomInFov = 20f;
    private static long lastFrameTime = System.nanoTime();

    private static boolean freeLooking;
    private static float lookYaw;
    private static float lookPitch;
    private static CameraType previousCameraType;

    public Camera() {
        super("Camera", ModuleCategory.Render, Keys.KEY_UNKNOWN, false, false);
    }

    @Override
    public boolean isLocked() {
        return true;
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
        stopFreeLook();
    }

    @Override
    public void onWorld(WorldEvent event) {
        stopFreeLook();
        resetZoomState();
    }

    @Override
    public void onKey(KeyboardEvent event) {
        handleZoomKey(event);

        int key = freeLookKey.get();
        if (key == Keys.KEY_UNKNOWN || event.key != key) return;

        if (event.action == Keys.PRESS && !freeLooking && freeLook.get() && mc.player != null && mc.gui.screen() == null)
            startFreeLook();
        else if (event.action == Keys.RELEASE)
            stopFreeLook();
    }

    public static boolean isNoClipActive() {
        return ModuleManager.getModuleState(Camera.class) && noClip.get();
    }

    public static float getNoClipDistance() {
        return noClipDistance.get();
    }

    public static boolean isFpsBobbingActive() {
        return ModuleManager.getModuleState(Camera.class) && fpsBobbing.get();
    }

    public static boolean shouldSkipEquipAnimation() {
        return ModuleManager.getModuleState(Camera.class) && noEquipAnimation.get() || OldHitting.shouldSkipEquipAnimation();
    }

    public static int hookSwingSpeed(int original, Entity entity) {
        if (ModuleManager.getModuleState(Camera.class) && slowSwing.get() && entity instanceof LocalPlayer)
            return 6 + setSwingSpeed.get();

        return original;
    }

    private void startFreeLook() {
        freeLooking = true;
        lookYaw = mc.player.getYRot();
        lookPitch = mc.player.getXRot();
        previousCameraType = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    }

    private static void stopFreeLook() {
        if (!freeLooking) return;

        freeLooking = false;
        if (previousCameraType != null)
            mc.options.setCameraType(previousCameraType);
        previousCameraType = null;
    }

    public static boolean shouldDisableCameraBobbing() {
        return ModuleManager.getModuleState(Camera.class) && minimalBobbing.get();
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

        return next;
    }

    public static boolean shouldZoom() {
        return ModuleManager.getModuleState(Camera.class) && zooming;
    }

    public static float zoomFovHook(float basedFov) {
        if (!ModuleManager.getModuleState(Camera.class))
            return basedFov;

        baseFov = basedFov;

        if (zooming && zoomedFov == null) {
            prevFov = basedFov;
            zoomedFov = basedFov;
        }

        if ((zooming || zoomed) && zoomedFov != null)
            return zoomedFov;

        return basedFov;
    }

    private static void resetZoomState() {
        zooming = false;
        prevFov = null;
        zoomedFov = null;
        baseFov = null;
        lastFrameTime = System.nanoTime();
        zoomed = false;
        zoomInFov = 20f;
    }

    private void handleZoomKey(KeyboardEvent event) {
        if (mc.player == null) return;

        if (event.key == zoomKey.get() && event.action == Keys.PRESS && mc.gui.screen() == null) {
            zooming = true;
            zoomed = true;
            lastFrameTime = System.nanoTime();
        }

        if (event.key == zoomKey.get() && event.action == Keys.RELEASE) {
            zooming = false;
            zoomInFov = 20f;
            lastFrameTime = System.nanoTime();
            mc.options.smoothCamera = false;
        }
    }

    @Override
    public void onRenderHud(RenderHudEvent event) {
        if (mc.player == null) return;

        long currentTime = System.nanoTime();
        float deltaTime = (currentTime - lastFrameTime) / 1e9f;
        lastFrameTime = currentTime;

        if (zooming) {
            if (zoomedFov != null) {
                zoomInFov = clampFov(zoomInFov, 3f, 160f);
                zoomedFov = animateFov(prevFov, zoomInFov, deltaTime);
                prevFov = zoomedFov;
                mc.options.smoothCamera = true;
            }
        } else if (zoomed && zoomedFov != null && baseFov != null) {
            zoomedFov = animateFov(prevFov, baseFov, deltaTime);
            prevFov = zoomedFov;

            if (Math.abs(zoomedFov - baseFov) <= 0.1f)
                finishZoomOut();
        } else if (zoomed) {
            finishZoomOut();
        }
    }

    @Override
    public void onMouseScroll(MouseScrollEvent event) {
        if (mc.player == null) return;

        if (zooming && zoomedFov != null) {
            float scroll = - (float) event.vertical * 5f;
            zoomInFov += scroll;
            event.cancel();
        }
    }

    private static float animateFov(float start, float end, float deltaTime) {
        float nextFov = Interpolation.INSTANCE.lerpWithTime(start, end, 8f, deltaTime);
        return clampFov(nextFov, Math.min(start, end), Math.max(start, end));
    }

    private static float clampFov(float fov, float minFov, float maxFov) {
        return Math.max(minFov, Math.min(maxFov, fov));
    }

    private static void finishZoomOut() {
        zoomedFov = null;
        prevFov = null;
        baseFov = null;
        zoomed = false;
        lastFrameTime = System.nanoTime();
        mc.options.smoothCamera = false;
    }
}
