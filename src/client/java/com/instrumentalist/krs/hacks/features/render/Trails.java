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
import org.lwjgl.nanovg.NVGPaint;
import org.nvgu.NVGU;
import org.nvgu.util.NVGUColour;

import java.awt.Color;

import static org.lwjgl.nanovg.NanoVG.NVG_ONE;
import static org.lwjgl.nanovg.NanoVG.NVG_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.nanovg.NanoVG.NVG_SRC_ALPHA;
import static org.lwjgl.nanovg.NanoVG.nvgClosePath;
import static org.lwjgl.nanovg.NanoVG.nvgGlobalCompositeBlendFunc;
import static org.lwjgl.nanovg.NanoVG.nvgLinearGradient;
import static org.nvgu.util.TrackedNanoVG.nvgBeginPath;
import static org.nvgu.util.TrackedNanoVG.nvgLineTo;
import static org.nvgu.util.TrackedNanoVG.nvgMoveTo;

public class Trails extends Module {
    private static final int LENGTH = 30;
    private static final int DELAY = 1;
    private static final double LAG = 0.3;
    private static final int BRIDGE_STEPS = 6;
    private static final int CAPACITY = LENGTH + 8;
    private static final int SUBDIVISIONS = 6;
    private static final int MAX_SAMPLES = LENGTH * SUBDIVISIONS + BRIDGE_STEPS + 4;

    private static final float WIDTH = 2.5f;
    private static final Color HEAD_COLOUR = new Color(110, 190, 255);
    private static final Color TAIL_COLOUR = new Color(255, 90, 200);

    private final double[] xs = new double[CAPACITY];
    private final double[] ys = new double[CAPACITY];
    private final double[] zs = new double[CAPACITY];
    private final float[] screenX = new float[MAX_SAMPLES];
    private final float[] screenY = new float[MAX_SAMPLES];
    private final float[] screenWidth = new float[MAX_SAMPLES];
    private final float[] life = new float[MAX_SAMPLES];
    private final float[] leftX = new float[MAX_SAMPLES];
    private final float[] leftY = new float[MAX_SAMPLES];
    private final float[] rightX = new float[MAX_SAMPLES];
    private final float[] rightY = new float[MAX_SAMPLES];
    private final float[] projected = new float[3];
    private final double[] curveHead = new double[3];
    private final double[] curveBefore = new double[3];
    private boolean hasSamples;
    private int firstTick;
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
        hasSamples = false;
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
        if (!hasSamples) {
            firstTick = tickCounter;
            hasSamples = true;
        }
        firstTick = Math.max(firstTick, tickCounter - CAPACITY + 1);

        int slot = Math.floorMod(tickCounter, CAPACITY);
        xs[slot] = player.getX();
        ys[slot] = player.getY();
        zs[slot] = player.getZ();
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        projectedCount = 0;
        if (mc.player == null || mc.level == null || !hasSamples || !RenderUtil.shouldRenderWorldHudOverlays()) return;

        double now = tickCounter - 1 + event.partialTicks;
        double headTime = now - DELAY;
        double tailTime = Math.max(now - LENGTH, firstTick);
        double endTime = now - LAG;
        double bridge = endTime - headTime;
        double span = endTime - tailTime;
        if (headTime - tailTime < 1.0e-3) return;

        Vec3 camera = mc.gameRenderer.mainCamera().position();
        float toScaledX = NanoVGManager.getScaledScreenWidth() / Math.max(1, mc.getWindow().getWidth());
        float toScaledY = NanoVGManager.getScaledScreenHeight() / Math.max(1, mc.getWindow().getHeight());

        evaluate(tailTime, 0f, camera, toScaledX, toScaledY);
        for (long step = (long) Math.floor(tailTime * SUBDIVISIONS) + 1; projectedCount < MAX_SAMPLES - BRIDGE_STEPS - 2; step++) {
            double time = step / (double) SUBDIVISIONS;
            if (time >= headTime - 1.0e-4) break;

            evaluate(time, (float) ((time - tailTime) / span), camera, toScaledX, toScaledY);
        }
        evaluate(headTime, (float) ((headTime - tailTime) / span), camera, toScaledX, toScaledY);

