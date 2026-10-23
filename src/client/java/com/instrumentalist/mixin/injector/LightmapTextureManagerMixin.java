package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.render.AntiBlind;
import com.instrumentalist.krs.hacks.features.render.FullBright;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapTextureManagerMixin {

    @Inject(at = @At("RETURN"), method = "extract")
    private void fullBrightAndAntiBlindHook(LightmapRenderState state, float delta, CallbackInfo ci) {
        if (FullBright.isBrightening()) {
            state.needsUpdate = true;
            state.blockFactor = 1.0F;
            state.blockLightTint = LightmapRenderStateExtractor.WHITE;
            state.skyFactor = 1.0F;
            state.skyLightColor = LightmapRenderStateExtractor.WHITE;
            state.ambientColor = LightmapRenderStateExtractor.WHITE;
            state.brightness = 1.0F;
            state.darknessEffectScale = 0.0F;
            state.nightVisionEffectIntensity = 1.0F;
            state.nightVisionColor = LightmapRenderStateExtractor.WHITE;
            state.bossOverlayWorldDarkening = 0.0F;
        }
        if (ModuleManager.getModuleState(AntiBlind.class) && AntiBlind.effects.get()) {
            state.needsUpdate = true;
            state.darknessEffectScale = 0f;
        }
    }
}
