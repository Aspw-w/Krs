package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.utils.GuiInputBlocker;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Screen.class)
public abstract class ScreenMixin {

    @ModifyVariable(method = "extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int blockRenderMouseXWhenImguiCaptures(int mouseX) {
        return GuiInputBlocker.sanitizeMouseCoordinate(mouseX);
    }

    @ModifyVariable(method = "extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int blockRenderMouseYWhenImguiCaptures(int mouseY) {
        return GuiInputBlocker.sanitizeMouseCoordinate(mouseY);
    }

    @ModifyReturnValue(method = "isMouseOver(DD)Z", at = @At("RETURN"))
    private boolean blockScreenHoverWhenImguiCaptures(boolean original) {
        return original && !GuiInputBlocker.shouldBlockMinecraftMouse();
    }
}
