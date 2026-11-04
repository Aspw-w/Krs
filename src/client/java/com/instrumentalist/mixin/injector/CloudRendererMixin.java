package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.render.LegacyRenderer;
import com.instrumentalist.krs.utils.render.LegacyAtmosphere;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(CloudRenderer.class)
public abstract class CloudRendererMixin {

    @ModifyArg(method = {"render(Lnet/minecraft/client/CloudStatus;Lcom/mojang/renderpearl/api/commands/RenderPass;)V", "renderOit"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/CloudRenderer;render(Lcom/mojang/renderpearl/api/commands/RenderPass;Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;)V"), index = 1)
    private RenderPipeline legacyRendererCloudFogHook(RenderPipeline pipeline) {
        if (!LegacyRenderer.isCloudsActive() || !LegacyRenderer.isFogActive())
            return pipeline;

        return LegacyAtmosphere.cloudPipeline(pipeline);
    }
}
