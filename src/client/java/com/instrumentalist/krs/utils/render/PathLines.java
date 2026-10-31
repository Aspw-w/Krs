package com.instrumentalist.krs.utils.render;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.EventListener;
import com.instrumentalist.krs.events.features.Render3DEvent;
import com.instrumentalist.krs.events.features.RenderHudEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.utils.nanovg.NanoVGManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;
import org.nvgu.NVGU;

import java.awt.Color;
import java.util.List;
import java.util.function.Consumer;

import static org.lwjgl.nanovg.NanoVG.*;

public final class PathLines implements EventListener {
    private static final PathLines INSTANCE = new PathLines();
    private static final int MAX_LINES = 16;
    private static final int MAX_POINTS = 512;
    private static final float NEAR_W = 0.05f;
    private static final float FADE_IN_SECONDS = 0.12f;
    private static final float FADE_OUT_SECONDS = 0.35f;
    private static final long HOLD_NANOS = 1_500_000_000L;
    private static final double LIFT = 0.05;
    private static final float GLOW_ALPHA = 0.2f;
    private static final float EDGE_ALPHA = 0.95f;
    private static final float GLOW_WIDTH = 4.5f;
    private static final float EDGE_WIDTH = 1.35f;

    private final Minecraft mc = Minecraft.getInstance();
    private final Line[] lines = new Line[MAX_LINES];
    private final Line staging = new Line();
    private final Vector4f clip = new Vector4f();
    private final Consumer<NVGU> drawer = this::draw;
    private int lineCount;
    private boolean registered;
    private boolean projected;
    private long lastFrameNanos;

    private PathLines() {
        for (int i = 0; i < MAX_LINES; i++)
            lines[i] = new Line();
    }

    public static void show(Object owner, Vec3 from, List<Vec3> path) {
        INSTANCE.put(owner, owner, from, path, RenderColors.DEFAULT);
    }

    public static void show(Object owner, Vec3 from, List<Vec3> path, Color color) {
        INSTANCE.put(owner, owner, from, path, color);
    }

    public static void show(Object owner, Object key, Vec3 from, List<Vec3> path) {
        INSTANCE.put(owner, key, from, path, RenderColors.DEFAULT);
    }

    public static void show(Object owner, Object key, Vec3 from, List<Vec3> path, Color color) {
        INSTANCE.put(owner, key, from, path, color);
    }

    public static void hide(Object owner, Object key) {
        long now = System.nanoTime();
        for (int i = 0; i < INSTANCE.lineCount; i++) {
            Line line = INSTANCE.lines[i];
            if (line.owner == owner && line.key == key && line.holdUntilNanos - now > 0L)
                line.holdUntilNanos = now;
        }
    }

    private void put(Object owner, Object key, Vec3 from, List<Vec3> path, Color color) {
        if (owner == null || key == null || path == null || path.isEmpty())
            return;

        staging.pointCount = 0;
        if (from != null)
            staging.add(from);
        for (int i = 0, n = path.size(); i < n && staging.pointCount < MAX_POINTS; i++)
            staging.add(path.get(i));
        if (staging.pointCount < 2)
            return;

        Line line = null;
        for (int i = 0; i < lineCount; i++) {
            if (lines[i].owner == owner && lines[i].key == key) {
                line = lines[i];
                break;
            }
        }

        boolean fresh = line == null;
        if (fresh) {
            if (lineCount < MAX_LINES) {
                line = lines[lineCount++];
            } else {
                line = lines[0];
                for (int i = 1; i < lineCount; i++) {
                    if (lines[i].holdUntilNanos < line.holdUntilNanos)
                        line = lines[i];
                }
            }
        }

        System.arraycopy(staging.points, 0, line.points, 0, staging.pointCount * 3);
        line.pointCount = staging.pointCount;
        line.owner = owner;
        line.key = key;
        line.color = color != null ? color : RenderColors.DEFAULT;
        line.holdUntilNanos = System.nanoTime() + HOLD_NANOS;
        if (fresh)
            line.alpha = 0f;
        line.commandCount = 0;
        setRegistered(true);
    }

    private void removeAt(int index) {
        int last = --lineCount;
        Line removed = lines[index];
        removed.owner = null;
        removed.key = null;
        lines[index] = lines[last];
        lines[last] = removed;
    }

    private void clear() {
        for (int i = 0; i < lineCount; i++) {
            lines[i].owner = null;
            lines[i].key = null;
        }
        lineCount = 0;
        projected = false;
        lastFrameNanos = 0L;
        setRegistered(false);
    }

    private void setRegistered(boolean state) {
        if (registered == state || Client.eventManager == null)
            return;

        registered = state;
        if (state)
            Client.eventManager.register(this);
        else
            Client.eventManager.unregister(this);
    }

    @Override
    public void onWorld(WorldEvent event) {
        clear();
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        long now = System.nanoTime();
        float deltaSeconds = lastFrameNanos == 0L ? 0f : Math.min(0.1f, (now - lastFrameNanos) / 1_000_000_000f);
        lastFrameNanos = now;
        projected = false;

        for (int i = lineCount - 1; i >= 0; i--) {
            Line line = lines[i];
            boolean target = now - line.holdUntilNanos < 0L;
            if (target)
                line.alpha = Math.min(1f, line.alpha + deltaSeconds / FADE_IN_SECONDS);
            else
                line.alpha = Math.max(0f, line.alpha - deltaSeconds / FADE_OUT_SECONDS);
            if (!target && line.alpha <= 0f)
                removeAt(i);
        }

        if (lineCount == 0) {
            clear();
            return;
        }

        if (mc.player == null || !RenderUtil.shouldRenderWorldHudOverlays())
            return;

        float screenWidth = NanoVGManager.getScaledScreenWidth();
        float screenHeight = NanoVGManager.getScaledScreenHeight();
        for (int i = 0; i < lineCount; i++)
            projected |= project(lines[i], screenWidth, screenHeight);
    }

