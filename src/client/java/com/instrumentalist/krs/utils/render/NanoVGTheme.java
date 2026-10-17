package com.instrumentalist.krs.utils.render;

import org.nvgu.NVGU;
import org.nvgu.util.LinearGradientDirection;

import java.awt.Color;
import java.util.List;

public final class NanoVGTheme {
    public static final Color BASE = new Color(0x16, 0x16, 0x16);
    public static final Color TEXT = new Color(0xF6, 0xF6, 0xF1);
    public static final Color MUTED = new Color(0xC8, 0xC8, 0xC2);
    // Softer system-style hues; fully saturated neon reads as a sticker on top of the glass.
    public static final Color DANGER = new Color(0xFF, 0x45, 0x3A);
    public static final Color WARNING = new Color(0xFF, 0x9F, 0x0A);
    public static final Color ACCENT = new Color(0x64, 0xD2, 0xFF);
    // One radius scale for every surface: labels < HUD/controls < panels < large menus.
    public static final float RADIUS_LABEL = 6f;
    public static final float RADIUS_HUD = 8f;
    public static final float RADIUS_PANEL = 12f;
    public static final float RADIUS_MENU = 16f;
    public static final float RADIUS_CONTROL = RADIUS_HUD;
    private static final Shader2DRenderer.GlassRequest PANEL_GLASS = Shader2DRenderer.GlassRequest.panel();
    private static final Shader2DRenderer.GlassRequest COMPACT_GLASS = Shader2DRenderer.GlassRequest.compact();
    private static final Shader2DRenderer.GlassRequest CONTROL_GLASS = Shader2DRenderer.GlassRequest.control();
    private static final Shader2DRenderer.GlassRequest CONTROL_DARK_GLASS = Shader2DRenderer.GlassRequest.controlDark();
    private static final Color PANEL_TOP = new Color(0x16, 0x16, 0x16, 64);
    private static final Color PANEL_BOTTOM = new Color(0x16, 0x16, 0x16, 92);

    public static final Color COMPACT_BACKGROUND = new Color(0x16, 0x16, 0x16, 78);
    public static final Color SWITCH_ON = new Color(0x30, 0xD1, 0x58);
    public static final Color SLIDER_FILL = ACCENT;
    public static final Color INPUT_FOCUS = new Color(0xEE, 0xF2, 0xF7);
    public static final Color SCROLLBAR_THUMB = new Color(0xEE, 0xF2, 0xF7);
    public static final Color CONTROL_TRACK = new Color(255, 255, 255, 190);
    public static final Color LIST_CHIP = new Color(255, 255, 255, 210);
    public static final Color KNOB_FILL = Color.WHITE;
    private static final float[] CONNECTED_BOXES = new float[256];
    private static final float[] CONNECTED_RADII = new float[256];
    private static final float[] CORNER_SCRATCH = new float[4];

    private NanoVGTheme() {
    }

    public static void renderPanelEffects(NVGU vg, float x, float y, float width, float height,
                                          float radius, float alpha) {
        float opacity = opacity(alpha);
        if (!isDrawable(width, height, opacity))
            return;

        vg.liquidGlassRoundedRectangle(x, y, width, height, radius, opacity, PANEL_GLASS);
    }

    public static void renderCompactEffects(NVGU vg, float x, float y, float width, float height,
                                            float radius, float alpha) {
        float opacity = opacity(alpha);
        if (!isDrawable(width, height, opacity))
            return;

        vg.liquidGlassRoundedRectangle(x, y, width, height, radius, opacity, COMPACT_GLASS);
    }

    public static void renderControlEffects(NVGU vg, float x, float y, float width, float height,
                                            float radius, float alpha) {
        renderControlEffects(vg, x, y, width, height, radius, alpha, 0f);
    }

    public static void renderControlEffects(NVGU vg, float x, float y, float width, float height,
                                            float radius, float alpha, float brightness) {
        float opacity = opacity(alpha);
        if (!isDrawable(width, height, opacity))
            return;

        CONTROL_GLASS.brightness = brightness;
        vg.liquidGlassRoundedRectangle(x, y, width, height, radius, opacity, CONTROL_GLASS);
    }

    public static void renderControlDarkEffects(NVGU vg, float x, float y, float width, float height,
                                                float radius, float alpha) {
        float opacity = opacity(alpha);
        if (!isDrawable(width, height, opacity))
            return;

        vg.liquidGlassRoundedRectangle(x, y, width, height, radius, opacity, CONTROL_DARK_GLASS);
    }

