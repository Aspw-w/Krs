package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.level.CaveFinder;
import com.mojang.renderpearl.backend.api.BackendRenderPipeline;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Since 26.3 OpenGL pipeline state is applied by the compiled pipeline itself when it is bound.
 */
@Mixin(targets = "com.mojang.renderpearl.backend.opengl.GlRenderPipeline")
public abstract class GlRenderPipelineMixin {

    @Unique
    private String krs$location;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void krs$rememberLocation(@Coerce Object device, BackendRenderPipeline.CreateInfo createInfo, @Coerce Object program, @Coerce Object vertexArray, CallbackInfo ci) {
        this.krs$location = createInfo.name();
    }

    @Inject(method = "bind", at = @At("RETURN"))
    private void krs$caveFinderCullFace(CallbackInfo ci) {
        GL11.glCullFace(CaveFinder.shouldCullFront(this.krs$location) ? GL11.GL_FRONT : GL11.GL_BACK);
    }
}
