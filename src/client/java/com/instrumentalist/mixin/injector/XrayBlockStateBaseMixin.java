package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.EventManager;
import com.instrumentalist.krs.events.guards.BlockEventCollisionGuard;
import com.instrumentalist.krs.events.features.BlockEvent;
import com.instrumentalist.krs.hacks.features.level.Xray;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class XrayBlockStateBaseMixin {
    @Shadow
    protected abstract BlockState asState();

    @ModifyReturnValue(
            method = "getShadeBrightness(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F",
            at = @At("RETURN")
    )
    private float krs$xrayShadeBrightness(float original) {
        return Xray.isActive() ? 1.0F : original;
    }

    @ModifyReturnValue(
            method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("RETURN")
    )
    private VoxelShape krs$blockEventCollisionShape(VoxelShape original, BlockGetter blockGetter, BlockPos blockPos, CollisionContext context) {
        EventManager eventManager = Client.eventManager;
        if (eventManager == null
                || !eventManager.hasListeners(BlockEvent.class)
                || BlockEventCollisionGuard.isBypassingBlockEvent())
            return original;

        BlockEvent event = new BlockEvent(asState(), blockPos.immutable(), original);
        eventManager.call(event);

        if (event.isCancelled() || event.voxelShape == null)
            return Shapes.empty();

        return event.voxelShape;
    }
}
