package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.utils.render.custommodel.CustomModelRenderer;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(WingsLayer.class)
public abstract class WingsLayerMixin {

    @WrapMethod(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V")
    private void krs$followCustomModelBody(PoseStack poseStack, SubmitNodeCollector submitter, int light, HumanoidRenderState state, float yRot, float xRot, Operation<Void> original) {
        boolean pushed = CustomModelRenderer.pushBodyFrame(state, poseStack);
        try {
            original.call(poseStack, submitter, light, state, yRot, xRot);
        } finally {
            if (pushed)
                poseStack.popPose();
        }
    }
}
