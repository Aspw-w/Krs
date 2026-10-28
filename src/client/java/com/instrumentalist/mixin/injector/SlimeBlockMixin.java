package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.level.NoBlockEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SlimeBlock.class)
public abstract class SlimeBlockMixin {

    @Inject(method = "stepOn", at = @At("HEAD"), cancellable = true)
    private void noBlockEffectsStepOnHook(Level level, BlockPos pos, BlockState onState, Entity entity, CallbackInfo ci) {
        if (NoBlockEffects.shouldIgnore(entity))
            ci.cancel();
    }
}
