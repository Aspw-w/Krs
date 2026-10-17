package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.utils.render.GraphicsApiCompatibility;
import com.mojang.renderpearl.frontend.FrontendCommandEncoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FrontendCommandEncoder.class)
public abstract class FrontendCommandEncoderMixin {

    // Synchronous compatibility layers record their uploads early and fill the staging data here.
    @Inject(method = "submit", at = @At("HEAD"))
    private void krs$completeCompatibilityReadbacks(CallbackInfo ci) {
        GraphicsApiCompatibility.completeDeferredReadbacks();
    }
}
