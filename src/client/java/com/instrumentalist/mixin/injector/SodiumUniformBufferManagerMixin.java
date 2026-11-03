package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.render.LegacyRenderer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.UniformBufferManager", remap = false)
public abstract class SodiumUniformBufferManagerMixin {

    @WrapOperation(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"), require = 0)
    private Object legacyRendererChunkFadeHook(OptionInstance<?> instance, Operation<Object> original) {
        return LegacyRenderer.chunkSectionFadeInTime(instance, original.call(instance));
    }
}
