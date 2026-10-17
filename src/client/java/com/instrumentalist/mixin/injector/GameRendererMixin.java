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
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
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

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.OptionsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import com.mojang.renderpearl.api.textures.GpuTextureView;
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

    @Unique
    private PoseStack krs$levelBobStack;

    @ModifyReturnValue(method = "mainRenderTarget", at = @At("RETURN"))
    private RenderTarget frameBufferEvent(RenderTarget original) {
        if (Client.loaded && Client.eventManager != null && Client.eventManager.hasListeners(FrameBufferEvent.class))
            Client.eventManager.call(new FrameBufferEvent());

        return original;
    }

    // Since 26.3 renderLevel() only renders the extracted state; the camera partial tick is part of that state.
    @Inject(method = "renderLevel()V", at = @At("HEAD"))
    private void captureRenderTickDelta(CallbackInfo ci) {
        RenderUtil.INSTANCE.beginWorldProjectionFrame();
        krs$currentCameraTickDelta = this.gameRenderState.levelRenderState.cameraRenderState.cameraEntityPartialTicks;
        krs$levelBobStack = null;
        ESP.beginCapture();
    }

    @Inject(method = "renderLevel()V", at = @At("RETURN"))
    private void krs$endShaderEspCapture(CallbackInfo ci) {
        ESP.endCapture();
        krs$levelBobStack = null;
    }

    @WrapOperation(
            method = "renderLevel()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"
            )
    )
    private GpuBufferSlice krs$captureShaderEspProjection(ProjectionMatrixBuffer projectionMatrixBuffer, Matrix4f projectionMatrix, Operation<GpuBufferSlice> original, @Local PoseStack bobStack) {
        krs$levelBobStack = bobStack;
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
            method = "render()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V"
            )
    )
    private void krs$renderNanoVgBackgroundBeforeGui(CallbackInfo ci) {
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

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;bobView(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V", ordinal = 0), method = "renderLevel()V")
    private void cameraBobTransformHook(CallbackInfo ci) {
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
                matrices.rotate(Axis.ZP.rotationDegrees(horizontalBob * tiltStrength));
                matrices.rotate(Axis.XP.rotationDegrees(Math.abs(Mth.cos(phase - phaseOffset) * strideInterp) * swingStrength));
            }
            ci.cancel();
        }
    }

    // The portal effect is now interpolated during extraction, see LevelExtractorMixin#antiBlindHook.

    // Since 26.3 the "hand" section moved from renderLevel into render3dHud.
    @Inject(method = "render3dHud", at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V", args = {"ldc=hand"}))
    private void krs$renderShaderEspBeforeDepthClear(CameraRenderState cameraState, PlayerRenderState playerState, OptionsRenderState optionsState, boolean consistentDepthRequired, CallbackInfo ci) {
        ESP.renderCapturedShadow();
    }

    @Inject(method = "render3dHud", at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V", args = {"ldc=hand"}))
    private void render3DEvent(CameraRenderState cameraState, PlayerRenderState playerState, OptionsRenderState optionsState, boolean consistentDepthRequired, CallbackInfo ci) {
        if (Client.eventManager == null || !Client.eventManager.hasListeners(Render3DEvent.class))
            return;

        RenderUtil.INSTANCE.beginWorldProjection();
        Render3DEvent event = new Render3DEvent(krs$eventPoseStack(), cameraState.cameraEntityPartialTicks);
        Client.eventManager.call(event);
    }

    @Inject(method = "render3dHud", at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V", args = {"ldc=hand"}))
    private void renderEvent(CameraRenderState cameraState, PlayerRenderState playerState, OptionsRenderState optionsState, boolean consistentDepthRequired, CallbackInfo ci) {
        if (Client.eventManager == null || !Client.eventManager.hasListeners(RenderEvent.class))
            return;

        RenderEvent event = new RenderEvent(krs$eventPoseStack(), cameraState.cameraEntityPartialTicks);
        Client.eventManager.call(event);
    }

    @Unique
    private PoseStack krs$eventPoseStack() {
        return krs$levelBobStack != null ? krs$levelBobStack : new PoseStack();
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void hideHand(CameraRenderState cameraRenderState, PlayerRenderState playerState, GpuTextureView depthTextureView, CallbackInfo ci) {
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
