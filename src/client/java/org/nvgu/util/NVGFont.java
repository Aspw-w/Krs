package org.nvgu.util;

import org.nvgu.NVGU;

import java.awt.*;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.lwjgl.nanovg.NanoVG.*;
import static org.nvgu.util.TrackedNanoVG.nvgBeginPath;
import static org.nvgu.util.TrackedNanoVG.nvgFontBlur;
import static org.nvgu.util.TrackedNanoVG.nvgTextBox;

public class NVGFont {

    public static final float SHADOW_OFFSET = 2f;
    private static final int WIDTH_CACHE_SIZE = 512;
    private static final int HEIGHT_CACHE_SIZE = 32;

    private final String identifier, path;
    private final float[] textBoxBounds = new float[4];
    private final String[] widthCacheText = new String[WIDTH_CACHE_SIZE];
    private final int[] widthCacheSize = new int[WIDTH_CACHE_SIZE];
    private final float[] widthCacheValue = new float[WIDTH_CACHE_SIZE];
    private final int[] heightCacheSize = new int[HEIGHT_CACHE_SIZE];
    private final float[] heightCacheValue = new float[HEIGHT_CACHE_SIZE];
    private final boolean[] heightCacheUsed = new boolean[HEIGHT_CACHE_SIZE];
    private ByteBuffer buffer;

    public NVGFont(String identifier, String path) {
        this.identifier = identifier;
        this.path = path;
    }

    public void drawText(String text, final float x, final float y, float size, Color color, Alignment alignment, boolean fontShadow) {
        NVGU vg = NVGU.INSTANCE;

        if (fontShadow)
            vg.textWithShadow(text, x, y, color, identifier, size, alignment, SHADOW_OFFSET, SHADOW_OFFSET);
        else
            vg.text(text, x, y, color, identifier, size, alignment);
    }

    public void drawGradientText(String text, final float x, final float y, float size, Alignment alignment, boolean fontShadow, NVGU.GlyphColour colours) {
        NVGU vg = NVGU.INSTANCE;

        if (fontShadow)
            vg.gradientTextWithShadow(text, x, y, identifier, size, alignment, colours, SHADOW_OFFSET, SHADOW_OFFSET);
        else
            vg.gradientText(text, x, y, identifier, size, alignment, colours);
    }

    public void drawTextBox(String text, final float x, final float y, float width, float size, Color color, Alignment alignment) {
        NVGU vg = NVGU.INSTANCE;

        String strippedText = NVGU.stripMinecraftFormatting(vg.filterRenderableText(text, identifier));
        if (strippedText == null || strippedText.isEmpty()) return;

        nvgBeginPath(vg.getHandle());

        nvgFillColor(vg.getHandle(), vg.createAndStoreColour(color));
        nvgFontFace(vg.getHandle(), identifier);
        nvgFontSize(vg.getHandle(), size);
        nvgTextAlign(vg.getHandle(), alignment.getTextAlignment());
        //noinspection DataFlowIssue
        nvgTextBox(vg.getHandle(), x, y + 1, width, strippedText);

        nvgClosePath(vg.getHandle());
    }

    public void drawTextBlur(String text, final float x, final float y, float size, Color color, float fontBlur, Alignment alignment) {
        NVGU vg = NVGU.INSTANCE;
        vg.save();
        try {
            nvgFontBlur(vg.getHandle(), fontBlur);
            vg.text(text, x, y - 1, color, identifier, size, alignment);
        } finally {
            vg.restore();
        }
    }

    public float getHeight(float size) {
        int sizeBits = Float.floatToIntBits(size);
        int index = spreadHash(sizeBits) & (HEIGHT_CACHE_SIZE - 1);
        if (heightCacheUsed[index] && heightCacheSize[index] == sizeBits)
            return heightCacheValue[index];

        float height = NVGU.INSTANCE.textHeight(identifier, size);
        heightCacheSize[index] = sizeBits;
        heightCacheValue[index] = height;
        heightCacheUsed[index] = true;
        return height;
    }

    public float getBoxHeight(String text, float width, float size) {
        NVGU vg = NVGU.INSTANCE;

        String strippedText = NVGU.stripMinecraftFormatting(vg.filterRenderableText(text, identifier));
        if (strippedText == null || strippedText.isEmpty()) return 0f;

        vg.save();
        try {
            nvgFontFace(vg.getHandle(), identifier);
            nvgFontSize(vg.getHandle(), size);
            //noinspection DataFlowIssue
            nvgTextBoxBounds(vg.getHandle(), 0, 0, width, strippedText, textBoxBounds);
            return textBoxBounds[3];
        } finally {
            vg.restore();
        }
    }

    public float getWidth(String text, float size) {
        if (text == null || text.isEmpty()) return 0f;

        int sizeBits = Float.floatToIntBits(size);
        int index = spreadHash(31 * text.hashCode() + sizeBits) & (WIDTH_CACHE_SIZE - 1);
        String cachedText = widthCacheText[index];
        if (cachedText != null && widthCacheSize[index] == sizeBits && cachedText.equals(text))
            return widthCacheValue[index];

        float width = NVGU.INSTANCE.textWidth(text, identifier, size);
        widthCacheSize[index] = sizeBits;
        widthCacheValue[index] = width;
        widthCacheText[index] = text;
        return width;
    }

    private static int spreadHash(int value) {
        return value ^ (value >>> 16);
    }

    private void clearMetricCaches() {
        Arrays.fill(widthCacheText, null);
        Arrays.fill(heightCacheUsed, false);
    }

    //getters and setters
    public String getIdentifier() {
        return identifier;
    }

    public String getPath() {
        return path;
    }

    public ByteBuffer getBuffer() {
        return buffer;
    }

    public void setBuffer(ByteBuffer buffer) {
        this.buffer = buffer;
        clearMetricCaches();
    }
}
