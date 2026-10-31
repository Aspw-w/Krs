package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.render.NoRender;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AtmosphericFogEnvironment.class)
public class AtmosphericFogEnvironmentMixin {

    @Inject(method = "setupFog", at = @At("RETURN"))
    private void noRenderFogHook(FogData fog, Camera camera, ClientLevel level, float renderDistance, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!NoRender.active(NoRender.fog)) return;

        fog.environmentalStart = 1.0E6F;
        fog.environmentalEnd = 1.0E6F + 1.0F;
    }
}