    public static void renderConnectedEffects(NVGU vg, List<? extends ConnectedGlassRect> entries, float radius, float alpha) {
        int written = packConnected(entries, radius);
        if (written == 0)
            return;

        float opacity = opacity(alpha);
        if (opacity <= 0.001f)
            return;

        vg.liquidGlassConnectedRoundedRectangles(CONNECTED_BOXES, CONNECTED_RADII, written, opacity, COMPACT_GLASS);
    }

    public static void renderConnected(NVGU vg, List<? extends ConnectedGlassRect> entries, float radius, float alpha) {
        if (entries == null || entries.isEmpty())
            return;

        float opacity = opacity(alpha);
        if (opacity <= 0.001f)
            return;

        for (int i = 0, n = entries.size(); i < n; i++) {
            ConnectedGlassRect entry = entries.get(i);
            if (entry == null || entry.width() <= 0f || entry.height() <= 0f)
                continue;

            writeCorners(entries, i, radius, CORNER_SCRATCH);
            renderCompact(
                    vg,
                    entry.x(),
                    entry.y(),
                    entry.width(),
                    entry.height(),
                    CORNER_SCRATCH[0],
                    CORNER_SCRATCH[1],
                    CORNER_SCRATCH[2],
                    CORNER_SCRATCH[3],
                    opacity
            );
        }
    }

    private static int packConnected(List<? extends ConnectedGlassRect> entries, float radius) {
        if (entries == null || entries.isEmpty())
            return 0;

        int written = 0;
        int limit = Math.min(entries.size(), CONNECTED_BOXES.length / 4);
        for (int i = 0; i < limit; i++) {
            ConnectedGlassRect entry = entries.get(i);
            if (entry == null || entry.width() <= 0f || entry.height() <= 0f)
                continue;

            int offset = written * 4;
            CONNECTED_BOXES[offset] = entry.x();
            CONNECTED_BOXES[offset + 1] = entry.y();
            CONNECTED_BOXES[offset + 2] = entry.width();
            CONNECTED_BOXES[offset + 3] = entry.height();
            writeCorners(entries, i, radius, CORNER_SCRATCH);
            CONNECTED_RADII[offset] = CORNER_SCRATCH[0];
            CONNECTED_RADII[offset + 1] = CORNER_SCRATCH[1];
            CONNECTED_RADII[offset + 2] = CORNER_SCRATCH[2];
            CONNECTED_RADII[offset + 3] = CORNER_SCRATCH[3];
            written++;
        }
        return written;
    }

    private static void writeCorners(List<? extends ConnectedGlassRect> entries, int index, float radius, float[] output) {
        ConnectedGlassRect entry = entries.get(index);
        if (entry.hasExplicitCorners()) {
            output[0] = entry.topLeftRadius();
            output[1] = entry.topRightRadius();
            output[2] = entry.bottomRightRadius();
            output[3] = entry.bottomLeftRadius();
            return;
        }

        ConnectedGlassRect previous = index > 0 ? entries.get(index - 1) : null;
        ConnectedGlassRect next = index + 1 < entries.size() ? entries.get(index + 1) : null;
        float left = entry.x();
        float right = entry.x() + entry.width();
        output[0] = exposedCornerRadius(previous, left, radius);
        output[1] = exposedCornerRadius(previous, right, radius);
        output[2] = exposedCornerRadius(next, right, radius);
        output[3] = exposedCornerRadius(next, left, radius);
    }

    // A corner that sticks out past its neighbour by less than the radius can only round off by that
    // much; a full radius would cut a notch into the seam between the two rows.
    private static float exposedCornerRadius(ConnectedGlassRect neighbor, float cornerX, float radius) {
        if (neighbor == null)
            return radius;

        float neighborLeft = neighbor.x();
        float neighborRight = neighbor.x() + neighbor.width();
        if (cornerX >= neighborLeft - 0.5f && cornerX <= neighborRight + 0.5f)
            return 0f;

        float exposedWidth = cornerX < neighborLeft ? neighborLeft - cornerX : cornerX - neighborRight;
        return Math.min(radius, exposedWidth);
    }

    public interface ConnectedGlassRect {
        float x();

        float y();

        float width();

        float height();

        default boolean hasExplicitCorners() {
            return false;
        }

        default float topLeftRadius() {
            return 0f;
        }

        default float topRightRadius() {
            return 0f;
        }

        default float bottomRightRadius() {
            return 0f;
        }

        default float bottomLeftRadius() {
            return 0f;
        }
    }

    public static void renderPanel(NVGU vg, float x, float y, float width, float height,
                                   float radius, float alpha) {
        renderPanel(vg, x, y, width, height, radius, alpha, 0);
    }

