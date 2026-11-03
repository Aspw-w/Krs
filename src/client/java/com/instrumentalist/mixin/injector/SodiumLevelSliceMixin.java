package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.render.LegacyRenderer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.world.LevelSlice", remap = false)
public abstract class SodiumLevelSliceMixin {

    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"), require = 0)
    private Object legacyRendererBiomeBlendHook(OptionInstance<?> instance, Operation<Object> original) {
        return LegacyRenderer.biomeBlendRadius(instance, original.call(instance));
    }
}
