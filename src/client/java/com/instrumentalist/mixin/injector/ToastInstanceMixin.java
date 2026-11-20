package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.utils.render.VanillaHudStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.gui.components.toasts.ToastManager$ToastInstance")
public class ToastInstanceMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;I)V", at = @At("HEAD"))
    private void krs$beginStyledToast(GuiGraphicsExtractor graphics, int screenWidth, CallbackInfo ci) {
        VanillaHudStyle.beginToast();
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;I)V", at = @At("RETURN"))
    private void krs$endStyledToast(GuiGraphicsExtractor graphics, int screenWidth, CallbackInfo ci) {
        VanillaHudStyle.endToast();
    }
}
