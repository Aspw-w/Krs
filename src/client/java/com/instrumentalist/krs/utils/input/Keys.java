package com.instrumentalist.krs.utils.input;

import com.mojang.blaze3d.platform.InputConstants;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.lwjgl.sdl.SDLKeyboard;

import java.util.Locale;

/**
 * Key, mouse button, action and modifier constants for the SDL3 based input of Minecraft 26.3+.
 * Names mirror the former GLFW constants; values are the SDL scancodes / button indices Minecraft now uses.
 * Key binds saved by older (GLFW based) client versions can be converted with {@link #fromLegacyGlfw(int)}.
 */
public final class Keys {
    /** Client-side "no key bound" sentinel. Kept at -1 so it never matches a real SDL scancode. */
    public static final int KEY_UNKNOWN = -1;

    public static final int KEY_SPACE = InputConstants.KEY_SPACE;
    public static final int KEY_APOSTROPHE = InputConstants.KEY_APOSTROPHE;
    public static final int KEY_COMMA = InputConstants.KEY_COMMA;
    public static final int KEY_MINUS = InputConstants.KEY_MINUS;
    public static final int KEY_PERIOD = InputConstants.KEY_PERIOD;
    public static final int KEY_SLASH = InputConstants.KEY_SLASH;
    public static final int KEY_0 = InputConstants.KEY_0;
    public static final int KEY_1 = InputConstants.KEY_1;
    public static final int KEY_2 = InputConstants.KEY_2;
    public static final int KEY_3 = InputConstants.KEY_3;
    public static final int KEY_4 = InputConstants.KEY_4;
    public static final int KEY_5 = InputConstants.KEY_5;
    public static final int KEY_6 = InputConstants.KEY_6;
    public static final int KEY_7 = InputConstants.KEY_7;
    public static final int KEY_8 = InputConstants.KEY_8;
    public static final int KEY_9 = InputConstants.KEY_9;
    public static final int KEY_SEMICOLON = InputConstants.KEY_SEMICOLON;
    public static final int KEY_EQUAL = InputConstants.KEY_EQUALS;
    public static final int KEY_A = InputConstants.KEY_A;
    public static final int KEY_B = InputConstants.KEY_B;
    public static final int KEY_C = InputConstants.KEY_C;
    public static final int KEY_D = InputConstants.KEY_D;
    public static final int KEY_E = InputConstants.KEY_E;
    public static final int KEY_F = InputConstants.KEY_F;
    public static final int KEY_G = InputConstants.KEY_G;
    public static final int KEY_H = InputConstants.KEY_H;
    public static final int KEY_I = InputConstants.KEY_I;
    public static final int KEY_J = InputConstants.KEY_J;
    public static final int KEY_K = InputConstants.KEY_K;
    public static final int KEY_L = InputConstants.KEY_L;
    public static final int KEY_M = InputConstants.KEY_M;
    public static final int KEY_N = InputConstants.KEY_N;
    public static final int KEY_O = InputConstants.KEY_O;
    public static final int KEY_P = InputConstants.KEY_P;
    public static final int KEY_Q = InputConstants.KEY_Q;
    public static final int KEY_R = InputConstants.KEY_R;
    public static final int KEY_S = InputConstants.KEY_S;
    public static final int KEY_T = InputConstants.KEY_T;
    public static final int KEY_U = InputConstants.KEY_U;
    public static final int KEY_V = InputConstants.KEY_V;
    public static final int KEY_W = InputConstants.KEY_W;
    public static final int KEY_X = InputConstants.KEY_X;
    public static final int KEY_Y = InputConstants.KEY_Y;
    public static final int KEY_Z = InputConstants.KEY_Z;
    public static final int KEY_LEFT_BRACKET = InputConstants.KEY_LBRACKET;
    public static final int KEY_BACKSLASH = InputConstants.KEY_BACKSLASH;
    public static final int KEY_RIGHT_BRACKET = InputConstants.KEY_RBRACKET;
    public static final int KEY_GRAVE_ACCENT = InputConstants.KEY_GRAVE;
    public static final int KEY_WORLD_1 = 100;
    public static final int KEY_WORLD_2 = 50;

