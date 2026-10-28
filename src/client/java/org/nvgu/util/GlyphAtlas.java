package org.nvgu.util;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;

import static org.lwjgl.stb.STBTruetype.*;

public final class GlyphAtlas {
    private static final int SIZE = 1024;
    private static final int PADDING = 1;
    private static final Glyph MISSING = new Glyph(0, 0, 0, 0, 0f, 0f, 0f, 0f);

    private final Long2ObjectOpenHashMap<Glyph> glyphs = new Long2ObjectOpenHashMap<>();
    private final Map<STBTTFontinfo, Integer> fontIds = new IdentityHashMap<>();
    private final ArrayList<PendingUpload> pendingUploads = new ArrayList<>();
    private int texture;
    private boolean textureCleared;
    private int cursorX = PADDING;
    private int cursorY = PADDING;
    private int rowHeight;
    private boolean full;

    public Glyph glyph(STBTTFontinfo font, float scale, float pixelSize, int codePoint) {
        int fontId = fontIds.computeIfAbsent(font, ignored -> fontIds.size());
        long key = (long) fontId << 56 ^ (long) Float.floatToIntBits(pixelSize) << 24 ^ codePoint;
        Glyph cached = glyphs.get(key);
        if (cached != null)
            return cached == MISSING ? null : cached;

        int glyphIndex = stbtt_FindGlyphIndex(font, codePoint);
        if (glyphIndex == 0) {
            glyphs.put(key, MISSING);
            return null;
        }

        Glyph glyph;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer x0 = stack.mallocInt(1);
            IntBuffer y0 = stack.mallocInt(1);
            IntBuffer x1 = stack.mallocInt(1);
            IntBuffer y1 = stack.mallocInt(1);
            stbtt_GetGlyphBitmapBox(font, glyphIndex, scale, scale, x0, y0, x1, y1);
            int width = x1.get(0) - x0.get(0);
            int height = y1.get(0) - y0.get(0);
            if (width <= 0 || height <= 0) {
                glyph = new Glyph(x0.get(0), y0.get(0), 0, 0, 0f, 0f, 0f, 0f);
            } else {
                if (!reserve(width, height)) {
                    full = true;
                    return null;
                }

                ByteBuffer pixels = MemoryUtil.memAlloc(width * height);
                stbtt_MakeGlyphBitmap(font, pixels, width, height, width, scale, scale, glyphIndex);
                pendingUploads.add(new PendingUpload(cursorX, cursorY, width, height, pixels));
                glyph = new Glyph(x0.get(0), y0.get(0), width, height,
                        cursorX / (float) SIZE, cursorY / (float) SIZE,
                        (cursorX + width) / (float) SIZE, (cursorY + height) / (float) SIZE);
                cursorX += width + PADDING;
            }
        }

        glyphs.put(key, glyph);
        return glyph;
    }

    public boolean isFull() {
        return full;
    }

    public void reset() {
        glyphs.clear();
        freePendingUploads();
        cursorX = PADDING;
        cursorY = PADDING;
        rowHeight = 0;
        textureCleared = false;
        full = false;
    }

    public int bindForDraw() {
        if (texture == 0) {
            texture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R8, SIZE, SIZE, 0, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            textureCleared = false;
        }

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        if (!textureCleared || !pendingUploads.isEmpty())
            uploadPending();
        return texture;
    }

    public void destroy() {
        freePendingUploads();
        glyphs.clear();
        fontIds.clear();
        if (texture != 0) {
            GL11.glDeleteTextures(texture);
            texture = 0;
        }
    }

    private boolean reserve(int width, int height) {
        if (width + PADDING * 2 > SIZE || height + PADDING * 2 > SIZE)
            return false;

        if (cursorX + width + PADDING > SIZE) {
            cursorX = PADDING;
            cursorY += rowHeight + PADDING;
            rowHeight = 0;
        }
        if (cursorY + height + PADDING > SIZE)
            return false;

        rowHeight = Math.max(rowHeight, height);
        return true;
    }

    private void uploadPending() {
        int previousAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        int previousRowLength = GL11.glGetInteger(GL12.GL_UNPACK_ROW_LENGTH);
        int previousSkipRows = GL11.glGetInteger(GL12.GL_UNPACK_SKIP_ROWS);
        int previousSkipPixels = GL11.glGetInteger(GL12.GL_UNPACK_SKIP_PIXELS);
        int previousUnpackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        try {
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL12.GL_UNPACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_PIXELS, 0);
            if (!textureCleared) {
                ByteBuffer zero = MemoryUtil.memCalloc(SIZE * SIZE);
                try {
                    GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, SIZE, SIZE, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, zero);
                } finally {
                    MemoryUtil.memFree(zero);
                }
                textureCleared = true;
            }
            for (PendingUpload upload : pendingUploads)
                GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, upload.x, upload.y, upload.width, upload.height, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, upload.pixels);
        } finally {
            freePendingUploads();
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, previousAlignment);
            GL11.glPixelStorei(GL12.GL_UNPACK_ROW_LENGTH, previousRowLength);
            GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_ROWS, previousSkipRows);
            GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_PIXELS, previousSkipPixels);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, previousUnpackBuffer);
        }
    }

    private void freePendingUploads() {
        for (PendingUpload upload : pendingUploads)
            MemoryUtil.memFree(upload.pixels);
        pendingUploads.clear();
    }

    public record Glyph(int offsetX, int offsetY, int width, int height, float u0, float v0, float u1, float v1) {
        public boolean empty() {
            return width <= 0 || height <= 0;
        }
    }

    private record PendingUpload(int x, int y, int width, int height, ByteBuffer pixels) {
    }
}