        curve(headTime, curveHead);
        curve(headTime - 0.5, curveBefore);
        int endBase = (int) Math.floor(endTime);
        double endFraction = endTime - endBase;
        double endX = sample(xs, endBase) + (sample(xs, endBase + 1) - sample(xs, endBase)) * endFraction;
        double endY = sample(ys, endBase) + (sample(ys, endBase + 1) - sample(ys, endBase)) * endFraction;
        double endZ = sample(zs, endBase) + (sample(zs, endBase + 1) - sample(zs, endBase)) * endFraction;
        double controlX = curveHead[0] + (curveHead[0] - curveBefore[0]) * bridge;
        double controlY = curveHead[1] + (curveHead[1] - curveBefore[1]) * bridge;
        double controlZ = curveHead[2] + (curveHead[2] - curveBefore[2]) * bridge;
        for (int i = 1; i <= BRIDGE_STEPS; i++) {
            double u = i / (double) BRIDGE_STEPS;
            double a = (1.0 - u) * (1.0 - u);
            double b = 2.0 * (1.0 - u) * u;
            double c = u * u;
            project(
                    a * curveHead[0] + b * controlX + c * endX,
                    a * curveHead[1] + b * controlY + c * endY,
                    a * curveHead[2] + b * controlZ + c * endZ,
                    (float) ((headTime + u * bridge - tailTime) / span),
                    camera, toScaledX, toScaledY
            );
        }
    }

    private void evaluate(double time, float remaining, Vec3 camera, float toScaledX, float toScaledY) {
        curve(time, curveHead);
        project(curveHead[0], curveHead[1], curveHead[2], remaining, camera, toScaledX, toScaledY);
    }

    private void curve(double time, double[] output) {
        int base = (int) Math.floor(time);
        float t = (float) (time - base);
        output[0] = catmullRom(sample(xs, base - 1), sample(xs, base), sample(xs, base + 1), sample(xs, base + 2), t);
        output[1] = catmullRom(sample(ys, base - 1), sample(ys, base), sample(ys, base + 1), sample(ys, base + 2), t);
        output[2] = catmullRom(sample(zs, base - 1), sample(zs, base), sample(zs, base + 1), sample(zs, base + 2), t);
    }

    private double sample(double[] values, int tick) {
        return values[Math.floorMod(Math.clamp(tick, firstTick, tickCounter), CAPACITY)];
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
            int start = 0;
            while (start < projectedCount) {
                while (start < projectedCount && Float.isNaN(screenX[start]))
                    start++;
                int end = start;
                while (end + 1 < projectedCount && !Float.isNaN(screenX[end + 1]))
                    end++;

                if (end - start >= 1) {
                    drawRibbon(vg, start, end, 3f, 45f);
                    drawRibbon(vg, start, end, 1f, 150f);
                }
                start = end + 1;
            }
        } finally {
            nvgGlobalCompositeBlendFunc(handle, NVG_SRC_ALPHA, NVG_ONE_MINUS_SRC_ALPHA);
            vg.restore();
        }
    }

    private void drawRibbon(NVGU vg, int start, int end, float widthScale, float alpha) {
        long handle = vg.getHandle();
        float pathLength = 0f;
        for (int i = start; i < end && pathLength < 1f; i++)
            pathLength += Math.abs(screenX[i + 1] - screenX[i]) + Math.abs(screenY[i + 1] - screenY[i]);
        if (pathLength < 1f) return;

        float normalX = 0f;
        float normalY = 1f;
        for (int i = start; i <= end; i++) {
            int previous = Math.max(i - 1, start);
            int next = Math.min(i + 1, end);
            float tangentX = screenX[next] - screenX[previous];
            float tangentY = screenY[next] - screenY[previous];
            float length = (float) Math.sqrt(tangentX * tangentX + tangentY * tangentY);
            if (length > 1.0e-4f) {
                normalX = -tangentY / length;
                normalY = tangentX / length;
            }

            float half = (0.6f + life[i] * screenWidth[i]) * widthScale * 0.5f;
            leftX[i] = screenX[i] + normalX * half;
            leftY[i] = screenY[i] + normalY * half;
            rightX[i] = screenX[i] - normalX * half;
            rightY[i] = screenY[i] - normalY * half;
        }

        nvgBeginPath(handle);
        nvgMoveTo(handle, leftX[start], leftY[start]);
        for (int i = start + 1; i <= end; i++)
            nvgLineTo(handle, leftX[i], leftY[i]);
        for (int i = end; i >= start; i--)
            nvgLineTo(handle, rightX[i], rightY[i]);
        nvgClosePath(handle);

        float tailLife = life[start];
        float headLife = life[end];
        Color tail = withAlpha(mix(TAIL_COLOUR, HEAD_COLOUR, tailLife), (int) (tailLife * tailLife * alpha));
        Color tip = withAlpha(mix(TAIL_COLOUR, HEAD_COLOUR, headLife), (int) (headLife * headLife * alpha));
        NVGPaint paint = vg.createAndStorePaint();
        nvgLinearGradient(handle, screenX[start], screenY[start], screenX[end], screenY[end], vg.createAndStoreColour(tail), vg.createAndStoreColour(tip), paint);
        new NVGUColour(paint).apply(vg, NVGUColour.RenderType.FILL);
    }

    private static double catmullRom(double p0, double p1, double p2, double p3, float t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return 0.5 * (2.0 * p1 + (p2 - p0) * t + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2 + (3.0 * p1 - p0 - 3.0 * p2 + p3) * t3);
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
