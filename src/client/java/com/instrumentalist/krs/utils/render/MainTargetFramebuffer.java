package com.instrumentalist.krs.utils.render;

import com.instrumentalist.krs.utils.IMinecraft;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.backend.api.GpuDeviceBackend;
import com.mojang.renderpearl.backend.opengl.GlDevice;
import com.mojang.renderpearl.backend.opengl.GlTextureView;
import com.mojang.renderpearl.frontend.FrontendGpuDevice;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class MainTargetFramebuffer implements IMinecraft {
    private static final int[] VIEWPORT = new int[4];
    private static final Map<GraphicsApiCompatibility.Layer, OpenGlCachedLayer> CACHED_LAYERS = new EnumMap<>(GraphicsApiCompatibility.Layer.class);
    private static final ByteBuffer COLOR_MASK = BufferUtils.createByteBuffer(4);
    private static final float[] CLEAR_COLOR = new float[4];
    private static int ownFramebuffer;
    private static int ownRenderbuffer;
    private static int ownColorTexture;
    private static int ownMipLevel;
    private static int ownWidth;
    private static int ownHeight;
    private static boolean ownFailed;

    private MainTargetFramebuffer() {
    }

    public static void runLayer(GraphicsApiCompatibility.Layer layer, Runnable render, Runnable discard) {
        if (GraphicsApiCompatibility.usesCompatibilityRenderer()) {
            GraphicsApiCompatibility.renderOffscreenLayer(layer, render, discard);
            return;
        }
        if (layer == GraphicsApiCompatibility.Layer.NANO_VG_WORLD) {
            run(render);
            return;
        }
        OpenGlCachedLayer cache = CACHED_LAYERS.computeIfAbsent(layer, ignored -> new OpenGlCachedLayer());
        run(() -> cache.render(render, discard));
    }

    public static void destroyLayers() {
        for (OpenGlCachedLayer cache : CACHED_LAYERS.values())
            cache.destroy();
        CACHED_LAYERS.clear();
    }

    public static void run(Runnable action) {
        RenderTarget target = mc.gameRenderer.mainRenderTarget();
        int framebuffer = framebuffer(target);
        if (framebuffer <= 0) {
            action.run();
            return;
        }

        int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, VIEWPORT);
        int viewportX = VIEWPORT[0];
        int viewportY = VIEWPORT[1];
        int viewportWidth = VIEWPORT[2];
        int viewportHeight = VIEWPORT[3];
        int unpackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        int packBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int unpackAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        int unpackRowLength = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
        int unpackSkipRows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS);
        int unpackSkipPixels = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
        boolean srgb = GL11.glIsEnabled(GL30.GL_FRAMEBUFFER_SRGB);
        boolean polygonOffset = GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL);
        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            GL11.glViewport(0, 0, target.width, target.height);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            resetAlpha();
            action.run();
        } finally {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GL11.glViewport(viewportX, viewportY, viewportWidth, viewportHeight);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, packBuffer);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, unpackAlignment);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, unpackRowLength);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, unpackSkipRows);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, unpackSkipPixels);
            if (srgb)
                GL11.glEnable(GL30.GL_FRAMEBUFFER_SRGB);
            if (polygonOffset)
                GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        }
    }

    private static void resetAlpha() {
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, COLOR_MASK);
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, CLEAR_COLOR);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glColorMask(false, false, false, true);
        GL11.glClearColor(0f, 0f, 0f, 1f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        int stencilMask = GL11.glGetInteger(GL11.GL_STENCIL_WRITEMASK);
        int clearStencil = GL11.glGetInteger(GL11.GL_STENCIL_CLEAR_VALUE);
        GL11.glStencilMask(0xFF);
        GL11.glClearStencil(0);
        GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        GL11.glStencilMask(stencilMask);
        GL11.glClearStencil(clearStencil);
        GL11.glColorMask(COLOR_MASK.get(0) != 0, COLOR_MASK.get(1) != 0, COLOR_MASK.get(2) != 0, COLOR_MASK.get(3) != 0);
        GL11.glClearColor(CLEAR_COLOR[0], CLEAR_COLOR[1], CLEAR_COLOR[2], CLEAR_COLOR[3]);
        if (scissor)
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
    }

    private static int framebuffer(RenderTarget target) {
        if (target == null || target.width <= 0 || target.height <= 0)
            return 0;

        GpuDeviceBackend backend = RenderSystem.getDevice() instanceof FrontendGpuDevice frontendDevice
                ? frontendDevice.backend
                : null;
        if (!(backend instanceof GlDevice glDevice) || !(target.getColorTextureView() instanceof GlTextureView colorView))
            return 0;

        int stencilFramebuffer = stencilFramebuffer(colorView.glId(), colorView.fboMipLevel(), target.width, target.height);
        if (stencilFramebuffer > 0)
            return stencilFramebuffer;

        GlTextureView depthView = target.getDepthTextureView() instanceof GlTextureView view ? view : null;
        return glDevice.frameBufferCache().getFbo(glDevice.directStateAccess(), List.of(colorView), depthView);
    }

    private static int stencilFramebuffer(int colorTexture, int mipLevel, int width, int height) {
        if (ownFramebuffer != 0 && ownColorTexture == colorTexture && ownMipLevel == mipLevel && ownWidth == width && ownHeight == height)
            return ownFramebuffer;
        if (ownFailed)
            return 0;

        int previousFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int previousRenderbuffer = GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);
        try {
            if (ownRenderbuffer == 0 || ownWidth != width || ownHeight != height) {
                if (ownRenderbuffer != 0)
                    GL30.glDeleteRenderbuffers(ownRenderbuffer);
                ownRenderbuffer = GL30.glGenRenderbuffers();
                GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, ownRenderbuffer);
                GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH24_STENCIL8, width, height);
            }
            if (ownFramebuffer == 0)
                ownFramebuffer = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, ownFramebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, colorTexture, mipLevel);
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT, GL30.GL_RENDERBUFFER, ownRenderbuffer);
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
                ownFailed = true;
                GL30.glDeleteFramebuffers(ownFramebuffer);
                GL30.glDeleteRenderbuffers(ownRenderbuffer);
                ownFramebuffer = 0;
                ownRenderbuffer = 0;
                return 0;
            }
            ownColorTexture = colorTexture;
            ownMipLevel = mipLevel;
            ownWidth = width;
            ownHeight = height;
            return ownFramebuffer;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, previousRenderbuffer);
        }
    }
}
