package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.MouseClickEvent;
import com.instrumentalist.krs.events.features.MouseScrollEvent;
import com.instrumentalist.krs.hacks.features.player.ChestStealer;
import com.instrumentalist.krs.utils.GuiInputBlocker;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.MouseHandler;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private double xpos;
    @Shadow private double ypos;
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;
    @Shadow private boolean ignoreFirstMove;

    @Unique
    private boolean krs$preserveCursorPosition;

    @Unique
    private double krs$preservedCursorX;

    @Unique
    private double krs$preservedCursorY;

    @Unique
    private boolean krs$chestStealerMouseLocked;

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void mouseScrollEvent(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (GuiInputBlocker.shouldBlockMinecraftMouse()) {
            ci.cancel();
            return;
        }

        if (Client.eventManager == null || !Client.eventManager.hasListeners(MouseScrollEvent.class))
            return;

        MouseScrollEvent event = new MouseScrollEvent(horizontal, vertical);
        Client.eventManager.call(event);

        if (event.isCancelled())
            ci.cancel();
    }

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void mouseClickEvent(long window, MouseButtonInfo button, int action, CallbackInfo ci) {
        if (GuiInputBlocker.shouldBlockMinecraftMouse()) {
            ci.cancel();
            return;
        }

        if (Client.eventManager == null || !Client.eventManager.hasListeners(MouseClickEvent.class))
            return;

        MouseClickEvent event = new MouseClickEvent(window, button.button(), action, button.modifiers());
        Client.eventManager.call(event);

        if (event.isCancelled())
            ci.cancel();
    }

    @Redirect(method = "handleAccumulatedMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;mouseMoved(DD)V"))
    private void blockMouseMovedWhenImguiCaptures(Screen screen, double mouseX, double mouseY) {
        if (!GuiInputBlocker.shouldBlockMinecraftMouse())
            screen.mouseMoved(mouseX, mouseY);
    }

    @Redirect(method = "handleAccumulatedMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;mouseDragged(Lnet/minecraft/client/input/MouseButtonEvent;DD)Z"))
    private boolean blockMouseDraggedWhenImguiCaptures(Screen screen, MouseButtonEvent event, double deltaX, double deltaY) {
        return !GuiInputBlocker.shouldBlockMinecraftMouse() && screen.mouseDragged(event, deltaX, deltaY);
    }

    @Redirect(method = "handleAccumulatedMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;afterMouseMove()V"))
    private void blockAfterMouseMoveWhenImguiCaptures(Screen screen) {
        if (!GuiInputBlocker.shouldBlockMinecraftMouse())
            screen.afterMouseMove();
    }

    @Inject(method = "handleAccumulatedMovement", at = @At("HEAD"))
    private void syncChestStealerMouseLock(CallbackInfo ci) {
        boolean shouldLock = ChestStealer.shouldMoveCamera();
        if (shouldLock == krs$chestStealerMouseLocked)
            return;

        krs$chestStealerMouseLocked = shouldLock;
        MouseHandler mouseHandler = (MouseHandler) (Object) this;

        if (!shouldLock && minecraft.gui.screen() == null) {
            if (!mouseHandler.isMouseGrabbed())
                mouseHandler.grabMouse();
            return;
        }

        Window window = minecraft.getWindow();
        double centerX = window.getScreenWidth() / 2.0;
        double centerY = window.getScreenHeight() / 2.0;
        this.xpos = centerX;
        this.ypos = centerY;
        this.accumulatedDX = 0.0;
        this.accumulatedDY = 0.0;
        this.ignoreFirstMove = true;
        if (shouldLock)
            InputConstants.grabMouse(window, centerX, centerY);
        else
            InputConstants.releaseMouse(window, centerX, centerY);
    }

    @Redirect(method = "onMove", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MouseHandler;mouseGrabbed:Z", opcode = Opcodes.GETFIELD))
    private boolean useRelativeMotionWhileChestStealerLocksMouse(MouseHandler mouseHandler) {
        return mouseHandler.isMouseGrabbed() || krs$chestStealerMouseLocked;
    }

    @Redirect(method = "handleAccumulatedMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;isMouseGrabbed()Z"))
    private boolean allowChestStealerCameraMovement(MouseHandler mouseHandler) {
        return mouseHandler.isMouseGrabbed() || ChestStealer.shouldMoveCamera();
    }

    @Inject(method = "grabMouse", at = @At("HEAD"))
    private void preserveCursorBeforeGrab(CallbackInfo ci) {
        captureCursorPosition(GuiInputBlocker.shouldPreserveCursorOnMouseGrab());
    }

    @Inject(method = "releaseMouse", at = @At("HEAD"))
    private void preserveCursorBeforeRelease(CallbackInfo ci) {
        captureCursorPosition(GuiInputBlocker.shouldPreserveCursorOnMouseRelease());
    }

    @Redirect(method = "grabMouse", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/InputConstants;grabMouse(Lcom/mojang/blaze3d/platform/Window;DD)V"))
    private void keepCursorPositionDuringImguiGrab(Window window, double x, double y) {
        if (!krs$preserveCursorPosition) {
            InputConstants.grabMouse(window, x, y);
            return;
        }

        InputConstants.grabMouse(window, krs$preservedCursorX, krs$preservedCursorY);
        this.xpos = krs$preservedCursorX;
        this.ypos = krs$preservedCursorY;
    }

    @Redirect(method = "releaseMouse", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/InputConstants;releaseMouse(Lcom/mojang/blaze3d/platform/Window;DD)V"))
    private void keepCursorPositionDuringImguiRelease(Window window, double x, double y) {
        if (!krs$preserveCursorPosition) {
            InputConstants.releaseMouse(window, x, y);
            return;
        }

        InputConstants.releaseMouse(window, krs$preservedCursorX, krs$preservedCursorY);
        this.xpos = krs$preservedCursorX;
        this.ypos = krs$preservedCursorY;
    }

    @Inject(method = {"grabMouse", "releaseMouse"}, at = @At("RETURN"))
    private void clearPreservedCursorPosition(CallbackInfo ci) {
        krs$preserveCursorPosition = false;
    }

    @Unique
    private void captureCursorPosition(boolean shouldPreserve) {
        krs$preserveCursorPosition = shouldPreserve;

        if (!shouldPreserve)
            return;

        krs$preservedCursorX = this.xpos;
        krs$preservedCursorY = this.ypos;
    }
}
