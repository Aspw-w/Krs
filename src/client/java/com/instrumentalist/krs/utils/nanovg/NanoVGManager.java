package com.instrumentalist.krs.utils.nanovg;

import com.instrumentalist.krs.utils.IMinecraft;
import com.instrumentalist.krs.utils.render.GraphicsApiCompatibility;
import com.instrumentalist.krs.utils.render.Shader2DRenderer;
import com.mojang.logging.LogUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.nvgu.NVGU;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.function.Consumer;
import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

public class NanoVGManager implements IMinecraft {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long FAILURE_LOG_INTERVAL_NANOS = 10_000_000_000L;
    private static final int MAX_QUEUED_RENDERERS = 1024;
    private static final Minecraft mc = Minecraft.getInstance();
    private final Object renderQueueLock = new Object();
    private Deque<Consumer<NVGU>> renderQueue = new ArrayDeque<>();
    private Deque<Consumer<NVGU>> frameRenderQueue = new ArrayDeque<>();
    private Deque<Consumer<NVGU>> beforeGuiRenderQueue = new ArrayDeque<>();
    private Deque<Consumer<NVGU>> beforeGuiFrameRenderQueue = new ArrayDeque<>();
    private final Consumer<NVGU> queuedRenderConsumer = this::renderFrameQueue;
    private final Consumer<NVGU> beforeGuiQueuedRenderConsumer = this::renderBeforeGuiFrameQueue;
    private Deque<Consumer<NVGU>> screenLayerRenderQueue = new ArrayDeque<>();
    private Deque<Consumer<NVGU>> screenLayerFrameRenderQueue = new ArrayDeque<>();
    private final Consumer<NVGU> screenLayerQueuedRenderConsumer = this::renderScreenLayerFrameQueue;
    private Deque<Consumer<NVGU>> hudLayerRenderQueue = new ArrayDeque<>();
    private Deque<Consumer<NVGU>> hudLayerFrameRenderQueue = new ArrayDeque<>();
    private final Consumer<NVGU> hudLayerQueuedRenderConsumer = this::renderHudLayerFrameQueue;
    private Deque<Consumer<NVGU>> overlayLayerRenderQueue = new ArrayDeque<>();
    private Deque<Consumer<NVGU>> overlayLayerFrameRenderQueue = new ArrayDeque<>();
    private final Consumer<NVGU> overlayLayerQueuedRenderConsumer = this::renderOverlayLayerFrameQueue;
    private final Map<Class<?>, Long> rendererFailureLogTimes = new HashMap<>();
    private final ByteBuffer colourWriteMask = BufferUtils.createByteBuffer(4);
    private long lastQueueOverflowLogNanos;
    private int droppedRenderers;
    private boolean worldAnchoredQueued;

    public static float getUiScale() {
        int guiScale = mc.getWindow().getGuiScale();
        if (guiScale <= 0) return 1.0f;
        return Math.max(0.25f, guiScale / 2.0f);
    }

    public static float getScaledScreenWidth() {
        return mc.getWindow().getScreenWidth() / getUiScale();
    }

    public static float getScaledScreenHeight() {
        return mc.getWindow().getScreenHeight() / getUiScale();
    }

    public static float toScaledMouseX(double mouseX) {
        return (float) (mouseX * mc.getWindow().getGuiScale() / getUiScale());
    }

    public static float toScaledMouseY(double mouseY) {
        return (float) (mouseY * mc.getWindow().getGuiScale() / getUiScale());
    }

    public static float fromFramebufferX(double x) {
        return (float) (x * getScaledScreenWidth() / Math.max(1, mc.getWindow().getWidth()));
    }

    public static float fromFramebufferY(double y) {
        return (float) (y * getScaledScreenHeight() / Math.max(1, mc.getWindow().getHeight()));
    }

