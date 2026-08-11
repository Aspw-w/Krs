package com.instrumentalist.krs.utils.nanovg;

import com.instrumentalist.krs.utils.IMinecraft;
import com.instrumentalist.krs.utils.render.GraphicsApiCompatibility;
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
    private final Map<Class<?>, Long> rendererFailureLogTimes = new HashMap<>();
    private final int[] viewport = new int[4];
    private final ByteBuffer colourWriteMask = BufferUtils.createByteBuffer(4);
    private long lastQueueOverflowLogNanos;
    private int droppedRenderers;

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
        }
        if (frameRenderQueue.isEmpty()) return;

        try {
            renderNow(queuedRenderConsumer);
        } finally {
            // Never replay stale renderers after a native or GL failure.
            frameRenderQueue.clear();
        }
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
        }
    }

    public void discardBeforeGuiRenderQueue() {
        clearBeforeGuiQueues();
    }

    private void clearQueues() {
        synchronized (renderQueueLock) {
            renderQueue.clear();
            frameRenderQueue.clear();
            beforeGuiRenderQueue.clear();
            beforeGuiFrameRenderQueue.clear();
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
        int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
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
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, colourWriteMask);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cullFace = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean scissorTest = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean stencilTest = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);

        boolean frameStarted = false;
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
                if (frameStarted) {
                    instance.endFrame();
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
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
                GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
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
