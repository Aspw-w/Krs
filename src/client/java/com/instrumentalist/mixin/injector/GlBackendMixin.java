package com.instrumentalist.mixin.injector;

import com.mojang.renderpearl.backend.opengl.GlBackend;
import org.lwjgl.sdl.SDLVideo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GlBackend.class)
public abstract class GlBackendMixin {

    @Inject(method = "createWindow", at = @At("HEAD"))
    private void krs$requestStencilBuffer(String title, int width, int height, long flags, CallbackInfoReturnable<Long> cir) {
        SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_STENCIL_SIZE, 8);
    }
}
