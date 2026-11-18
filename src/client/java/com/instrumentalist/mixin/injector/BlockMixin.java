package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.world.Xray;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Block.class)
public abstract class BlockMixin {

    @ModifyReturnValue(method = "shouldRenderFace", at = @At("RETURN"))
    private static boolean xRayHook(boolean original, BlockState state, BlockState otherState, Direction side) {
        return Xray.hookTransparentOre(state, original);
    }
}
