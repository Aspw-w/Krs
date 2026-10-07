package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.utils.render.GuiEntityRenderGuard;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.render.pip.GuiEntityRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GuiEntityRenderer.class)
public abstract class GuiEntityRendererMixin {

    @WrapMethod(
            method = "renderToTexture(Lnet/minecraft/client/renderer/state/gui/pip/GuiEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V"
    )
    private void krs$guardGuiEntityRender(GuiEntityRenderState state, PoseStack poseStack, SubmitNodeCollector submitter, Operation<Void> original) {
        GuiEntityRenderGuard.run(() -> original.call(state, poseStack, submitter));
    }
}
