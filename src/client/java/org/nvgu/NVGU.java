package org.nvgu;

import com.instrumentalist.krs.utils.nanovg.NVGFonts;
import com.instrumentalist.krs.utils.render.Shader2DRenderer;
import org.lwjgl.BufferUtils;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.opengl.GL11;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.system.MemoryUtil;
import org.nvgu.util.*;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.nanovg.NanoVG.*;
import static org.lwjgl.nanovg.NanoVGGL3.*;
import static org.lwjgl.stb.STBTruetype.*;

@SuppressWarnings({"unused", "UnusedReturnValue"})
public class NVGU {

    public static NVGU INSTANCE = new NVGU();
    private static final int FILTER_CACHE_SIZE = 1024;

    private long handle = -1;
    private final List<NVGColor> colourPool = new ArrayList<>();
    private final List<NVGPaint> paintPool = new ArrayList<>();
    private int usedColours;
    private int usedPaints;

    private String currentFont = null;
    private int currentFontSize = -1;
    private Alignment alignment = Alignment.LEFT_TOP;
    private int frameWidth = 1;
    private int frameHeight = 1;
    private float frameDevicePixelRatio = 1f;
    private float currentGlobalAlpha = 1f;
    private boolean frameActive;
    private EffectBatch effectBatch = null;
    private final EffectBatch reusableEffectBatch = new EffectBatch();
    private final float[] effectTransform = new float[6];
    private final float[] shaderTransform = new float[6];
    private final ShaderRect reusableShaderRect = new ShaderRect();
    private final ArrayList<Shader2DRenderer.IndicatorRequest> transformedIndicatorRequests = new ArrayList<>();
    private final ArrayList<Shader2DRenderer.IndicatorRequest> transformedIndicatorRequestPool = new ArrayList<>();
    private final float[] textBounds = new float[4];
    private final float[] textAscender = new float[1];
    private final float[] textDescender = new float[1];
    private final float[] textLineHeight = new float[1];

    // retain all buffers to prevent them being GCed
    private final List<ByteBuffer> bufferRegistry = new ArrayList<>();
    private final List<NVGFont> loadedFonts = new ArrayList<>();
    private final Map<String, Integer> textures = new HashMap<>();
    private final Map<String, Dimension> textureSizes = new HashMap<>();
    private final Map<String, STBTTFontinfo> displayFonts = new HashMap<>();
    private final String[] filterCacheText = new String[FILTER_CACHE_SIZE];
    private final String[] filterCacheFont = new String[FILTER_CACHE_SIZE];
    private final String[] filterCacheResult = new String[FILTER_CACHE_SIZE];

    /**
     * Creates the instance of NanoVG
     */
    public NVGU create() {
        if (handle != -1)
            return this;

        this.handle = nvgCreate(NVG_ANTIALIAS | NVG_STENCIL_STROKES);
        if (handle == 0) {
            handle = -1;
            throw new IllegalStateException("Failed to create NanoVG context");
        }

        try {
            for (Field field : NVGFonts.class.getDeclaredFields()) {
                field.setAccessible(true);
                Object object = field.get(null);
                if (!(object instanceof NVGFont font)) continue;

                ByteBuffer buffer = createFont(font.getIdentifier(), NVGFonts.class.getResourceAsStream(font.getPath()));
                loadedFonts.add(font);
                font.setBuffer(buffer);
            }
        } catch (IllegalAccessException exception) {
            destroyAfterCreateFailure(exception);
            throw new IllegalStateException("Failed to access NanoVG font definitions", exception);
        } catch (RuntimeException | Error failure) {
            destroyAfterCreateFailure(failure);
            throw failure;
        }

        return this;
    }

    private void destroyAfterCreateFailure(Throwable failure) {
        try {
            destroy();
        } catch (Throwable cleanupFailure) {
            if (cleanupFailure != failure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
    }


    /**
     * Creates a font with the given identifier from the given input stream.
     *
     * @param identifier what identifier will be used to draw the font
     * @param fontStream the input stream of the font
     */
    public ByteBuffer createFont(String identifier, InputStream fontStream) {
        if (handle == -1)
            throw new IllegalStateException("NanoVG context has not been created");
        if (fontStream == null)
            throw new IllegalArgumentException("Missing font resource: " + identifier);

        ByteBuffer buffer = getBytes(fontStream, 1024);
        int fontHandle = nvgCreateFontMem(handle, identifier, buffer, false);
        if (fontHandle < 0)
            throw new IllegalStateException("Failed to create NanoVG font: " + identifier);

        registerDisplayFont(identifier, buffer);
        bufferRegistry.add(buffer);

        return buffer;
    }

    /**
     * Creates a texture.
     *
     * @param identifier what identifier will be used to draw the texture
     * @param texture    the input stream of the texture
     */
    public NVGU createTexture(String identifier, InputStream texture) {
        return createTexture(identifier, texture, NVG_IMAGE_NEAREST);
    }

    /**
     * Creates a texture.
     *
     * @param identifier what identifier will be used to draw the texture
     * @param texture    the input stream of the texture
     * @param flags      any additional flags you want
     */
    public NVGU createTexture(String identifier, InputStream texture, int flags) {
        if (handle == -1)
            throw new IllegalStateException("NanoVG context has not been created");
        if (texture == null)
            throw new IllegalArgumentException("Missing texture resource: " + identifier);

        if (!textures.containsKey(identifier)) {
            ByteBuffer imageData = getBytes(texture, 512);
            int unpackAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
            int imageHandle;
            try {
                imageHandle = nvgCreateImageMem(handle, flags, imageData);
            } finally {
                GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, unpackAlignment);
            }
            if (imageHandle <= 0)
                throw new IllegalStateException("Failed to create NanoVG texture: " + identifier);

            textures.put(identifier, imageHandle);

            int[] width = new int[1];
            int[] height = new int[1];
            nvgImageSize(handle, imageHandle, width, height);
            textureSizes.put(identifier, new Dimension(width[0], height[0]));
        }

        return this;
    }

    public NVGU createOrUpdateTextureRGBA(String identifier, int width, int height, ByteBuffer rgba) {
        return createOrUpdateTextureRGBA(identifier, width, height, rgba, NVG_IMAGE_NEAREST);
    }

    public NVGU createOrUpdateTextureRGBA(String identifier, int width, int height, ByteBuffer rgba, int flags) {
        if (handle == -1)
            throw new IllegalStateException("NanoVG context has not been created");
        long requiredBytes = (long) width * height * 4L;
        if (width <= 0 || height <= 0 || rgba == null || requiredBytes > Integer.MAX_VALUE || rgba.remaining() < requiredBytes)
            throw new IllegalArgumentException("Invalid RGBA texture data for " + identifier);

        Integer imageHandle = textures.get(identifier);
        Dimension size = textureSizes.get(identifier);

        int unpackAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        try {
            if (imageHandle == null || size == null || size.width != width || size.height != height) {
                int newImageHandle = nvgCreateImageRGBA(handle, width, height, flags, rgba);
                if (newImageHandle <= 0)
                    throw new IllegalStateException("Failed to create NanoVG texture: " + identifier);

                if (imageHandle != null)
                    nvgDeleteImage(handle, imageHandle);

                textures.put(identifier, newImageHandle);
                textureSizes.put(identifier, new Dimension(width, height));
            } else {
                nvgUpdateImage(handle, imageHandle, rgba);
            }
        } finally {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, unpackAlignment);
        }

        return this;
    }

    public boolean hasTexture(String identifier) {
        Integer imageHandle = textures.get(identifier);
        return imageHandle != null && imageHandle > 0;
    }

    /**
     * Destroys the instance of NanoVG
     */
    public void destroy() {
        long context = handle;
        handle = -1;
        frameActive = false;
        effectBatch = null;

        Throwable failure = null;
        if (context != -1) {
            for (int imageHandle : textures.values()) {
                failure = runCleanup(failure, () -> nvgDeleteImage(context, imageHandle));
            }
            failure = runCleanup(failure, Shader2DRenderer.INSTANCE::destroy);
            failure = runCleanup(failure, () -> nvgDelete(context));
        }

        for (STBTTFontinfo fontInfo : displayFonts.values())
            failure = runCleanup(failure, fontInfo::free);
        for (NVGColor colour : colourPool)
            failure = runCleanup(failure, colour::free);
        for (NVGPaint paint : paintPool)
            failure = runCleanup(failure, paint::free);

        loadedFonts.forEach(font -> font.setBuffer(null));
        loadedFonts.clear();
        bufferRegistry.clear();
        textures.clear();
        textureSizes.clear();
        displayFonts.clear();
        clearTextFilterCache();
        colourPool.clear();
        paintPool.clear();
        recycleFrameResources();

        rethrowCleanupFailure(failure);
    }

    private static Throwable runCleanup(Throwable previousFailure, CleanupAction action) {
        try {
            action.run();
        } catch (Throwable failure) {
            if (previousFailure == null)
                return failure;
            if (failure != previousFailure)
                previousFailure.addSuppressed(failure);
        }
        return previousFailure;
    }

