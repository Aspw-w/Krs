package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.render.Rotations;
import com.instrumentalist.krs.hacks.features.render.TrueSight;
import com.instrumentalist.mixin.oringo.IEntityRenderState;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @ModifyExpressionValue(
            method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;solveBodyRot(Lnet/minecraft/world/entity/LivingEntity;FF)F")
    )
    private float krs$vanillaRotationBodyYaw(float original, LivingEntity entity, LivingEntityRenderState state, float tickDelta) {
        if (krs$shouldApplyVanillaRotation(entity))
            return Client.rotationManager.getInterpolatedBodyYaw(tickDelta);

        return original;
    }

    @ModifyExpressionValue(
            method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;rotLerp(FFF)F", ordinal = 0)
    )
    private float krs$vanillaRotationHeadYaw(float original, LivingEntity entity, LivingEntityRenderState state, float tickDelta) {
        if (krs$shouldApplyVanillaRotation(entity))
            return Client.rotationManager.getInterpolatedYaw(tickDelta);

        return original;
    }

    @ModifyExpressionValue(
            method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getXRot(F)F")
    )
    private float krs$vanillaRotationPitch(float original, LivingEntity entity, LivingEntityRenderState state, float tickDelta) {
        if (krs$shouldApplyVanillaRotation(entity))
            return Client.rotationManager.getInterpolatedPitch(tickDelta);

        return original;
    }

    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD")
    )
    private void krs$revealInvisibleEntities(LivingEntityRenderState state, PoseStack matrices, SubmitNodeCollector submitter, CameraRenderState cameraState, CallbackInfo ci) {
        if (!ModuleManager.getModuleState(TrueSight.class) || !TrueSight.entities.get())
            return;

        Entity entity = ((IEntityRenderState) state).client$getEntity();
        if (!TrueSight.requiresTrueSight(entity))
            return;

        state.isInvisible = false;
        state.isInvisibleToPlayer = false;
    }

    @Unique
    private boolean krs$shouldApplyVanillaRotation(LivingEntity entity) {
        return Client.rotationManager != null
                && Client.rotationManager.isRotating()
                && Rotations.shouldUseVanilla()
                && entity == Minecraft.getInstance().player;
    }
}