    @Override
    public void onRenderHud(RenderHudEvent event) {
        if (projected && lineCount > 0 && RenderUtil.shouldRenderWorldHudOverlays())
            Client.nanoVgManager.loadWorldAnchored(drawer);
    }

    private boolean project(Line line, float screenWidth, float screenHeight) {
        line.commandCount = 0;
        if (line.alpha <= 0f)
            return false;

        float[] clipPoints = line.clipPoints;
        for (int i = 0; i < line.pointCount; i++) {
            int point = i * 3;
            int offset = i * 3;
            if (RenderUtil.INSTANCE.renderedWorldToClip(line.points[point], line.points[point + 1] + LIFT, line.points[point + 2], clip)) {
                clipPoints[offset] = clip.x;
                clipPoints[offset + 1] = clip.y;
                clipPoints[offset + 2] = clip.w;
            } else {
                clipPoints[offset + 2] = -1f;
            }
        }

        boolean connected = false;
        for (int i = 0; i + 1 < line.pointCount; i++) {
            int from = i * 3;
            int to = from + 3;
            float fromX = clipPoints[from], fromY = clipPoints[from + 1], fromW = clipPoints[from + 2];
            float toX = clipPoints[to], toY = clipPoints[to + 1], toW = clipPoints[to + 2];
            if (fromW < NEAR_W && toW < NEAR_W) {
                connected = false;
                continue;
            }

            boolean fromClipped = fromW < NEAR_W;
            if (fromClipped) {
                float t = (NEAR_W - fromW) / (toW - fromW);
                fromX += (toX - fromX) * t;
                fromY += (toY - fromY) * t;
                fromW = NEAR_W;
            }
            boolean toClipped = toW < NEAR_W;
            if (toClipped) {
                float t = (NEAR_W - toW) / (fromW - toW);
                toX += (fromX - toX) * t;
                toY += (fromY - toY) * t;
                toW = NEAR_W;
            }

            if (!connected || fromClipped)
                line.addCommand(true, toScreenX(fromX, fromW, screenWidth), toScreenY(fromY, fromW, screenHeight));
            line.addCommand(false, toScreenX(toX, toW, screenWidth), toScreenY(toY, toW, screenHeight));
            connected = !toClipped;
        }

        return line.commandCount > 0;
    }

    private static float toScreenX(float clipX, float clipW, float screenWidth) {
        return (clipX / clipW * 0.5f + 0.5f) * screenWidth;
    }

    private static float toScreenY(float clipY, float clipW, float screenHeight) {
        return (0.5f - clipY / clipW * 0.5f) * screenHeight;
    }

    private void draw(NVGU vg) {
        if (lineCount == 0)
            return;

        long handle = vg.getHandle();
        nvgSave(handle);
        nvgLineJoin(handle, NVG_ROUND);
        nvgLineCap(handle, NVG_BUTT);

        for (int i = 0; i < lineCount; i++)
            drawLine(vg, handle, lines[i], lines[i].color.getAlpha() / 255f * lines[i].alpha);

        nvgRestore(handle);
    }

    private void drawLine(NVGU vg, long handle, Line line, float opacity) {
        if (opacity <= 0f)
            return;

        if (line.commandCount > 0) {
            nvgBeginPath(handle);
            float[] commands = line.commands;
            for (int i = 0; i < line.commandCount; i++) {
                int offset = i * 2;
                if (line.moveCommands[i])
                    nvgMoveTo(handle, commands[offset], commands[offset + 1]);
                else
                    nvgLineTo(handle, commands[offset], commands[offset + 1]);
            }
            nvgStrokeWidth(handle, GLOW_WIDTH);
            nvgStrokeColor(handle, vg.createAndStoreColour(line.color).a(GLOW_ALPHA * opacity));
            nvgStroke(handle);
            nvgStrokeWidth(handle, EDGE_WIDTH);
            nvgStrokeColor(handle, vg.createAndStoreColour(line.color).a(EDGE_ALPHA * opacity));
            nvgStroke(handle);
        }
    }

    private static final class Line {
        private Object owner;
        private Object key;
        private Color color = RenderColors.DEFAULT;
        private long holdUntilNanos;
        private float alpha;
        private int pointCount;
        private final double[] points = new double[MAX_POINTS * 3];
        private final float[] clipPoints = new float[MAX_POINTS * 3];
        private int commandCount;
        private final boolean[] moveCommands = new boolean[MAX_POINTS * 2];
        private final float[] commands = new float[MAX_POINTS * 4];

        private void add(Vec3 position) {
            if (position == null || !Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z))
                return;

            int offset = pointCount * 3;
            if (pointCount > 0
                    && points[offset - 3] == position.x
                    && points[offset - 2] == position.y
                    && points[offset - 1] == position.z)
                return;

            points[offset] = position.x;
            points[offset + 1] = position.y;
            points[offset + 2] = position.z;
            pointCount++;
        }

        private void addCommand(boolean move, float x, float y) {
            moveCommands[commandCount] = move;
            commands[commandCount * 2] = x;
            commands[commandCount * 2 + 1] = y;
            commandCount++;
        }
    }
}
