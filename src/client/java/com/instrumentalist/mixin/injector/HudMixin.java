package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.render.HUDHider;
import com.instrumentalist.krs.hacks.features.render.NoRender;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class HudMixin {

    @Inject(method = "extractVignette", at = @At("HEAD"), cancellable = true)
    private void noRenderVignette(GuiGraphicsExtractor graphics, Entity camera, CallbackInfo ci) {
        if (NoRender.active(NoRender.vignette))
            ci.cancel();
    }

    @Inject(method = "extractPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void noRenderPortalOverlay(GuiGraphicsExtractor graphics, float alpha, CallbackInfo ci) {
        if (NoRender.active(NoRender.portalOverlay))
            ci.cancel();
    }

    @Inject(method = "extractConfusionOverlay", at = @At("HEAD"), cancellable = true)
    private void noRenderConfusionOverlay(GuiGraphicsExtractor graphics, float strength, CallbackInfo ci) {
        if (NoRender.active(NoRender.portalOverlay))
            ci.cancel();
    }

    @Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void hideScoreboard(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (ModuleManager.getModuleState(HUDHider.class) && HUDHider.board.get()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractBossOverlay", at = @At("HEAD"), cancellable = true)
    private void hideBossBar(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (ModuleManager.getModuleState(HUDHider.class) && HUDHider.bos.get()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void hideActionBar(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (ModuleManager.getModuleState(HUDHider.class) && HUDHider.bar.get()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractTitle", at = @At("HEAD"), cancellable = true)
    private void hideTitle(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (ModuleManager.getModuleState(HUDHider.class) && HUDHider.titled.get()) {
            ci.cancel();
        }
    }
}
