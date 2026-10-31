package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.render.NoRender;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {

    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void noRenderParticleHook(Particle particle, CallbackInfo ci) {
        if (NoRender.shouldHideParticle(particle))
            ci.cancel();
    }
}
