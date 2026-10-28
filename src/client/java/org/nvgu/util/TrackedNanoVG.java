package org.nvgu.util;

import com.instrumentalist.krs.utils.render.GraphicsApiCompatibility;
import org.lwjgl.nanovg.NanoVG;

import java.nio.ByteBuffer;

/**
 * Drop-in replacements for the NanoVG calls that build geometry or put pixels
 * on screen. While a graphics compatibility layer is rendered they report a
 * conservative frame-space bound of every fill, stroke and text run, so only
 * that part of the layer has to be transferred to the non-OpenGL backend.
 * Outside a layer they only forward to NanoVG.
 *
 * <p>Every path command used by NVGU must be wrapped here. A fill or stroke
 * without tracked geometry marks the whole layer instead.</p>
 */
public final class TrackedNanoVG {
    // NanoVG's default miter limit; NVGU never changes it.
    private static final float MITER_LIMIT = 10f;
    // Covers NanoVG's anti-aliasing fringe.
    private static final float FRINGE_PADDING = 2f;

    private static final float[] XFORM = new float[6];
    private static final float[] TEXT_BOUNDS = new float[4];
    private static float frameWidth = 1f;
    private static float frameHeight = 1f;
    private static float maxStrokeWidth = 1f;
    private static float maxFontBlur;
    private static boolean pathEmpty = true;
    private static float pathMinX;
    private static float pathMinY;
    private static float pathMaxX;
    private static float pathMaxY;

    private TrackedNanoVG() {
    }

    public static void nvgBeginFrame(long ctx, float windowWidth, float windowHeight, float devicePixelRatio) {
        // NanoVG resets its state stack here, so the tracked maxima start over too.
        frameWidth = Math.max(1f, windowWidth);
        frameHeight = Math.max(1f, windowHeight);
        maxStrokeWidth = 1f;
        maxFontBlur = 0f;
        pathEmpty = true;
        NanoVG.nvgBeginFrame(ctx, windowWidth, windowHeight, devicePixelRatio);
    }

    public static void nvgBeginPath(long ctx) {
        pathEmpty = true;
        NanoVG.nvgBeginPath(ctx);
    }

    public static void nvgMoveTo(long ctx, float x, float y) {
        if (GraphicsApiCompatibility.isRegionTrackingActive()) {
            NanoVG.nvgCurrentTransform(ctx, XFORM);
            includePathPoint(x, y);
        }
        NanoVG.nvgMoveTo(ctx, x, y);
    }

    public static void nvgLineTo(long ctx, float x, float y) {
        if (GraphicsApiCompatibility.isRegionTrackingActive()) {
            NanoVG.nvgCurrentTransform(ctx, XFORM);
            includePathPoint(x, y);
        }
        NanoVG.nvgLineTo(ctx, x, y);
    }

    public static void nvgRect(long ctx, float x, float y, float w, float h) {
        if (GraphicsApiCompatibility.isRegionTrackingActive())
            includePathRect(ctx, x, y, x + w, y + h);
        NanoVG.nvgRect(ctx, x, y, w, h);
    }

    public static void nvgRoundedRectVarying(long ctx, float x, float y, float w, float h,
                                             float radTopLeft, float radTopRight, float radBottomRight, float radBottomLeft) {
        if (GraphicsApiCompatibility.isRegionTrackingActive())
            includePathRect(ctx, x, y, x + w, y + h);
        NanoVG.nvgRoundedRectVarying(ctx, x, y, w, h, radTopLeft, radTopRight, radBottomRight, radBottomLeft);
    }

    public static void nvgCircle(long ctx, float cx, float cy, float r) {
        if (GraphicsApiCompatibility.isRegionTrackingActive())
            includePathRect(ctx, cx - r, cy - r, cx + r, cy + r);
        NanoVG.nvgCircle(ctx, cx, cy, r);
    }

    public static void nvgStrokeWidth(long ctx, float size) {
        // A maximum survives nvgSave/nvgRestore, which this class cannot see.
        maxStrokeWidth = Math.max(maxStrokeWidth, size);
        NanoVG.nvgStrokeWidth(ctx, size);
    }

    public static void nvgFontBlur(long ctx, float blur) {
        maxFontBlur = Math.max(maxFontBlur, blur);
        NanoVG.nvgFontBlur(ctx, blur);
    }

    public static void nvgFill(long ctx) {
        if (GraphicsApiCompatibility.isRegionTrackingActive())
            markPath(FRINGE_PADDING);
        NanoVG.nvgFill(ctx);
    }

    public static void nvgStroke(long ctx) {
        if (GraphicsApiCompatibility.isRegionTrackingActive()) {
            NanoVG.nvgCurrentTransform(ctx, XFORM);
            float strokeWidth = maxStrokeWidth * averageScale();
            // Miter joins reach at most half the stroke width times the miter limit.
            markPath(strokeWidth * 0.5f * MITER_LIMIT + FRINGE_PADDING);
        }
        NanoVG.nvgStroke(ctx);
    }