    public static final int KEY_ESCAPE = InputConstants.KEY_ESCAPE;
    public static final int KEY_ENTER = InputConstants.KEY_RETURN;
    public static final int KEY_TAB = InputConstants.KEY_TAB;
    public static final int KEY_BACKSPACE = InputConstants.KEY_BACKSPACE;
    public static final int KEY_INSERT = InputConstants.KEY_INSERT;
    public static final int KEY_DELETE = InputConstants.KEY_DELETE;
    public static final int KEY_RIGHT = InputConstants.KEY_RIGHT;
    public static final int KEY_LEFT = InputConstants.KEY_LEFT;
    public static final int KEY_DOWN = InputConstants.KEY_DOWN;
    public static final int KEY_UP = InputConstants.KEY_UP;
    public static final int KEY_PAGE_UP = InputConstants.KEY_PAGEUP;
    public static final int KEY_PAGE_DOWN = InputConstants.KEY_PAGEDOWN;
    public static final int KEY_HOME = InputConstants.KEY_HOME;
    public static final int KEY_END = InputConstants.KEY_END;
    public static final int KEY_CAPS_LOCK = InputConstants.KEY_CAPSLOCK;
    public static final int KEY_SCROLL_LOCK = InputConstants.KEY_SCROLLLOCK;
    public static final int KEY_NUM_LOCK = InputConstants.KEY_NUMLOCK;
    public static final int KEY_PRINT_SCREEN = InputConstants.KEY_PRINTSCREEN;
    public static final int KEY_PAUSE = InputConstants.KEY_PAUSE;
    public static final int KEY_F1 = InputConstants.KEY_F1;
    public static final int KEY_F2 = InputConstants.KEY_F2;
    public static final int KEY_F3 = InputConstants.KEY_F3;
    public static final int KEY_F4 = InputConstants.KEY_F4;
    public static final int KEY_F5 = InputConstants.KEY_F5;
    public static final int KEY_F6 = InputConstants.KEY_F6;
    public static final int KEY_F7 = InputConstants.KEY_F7;
    public static final int KEY_F8 = InputConstants.KEY_F8;
    public static final int KEY_F9 = InputConstants.KEY_F9;
    public static final int KEY_F10 = InputConstants.KEY_F10;
    public static final int KEY_F11 = InputConstants.KEY_F11;
    public static final int KEY_F12 = InputConstants.KEY_F12;
    public static final int KEY_F13 = InputConstants.KEY_F13;
    public static final int KEY_F14 = InputConstants.KEY_F14;
    public static final int KEY_F15 = InputConstants.KEY_F15;
    public static final int KEY_F16 = InputConstants.KEY_F16;
    public static final int KEY_F17 = InputConstants.KEY_F17;
    public static final int KEY_F18 = InputConstants.KEY_F18;
    public static final int KEY_F19 = InputConstants.KEY_F19;
    public static final int KEY_F20 = InputConstants.KEY_F20;
    public static final int KEY_F21 = InputConstants.KEY_F21;
    public static final int KEY_F22 = InputConstants.KEY_F22;
    public static final int KEY_F23 = InputConstants.KEY_F23;
    public static final int KEY_F24 = InputConstants.KEY_F24;
    public static final int KEY_KP_0 = InputConstants.KEY_NUMPAD0;
    public static final int KEY_KP_1 = InputConstants.KEY_NUMPAD1;
    public static final int KEY_KP_2 = InputConstants.KEY_NUMPAD2;
    public static final int KEY_KP_3 = InputConstants.KEY_NUMPAD3;
    public static final int KEY_KP_4 = InputConstants.KEY_NUMPAD4;
    public static final int KEY_KP_5 = InputConstants.KEY_NUMPAD5;
    public static final int KEY_KP_6 = InputConstants.KEY_NUMPAD6;
    public static final int KEY_KP_7 = InputConstants.KEY_NUMPAD7;
    public static final int KEY_KP_8 = InputConstants.KEY_NUMPAD8;
    public static final int KEY_KP_9 = InputConstants.KEY_NUMPAD9;
    public static final int KEY_KP_DECIMAL = 99;
    public static final int KEY_KP_DIVIDE = 84;
    public static final int KEY_KP_MULTIPLY = InputConstants.KEY_MULTIPLY;
    public static final int KEY_KP_SUBTRACT = 86;
    public static final int KEY_KP_ADD = InputConstants.KEY_ADD;
    public static final int KEY_KP_ENTER = InputConstants.KEY_NUMPADENTER;
    public static final int KEY_KP_EQUAL = InputConstants.KEY_NUMPADEQUALS;
    public static final int KEY_LEFT_SHIFT = InputConstants.KEY_LSHIFT;
    public static final int KEY_LEFT_CONTROL = InputConstants.KEY_LCONTROL;
    public static final int KEY_LEFT_ALT = InputConstants.KEY_LALT;
    public static final int KEY_LEFT_SUPER = InputConstants.KEY_LGUI;
    public static final int KEY_RIGHT_SHIFT = InputConstants.KEY_RSHIFT;
    public static final int KEY_RIGHT_CONTROL = InputConstants.KEY_RCONTROL;
    public static final int KEY_RIGHT_ALT = InputConstants.KEY_RALT;
    public static final int KEY_RIGHT_SUPER = InputConstants.KEY_RGUI;
    /** The context menu key; SDL reports it as {@code SDL_SCANCODE_APPLICATION}. */
    public static final int KEY_MENU = 101;

