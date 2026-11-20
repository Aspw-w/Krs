package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.utils.render.VanillaHudStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ToastManager.class)
public class ToastManagerMixin {

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void krs$markToastLayer(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        VanillaHudStyle.markToastSplit(graphics);
    }
}
