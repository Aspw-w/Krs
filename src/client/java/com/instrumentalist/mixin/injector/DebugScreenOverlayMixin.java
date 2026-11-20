package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.render.Interface;
import com.instrumentalist.krs.utils.render.VanillaHudStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(DebugScreenOverlay.class)
public class DebugScreenOverlayMixin {

    @Inject(method = "extractLines", at = @At("HEAD"), cancellable = true)
    private void krs$styledDebugLines(GuiGraphicsExtractor graphics, List<String> lines, boolean alignLeft, int scaledScreenWidth, CallbackInfo ci) {
        if (Interface.styleDebugScreen() && VanillaHudStyle.debugLines(graphics, lines, alignLeft, scaledScreenWidth))
            ci.cancel();
    }
}