    private static void rethrowCleanupFailure(Throwable failure) {
        if (failure instanceof RuntimeException runtimeException)
            throw runtimeException;
        if (failure instanceof Error error)
            throw error;
        if (failure != null)
            throw new IllegalStateException("Failed to release NanoVG resources", failure);
    }

    @FunctionalInterface
    private interface CleanupAction {
        void run();
    }

    public void clearTexture() {
        if (handle == -1) {
            textures.clear();
            textureSizes.clear();
            return;
        }

        textures.forEach((identifier, imageHandle) -> nvgDeleteImage(handle, imageHandle));
        textures.clear();
        textureSizes.clear();
    }

    /**
     * Begins a new frame
     *
     * @param width  the horizontal size of the frame in pixels
     * @param height the vertical size of the frame in pixels
     */
    public NVGU beginFrame(int width, int height) {
        beginFrameInternal(width, height, 1f, true);

        return this;
    }

    public NVGU beginFrame(int width, int height, float devicePixelRatio) {
        beginFrameInternal(width, height, devicePixelRatio, true);

        return this;
    }

    private void beginFrameInternal(int width, int height, float devicePixelRatio, boolean resetShaderFrame) {
        this.frameWidth = Math.max(1, width);
        this.frameHeight = Math.max(1, height);
        this.frameDevicePixelRatio = Math.max(0.001f, devicePixelRatio);
        if (resetShaderFrame) {
            Shader2DRenderer.INSTANCE.beginFrame();
            currentGlobalAlpha = 1f;
        }
        nvgBeginFrame(handle, width, height, devicePixelRatio);
        frameActive = true;
        nvgGlobalAlpha(handle, currentGlobalAlpha);
    }

    /**
     * Ends the current frame
     */
    public NVGU endFrame() {
        try {
            try {
                flushEffectBatch();
            } finally {
                endNativeFrame();
            }
        } finally {
            recycleFrameResources();
        }
        return this;
    }

    private void endNativeFrame() {
        if (!frameActive)
            return;

        frameActive = false;
        nvgEndFrame(handle);
    }

    /**
     * Begins, renders and ends a frame, and frees resources at the end.
     *
     * @param width  the horizontal size of the frame in pixels
     * @param height the vertical size of the frame in pixels
     * @param render what will be rendered in the frame
     */
    public NVGU frame(int width, int height, Runnable render) {
        beginFrame(width, height);
        try {
            render.run();
        } finally {
            endFrame();
        }

        return this;
    }

    /**
     * Provides a scope where any transformations that have taken place will be reverted
     * immediately after rendering, such as rotations or scaling.
     *
     * @param render what will be rendered in the scope
     */
    public NVGU scope(Runnable render) {
        save();
        try {
            render.run();
        } finally {
            restore();
        }

        return this;
    }

    /**
     * Saves current transformations
     */
    public NVGU save() {
        nvgSave(handle);
        return this;
    }

    /**
     * Restores previous transformations
     */
    public NVGU restore() {
        nvgRestore(handle);
        return this;
    }

    public NVGU globalAlpha(float alpha, Runnable render) {
        float previousGlobalAlpha = currentGlobalAlpha;
        try {
            currentGlobalAlpha = previousGlobalAlpha * Math.clamp(alpha, 0f, 1f);
            nvgGlobalAlpha(handle, currentGlobalAlpha);
            render.run();
        } finally {
            currentGlobalAlpha = previousGlobalAlpha;
            nvgGlobalAlpha(handle, currentGlobalAlpha);
        }

        return this;
    }

    /**
     * Textured rectangle
     *
     * @param x       left coordinate
     * @param y       top coordinate
     * @param width   width of the rectangle
     * @param height  height of the rectangle
     * @param texture the texture identifier to use
     */
    public NVGU texturedRectangle(float x, float y, float width, float height, String texture) {
        return rectangle(x, y, width, height, texture(texture, x, y, width, height));
    }

    /**
     * Basic coloured rectangle.
     *
     * @param rectangle bounds of the rectangle
     * @param colour    colour of the rectangle
     */
    public NVGU rectangle(Rectangle rectangle, Color colour) {
        return rectangle((float) rectangle.getX(), (float) rectangle.getY(), (float) rectangle.getWidth(), (float) rectangle.getHeight(), colour);
    }

    /**
     * Basic coloured rectangle.
     *
     * @param x      left coordinate
     * @param y      top coordinate
     * @param width  width of the rectangle
     * @param height height of the rectangle
     * @param colour colour of the rectangle
     */
    public NVGU rectangle(float x, float y, float width, float height, Color colour) {
        nvgBeginPath(handle);

        nvgRect(handle, x, y, width, height);

        if (colour instanceof NVGUColour) {
            ((NVGUColour) colour).apply(this, NVGUColour.RenderType.FILL);
        } else {
            nvgFillColor(handle, createAndStoreColour(colour));
            nvgFill(handle);
        }

        nvgClosePath(handle);

        return this;
    }

    /**
     * Basic coloured line.
     *
     * @param x1        start x coordinate
     * @param y1        start y coordinate
     * @param x2        end x coordinate
     * @param y2        end y coordinate
     * @param thickness stroke thickness
     * @param colour    colour of the line
     */
    public NVGU line(float x1, float y1, float x2, float y2, float thickness, Color colour) {
        nvgBeginPath(handle);

        nvgMoveTo(handle, x1, y1);
        nvgLineTo(handle, x2, y2);
        nvgStrokeWidth(handle, thickness);

        if (colour instanceof NVGUColour) {
            ((NVGUColour) colour).apply(this, NVGUColour.RenderType.STROKE);
        } else {
            nvgStrokeColor(handle, createAndStoreColour(colour));
            nvgStroke(handle);
        }

        nvgClosePath(handle);

        return this;
    }

    /**
     * Textured rectangle border
     *
     * @param x       left coordinate
     * @param y       top coordinate
     * @param width   width of the rectangle
     * @param height  height of the rectangle
     * @param texture the texture identifier to use
     */
    public NVGU texturedRectangleBorder(float x, float y, float width, float height, float thickness, String texture, Border border) {
        return rectangleBorder(x, y, width, height, thickness, texture(texture, x, y, width, height), border);
    }

    /**
     * Basic coloured rectangle border.
     *
     * @param rectangle bounds of the rectangle
     * @param colour    colour of the rectangle
     */
    public NVGU rectangleBorder(Rectangle rectangle, float thickness, Color colour, Border border) {
        return rectangleBorder((float) rectangle.getX(), (float) rectangle.getY(), (float) rectangle.getWidth(), (float) rectangle.getHeight(), thickness, colour, border);
    }

    /**
     * Basic coloured rectangle border.
     *
     * @param x         left coordinate
     * @param y         top coordinate
     * @param width     width of the rectangle
     * @param height    height of the rectangle
     * @param thickness thickness of the border
     * @param colour    colour of the rectangle
     */
    public NVGU rectangleBorder(float x, float y, float width, float height, float thickness, Color colour, Border border) {
        nvgBeginPath(handle);

        switch (border) {
            case INSIDE: {
                x += thickness / 2f;
                y += thickness / 2f;
                width -= thickness;
                height -= thickness;
                break;
            }

            case OUTSIDE: {
                x -= thickness / 2f;
                y -= thickness / 2f;
                width += thickness;
                height += thickness;
                break;
            }
        }

        nvgRect(handle, x, y, width, height);
        nvgStrokeWidth(handle, thickness);

        if (colour instanceof NVGUColour) {
            ((NVGUColour) colour).apply(this, NVGUColour.RenderType.STROKE);
        } else {
            nvgStrokeColor(handle, createAndStoreColour(colour));
            nvgStroke(handle);
        }

        nvgClosePath(handle);

        return this;
    }

    /**
     * Textured rounded rectangle
     *
     * @param x           left coordinate
     * @param y           top coordinate
     * @param width       width of the rectangle
     * @param height      height of the rectangle
     * @param topLeft     radius of the top left corner
     * @param topRight    radius of the top right corner
     * @param bottomRight radius of the bottom right corner
     * @param bottomLeft  radius of the bottom left corner
     * @param texture     the texture identifier to use
     */
    public NVGU texturedRoundedRectangle(float x, float y, float width, float height, float topLeft, float topRight, float bottomRight, float bottomLeft, String texture) {
        return roundedRectangle(x, y, width, height, topLeft, topRight, bottomRight, bottomLeft, texture(texture, x, y, width, height));
    }

    /**
     * Textured rounded rectangle
     *
     * @param x       left coordinate
     * @param y       top coordinate
     * @param width   width of the rectangle
     * @param height  height of the rectangle
     * @param radius  radius of the rounded rectangle
     * @param texture the texture identifier to use
     */
    public NVGU texturedRoundedRectangle(float x, float y, float width, float height, float radius, String texture) {
        return roundedRectangle(x, y, width, height, radius, texture(texture, x, y, width, height));
    }

