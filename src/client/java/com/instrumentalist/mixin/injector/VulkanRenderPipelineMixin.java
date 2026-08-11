package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.level.CaveFinder;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.lwjgl.vulkan.VK10;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "com.mojang.blaze3d.vulkan.VulkanRenderPipeline")
public abstract class VulkanRenderPipelineMixin {

    @ModifyArg(
            method = "compile",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/vulkan/VkPipelineRasterizationStateCreateInfo;cullMode(I)Lorg/lwjgl/vulkan/VkPipelineRasterizationStateCreateInfo;"
            ),
            index = 0
    )
    private static int krs$caveFinderCullMode(int original, @Local(argsOnly = true) RenderPipeline pipeline) {
        return CaveFinder.shouldCullFront(pipeline) ? VK10.VK_CULL_MODE_FRONT_BIT : original;
    }
}
