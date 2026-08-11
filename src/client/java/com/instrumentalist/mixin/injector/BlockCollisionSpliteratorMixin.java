package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.guards.BlockEventCollisionGuard;
import com.instrumentalist.krs.events.features.BlockEvent;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockCollisions.class)
public abstract class BlockCollisionSpliteratorMixin {

    @Shadow
    @Final
    private BlockPos.MutableBlockPos pos;

    @Shadow
    @Final
    private CollisionContext context;

    @Shadow
    @Final
    private CollisionGetter collisionGetter;

    @Unique
    private BlockPos krs$cachedBlockEventPos;
    @Unique
    private BlockState krs$cachedBlockEventState;
    @Unique
    private boolean krs$cachedBlockEventShape;

    @ModifyExpressionValue(method = "computeNext", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;isSuffocating(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean keepBlockEventShapeForSuffocationCheck(boolean original, @Local BlockState blockState) {
        return original || hasBlockEventShape(blockState);
    }

    @ModifyExpressionValue(method = "computeNext", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;hasLargeCollisionShape()Z"))
    private boolean keepBlockEventShapeForLargeShapeCheck(boolean original, @Local BlockState blockState) {
        return original || hasBlockEventShape(blockState);
    }

    @ModifyExpressionValue(method = "computeNext", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Ljava/lang/Object;)Z"))
    private boolean keepBlockEventShapeForMovingPistonCheck(boolean original, @Local BlockState blockState) {
        return original || hasBlockEventShape(blockState);
    }

    private boolean hasBlockEventShape(BlockState blockState) {
        if (this.pos == null || Client.eventManager == null || !Client.eventManager.hasListeners(BlockEvent.class))
            return false;

        if (blockState == krs$cachedBlockEventState && krs$cachedBlockEventPos != null && krs$cachedBlockEventPos.equals(this.pos))
            return krs$cachedBlockEventShape;

        VoxelShape originalShape = BlockEventCollisionGuard.getOriginalCollisionShape(blockState, this.collisionGetter, this.pos, this.context);
        BlockEvent event = new BlockEvent(blockState, this.pos.immutable(), originalShape);
        Client.eventManager.call(event);

        krs$cachedBlockEventState = blockState;
        krs$cachedBlockEventPos = this.pos.immutable();
        krs$cachedBlockEventShape = !event.isCancelled()
                && event.voxelShape != null
                && event.voxelShape != originalShape
                && !event.voxelShape.isEmpty();
        return krs$cachedBlockEventShape;
    }
}