    /**
     * Basic coloured rounded rectangle.
     *
     * @param bounds      bounds of the rectangle
     * @param topLeft     radius of the top left corner
     * @param topRight    radius of the top right corner
     * @param bottomRight radius of the bottom right corner
     * @param bottomLeft  radius of the bottom left corner
     * @param colour      colour of the rounded rectangle
     */
    public NVGU roundedRectangle(Rectangle bounds, float topLeft, float topRight, float bottomRight, float bottomLeft, Color colour) {
        return roundedRectangle((float) bounds.getX(), (float) bounds.getY(), (float) bounds.getWidth(), (float) bounds.getHeight(), topLeft, topRight, bottomRight, bottomLeft, colour);
    }

    /**
     * Basic coloured rounded rectangle.
     *
     * @param bounds bounds of the rectangle
     * @param radius radius of the rounded rectangle
     * @param colour colour of the rounded rectangle
     */
    public NVGU roundedRectangle(Rectangle bounds, float radius, Color colour) {
        return roundedRectangle((float) bounds.getX(), (float) bounds.getY(), (float) bounds.getWidth(), (float) bounds.getHeight(), radius, colour);
    }

    /**
     * Basic coloured rounded rectangle.
     *
     * @param x      left coordinate
     * @param y      top coordinate
     * @param width  width of the rectangle
     * @param height height of the rectangle
     * @param radius radius of the rounded rectangle
     * @param colour colour of the rounded rectangle
     */
    public NVGU roundedRectangle(float x, float y, float width, float height, float radius, Color colour) {
        return roundedRectangle(x, y, width, height, radius, radius, radius, radius, colour);
    }

    /**
     * Basic coloured rounded rectangle.
     *
     * @param x           left coordinate
     * @param y           top coordinate
     * @param width       width of the rectangle
     * @param height      height of the rectangle
     * @param topLeft     radius of the top left corner
     * @param topRight    radius of the top right corner
     * @param bottomRight radius of the bottom right corner
     * @param bottomLeft  radius of the bottom left corner
     * @param colour      colour of the rounded rectangle
     */
    public NVGU roundedRectangle(float x, float y, float width, float height, float topLeft, float topRight, float bottomRight, float bottomLeft, Color colour) {
        nvgBeginPath(handle);

        nvgRoundedRectVarying(handle, x, y, width, height, topLeft, topRight, bottomRight, bottomLeft);

        if (colour instanceof NVGUColour) {
            ((NVGUColour) colour).apply(this, NVGUColour.RenderType.FILL);
        } else {
            nvgFillColor(handle, createAndStoreColour(colour));
            nvgFill(handle);
        }

        nvgClosePath(handle);

        return this;
    }

    /**
     * Draws the current framebuffer blurred inside a rounded rectangle using a shader.
     * This is intended for translucent 2D panels that sit above the world or existing GUI.
     */
    public NVGU blurRoundedRectangle(float x, float y, float width, float height, float radius, float blurRadius, float alpha) {
        ShaderRect rect = transformShaderRect(x, y, width, height, radius);
        float effectiveAlpha = alpha * currentGlobalAlpha;

        if (effectBatch != null) {
            effectBatch.addBlur(rect.x, rect.y, rect.width, rect.height, rect.radius, blurRadius * rect.scale, effectiveAlpha);
        } else {
            Shader2DRenderer.INSTANCE.drawBlurredRoundedRect(frameWidth, frameHeight, rect.x, rect.y, rect.width, rect.height, rect.radius, blurRadius * rect.scale, effectiveAlpha);
        }

        return this;
    }

    /**
     * Draws a soft rounded-rectangle shadow using a shader.
     * Call it before drawing the panel itself so the solid panel covers the shadow body.
     */
    public NVGU shadowRoundedRectangle(float x, float y, float width, float height, float radius, float blurRadius, Color colour) {
        return shadowRoundedRectangle(x, y, width, height, radius, blurRadius, 0f, 0f, 0f, colour);
    }

    /**
     * Draws a soft rounded-rectangle shadow using a shader.
     * Call it before drawing the panel itself so the solid panel covers the shadow body.
     */
    public NVGU shadowRoundedRectangle(float x, float y, float width, float height, float radius, float blurRadius, float spread, float offsetX, float offsetY, Color colour) {
        ShaderRect rect = transformShaderRect(x, y, width, height, radius);
        float offsetScaleX = rect.scaleX;
        float offsetScaleY = rect.scaleY;
        Color effectiveColour = withGlobalAlpha(colour);

        if (effectBatch != null) {
            effectBatch.addShadow(
                    rect.x,
                    rect.y,
                    rect.width,
                    rect.height,
                    rect.radius,
                    blurRadius * rect.scale,
                    spread * rect.scale,
                    offsetX * offsetScaleX,
                    offsetY * offsetScaleY,
                    effectiveColour
            );
            return this;
        }

        Shader2DRenderer.INSTANCE.drawShadowRoundedRect(
                frameWidth,
                frameHeight,
                rect.x,
                rect.y,
                rect.width,
                rect.height,
                rect.radius,
                blurRadius * rect.scale,
                spread * rect.scale,
                offsetX * offsetScaleX,
                offsetY * offsetScaleY,
                effectiveColour
        );

        return this;
    }

    /**
     * Draws a batch of directional chevrons with the signed-distance-field indicator shader.
     * Request coordinates use the current NanoVG coordinate system and transform.
     */
    public NVGU directionalIndicators(List<Shader2DRenderer.IndicatorRequest> requests) {
        if (requests == null || requests.isEmpty() || !frameActive)
            return this;

        if (effectBatch != null)
            flushEffectBatch();

        nvgCurrentTransform(handle, shaderTransform);
        float scaleX = (float) Math.hypot(shaderTransform[0], shaderTransform[1]);
        float scaleY = (float) Math.hypot(shaderTransform[2], shaderTransform[3]);
        float scale = Math.max(0.001f, Math.max(scaleX, scaleY));
        transformedIndicatorRequests.clear();

        for (Shader2DRenderer.IndicatorRequest request : requests) {
            if (request == null || request.color == null || request.color.getAlpha() <= 0 || request.size <= 0f)
                continue;

            float directionX = (float) Math.sin(request.angle);
            float directionY = (float) -Math.cos(request.angle);
            float transformedDirectionX = directionX * shaderTransform[0] + directionY * shaderTransform[2];
            float transformedDirectionY = directionX * shaderTransform[1] + directionY * shaderTransform[3];
            float transformedAngle = (float) Math.atan2(transformedDirectionX, -transformedDirectionY);
            Color effectiveColor = withGlobalAlpha(request.color);
            if (effectiveColor.getAlpha() <= 0)
                continue;

            int index = transformedIndicatorRequests.size();
            while (transformedIndicatorRequestPool.size() <= index)
                transformedIndicatorRequestPool.add(new Shader2DRenderer.IndicatorRequest());

            transformedIndicatorRequests.add(transformedIndicatorRequestPool.get(index).set(
                    transformX(shaderTransform, request.centerX, request.centerY),
                    transformY(shaderTransform, request.centerX, request.centerY),
                    transformedAngle,
                    request.size * scale,
                    request.thickness * scale,
                    request.glowRadius * scale,
                    request.pulse,
                    effectiveColor
            ));
        }

        if (transformedIndicatorRequests.isEmpty())
            return this;

        nvgCurrentTransform(handle, effectTransform);
        endNativeFrame();

        Shader2DRenderer.INSTANCE.drawDirectionalIndicators(
                frameWidth,
                frameHeight,
                transformedIndicatorRequests
        );

        beginFrameInternal(frameWidth, frameHeight, frameDevicePixelRatio, false);
        nvgSave(handle);
        nvgTransform(handle, effectTransform[0], effectTransform[1], effectTransform[2], effectTransform[3], effectTransform[4], effectTransform[5]);
        nvgGlobalAlpha(handle, currentGlobalAlpha);

        return this;
    }

    public NVGU beginEffectBatch() {
        if (effectBatch != null) {
            throw new IllegalStateException("An NVGU effect batch is already active");
        }

        reusableEffectBatch.clear();
        effectBatch = reusableEffectBatch;
        return this;
    }

    /**
     * Draws multiple rounded rectangles with one NanoVG fill command.
     * Each rectangle occupies eight consecutive values in {@code geometry}:
     * x, y, width, height, top-left, top-right, bottom-right and bottom-left radius.
     */
    public NVGU roundedRectangles(float[] geometry, int rectangleCount, Color colour) {
        if (geometry == null || colour == null || rectangleCount <= 0)
            return this;

        int availableRectangles = geometry.length / 8;
        int count = Math.min(rectangleCount, availableRectangles);
        if (count <= 0)
            return this;

        nvgBeginPath(handle);
        int drawn = 0;
        for (int i = 0; i < count; i++) {
            int offset = i * 8;
            float width = geometry[offset + 2];
            float height = geometry[offset + 3];
            if (width <= 0f || height <= 0f)
                continue;

            nvgRoundedRectVarying(
                    handle,
                    geometry[offset],
                    geometry[offset + 1],
                    width,
                    height,
                    geometry[offset + 4],
                    geometry[offset + 5],
                    geometry[offset + 6],
                    geometry[offset + 7]
            );
            drawn++;
        }

        if (drawn > 0) {
            if (colour instanceof NVGUColour nvgColour) {
                nvgColour.apply(this, NVGUColour.RenderType.FILL);
            } else {
                nvgFillColor(handle, createAndStoreColour(colour));
                nvgFill(handle);
            }
        }

        nvgClosePath(handle);
        return this;
    }

