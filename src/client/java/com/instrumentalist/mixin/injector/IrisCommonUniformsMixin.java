package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.render.AntiBlind;
import com.instrumentalist.krs.hacks.features.render.FullBright;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.uniforms.CommonUniforms", remap = false)
public abstract class IrisCommonUniformsMixin {

    @Inject(method = "getNightVision()F", at = @At("HEAD"), cancellable = true, require = 0)
    private static void krs$fullBrightNightVision(CallbackInfoReturnable<Float> cir) {
        if (FullBright.isBrightening())
            cir.setReturnValue(1.0f);
    }

    @Inject(method = "getDarknessFactor()F", at = @At("HEAD"), cancellable = true, require = 0)
    private static void krs$fullBrightDarkness(CallbackInfoReturnable<Float> cir) {
        if (FullBright.isBrightening() || ModuleManager.getModuleState(AntiBlind.class) && AntiBlind.effects.get())
            cir.setReturnValue(0.0f);
    }
}
