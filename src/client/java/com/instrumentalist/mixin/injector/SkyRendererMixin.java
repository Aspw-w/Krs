package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.render.LegacyRenderer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void legacyRendererSkyHook(ClientLevel level, float partialTicks, Camera camera, SkyRenderState state, CallbackInfo ci) {
        if (LegacyRenderer.isSkyActive())
            LegacyRenderer.applySky(state, level, camera, partialTicks);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void legacyRendererShortRenderDistanceHook(GpuBufferSlice skyFog, SkyRenderState state, CallbackInfo ci) {
        if (LegacyRenderer.shouldSkipSky())
            ci.cancel();
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SkyRenderer;renderSkyDisc(Lcom/mojang/renderpearl/api/commands/RenderPass;Lorg/joml/Vector3fc;)V"))
    private void legacyRendererUpperSkyHook(SkyRenderer instance, RenderPass renderPass, Vector3fc skyColor, Operation<Void> original) {
        if (!LegacyRenderer.isSkyActive() || !LegacyRenderer.renderUpperSky(renderPass))
            original.call(instance, renderPass, skyColor);
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SkyRenderer;renderDarkDisc(Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
    private void legacyRendererLowerSkyHook(SkyRenderer instance, RenderPass renderPass, Operation<Void> original) {
        if (!LegacyRenderer.isSkyActive()) {
            original.call(instance, renderPass);
            return;
        }

        if (!LegacyRenderer.renderLowerSky(renderPass) && LegacyRenderer.vanillaDarkDisc())
            original.call(instance, renderPass);
    }
}