    public NVGU flushEffectBatch() {
        if (effectBatch == null)
            return this;

        EffectBatch batch = effectBatch;
        effectBatch = null;
        if (batch.isEmpty())
            return this;

        nvgCurrentTransform(handle, effectTransform);

        endNativeFrame();

        Shader2DRenderer.INSTANCE.drawEffects(frameWidth, frameHeight, batch.blurRequests, batch.shadowRequests);

        beginFrameInternal(frameWidth, frameHeight, frameDevicePixelRatio, false);
        nvgSave(handle);
        nvgTransform(handle, effectTransform[0], effectTransform[1], effectTransform[2], effectTransform[3], effectTransform[4], effectTransform[5]);
        nvgGlobalAlpha(handle, currentGlobalAlpha);

        return this;
    }

    public NVGU effectBatch(Runnable effects) {
        beginEffectBatch();

        try {
            effects.run();
        } finally {
            flushEffectBatch();
        }

        return this;
    }

    public NVGU restartFrameAfterFailure(float scale) {
        effectBatch = null;
        reusableEffectBatch.clear();
        endNativeFrame();
        currentGlobalAlpha = 1f;
        beginFrameInternal(frameWidth, frameHeight, frameDevicePixelRatio, true);
        save();
        scale(0f, 0f, scale);
        return this;
    }

    /**
     * Textured rounded rectangle
     *
     * @param x           left coordinate
     * @param y           top coordinate
     * @param width       width of the rectangle
     * @param height      height of the rectangle
     * @param topLeft     radius of the top left corner
     * @param topRight    radius of the top right corner
     * @param bottomRight radius of the bottom right corner
     * @param bottomLeft  radius of the bottom left corner
     * @param thickness   thickness of the border
     * @param texture     the texture identifier to use
     * @param border      the border type to use
     */
    public NVGU texturedRoundedRectangleBorder(float x, float y, float width, float height, float topLeft, float topRight, float bottomRight, float bottomLeft, float thickness, String texture, Border border) {
        return roundedRectangleBorder(x, y, width, height, topLeft, topRight, bottomRight, bottomLeft, thickness, texture(texture, x, y, width, height), border);
    }

    /**
     * Textured rounded rectangle
     *
     * @param x       left coordinate
     * @param y       top coordinate
     * @param width   width of the rectangle
     * @param height  height of the rectangle
     * @param radius  radius of the rounded rectangle
     * @param texture the texture identifier to use
     */
    public NVGU texturedRoundedRectangleBorder(float x, float y, float width, float height, float radius, float thickness, String texture, Border border) {
        return roundedRectangleBorder(x, y, width, height, radius, thickness, texture(texture, x, y, width, height), border);
    }

    /**
     * Basic coloured rounded rectangle border.
     *
     * @param bounds      bounds of the rectangle
     * @param topLeft     radius of the top left corner
     * @param topRight    radius of the top right corner
     * @param bottomRight radius of the bottom right corner
     * @param bottomLeft  radius of the bottom left corner
     * @param thickness   the thickness of the border
     * @param colour      colour of the rounded rectangle
     */
    public NVGU roundedRectangleBorder(Rectangle bounds, float topLeft, float topRight, float bottomRight, float bottomLeft, float thickness, Color colour, Border border) {
        return roundedRectangleBorder((float) bounds.getX(), (float) bounds.getY(), (float) bounds.getWidth(), (float) bounds.getHeight(), topLeft, topRight, bottomRight, bottomLeft, thickness, colour, border);
    }

    /**
     * Basic coloured rounded rectangle border.
     *
     * @param bounds    bounds of the rectangle
     * @param radius    radius of the rounded rectangle
     * @param thickness the thickness of the border
     * @param colour    colour of the rounded rectangle
     */
    public NVGU roundedRectangleBorder(Rectangle bounds, float radius, float thickness, Color colour, Border border) {
        return roundedRectangleBorder((float) bounds.getX(), (float) bounds.getY(), (float) bounds.getWidth(), (float) bounds.getHeight(), radius, thickness, colour, border);
    }

    /**
     * Basic coloured rounded rectangle border.
     *
     * @param x         left coordinate
     * @param y         top coordinate
     * @param width     width of the rectangle
     * @param height    height of the rectangle
     * @param radius    radius of the rounded rectangle
     * @param thickness the thickness of the border
     * @param colour    colour of the rounded rectangle
     */
    public NVGU roundedRectangleBorder(float x, float y, float width, float height, float radius, float thickness, Color colour, Border border) {
        return roundedRectangleBorder(x, y, width, height, radius, radius, radius, radius, thickness, colour, border);
    }

    /**
     * Basic coloured rounded rectangle border.
     *
     * @param x           left coordinate
     * @param y           top coordinate
     * @param width       width of the rectangle
     * @param height      height of the rectangle
     * @param topLeft     radius of the top left corner
     * @param topRight    radius of the top right corner
     * @param bottomRight radius of the bottom right corner
     * @param bottomLeft  radius of the bottom left corner
     * @param thickness   the thickness of the border
     * @param colour      colour of the rounded rectangle
     */
    public NVGU roundedRectangleBorder(float x, float y, float width, float height, float topLeft, float topRight, float bottomRight, float bottomLeft, float thickness, Color colour, Border border) {
        nvgBeginPath(handle);

        switch (border) {
            case INSIDE: {
                x += thickness / 2f;
                y += thickness / 2f;
                width -= thickness;
                height -= thickness;
                break;
            }

            case OUTSIDE: {
                x -= thickness / 2f;
                y -= thickness / 2f;
                width += thickness;
                height += thickness;
                break;
            }
        }

        nvgRoundedRectVarying(handle, x, y, width, height, topLeft, topRight, bottomRight, bottomLeft);

        nvgStrokeWidth(handle, thickness);

        if (colour instanceof NVGUColour) {
            ((NVGUColour) colour).apply(this, NVGUColour.RenderType.STROKE);
        } else {
            nvgStrokeColor(handle, createAndStoreColour(colour));
            nvgStroke(handle);
        }

        nvgClosePath(handle);

        return this;
    }

    /**
     * Basic coloured circle.
     *
     * @param x      centre x coordinate of the circle
     * @param y      centre y coordinate of the circle
     * @param radius radius of the circle
     * @param colour colour of the circle
     */
    public NVGU circle(float x, float y, float radius, Color colour) {
        nvgBeginPath(handle);

        nvgCircle(handle, x, y, radius);

        if (colour instanceof NVGUColour) {
            ((NVGUColour) colour).apply(this, NVGUColour.RenderType.FILL);
        } else {
            nvgFillColor(handle, createAndStoreColour(colour));
            nvgFill(handle);
        }

        nvgClosePath(handle);

        return this;
    }

    /**
     * Basic coloured circle border.
     *
     * @param x         centre x coordinate of the circle
     * @param y         centre y coordinate of the circle
     * @param radius    radius of the circle
     * @param thickness thickness of the circle border
     * @param colour    colour of the circle
     */
    public NVGU circleBorder(float x, float y, float radius, float thickness, Color colour) {
        nvgBeginPath(handle);

        nvgCircle(handle, x, y, radius);
        nvgStrokeWidth(handle, thickness);

        if (colour instanceof NVGUColour) {
            ((NVGUColour) colour).apply(this, NVGUColour.RenderType.STROKE);
        } else {
            nvgStrokeColor(handle, createAndStoreColour(colour));
            nvgStroke(handle);
        }

        nvgClosePath(handle);

        return this;
    }

    /**
     * Basic coloured right-angled triangle.
     *
     * @param x      centre x coordinate of the circle
     * @param y      centre y coordinate of the circle
     * @param width  width of the triangle
     * @param height height of the triangle
     * @param colour colour of the circle
     * @param corner where the corner is located
     */
    public NVGU rightAngledTriangle(float x, float y, float width, float height, Color colour, RightAngledTriangleCorner corner) {
        nvgBeginPath(handle);

        switch (corner) {
            case TOP_LEFT: {
                nvgMoveTo(handle, x, y);
                nvgLineTo(handle, x + width, y);
                nvgLineTo(handle, x, y + height);
                nvgLineTo(handle, x, y);
                break;
            }

            case TOP_RIGHT: {
                nvgMoveTo(handle, x + width, y);
                nvgLineTo(handle, x, y);
                nvgLineTo(handle, x + width, y + height);
                nvgLineTo(handle, x + width, y);
                break;
            }

            case BOTTOM_LEFT: {
                nvgMoveTo(handle, x, y + height);
                nvgLineTo(handle, x + width, y + height);
                nvgLineTo(handle, x, y);
                nvgLineTo(handle, x, y + height);
                break;
            }

            case BOTTOM_RIGHT: {
                nvgMoveTo(handle, x + width, y + height);
                nvgLineTo(handle, x, y + height);
                nvgLineTo(handle, x + width, y);
                nvgLineTo(handle, x + width, y + height);
                break;
            }
        }

        if (colour instanceof NVGUColour) {
            ((NVGUColour) colour).apply(this, NVGUColour.RenderType.FILL);
        } else {
            nvgFillColor(handle, createAndStoreColour(colour));
            nvgFill(handle);
        }

        nvgClosePath(handle);

        return this;
    }

