package com.instrumentalist.krs.utils;

import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.screen.NanoVGClickGuiScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.ChatScreen;
import com.instrumentalist.krs.utils.input.Keys;
import xyz.breadloaf.imguimc.imgui.ImguiLoader;

public final class GuiInputBlocker implements IMinecraft {
    private static boolean closingDebugScreen;

    private GuiInputBlocker() {
    }

    public static boolean shouldBlockMinecraftMouse() {
        return ModuleManager.isDebugRendering && (mc.gui.screen() == null || mc.gui.screen() instanceof ChatScreen || ImguiLoader.wantsCaptureMouse());
    }

    public static boolean shouldAllowWorldHudOverlays() {
        return ModuleManager.isDebugRendering;
    }

    public static boolean shouldPreserveCursorOnMouseGrab() {
        return ModuleManager.isDebugRendering && !closingDebugScreen;
    }

    public static boolean shouldPreserveCursorOnMouseRelease() {
        return ModuleManager.isDebugRendering && mc.gui.screen() != null;
    }

    public static void setClosingDebugScreen(boolean closing) {
        closingDebugScreen = closing;
    }

    public static boolean shouldBlockMinecraftKeyboard() {
        return ModuleManager.isDebugRendering && ImguiLoader.wantsTextInput();
    }

    public static boolean shouldBlockGameMovement() {
        return shouldBlockMinecraftKeyboard()
                || mc.gui.screen() instanceof NanoVGClickGuiScreen screen && screen.shouldBlockGameMovement();
    }

    public static boolean shouldSyncNanoVGClickGuiMovementKeys() {
        return mc.gui.screen() instanceof NanoVGClickGuiScreen screen && screen.shouldSyncGameMovementKeys();
    }

    public static boolean shouldBlockMinecraftKeyEvent(int keyCode) {
        return shouldBlockMinecraftKeyboard() && keyCode != Keys.KEY_ESCAPE;
    }

    public static int sanitizeMouseCoordinate(int coordinate) {
        return shouldBlockMinecraftMouse() ? -1_000_000 : coordinate;
    }

    public static boolean shouldBlockMovementKey(int keyCode) {
        return shouldBlockGameMovement() && isMovementKey(keyCode);
    }

    public static void releaseMovementKeys() {
        Options options = mc.options;
        if (options == null) return;

        release(options.keyUp);
        release(options.keyDown);
        release(options.keyLeft);
        release(options.keyRight);
        release(options.keyJump);
        release(options.keyShift);
        release(options.keySprint);
    }

    private static boolean isMovementKey(int keyCode) {
        Options options = mc.options;
        return options != null
                && (matches(options.keyUp, keyCode)
                || matches(options.keyDown, keyCode)
                || matches(options.keyLeft, keyCode)
                || matches(options.keyRight, keyCode)
                || matches(options.keyJump, keyCode)
                || matches(options.keyShift, keyCode)
                || matches(options.keySprint, keyCode));
    }

    private static boolean matches(KeyMapping keyMapping, int keyCode) {
        return keyMapping != null && keyMapping.key.getValue() == keyCode;
    }

    private static void release(KeyMapping keyMapping) {
        if (keyMapping != null)
            keyMapping.setDown(false);
    }
}
