package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.utils.render.VanillaHudStyle;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(GuiRenderState.class)
public class GuiRenderStateMixin {

    @Inject(method = "traverse(Ljava/util/function/Consumer;Lnet/minecraft/client/renderer/state/gui/GuiRenderState$TraverseRange;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/state/gui/GuiRenderState;traverse(Lnet/minecraft/client/renderer/state/gui/GuiRenderState$Node;Ljava/util/function/Consumer;)V"))
    private void krs$trackStratum(Consumer<?> consumer, GuiRenderState.TraverseRange range, CallbackInfo ci, @Local(ordinal = 2) int stratum) {
        VanillaHudStyle.setTraversedStratum(stratum);
    }
}
