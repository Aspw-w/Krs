package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.render.ESP;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(SubmitNodeStorage.class)
public abstract class SubmitNodeStorageMixin {

    @Inject(method = "submitModel", at = @At("HEAD"))
    private <S> void krs$captureShaderEspSubmittedModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, int lightCoords, int overlayCoords, int color, UvMapping uvMapping, int outlineColor, CallbackInfo ci) {
        ESP.captureSubmittedModel(model, state, poseStack, renderType, lightCoords, overlayCoords);
    }

    @Inject(method = "submitItem", at = @At("HEAD"))
    private void krs$captureShaderEspItem(PoseStack poseStack, ItemDisplayContext displayContext, int light, int overlay, int color, int[] tintLayers, ItemQuads quads, ItemStackRenderState.FoilType foilType, CallbackInfo ci) {
        ESP.captureItem(poseStack, quads.all());
    }
}
