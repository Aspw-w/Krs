package com.instrumentalist.krs.utils.render;

import org.nvgu.NVGU;
import org.nvgu.util.Border;
import org.nvgu.util.LinearGradientDirection;

import java.awt.Color;

/**
 * Shared visual language for NanoVG surfaces.
 * Primary panels receive a restrained outline and deeper shadow, while compact
 * surfaces stay flat so nested UI does not turn into a stack of glowing cards.
 */
public final class NanoVGTheme {
    private static final float BLUR_RADIUS = 7f;
    private static final float BLUR_ALPHA = 0.84f;
    private static final Color PANEL_TOP = new Color(18, 24, 31, 82);
    private static final Color PANEL_BOTTOM = new Color(8, 12, 18, 100);
    private static final Color PANEL_BORDER_TOP = new Color(255, 255, 255, 44);
    private static final Color PANEL_BORDER_BOTTOM = new Color(255, 255, 255, 14);
    private static final Color PANEL_SHADOW = new Color(0, 0, 0, 102);
    private static final Color COMPACT_SHADOW = new Color(0, 0, 0, 68);

    public static final Color COMPACT_BACKGROUND = new Color(10, 15, 21, 96);

    private NanoVGTheme() {
    }

    public static void renderPanelEffects(NVGU vg, float x, float y, float width, float height,
                                          float radius, float alpha) {
        float opacity = opacity(alpha);
        if (!isDrawable(width, height, opacity))
            return;

        vg.blurRoundedRectangle(x, y, width, height, radius, BLUR_RADIUS, BLUR_ALPHA * opacity);
        vg.shadowRoundedRectangle(
                x, y, width, height, radius,
                12f, 1f, 0f, 3f,
                scaledAlpha(PANEL_SHADOW, opacity)
        );
    }

    public static void renderCompactEffects(NVGU vg, float x, float y, float width, float height,
                                            float radius, float alpha) {
        float opacity = opacity(alpha);
        if (!isDrawable(width, height, opacity))
            return;

        vg.blurRoundedRectangle(x, y, width, height, radius, BLUR_RADIUS, BLUR_ALPHA * opacity);
        vg.shadowRoundedRectangle(
                x, y, width, height, radius,
                8f, 0f, 0f, 2f,
                scaledAlpha(COMPACT_SHADOW, opacity)
        );
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
        vg.roundedRectangleBorder(
                x, y, width, height, radius, 1f,
                vg.linearGradient(
                        x, y, width, height, feather,
                        scaledAlpha(PANEL_BORDER_TOP, opacity),
                        scaledAlpha(PANEL_BORDER_BOTTOM, opacity),
                        LinearGradientDirection.TOP_TO_BOTTOM
                ),
                Border.INSIDE
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

    public static void renderBackdropTint(NVGU vg, float width, float height, float alpha) {
        float opacity = opacity(alpha);
        if (!isDrawable(width, height, opacity))
            return;

        vg.rectangle(
                0f, 0f, width, height,
                vg.linearGradient(
                        0f, 0f, width, height, Math.max(1f, height),
                        scaledAlpha(new Color(6, 10, 16, 30), opacity),
                        scaledAlpha(new Color(3, 6, 10, 82), opacity),
                        LinearGradientDirection.TOP_TO_BOTTOM
                )
        );
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
