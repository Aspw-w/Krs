package com.instrumentalist.mixin.injector;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.instrumentalist.krs.utils.render.VanillaHudStyle;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.WindowRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {

    @Unique
    private boolean krs$skipEmptyScissorDraw;

    @Inject(method = "executeDraw", at = @At("HEAD"))
    private void krs$resetEmptyScissor(CallbackInfo ci) {
        krs$skipEmptyScissorDraw = false;
    }

    @Inject(method = "enableScissor", at = @At("HEAD"), cancellable = true)
    private void krs$skipEmptyScissor(ScreenRectangle rectangle, RenderPass pass, CallbackInfo ci) {
        if (!krs$hasDrawableScissor(rectangle)) {
            krs$skipEmptyScissorDraw = true;
            pass.disableScissor();
            ci.cancel();
        }
    }

    @WrapOperation(
            method = "executeDraw",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/renderpearl/api/commands/RenderPass;drawIndexed(IIIII)V"
            )
    )
    private void krs$skipDrawOutsideScissor(RenderPass pass, int indexCount, int instanceCount, int firstIndex, int baseVertex, int baseInstance, Operation<Void> original) {
        if (krs$skipEmptyScissorDraw) {
            krs$skipEmptyScissorDraw = false;
            return;
        }

        original.call(pass, indexCount, instanceCount, firstIndex, baseVertex, baseInstance);
    }

    @Unique
    private static boolean krs$hasDrawableScissor(ScreenRectangle rectangle) {
        if (rectangle == null || rectangle.width() <= 0 || rectangle.height() <= 0)
            return false;

        WindowRenderState window = Minecraft.getInstance().gameRenderer.gameRenderState().windowRenderState;
        int scale = window.guiScale;
        if (scale <= 0 || window.width <= 0 || window.height <= 0)
            return false;

        int left = rectangle.left() * scale;
        int top = rectangle.top() * scale;
        int right = Math.min(rectangle.right() * scale, window.width);
        int bottom = Math.min(rectangle.bottom() * scale, window.height);
        return right > left && bottom > top;
    }

    @Shadow
    @Final
    private List<?> draws;

    @Shadow
    private RenderPipeline previousPipeline;

    @Inject(method = "prepare", at = @At("HEAD"))
    private void krs$beginSplitMesh(CallbackInfo ci) {
        VanillaHudStyle.beginGuiMesh();
    }

    @Inject(method = "prepare", at = @At("RETURN"))
    private void krs$endSplitMesh(CallbackInfo ci) {
        VanillaHudStyle.endGuiMesh(draws.size());
    }

    @Inject(method = "addElementToMesh", at = @At("HEAD"))
    private void krs$splitMesh(GuiElementRenderState elementState, CallbackInfo ci) {
        if (VanillaHudStyle.startsSplit(draws.size()))
            previousPipeline = null;
    }

    @WrapOperation(method = "draw", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;executeDrawRange(Ljava/util/function/Supplier;Lcom/mojang/blaze3d/pipeline/RenderTarget;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;II)V"))
    private void krs$renderLayersBetweenDraws(GuiRenderer renderer, Supplier<String> label, RenderTarget target, GpuBufferSlice transforms,
                                              int start, int end, Operation<Void> original) {
        int cursor = start;
        int split;
        while ((split = VanillaHudStyle.nextSplit(cursor, end)) >= 0) {
            if (split > cursor)
                original.call(renderer, label, target, transforms, cursor, split);
            VanillaHudStyle.renderSplit(split);
            cursor = split;
        }
        if (end > cursor)
            original.call(renderer, label, target, transforms, cursor, end);
    }
}
