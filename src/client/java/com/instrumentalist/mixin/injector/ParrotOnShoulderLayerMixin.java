package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.utils.render.custommodel.CustomModelRenderer;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ParrotOnShoulderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ParrotOnShoulderLayer.class)
public abstract class ParrotOnShoulderLayerMixin {

    @WrapMethod(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V")
    private void krs$followCustomModelBody(PoseStack poseStack, SubmitNodeCollector submitter, int light, AvatarRenderState state, float yRot, float xRot, Operation<Void> original) {
        boolean pushed = CustomModelRenderer.pushBodyFrame(state, poseStack);
        try {
            original.call(poseStack, submitter, light, state, yRot, xRot);
        } finally {
            if (pushed)
                poseStack.popPose();
        }
    }
}
