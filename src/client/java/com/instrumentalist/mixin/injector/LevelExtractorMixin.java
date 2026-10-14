package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.render.AntiBlind;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {

    // Since 26.3 the portal effect intensity is interpolated while the player state is extracted.
    @WrapOperation(method = "extractPlayerState", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;lerp(FFF)F", ordinal = 0))
    private float antiBlindHook(float delta, float start, float end, Operation<Float> original) {
        if (ModuleManager.getModuleState(AntiBlind.class) && AntiBlind.effects.get())
            return 0;

        return original.call(delta, start, end);
    }
}
