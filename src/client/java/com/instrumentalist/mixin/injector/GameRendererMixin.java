package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.utils.IMinecraft;
import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.Render3DEvent;
import com.instrumentalist.krs.events.features.RenderEvent;
import com.instrumentalist.krs.events.features.FrameBufferEvent;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.player.Freecam;
import com.instrumentalist.krs.hacks.features.render.*;
import com.instrumentalist.krs.utils.render.DebugOverlayRenderer;
import com.instrumentalist.krs.utils.render.GraphicsApiCompatibility;
import com.instrumentalist.krs.utils.render.RenderUtil;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.math.Axis;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin implements DebugOverlayRenderer, IMinecraft {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private GameRenderState gameRenderState;

    @Shadow
    @Final
    private GuiRenderer guiRenderer;

    @Unique
    private boolean cancelNextBobView;

    @Unique
    private float krs$currentCameraTickDelta;

    @Inject(method = "mainRenderTarget", at = @At("HEAD"))
    private void frameBufferEvent(CallbackInfoReturnable<RenderTarget> ci) {
        if (!Client.loaded || Client.eventManager == null || !Client.eventManager.hasListeners(FrameBufferEvent.class))
            return;

        Client.eventManager.call(new FrameBufferEvent());
    }

    @Inject(method = "renderLevel(Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"))
    private void captureRenderTickDelta(DeltaTracker tickCounter, CallbackInfo ci) {
        RenderUtil.INSTANCE.beginWorldProjectionFrame();
        krs$currentCameraTickDelta = mc.gameRenderer.mainCamera().getCameraEntityPartialTicks(tickCounter);
        ESP.beginCapture();
    }

    @Inject(method = "renderLevel(Lnet/minecraft/client/DeltaTracker;)V", at = @At("RETURN"))
    private void krs$endShaderEspCapture(DeltaTracker tickCounter, CallbackInfo ci) {
        ESP.endCapture();
    }

    @WrapOperation(
            method = "renderLevel(Lnet/minecraft/client/DeltaTracker;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"
            )
    )
    private GpuBufferSlice krs$captureShaderEspProjection(ProjectionMatrixBuffer projectionMatrixBuffer, Matrix4f projectionMatrix, Operation<GpuBufferSlice> original) {
        Matrix4fc viewRotationMatrix = this.gameRenderState.levelRenderState.cameraRenderState.viewRotationMatrix;
        ESP.updateProjection(projectionMatrix, viewRotationMatrix);
        RenderUtil.INSTANCE.updateRenderedWorldProjection(projectionMatrix, viewRotationMatrix);
        return original.call(projectionMatrixBuffer, projectionMatrix);
    }

    @Override
    public void krs$renderDebugOverlayOnTop() {
        GuiGraphicsExtractor extractor = new GuiGraphicsExtractor(
                this.minecraft,
                this.gameRenderState.guiRenderState,
                (int) this.minecraft.mouseHandler.getScaledXPos(this.minecraft.getWindow()),
                (int) this.minecraft.mouseHandler.getScaledYPos(this.minecraft.getWindow())
        );
        this.minecraft.getDebugOverlay().extractRenderState(extractor);
        this.guiRenderer.render();
    }

    @Inject(
            method = "render(Lnet/minecraft/client/DeltaTracker;Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V"
            )
    )
    private void krs$renderNanoVgBackgroundBeforeGui(DeltaTracker tickCounter, boolean renderLevel, CallbackInfo ci) {
        if (Client.nanoVgManager == null)
            return;

        if (GraphicsApiCompatibility.usesCompatibilityRenderer()) {
            if (Client.nanoVgManager.hasQueuedBeforeGuiRenderers())
                GraphicsApiCompatibility.renderOffscreenLayer(
                        GraphicsApiCompatibility.Layer.NANO_VG_BEFORE_GUI,
                        Client.nanoVgManager::renderQueuedBeforeGui,
                        Client.nanoVgManager::discardBeforeGuiRenderQueue
                );
        } else {
            Client.nanoVgManager.renderQueuedBeforeGui();
        }
    }

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;bobView(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V", ordinal = 0), method = "renderLevel(Lnet/minecraft/client/DeltaTracker;)V")
    private void cameraBobTransformHook(DeltaTracker tickCounter, CallbackInfo ci) {
        cancelNextBobView = true;
    }

    @Inject(at = @At("HEAD"), method = "bobView(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V", cancellable = true)
    private void onViewBobbing(CameraRenderState cameraRenderState, PoseStack matrices, CallbackInfo ci) {
        if (ModuleManager.getModuleState(Freecam.class) && Freecam.getCanFly()) {
            cancelNextBobView = false;
            ci.cancel();
            return;
        }

        if (cancelNextBobView) {
            if (ModuleManager.getModuleState(Freecam.class) && Freecam.getCanFly())
                ci.cancel();

            cancelNextBobView = false;
        }

        if (!ci.isCancelled() && ModuleManager.getModuleState(FPSBobbing.class)) {
            Entity cameraEntity = mc.getCameraEntity();
            if (cameraEntity instanceof AbstractClientPlayer player) {
                float tickDelta = krs$currentCameraTickDelta;
                float totalMovement = player.avatarState().getInterpolatedWalkDistance(tickDelta);
                float strideInterp = player.avatarState().getInterpolatedBob(tickDelta);

                float tiltStrength = 3.0f;
                float swingStrength = 5.0f;
                float bobStrength = 0.5f;

                float phase = totalMovement * (float) Math.PI;
                float phaseOffset = 0.2f;
                float verticalBob = -Math.abs(Mth.cos(phase) * strideInterp);
                float horizontalBob = Mth.sin(phase) * strideInterp;

                matrices.translate(horizontalBob * bobStrength, verticalBob * bobStrength, 0.0f);
                matrices.mulPose(Axis.ZP.rotationDegrees(horizontalBob * tiltStrength));
                matrices.mulPose(Axis.XP.rotationDegrees(Math.abs(Mth.cos(phase - phaseOffset) * strideInterp) * swingStrength));
            }
            ci.cancel();
        }
    }

    @WrapOperation(at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;lerp(FFF)F", ordinal = 0), method = "renderLevel(Lnet/minecraft/client/DeltaTracker;)V")
    private float antiBlindHook(float delta, float start, float end, Operation<Float> original) {
        if (ModuleManager.getModuleState(AntiBlind.class) && AntiBlind.effects.get())
            return 0;

        return original.call(delta, start, end);
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V", args = {"ldc=hand"}))
    private void krs$renderShaderEspBeforeDepthClear(DeltaTracker tickCounter, CallbackInfo ci) {
        ESP.renderCapturedShadow();
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V", args = {"ldc=hand"}))
    private void render3DEvent(DeltaTracker tickCounter, CallbackInfo ci, @Local(ordinal = 1) float tickDelta, @Local PoseStack matrixStack) {
        if (Client.eventManager == null || !Client.eventManager.hasListeners(Render3DEvent.class))
            return;

        RenderUtil.INSTANCE.beginWorldProjection();
        Render3DEvent event = new Render3DEvent(matrixStack, tickDelta);
        Client.eventManager.call(event);
    }

    @Inject(at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V", args = {"ldc=hand"}), method = "renderLevel(Lnet/minecraft/client/DeltaTracker;)V")
    private void renderEvent(DeltaTracker tickCounter, CallbackInfo ci, @Local(ordinal = 1) float tickDelta, @Local PoseStack matrixStack) {
        if (Client.eventManager == null || !Client.eventManager.hasListeners(RenderEvent.class))
            return;

        RenderEvent event = new RenderEvent(matrixStack, tickDelta);
        Client.eventManager.call(event);
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void hideHand(CameraRenderState cameraRenderState, float tickDelta, Matrix4fc matrix4f, CallbackInfo ci) {
        cancelNextBobView = false;

        if (Zoom.shouldZoom())
            ci.cancel();
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void noHurtCamHook(CameraRenderState cameraRenderState, PoseStack matrixStack, CallbackInfo ci) {
        if (ModuleManager.getModuleState(NoHurtCam.class))
            ci.cancel();
    }
}
