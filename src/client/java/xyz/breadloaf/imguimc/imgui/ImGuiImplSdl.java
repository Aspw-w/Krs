package xyz.breadloaf.imguimc.imgui;

import com.instrumentalist.krs.utils.input.Keys;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.callback.ImStrConsumer;
import imgui.callback.ImStrSupplier;
import imgui.flag.ImGuiBackendFlags;
import imgui.flag.ImGuiConfigFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiMouseCursor;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLClipboard;
import org.lwjgl.sdl.SDLKeyboard;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.sdl.SDLTimer;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDL_Event;
import org.lwjgl.sdl.SDL_KeyboardEvent;
import org.lwjgl.sdl.SDL_MouseButtonEvent;
import org.lwjgl.sdl.SDL_MouseWheelEvent;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Dear ImGui platform backend for the SDL3 window used by Minecraft 26.3+.
 * <p>
 * Mirrors the single-viewport behaviour of imgui-java's {@code ImGuiImplGlfw}. Minecraft owns the SDL event loop, so
 * events are forwarded from {@code SDLEventHandler} through {@link #processEvent(SDL_Event)} instead of callbacks.
 */
public class ImGuiImplSdl {
    private static final int SDL_EVENT_KEY_DOWN = 0x300;
    private static final int SDL_EVENT_KEY_UP = 0x301;
    private static final int SDL_EVENT_TEXT_INPUT = 0x303;
    private static final int SDL_EVENT_MOUSE_BUTTON_DOWN = 0x401;
    private static final int SDL_EVENT_MOUSE_WHEEL = 0x403;
    private static final int SDL_EVENT_WINDOW_FOCUS_GAINED = 0x20E;
    private static final int SDL_EVENT_WINDOW_FOCUS_LOST = 0x20F;
    private static final int KEYS_DOWN_SIZE = 512;
    private static final Object TEXT_INPUT_OWNER = new Object();

    private long windowPtr;
    private final boolean[] mouseJustPressed = new boolean[ImGuiMouseButton.COUNT];
    private final long[] mouseCursors = new long[ImGuiMouseCursor.COUNT];
    private long lastCursor;
    private long time;
    private boolean textInputActive;

    public boolean init(final long windowId) {
        this.windowPtr = windowId;

        final ImGuiIO io = ImGui.getIO();
        io.addBackendFlags(ImGuiBackendFlags.HasMouseCursors | ImGuiBackendFlags.HasSetMousePos);
        io.setBackendPlatformName("krs_imgui_impl_sdl3");

        // Keyboard mapping. ImGui uses these indices to peek into io.KeysDown[], which is indexed by SDL scancode.
        final int[] keyMap = new int[ImGuiKey.COUNT];
        keyMap[ImGuiKey.Tab] = Keys.KEY_TAB;
        keyMap[ImGuiKey.LeftArrow] = Keys.KEY_LEFT;
        keyMap[ImGuiKey.RightArrow] = Keys.KEY_RIGHT;
        keyMap[ImGuiKey.UpArrow] = Keys.KEY_UP;
        keyMap[ImGuiKey.DownArrow] = Keys.KEY_DOWN;
        keyMap[ImGuiKey.PageUp] = Keys.KEY_PAGE_UP;
        keyMap[ImGuiKey.PageDown] = Keys.KEY_PAGE_DOWN;
        keyMap[ImGuiKey.Home] = Keys.KEY_HOME;
        keyMap[ImGuiKey.End] = Keys.KEY_END;
        keyMap[ImGuiKey.Insert] = Keys.KEY_INSERT;
        keyMap[ImGuiKey.Delete] = Keys.KEY_DELETE;
        keyMap[ImGuiKey.Backspace] = Keys.KEY_BACKSPACE;
        keyMap[ImGuiKey.Space] = Keys.KEY_SPACE;
        keyMap[ImGuiKey.Enter] = Keys.KEY_ENTER;
        keyMap[ImGuiKey.Escape] = Keys.KEY_ESCAPE;
        keyMap[ImGuiKey.KeyPadEnter] = Keys.KEY_KP_ENTER;
        keyMap[ImGuiKey.A] = Keys.KEY_A;
        keyMap[ImGuiKey.C] = Keys.KEY_C;
        keyMap[ImGuiKey.V] = Keys.KEY_V;
        keyMap[ImGuiKey.X] = Keys.KEY_X;
        keyMap[ImGuiKey.Y] = Keys.KEY_Y;
        keyMap[ImGuiKey.Z] = Keys.KEY_Z;
        io.setKeyMap(keyMap);

        io.setGetClipboardTextFn(new ImStrSupplier() {
            @Override
            public String get() {
                final String clipboardString = SDLClipboard.SDL_GetClipboardText();
                return clipboardString != null ? clipboardString : "";
            }
        });
        io.setSetClipboardTextFn(new ImStrConsumer() {
            @Override
            public void accept(final String str) {
                SDLClipboard.SDL_SetClipboardText(str);
            }
        });

        mouseCursors[ImGuiMouseCursor.Arrow] = SDLMouse.SDL_CreateSystemCursor(SDLMouse.SDL_SYSTEM_CURSOR_DEFAULT);
        mouseCursors[ImGuiMouseCursor.TextInput] = SDLMouse.SDL_CreateSystemCursor(SDLMouse.SDL_SYSTEM_CURSOR_TEXT);
        mouseCursors[ImGuiMouseCursor.ResizeAll] = SDLMouse.SDL_CreateSystemCursor(SDLMouse.SDL_SYSTEM_CURSOR_DEFAULT);
        mouseCursors[ImGuiMouseCursor.ResizeNS] = SDLMouse.SDL_CreateSystemCursor(SDLMouse.SDL_SYSTEM_CURSOR_NS_RESIZE);
        mouseCursors[ImGuiMouseCursor.ResizeEW] = SDLMouse.SDL_CreateSystemCursor(SDLMouse.SDL_SYSTEM_CURSOR_EW_RESIZE);
        mouseCursors[ImGuiMouseCursor.ResizeNESW] = SDLMouse.SDL_CreateSystemCursor(SDLMouse.SDL_SYSTEM_CURSOR_DEFAULT);
        mouseCursors[ImGuiMouseCursor.ResizeNWSE] = SDLMouse.SDL_CreateSystemCursor(SDLMouse.SDL_SYSTEM_CURSOR_DEFAULT);
        mouseCursors[ImGuiMouseCursor.Hand] = SDLMouse.SDL_CreateSystemCursor(SDLMouse.SDL_SYSTEM_CURSOR_POINTER);
        mouseCursors[ImGuiMouseCursor.NotAllowed] = SDLMouse.SDL_CreateSystemCursor(SDLMouse.SDL_SYSTEM_CURSOR_DEFAULT);

        ImGui.getMainViewport().setPlatformHandle(windowPtr);
        return true;
    }

    /**
     * Feeds one SDL event into ImGui. Called on the render thread from Minecraft's SDL event pump.
     */
    public void processEvent(final SDL_Event event) {
        final ImGuiIO io = ImGui.getIO();
        switch (event.type()) {
            case SDL_EVENT_KEY_DOWN, SDL_EVENT_KEY_UP -> {
                SDL_KeyboardEvent keyEvent = event.key();
                if (keyEvent.windowID() != SDLVideo.SDL_GetWindowID(windowPtr))
                    return;

                int key = keyEvent.scancode();
                if (key >= 0 && key < KEYS_DOWN_SIZE) {
                    if (event.type() == SDL_EVENT_KEY_DOWN)
                        io.setKeysDown(key, true);
                    else
                        io.setKeysDown(key, false);
                }
                updateModifiers(io);
            }
            case SDL_EVENT_TEXT_INPUT -> {
                if (event.text().windowID() != SDLVideo.SDL_GetWindowID(windowPtr))
                    return;

                String text = event.text().textString();
                if (text != null)
                    text.codePoints().forEach(io::addInputCharacter);
            }
            case SDL_EVENT_MOUSE_BUTTON_DOWN -> {
                SDL_MouseButtonEvent buttonEvent = event.button();
                int button = toImGuiMouseButton(buttonEvent.button());
                if (button >= 0 && button < mouseJustPressed.length)
                    mouseJustPressed[button] = true;
            }
            case SDL_EVENT_MOUSE_WHEEL -> {
                SDL_MouseWheelEvent wheel = event.wheel();
                io.setMouseWheelH(io.getMouseWheelH() + wheel.x());
                io.setMouseWheel(io.getMouseWheel() + wheel.y());
            }
            case SDL_EVENT_WINDOW_FOCUS_GAINED -> io.addFocusEvent(true);
            case SDL_EVENT_WINDOW_FOCUS_LOST -> io.addFocusEvent(false);
            default -> {
            }
        }
    }

    public void newFrame() {
        final ImGuiIO io = ImGui.getIO();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer winWidth = stack.mallocInt(1);
            IntBuffer winHeight = stack.mallocInt(1);
            IntBuffer fbWidth = stack.mallocInt(1);
            IntBuffer fbHeight = stack.mallocInt(1);
            SDLVideo.SDL_GetWindowSize(windowPtr, winWidth, winHeight);
            SDLVideo.SDL_GetWindowSizeInPixels(windowPtr, fbWidth, fbHeight);

            io.setDisplaySize((float) winWidth.get(0), (float) winHeight.get(0));
            if (winWidth.get(0) > 0 && winHeight.get(0) > 0) {
                io.setDisplayFramebufferScale(
                        (float) fbWidth.get(0) / winWidth.get(0),
                        (float) fbHeight.get(0) / winHeight.get(0)
                );
            }
        }

        final long currentTime = SDLTimer.SDL_GetTicksNS();
        io.setDeltaTime(time > 0L && currentTime > time ? (float) ((currentTime - time) / 1_000_000_000.0) : 1.0f / 60.0f);
        time = currentTime;

        updateMousePosAndButtons(io);
        updateMouseCursor(io);
        updateTextInput(io);
    }

    public void dispose() {
        stopTextInput();
        for (int i = 0; i < mouseCursors.length; i++) {
            if (mouseCursors[i] != 0L) {
                SDLMouse.SDL_DestroyCursor(mouseCursors[i]);
                mouseCursors[i] = 0L;
            }
        }
        lastCursor = 0L;
        windowPtr = 0L;
    }

    private void updateModifiers(final ImGuiIO io) {
        io.setKeyCtrl(io.getKeysDown(Keys.KEY_LEFT_CONTROL) || io.getKeysDown(Keys.KEY_RIGHT_CONTROL));
        io.setKeyShift(io.getKeysDown(Keys.KEY_LEFT_SHIFT) || io.getKeysDown(Keys.KEY_RIGHT_SHIFT));
        io.setKeyAlt(io.getKeysDown(Keys.KEY_LEFT_ALT) || io.getKeysDown(Keys.KEY_RIGHT_ALT));
        io.setKeySuper(io.getKeysDown(Keys.KEY_LEFT_SUPER) || io.getKeysDown(Keys.KEY_RIGHT_SUPER));
    }

    private void updateMousePosAndButtons(final ImGuiIO io) {
        final boolean focused = (SDLVideo.SDL_GetWindowFlags(windowPtr) & SDLVideo.SDL_WINDOW_INPUT_FOCUS) != 0;
        final boolean hovered = SDLMouse.SDL_GetMouseFocus() == windowPtr;

        final float mousePosBackupX = io.getMousePosX();
        final float mousePosBackupY = io.getMousePosY();
        io.setMousePos(-Float.MAX_VALUE, -Float.MAX_VALUE);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer mouseX = stack.mallocFloat(1);
            FloatBuffer mouseY = stack.mallocFloat(1);
            int buttons = SDLMouse.SDL_GetMouseState(mouseX, mouseY);

            for (int i = 0; i < ImGuiMouseButton.COUNT; i++) {
                // A press that came in this frame always counts as held, so short clicks are not lost.
                boolean down = focused && (buttons & (1 << (toSdlMouseButton(i) - 1))) != 0;
                io.setMouseDown(i, mouseJustPressed[i] || down);
                mouseJustPressed[i] = false;
            }

            if (io.getWantSetMousePos() && focused)
                SDLMouse.SDL_WarpMouseInWindow(windowPtr, mousePosBackupX, mousePosBackupY);

            if (hovered || focused)
                io.setMousePos(mouseX.get(0), mouseY.get(0));
        }
    }

    private void updateMouseCursor(final ImGuiIO io) {
        if (io.hasConfigFlags(ImGuiConfigFlags.NoMouseCursorChange) || SDLMouse.SDL_GetWindowRelativeMouseMode(windowPtr))
            return;

        final int imguiCursor = ImGui.getMouseCursor();
        if (imguiCursor == ImGuiMouseCursor.None || io.getMouseDrawCursor()) {
            SDLMouse.SDL_HideCursor();
            return;
        }

        long cursor = imguiCursor >= 0 && imguiCursor < mouseCursors.length && mouseCursors[imguiCursor] != 0L
                ? mouseCursors[imguiCursor]
                : mouseCursors[ImGuiMouseCursor.Arrow];
        if (cursor != 0L && cursor != lastCursor) {
            SDLMouse.SDL_SetCursor(cursor);
            lastCursor = cursor;
        }
        SDLMouse.SDL_ShowCursor();
    }

    private void updateTextInput(final ImGuiIO io) {
        // SDL only produces text events while text input is enabled. GLFW always delivered characters, so keep
        // text input enabled whenever an ImGui text field is active.
        if (io.getWantTextInput()) {
            if (!textInputActive) {
                Minecraft.getInstance().textInputManager().startTextInput(TEXT_INPUT_OWNER);
                textInputActive = true;
            }
        } else {
            stopTextInput();
        }
    }

    private void stopTextInput() {
        if (!textInputActive)
            return;

        textInputActive = false;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.textInputManager() != null)
            minecraft.textInputManager().stopTextInput(TEXT_INPUT_OWNER);
    }

    private static int toImGuiMouseButton(final int sdlButton) {
        return switch (sdlButton) {
            case 1 -> ImGuiMouseButton.Left;
            case 2 -> ImGuiMouseButton.Middle;
            case 3 -> ImGuiMouseButton.Right;
            default -> sdlButton - 1;
        };
    }

    private static int toSdlMouseButton(final int imguiButton) {
        return switch (imguiButton) {
            case ImGuiMouseButton.Left -> 1;
            case ImGuiMouseButton.Right -> 3;
            case ImGuiMouseButton.Middle -> 2;
            default -> imguiButton + 1;
        };
    }
}
