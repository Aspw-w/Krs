package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.level.CaveFinder;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer", remap = false)
public abstract class SodiumDefaultChunkRendererMixin {
    private static final int ALL_MODEL_FACES = 0x7F;

    @ModifyReturnValue(method = "getVisibleFaces(IIIIII)I", at = @At("RETURN"), require = 0)
    private static int krs$caveFinderDrawAllFaces(int original) {
        return CaveFinder.isActive() ? ALL_MODEL_FACES : original;
    }
}