    public static final int MOUSE_BUTTON_LEFT = InputConstants.MOUSE_BUTTON_LEFT;
    public static final int MOUSE_BUTTON_MIDDLE = InputConstants.MOUSE_BUTTON_MIDDLE;
    public static final int MOUSE_BUTTON_RIGHT = InputConstants.MOUSE_BUTTON_RIGHT;

    public static final int RELEASE = InputConstants.RELEASE;
    public static final int PRESS = InputConstants.PRESS;
    public static final int REPEAT = InputConstants.REPEAT;

    public static final int MOD_SHIFT = InputConstants.MOD_SHIFT;
    public static final int MOD_CONTROL = InputConstants.MOD_CONTROL;
    public static final int MOD_ALT = InputConstants.MOD_ALT;
    public static final int MOD_SUPER = InputConstants.MOD_SUPER;

    private static final Int2IntOpenHashMap LEGACY_GLFW_TO_SDL = new Int2IntOpenHashMap();

    static {
        LEGACY_GLFW_TO_SDL.defaultReturnValue(KEY_UNKNOWN);
        legacy(32, KEY_SPACE);
        legacy(39, KEY_APOSTROPHE);
        legacy(44, KEY_COMMA);
        legacy(45, KEY_MINUS);
        legacy(46, KEY_PERIOD);
        legacy(47, KEY_SLASH);
        legacy(48, KEY_0);
        for (int i = 1; i <= 9; i++)
            legacy(48 + i, KEY_1 + i - 1);
        legacy(59, KEY_SEMICOLON);
        legacy(61, KEY_EQUAL);
        for (int i = 0; i < 26; i++)
            legacy(65 + i, KEY_A + i);
        legacy(91, KEY_LEFT_BRACKET);
        legacy(92, KEY_BACKSLASH);
        legacy(93, KEY_RIGHT_BRACKET);
        legacy(96, KEY_GRAVE_ACCENT);
        legacy(161, KEY_WORLD_1);
        legacy(162, KEY_WORLD_2);
        legacy(256, KEY_ESCAPE);
        legacy(257, KEY_ENTER);
        legacy(258, KEY_TAB);
        legacy(259, KEY_BACKSPACE);
        legacy(260, KEY_INSERT);
        legacy(261, KEY_DELETE);
        legacy(262, KEY_RIGHT);
        legacy(263, KEY_LEFT);
        legacy(264, KEY_DOWN);
        legacy(265, KEY_UP);
        legacy(266, KEY_PAGE_UP);
        legacy(267, KEY_PAGE_DOWN);
        legacy(268, KEY_HOME);
        legacy(269, KEY_END);
        legacy(280, KEY_CAPS_LOCK);
        legacy(281, KEY_SCROLL_LOCK);
        legacy(282, KEY_NUM_LOCK);
        legacy(283, KEY_PRINT_SCREEN);
        legacy(284, KEY_PAUSE);
        for (int i = 0; i < 12; i++)
            legacy(290 + i, KEY_F1 + i);
        for (int i = 0; i < 12; i++)
            legacy(302 + i, KEY_F13 + i);
        legacy(320, KEY_KP_0);
        for (int i = 1; i <= 9; i++)
            legacy(320 + i, KEY_KP_1 + i - 1);
        legacy(330, KEY_KP_DECIMAL);
        legacy(331, KEY_KP_DIVIDE);
        legacy(332, KEY_KP_MULTIPLY);
        legacy(333, KEY_KP_SUBTRACT);
        legacy(334, KEY_KP_ADD);
        legacy(335, KEY_KP_ENTER);
        legacy(336, KEY_KP_EQUAL);
        legacy(340, KEY_LEFT_SHIFT);
        legacy(341, KEY_LEFT_CONTROL);
        legacy(342, KEY_LEFT_ALT);
        legacy(343, KEY_LEFT_SUPER);
        legacy(344, KEY_RIGHT_SHIFT);
        legacy(345, KEY_RIGHT_CONTROL);
        legacy(346, KEY_RIGHT_ALT);
        legacy(347, KEY_RIGHT_SUPER);
        legacy(348, KEY_MENU);
    }

