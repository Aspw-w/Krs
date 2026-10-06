package com.instrumentalist.krs.utils.render;

import org.nvgu.NVGU;
import org.nvgu.util.LinearGradientDirection;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.List;

public final class NanoVGTheme {
    public static final Color BASE = new Color(0x16, 0x16, 0x16);
    public static Color ACCENT = new Color(0xFF, 0xD0, 0x00);
    private static final Shader2DRenderer.GlassRequest PANEL_GLASS = Shader2DRenderer.GlassRequest.panel();
    private static final Shader2DRenderer.GlassRequest COMPACT_GLASS = Shader2DRenderer.GlassRequest.compact();
    private static final Shader2DRenderer.GlassRequest CONTROL_GLASS = Shader2DRenderer.GlassRequest.control();
    private static final Shader2DRenderer.GlassRequest CONTROL_DARK_GLASS = Shader2DRenderer.GlassRequest.controlDark();
    private static final Color PANEL_TOP = new Color(0x16, 0x16, 0x16, 64);
    private static final Color PANEL_BOTTOM = new Color(0x16, 0x16, 0x16, 92);

    public static final Color COMPACT_BACKGROUND = new Color(0x16, 0x16, 0x16, 78);
    public static final Color SWITCH_ON = new Color(0x2F, 0xD1, 0x6A);
    public static final Color SLIDER_FILL = new Color(0xFF, 0xB3, 0x40);
    public static final Color INPUT_FOCUS = new Color(0xEE, 0xF2, 0xF7);
    public static final Color SCROLLBAR_THUMB = new Color(0xEE, 0xF2, 0xF7);
    public static final Color CONTROL_TRACK = new Color(255, 255, 255, 190);
    public static final Color LIST_CHIP = new Color(255, 255, 255, 210);
    public static final Color KNOB_FILL = Color.WHITE;
    private static final float[] CONNECTED_BOXES = new float[256];
    private static final float[] CONNECTED_RADII = new float[256];
    private static final float[] CORNER_SCRATCH = new float[4];
    private static final int TITLE_ACCENT_BINS = 48;
    private static boolean accentLoaded;

    static {
        loadAccentFromTitle();
    }

    private NanoVGTheme() {
    }

    public static void loadAccentFromTitle() {
        if (accentLoaded)
            return;
        accentLoaded = true;

        try (InputStream stream = NanoVGTheme.class.getClassLoader().getResourceAsStream("assets/krs/title.png")) {
            if (stream == null)
                return;
            BufferedImage image = ImageIO.read(stream);
            if (image == null)
                return;
            Color derived = deriveAccent(image);
            if (derived != null)
                ACCENT = derived;
        } catch (Exception ignored) {
        }
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
        output[0] = coversCorner(previous, left) ? 0f : radius;
        output[1] = coversCorner(previous, right) ? 0f : radius;
        output[2] = coversCorner(next, right) ? 0f : radius;
        output[3] = coversCorner(next, left) ? 0f : radius;
    }

    private static boolean coversCorner(ConnectedGlassRect neighbor, float cornerX) {
        return neighbor != null && cornerX >= neighbor.x() - 0.5f && cornerX <= neighbor.x() + neighbor.width() + 0.5f;
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

    private static Color deriveAccent(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= 0 || height <= 0)
            return null;

        int stepX = Math.max(1, width / 160);
        int stepY = Math.max(1, height / 90);
        float[] weights = new float[TITLE_ACCENT_BINS];
        float[] saturations = new float[TITLE_ACCENT_BINS];
        float[] hsb = new float[3];

        for (int y = 0; y < height; y += stepY) {
            for (int x = 0; x < width; x += stepX) {
                int pixel = image.getRGB(x, y);
                if (((pixel >>> 24) & 0xFF) < 16)
                    continue;

                Color.RGBtoHSB((pixel >> 16) & 0xFF, (pixel >> 8) & 0xFF, pixel & 0xFF, hsb);
                if (hsb[1] < 0.18f || hsb[2] < 0.16f || hsb[2] > 0.97f)
                    continue;

                int bin = Math.min(TITLE_ACCENT_BINS - 1, (int) (hsb[0] * TITLE_ACCENT_BINS));
                float weight = hsb[1] * hsb[1] * hsb[2];
                weights[bin] += weight;
                saturations[bin] += hsb[1] * weight;
            }
        }

        int bestBin = -1;
        float bestScore = 0f;
        for (int i = 0; i < TITLE_ACCENT_BINS; i++) {
            float weight = smoothedWeight(weights, i);
            if (weight <= 0.0001f)
                continue;
            float averageSaturation = saturations[i] / Math.max(weights[i], 0.0001f);
            float score = weight * averageSaturation * averageSaturation;
            if (score > bestScore) {
                bestScore = score;
                bestBin = i;
            }
        }
        if (bestBin < 0)
            return null;

        float red = 0f;
        float green = 0f;
        float blue = 0f;
        float mass = 0f;
        for (int y = 0; y < height; y += stepY) {
            for (int x = 0; x < width; x += stepX) {
                int pixel = image.getRGB(x, y);
                if (((pixel >>> 24) & 0xFF) < 16)
                    continue;

                Color.RGBtoHSB((pixel >> 16) & 0xFF, (pixel >> 8) & 0xFF, pixel & 0xFF, hsb);
                if (hsb[1] < 0.18f || hsb[2] < 0.16f || hsb[2] > 0.97f)
                    continue;

                int bin = Math.min(TITLE_ACCENT_BINS - 1, (int) (hsb[0] * TITLE_ACCENT_BINS));
                if (hueDistance(bin, bestBin) > 1)
                    continue;

                float weight = hsb[1] * hsb[1] * hsb[2];
                red += ((pixel >> 16) & 0xFF) * weight;
                green += ((pixel >> 8) & 0xFF) * weight;
                blue += (pixel & 0xFF) * weight;
                mass += weight;
            }
        }
        if (mass <= 0.0001f)
            return null;

        Color.RGBtoHSB(
                Math.clamp(Math.round(red / mass), 0, 255),
                Math.clamp(Math.round(green / mass), 0, 255),
                Math.clamp(Math.round(blue / mass), 0, 255),
                hsb
        );
        hsb[1] = Math.clamp(Math.max(hsb[1] * 1.55f, 0.82f), 0f, 0.94f);
        hsb[2] = 1f;
        return new Color(Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]));
    }

    private static float smoothedWeight(float[] weights, int index) {
        int previous = (index + TITLE_ACCENT_BINS - 1) % TITLE_ACCENT_BINS;
        int next = (index + 1) % TITLE_ACCENT_BINS;
        return weights[index] + 0.45f * weights[previous] + 0.45f * weights[next];
    }

    private static int hueDistance(int left, int right) {
        int delta = Math.abs(left - right);
        return Math.min(delta, TITLE_ACCENT_BINS - delta);
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
