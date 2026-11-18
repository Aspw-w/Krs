package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.screen.CustomTitleScreen;
import com.instrumentalist.krs.screen.NanoVGClickGuiScreen;
import com.instrumentalist.krs.utils.GuiInputBlocker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.nvgu.NVGU;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Screen.class)
public abstract class ScreenMixin {

    @Unique
    private static final Consumer<NVGU> KRS_NOTIFICATION_RENDERER = vg -> Client.notificationManager.renderNotifications(vg);

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", at = @At("TAIL"))
    private void krs$renderMenuNotifications(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        Object self = this;
        if (minecraft.level != null || Client.nanoVgManager == null || Client.notificationManager == null)
            return;
        if (self != minecraft.gui.screen() || self instanceof CustomTitleScreen || self instanceof NanoVGClickGuiScreen)
            return;

        Client.nanoVgManager.load(KRS_NOTIFICATION_RENDERER);
    }

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
