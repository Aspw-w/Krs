package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.guards.BlockEventCollisionGuard;
import com.instrumentalist.krs.events.features.BlockEvent;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.level.Xray;
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
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class XrayBlockStateBaseMixin {
    @Shadow
    protected abstract BlockState asState();

    @Inject(
            method = "getShadeBrightness(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F",
            at = @At("RETURN"),
            cancellable = true,
            order = 980
    )
    private void krs$xrayShadeBrightness(BlockGetter blockGetter, BlockPos blockPos, CallbackInfoReturnable<Float> cir) {
        if (ModuleManager.getModuleState(Xray.class)) {
            cir.setReturnValue(1.0F);
        }
    }

    @Inject(
            method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("RETURN"),
            cancellable = true,
            order = 980
    )
    private void krs$blockEventCollisionShape(BlockGetter blockGetter, BlockPos blockPos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (Client.eventManager == null
                || BlockEventCollisionGuard.isBypassingBlockEvent()
                || !Client.eventManager.hasListeners(BlockEvent.class))
            return;

        BlockEvent event = new BlockEvent(asState(), blockPos.immutable(), cir.getReturnValue());
        Client.eventManager.call(event);

        if (event.isCancelled() || event.voxelShape == null)
            cir.setReturnValue(Shapes.empty());
        else
            cir.setReturnValue(event.voxelShape);
    }
}
