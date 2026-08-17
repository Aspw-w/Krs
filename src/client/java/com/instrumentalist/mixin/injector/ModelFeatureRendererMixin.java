package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.render.ESP;
import com.instrumentalist.krs.utils.render.OldHittingRenderGuard;
import com.instrumentalist.mixin.oringo.IEntityRenderState;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ModelFeatureRenderer.class, priority = 2000)
public abstract class ModelFeatureRendererMixin {

    @Shadow
    @Final
    private PoseStack poseStack;

    @WrapMethod(method = "prepareModel")
    private <S> void krs$exposeRenderedEntity(ModelFeatureRenderer.Submit<S> modelSubmit, Operation<Void> original) {
        Entity renderedEntity = null;
        if (modelSubmit.state() instanceof EntityRenderState state)
            renderedEntity = ((IEntityRenderState) state).client$getEntity();

        Entity previous = OldHittingRenderGuard.enter(renderedEntity);
        try {
            original.call(modelSubmit);
        } finally {
            OldHittingRenderGuard.exit(previous);
        }
    }

    @Inject(
            method = "prepareModel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/Model;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
                    ordinal = 0
            )
    )
    private <S> void krs$captureShaderEspModel(ModelFeatureRenderer.Submit<S> modelSubmit, CallbackInfo ci) {
        ESP.captureModel(modelSubmit, poseStack, modelSubmit.renderType());
    }
}
