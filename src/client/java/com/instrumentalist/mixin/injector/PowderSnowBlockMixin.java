package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.level.NoBlockEffects;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.InsideBlockEffectType;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PowderSnowBlock.class)
public abstract class PowderSnowBlockMixin {

    @WrapOperation(method = "entityInside", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;makeStuckInBlock(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/phys/Vec3;)V"))
    private void noBlockEffectsStuckHook(Entity entity, BlockState state, Vec3 speedMultiplier, Operation<Void> original) {
        if (!NoBlockEffects.shouldIgnore(entity))
            original.call(entity, state, speedMultiplier);
    }

    @WrapOperation(method = "entityInside", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/InsideBlockEffectApplier;apply(Lnet/minecraft/world/entity/InsideBlockEffectType;)V"))
    private void noBlockEffectsFreezeHook(InsideBlockEffectApplier applier, InsideBlockEffectType type, Operation<Void> original, @Local(argsOnly = true) Entity entity) {
        if (type != InsideBlockEffectType.FREEZE || !NoBlockEffects.shouldIgnore(entity))
            original.call(applier, type);
    }

    @ModifyReturnValue(method = "canEntityWalkOnPowderSnow", at = @At("RETURN"))
    private static boolean noBlockEffectsWalkHook(boolean original, @Local(argsOnly = true) Entity entity) {
        return original || NoBlockEffects.shouldIgnore(entity);
    }
}
