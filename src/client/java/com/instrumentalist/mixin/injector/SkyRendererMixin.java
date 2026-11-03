package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.render.LegacyRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void legacyRendererSkyHook(ClientLevel level, float partialTicks, Camera camera, SkyRenderState state, CallbackInfo ci) {
        if (LegacyRenderer.isSkyActive())
            LegacyRenderer.applySky(state, level, camera, partialTicks);
    }
}