    private Keys() {
    }

    private static void legacy(int glfwKey, int sdlScancode) {
        LEGACY_GLFW_TO_SDL.put(glfwKey, sdlScancode);
    }

    /**
     * Converts a key code saved by a GLFW based (pre 26.3) client into the matching SDL scancode.
     * Keys without an SDL equivalent become {@link #KEY_UNKNOWN}.
     */
    public static int fromLegacyGlfw(int glfwKey) {
        return LEGACY_GLFW_TO_SDL.get(glfwKey);
    }

    /**
     * Returns the layout dependent, lower case name of a printable key, or {@code null} for keys that do not produce
     * a character. Behaves like the former {@code glfwGetKeyName} lookup.
     */
    public static String printableKeyName(int key) {
        String keypadName = switch (key) {
            case KEY_KP_0 -> "0";
            case KEY_KP_1 -> "1";
            case KEY_KP_2 -> "2";
            case KEY_KP_3 -> "3";
            case KEY_KP_4 -> "4";
            case KEY_KP_5 -> "5";
            case KEY_KP_6 -> "6";
            case KEY_KP_7 -> "7";
            case KEY_KP_8 -> "8";
            case KEY_KP_9 -> "9";
            case KEY_KP_DECIMAL -> ".";
            case KEY_KP_DIVIDE -> "/";
            case KEY_KP_MULTIPLY -> "*";
            case KEY_KP_SUBTRACT -> "-";
            case KEY_KP_ADD -> "+";
            case KEY_KP_EQUAL -> "=";
            default -> null;
        };
        if (keypadName != null)
            return keypadName;
        if (!isPrintableKey(key))
            return null;

        try {
            int keycode = SDLKeyboard.SDL_GetKeyFromScancode(key, (short) 0, false);
            String name = SDLKeyboard.SDL_GetKeyName(keycode);
            if (name == null || name.isEmpty() || name.codePointCount(0, name.length()) != 1)
                return null;
            return name.toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isPrintableKey(int key) {
        return key >= KEY_A && key <= KEY_0
                || key >= KEY_MINUS && key <= KEY_SLASH && key != KEY_SPACE
                || key == KEY_WORLD_1;
    }
}
