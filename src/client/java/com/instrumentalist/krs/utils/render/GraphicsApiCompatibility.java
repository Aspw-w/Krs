package com.instrumentalist.krs.utils.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

import static org.lwjgl.glfw.GLFW.GLFW_CLIENT_API;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MAJOR;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MINOR;
import static org.lwjgl.glfw.GLFW.GLFW_FALSE;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_API;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_CORE_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_FORWARD_COMPAT;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_TRUE;
import static org.lwjgl.glfw.GLFW.GLFW_VISIBLE;
import static org.lwjgl.glfw.GLFW.glfwCreateWindow;
import static org.lwjgl.glfw.GLFW.glfwDefaultWindowHints;
import static org.lwjgl.glfw.GLFW.glfwDestroyWindow;
import static org.lwjgl.glfw.GLFW.glfwGetCurrentContext;
import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;
import static org.lwjgl.glfw.GLFW.glfwSwapInterval;
import static org.lwjgl.glfw.GLFW.glfwWindowHint;

/**
 * Keeps the legacy NanoVG and ImGui OpenGL renderers usable when Minecraft is
 * running on a non-OpenGL graphics backend.
 *
 * <p>The legacy renderers draw into a transparent framebuffer owned by a small
 * hidden OpenGL context. The pixels are then uploaded through Minecraft's
 * backend-neutral GPU API and composited over the active render target. No
 * OpenGL calls are made against Minecraft's Vulkan window.</p>
 */
public final class GraphicsApiCompatibility {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float MAX_SCALED_LAYER_PIXEL_RATIO = 2.0f;
    private static final long CONTINUOUS_LAYER_TIMEOUT_NANOS = 100_000_000L;
    private static final int TRANSFER_SLOT_COUNT = 2;
    private static final int TRANSFER_FREE = 0;
    private static final int TRANSFER_COPYING = 1;
    private static final int TRANSFER_READY = 2;
    private static final int TRANSFER_GPU_IN_FLIGHT = 3;
    private static final int FALLBACK_REFRESH_RATE = 120;
    private static final long REFRESH_RATE_CACHE_NANOS = 1_000_000_000L;

    public enum Layer {
        NANO_VG(true, -1),
        NANO_VG_BEFORE_GUI(true, -1),
        NANO_VG_IMMEDIATE(true, -1),
        ESP(true, 0),
        IMGUI(false, -1);

        private final boolean scaled;
        private final int maximumFramesPerSecond;

        Layer(boolean scaled, int maximumFramesPerSecond) {
            this.scaled = scaled;
            this.maximumFramesPerSecond = maximumFramesPerSecond;
        }
    }

