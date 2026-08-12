package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.render.TrueSight;
import com.instrumentalist.mixin.oringo.IEntityRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class TrueSightMixin {

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
}