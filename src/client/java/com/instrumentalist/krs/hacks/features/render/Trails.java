package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.Render3DEvent;
import com.instrumentalist.krs.events.features.RenderHudEvent;
import com.instrumentalist.krs.events.features.TickEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.nanovg.NanoVGManager;
import com.instrumentalist.krs.utils.render.RenderUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.nvgu.NVGU;

import java.awt.Color;

import static org.lwjgl.nanovg.NanoVG.NVG_ONE;
import static org.lwjgl.nanovg.NanoVG.NVG_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.nanovg.NanoVG.NVG_SRC_ALPHA;
import static org.lwjgl.nanovg.NanoVG.nvgGlobalCompositeBlendFunc;

public class Trails extends Module {
    private static final int MAX_POINTS = 32;
    private static final double MIN_STEP_SQUARED = 0.0004;

    private static final int LENGTH = 30;
    private static final float WIDTH = 2.5f;
    private static final Color HEAD_COLOUR = new Color(110, 190, 255);
    private static final Color TAIL_COLOUR = new Color(255, 90, 200);

    private final double[] xs = new double[MAX_POINTS];
    private final double[] ys = new double[MAX_POINTS];
    private final double[] zs = new double[MAX_POINTS];
    private final int[] ticks = new int[MAX_POINTS];
    private final float[] screenX = new float[MAX_POINTS + 1];
    private final float[] screenY = new float[MAX_POINTS + 1];
    private final float[] screenWidth = new float[MAX_POINTS + 1];
    private final float[] life = new float[MAX_POINTS + 1];
    private final float[] projected = new float[3];
    private int head;
    private int size;
    private int tickCounter;
    private int projectedCount;

    public Trails() {
        super("Trails", ModuleCategory.Render, Keys.KEY_UNKNOWN, false, false);
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        reset();
    }

    @Override
    public void onWorld(WorldEvent event) {
        reset();
    }

    private void reset() {
        head = 0;
        size = 0;
        projectedCount = 0;
    }

    @Override
    public void onTick(TickEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            reset();
            return;
        }

        tickCounter++;
        while (size > 0 && tickCounter - ticks[oldestIndex()] > LENGTH)
            size--;

        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        if (size > 0) {
            int newest = (head - 1 + MAX_POINTS) % MAX_POINTS;
            double dx = x - xs[newest];
            double dy = y - ys[newest];
            double dz = z - zs[newest];
            if (dx * dx + dy * dy + dz * dz < MIN_STEP_SQUARED) return;
        }

        xs[head] = x;
        ys[head] = y;
        zs[head] = z;
        ticks[head] = tickCounter;
        head = (head + 1) % MAX_POINTS;
        size = Math.min(size + 1, MAX_POINTS);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        projectedCount = 0;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || size == 0 || !RenderUtil.shouldRenderWorldHudOverlays()) return;

        float partialTicks = event.partialTicks;
        Vec3 camera = mc.gameRenderer.mainCamera().position();
        float toScaledX = NanoVGManager.getScaledScreenWidth() / Math.max(1, mc.getWindow().getWidth());
        float toScaledY = NanoVGManager.getScaledScreenHeight() / Math.max(1, mc.getWindow().getHeight());
        float now = tickCounter + partialTicks;

        for (int i = 0; i < size; i++) {
            int index = (oldestIndex() + i) % MAX_POINTS;
            float remaining = Math.clamp(1f - (now - ticks[index]) / LENGTH, 0f, 1f);
            project(xs[index], ys[index], zs[index], remaining, camera, toScaledX, toScaledY);
        }

        Vec3 lerped = RenderUtil.INSTANCE.getLerpedPos(player, partialTicks);
        project(lerped.x, lerped.y, lerped.z, 1f, camera, toScaledX, toScaledY);
    }

    private void project(double x, double y, double z, float remaining, Vec3 camera, float toScaledX, float toScaledY) {
        int slot = projectedCount++;
        life[slot] = remaining;
        if (!RenderUtil.INSTANCE.renderedWorldToScreen(x, y, z, projected)) {
            screenX[slot] = Float.NaN;
            return;
        }

        double dx = x - camera.x;
        double dy = y - camera.y;
        double dz = z - camera.z;
        float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        screenX[slot] = projected[0] * toScaledX;
        screenY[slot] = projected[1] * toScaledY;
        screenWidth[slot] = Math.clamp(WIDTH * 6f / Math.max(distance, 0.5f), WIDTH * 0.2f, WIDTH * 4f);
    }

    @Override
    public void onRenderHud(RenderHudEvent event) {
        if (projectedCount < 2 || !RenderUtil.shouldRenderWorldHudOverlays()) return;

        Client.nanoVgManager.loadWorldAnchored(this::drawTrail);
    }

    private void drawTrail(NVGU vg) {
        long handle = vg.getHandle();

        vg.save();
        try {
            nvgGlobalCompositeBlendFunc(handle, NVG_SRC_ALPHA, NVG_ONE);
            for (int i = 0; i < projectedCount - 1; i++) {
                if (Float.isNaN(screenX[i]) || Float.isNaN(screenX[i + 1])) continue;

                float fraction = life[i];
                if (fraction <= 0f) continue;

                Color colour = mix(TAIL_COLOUR, HEAD_COLOUR, fraction);
                float thickness = 0.6f + fraction * screenWidth[i + 1];
                vg.line(screenX[i], screenY[i], screenX[i + 1], screenY[i + 1], thickness * 3f, withAlpha(colour, (int) (fraction * fraction * 45f)));
                vg.line(screenX[i], screenY[i], screenX[i + 1], screenY[i + 1], thickness, withAlpha(colour, (int) (fraction * fraction * 150f)));
            }
        } finally {
            nvgGlobalCompositeBlendFunc(handle, NVG_SRC_ALPHA, NVG_ONE_MINUS_SRC_ALPHA);
            vg.restore();
        }
    }

    private int oldestIndex() {
        return (head - size + MAX_POINTS) % MAX_POINTS;
    }

    private static Color mix(Color a, Color b, float t) {
        float inverse = 1f - t;
        return new Color(
                Math.round(a.getRed() * inverse + b.getRed() * t),
                Math.round(a.getGreen() * inverse + b.getGreen() * t),
                Math.round(a.getBlue() * inverse + b.getBlue() * t)
        );
    }

    private static Color withAlpha(Color colour, int alpha) {
        return new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), Math.clamp(alpha, 0, 255));
    }
}