    private static final RenderPipeline COMPOSITE_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("krs", "pipeline/legacy_ui_composite"))
            .withVertexShader("core/screenquad")
            .withFragmentShader("core/blit_screen")
            .withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build();

    private static boolean initialized;
    private static boolean compatibilityRenderer;
    private static boolean layerActive;
    private static int activeLayerWidth;
    private static int activeLayerHeight;

    private static long offscreenWindow;
    private static GLCapabilities offscreenCapabilities;
    private static final OpenGlFramebuffer SCALED_FRAMEBUFFER = new OpenGlFramebuffer();
    private static final OpenGlFramebuffer NATIVE_FRAMEBUFFER = new OpenGlFramebuffer();
    private static final Map<Layer, LayerCache> LAYER_CACHES = new EnumMap<>(Layer.class);
    private static ExecutorService transferExecutor;
    private static long lastTransferFailureLogNanos;
    private static long refreshRateCacheExpiresNanos;
    private static int cachedRefreshRate = FALLBACK_REFRESH_RATE;

    private GraphicsApiCompatibility() {
    }

    public static void initialize() {
        if (initialized)
            return;

        GpuDevice device = RenderSystem.getDevice();
        compatibilityRenderer = !(device.backend instanceof GlDevice);
        if (compatibilityRenderer) {
            createOffscreenContext();
            transferExecutor = Executors.newFixedThreadPool(2, task -> {
                Thread thread = new Thread(task, "Krs compatibility transfer");
                thread.setDaemon(true);
                return thread;
            });
            LOGGER.info("Krs legacy UI compatibility renderer enabled for graphics backend {}",
                    device.getDeviceInfo().backendName());
        }

        initialized = true;
    }

    public static boolean usesCompatibilityRenderer() {
        return initialized && compatibilityRenderer;
    }

    public static boolean isLayerActive() {
        return layerActive;
    }

    public static int getActiveLayerWidth() {
        return activeLayerWidth;
    }

    public static int getActiveLayerHeight() {
        return activeLayerHeight;
    }

    public static void runWithOpenGlContext(Runnable action) {
        if (action == null)
            return;
        if (!usesCompatibilityRenderer()) {
            action.run();
            return;
        }
        if (offscreenWindow == 0L || offscreenCapabilities == null)
            throw new IllegalStateException("The Krs OpenGL compatibility context is not available");

        long previousContext = glfwGetCurrentContext();
        if (previousContext == offscreenWindow) {
            action.run();
            return;
        }

        GLCapabilities previousCapabilities = currentCapabilitiesOrNull();
        glfwMakeContextCurrent(offscreenWindow);
        GL.setCapabilities(offscreenCapabilities);
        try {
            action.run();
        } finally {
            glfwMakeContextCurrent(previousContext);
            GL.setCapabilities(previousCapabilities);
        }
    }

    /**
     * Renders one transparent legacy UI layer and composites it over the
     * current Minecraft main target. Calls may be nested by immediate-mode
     * helpers without clearing the layer again.
     */
    public static void renderOffscreenLayer(Runnable renderer) {
        renderOffscreenLayer(Layer.NANO_VG_IMMEDIATE, renderer, null);
    }

    public static void renderOffscreenLayer(Layer layer, Runnable renderer) {
        renderOffscreenLayer(layer, renderer, null);
    }

    /**
     * Renders a cached compatibility layer. UI layers follow the active
     * display refresh rate at a maximum device-pixel ratio of two. ESP requests
     * every game frame so camera transforms cannot outpace its cached image.
     * The asynchronous transfer queue applies backpressure when either GPU is
     * unable to keep up.
     *
     * @param skipped invoked instead of {@code renderer} when a cached frame is
     *                reused; queue-backed renderers use this to drop stale work
     */
    public static void renderOffscreenLayer(Layer layer, Runnable renderer, Runnable skipped) {
        if (renderer == null)
            return;
        if (!usesCompatibilityRenderer()) {
            renderer.run();
            return;
        }
        if (layerActive) {
            renderer.run();
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gameRenderer == null)
            return;

        RenderTarget target = minecraft.gameRenderer.mainRenderTarget();
        if (target == null || target.width <= 0 || target.height <= 0
                || target.getColorTextureView() == null)
            return;

        int[] renderSize = getRenderSize(layer, target.width, target.height);
        int width = renderSize[0];
        int height = renderSize[1];
        LayerCache cache = LAYER_CACHES.computeIfAbsent(layer, ignored -> new LayerCache());
        long now = System.nanoTime();
        boolean sizeChanged = cache.width != width || cache.height != height;
        if (sizeChanged)
            resetLayerCache(cache, width, height);

        boolean resumed = cache.lastInvocationNanos == 0L
                || now - cache.lastInvocationNanos > CONTINUOUS_LAYER_TIMEOUT_NANOS;
        cache.lastInvocationNanos = now;
        if (resumed && !sizeChanged)
            invalidateLayerCache(cache);

        reclaimGpuTransfers(cache);
        uploadNewestReadyTransfer(cache);

        long updateIntervalNanos = getUpdateIntervalNanos(layer, now);
        boolean update = updateIntervalNanos == 0L
                || cache.nextRenderNanos == 0L
                || now >= cache.nextRenderNanos;

        OpenGlFramebuffer glFramebuffer = layer.scaled ? SCALED_FRAMEBUFFER : NATIVE_FRAMEBUFFER;
        boolean rendered = false;
        if (update) {
            boolean[] issued = new boolean[1];
            runWithOpenGlContext(() -> issued[0] = advanceReadbackPipeline(
                    glFramebuffer, cache, width, height, renderer
            ));
            rendered = issued[0];
            if (rendered)
                scheduleNextRender(cache, now, updateIntervalNanos);
        }

        if (!rendered && skipped != null)
            skipped.run();

        compositeCachedLayer(target, cache, width, height);
    }

    private static long getUpdateIntervalNanos(Layer layer, long now) {
        int maximumFramesPerSecond = layer.maximumFramesPerSecond;
        if (maximumFramesPerSecond == 0)
            return 0L;
        if (maximumFramesPerSecond < 0)
            maximumFramesPerSecond = getDisplayRefreshRate(now);
        return 1_000_000_000L / Math.max(1, maximumFramesPerSecond);
    }

    private static int getDisplayRefreshRate(long now) {
        if (now < refreshRateCacheExpiresNanos)
            return cachedRefreshRate;

        int refreshRate = 0;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.getWindow() != null)
            refreshRate = minecraft.getWindow().getRefreshRate();
        if (refreshRate < 30) {
            int frameLimit = minecraft == null ? 0 : minecraft.options.framerateLimit().get();
            refreshRate = frameLimit >= 30 ? frameLimit : FALLBACK_REFRESH_RATE;
        }

        cachedRefreshRate = refreshRate;
        refreshRateCacheExpiresNanos = now + REFRESH_RATE_CACHE_NANOS;
        return refreshRate;
    }

    private static void scheduleNextRender(LayerCache cache, long now, long intervalNanos) {
        if (intervalNanos == 0L) {
            cache.nextRenderNanos = 0L;
            return;
        }

        long next = cache.nextRenderNanos;
        if (next == 0L || now - next > intervalNanos * 4L) {
            cache.nextRenderNanos = now + intervalNanos;
            return;
        }

        do {
            next += intervalNanos;
        } while (next <= now);
        cache.nextRenderNanos = next;
    }

    private static int[] getRenderSize(Layer layer, int targetWidth, int targetHeight) {
        if (!layer.scaled)
            return new int[]{targetWidth, targetHeight};

        Minecraft minecraft = Minecraft.getInstance();
        int logicalWidth = Math.max(1, minecraft.getWindow().getScreenWidth());
        int logicalHeight = Math.max(1, minecraft.getWindow().getScreenHeight());
        float pixelRatio = Math.max(
                targetWidth / (float) logicalWidth,
                targetHeight / (float) logicalHeight
        );
        float scale = Math.min(1.0f, MAX_SCALED_LAYER_PIXEL_RATIO / Math.max(1.0f, pixelRatio));
        return new int[]{
                Math.max(1, Math.round(targetWidth * scale)),
                Math.max(1, Math.round(targetHeight * scale))
        };
    }

    private static void createOffscreenContext() {
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_API);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);

        try {
            offscreenWindow = glfwCreateWindow(1, 1, "Krs OpenGL compatibility renderer", 0L, 0L);
        } finally {
            // Window hints are process-global. Do not leak the compatibility
            // context settings into windows created by Minecraft or other mods.
            glfwDefaultWindowHints();
        }

        if (offscreenWindow == 0L)
            throw new IllegalStateException("Could not create the Krs OpenGL compatibility context");

        long previousContext = glfwGetCurrentContext();
        GLCapabilities previousCapabilities = currentCapabilitiesOrNull();
        glfwMakeContextCurrent(offscreenWindow);
        try {
            offscreenCapabilities = GL.createCapabilities();
            glfwSwapInterval(0);
        } catch (RuntimeException | Error failure) {
            glfwMakeContextCurrent(previousContext);
            GL.setCapabilities(previousCapabilities);
            glfwDestroyWindow(offscreenWindow);
            offscreenWindow = 0L;
            throw failure;
        }

        glfwMakeContextCurrent(previousContext);
        GL.setCapabilities(previousCapabilities);
    }

    private static GLCapabilities currentCapabilitiesOrNull() {
        try {
            return GL.getCapabilities();
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    private static boolean advanceReadbackPipeline(OpenGlFramebuffer target, LayerCache cache,
                                                   int width, int height, Runnable renderer) {
        reclaimFinishedReadbackCopies(cache);
        dispatchLatestCompletedReadback(cache);

        ReadbackSlot slot = findFreeReadbackSlot(cache);
        if (slot == null)
            return false;

        ensureOpenGlFramebuffer(target, width, height);
        ensureReadbackBuffer(slot, cache.byteCount);
        renderAndQueueReadback(target, cache, slot, width, height, renderer);
        return true;
    }

    private static void renderAndQueueReadback(OpenGlFramebuffer target, LayerCache cache, ReadbackSlot slot,
                                               int width, int height, Runnable renderer) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, target.framebuffer);
        try {
            GL11.glViewport(0, 0, width, height);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glColorMask(true, true, true, true);
            GL11.glDepthMask(true);
            GL11.glStencilMask(0xFF);
            GL11.glClearColor(0f, 0f, 0f, 0f);
            GL11.glClearDepth(1.0);
            GL11.glClearStencil(0);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);

            layerActive = true;
            activeLayerWidth = width;
            activeLayerHeight = height;
            try {
                renderer.run();
            } finally {
                layerActive = false;
                activeLayerWidth = 0;
                activeLayerHeight = 0;
            }

            int packAlignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
            int packRowLength = GL11.glGetInteger(GL12.GL_PACK_ROW_LENGTH);
            int pixelPackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
            try {
                GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, slot.buffer);
                GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
                GL11.glPixelStorei(GL12.GL_PACK_ROW_LENGTH, 0);
                GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);
                slot.fence = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
                slot.sequence = ++cache.nextSequence;
                slot.generation = cache.generation;
                GL11.glFlush();
            } finally {
                GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, packAlignment);
                GL11.glPixelStorei(GL12.GL_PACK_ROW_LENGTH, packRowLength);
                GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pixelPackBuffer);
            }
        } finally {
            layerActive = false;
            activeLayerWidth = 0;
            activeLayerHeight = 0;
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        }
    }

    private static void dispatchLatestCompletedReadback(LayerCache cache) {
        TransferSlot transfer = findFreeTransferSlot(cache);
        if (transfer == null)
            return;

        ReadbackSlot newest = null;
        for (ReadbackSlot slot : cache.readbackSlots) {
            if (slot.fence == 0L)
                continue;

            int status = GL32.glClientWaitSync(slot.fence, 0, 0L);
            if (status == GL32.GL_TIMEOUT_EXPIRED)
                continue;
            if (status == GL32.GL_WAIT_FAILED) {
                releaseSignaledReadback(slot);
                logTransferFailure(new IllegalStateException("OpenGL failed to poll a compatibility readback fence"));
                continue;
            }

            if (slot.generation != cache.generation) {
                releaseSignaledReadback(slot);
                continue;
            }

            if (newest == null || slot.sequence > newest.sequence) {
                if (newest != null)
                    releaseSignaledReadback(newest);
                newest = slot;
            } else {
                releaseSignaledReadback(slot);
            }
        }

        if (newest == null)
            return;

        int previousBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, newest.buffer);
        ByteBuffer mapped = GL30.glMapBufferRange(
                GL21.GL_PIXEL_PACK_BUFFER, 0L, cache.byteCount, GL30.GL_MAP_READ_BIT
        );
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, previousBuffer);
        releaseReadbackFence(newest);
        if (mapped == null) {
            logTransferFailure(new IllegalStateException("Could not map a completed OpenGL compatibility readback"));
            return;
        }

        ReadbackSlot readback = newest;
        readback.mappedBuffer = mapped;
        readback.copyComplete = false;
        transfer.sequence = readback.sequence;
        transfer.generation = readback.generation;
        transfer.state = TRANSFER_COPYING;

        ExecutorService executor = transferExecutor;
        try {
            readback.copyTask = executor.submit(() -> copyReadbackOnWorker(cache, readback, transfer));
        } catch (RejectedExecutionException exception) {
            transfer.state = TRANSFER_FREE;
            readback.copyComplete = true;
            logTransferFailure(exception);
        }
    }

    private static void copyReadbackOnWorker(LayerCache cache, ReadbackSlot readback, TransferSlot transfer) {
        try {
            MemoryUtil.memCopy(
                    MemoryUtil.memAddress(readback.mappedBuffer),
                    MemoryUtil.memAddress(transfer.mapping.data()),
                    cache.byteCount
            );
            transfer.state = TRANSFER_READY;
        } catch (Throwable failure) {
            transfer.state = TRANSFER_FREE;
            logTransferFailure(failure);
        } finally {
            readback.copyComplete = true;
        }
    }

    private static void reclaimFinishedReadbackCopies(LayerCache cache) {
        int previousBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        try {
            for (ReadbackSlot slot : cache.readbackSlots) {
                if (slot.mappedBuffer == null || !slot.copyComplete)
                    continue;

                GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, slot.buffer);
                if (!GL15.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER))
                    logTransferFailure(new IllegalStateException("OpenGL compatibility readback data became corrupt"));
                slot.mappedBuffer = null;
                slot.copyTask = null;
                slot.copyComplete = false;
                slot.sequence = 0L;
                slot.generation = 0L;
            }
        } finally {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, previousBuffer);
        }
    }

    private static ReadbackSlot findFreeReadbackSlot(LayerCache cache) {
        for (ReadbackSlot slot : cache.readbackSlots) {
            if (slot.fence == 0L && slot.mappedBuffer == null)
                return slot;
        }
        return null;
    }

    private static TransferSlot findFreeTransferSlot(LayerCache cache) {
        for (TransferSlot slot : cache.transferSlots) {
            if (slot.state == TRANSFER_FREE)
                return slot;
        }
        return null;
    }

    private static void releaseSignaledReadback(ReadbackSlot slot) {
        releaseReadbackFence(slot);
        slot.sequence = 0L;
        slot.generation = 0L;
    }

    private static void releaseReadbackFence(ReadbackSlot slot) {
        if (slot.fence != 0L) {
            GL32.glDeleteSync(slot.fence);
            slot.fence = 0L;
        }
    }

    private static void ensureReadbackBuffer(ReadbackSlot slot, int byteCount) {
        if (slot.buffer != 0)
            return;

        int previousBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        slot.buffer = GL15.glGenBuffers();
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, slot.buffer);
        GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, byteCount, GL15.GL_STREAM_READ);
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, previousBuffer);
    }

    private static synchronized void logTransferFailure(Throwable failure) {
        long now = System.nanoTime();
        if (lastTransferFailureLogNanos != 0L && now - lastTransferFailureLogNanos < 10_000_000_000L)
            return;
        lastTransferFailureLogNanos = now;
        LOGGER.error("Failed to transfer a legacy UI frame without blocking the render thread", failure);
    }

    private static void ensureOpenGlFramebuffer(OpenGlFramebuffer target, int width, int height) {
        if (target.framebuffer != 0 && target.width == width && target.height == height)
            return;

        destroyOpenGlFramebuffer(target);

        target.framebuffer = GL30.glGenFramebuffers();
        target.colorTexture = GL11.glGenTextures();
        target.depthStencilRenderbuffer = GL30.glGenRenderbuffers();

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, target.framebuffer);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, target.colorTexture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, target.colorTexture, 0);

        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, target.depthStencilRenderbuffer);
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH24_STENCIL8, width, height);
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT,
                GL30.GL_RENDERBUFFER, target.depthStencilRenderbuffer);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);

        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            destroyOpenGlFramebuffer(target);
            throw new IllegalStateException("Incomplete Krs compatibility framebuffer: 0x"
                    + Integer.toHexString(status));
        }

        target.width = width;
        target.height = height;
    }

    private static void resetLayerCache(LayerCache cache, int width, int height) {
        awaitWorkerCopies(cache);
        runWithOpenGlContext(() -> destroyReadbackResources(cache));
        destroyTransferResources(cache);
        destroyUploadTextures(cache);

        cache.width = width;
        cache.height = height;
        cache.byteCount = Math.multiplyExact(Math.multiplyExact(width, height), 4);
        cache.generation++;
        cache.nextSequence = 0L;
        cache.nextRenderNanos = 0L;
        cache.activeTextureIndex = -1;

        ensureTransferResources(cache);
        ensureUploadTextures(cache);
    }

    private static void invalidateLayerCache(LayerCache cache) {
        cache.generation++;
        cache.nextRenderNanos = 0L;
        cache.activeTextureIndex = -1;
    }

    private static void ensureTransferResources(LayerCache cache) {
        GpuDevice device = RenderSystem.getDevice();
        int usage = GpuBuffer.USAGE_MAP_WRITE
                | GpuBuffer.USAGE_HINT_CLIENT_STORAGE
                | GpuBuffer.USAGE_COPY_SRC;
        for (int index = 0; index < cache.transferSlots.length; index++) {
            TransferSlot slot = cache.transferSlots[index];
            if (slot.buffer != null && !slot.buffer.isClosed())
                continue;

            int slotIndex = index;
            slot.buffer = device.createBuffer(
                    () -> "Krs legacy UI staging " + slotIndex,
                    usage,
                    cache.byteCount
            );
            slot.mapping = slot.buffer.map(false, true);
            slot.state = TRANSFER_FREE;
        }
    }

    private static void ensureUploadTextures(LayerCache cache) {
        boolean valid = true;
        for (GpuTexture texture : cache.textures)
            valid &= texture != null && !texture.isClosed();
        if (valid)
            return;

        destroyUploadTextures(cache);
        GpuDevice device = RenderSystem.getDevice();
        for (int index = 0; index < cache.textures.length; index++) {
            cache.textures[index] = device.createTexture(
                    "Krs legacy UI upload " + index,
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    GpuFormat.RGBA8_UNORM,
                    cache.width,
                    cache.height,
                    1,
                    1
            );
            cache.textureViews[index] = device.createTextureView(cache.textures[index]);
        }
    }

    private static void reclaimGpuTransfers(LayerCache cache) {
        for (TransferSlot slot : cache.transferSlots) {
            if (slot.state != TRANSFER_GPU_IN_FLIGHT)
                continue;

            GpuFence fence = slot.gpuFence;
            if (fence != null && !fence.awaitCompletion(0L))
                continue;

            if (fence != null)
                fence.close();
            slot.gpuFence = null;
            slot.state = TRANSFER_FREE;
        }
    }

    private static void uploadNewestReadyTransfer(LayerCache cache) {
        TransferSlot newest = null;
        for (TransferSlot slot : cache.transferSlots) {
            if (slot.state != TRANSFER_READY)
                continue;
            if (slot.generation != cache.generation) {
                slot.state = TRANSFER_FREE;
                continue;
            }
            if (newest == null || slot.sequence > newest.sequence) {
                if (newest != null)
                    newest.state = TRANSFER_FREE;
                newest = slot;
            } else {
                slot.state = TRANSFER_FREE;
            }
        }

        if (newest == null)
            return;

        ensureUploadTextures(cache);
        int textureIndex = (cache.activeTextureIndex + 1) % cache.textures.length;
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyBufferToTexture(
                newest.buffer.slice(),
                0, 0, cache.width, cache.height,
                cache.textures[textureIndex],
                0, 0, cache.width, cache.height,
                0, 0
        );
        newest.gpuFence = encoder.createFence();
        newest.state = TRANSFER_GPU_IN_FLIGHT;
        cache.activeTextureIndex = textureIndex;
    }

    private static void compositeCachedLayer(RenderTarget target, LayerCache cache, int width, int height) {
        int textureIndex = cache.activeTextureIndex;
        if (textureIndex < 0)
            return;

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        GpuTextureView depthView = target.getDepthTextureView();
        try (RenderPass pass = depthView == null
                ? encoder.createRenderPass(() -> "Krs legacy UI composite", target.getColorTextureView(), Optional.empty())
                : encoder.createRenderPass(() -> "Krs legacy UI composite", target.getColorTextureView(), Optional.empty(),
                        depthView, OptionalDouble.empty())) {
            pass.setPipeline(COMPOSITE_PIPELINE);
            pass.bindTexture("InSampler", cache.textureViews[textureIndex],
                    RenderSystem.getSamplerCache().getClampToEdge(
                            width == target.width && height == target.height ? FilterMode.NEAREST : FilterMode.LINEAR
                    ));
            pass.draw(3, 1, 0, 0);
        }
    }

    private static void awaitWorkerCopies(LayerCache cache) {
        for (ReadbackSlot slot : cache.readbackSlots) {
            Future<?> task = slot.copyTask;
            if (task == null)
                continue;
            try {
                task.get();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while draining compatibility transfers", exception);
            } catch (Exception exception) {
                logTransferFailure(exception);
            }
        }
    }

    public static void shutdown() {
        if (!initialized)
            return;

        ExecutorService executor = transferExecutor;
        if (executor != null)
            executor.shutdown();

        for (LayerCache cache : LAYER_CACHES.values())
            awaitWorkerCopies(cache);

        if (compatibilityRenderer && offscreenWindow != 0L) {
            runWithOpenGlContext(() -> {
                for (LayerCache cache : LAYER_CACHES.values())
                    destroyReadbackResources(cache);
                destroyOpenGlFramebuffer(SCALED_FRAMEBUFFER);
                destroyOpenGlFramebuffer(NATIVE_FRAMEBUFFER);
            });
            glfwDestroyWindow(offscreenWindow);
        }

        for (LayerCache cache : LAYER_CACHES.values()) {
            destroyTransferResources(cache);
            destroyUploadTextures(cache);
        }
        LAYER_CACHES.clear();
        if (executor != null)
            executor.shutdownNow();

        offscreenWindow = 0L;
        offscreenCapabilities = null;
        transferExecutor = null;
        refreshRateCacheExpiresNanos = 0L;
        cachedRefreshRate = FALLBACK_REFRESH_RATE;
        compatibilityRenderer = false;
        layerActive = false;
        activeLayerWidth = 0;
        activeLayerHeight = 0;
        initialized = false;
    }

    private static void destroyOpenGlFramebuffer(OpenGlFramebuffer target) {
        if (target.depthStencilRenderbuffer != 0) {
            GL30.glDeleteRenderbuffers(target.depthStencilRenderbuffer);
            target.depthStencilRenderbuffer = 0;
        }
        if (target.colorTexture != 0) {
            GL11.glDeleteTextures(target.colorTexture);
            target.colorTexture = 0;
        }
        if (target.framebuffer != 0) {
            GL30.glDeleteFramebuffers(target.framebuffer);
            target.framebuffer = 0;
        }
        target.width = 0;
        target.height = 0;
    }

    private static void destroyReadbackResources(LayerCache cache) {
        int previousBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        try {
            for (ReadbackSlot slot : cache.readbackSlots) {
                releaseReadbackFence(slot);
                if (slot.mappedBuffer != null) {
                    GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, slot.buffer);
                    GL15.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER);
                    slot.mappedBuffer = null;
                }
                if (slot.buffer != 0) {
                    GL15.glDeleteBuffers(slot.buffer);
                    slot.buffer = 0;
                }
                slot.copyTask = null;
                slot.copyComplete = false;
                slot.sequence = 0L;
                slot.generation = 0L;
            }
        } finally {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, previousBuffer);
        }
    }

    private static void destroyTransferResources(LayerCache cache) {
        for (TransferSlot slot : cache.transferSlots) {
            if (slot.gpuFence != null) {
                slot.gpuFence.close();
                slot.gpuFence = null;
            }
            if (slot.mapping != null) {
                slot.mapping.close();
                slot.mapping = null;
            }
            if (slot.buffer != null) {
                slot.buffer.close();
                slot.buffer = null;
            }
            slot.state = TRANSFER_FREE;
            slot.sequence = 0L;
            slot.generation = 0L;
        }
    }

    private static void destroyUploadTextures(LayerCache cache) {
        for (int index = 0; index < cache.textures.length; index++) {
            if (cache.textureViews[index] != null) {
                cache.textureViews[index].close();
                cache.textureViews[index] = null;
            }
            if (cache.textures[index] != null) {
                cache.textures[index].close();
                cache.textures[index] = null;
            }
        }
        cache.activeTextureIndex = -1;
    }

    private static final class OpenGlFramebuffer {
        private int framebuffer;
        private int colorTexture;
        private int depthStencilRenderbuffer;
        private int width;
        private int height;
    }

    private static final class ReadbackSlot {
        private int buffer;
        private long fence;
        private long sequence;
        private long generation;
        private ByteBuffer mappedBuffer;
        private Future<?> copyTask;
        private volatile boolean copyComplete;
    }

    private static final class TransferSlot {
        private GpuBuffer buffer;
        private GpuBufferSlice.MappedView mapping;
        private GpuFence gpuFence;
        private long sequence;
        private long generation;
        private volatile int state = TRANSFER_FREE;
    }

    private static final class LayerCache {
        private final ReadbackSlot[] readbackSlots = new ReadbackSlot[TRANSFER_SLOT_COUNT];
        private final TransferSlot[] transferSlots = new TransferSlot[TRANSFER_SLOT_COUNT];
        private final GpuTexture[] textures = new GpuTexture[TRANSFER_SLOT_COUNT];
        private final GpuTextureView[] textureViews = new GpuTextureView[TRANSFER_SLOT_COUNT];
        private int width;
        private int height;
        private int byteCount;
        private int activeTextureIndex = -1;
        private long generation;
        private long nextSequence;
        private long nextRenderNanos;
        private long lastInvocationNanos;

        private LayerCache() {
            for (int index = 0; index < TRANSFER_SLOT_COUNT; index++) {
                readbackSlots[index] = new ReadbackSlot();
                transferSlots[index] = new TransferSlot();
            }
        }
    }
}