    public static boolean shouldRenderBelowDebugOverlay() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.debugEntries == null)
            return false;

        return minecraft.debugEntries.isOverlayVisible() && (!minecraft.gui.hud.isHidden() || minecraft.gui.screen() != null);
    }

    public void load(Consumer<NVGU> vg) {
        if (vg == null)
            return;

        synchronized (renderQueueLock) {
            if (renderQueue.size() >= MAX_QUEUED_RENDERERS) {
                renderQueue.pollFirst();
                droppedRenderers++;
                logQueueOverflow();
            }
            renderQueue.addLast(vg);
        }
    }

    /**
     * Queues a renderer whose positions are projected from the world this
     * frame (name tags, indicators). On non-OpenGL backends such frames are
     * composited synchronously so they cannot trail the camera.
     */
    public void loadWorldAnchored(Consumer<NVGU> vg) {
        if (vg == null)
            return;

        synchronized (renderQueueLock) {
            worldAnchoredQueued = true;
        }
        load(vg);
    }

    public boolean hasWorldAnchoredRenderers() {
        synchronized (renderQueueLock) {
            return worldAnchoredQueued;
        }
    }

    public void loadBeforeGui(Consumer<NVGU> vg) {
        if (vg == null)
            return;

        synchronized (renderQueueLock) {
            if (beforeGuiRenderQueue.size() >= MAX_QUEUED_RENDERERS) {
                beforeGuiRenderQueue.pollFirst();
                droppedRenderers++;
                logQueueOverflow();
            }
            beforeGuiRenderQueue.addLast(vg);
        }
    }

    public void renderQueued() {
        NVGU vg = NVGU.INSTANCE;
        if (vg == null || !vg.isCreated()) {
            clearQueues();
            return;
        }

        synchronized (renderQueueLock) {
            // Swap buffers instead of copying every renderer while producers are blocked.
            Deque<Consumer<NVGU>> pendingRenderers = renderQueue;
            renderQueue = frameRenderQueue;
            frameRenderQueue = pendingRenderers;
            renderQueue.clear();
            worldAnchoredQueued = false;
        }
        if (frameRenderQueue.isEmpty()) return;

        try {
            renderNow(queuedRenderConsumer);
        } finally {
            // Never replay stale renderers after a native or GL failure.
            frameRenderQueue.clear();
        }
    }

    public void renderConsumers(List<Consumer<NVGU>> consumers) {
        if (consumers.isEmpty())
            return;
        NVGU vg = NVGU.INSTANCE;
        if (vg == null || !vg.isCreated())
            return;
        renderNow(instance -> {
            for (Consumer<NVGU> consumer : consumers)
                renderQueuedConsumer(instance, consumer);
        });
    }

    public void loadScreenLayer(Consumer<NVGU> vg) {
        if (vg == null)
            return;

        synchronized (renderQueueLock) {
            if (screenLayerRenderQueue.size() >= MAX_QUEUED_RENDERERS) {
                screenLayerRenderQueue.pollFirst();
                droppedRenderers++;
                logQueueOverflow();
            }
            screenLayerRenderQueue.addLast(vg);
        }
    }

    public boolean hasQueuedScreenLayerRenderers() {
        synchronized (renderQueueLock) {
            return !screenLayerRenderQueue.isEmpty();
        }
    }

    public void renderQueuedScreenLayer() {
        NVGU vg = NVGU.INSTANCE;
        if (vg == null || !vg.isCreated()) {
            discardScreenLayerRenderQueue();
            return;
        }

        synchronized (renderQueueLock) {
            Deque<Consumer<NVGU>> pendingRenderers = screenLayerRenderQueue;
            screenLayerRenderQueue = screenLayerFrameRenderQueue;
            screenLayerFrameRenderQueue = pendingRenderers;
            screenLayerRenderQueue.clear();
        }
        if (screenLayerFrameRenderQueue.isEmpty()) return;

        try {
            renderNow(screenLayerQueuedRenderConsumer);
        } finally {
            screenLayerFrameRenderQueue.clear();
        }
    }

    public void discardScreenLayerRenderQueue() {
        synchronized (renderQueueLock) {
            screenLayerRenderQueue.clear();
            screenLayerFrameRenderQueue.clear();
        }
    }

    private void renderScreenLayerFrameQueue(NVGU vg) {
        Consumer<NVGU> renderer;
        while ((renderer = screenLayerFrameRenderQueue.pollFirst()) != null)
            renderQueuedConsumer(vg, renderer);
    }

    public void loadOverlayLayer(Consumer<NVGU> vg) {
        if (vg == null)
            return;

        synchronized (renderQueueLock) {
            if (overlayLayerRenderQueue.size() >= MAX_QUEUED_RENDERERS) {
                overlayLayerRenderQueue.pollFirst();
                droppedRenderers++;
                logQueueOverflow();
            }
            overlayLayerRenderQueue.addLast(vg);
        }
    }

    public boolean hasQueuedOverlayLayerRenderers() {
        synchronized (renderQueueLock) {
            return !overlayLayerRenderQueue.isEmpty();
        }
    }

    public void renderQueuedOverlayLayer() {
        NVGU vg = NVGU.INSTANCE;
        if (vg == null || !vg.isCreated()) {
            discardOverlayLayerRenderQueue();
            return;
        }

        synchronized (renderQueueLock) {
            Deque<Consumer<NVGU>> pendingRenderers = overlayLayerRenderQueue;
            overlayLayerRenderQueue = overlayLayerFrameRenderQueue;
            overlayLayerFrameRenderQueue = pendingRenderers;
            overlayLayerRenderQueue.clear();
        }
        if (overlayLayerFrameRenderQueue.isEmpty()) return;

        try {
            renderNow(overlayLayerQueuedRenderConsumer);
        } finally {
            overlayLayerFrameRenderQueue.clear();
        }
    }

    public void discardOverlayLayerRenderQueue() {
        synchronized (renderQueueLock) {
            overlayLayerRenderQueue.clear();
            overlayLayerFrameRenderQueue.clear();
        }
    }

    private void renderOverlayLayerFrameQueue(NVGU vg) {
        Consumer<NVGU> renderer;
        while ((renderer = overlayLayerFrameRenderQueue.pollFirst()) != null)
            renderQueuedConsumer(vg, renderer);
    }

    public void loadHudLayer(Consumer<NVGU> vg) {
        if (vg == null)
            return;

        synchronized (renderQueueLock) {
            if (hudLayerRenderQueue.size() >= MAX_QUEUED_RENDERERS) {
                hudLayerRenderQueue.pollFirst();
                droppedRenderers++;
                logQueueOverflow();
            }
            hudLayerRenderQueue.addLast(vg);
        }
    }

    public boolean hasQueuedHudLayerRenderers() {
        synchronized (renderQueueLock) {
            return !hudLayerRenderQueue.isEmpty();
        }
    }

    public void renderQueuedHudLayer() {
        NVGU vg = NVGU.INSTANCE;
        if (vg == null || !vg.isCreated()) {
            discardHudLayerRenderQueue();
            return;
        }

        synchronized (renderQueueLock) {
            Deque<Consumer<NVGU>> pendingRenderers = hudLayerRenderQueue;
            hudLayerRenderQueue = hudLayerFrameRenderQueue;
            hudLayerFrameRenderQueue = pendingRenderers;
            hudLayerRenderQueue.clear();
        }
        if (hudLayerFrameRenderQueue.isEmpty()) return;

        try {
            renderNow(hudLayerQueuedRenderConsumer);
        } finally {
            hudLayerFrameRenderQueue.clear();
        }
    }

    public void discardHudLayerRenderQueue() {
        synchronized (renderQueueLock) {
            hudLayerRenderQueue.clear();
            hudLayerFrameRenderQueue.clear();
        }
    }

    private void renderHudLayerFrameQueue(NVGU vg) {
        Consumer<NVGU> renderer;
        while ((renderer = hudLayerFrameRenderQueue.pollFirst()) != null)
            renderQueuedConsumer(vg, renderer);
    }

    public void renderQueuedBeforeGui() {
        NVGU vg = NVGU.INSTANCE;
        if (vg == null || !vg.isCreated()) {
            clearBeforeGuiQueues();
            return;
        }

        synchronized (renderQueueLock) {
            Deque<Consumer<NVGU>> pendingRenderers = beforeGuiRenderQueue;
            beforeGuiRenderQueue = beforeGuiFrameRenderQueue;
            beforeGuiFrameRenderQueue = pendingRenderers;
            beforeGuiRenderQueue.clear();
        }
        if (beforeGuiFrameRenderQueue.isEmpty()) return;

        try {
            renderNow(beforeGuiQueuedRenderConsumer);
        } finally {
            beforeGuiFrameRenderQueue.clear();
        }
    }

    private void renderFrameQueue(NVGU vg) {
        Consumer<NVGU> renderer;
        while ((renderer = frameRenderQueue.pollFirst()) != null)
            renderQueuedConsumer(vg, renderer);
    }

    private void renderBeforeGuiFrameQueue(NVGU vg) {
        Consumer<NVGU> renderer;
        while ((renderer = beforeGuiFrameRenderQueue.pollFirst()) != null)
            renderQueuedConsumer(vg, renderer);
    }

    private void renderQueuedConsumer(NVGU vg, Consumer<NVGU> renderer) {
        try {
            renderer.accept(vg);
        } catch (RuntimeException exception) {
            logRendererFailure(renderer, exception);
            try {
                vg.restartFrameAfterFailure(getUiScale());
            } catch (RuntimeException resetException) {
                exception.addSuppressed(resetException);
                throw exception;
            }
        }
    }

    private void logRendererFailure(Consumer<NVGU> renderer, RuntimeException exception) {
        Class<?> rendererClass = renderer.getClass();
        long now = System.nanoTime();
        Long previousLog = rendererFailureLogTimes.get(rendererClass);
        if (previousLog != null && now - previousLog < FAILURE_LOG_INTERVAL_NANOS)
            return;

        rendererFailureLogTimes.put(rendererClass, now);
        LOGGER.error("Failed to render queued NanoVG component {}", rendererClass.getName(), exception);
    }

    public void renderImmediate(Consumer<NVGU> vg) {
        if (vg == null)
            return;

        NVGU instance = NVGU.INSTANCE;
        if (instance == null || !instance.isCreated())
            return;

        if (GraphicsApiCompatibility.usesCompatibilityRenderer()
                && !GraphicsApiCompatibility.isLayerActive()) {
            GraphicsApiCompatibility.renderOffscreenLayer(() -> renderNow(vg));
            return;
        }

        renderNow(vg);
    }

    public boolean hasQueuedRenderers() {
        synchronized (renderQueueLock) {
            return !renderQueue.isEmpty() || !frameRenderQueue.isEmpty();
        }
    }

    public boolean hasQueuedBeforeGuiRenderers() {
        synchronized (renderQueueLock) {
            return !beforeGuiRenderQueue.isEmpty() || !beforeGuiFrameRenderQueue.isEmpty();
        }
    }

    public void shutdown() {
        clearQueues();
        rendererFailureLogTimes.clear();
    }

    public void discardQueuedRenderers() {
        clearQueues();
    }

    public void discardRenderQueue() {
        synchronized (renderQueueLock) {
            renderQueue.clear();
            frameRenderQueue.clear();
            worldAnchoredQueued = false;
        }
    }

    public void discardBeforeGuiRenderQueue() {
        clearBeforeGuiQueues();
    }

    private void clearQueues() {
        synchronized (renderQueueLock) {
            worldAnchoredQueued = false;
            renderQueue.clear();
            frameRenderQueue.clear();
            beforeGuiRenderQueue.clear();
            beforeGuiFrameRenderQueue.clear();
            screenLayerRenderQueue.clear();
            screenLayerFrameRenderQueue.clear();
            hudLayerRenderQueue.clear();
            hudLayerFrameRenderQueue.clear();
            overlayLayerRenderQueue.clear();
            overlayLayerFrameRenderQueue.clear();
        }
    }

    private void clearBeforeGuiQueues() {
        synchronized (renderQueueLock) {
            beforeGuiRenderQueue.clear();
            beforeGuiFrameRenderQueue.clear();
        }
    }

    private void logQueueOverflow() {
        long now = System.nanoTime();
        if (lastQueueOverflowLogNanos != 0L && now - lastQueueOverflowLogNanos < FAILURE_LOG_INTERVAL_NANOS)
            return;

        LOGGER.warn("NanoVG render queue reached its limit; dropped {} stale renderer(s)", droppedRenderers);
        droppedRenderers = 0;
        lastQueueOverflowLogNanos = now;
    }

    private void renderNow(Consumer<NVGU> vg) {
        NVGU instance = NVGU.INSTANCE;
        if (instance == null || !instance.isCreated())
            return;

        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int textureBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int sampler = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
        GL13.glActiveTexture(activeTexture);
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int vertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int elementArrayBuffer = GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
        int blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        int blendEquationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
        int blendEquationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        int cullFaceMode = GL11.glGetInteger(GL11.GL_CULL_FACE_MODE);
        int frontFace = GL11.glGetInteger(GL11.GL_FRONT_FACE);
        int stencilWriteMask = GL11.glGetInteger(GL11.GL_STENCIL_WRITEMASK);
        int stencilFunc = GL11.glGetInteger(GL11.GL_STENCIL_FUNC);
        int stencilRef = GL11.glGetInteger(GL11.GL_STENCIL_REF);
        int stencilValueMask = GL11.glGetInteger(GL11.GL_STENCIL_VALUE_MASK);
        int stencilFail = GL11.glGetInteger(GL11.GL_STENCIL_FAIL);
        int stencilDepthFail = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_FAIL);
        int stencilDepthPass = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_PASS);
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, colourWriteMask);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cullFace = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean scissorTest = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean stencilTest = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);

        boolean frameStarted = false;
        boolean frameStateEntered = false;
        Shader2DRenderer.NanoVgFrameState previousFrameState = null;
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL33.glBindSampler(0, 0);
            GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            previousFrameState = Shader2DRenderer.enterNanoVgFrameState();
            frameStateEntered = true;

            int width = mc.getWindow().getScreenWidth();
            int height = mc.getWindow().getScreenHeight();
            int framebufferWidth = mc.getWindow().getWidth();
            int framebufferHeight = mc.getWindow().getHeight();
            if (GraphicsApiCompatibility.usesCompatibilityRenderer()
                    && GraphicsApiCompatibility.isLayerActive()) {
                framebufferWidth = GraphicsApiCompatibility.getActiveLayerWidth();
                framebufferHeight = GraphicsApiCompatibility.getActiveLayerHeight();
            }
            float devicePixelRatio = Math.max(1.0f, Math.max(
                    framebufferWidth / (float) Math.max(1, width),
                    framebufferHeight / (float) Math.max(1, height)
            ));
            float uiScale = getUiScale();

            instance.beginFrame(width, height, devicePixelRatio);
            frameStarted = true;
            instance.save();
            try {
                instance.scale(0f, 0f, uiScale);
                vg.accept(instance);
            } finally {
                instance.restore();
            }
        } finally {
            try {
                try {
                    if (frameStarted) {
                        instance.endFrame();
                    }
                } finally {
                    if (frameStateEntered)
                        Shader2DRenderer.exitNanoVgFrameState(previousFrameState);
                }
            } finally {
                // NanoVG flushing may fail. Never leave Minecraft's GL state partially replaced.
                restoreCapability(GL11.GL_BLEND, blend);
                GL20.glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha);
                GL14.glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
                restoreCapability(GL11.GL_DEPTH_TEST, depthTest);
                restoreCapability(GL11.GL_CULL_FACE, cullFace);
                GL11.glCullFace(cullFaceMode);
                GL11.glFrontFace(frontFace);
                restoreCapability(GL11.GL_SCISSOR_TEST, scissorTest);
                restoreCapability(GL11.GL_STENCIL_TEST, stencilTest);
                GL11.glStencilMask(stencilWriteMask);
                GL11.glStencilFunc(stencilFunc, stencilRef, stencilValueMask);
                GL11.glStencilOp(stencilFail, stencilDepthFail, stencilDepthPass);
                GL11.glColorMask(colourWriteMask.get(0) != 0, colourWriteMask.get(1) != 0, colourWriteMask.get(2) != 0, colourWriteMask.get(3) != 0);
                GL11.glDepthMask(depthMask);
                GL20.glUseProgram(program);
                GL30.glBindVertexArray(vertexArray);
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer);
                GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, elementArrayBuffer);
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL33.glBindSampler(0, sampler);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureBinding);
                GL13.glActiveTexture(activeTexture);
            }
        }
    }

    private static void restoreCapability(int capability, boolean enabled) {
        if (enabled) {
            GL11.glEnable(capability);
        } else {
            GL11.glDisable(capability);
        }
    }
}
