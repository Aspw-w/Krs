package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.level.CaveFinder;
import com.instrumentalist.krs.hacks.features.level.Xray;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.async.CullTask", remap = false)
public abstract class SodiumCullTaskMixin {

    @ModifyArg(
            method = "runTask()Lnet/caffeinemc/mods/sodium/client/render/chunk/async/CullResult;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/occlusion/OcclusionCuller;findVisible(Lnet/caffeinemc/mods/sodium/client/render/chunk/occlusion/OcclusionCuller$GraphOcclusionVisitor;Lnet/caffeinemc/mods/sodium/client/render/chunk/occlusion/OcclusionCuller$GraphOcclusionVisitor;Lnet/caffeinemc/mods/sodium/client/render/chunk/occlusion/OcclusionCuller$VisibilityTestingVisitor;Lnet/caffeinemc/mods/sodium/client/render/viewport/Viewport;FFZLnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)V"
            ),
            index = 6,
            require = 0
    )
    private boolean krs$disableChunkOcclusion(boolean original) {
        if (ModuleManager.getModuleState(Xray.class)
                || CaveFinder.isActive()) {
            return false;
        }

        return original;
    }
}