    public static float nvgText(long ctx, float x, float y, CharSequence string) {
        if (GraphicsApiCompatibility.isRegionTrackingActive()) {
            NanoVG.nvgTextBounds(ctx, x, y, string, TEXT_BOUNDS);
            markText(ctx);
        }
        return NanoVG.nvgText(ctx, x, y, string);
    }

    public static float nvgText(long ctx, float x, float y, ByteBuffer string) {
        if (GraphicsApiCompatibility.isRegionTrackingActive()) {
            NanoVG.nvgTextBounds(ctx, x, y, string, TEXT_BOUNDS);
            markText(ctx);
        }
        return NanoVG.nvgText(ctx, x, y, string);
    }

    public static void markTextRegion(long ctx, float x, float y, ByteBuffer string, float shadowOffsetX, float shadowOffsetY) {
        if (!GraphicsApiCompatibility.isRegionTrackingActive())
            return;

        NanoVG.nvgTextBounds(ctx, x, y, string, TEXT_BOUNDS);
        TEXT_BOUNDS[0] += Math.min(0f, shadowOffsetX);
        TEXT_BOUNDS[1] += Math.min(0f, shadowOffsetY);
        TEXT_BOUNDS[2] += Math.max(0f, shadowOffsetX);
        TEXT_BOUNDS[3] += Math.max(0f, shadowOffsetY);
        markText(ctx);
    }

    public static float nvgTextUntracked(long ctx, float x, float y, ByteBuffer string) {
        return NanoVG.nvgText(ctx, x, y, string);
    }

    public static void nvgTextBox(long ctx, float x, float y, float breakRowWidth, CharSequence string) {
        if (GraphicsApiCompatibility.isRegionTrackingActive()) {
            NanoVG.nvgTextBoxBounds(ctx, x, y, breakRowWidth, string, TEXT_BOUNDS);
            markText(ctx);
        }
        NanoVG.nvgTextBox(ctx, x, y, breakRowWidth, string);
    }

    private static void includePathRect(long ctx, float minX, float minY, float maxX, float maxY) {
        NanoVG.nvgCurrentTransform(ctx, XFORM);
        includePathPoint(minX, minY);
        includePathPoint(maxX, minY);
        includePathPoint(minX, maxY);
        includePathPoint(maxX, maxY);
    }

    private static void includePathPoint(float x, float y) {
        float transformedX = XFORM[0] * x + XFORM[2] * y + XFORM[4];
        float transformedY = XFORM[1] * x + XFORM[3] * y + XFORM[5];
        if (pathEmpty) {
            pathMinX = pathMaxX = transformedX;
            pathMinY = pathMaxY = transformedY;
            pathEmpty = false;
            return;
        }
        pathMinX = Math.min(pathMinX, transformedX);
        pathMinY = Math.min(pathMinY, transformedY);
        pathMaxX = Math.max(pathMaxX, transformedX);
        pathMaxY = Math.max(pathMaxY, transformedY);
    }

    private static void markPath(float padding) {
        if (pathEmpty) {
            // Geometry from a path command this class does not wrap.
            GraphicsApiCompatibility.markWholeLayer();
            return;
        }
        GraphicsApiCompatibility.markLayerRegion(frameWidth, frameHeight,
                pathMinX - padding, pathMinY - padding, pathMaxX + padding, pathMaxY + padding);
    }

    private static void markText(long ctx) {
        float minX = TEXT_BOUNDS[0];
        float minY = TEXT_BOUNDS[1];
        float maxX = TEXT_BOUNDS[2];
        float maxY = TEXT_BOUNDS[3];
        // The bounds follow the font's line metrics; accents and blurred glyph
        // quads can reach past them.
        float padding = (maxY - minY) * 0.5f + maxFontBlur * 2f + FRINGE_PADDING;

        NanoVG.nvgCurrentTransform(ctx, XFORM);
        boolean previousPathEmpty = pathEmpty;
        float previousMinX = pathMinX;
        float previousMinY = pathMinY;
        float previousMaxX = pathMaxX;
        float previousMaxY = pathMaxY;
        pathEmpty = true;
        includePathPoint(minX - padding, minY - padding);
        includePathPoint(maxX + padding, minY - padding);
        includePathPoint(minX - padding, maxY + padding);
        includePathPoint(maxX + padding, maxY + padding);
        markPath(FRINGE_PADDING);

        // Text does not consume the current path.
        pathEmpty = previousPathEmpty;
        pathMinX = previousMinX;
        pathMinY = previousMinY;
        pathMaxX = previousMaxX;
        pathMaxY = previousMaxY;
    }

    private static float averageScale() {
        float scaleX = (float) Math.sqrt(XFORM[0] * XFORM[0] + XFORM[2] * XFORM[2]);
        float scaleY = (float) Math.sqrt(XFORM[1] * XFORM[1] + XFORM[3] * XFORM[3]);
        return Math.max(scaleX, scaleY);
    }
}
