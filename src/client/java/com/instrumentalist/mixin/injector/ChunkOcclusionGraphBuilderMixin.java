package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.level.CaveFinder;
import com.instrumentalist.krs.hacks.features.level.Xray;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VisGraph.class)
public abstract class ChunkOcclusionGraphBuilderMixin {

    @Inject(at = @At("HEAD"), method = "setOpaque(Lnet/minecraft/core/BlockPos;)V", cancellable = true)
    private void chunkOcclusionHook(BlockPos pos, CallbackInfo ci) {
        if (ModuleManager.getModuleState(Xray.class)
                || CaveFinder.isActive())
            ci.cancel();
    }
}
