package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.level.NoBlockEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.HoneyBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HoneyBlock.class)
public abstract class HoneyBlockMixin {

    @Inject(method = "isSlidingDown", at = @At("HEAD"), cancellable = true)
    private void noBlockEffectsSlideHook(BlockPos pos, Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (NoBlockEffects.shouldIgnore(entity))
            cir.setReturnValue(false);
    }
}
