package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.utils.render.TitleBackground;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.Panorama;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Panorama.class)
public abstract class PanoramaMixin {

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void replaceWithTitleBackground(GuiGraphicsExtractor graphics, int width, int height, CallbackInfo ci) {
        if (TitleBackground.render(graphics, width, height))
            ci.cancel();
    }
}
