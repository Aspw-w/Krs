package com.instrumentalist.mixin.injector;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.instrumentalist.krs.hacks.features.render.OldHitting;
import com.instrumentalist.krs.hacks.features.render.ESP;
import com.instrumentalist.krs.utils.math.ToolUtil;
import com.instrumentalist.krs.utils.render.GuiEntityRenderGuard;
import com.instrumentalist.mixin.oringo.IEntityRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {

    @Unique
    private boolean krs$legacyVanillaSwordTransform;

    @Unique
    private int krs$legacyVanillaArmDirection;

    @Inject(
            method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;FF)V",
            at = @At("HEAD")
    )
    private void krs$beginShaderEspItemCapture(PoseStack poseStack, SubmitNodeCollector submitter, int light, ArmedEntityRenderState state, float limbSwing, float limbSwingAmount, CallbackInfo ci) {
        ESP.beginItemCapture(state);
    }

    @Inject(
            method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;FF)V",
            at = @At("RETURN")
    )
    private void krs$endShaderEspItemCapture(PoseStack poseStack, SubmitNodeCollector submitter, int light, ArmedEntityRenderState state, float limbSwing, float limbSwingAmount, CallbackInfo ci) {
        ESP.endItemCapture();
    }

    @Inject(method = "submitArmWithItem", at = @At("HEAD"))
    private void krs$captureLegacyVanillaSwordTransform(ArmedEntityRenderState state, ItemStackRenderState item, ItemStack itemStack, HumanoidArm arm, PoseStack poseStack, SubmitNodeCollector submitter, int light, CallbackInfo ci) {
        krs$legacyVanillaSwordTransform = !GuiEntityRenderGuard.isActive()
                && ((IEntityRenderState) state).client$getEntity() instanceof LocalPlayer
                && state.mainArm == arm
                && ToolUtil.INSTANCE.isSword(itemStack)
                && OldHitting.shouldUseLegacyVanillaThirdPerson();
        krs$legacyVanillaArmDirection = arm == HumanoidArm.RIGHT ? 1 : -1;
    }

    @ModifyArgs(
            method = "submitArmWithItem",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V")
    )
    private void krs$useLegacyVanillaHandTranslation(Args args) {
        if (!krs$legacyVanillaSwordTransform)
            return;

        args.set(0, (float) args.get(0) * -1.0F);
        args.set(1, 0.4375F);
        args.set(2, (float) args.get(2) / -10.0F);
    }

    @WrapWithCondition(
            method = "submitArmWithItem",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V")
    )
    private boolean krs$skipModernThirdPersonRotation(PoseStack poseStack, Quaternionfc rotation) {
        return !krs$legacyVanillaSwordTransform;
    }

    @Inject(
            method = "submitArmWithItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V")
    )
    private void krs$applyLegacyVanillaSwordTransform(ArmedEntityRenderState state, ItemStackRenderState item, ItemStack itemStack, HumanoidArm arm, PoseStack poseStack, SubmitNodeCollector submitter, int light, CallbackInfo ci) {
        if (!krs$legacyVanillaSwordTransform)
            return;

        int direction = krs$legacyVanillaArmDirection;

        // Minecraft 1.7 RenderPlayer's blocking transform, before its full-3D item transform.
        poseStack.translate(direction * 0.05F, 0.0F, -0.1F);
        poseStack.mulPose(Axis.YP.rotationDegrees(direction * -50.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(-10.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(direction * -60.0F));

        poseStack.translate(direction * -0.0625F, 0.1875F, 0.0F);
        poseStack.scale(0.625F, 0.625F, 0.625F);
        poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(100.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(direction * -145.0F));
        poseStack.translate(-0.011765625F, 0.0F, 0.002125F);

        // Convert the old icon-space transform, then cancel the baked modern handheld transform.
        poseStack.translate(0.0F, -0.3F, 0.0F);
        poseStack.scale(1.5F, 1.5F, 1.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(direction * 50.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(direction * 335.0F));
        poseStack.translate(direction * -0.9375F, -0.0625F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(direction * 180.0F));
        poseStack.translate(direction * -0.5F, 0.5F, 0.03125F);

        float inverseHandheldScale = 1.0F / 0.85F;
        poseStack.scale(inverseHandheldScale, inverseHandheldScale, inverseHandheldScale);
        poseStack.mulPose(Axis.ZP.rotationDegrees(direction * -55.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(direction * 90.0F));
        poseStack.translate(0.0F, -0.25F, -0.03125F);
    }
}
