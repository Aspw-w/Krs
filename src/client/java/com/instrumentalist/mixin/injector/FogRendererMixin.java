package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.render.LegacyRenderer;
import com.instrumentalist.krs.hacks.features.render.NoRender;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FogRenderer.class, priority = 500)
public abstract class FogRendererMixin {

    @Inject(method = "setupFog", at = @At("RETURN"))
    private void legacyRendererFogHook(Camera camera, int renderDistanceInChunks, DeltaTracker deltaTracker, float darkenWorldAmount, ClientLevel level, CallbackInfoReturnable<FogData> cir) {
        if (!LegacyRenderer.isFogActive() || NoRender.active(NoRender.fog)) return;

        LegacyRenderer.applyFog(cir.getReturnValue(), camera, level, renderDistanceInChunks, deltaTracker.getGameTimeDeltaPartialTick(false));
    }
}
