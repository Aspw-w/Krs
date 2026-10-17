package com.instrumentalist.krs.utils.render;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.GpuFence;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
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
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDL_DisplayMode;
import org.lwjgl.system.SharedLibrary;
import org.lwjgl.system.MemoryUtil;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;


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
    private static final int TRANSFER_SLOT_COUNT = 3;
    private static final int TRANSFER_FREE = 0;
    private static final int TRANSFER_COPYING = 1;
    private static final int TRANSFER_READY = 2;
    private static final int TRANSFER_GPU_IN_FLIGHT = 3;
    private static final int FALLBACK_REFRESH_RATE = 120;
    private static final long REFRESH_RATE_CACHE_NANOS = 1_000_000_000L;
    private static final int BACKDROP_SLOT_COUNT = 3;
    private static final int BACKDROP_FREE = 0;
    private static final int BACKDROP_IN_FLIGHT = 1;
    private static final int BACKDROP_READY = 2;
    private static final long BACKDROP_REQUEST_TIMEOUT_NANOS = 1_000_000_000L;
    private static final long BACKDROP_MAX_AGE_NANOS = 500_000_000L;
    private static final long BACKDROP_HALF_RESOLUTION_PIXELS = 2_000_000L;
    private static final long DEFERRED_FILL_WAIT_NANOS = 1_000_000_000L;
    private static final int PARALLEL_COPY_MIN_BYTES = 1 << 20;
    private static final int PARALLEL_COPY_CHUNKS = 3;
    // Region edges snap to this grid so neighbouring draws (text runs, list rows) merge.
    private static final int REGION_SNAP = 16;
    private static final int MAX_TRACKED_REGIONS = 32;
    private static final int MAX_TRANSFER_REGIONS = 8;
    // Above this share of the layer a single full transfer is cheaper than several regions.
    private static final long FULL_TRANSFER_AREA_PERCENT = 70L;
    private static final Vector4fc TRANSPARENT = new Vector4f(0f, 0f, 0f, 0f);

    public enum Layer {
        NANO_VG(true, -1, false, true),
        NANO_VG_BEFORE_GUI(true, -1, false, true),
        NANO_VG_IMMEDIATE(true, -1, false, true),
        // World-anchored: a frame that arrives late is drawn against a newer camera.
        ESP(true, 0, true, true),
        IMGUI(false, -1, false, false);

        private final boolean scaled;
        private final int maximumFramesPerSecond;
        private final boolean synchronous;
        // Everything drawn into the layer reports its bounds (NanoVG and Shader2DRenderer),
        // so only those regions are transferred. Other layers always transfer the whole frame.
        private final boolean regionTracked;

        Layer(boolean scaled, int maximumFramesPerSecond, boolean synchronous, boolean regionTracked) {
            this.scaled = scaled;
            this.maximumFramesPerSecond = maximumFramesPerSecond;
            this.synchronous = synchronous;
            this.regionTracked = regionTracked;
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

    private static final RenderPipeline BACKDROP_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("krs", "pipeline/legacy_ui_backdrop"))
            .withVertexShader("core/screenquad")
            .withFragmentShader("core/blit_screen")
            .withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build();

    private static boolean initialized;
    private static boolean compatibilityRenderer;
    private static boolean layerActive;
    private static int activeLayerWidth;
    private static int activeLayerHeight;
    private static LayerCache activeLayerCache;
    private static int activeBackdropTexture;
    private static final RegionSet ACTIVE_REGIONS = new RegionSet(MAX_TRACKED_REGIONS);
    private static boolean activeRegionsWhole;

    private static long offscreenWindow;
    private static long offscreenContext;
    private static boolean offscreenLibraryLoaded;
    private static GLCapabilities offscreenCapabilities;
    private static final OpenGlFramebuffer SCALED_FRAMEBUFFER = new OpenGlFramebuffer();
    private static final OpenGlFramebuffer NATIVE_FRAMEBUFFER = new OpenGlFramebuffer();
    private static final Map<Layer, LayerCache> LAYER_CACHES = new EnumMap<>(Layer.class);
    // Synchronous frames whose GPU upload is already recorded but whose staging data is still
    // being read back by OpenGL. They are filled right before Minecraft submits the frame.
    private static final List<TransferSlot> PENDING_FILLS = new ArrayList<>();
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
        compatibilityRenderer = !"OpenGL".equals(device.getDeviceInfo().backendName());
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

    /**
     * Returns an OpenGL texture holding Minecraft's main target as it looked
     * before the active layer is composited, or {@code 0} when none is
     * available yet. Compatibility layers start transparent, so effects that
     * sample the screen behind them (blur, liquid glass) need this instead of
     * the layer framebuffer. The capture is asynchronous and may be a frame or
     * two old.
     */
    public static int getActiveBackdropTexture() {
        return layerActive ? activeBackdropTexture : 0;
    }

    /**
     * Marks the active layer as sampling the screen behind it. Backdrop
     * captures continue while a layer keeps requesting them.
     */
    public static void requestBackdrop() {
        if (layerActive && activeLayerCache != null)
            activeLayerCache.backdrop.lastRequestNanos = System.nanoTime();
    }

    /**
     * Whether draws should currently report their bounds through
     * {@link #markLayerRegion}. False outside a layer and once the active layer
     * is known to need a full transfer.
     */
    public static boolean isRegionTrackingActive() {
        return layerActive && !activeRegionsWhole;
    }

    /**
     * Marks the whole active layer as drawn, for draws whose extent is unknown.
     */
    public static void markWholeLayer() {
        if (layerActive)
            activeRegionsWhole = true;
    }

    /**
     * Reports that the active layer may have pixels inside the given rectangle.
     * Coordinates are in a top-down frame space of {@code frameWidth} by
     * {@code frameHeight} units mapped onto the whole layer, as used by NanoVG
     * and Shader2DRenderer. The rectangle must already include every pixel the
     * draw can touch (anti-aliasing, blur, shadows).
     */
    public static void markLayerRegion(float frameWidth, float frameHeight,
                                       float minX, float minY, float maxX, float maxY) {
        if (!layerActive || activeRegionsWhole)
            return;
        if (Float.isNaN(minX + minY + maxX + maxY) || !(frameWidth > 0f) || !(frameHeight > 0f)) {
            activeRegionsWhole = true;
            return;
        }

        int width = activeLayerWidth;
        int height = activeLayerHeight;
        float scaleX = width / frameWidth;
        float scaleY = height / frameHeight;
        // Clamp before converting so huge coordinates cannot overflow.
        float left = clampToLayer(minX * scaleX, width);
        float right = clampToLayer(maxX * scaleX, width);
        float top = clampToLayer(minY * scaleY, height);
        float bottom = clampToLayer(maxY * scaleY, height);
        if (right <= 0f || left >= width || bottom <= 0f || top >= height)
            return;

        // The OpenGL layer is bottom-up; one extra pixel covers filtering at the edge.
        int x0 = snapDown((int) Math.floor(left) - 1);
        int x1 = snapUp((int) Math.ceil(right) + 1, width);
        int y0 = snapDown(height - (int) Math.ceil(bottom) - 1);
        int y1 = snapUp(height - (int) Math.floor(top) + 1, height);
        if (x0 < x1 && y0 < y1)
            ACTIVE_REGIONS.add(x0, y0, x1, y1);
    }

    private static float clampToLayer(float value, int size) {
        return Math.max(-REGION_SNAP, Math.min(size + REGION_SNAP, value));
    }

    private static int snapDown(int value) {
        return Math.max(0, Math.floorDiv(value, REGION_SNAP) * REGION_SNAP);
    }

    private static int snapUp(int value, int size) {
        return Math.min(size, Math.floorDiv(value + REGION_SNAP - 1, REGION_SNAP) * REGION_SNAP);
    }

    public static void runWithOpenGlContext(Runnable action) {
        if (action == null)
            return;
        if (!usesCompatibilityRenderer()) {
            action.run();
            return;
        }
        if (offscreenWindow == 0L || offscreenContext == 0L || offscreenCapabilities == null)
            throw new IllegalStateException("The Krs OpenGL compatibility context is not available");

        long previousContext = SDLVideo.SDL_GL_GetCurrentContext();
        if (previousContext == offscreenContext) {
            action.run();
            return;
        }
        if (previousContext == 0L) {
            // Nothing else uses OpenGL on this thread with a non-OpenGL backend. Leaving the
            // compatibility context current saves two context switches (each an implicit
            // flush in the driver) for every layer, readback fill and backdrop upload.
            SDLVideo.SDL_GL_MakeCurrent(offscreenWindow, offscreenContext);
            GL.setCapabilities(offscreenCapabilities);
            action.run();
            return;
        }

        long previousWindow = SDLVideo.SDL_GL_GetCurrentWindow();
        GLCapabilities previousCapabilities = currentCapabilitiesOrNull();
        SDLVideo.SDL_GL_MakeCurrent(offscreenWindow, offscreenContext);
        GL.setCapabilities(offscreenCapabilities);
        try {
            action.run();
        } finally {
            SDLVideo.SDL_GL_MakeCurrent(previousWindow, previousContext);
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

    public static void renderOffscreenLayer(Layer layer, Runnable renderer, Runnable skipped) {
        renderOffscreenLayer(layer, renderer, skipped, false);
    }

    /**
     * Renders a cached compatibility layer. UI layers follow the active
     * display refresh rate at a maximum device-pixel ratio of two. The
     * asynchronous transfer queue applies backpressure when either GPU is
     * unable to keep up.
     *
     * <p>Synchronous frames are rendered, read back and uploaded within the
     * current game frame. Anything projected from the world (ESP, name tags)
     * must use them: an asynchronous frame reaches the screen a few frames
     * late and visibly trails the camera while it turns or bobs. The upload
     * and composite are recorded immediately, while the readback runs on the
     * OpenGL context and only has to land in the staging buffer before the
     * frame is submitted (see {@link #completeDeferredReadbacks()}), so the
     * render thread does not stall on the OpenGL GPU queue.</p>
     *
     * @param skipped     invoked instead of {@code renderer} when a cached frame is
     *                    reused; queue-backed renderers use this to drop stale work
     * @param synchronous forces this frame to be synchronous for layers that are
     *                    not synchronous by default
     */
    public static void renderOffscreenLayer(Layer layer, Runnable renderer, Runnable skipped, boolean synchronous) {
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

        OpenGlFramebuffer glFramebuffer = layer.scaled ? SCALED_FRAMEBUFFER : NATIVE_FRAMEBUFFER;
        boolean trackRegions = layer.regionTracked;
        boolean rendered = false;
        if (synchronous || layer.synchronous) {
            renderSynchronousFrame(glFramebuffer, cache, width, height, renderer, trackRegions);
            rendered = true;
        } else {
            uploadNewestReadyTransfer(cache);

            long updateIntervalNanos = getUpdateIntervalNanos(layer, now);
            boolean update = updateIntervalNanos == 0L
                    || cache.nextRenderNanos == 0L
                    || now >= cache.nextRenderNanos;

            if (update) {
                boolean[] issued = new boolean[1];
                runWithOpenGlContext(() -> issued[0] = advanceReadbackPipeline(
                        glFramebuffer, cache, width, height, renderer, trackRegions
                ));
                rendered = issued[0];
                if (rendered)
                    scheduleNextRender(cache, now, updateIntervalNanos);
            }
        }

        if (!rendered && skipped != null)
            skipped.run();

        // Capture before compositing so the backdrop never contains this layer.
        if (rendered)
            captureBackdropIfRequested(target, cache, width, height);

        compositeCachedLayer(target, cache, width, height);
    }

    private static void renderSynchronousFrame(OpenGlFramebuffer glFramebuffer, LayerCache cache,
                                               int width, int height, Runnable renderer, boolean trackRegions) {
        // Frames still in the asynchronous pipeline are older than this one.
        cache.generation++;
        TransferSlot transfer = acquireSynchronousTransferSlot(cache);
        runWithOpenGlContext(() -> {
            ensureOpenGlFramebuffer(glFramebuffer, width, height);
            renderAndQueueDeferredReadback(glFramebuffer, cache, transfer, width, height, renderer, trackRegions);
        });

        transfer.sequence = ++cache.nextSequence;
        transfer.generation = cache.generation;
        transfer.state = TRANSFER_READY;
        // The GPU copy only reads the staging buffer when the frame executes,
        // so it can be recorded before the pixels arrive.
        uploadNewestReadyTransfer(cache);
    }

    /**
     * Copies the pixels of every synchronous frame recorded since the last
     * submit into their staging buffers. Must run before the GPU frame that
     * uploads them is submitted.
     */
    public static void completeDeferredReadbacks() {
        if (PENDING_FILLS.isEmpty())
            return;

        try {
            runWithOpenGlContext(() -> {
                int previousBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
                try {
                    for (TransferSlot slot : PENDING_FILLS)
                        fillDeferredReadback(slot);
                } finally {
                    GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, previousBuffer);
                }
            });
        } finally {
            PENDING_FILLS.clear();
        }
    }

    private static void fillDeferredReadback(TransferSlot slot) {
        long fence = slot.readbackFence;
        slot.readbackFence = 0L;
        if (fence == 0L || slot.mapping == null || slot.pixelPackBuffer == 0)
            return;

        try {
            int status;
            do {
                status = GL32.glClientWaitSync(fence, GL32.GL_SYNC_FLUSH_COMMANDS_BIT, DEFERRED_FILL_WAIT_NANOS);
            } while (status == GL32.GL_TIMEOUT_EXPIRED);
            if (status == GL32.GL_WAIT_FAILED) {
                logTransferFailure(new IllegalStateException("OpenGL failed to wait for a compatibility readback fence"));
                return;
            }
        } finally {
            GL32.glDeleteSync(fence);
        }

        int byteCount = slot.pixelPackBufferSize;
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, slot.pixelPackBuffer);
        ByteBuffer mapped = GL30.glMapBufferRange(GL21.GL_PIXEL_PACK_BUFFER, 0L, byteCount, GL30.GL_MAP_READ_BIT);
        if (mapped == null) {
            logTransferFailure(new IllegalStateException("Could not map a synchronous OpenGL compatibility readback"));
            return;
        }
        try {
            copyRegions(MemoryUtil.memAddress(mapped), MemoryUtil.memAddress(slot.mapping.data()),
                    slot.regions, slot.layerWidth, true);
        } finally {
            if (!GL15.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER))
                logTransferFailure(new IllegalStateException("OpenGL compatibility readback data became corrupt"));
        }
    }

    /**
     * Copies the regions of a bottom-up RGBA8 frame of {@code width} pixels per
     * row between two buffers with the same layout.
     */
    private static void copyRegions(long source, long destination, RegionSet regions, int width, boolean parallel) {
        long rowBytes = (long) width * 4L;
        for (int index = 0; index < regions.count; index++) {
            int x0 = regions.x0(index);
            int y0 = regions.y0(index);
            int x1 = regions.x1(index);
            int y1 = regions.y1(index);
            long offset = y0 * rowBytes + x0 * 4L;
            if (x0 == 0 && x1 == width) {
                int byteCount = Math.toIntExact((y1 - y0) * rowBytes);
                if (parallel)
                    copyMemoryParallel(source + offset, destination + offset, byteCount);
                else
                    MemoryUtil.memCopy(source + offset, destination + offset, byteCount);
                continue;
            }

            long regionRowBytes = (x1 - x0) * 4L;
            for (int y = y0; y < y1; y++, offset += rowBytes)
                MemoryUtil.memCopy(source + offset, destination + offset, regionRowBytes);
        }
    }

    private static void copyMemoryParallel(long source, long destination, int byteCount) {
        ExecutorService executor = transferExecutor;
        if (executor == null || byteCount < PARALLEL_COPY_MIN_BYTES) {
            MemoryUtil.memCopy(source, destination, byteCount);
            return;
        }

        // Cache-line aligned chunks; the render thread copies the first one itself.
        int chunk = ((byteCount / PARALLEL_COPY_CHUNKS) + 63) & ~63;
        List<Future<?>> tasks = new ArrayList<>(PARALLEL_COPY_CHUNKS - 1);
        List<long[]> ranges = new ArrayList<>(PARALLEL_COPY_CHUNKS - 1);
        for (int offset = chunk; offset < byteCount; offset += chunk) {
            long start = offset;
            long length = Math.min(chunk, byteCount - offset);
            try {
                tasks.add(executor.submit(() -> MemoryUtil.memCopy(source + start, destination + start, length)));
            } catch (RejectedExecutionException exception) {
                tasks.add(null);
            }
            ranges.add(new long[]{start, length});
        }

        MemoryUtil.memCopy(source, destination, Math.min(chunk, byteCount));
        for (int index = 0; index < tasks.size(); index++) {
            Future<?> task = tasks.get(index);
            long[] range = ranges.get(index);
            // Workers may be busy with asynchronous layers; never wait behind them.
            if (task == null || task.cancel(false)) {
                MemoryUtil.memCopy(source + range[0], destination + range[0], range[1]);
                continue;
            }
            try {
                task.get();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                MemoryUtil.memCopy(source + range[0], destination + range[0], range[1]);
            } catch (Exception exception) {
                MemoryUtil.memCopy(source + range[0], destination + range[0], range[1]);
            }
        }
    }

    private static TransferSlot acquireSynchronousTransferSlot(LayerCache cache) {
        releaseStaleReadyTransfers(cache);
        TransferSlot slot = findFreeTransferSlot(cache);
        if (slot != null)
            return slot;

        TransferSlot oldest = null;
        for (TransferSlot candidate : cache.transferSlots) {
            if (candidate.state == TRANSFER_GPU_IN_FLIGHT && (oldest == null || candidate.sequence < oldest.sequence))
                oldest = candidate;
        }
        if (oldest != null) {
            if (oldest.gpuFence != null) {
                oldest.gpuFence.awaitCompletion(GpuFence.NO_TIMEOUT);
                oldest.gpuFence.close();
                oldest.gpuFence = null;
            }
            oldest.state = TRANSFER_FREE;
            return oldest;
        }

        // Only worker copies started by an earlier asynchronous frame remain.
        awaitWorkerCopies(cache);
        releaseStaleReadyTransfers(cache);
        slot = findFreeTransferSlot(cache);
        if (slot == null)
            throw new IllegalStateException("No compatibility transfer slot became available");
        return slot;
    }

    private static void releaseStaleReadyTransfers(LayerCache cache) {
        for (TransferSlot slot : cache.transferSlots) {
            if (slot.state == TRANSFER_READY && slot.generation != cache.generation)
                slot.state = TRANSFER_FREE;
        }
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
            refreshRate = getWindowRefreshRate(minecraft.getWindow().handle());
        if (refreshRate < 30) {
            int frameLimit = minecraft == null ? 0 : minecraft.options.framerateLimit().get();
            refreshRate = frameLimit >= 30 ? frameLimit : FALLBACK_REFRESH_RATE;
        }

        cachedRefreshRate = refreshRate;
        refreshRateCacheExpiresNanos = now + REFRESH_RATE_CACHE_NANOS;
        return refreshRate;
    }

    private static int getWindowRefreshRate(long windowHandle) {
        try {
            int display = SDLVideo.SDL_GetDisplayForWindow(windowHandle);
            if (display == 0)
                return 0;
            SDL_DisplayMode mode = SDLVideo.SDL_GetCurrentDisplayMode(display);
            return mode == null ? 0 : Math.round(mode.refresh_rate());
        } catch (Throwable ignored) {
            return 0;
        }
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
        if (!SDLVideo.SDL_GL_LoadLibrary(((SharedLibrary) GL.getFunctionProvider()).getPath()))
            throw new IllegalStateException("Could not load OpenGL for the Krs compatibility context: " + SDLError.SDL_GetError());
        offscreenLibraryLoaded = true;

        SDLVideo.SDL_GL_ResetAttributes();
        SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_CONTEXT_MAJOR_VERSION, 3);
        SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_CONTEXT_MINOR_VERSION, 3);
        SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_CONTEXT_PROFILE_MASK, SDLVideo.SDL_GL_CONTEXT_PROFILE_CORE);
        SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_CONTEXT_FLAGS, SDLVideo.SDL_GL_CONTEXT_FORWARD_COMPATIBLE_FLAG);

        long previousWindow = SDLVideo.SDL_GL_GetCurrentWindow();
        long previousContext = SDLVideo.SDL_GL_GetCurrentContext();
        GLCapabilities previousCapabilities = currentCapabilitiesOrNull();
        try {
            offscreenWindow = SDLVideo.SDL_CreateWindow("Krs OpenGL compatibility renderer", 1, 1,
                    SDLVideo.SDL_WINDOW_OPENGL | SDLVideo.SDL_WINDOW_HIDDEN);
            if (offscreenWindow == 0L)
                throw new IllegalStateException("Could not create the Krs OpenGL compatibility window: " + SDLError.SDL_GetError());

            offscreenContext = SDLVideo.SDL_GL_CreateContext(offscreenWindow);
            if (offscreenContext == 0L)
                throw new IllegalStateException("Could not create the Krs OpenGL compatibility context: " + SDLError.SDL_GetError());
        } catch (RuntimeException | Error failure) {
            destroyOffscreenContext();
            throw failure;
        } finally {
            // GL attributes are process-global. Do not leak the compatibility
            // context settings into windows created by Minecraft or other mods.
            SDLVideo.SDL_GL_ResetAttributes();
        }

        try {
            SDLVideo.SDL_GL_MakeCurrent(offscreenWindow, offscreenContext);
            offscreenCapabilities = GL.createCapabilities();
            SDLVideo.SDL_GL_SetSwapInterval(0);
        } catch (RuntimeException | Error failure) {
            SDLVideo.SDL_GL_MakeCurrent(previousWindow, previousContext);
            GL.setCapabilities(previousCapabilities);
            destroyOffscreenContext();
            throw failure;
        }

        SDLVideo.SDL_GL_MakeCurrent(previousWindow, previousContext);
        GL.setCapabilities(previousCapabilities);
    }

    private static void destroyOffscreenContext() {
        if (offscreenContext != 0L) {
            SDLVideo.SDL_GL_DestroyContext(offscreenContext);
            offscreenContext = 0L;
        }
        if (offscreenWindow != 0L) {
            SDLVideo.SDL_DestroyWindow(offscreenWindow);
            offscreenWindow = 0L;
        }
        if (offscreenLibraryLoaded) {
            SDLVideo.SDL_GL_UnloadLibrary();
            offscreenLibraryLoaded = false;
        }
    }

    private static GLCapabilities currentCapabilitiesOrNull() {
        try {
            return GL.getCapabilities();
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    private static boolean advanceReadbackPipeline(OpenGlFramebuffer target, LayerCache cache,
                                                   int width, int height, Runnable renderer, boolean trackRegions) {
        reclaimFinishedReadbackCopies(cache);
        dispatchLatestCompletedReadback(cache);

        ReadbackSlot slot = findFreeReadbackSlot(cache);
        if (slot == null)
            return false;

        ensureOpenGlFramebuffer(target, width, height);
        ensureReadbackBuffer(slot, cache.byteCount);
        renderAndQueueReadback(target, cache, slot, width, height, renderer, trackRegions);
        return true;
    }

    /**
     * Renders the layer and stores the regions that may hold pixels in
     * {@code regions}, in bottom-up layer pixels.
     */
    private static void renderLayerContents(OpenGlFramebuffer target, LayerCache cache,
                                            int width, int height, Runnable renderer,
                                            boolean trackRegions, RegionSet regions) {
        uploadReadyBackdrop(cache.backdrop);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, target.framebuffer);
        GL11.glViewport(0, 0, width, height);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glColorMask(true, true, true, true);
        GL11.glDepthMask(true);
        GL11.glStencilMask(0xFF);
        GL11.glClearColor(0f, 0f, 0f, 0f);
        GL11.glClearDepth(1.0);
        GL11.glClearStencil(0);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);

        ACTIVE_REGIONS.clear();
        activeRegionsWhole = !trackRegions;
        layerActive = true;
        activeLayerWidth = width;
        activeLayerHeight = height;
        activeLayerCache = cache;
        activeBackdropTexture = usableBackdropTexture(cache.backdrop);
        try {
            renderer.run();
        } finally {
            layerActive = false;
            activeLayerWidth = 0;
            activeLayerHeight = 0;
            activeLayerCache = null;
            activeBackdropTexture = 0;
        }

        if (activeRegionsWhole) {
            regions.setWhole(width, height);
        } else {
            regions.copyFrom(ACTIVE_REGIONS);
            regions.reduceTo(MAX_TRANSFER_REGIONS);
            if (regions.area() * 100L >= (long) width * height * FULL_TRANSFER_AREA_PERCENT)
                regions.setWhole(width, height);
        }

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, target.framebuffer);
    }

    /**
     * Reads the regions into a pixel pack buffer that holds the whole bottom-up
     * frame, each region at its own place. A pixel pack buffer must be bound.
     */
    private static void readPixelRegions(RegionSet regions, int width) {
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glPixelStorei(GL12.GL_PACK_ROW_LENGTH, width);
        for (int index = 0; index < regions.count; index++) {
            int x0 = regions.x0(index);
            int y0 = regions.y0(index);
            long offset = ((long) y0 * width + x0) * 4L;
            GL11.glReadPixels(x0, y0, regions.x1(index) - x0, regions.y1(index) - y0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, offset);
        }
    }

    private static void renderAndQueueDeferredReadback(OpenGlFramebuffer target, LayerCache cache, TransferSlot transfer,
                                                       int width, int height, Runnable renderer, boolean trackRegions) {
        try {
            renderLayerContents(target, cache, width, height, renderer, trackRegions, transfer.regions);
            transfer.layerWidth = width;
            // Nothing was drawn: there is nothing to read back or upload.
            if (transfer.regions.count == 0)
                return;

            int packAlignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
            int packRowLength = GL11.glGetInteger(GL12.GL_PACK_ROW_LENGTH);
            int pixelPackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
            try {
                if (transfer.pixelPackBuffer == 0 || transfer.pixelPackBufferSize != cache.byteCount) {
                    if (transfer.pixelPackBuffer == 0)
                        transfer.pixelPackBuffer = GL15.glGenBuffers();
                    GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, transfer.pixelPackBuffer);
                    GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, cache.byteCount, GL15.GL_STREAM_READ);
                    transfer.pixelPackBufferSize = cache.byteCount;
                }
                GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, transfer.pixelPackBuffer);
                // Asynchronous DMA into the PBO; completed before submit by completeDeferredReadbacks.
                readPixelRegions(transfer.regions, width);
                if (transfer.readbackFence != 0L)
                    GL32.glDeleteSync(transfer.readbackFence);
                transfer.readbackFence = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
                GL11.glFlush();
                if (!PENDING_FILLS.contains(transfer))
                    PENDING_FILLS.add(transfer);
            } finally {
                GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, packAlignment);
                GL11.glPixelStorei(GL12.GL_PACK_ROW_LENGTH, packRowLength);
                GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pixelPackBuffer);
            }
        } finally {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        }
    }

    private static void renderAndQueueReadback(OpenGlFramebuffer target, LayerCache cache, ReadbackSlot slot,
                                               int width, int height, Runnable renderer, boolean trackRegions) {
        try {
            renderLayerContents(target, cache, width, height, renderer, trackRegions, slot.regions);
            slot.layerWidth = width;

            int packAlignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
            int packRowLength = GL11.glGetInteger(GL12.GL_PACK_ROW_LENGTH);
            int pixelPackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
            try {
                GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, slot.buffer);
                // An empty frame still goes through the fence so it replaces older frames in order.
                readPixelRegions(slot.regions, width);
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
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        }
    }

    private static void captureBackdropIfRequested(RenderTarget target, LayerCache cache, int width, int height) {
        Backdrop backdrop = cache.backdrop;
        long now = System.nanoTime();
        if (backdrop.lastRequestNanos == 0L || now - backdrop.lastRequestNanos > BACKDROP_REQUEST_TIMEOUT_NANOS)
            return;

        BackdropSlot slot = null;
        for (BackdropSlot candidate : backdrop.slots) {
            if (candidate.state == BACKDROP_FREE) {
                slot = candidate;
                break;
            }
        }
        if (slot == null)
            return;

        // Glass mostly samples the blurred copy, so large targets are captured at half size.
        int divisor = (long) width * height >= BACKDROP_HALF_RESOLUTION_PIXELS ? 2 : 1;
        int captureWidth = Math.max(1, width / divisor);
        int captureHeight = Math.max(1, height / divisor);
        GpuDevice device = RenderSystem.getDevice();
        if (backdrop.texture == null || backdrop.texture.isClosed()
                || backdrop.textureWidth != captureWidth || backdrop.textureHeight != captureHeight) {
            destroyBackdropTexture(backdrop);
            backdrop.texture = device.createTexture(
                    "Krs legacy UI backdrop",
                    GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_SRC,
                    GpuFormat.RGBA8_UNORM,
                    captureWidth,
                    captureHeight,
                    1,
                    1
            );
            backdrop.textureView = device.createTextureView(backdrop.texture);
            backdrop.textureWidth = captureWidth;
            backdrop.textureHeight = captureHeight;
        }
        if (slot.buffer == null || slot.buffer.isClosed()
                || slot.width != captureWidth || slot.height != captureHeight) {
            if (slot.buffer != null)
                slot.buffer.close();
            slot.buffer = device.createBuffer(
                    () -> "Krs legacy UI backdrop readback",
                    GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST,
                    (long) captureWidth * captureHeight * 4L
            );
            slot.width = captureWidth;
            slot.height = captureHeight;
        }

        CommandEncoder encoder = device.createCommandEncoder();
        try (RenderPass pass = encoder.createRenderPass(() -> "Krs legacy UI backdrop", backdrop.textureView, Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(BACKDROP_PIPELINE));
            pass.setUniform("InSampler", target.getColorTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.draw(3, 1, 0, 0);
        }

        BackdropSlot capture = slot;
        capture.state = BACKDROP_IN_FLIGHT;
        capture.capturedNanos = now;
        capture.sequence = ++backdrop.nextSequence;
        encoder.copyTextureToBuffer(backdrop.texture, capture.buffer, 0L, () -> capture.state = BACKDROP_READY, 0);
    }

    private static void uploadReadyBackdrop(Backdrop backdrop) {
        BackdropSlot newest = null;
        for (BackdropSlot slot : backdrop.slots) {
            if (slot.state != BACKDROP_READY)
                continue;
            if (newest == null || slot.sequence > newest.sequence) {
                if (newest != null)
                    newest.state = BACKDROP_FREE;
                newest = slot;
            } else {
                slot.state = BACKDROP_FREE;
            }
        }
        if (newest == null)
            return;

        int textureBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int unpackAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        int unpackRowLength = GL11.glGetInteger(GL12.GL_UNPACK_ROW_LENGTH);
        int pixelUnpackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        try (GpuBufferSlice.MappedView view = newest.buffer.map(true, false)) {
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL12.GL_UNPACK_ROW_LENGTH, 0);
            if (backdrop.glTexture == 0) {
                backdrop.glTexture = GL11.glGenTextures();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, backdrop.glTexture);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            } else {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, backdrop.glTexture);
            }

            // Buffer rows use the same bottom-up order as OpenGL textures.
            if (backdrop.glWidth != newest.width || backdrop.glHeight != newest.height) {
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, newest.width, newest.height, 0,
                        GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, view.data());
                backdrop.glWidth = newest.width;
                backdrop.glHeight = newest.height;
            } else {
                GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, newest.width, newest.height,
                        GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, view.data());
            }
            backdrop.glCapturedNanos = newest.capturedNanos;
        } catch (RuntimeException exception) {
            logTransferFailure(exception);
        } finally {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, unpackAlignment);
            GL11.glPixelStorei(GL12.GL_UNPACK_ROW_LENGTH, unpackRowLength);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, pixelUnpackBuffer);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureBinding);
            newest.state = BACKDROP_FREE;
        }
    }

    private static int usableBackdropTexture(Backdrop backdrop) {
        if (backdrop.glTexture == 0 || backdrop.glCapturedNanos == 0L)
            return 0;
        // A stale capture (e.g. after the layer was idle) would show the wrong scene.
        return System.nanoTime() - backdrop.glCapturedNanos <= BACKDROP_MAX_AGE_NANOS ? backdrop.glTexture : 0;
    }

    private static void destroyBackdropTexture(Backdrop backdrop) {
        if (backdrop.textureView != null) {
            backdrop.textureView.close();
            backdrop.textureView = null;
        }
        if (backdrop.texture != null) {
            backdrop.texture.close();
            backdrop.texture = null;
        }
        backdrop.textureWidth = 0;
        backdrop.textureHeight = 0;
    }

    private static void destroyBackdropResources(Backdrop backdrop) {
        destroyBackdropTexture(backdrop);
        for (BackdropSlot slot : backdrop.slots) {
            if (slot.buffer != null) {
                slot.buffer.close();
                slot.buffer = null;
            }
            slot.width = 0;
            slot.height = 0;
            slot.state = BACKDROP_FREE;
        }
        backdrop.lastRequestNanos = 0L;
    }

    private static void destroyBackdropOpenGlTexture(Backdrop backdrop) {
        if (backdrop.glTexture != 0) {
            GL11.glDeleteTextures(backdrop.glTexture);
            backdrop.glTexture = 0;
        }
        backdrop.glWidth = 0;
        backdrop.glHeight = 0;
        backdrop.glCapturedNanos = 0L;
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

        transfer.regions.copyFrom(newest.regions);
        transfer.layerWidth = newest.layerWidth;
        if (newest.regions.count == 0) {
            // An empty frame has no pixels to copy; it only has to replace older frames.
            transfer.sequence = newest.sequence;
            transfer.generation = newest.generation;
            releaseSignaledReadback(newest);
            transfer.state = TRANSFER_READY;
            return;
        }

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
            readback.copyTask = executor.submit(() -> copyReadbackOnWorker(readback, transfer));
        } catch (RejectedExecutionException exception) {
            transfer.state = TRANSFER_FREE;
            readback.copyComplete = true;
            logTransferFailure(exception);
        }
    }

    private static void copyReadbackOnWorker(ReadbackSlot readback, TransferSlot transfer) {
        try {
            copyRegions(
                    MemoryUtil.memAddress(readback.mappedBuffer),
                    MemoryUtil.memAddress(transfer.mapping.data()),
                    transfer.regions,
                    transfer.layerWidth,
                    false
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
        // Uploads recorded this frame still read the staging buffers about to be destroyed.
        completeDeferredReadbacks();
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
                    // Render attachment usage is required to clear the texture.
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                    GpuFormat.RGBA8_UNORM,
                    cache.width,
                    cache.height,
                    1,
                    1
            );
            cache.textureViews[index] = device.createTextureView(cache.textures[index]);
            // New texture contents are undefined until cleared or fully overwritten.
            cache.textureRegions[index].setWhole(cache.width, cache.height);
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

        RegionSet regions = newest.regions;
        if (regions.count == 0) {
            // The newest frame is empty; nothing has to be composited until a frame draws again.
            newest.state = TRANSFER_FREE;
            cache.activeTextureIndex = -1;
            return;
        }

        ensureUploadTextures(cache);
        int textureIndex = cache.nextTextureIndex;
        cache.nextTextureIndex = (textureIndex + 1) % cache.textures.length;
        GpuTexture texture = cache.textures[textureIndex];
        RegionSet textureRegions = cache.textureRegions[textureIndex];
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        // Pixels outside the new regions must be transparent, as in the OpenGL layer.
        if (!regions.covers(textureRegions))
            encoder.clearColorTexture(texture, TRANSPARENT);
        GpuBufferSlice source = newest.buffer.slice();
        for (int index = 0; index < regions.count; index++) {
            int x0 = regions.x0(index);
            int y0 = regions.y0(index);
            int copyWidth = regions.x1(index) - x0;
            int copyHeight = regions.y1(index) - y0;
            encoder.copyBufferToTexture(
                    source,
                    x0, y0, cache.width, cache.height,
                    texture,
                    x0, y0, copyWidth, copyHeight,
                    0, 0
            );
        }
        textureRegions.copyFrom(regions);
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
            pass.setPipeline(RenderSystem.getCompiledPipeline(COMPOSITE_PIPELINE));
            pass.setUniform("InSampler", cache.textureViews[textureIndex],
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
                for (LayerCache cache : LAYER_CACHES.values()) {
                    destroyReadbackResources(cache);
                    destroyBackdropOpenGlTexture(cache.backdrop);
                }
                destroyOpenGlFramebuffer(SCALED_FRAMEBUFFER);
                destroyOpenGlFramebuffer(NATIVE_FRAMEBUFFER);
            });
            // runWithOpenGlContext may have left the context current on this thread.
            if (SDLVideo.SDL_GL_GetCurrentContext() == offscreenContext) {
                SDLVideo.SDL_GL_MakeCurrent(offscreenWindow, 0L);
                GL.setCapabilities(null);
            }
            destroyOffscreenContext();
        }

        for (LayerCache cache : LAYER_CACHES.values()) {
            destroyTransferResources(cache);
            destroyUploadTextures(cache);
            destroyBackdropResources(cache.backdrop);
        }
        LAYER_CACHES.clear();
        PENDING_FILLS.clear();
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
        activeLayerCache = null;
        activeBackdropTexture = 0;
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
            for (TransferSlot slot : cache.transferSlots) {
                PENDING_FILLS.remove(slot);
                if (slot.readbackFence != 0L) {
                    GL32.glDeleteSync(slot.readbackFence);
                    slot.readbackFence = 0L;
                }
                if (slot.pixelPackBuffer != 0) {
                    GL15.glDeleteBuffers(slot.pixelPackBuffer);
                    slot.pixelPackBuffer = 0;
                }
                slot.pixelPackBufferSize = 0;
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
        cache.nextTextureIndex = 0;
    }

    private static final class OpenGlFramebuffer {
        private int framebuffer;
        private int colorTexture;
        private int depthStencilRenderbuffer;
        private int width;
        private int height;
    }

    private static final class ReadbackSlot {
        private final RegionSet regions = new RegionSet(MAX_TRANSFER_REGIONS);
        private int layerWidth;
        private int buffer;
        private long fence;
        private long sequence;
        private long generation;
        private ByteBuffer mappedBuffer;
        private Future<?> copyTask;
        private volatile boolean copyComplete;
    }

    private static final class TransferSlot {
        // Written before the slot is handed to a worker or marked ready.
        private final RegionSet regions = new RegionSet(MAX_TRANSFER_REGIONS);
        private int layerWidth;
        private GpuBuffer buffer;
        private GpuBufferSlice.MappedView mapping;
        private GpuFence gpuFence;
        private int pixelPackBuffer;
        private int pixelPackBufferSize;
        private long readbackFence;
        private long sequence;
        private long generation;
        private volatile int state = TRANSFER_FREE;
    }

    /**
     * Rectangles in bottom-up layer pixels, {@code [x0, x1) x [y0, y1)}.
     * Rectangles may overlap; together they cover every pixel that may be
     * non-transparent.
     */
    private static final class RegionSet {
        private final int[] bounds;
        private int count;

        private RegionSet(int capacity) {
            bounds = new int[capacity * 4];
        }

        private int x0(int index) {
            return bounds[index * 4];
        }

        private int y0(int index) {
            return bounds[index * 4 + 1];
        }

        private int x1(int index) {
            return bounds[index * 4 + 2];
        }

        private int y1(int index) {
            return bounds[index * 4 + 3];
        }

        private void clear() {
            count = 0;
        }

        private void setWhole(int width, int height) {
            count = 1;
            set(0, 0, 0, width, height);
        }

        private void copyFrom(RegionSet other) {
            if (other.count * 4 > bounds.length)
                throw new IllegalArgumentException("Region set capacity exceeded");
            System.arraycopy(other.bounds, 0, bounds, 0, other.count * 4);
            count = other.count;
        }

        private long area() {
            long area = 0L;
            for (int index = 0; index < count; index++)
                area += (long) (x1(index) - x0(index)) * (y1(index) - y0(index));
            return area;
        }

        /**
         * Whether every rectangle of {@code other} lies inside one of these.
         */
        private boolean covers(RegionSet other) {
            for (int index = 0; index < other.count; index++) {
                boolean covered = false;
                for (int candidate = 0; candidate < count && !covered; candidate++) {
                    covered = x0(candidate) <= other.x0(index) && y0(candidate) <= other.y0(index)
                            && x1(candidate) >= other.x1(index) && y1(candidate) >= other.y1(index);
                }
                if (!covered)
                    return false;
            }
            return true;
        }

        private void add(int x0, int y0, int x1, int y1) {
            // Absorb every rectangle the new one overlaps or touches.
            for (int index = 0; index < count; ) {
                if (x0 <= x1(index) && x0(index) <= x1 && y0 <= y1(index) && y0(index) <= y1) {
                    x0 = Math.min(x0, x0(index));
                    y0 = Math.min(y0, y0(index));
                    x1 = Math.max(x1, x1(index));
                    y1 = Math.max(y1, y1(index));
                    remove(index);
                    index = 0;
                } else {
                    index++;
                }
            }

            if (count * 4 < bounds.length) {
                set(count++, x0, y0, x1, y1);
                return;
            }

            int target = 0;
            long smallestGrowth = Long.MAX_VALUE;
            for (int index = 0; index < count; index++) {
                long growth = unionArea(index, x0, y0, x1, y1)
                        - (long) (x1(index) - x0(index)) * (y1(index) - y0(index));
                if (growth < smallestGrowth) {
                    smallestGrowth = growth;
                    target = index;
                }
            }
            set(target,
                    Math.min(x0, x0(target)), Math.min(y0, y0(target)),
                    Math.max(x1, x1(target)), Math.max(y1, y1(target)));
        }

        /**
         * Merges the pair of rectangles that adds the least area until at most
         * {@code maximum} remain.
         */
        private void reduceTo(int maximum) {
            while (count > maximum) {
                int first = 0;
                int second = 1;
                long smallestGrowth = Long.MAX_VALUE;
                for (int a = 0; a < count; a++) {
                    long areaA = (long) (x1(a) - x0(a)) * (y1(a) - y0(a));
                    for (int b = a + 1; b < count; b++) {
                        long areaB = (long) (x1(b) - x0(b)) * (y1(b) - y0(b));
                        long growth = unionArea(a, x0(b), y0(b), x1(b), y1(b)) - areaA - areaB;
                        if (growth < smallestGrowth) {
                            smallestGrowth = growth;
                            first = a;
                            second = b;
                        }
                    }
                }
                set(first,
                        Math.min(x0(first), x0(second)), Math.min(y0(first), y0(second)),
                        Math.max(x1(first), x1(second)), Math.max(y1(first), y1(second)));
                remove(second);
            }
        }

        private long unionArea(int index, int x0, int y0, int x1, int y1) {
            return (long) (Math.max(x1, x1(index)) - Math.min(x0, x0(index)))
                    * (Math.max(y1, y1(index)) - Math.min(y0, y0(index)));
        }

        private void remove(int index) {
            count--;
            if (index != count)
                System.arraycopy(bounds, count * 4, bounds, index * 4, 4);
        }

        private void set(int index, int x0, int y0, int x1, int y1) {
            int offset = index * 4;
            bounds[offset] = x0;
            bounds[offset + 1] = y0;
            bounds[offset + 2] = x1;
            bounds[offset + 3] = y1;
        }
    }

    private static final class BackdropSlot {
        private GpuBuffer buffer;
        private int width;
        private int height;
        private long sequence;
        private long capturedNanos;
        private volatile int state = BACKDROP_FREE;
    }

    private static final class Backdrop {
        private final BackdropSlot[] slots = new BackdropSlot[BACKDROP_SLOT_COUNT];
        private GpuTexture texture;
        private GpuTextureView textureView;
        private int textureWidth;
        private int textureHeight;
        private int glTexture;
        private int glWidth;
        private int glHeight;
        private long glCapturedNanos;
        private long nextSequence;
        private long lastRequestNanos;

        private Backdrop() {
            for (int index = 0; index < BACKDROP_SLOT_COUNT; index++)
                slots[index] = new BackdropSlot();
        }
    }

    private static final class LayerCache {
        private final Backdrop backdrop = new Backdrop();
        private final ReadbackSlot[] readbackSlots = new ReadbackSlot[TRANSFER_SLOT_COUNT];
        private final TransferSlot[] transferSlots = new TransferSlot[TRANSFER_SLOT_COUNT];
        private final GpuTexture[] textures = new GpuTexture[TRANSFER_SLOT_COUNT];
        private final GpuTextureView[] textureViews = new GpuTextureView[TRANSFER_SLOT_COUNT];
        // Where each upload texture may still hold non-transparent pixels.
        private final RegionSet[] textureRegions = new RegionSet[TRANSFER_SLOT_COUNT];
        private int width;
        private int height;
        private int byteCount;
        private int activeTextureIndex = -1;
        private int nextTextureIndex;
        private long generation;
        private long nextSequence;
        private long nextRenderNanos;
        private long lastInvocationNanos;

        private LayerCache() {
            for (int index = 0; index < TRANSFER_SLOT_COUNT; index++) {
                readbackSlots[index] = new ReadbackSlot();
                transferSlots[index] = new TransferSlot();
                textureRegions[index] = new RegionSet(MAX_TRANSFER_REGIONS);
            }
        }
    }
}
