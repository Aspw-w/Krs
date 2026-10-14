package com.instrumentalist.mixin.injector;

import com.mojang.blaze3d.pipeline.PipelineCache;
import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderSystem.class)
public interface RenderSystemAccessor {

    @Accessor("currentPipelineCache")
    static PipelineCache krs$getCurrentPipelineCache() {
        throw new AssertionError();
    }

    @Accessor("fallbackPipelineCache")
    static PipelineCache krs$getFallbackPipelineCache() {
        throw new AssertionError();
    }
}