    /**
     * Basic coloured right-angled triangle border.
     *
     * @param x      centre x coordinate of the circle
     * @param y      centre y coordinate of the circle
     * @param width  width of the triangle
     * @param height height of the triangle
     * @param colour colour of the circle
     * @param corner where the corner is located
     */
    public NVGU rightAngledTriangleBorder(float x, float y, float width, float height, float thickness, Color colour, RightAngledTriangleCorner corner) {
        nvgBeginPath(handle);

        switch (corner) {
            case TOP_LEFT: {
                nvgMoveTo(handle, x, y);
                nvgLineTo(handle, x + width, y);
                nvgLineTo(handle, x, y + height);
                nvgLineTo(handle, x, y);
                break;
            }

            case TOP_RIGHT: {
                nvgMoveTo(handle, x + width, y);
                nvgLineTo(handle, x, y);
                nvgLineTo(handle, x + width, y + height);
                nvgLineTo(handle, x + width, y);
                break;
            }

            case BOTTOM_LEFT: {
                nvgMoveTo(handle, x, y + height);
                nvgLineTo(handle, x + width, y + height);
                nvgLineTo(handle, x, y);
                nvgLineTo(handle, x, y + height);
                break;
            }

            case BOTTOM_RIGHT: {
                nvgMoveTo(handle, x + width, y + height);
                nvgLineTo(handle, x, y + height);
                nvgLineTo(handle, x + width, y);
                nvgLineTo(handle, x + width, y + height);
                break;
            }
        }

        nvgStrokeWidth(handle, thickness);

        if (colour instanceof NVGUColour) {
            ((NVGUColour) colour).apply(this, NVGUColour.RenderType.STROKE);
        } else {
            nvgStrokeColor(handle, createAndStoreColour(colour));
            nvgStroke(handle);
        }

        nvgClosePath(handle);

        return this;
    }

    /**
     * Basic filled polygon.
     *
     * @param points array of points, a point being a float array of length 2
     * @param colour the colour of the polygon
     */
    public NVGU polygon(float[][] points, Color colour) {
        nvgBeginPath(handle);

        nvgMoveTo(handle, points[0][0], points[0][1]);

        for (int i = 1; i < points.length; i++) {
            nvgLineTo(handle, points[i][0], points[i][1]);
        }

        if (colour instanceof NVGUColour) {
            ((NVGUColour) colour).apply(this, NVGUColour.RenderType.FILL);
        } else {
            nvgFillColor(handle, createAndStoreColour(colour));
            nvgFill(handle);
        }

        nvgClosePath(handle);

        return this;
    }

    /**
     * Basic filled polygon.
     *
     * @param points array of points, a point being a float array of length 2
     * @param colour the colour of the polygon
     */
    public NVGU polygonBorder(float[][] points, float thickness, Color colour) {
        nvgBeginPath(handle);

        nvgMoveTo(handle, points[0][0], points[0][1]);

        for (int i = 1; i < points.length; i++) {
            nvgLineTo(handle, points[i][0], points[i][1]);
        }

        nvgStrokeWidth(handle, thickness);

        if (colour instanceof NVGUColour) {
            ((NVGUColour) colour).apply(this, NVGUColour.RenderType.STROKE);
        } else {
            nvgStrokeColor(handle, createAndStoreColour(colour));
            nvgStroke(handle);
        }

        nvgClosePath(handle);

        return this;
    }

    /**
     * Renders the given text at given coordinates.
     * Uses {@link NVGU#currentFont}, {@link NVGU#currentFontSize} and {@link NVGU#alignment}
     * for the additional data.
     * If these aren't set, a {@link NullPointerException} with be thrown.
     *
     * @param text   the text to draw
     * @param x      the x position
     * @param y      the y position
     * @param colour the colour of the text - will not accept gradients
     */
    public NVGU text(String text, float x, float y, Color colour) {
        return text(text, x, y, colour, this.currentFont, this.currentFontSize, this.alignment);
    }

    /**
     * Renders the given text at given coordinates, with alignment {@link Alignment#LEFT_TOP}
     *
     * @param text   the text to draw
     * @param x      the x position
     * @param y      the y position
     * @param colour the colour of the text - will not accept gradients
     * @param font   what font to use - must have been created using {@link NVGU#createFont(String, InputStream)}
     * @param size   the font size
     */
    public NVGU text(String text, float x, float y, Color colour, String font, int size) {
        return text(text, x, y, colour, font, size, Alignment.LEFT_TOP);
    }

    /**
     * Renders the given text at given coordinates
     *
     * @param text      the text to draw
     * @param x         the x position
     * @param y         the y position
     * @param colour    the colour of the text - will not accept gradients
     * @param font      what font to use - must have been created using {@link NVGU#createFont(String, InputStream)}
     * @param size      the font size
     * @param alignment how the text should be aligned in accordance with the coordinates
     */
    public NVGU text(String text, float x, float y, Color colour, String font, float size, Alignment alignment) {
        if (text == null || text.isEmpty()) return this;

        String filteredText = filterRenderableText(text, font);
        if (filteredText.isEmpty()) return this;

        if (hasMinecraftFormatting(filteredText)) {
            return formattedText(filteredText, x, y, colour, font, size, alignment);
        }

        nvgBeginPath(handle);

        nvgFillColor(handle, createAndStoreColour(colour));
        nvgFontFace(handle, font);
        nvgFontSize(handle, size);
        nvgTextAlign(handle, alignment.getTextAlignment());
        nvgText(handle, x, y + 1, filteredText);

        nvgClosePath(handle);

        return this;
    }

    private NVGU formattedText(String text, float x, float y, Color colour, String font, float size, Alignment alignment) {
        List<TextSegment> segments = parseMinecraftFormattedText(text, colour);
        if (segments.isEmpty()) return this;

        float drawX = getAlignedTextStartX(text, x, font, size, alignment);
        Alignment drawAlignment = leftAligned(alignment);

        nvgBeginPath(handle);

        nvgFontFace(handle, font);
        nvgFontSize(handle, size);
        nvgTextAlign(handle, drawAlignment.getTextAlignment());

        for (TextSegment segment : segments) {
            nvgFillColor(handle, createAndStoreColour(segment.colour));
            drawX = nvgText(handle, drawX, y + 1, segment.text);
        }

        nvgClosePath(handle);

        return this;
    }

    public String getCurrentFont() {
        return currentFont;
    }

    public int getCurrentFontSize() {
        return currentFontSize;
    }

    public Alignment getAlignment() {
        return alignment;
    }

    /**
     * Sets the font data for {@link NVGU#text(String, float, float, Color)}
     *
     * @param font      what font to use - must have been created using {@link NVGU#createFont(String, InputStream)}
     * @param size      the size of the font
     * @param alignment the alignment of the font
     */
    public NVGU setFontData(String font, int size, Alignment alignment) {
        this.currentFont = font;
        this.currentFontSize = size;
        this.alignment = alignment;

        return this;
    }

    /**
     * Gets the width of the given text.
     * Uses {@link NVGU#currentFont} and {@link NVGU#currentFontSize}
     * for the additional data.
     * If these aren't set, a {@link NullPointerException} with be thrown.
     *
     * @param text the given text to calculate the width of
     * @return the width of the text
     */
    public float textWidth(String text) {
        return textWidth(text, currentFont, currentFontSize);
    }

    /**
     * Gets the width of the given text.
     *
     * @param text the given text to calculate the width of
     * @param font the font to use
     * @param size the size of the font
     * @return the width of the text
     */
    public float textWidth(String text, String font, float size) {
        if (text == null || text.isEmpty()) return 0f;

        String filteredText = filterRenderableText(text, font);
        if (filteredText.isEmpty()) return 0f;

        if (hasMinecraftFormatting(filteredText)) {
            float width = 0f;
            for (TextSegment segment : parseMinecraftFormattedText(filteredText, Color.WHITE)) {
                width += rawTextWidth(segment.text, font, size);
            }
            return width;
        }

        return rawTextWidth(filteredText, font, size);
    }

    public boolean canRenderText(String text, String font) {
        if (text == null || text.isEmpty()) return false;
        return filterRenderableText(text, font).equals(text);
    }

