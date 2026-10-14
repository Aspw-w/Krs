package com.instrumentalist.krs.hacks.features.render;



import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.KeyboardEvent;
import com.instrumentalist.krs.events.features.MouseScrollEvent;
import com.instrumentalist.krs.events.features.RenderHudEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.math.Interpolation;
import com.instrumentalist.krs.utils.value.KeyBindValue;
import com.instrumentalist.krs.utils.input.Keys;
import xyz.breadloaf.imguimc.customwindow.ModuleRenderable;

public class Zoom extends Module {

    public Zoom() {
        super("Zoom", ModuleCategory.Render, Keys.KEY_UNKNOWN, true, false);
    }

    @Setting
    private final KeyBindValue zoomKey = new KeyBindValue("Zoom Key (Bind config)", Keys.KEY_UNKNOWN, KeyBindValue.ConfigStorage.BIND_CONFIG);

    private static boolean zooming = false;
    private static Float prevFov = null;
    private static Float zoomedFov = null;
    private static Float baseFov = null;
    private static boolean zoomed = false;
    private static float zoomInFov = 20f;
    private static long lastFrameTime = System.nanoTime();

    public static boolean shouldZoom() {
        return ModuleManager.getModuleState(Zoom.class) && zooming;
    }

    public static float zoomFovHook(float basedFov) {
        if (!ModuleManager.getModuleState(Zoom.class))
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

    @Override
    public String tag() {
        return ModuleRenderable.keyName(zoomKey.get());
    }

    @Override
    public void onDisable() {
        if (Client.loaded)
            this.toggle();
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onWorld(WorldEvent event) {
        resetZoomState();
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

    @Override
    public void onKey(KeyboardEvent event) {
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
