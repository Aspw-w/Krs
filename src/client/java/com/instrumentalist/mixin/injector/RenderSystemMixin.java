package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.utils.IMinecraft;
import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.screen.CustomTitleScreen;
import com.instrumentalist.krs.screen.NanoVGClickGuiScreen;
import com.instrumentalist.krs.utils.nanovg.NanoVGManager;
import com.instrumentalist.krs.utils.render.DebugOverlayRenderer;
import com.instrumentalist.krs.utils.render.GraphicsApiCompatibility;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.breadloaf.imguimc.imgui.ImguiLoader;

@Mixin(Minecraft.class)
public abstract class RenderSystemMixin implements IMinecraft {

    @Inject(
            method = "renderFrame",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/GpuSurface;blitFromTexture(Lcom/mojang/blaze3d/systems/CommandEncoder;Lcom/mojang/blaze3d/textures/GpuTextureView;)V"
            )
    )
    private void krs$renderCompatibilityOverlaysBeforeSurfaceBlit(boolean renderLevel, CallbackInfo ci) {
        if (GraphicsApiCompatibility.usesCompatibilityRenderer())
            krs$renderLegacyOverlays();
    }

    @Inject(
            method = "renderFrame",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/GpuSurface;present()V"
            )
    )
    private void krs$renderOpenGlOverlaysBeforePresent(boolean renderLevel, CallbackInfo ci) {
        if (!GraphicsApiCompatibility.usesCompatibilityRenderer())
            krs$renderLegacyOverlays();
    }

    private static void krs$renderLegacyOverlays() {
        NanoVGClickGuiScreen.renderDetachedIfNeeded();

        if (GraphicsApiCompatibility.usesCompatibilityRenderer()) {
            if (Client.nanoVgManager != null
                    && (Client.nanoVgManager.hasQueuedRenderers()
                    || CustomTitleScreen.shouldRenderStartupLoadBar())) {
                GraphicsApiCompatibility.renderOffscreenLayer(
                        GraphicsApiCompatibility.Layer.NANO_VG,
                        () -> {
                            Client.nanoVgManager.renderQueued();
                            CustomTitleScreen.renderStartupLoadBarIfNeeded(Client.nanoVgManager);
                        },
                        Client.nanoVgManager::discardRenderQueue
                );
            }
        } else if (Client.nanoVgManager != null) {
            Client.nanoVgManager.renderQueued();
            CustomTitleScreen.renderStartupLoadBarIfNeeded(Client.nanoVgManager);
        }

        if (NanoVGManager.shouldRenderBelowDebugOverlay() && mc.gameRenderer instanceof DebugOverlayRenderer debugOverlayRenderer) {
            debugOverlayRenderer.krs$renderDebugOverlayOnTop();
        }

        if (GraphicsApiCompatibility.usesCompatibilityRenderer()) {
            if (ImguiLoader.shouldRenderFrame())
                GraphicsApiCompatibility.renderOffscreenLayer(
                        GraphicsApiCompatibility.Layer.IMGUI,
                        ImguiLoader::onFrameRender
                );
        } else {
            ImguiLoader.onFrameRender();
        }
    }
}
