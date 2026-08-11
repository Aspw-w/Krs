package xyz.breadloaf.imguimc.imgui;

import com.instrumentalist.mixin.Initializer;
import com.instrumentalist.krs.utils.render.GraphicsApiCompatibility;
import com.mojang.logging.LogUtils;
import imgui.*;
import imgui.flag.*;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import xyz.breadloaf.imguimc.font.FontExtractor;
import xyz.breadloaf.imguimc.interfaces.Renderable;
import xyz.breadloaf.imguimc.interfaces.Theme;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.glfwGetCurrentContext;
import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;

public class ImguiLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long CONTENT_SCALE_REFRESH_INTERVAL_NANOS = 250_000_000L;

    private static ImGuiImplGlfw imGuiGlfw = null;
    private static ImGuiImplGl3 imGuiGl3 = null;

    private static long windowHandle;
    private static float appliedUiScale = 1.0f;
    private static float loadedFontScale = -1.0f;
    private static float cachedWindowContentScale = 1.0f;
    private static long lastContentScaleRefreshNanos;
    private static long lastFrameFailureLogNanos;
    private static final Map<Class<?>, Long> componentFailureLogTimes = new HashMap<>();

    private static boolean fontLoaded = false;
    private static boolean initialized = false;
    private static boolean contextCreated = false;
    private static boolean customFontAvailable = false;
    private static boolean renderedComponentsLastFrame = false;

    public static void onGlfwInit(long handle) {
        if (initialized)
            return;

        if (GraphicsApiCompatibility.usesCompatibilityRenderer()
                && !GraphicsApiCompatibility.isLayerActive()) {
            GraphicsApiCompatibility.runWithOpenGlContext(() -> initialize(handle));
            return;
        }

        initialize(handle);
    }

    private static void initialize(long handle) {
        if (initialized)
            return;

        try {
            FontExtractor.extractFont();
            customFontAvailable = true;
        } catch (IOException exception) {
            customFontAvailable = false;
            LOGGER.warn("Could not extract the custom ImGui font; using the default font", exception);
        }
        windowHandle = handle;
        try {
            initializeImGui();
            imGuiGlfw = new ImGuiImplGlfw();
            imGuiGlfw.init(handle,true);
            imGuiGl3 = new ImGuiImplGl3();
            imGuiGl3.init();
            applyDisplayScale();
            initialized = true;
        } catch (RuntimeException | Error error) {
            shutdown();
            throw error;
        }
    }

    private static void rebuildCustomFont(float scale) {
        ImGuiIO io = ImGui.getIO();
        ImFontAtlas fontAtlas = io.getFonts();
        String fontPath = FontExtractor.getFontPath("arial.ttf");
        float fontSize = Math.max(8.0f, Math.round(17.0f * scale));

        fontAtlas.clear();

        ImFontConfig fontConfig = new ImFontConfig();
        try {
            fontConfig.setPixelSnapH(true);
            fontConfig.setOversampleH(3);
            fontConfig.setOversampleV(2);

            ImFont customFont = fontAtlas.addFontFromFileTTF(fontPath, fontSize, fontConfig);
            if (customFont != null && customFont.isValidPtr()) {
                io.setFontDefault(customFont);
            } else {
                customFontAvailable = false;
                io.setFontDefault(fontAtlas.addFontDefault());
                LOGGER.warn("Could not load the custom ImGui font {}; using the default font", fontPath);
            }
        } finally {
            fontConfig.destroy();
        }

        fontAtlas.build();
        imGuiGl3.updateFontsTexture();
        fontLoaded = true;
        loadedFontScale = scale;
    }

    public static void onFrameRender() {
        if (!shouldRenderFrame())
            return;

        if (GraphicsApiCompatibility.usesCompatibilityRenderer()
                && !GraphicsApiCompatibility.isLayerActive()) {
            GraphicsApiCompatibility.renderOffscreenLayer(
                    GraphicsApiCompatibility.Layer.IMGUI,
                    ImguiLoader::renderFrame
            );
            return;
        }

        renderFrame();
    }

    public static boolean shouldRenderFrame() {
        return initialized && (!Initializer.renderstack.isEmpty() || renderedComponentsLastFrame);
    }

    private static void renderFrame() {
        if (!initialized) return;

        boolean hasComponents = !Initializer.renderstack.isEmpty();
        if (!hasComponents && !renderedComponentsLastFrame)
            return;
        if (hasComponents)
            renderedComponentsLastFrame = true;

        boolean frameStarted = false;
        try {
            applyDisplayScale();
            imGuiGlfw.newFrame();
            ImGui.newFrame();
            frameStarted = true;

            setupDocking();
            try {
                for (Renderable renderable: Initializer.renderstack) {
                    if (renderable == null)
                        continue;

                    Theme theme = null;
                    try {
                        theme = renderable.getTheme();
                        if (theme == null)
                            throw new IllegalStateException("ImGui renderable returned a null theme");
                        theme.preRender();
                        renderable.render();
                    } catch (RuntimeException exception) {
                        logComponentFailure(renderable, exception);
                    } finally {
                        if (theme != null) {
                            try {
                                theme.postRender();
                            } catch (RuntimeException exception) {
                                logComponentFailure(renderable, exception);
                            }
                        }
                    }
                }
            } finally {
                finishDocking();
            }

            ImGui.render();
            frameStarted = false;
            endFrame(windowHandle);
            if (!hasComponents)
                renderedComponentsLastFrame = false;
        } catch (RuntimeException exception) {
            if (frameStarted) {
                try {
                    ImGui.endFrame();
                } catch (RuntimeException endFrameException) {
                    exception.addSuppressed(endFrameException);
                }
            }
            logFrameFailure(exception);
        }
    }

    private static void logFrameFailure(RuntimeException exception) {
        long now = System.nanoTime();
        if (lastFrameFailureLogNanos != 0L && now - lastFrameFailureLogNanos < 10_000_000_000L)
            return;

        lastFrameFailureLogNanos = now;
        LOGGER.error("Failed to render the ImGui frame", exception);
    }

    private static void logComponentFailure(Renderable renderable, RuntimeException exception) {
        Class<?> renderableClass = renderable.getClass();
        long now = System.nanoTime();
        Long previousLog = componentFailureLogTimes.get(renderableClass);
        if (previousLog != null && now - previousLog < 10_000_000_000L)
            return;

        componentFailureLogTimes.put(renderableClass, now);
        LOGGER.error("Failed to render ImGui component {}", renderableClass.getName(), exception);
    }

    private static void setupDocking() {
        int windowFlags = ImGuiWindowFlags.NoDocking;

        ImGuiViewport viewport = ImGui.getMainViewport();
        ImGui.setNextWindowPos(viewport.getPosX(), viewport.getPosY(), ImGuiCond.Always);
        ImGui.setNextWindowSize(viewport.getSizeX(), viewport.getSizeY());
        windowFlags |= ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoMove |
                ImGuiWindowFlags.NoBringToFrontOnFocus | ImGuiWindowFlags.NoNavFocus | ImGuiWindowFlags.NoBackground;

        ImGui.begin("imgui-mc docking host window", windowFlags);

        ImGui.dockSpace(Initializer.getDockId(), 0, 0, ImGuiDockNodeFlags.PassthruCentralNode |
                ImGuiDockNodeFlags.NoCentralNode | ImGuiDockNodeFlags.NoDockingInCentralNode);
    }

    private static void finishDocking() {
        ImGui.end();
    }

    private static void initializeImGui() {
        ImGui.createContext();
        contextCreated = true;

        final ImGuiIO io = ImGui.getIO();

        io.setIniFilename(null);                               // We don't want to save .ini file
        io.addConfigFlags(ImGuiConfigFlags.NavEnableKeyboard); // Enable KeyboardHandler Controls
        io.addConfigFlags(ImGuiConfigFlags.DockingEnable);     // Enable Docking
        if (!GraphicsApiCompatibility.usesCompatibilityRenderer()) {
            io.addConfigFlags(ImGuiConfigFlags.ViewportsEnable); // Platform windows require Minecraft's OpenGL context
            io.setConfigViewportsNoTaskBarIcon(true);
        }

        final ImFontAtlas fontAtlas = io.getFonts();
        fontAtlas.addFontDefault();

        if (io.hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)) {
            final ImGuiStyle style = ImGui.getStyle();
            style.setWindowRounding(0.0f);
            style.setColor(ImGuiCol.WindowBg, ImGui.getColorU32(ImGuiCol.WindowBg, 1));
        }
    }

    private static void applyDisplayScale() {
        float scale = getWindowContentScale();
        ImGuiIO io = ImGui.getIO();
        io.setFontGlobalScale(1.0f);

        if (customFontAvailable && (!fontLoaded || Math.abs(scale - loadedFontScale) > 0.01f)) {
            rebuildCustomFont(scale);
        }

        if (Math.abs(scale - appliedUiScale) > 0.01f) {
            ImGui.getStyle().scaleAllSizes(scale / appliedUiScale);
            appliedUiScale = scale;
        }
    }

    private static float getWindowContentScale() {
        long now = System.nanoTime();
        if (lastContentScaleRefreshNanos != 0L
                && now - lastContentScaleRefreshNanos < CONTENT_SCALE_REFRESH_INTERVAL_NANOS)
            return cachedWindowContentScale;

        float scale = 1.0f;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer xScale = stack.mallocFloat(1);
            FloatBuffer yScale = stack.mallocFloat(1);
            GLFW.glfwGetWindowContentScale(windowHandle, xScale, yScale);
            scale = Math.max(xScale.get(0), yScale.get(0));
        } catch (Throwable ignored) {
            scale = 1.0f;
        }

        if (!Float.isFinite(scale) || scale <= 0.0f)
            scale = 1.0f;

        cachedWindowContentScale = Math.max(1.0f, scale);
        lastContentScaleRefreshNanos = now;
        return cachedWindowContentScale;
    }

    private static void endFrame(long windowPtr) {
        // After Dear ImGui prepared a draw data, we use it in the LWJGL3 renderer.
        // At that moment ImGui will be rendered to the current OpenGL context.
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int textureBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int sampler = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
        boolean scissorTest = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        GL13.glActiveTexture(activeTexture);

        try {
            prepareImGuiGlState();
            imGuiGl3.renderDrawData(ImGui.getDrawData());

            if (ImGui.getIO().hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)) {
                final long backupWindowPtr = glfwGetCurrentContext();
                try {
                    ImGui.updatePlatformWindows();
                    prepareImGuiGlState();
                    ImGui.renderPlatformWindowsDefault();
                } finally {
                    glfwMakeContextCurrent(backupWindowPtr);
                }
            }
        } finally {
            restoreCapability(GL11.GL_SCISSOR_TEST, scissorTest);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL33.glBindSampler(0, sampler);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureBinding);
            GL13.glActiveTexture(activeTexture);
        }

        //glfwSwapBuffers(windowPtr);
        //glfwPollEvents();
    }

    private static void prepareImGuiGlState() {
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL33.glBindSampler(0, 0);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private static void restoreCapability(int capability, boolean enabled) {
        if (enabled) {
            GL11.glEnable(capability);
        } else {
            GL11.glDisable(capability);
        }
    }

    public static boolean wantsCaptureKeyboard() {
        return initialized && ImGui.getIO().getWantCaptureKeyboard();
    }

    public static boolean wantsTextInput() {
        return initialized && ImGui.getIO().getWantTextInput();
    }

    public static boolean wantsCaptureMouse() {
        return initialized && ImGui.getIO().getWantCaptureMouse();
    }

    public static void shutdown() {
        if (GraphicsApiCompatibility.usesCompatibilityRenderer()
                && !GraphicsApiCompatibility.isLayerActive()) {
            GraphicsApiCompatibility.runWithOpenGlContext(ImguiLoader::shutdownInternal);
            return;
        }

        shutdownInternal();
    }

    private static void shutdownInternal() {
        initialized = false;
        fontLoaded = false;
        customFontAvailable = false;
        renderedComponentsLastFrame = false;
        loadedFontScale = -1.0f;
        appliedUiScale = 1.0f;
        cachedWindowContentScale = 1.0f;
        lastContentScaleRefreshNanos = 0L;
        lastFrameFailureLogNanos = 0L;
        componentFailureLogTimes.clear();
        windowHandle = 0L;

        if (imGuiGl3 != null) {
            try {
                imGuiGl3.dispose();
            } catch (Throwable exception) {
                LOGGER.warn("Failed to dispose the ImGui OpenGL backend", exception);
            } finally {
                imGuiGl3 = null;
            }
        }

        if (imGuiGlfw != null) {
            try {
                imGuiGlfw.dispose();
            } catch (Throwable exception) {
                LOGGER.warn("Failed to dispose the ImGui GLFW backend", exception);
            } finally {
                imGuiGlfw = null;
            }
        }

        if (contextCreated) {
            try {
                ImGui.destroyContext();
            } catch (Throwable exception) {
                LOGGER.warn("Failed to destroy the ImGui context", exception);
            } finally {
                contextCreated = false;
            }
        }
    }
}