    public String filterRenderableText(String text, String font) {
        if (text == null || text.isEmpty()) return "";

        int cacheIndex = filterCacheIndex(text, font);
        String cachedText = filterCacheText[cacheIndex];
        if (cachedText != null && cachedText.equals(text)
                && java.util.Objects.equals(filterCacheFont[cacheIndex], font))
            return filterCacheResult[cacheIndex];

        STBTTFontinfo displayFont = displayFonts.get(font);
        StringBuilder filteredText = null;
        int copyFrom = 0;

        for (int i = 0; i < text.length(); ) {
            ParsedMinecraftHexColor hexColor = parseMinecraftHexColor(text, i, 255);
            if (hexColor != null) {
                i = hexColor.nextIndex;
                continue;
            }

            if (isMinecraftFormattingCodeAt(text, i)) {
                i += 2;
                continue;
            }

            char first = text.charAt(i);
            int charLength = 1;
            boolean renderable;
            if (Character.isHighSurrogate(first)) {
                if (i + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(i + 1))) {
                    renderable = false;
                } else {
                    charLength = 2;
                    int codePoint = Character.toCodePoint(first, text.charAt(i + 1));
                    renderable = canRenderCodePoint(codePoint, displayFont);
                }
            } else if (Character.isLowSurrogate(first)) {
                renderable = false;
            } else {
                renderable = canRenderCodePoint(first, displayFont);
            }

            if (!renderable) {
                if (filteredText == null)
                    filteredText = new StringBuilder(text.length());

                filteredText.append(text, copyFrom, i);
                copyFrom = i + charLength;
            }

            i += charLength;
        }

