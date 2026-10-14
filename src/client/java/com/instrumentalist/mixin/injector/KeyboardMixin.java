package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.KeyboardEvent;
import com.instrumentalist.krs.hacks.features.render.ImGui;
import com.instrumentalist.krs.hacks.features.render.ClickGui;
import com.instrumentalist.krs.utils.GuiInputBlocker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.breadloaf.imguimc.imgui.ImguiLoader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.PreeditEvent;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    public void setup(Minecraft minecraft, CallbackInfo ci) {
        ImguiLoader.onWindowInit(minecraft.getWindow().handle());
    }

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    public void onKey(long window, int action, KeyEvent keyEvent, CallbackInfo ci) {
        if ((keyEvent.key() != ImGui.getOpenGuiKey() || keyEvent.key() != ClickGui.getOpenGuiKey()) && GuiInputBlocker.shouldBlockMinecraftKeyEvent(keyEvent.key())) {
            GuiInputBlocker.releaseMovementKeys();
            ci.cancel();
            return;
        }

        if (Client.eventManager != null && keyEvent.key() > 0 && Client.eventManager.hasListeners(KeyboardEvent.class)) {
            final KeyboardEvent event = new KeyboardEvent(keyEvent.key(), action);
            Client.eventManager.call(event);
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    public void onCharTyped(long window, CharacterEvent characterEvent, CallbackInfo ci) {
        if (GuiInputBlocker.shouldBlockMinecraftKeyboard()) {
            ci.cancel();
        }
    }

    @Inject(method = "textEditing", at = @At("HEAD"), cancellable = true)
    public void onPreedit(long window, PreeditEvent preeditEvent, CallbackInfo ci) {
        if (GuiInputBlocker.shouldBlockMinecraftKeyboard()) {
            ci.cancel();
        }
    }
}