    public static void renderPanel(NVGU vg, float x, float y, float width, float height,
                                   float radius, float alpha, int backgroundAlphaOffset) {
        float opacity = opacity(alpha);
        if (!isDrawable(width, height, opacity))
            return;

        float feather = Math.max(1f, height);
        vg.roundedRectangle(
                x, y, width, height, radius,
                vg.linearGradient(
                        x, y, width, height, feather,
                        scaledAlpha(PANEL_TOP, opacity, backgroundAlphaOffset),
                        scaledAlpha(PANEL_BOTTOM, opacity, backgroundAlphaOffset),
                        LinearGradientDirection.TOP_TO_BOTTOM
                )
        );
    }

    public static void renderCompact(NVGU vg, float x, float y, float width, float height,
                                     float radius, float alpha) {
        renderCompact(vg, x, y, width, height, radius, alpha, 0);
    }

    public static void renderCompact(NVGU vg, float x, float y, float width, float height,
                                     float radius, float alpha, int backgroundAlphaOffset) {
        renderCompact(vg, x, y, width, height, radius, radius, radius, radius, alpha, backgroundAlphaOffset);
    }

    public static void renderCompact(NVGU vg, float x, float y, float width, float height,
                                     float topLeft, float topRight, float bottomRight, float bottomLeft,
                                     float alpha) {
        renderCompact(vg, x, y, width, height, topLeft, topRight, bottomRight, bottomLeft, alpha, 0);
    }

    public static void renderCompact(NVGU vg, float x, float y, float width, float height,
                                     float topLeft, float topRight, float bottomRight, float bottomLeft,
                                     float alpha, int backgroundAlphaOffset) {
        float opacity = opacity(alpha);
        if (!isDrawable(width, height, opacity))
            return;

        vg.roundedRectangle(
                x, y, width, height,
                topLeft, topRight, bottomRight, bottomLeft,
                scaledAlpha(COMPACT_BACKGROUND, opacity, backgroundAlphaOffset)
        );
    }

    public static Color accent(int alpha) {
        return new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), Math.clamp(alpha, 0, 255));
    }

    public static Color text(int alpha) {
        return new Color(TEXT.getRed(), TEXT.getGreen(), TEXT.getBlue(), Math.clamp(alpha, 0, 255));
    }

    public static Color muted(int alpha) {
        return new Color(MUTED.getRed(), MUTED.getGreen(), MUTED.getBlue(), Math.clamp(alpha, 0, 255));
    }

    public static Color danger(int alpha) {
        return new Color(DANGER.getRed(), DANGER.getGreen(), DANGER.getBlue(), Math.clamp(alpha, 0, 255));
    }

    public static Color warning(int alpha) {
        return new Color(WARNING.getRed(), WARNING.getGreen(), WARNING.getBlue(), Math.clamp(alpha, 0, 255));
    }

    public static Color switchOn(int alpha) {
        return new Color(SWITCH_ON.getRed(), SWITCH_ON.getGreen(), SWITCH_ON.getBlue(), Math.clamp(alpha, 0, 255));
    }

    public static Color sliderFill(int alpha) {
        return new Color(SLIDER_FILL.getRed(), SLIDER_FILL.getGreen(), SLIDER_FILL.getBlue(), Math.clamp(alpha, 0, 255));
    }

    public static Color inputFocus(int alpha) {
        return new Color(INPUT_FOCUS.getRed(), INPUT_FOCUS.getGreen(), INPUT_FOCUS.getBlue(), Math.clamp(alpha, 0, 255));
    }

    public static Color scrollbarThumb(int alpha) {
        return new Color(SCROLLBAR_THUMB.getRed(), SCROLLBAR_THUMB.getGreen(), SCROLLBAR_THUMB.getBlue(), Math.clamp(alpha, 0, 255));
    }

    public static Color base(int alpha) {
        return new Color(BASE.getRed(), BASE.getGreen(), BASE.getBlue(), Math.clamp(alpha, 0, 255));
    }

    public static Color scaledAlpha(Color color, float alpha) {
        return scaledAlpha(color, alpha, 0);
    }

    public static Color scaledAlpha(Color color, float alpha, int baseAlphaOffset) {
        float opacity = opacity(alpha);
        return new Color(
                color.getRed(),
                color.getGreen(),
                color.getBlue(),
                Math.clamp(Math.round(Math.clamp(color.getAlpha() + baseAlphaOffset, 0, 255) * opacity), 0, 255)
        );
    }

    public static Color offsetAlpha(Color color, int alphaOffset) {
        return new Color(
                color.getRed(),
                color.getGreen(),
                color.getBlue(),
                Math.clamp(color.getAlpha() + alphaOffset, 0, 255)
        );
    }

    private static boolean isDrawable(float width, float height, float alpha) {
        return width > 0f && height > 0f && alpha > 0.001f;
    }

    private static float opacity(float alpha) {
        return Float.isFinite(alpha) ? Math.clamp(alpha, 0f, 1f) : 0f;
    }
}
