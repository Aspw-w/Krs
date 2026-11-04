package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.render.LegacyRenderer;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Biome.class)
public abstract class BiomeMixin {

    @ModifyReturnValue(method = "getWaterColor", at = @At("RETURN"))
    private int legacyRendererWaterColorHook(int original) {
        return LegacyRenderer.waterColor((Biome) (Object) this, original);
    }
}