        String result = filteredText == null
                ? text
                : filteredText.append(text, copyFrom, text.length()).toString();
        filterCacheText[cacheIndex] = text;
        filterCacheFont[cacheIndex] = font;
        filterCacheResult[cacheIndex] = result;
        return result;
    }

    private static int filterCacheIndex(String text, String font) {
        int hash = 31 * text.hashCode() + (font != null ? font.hashCode() : 0);
        hash ^= hash >>> 16;
        return hash & (FILTER_CACHE_SIZE - 1);
    }

    private void clearTextFilterCache() {
        Arrays.fill(filterCacheText, null);
        Arrays.fill(filterCacheFont, null);
        Arrays.fill(filterCacheResult, null);
    }

    private static boolean canRenderCodePoint(int codePoint, STBTTFontinfo displayFont) {
        if (!isRenderableCodePoint(codePoint)) return false;
        return displayFont == null || isTextWhitespace(codePoint) || stbtt_FindGlyphIndex(displayFont, codePoint) != 0;
    }

    private static boolean isRenderableCodePoint(int codePoint) {
        if (codePoint == 0xFFFD) return false;
        if (isTextWhitespace(codePoint)) return true;
        if (Character.isISOControl(codePoint)) return false;

        int type = Character.getType(codePoint);
        return type != Character.UNASSIGNED && type != Character.SURROGATE;
    }

    private static boolean isTextWhitespace(int codePoint) {
        return codePoint == '\n' || codePoint == '\r' || codePoint == '\t';
    }

    private void registerDisplayFont(String identifier, ByteBuffer buffer) {
        clearTextFilterCache();
        STBTTFontinfo oldFontInfo = displayFonts.remove(identifier);
        if (oldFontInfo != null) oldFontInfo.free();

        STBTTFontinfo fontInfo = STBTTFontinfo.malloc();
        if (stbtt_InitFont(fontInfo, buffer.asReadOnlyBuffer())) {
            displayFonts.put(identifier, fontInfo);
        } else {
            fontInfo.free();
        }
    }

    private float rawTextWidth(String text, String font, float size) {
        save();
        try {
            nvgFontFace(handle, font);
            nvgFontSize(handle, size);
            nvgTextAlign(handle, Alignment.LEFT_TOP.getTextAlignment());
            return nvgTextBounds(handle, 0f, 0f, text, textBounds);
        } finally {
            restore();
        }
    }

    public static String stripMinecraftFormatting(String text) {
        if (text == null || text.isEmpty()) return text;
        if (!hasMinecraftFormatting(text)) return text;

        StringBuilder strippedText = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            ParsedMinecraftHexColor hexColor = parseMinecraftHexColor(text, i, 255);
            if (hexColor != null) {
                i = hexColor.nextIndex - 1;
                continue;
            }

            if (isMinecraftFormattingCodeAt(text, i)) {
                i++;
                continue;
            }

            strippedText.append(text.charAt(i));
        }

        return strippedText.toString();
    }

    private static boolean hasMinecraftFormatting(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (parseMinecraftHexColor(text, i, 255) != null || isMinecraftFormattingCodeAt(text, i)) {
                return true;
            }
        }

        return false;
    }

    private static List<TextSegment> parseMinecraftFormattedText(String text, Color defaultColour) {
        List<TextSegment> segments = new ArrayList<>();
        StringBuilder currentText = new StringBuilder(text.length());
        Color currentColour = defaultColour;

        for (int i = 0; i < text.length(); i++) {
            ParsedMinecraftHexColor hexColor = parseMinecraftHexColor(text, i, defaultColour.getAlpha());
            if (hexColor != null) {
                addTextSegment(segments, currentText, currentColour);
                currentColour = hexColor.colour;
                i = hexColor.nextIndex - 1;
                continue;
            }

            if (isMinecraftFormattingCodeAt(text, i)) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                Color legacyColour = getMinecraftLegacyColour(code, defaultColour.getAlpha());

                if (legacyColour != null) {
                    addTextSegment(segments, currentText, currentColour);
                    currentColour = legacyColour;
                } else if (code == 'r') {
                    addTextSegment(segments, currentText, currentColour);
                    currentColour = defaultColour;
                }

                i++;
                continue;
            }

            currentText.append(text.charAt(i));
        }

        addTextSegment(segments, currentText, currentColour);
        return segments;
    }

    private static void addTextSegment(List<TextSegment> segments, StringBuilder currentText, Color colour) {
        if (currentText.isEmpty()) return;

        segments.add(new TextSegment(currentText.toString(), colour));
        currentText.setLength(0);
    }

    private static boolean isMinecraftFormattingCodeAt(String text, int index) {
        if (index + 1 >= text.length()) return false;

        char prefix = text.charAt(index);
        if (prefix != '\u00A7' && prefix != '&') return false;

        return "0123456789abcdefklmnor".indexOf(Character.toLowerCase(text.charAt(index + 1))) >= 0;
    }

    private static Color getMinecraftLegacyColour(char code, int alpha) {
        int colourIndex = "0123456789abcdef".indexOf(Character.toLowerCase(code));
        if (colourIndex < 0) return null;

        return colourFromRgb(switch (colourIndex) {
            case 0 -> 0x000000;
            case 1 -> 0x0000AA;
            case 2 -> 0x00AA00;
            case 3 -> 0x00AAAA;
            case 4 -> 0xAA0000;
            case 5 -> 0xAA00AA;
            case 6 -> 0xFFAA00;
            case 7 -> 0xAAAAAA;
            case 8 -> 0x555555;
            case 9 -> 0x5555FF;
            case 10 -> 0x55FF55;
            case 11 -> 0x55FFFF;
            case 12 -> 0xFF5555;
            case 13 -> 0xFF55FF;
            case 14 -> 0xFFFF55;
            default -> 0xFFFFFF;
        }, alpha);
    }

    private static ParsedMinecraftHexColor parseMinecraftHexColor(String text, int index, int alpha) {
        if (index + 13 >= text.length()) return null;

        char prefix = text.charAt(index);
        if (prefix != '\u00A7' && prefix != '&') return null;
        if (Character.toLowerCase(text.charAt(index + 1)) != 'x') return null;

        int rgb = 0;
        for (int i = 0; i < 6; i++) {
            int prefixIndex = index + 2 + i * 2;
            int digitIndex = prefixIndex + 1;

            if (text.charAt(prefixIndex) != prefix) return null;

            int hexDigit = Character.digit(text.charAt(digitIndex), 16);
            if (hexDigit < 0) return null;

            rgb = (rgb << 4) | hexDigit;
        }

        return new ParsedMinecraftHexColor(colourFromRgb(rgb, alpha), index + 14);
    }

    private static Color colourFromRgb(int rgb, int alpha) {
        return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha);
    }

    private float getAlignedTextStartX(String text, float x, String font, float size, Alignment alignment) {
        return switch (alignment) {
            case CENTER_TOP, CENTER_MIDDLE, CENTER_BOTTOM -> x - textWidth(text, font, size) / 2f;
            case RIGHT_TOP, RIGHT_MIDDLE, RIGHT_BOTTOM -> x - textWidth(text, font, size);
            default -> x;
        };
    }

    private Alignment leftAligned(Alignment alignment) {
        return switch (alignment) {
            case LEFT_MIDDLE, CENTER_MIDDLE, RIGHT_MIDDLE -> Alignment.LEFT_MIDDLE;
            case LEFT_BOTTOM, CENTER_BOTTOM, RIGHT_BOTTOM -> Alignment.LEFT_BOTTOM;
            default -> Alignment.LEFT_TOP;
        };
    }

    private static final class TextSegment {
        private final String text;
        private final Color colour;

        private TextSegment(String text, Color colour) {
            this.text = text;
            this.colour = colour;
        }
    }

    private static final class ParsedMinecraftHexColor {
        private final Color colour;
        private final int nextIndex;

        private ParsedMinecraftHexColor(Color colour, int nextIndex) {
            this.colour = colour;
            this.nextIndex = nextIndex;
        }
    }

    /**
     * Gets the height of a font.
     * Uses {@link NVGU#currentFont} and {@link NVGU#currentFontSize}
     * for the additional data.
     * If these aren't set, a {@link NullPointerException} with be thrown.
     *
     * @return the height of the font
     */
    public float textHeight() {
        return textHeight(currentFont, currentFontSize);
    }

    /**
     * Gets the height of a font.
     *
     * @param font the font to use
     * @param size the font size
     * @return the height of the font
     */
    public float textHeight(String font, float size) {
        nvgFontFace(handle, font);
        nvgFontSize(handle, size);
        nvgTextMetrics(handle, textAscender, textDescender, textLineHeight);

        return textLineHeight[0];
    }

    /**
     * Translates subsequent rendering to the given coordinates
     *
     * @param x horizontal coordinate
     * @param y vertical coordinate
     */
    public NVGU translate(float x, float y) {
        nvgTranslate(handle, x, y);

        return this;
    }

    public NVGU translate(float x, float y, Runnable runnable) {
        translate(x, y);
        try {
            runnable.run();
        } finally {
            translate(-x, -y);
        }
        return this;
    }

    /**
     * Rotates subsequent rendering by the given angle
     *
     * @param x     horizontal coordinate
     * @param y     vertical coordinate
     * @param angle angle of rotation (degrees)
     */
    public NVGU rotateDegrees(float x, float y, float angle) {
        translate(x, y);
        nvgRotate(handle, (float) Math.toRadians(angle));
        translate(-x, -y);

        return this;
    }

    /**
     * Rotates the rendering in the render block
     *
     * @param x      horizontal coordinate
     * @param y      vertical coordinate
     * @param angle  angle of rotation (degrees)
     * @param render what will be rotated and rendered
     */
    public NVGU rotateDegrees(float x, float y, float angle, Runnable render) {
        scope(() -> {
            rotateDegrees(x, y, angle);
            render.run();
        });

        return this;
    }

    /**
     * Rotates the rendering in the render block
     *
     * @param x      horizontal coordinate
     * @param y      vertical coordinate
     * @param angle  angle of rotation (radians)
     * @param render what will be rotated and rendered
     */
    public NVGU rotateRadians(float x, float y, float angle, Runnable render) {
        scope(() -> {
            rotateRadians(x, y, angle);
            render.run();
        });

        return this;
    }

    /**
     * Rotates subsequent rendering by the given angle
     *
     * @param x     horizontal coordinate
     * @param y     vertical coordinate
     * @param angle angle of rotation (radians)
     */
    public NVGU rotateRadians(float x, float y, float angle) {
        translate(x, y);
        nvgRotate(handle, angle);
        translate(-x, -y);

        return this;
    }

    /**
     * Scales subsequent rendering by the given factors
     *
     * @param x       horizontal coordinate
     * @param y       vertical coordinate
     * @param factorX horizontal scale factor
     * @param factorY vertical scale factor
     */
    public NVGU scale(float x, float y, float factorX, float factorY) {
        translate(x, y);
        nvgScale(handle, factorX, factorY);
        translate(-x, -y);

        return this;
    }

    /**
     * Scales subsequent rendering by the given factor
     *
     * @param x      horizontal coordinate
     * @param y      vertical coordinate
     * @param factor scale factor
     */
    public NVGU scale(float x, float y, float factor) {
        return scale(x, y, factor, factor);
    }

    private ShaderRect transformShaderRect(float x, float y, float width, float height, float radius) {
        nvgCurrentTransform(handle, shaderTransform);

        float x2 = x + width;
        float y2 = y + height;

        float p1x = transformX(shaderTransform, x, y);
        float p1y = transformY(shaderTransform, x, y);
        float p2x = transformX(shaderTransform, x2, y);
        float p2y = transformY(shaderTransform, x2, y);
        float p3x = transformX(shaderTransform, x2, y2);
        float p3y = transformY(shaderTransform, x2, y2);
        float p4x = transformX(shaderTransform, x, y2);
        float p4y = transformY(shaderTransform, x, y2);

        float minX = Math.min(Math.min(p1x, p2x), Math.min(p3x, p4x));
        float minY = Math.min(Math.min(p1y, p2y), Math.min(p3y, p4y));
        float maxX = Math.max(Math.max(p1x, p2x), Math.max(p3x, p4x));
        float maxY = Math.max(Math.max(p1y, p2y), Math.max(p3y, p4y));

        float scaleX = (float) Math.hypot(shaderTransform[0], shaderTransform[1]);
        float scaleY = (float) Math.hypot(shaderTransform[2], shaderTransform[3]);
        float scale = Math.max(0.001f, Math.max(scaleX, scaleY));

        return reusableShaderRect.set(minX, minY, maxX - minX, maxY - minY, radius * scale, scale, scaleX, scaleY);
    }

    private static float transformX(float[] transform, float x, float y) {
        return x * transform[0] + y * transform[2] + transform[4];
    }

    private static float transformY(float[] transform, float x, float y) {
        return x * transform[1] + y * transform[3] + transform[5];
    }

    private Color withGlobalAlpha(Color colour) {
        if (currentGlobalAlpha >= 0.999f)
            return colour;

        int alpha = Math.clamp(Math.round(colour.getAlpha() * currentGlobalAlpha), 0, 255);
        return new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), alpha);
    }

    private static final class ShaderRect {
        private float x;
        private float y;
        private float width;
        private float height;
        private float radius;
        private float scale;
        private float scaleX;
        private float scaleY;

        private ShaderRect set(float x, float y, float width, float height, float radius, float scale, float scaleX, float scaleY) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.radius = radius;
            this.scale = scale;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            return this;
        }
    }

    private static final class EffectBatch {
        private final ArrayList<Shader2DRenderer.BlurRequest> blurRequests = new ArrayList<>();
        private final ArrayList<Shader2DRenderer.ShadowRequest> shadowRequests = new ArrayList<>();
        private final ArrayList<Shader2DRenderer.BlurRequest> blurRequestPool = new ArrayList<>();
        private final ArrayList<Shader2DRenderer.ShadowRequest> shadowRequestPool = new ArrayList<>();

        private void addBlur(float x, float y, float width, float height, float radius, float blurRadius, float alpha) {
            int index = blurRequests.size();
            while (blurRequestPool.size() <= index)
                blurRequestPool.add(new Shader2DRenderer.BlurRequest());

            blurRequests.add(blurRequestPool.get(index).set(x, y, width, height, radius, blurRadius, alpha));
        }

        private void addShadow(float x, float y, float width, float height, float radius, float blurRadius,
                               float spread, float offsetX, float offsetY, Color colour) {
            int index = shadowRequests.size();
            while (shadowRequestPool.size() <= index)
                shadowRequestPool.add(new Shader2DRenderer.ShadowRequest());

            shadowRequests.add(shadowRequestPool.get(index).set(
                    x, y, width, height, radius, blurRadius, spread, offsetX, offsetY, colour
            ));
        }

        private void clear() {
            blurRequests.clear();
            shadowRequests.clear();
        }

        private boolean isEmpty() {
            return blurRequests.isEmpty() && shadowRequests.isEmpty();
        }
    }

    // utility methods

    /**
     * Creates an instance of {@link NVGColor} from the given {@link Color}
     *
     * @param colour the colour to transform into {@link NVGColor}
     */
    public NVGColor createAndStoreColour(Color colour) {
        NVGColor nvgColour;
        if (usedColours < colourPool.size()) {
            nvgColour = colourPool.get(usedColours);
        } else {
            nvgColour = NVGColor.calloc();
            colourPool.add(nvgColour);
        }
        usedColours++;

        return nvgColour.r(colour.getRed() / 255f)
                .g(colour.getGreen() / 255f)
                .b(colour.getBlue() / 255f)
                .a(colour.getAlpha() / 255f);
    }

    /**
     * Creates an instance of {@link NVGPaint}
     */
    public NVGPaint createAndStorePaint() {
        if (usedPaints < paintPool.size())
            return paintPool.get(usedPaints++);

        NVGPaint paint = NVGPaint.calloc();
        paintPool.add(paint);
        usedPaints++;
        return paint;
    }

    /**
     * Creates a linear gradient in an instance of an {@link NVGUColour}.
     * The position parameters will most likely be the same as the coordinates of whatever shape you are
     * drawing, e.g. a rectangle.
     * The feather will be the greatest of either width or height.
     *
     * @param x         start x coordinate of the gradient
     * @param y         start y coordinate of the gradient
     * @param width     width of the gradient
     * @param height    height of the gradient
     * @param start     start colour of the gradient
     * @param end       end colour of the gradient
     * @param direction direction of the gradient
     * @return instance of the gradient inside an {@link NVGUColour}
     */
    public NVGUColour linearGradient(float x, float y, float width, float height, Color start, Color end, LinearGradientDirection direction) {
        return linearGradient(x, y, width, height, Math.max(width, height), start, end, direction);
    }

    /**
     * Creates a linear gradient in an instance of an {@link NVGUColour}. The position
     * parameters will most likely be the same as the coordinates of whatever shape you are
     * drawing, e.g. a rectangle.
     *
     * @param x         start x coordinate of the gradient
     * @param y         start y coordinate of the gradient
     * @param width     width of the gradient
     * @param height    height of the gradient
     * @param feather   the distance for the gradient to apply between the two colours
     * @param start     start colour of the gradient
     * @param end       end colour of the gradient
     * @param direction direction of the gradient
     * @return instance of the gradient inside an {@link NVGUColour}
     */
    public NVGUColour linearGradient(float x, float y, float width, float height, float feather, Color start, Color end, LinearGradientDirection direction) {
        NVGUColour colour = new NVGUColour(createAndStorePaint());

        float startX = x;
        float startY = y;
        float endX = x + width;
        float endY = y;

        switch (direction) {
            case RIGHT_TO_LEFT:
                startX = x + width;
                endX = x;
                break;

            case TOP_TO_BOTTOM:
                startX = x;
                startY = y;
                endX = x;
                endY = y + height;
                break;

            case BOTTOM_TO_TOP:
                startX = x;
                startY = y + height;
                endX = x;
                endY = y;
                break;

            case DIAGONAL_LEFT_TO_RIGHT_UP:
                startY = y + height;
                endY = y;
                break;

            case DIAGONAL_LEFT_TO_RIGHT_DOWN:
                startY = y;
                endY = y + height;
                break;

            case DIAGONAL_RIGHT_TO_LEFT_UP:
                startX = x + width;
                startY = y + height;
                endX = x;
                endY = y;
                break;

            case DIAGONAL_RIGHT_TO_LEFT_DOWN:
                startX = x + width;
                startY = y;
                endX = x;
                endY = y + height;
                break;
        }

        colour.setPaint(nvgLinearGradient(handle, startX, startY, endX, endY, createAndStoreColour(start), createAndStoreColour(end), colour.getPaint()).feather(feather));

        return colour;
    }

    /**
     * Creates a radial gradient in an instance of an {@link NVGUColour}.
     * The position parameters will most likely be the same as the coordinates of whatever shape you are
     * drawing, e.g. a rectangle.
     * The feather will be the greatest of either width or height.
     *
     * @param x           start x coordinate of the gradient
     * @param y           start y coordinate of the gradient
     * @param width       width of the gradient
     * @param height      height of the gradient
     * @param innerRadius the inner radius of the gradient
     * @param outerRadius the outer radius of the gradient
     * @param start       start colour of the gradient
     * @param end         end colour of the gradient
     * @param alignment   alignment of the gradient
     * @return instance of the gradient inside an {@link NVGUColour}
     */
    public NVGUColour radialGradient(float x, float y, float width, float height, float innerRadius, float outerRadius, Color start, Color end, Alignment alignment) {
        return radialGradient(x, y, width, height, innerRadius, outerRadius, Math.max(width, height), start, end, alignment);
    }

    /**
     * Creates a radial gradient in an instance of an {@link NVGUColour}.
     * The position parameters will most likely be the same as the coordinates of whatever shape you are
     * drawing, e.g. a rectangle.
     *
     * @param x           start x coordinate of the gradient
     * @param y           start y coordinate of the gradient
     * @param width       width of the gradient
     * @param height      height of the gradient
     * @param innerRadius the inner radius of the gradient
     * @param outerRadius the outer radius of the gradient
     * @param feather     the distance for the gradient to apply between the two colours
     * @param start       start colour of the gradient
     * @param end         end colour of the gradient
     * @param alignment   alignment of the gradient
     * @return instance of the gradient inside an {@link NVGUColour}
     */
    public NVGUColour radialGradient(float x, float y, float width, float height, float innerRadius, float outerRadius, float feather, Color start, Color end, Alignment alignment) {
        NVGUColour colour = new NVGUColour(createAndStorePaint());

        float startX = x;
        float startY = y;

        switch (alignment) {
            case CENTER_TOP:
                startX = x + width / 2f;
                break;

            case RIGHT_TOP:
                startX = x + width;
                break;

            case LEFT_MIDDLE:
                startY = y + height / 2f;
                break;

            case CENTER_MIDDLE:
                startX = x + width / 2f;
                startY = y + height / 2f;
                break;

            case RIGHT_MIDDLE:
                startX = x + width;
                startY = y + height / 2f;
                break;

            case LEFT_BOTTOM:
                startY = y + height;
                break;

            case CENTER_BOTTOM:
                startX = x + width / 2f;
                startY = y + height;
                break;

            case RIGHT_BOTTOM:
                startX = x + width;
                startY = y + height;
                break;
        }

        colour.setPaint(nvgRadialGradient(handle, startX, startY, innerRadius, outerRadius, createAndStoreColour(start), createAndStoreColour(end), colour.getPaint()).feather(feather));

        return colour;
    }

    /**
     * Creates a texture (based on the given identifier) at the given coordinates
     *
     * @param identifier the key used to identify the texture
     * @param x          x coordinate of the rectangle
     * @param y          y coordinate of the rectangle
     * @param width      width of the texture
     * @param height     height of the texture
     * @return the texture as a {@link NVGUColour}
     */
    public NVGUColour texture(String identifier, float x, float y, float width, float height) {
        Integer imageHandle = textures.get(identifier);
        if (imageHandle == null || imageHandle <= 0)
            throw new IllegalArgumentException("Unknown NanoVG texture: " + identifier);

        NVGUColour colour = new NVGUColour(createAndStorePaint());

        nvgImagePattern(handle, x, y, width, height, 0, imageHandle, 1f, colour.getPaint());

        return colour;
    }

    /**
     * Creates a scissor box with the specified bounds
     *
     * @param x      x coordinate
     * @param y      y coordinate
     * @param width  width of the box
     * @param height height of the box
     */
    public NVGU pushScissor(float x, float y, float width, float height) {
        nvgSave(handle);
        nvgIntersectScissor(handle, x, y, width, height);
        return this;
    }

    /**
     * Essentially just a wrapper around {@link #save()}, but should be useful for
     * readability
     */
    public NVGU popScissor() {
        nvgRestore(handle);
        return this;
    }

    /**
     * Creates a scope for a scissor box with the specified bounds
     *
     * @param x      x coordinate of the box
     * @param y      y coordinate of the box
     * @param width  width of the box
     * @param height height of the box
     * @param block  the scoped code
     */
    public NVGU scissor(float x, float y, float width, float height, Runnable block) {
        pushScissor(x, y, width, height);
        try {
            block.run();
        } finally {
            popScissor();
        }

        return this;
    }

    /**
     * Frees all resources allocated by this object
     */
    public NVGU freeResources() {
        Throwable failure = null;
        for (NVGColor colour : colourPool)
            failure = runCleanup(failure, colour::free);
        for (NVGPaint paint : paintPool)
            failure = runCleanup(failure, paint::free);
        colourPool.clear();
        paintPool.clear();
        recycleFrameResources();

        rethrowCleanupFailure(failure);
        return this;
    }

    private void recycleFrameResources() {
        usedColours = 0;
        usedPaints = 0;
    }

    /**
     * Gets the handle of the NanoVG instance.
     *
     * @return the handle of the NanoVG instance
     */
    public long getHandle() {
        return handle;
    }

    /**
     * Returns whether the native NanoVG context is ready for drawing.
     */
    public boolean isCreated() {
        return handle != -1;
    }

    /**
     * Allows you to execute code without breaking out of a chain of method calls
     *
     * @param runnable the code to be executed
     */
    public NVGU also(Runnable runnable) {
        runnable.run();

        return this;
    }

    private ByteBuffer getBytes(InputStream stream, int size) {
        ByteBuffer buffer = BufferUtils.createByteBuffer(size);
        if (stream == null) {
            buffer.flip();
            return MemoryUtil.memSlice(buffer);
        }

        try (ReadableByteChannel channel = Channels.newChannel(stream)) {
            while (true) {
                int bytes = channel.read(buffer);

                if (bytes == -1) {
                    break;
                }

                if (buffer.remaining() == 0) {
                    ByteBuffer newBuffer = BufferUtils.createByteBuffer(buffer.capacity() * 3 / 2);
                    buffer.flip();
                    newBuffer.put(buffer);
                    buffer = newBuffer;
                }
            }
        } catch (IOException ignored) {
        }

        buffer.flip();

        return MemoryUtil.memSlice(buffer);
    }

    public Map<String, Integer> getTextures() {
        return textures;
    }

}
