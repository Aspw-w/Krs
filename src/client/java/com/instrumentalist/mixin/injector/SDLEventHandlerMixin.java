package com.instrumentalist.mixin.injector;

import com.mojang.blaze3d.platform.SDLEventHandler;
import org.lwjgl.sdl.SDL_Event;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.breadloaf.imguimc.imgui.ImguiLoader;

@Mixin(SDLEventHandler.class)
public abstract class SDLEventHandlerMixin {

    @Inject(method = {"handleKeyEvent", "handleTextInputEvent", "handleMouseButtonEvent", "handleMouseWheelEvent"}, at = @At("HEAD"))
    private void forwardInputToImgui(SDL_Event event, CallbackInfo ci) {
        ImguiLoader.onSdlEvent(event);
    }
}
