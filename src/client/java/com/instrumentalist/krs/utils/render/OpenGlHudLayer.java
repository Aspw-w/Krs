package com.instrumentalist.krs.utils.render;

import com.instrumentalist.krs.utils.nanovg.NanoVGManager;
import com.mojang.logging.LogUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public final class OpenGlHudLayer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long MAX_FRAME_SAMPLE_NANOS = 100_000_000L;
    private static final float ENTER_LAYER_RATIO = 0.7f;
    private static final float LEAVE_LAYER_RATIO = 0.9f;

    private static final String VERTEX_SHADER = """
            #version 330 core

            const vec2 POSITIONS[3] = vec2[](
                vec2(-1.0, -1.0),
                vec2( 3.0, -1.0),
                vec2(-1.0,  3.0)
            );

            void main() {
                gl_Position = vec4(POSITIONS[gl_VertexID], 0.0, 1.0);
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #version 330 core

            uniform sampler2D uLayer;
            uniform vec2 uOrigin;

            out vec4 fragColor;

            void main() {
                fragColor = texelFetch(uLayer, ivec2(gl_FragCoord.xy - uOrigin), 0);
            }
            """;

    private static boolean failed;
    private static int framebuffer;
    private static int colorTexture;
    private static int depthStencilRenderbuffer;
    private static int backdropTexture;
    private static int program;
    private static int vertexArray;
    private static int layerUniform = -1;
    private static int originUniform = -1;
    private static int width;
    private static int height;
    private static boolean valid;
    private static boolean layerMode;
    private static long lastFrameNanos;
    private static float averageFrameNanos;
    private static long nextRenderNanos;

    private OpenGlHudLayer() {
    }

    public static void render(NanoVGManager manager) {
        long now = System.nanoTime();
        boolean useLayer = updateMode(now);
        if (failed || !useLayer || !manager.hasQueuedRenderers() || manager.hasWorldAnchoredRenderers()) {
            valid = false;
            manager.renderQueued();
            return;
        }

        try {
            renderLayered(manager, now);
        } catch (RuntimeException exception) {
            failed = true;
            valid = false;
            manager.discardRenderQueue();
            LOGGER.error("Disabled the cached HUD layer after a rendering failure", exception);
        }
    }

    public static void destroy() {
        if (program != 0) {
            GL20.glDeleteProgram(program);
            program = 0;
        }
        if (vertexArray != 0) {
            GL30.glDeleteVertexArrays(vertexArray);
            vertexArray = 0;
        }
        destroyTargets();
        valid = false;
    }

    private static boolean updateMode(long now) {
        if (lastFrameNanos != 0L) {
            long sample = Math.min(MAX_FRAME_SAMPLE_NANOS, now - lastFrameNanos);
            averageFrameNanos = averageFrameNanos == 0f ? sample : averageFrameNanos * 0.95f + sample * 0.05f;
        }
        lastFrameNanos = now;

        if (averageFrameNanos == 0f)
            return layerMode = false;

        long interval = GraphicsApiCompatibility.displayRefreshIntervalNanos(now);
        if (layerMode)
            layerMode = averageFrameNanos < interval * LEAVE_LAYER_RATIO;
        else
            layerMode = averageFrameNanos < interval * ENTER_LAYER_RATIO;
        return layerMode;
    }

    private static void renderLayered(NanoVGManager manager, long now) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer viewport = stack.mallocInt(4);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            int viewportX = viewport.get(0);
            int viewportY = viewport.get(1);
            int viewportWidth = viewport.get(2);
            int viewportHeight = viewport.get(3);
            if (viewportWidth <= 0 || viewportHeight <= 0) {
                valid = false;
                manager.renderQueued();
                return;
            }

            IntBuffer scissorBox = stack.mallocInt(4);
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBox);
            ByteBuffer colorMask = stack.malloc(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, colorMask);
            int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            int renderbuffer = GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);
            int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            int textureBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            int sampler = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
            int currentProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            int currentVertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            int blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
            int blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
            int blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
            int blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
            int blendEquationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
            int blendEquationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
            int stencilWriteMask = GL11.glGetInteger(GL11.GL_STENCIL_WRITEMASK);
            boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
            boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            boolean cullFace = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            boolean scissorTest = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            boolean stencilTest = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);

            try {
                ensureProgram();
                boolean resized = ensureTargets(viewportWidth, viewportHeight);
                boolean redraw = resized || !valid || now >= nextRenderNanos;

                if (redraw) {
                    GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
                    GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
                    GL33.glBindSampler(0, 0);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, backdropTexture);
                    GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, viewportX, viewportY, viewportWidth, viewportHeight);

                    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
                    GL11.glViewport(0, 0, viewportWidth, viewportHeight);
                    GL11.glDisable(GL11.GL_SCISSOR_TEST);
                    GL11.glColorMask(true, true, true, true);
                    GL11.glDepthMask(true);
                    GL11.glStencilMask(0xFF);
                    GL11.glClearColor(0f, 0f, 0f, 0f);
                    GL11.glClearDepth(1.0);
                    GL11.glClearStencil(0);
                    GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);

                    valid = false;
                    GraphicsApiCompatibility.runDirectOpenGlLayer(viewportWidth, viewportHeight, backdropTexture, manager::renderQueued);
                    valid = true;
                    scheduleNextRender(now);
                } else {
                    manager.discardRenderQueue();
                }

                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
                GL11.glViewport(viewportX, viewportY, viewportWidth, viewportHeight);
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glDisable(GL11.GL_CULL_FACE);
                GL11.glDisable(GL11.GL_STENCIL_TEST);
                GL11.glDepthMask(false);
                GL11.glColorMask(true, true, true, true);
                GL11.glEnable(GL11.GL_BLEND);
                GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
                GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL20.glUseProgram(program);
                GL20.glUniform1i(layerUniform, 0);
                GL20.glUniform2f(originUniform, viewportX, viewportY);
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL33.glBindSampler(0, 0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTexture);
                GL30.glBindVertexArray(vertexArray);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            } finally {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
                GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, renderbuffer);
                GL11.glViewport(viewportX, viewportY, viewportWidth, viewportHeight);
                GL11.glScissor(scissorBox.get(0), scissorBox.get(1), scissorBox.get(2), scissorBox.get(3));
                setCapability(GL11.GL_SCISSOR_TEST, scissorTest);
                setCapability(GL11.GL_DEPTH_TEST, depthTest);
                setCapability(GL11.GL_CULL_FACE, cullFace);
                setCapability(GL11.GL_STENCIL_TEST, stencilTest);
                setCapability(GL11.GL_BLEND, blend);
                GL20.glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha);
                GL14.glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
                GL11.glDepthMask(depthMask);
                GL11.glStencilMask(stencilWriteMask);
                GL11.glColorMask(colorMask.get(0) != 0, colorMask.get(1) != 0, colorMask.get(2) != 0, colorMask.get(3) != 0);
                GL20.glUseProgram(currentProgram);
                GL30.glBindVertexArray(currentVertexArray);
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL33.glBindSampler(0, sampler);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureBinding);
                GL13.glActiveTexture(activeTexture);
            }
        }
    }

    private static void scheduleNextRender(long now) {
        long interval = GraphicsApiCompatibility.displayRefreshIntervalNanos(now);
        long next = nextRenderNanos;
        if (next == 0L || now - next > interval * 4L) {
            nextRenderNanos = now + interval;
            return;
        }

        do {
            next += interval;
        } while (next <= now);
        nextRenderNanos = next;
    }

    private static boolean ensureTargets(int targetWidth, int targetHeight) {
        if (framebuffer != 0 && width == targetWidth && height == targetHeight)
            return false;

        destroyTargets();

        colorTexture = createTexture(targetWidth, targetHeight);
        backdropTexture = createTexture(targetWidth, targetHeight);
        depthStencilRenderbuffer = GL30.glGenRenderbuffers();
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthStencilRenderbuffer);
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH24_STENCIL8, targetWidth, targetHeight);

        framebuffer = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, colorTexture, 0);
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT, GL30.GL_RENDERBUFFER, depthStencilRenderbuffer);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            destroyTargets();
            throw new IllegalStateException("Cached HUD layer framebuffer is incomplete: 0x" + Integer.toHexString(status));
        }

        width = targetWidth;
        height = targetHeight;
        valid = false;
        return true;
    }

    private static int createTexture(int textureWidth, int textureHeight) {
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, textureWidth, textureHeight, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);
        return texture;
    }

    private static void destroyTargets() {
        if (framebuffer != 0) {
            GL30.glDeleteFramebuffers(framebuffer);
            framebuffer = 0;
        }
        if (colorTexture != 0) {
            GL11.glDeleteTextures(colorTexture);
            colorTexture = 0;
        }
        if (backdropTexture != 0) {
            GL11.glDeleteTextures(backdropTexture);
            backdropTexture = 0;
        }
        if (depthStencilRenderbuffer != 0) {
            GL30.glDeleteRenderbuffers(depthStencilRenderbuffer);
            depthStencilRenderbuffer = 0;
        }
        width = 0;
        height = 0;
    }

    private static void ensureProgram() {
        if (program != 0)
            return;

        int vertexShader = compileShader(GL20.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fragmentShader = compileShader(GL20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        int linked = GL20.glCreateProgram();
        GL20.glAttachShader(linked, vertexShader);
        GL20.glAttachShader(linked, fragmentShader);
        GL20.glLinkProgram(linked);
        GL20.glDeleteShader(vertexShader);
        GL20.glDeleteShader(fragmentShader);
        if (GL20.glGetProgrami(linked, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(linked);
            GL20.glDeleteProgram(linked);
            throw new IllegalStateException("Could not link the cached HUD layer shader: " + log);
        }

        program = linked;
        layerUniform = GL20.glGetUniformLocation(program, "uLayer");
        originUniform = GL20.glGetUniformLocation(program, "uOrigin");
        vertexArray = GL30.glGenVertexArrays();
    }

    private static int compileShader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("Could not compile the cached HUD layer shader: " + log);
        }
        return shader;
    }

    private static void setCapability(int capability, boolean enabled) {
        if (enabled)
            GL11.glEnable(capability);
        else
            GL11.glDisable(capability);
    }
}
