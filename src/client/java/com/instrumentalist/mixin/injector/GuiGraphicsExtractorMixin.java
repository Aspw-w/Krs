package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.utils.math.TimerUtil;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.instrumentalist.krs.utils.render.VanillaHudStyle;
import net.minecraft.client.gui.Font;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin {
    @ModifyExpressionValue(method = "itemCooldown", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/DeltaTracker;getGameTimeDeltaPartialTick(Z)F"))
    private float krs$selfCooldownPartialTicks(float original) {
        return TimerUtil.selfPartialTick(original);
    }

    @Inject(method = "tooltip", at = @At("HEAD"), cancellable = true)
    private void krs$styledTooltip(Font font, List<ClientTooltipComponent> lines, int xo, int yo, ClientTooltipPositioner positioner,
                                   @Nullable Identifier style, boolean extraSpaceAfterFirstLine, CallbackInfo ci) {
        List<FormattedCharSequence> texts = new ArrayList<>(lines.size());
        for (ClientTooltipComponent line : lines) {
            if (!(line instanceof ClientTextTooltip textLine))
                return;
            texts.add(((ClientTextTooltipAccessor) textLine).krs$getText());
        }
        if (VanillaHudStyle.tooltip((GuiGraphicsExtractor) (Object) this, texts, xo, yo, positioner))
            ci.cancel();
    }

    @Inject(method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V", at = @At("HEAD"), cancellable = true)
    private void krs$styledToastText(Font font, FormattedCharSequence text, int x, int y, int color, boolean dropShadow, CallbackInfo ci) {
        if (VanillaHudStyle.toastText((GuiGraphicsExtractor) (Object) this, text, x, y, color))
            ci.cancel();
    }

    @Inject(method = "blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V", at = @At("HEAD"), cancellable = true)
    private void krs$styledToastBackground(RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height, int color, CallbackInfo ci) {
        if (VanillaHudStyle.toastBackground((GuiGraphicsExtractor) (Object) this, x, y, width, height))
            ci.cancel();
    }
}
