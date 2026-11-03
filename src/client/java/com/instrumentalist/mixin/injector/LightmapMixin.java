package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.render.LegacyRenderer;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTexture;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Lightmap.class)
public abstract class LightmapMixin {

    @Shadow
    @Final
    private GpuTexture texture;

    @Unique
    private NativeImage krs$legacyLightmap;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void legacyRendererLightmapHook(LightmapRenderState renderState, CallbackInfo ci) {
        if (!LegacyRenderer.lightmapPending || !LegacyRenderer.isLightmapActive()) return;

        if (krs$legacyLightmap == null)
            krs$legacyLightmap = new NativeImage(Lightmap.TEXTURE_SIZE, Lightmap.TEXTURE_SIZE, false);

        for (int i = 0; i < 256; ++i)
            krs$legacyLightmap.setPixel(i % 16, i / 16, LegacyRenderer.lightmapColors[i]);

        RenderSystem.getDevice().createCommandEncoder().writeToTexture(this.texture, krs$legacyLightmap);
        LegacyRenderer.lightmapPending = false;
        ci.cancel();
    }

    @Inject(method = "close", at = @At("RETURN"))
    private void legacyRendererCloseHook(CallbackInfo ci) {
        if (krs$legacyLightmap != null) {
            krs$legacyLightmap.close();
            krs$legacyLightmap = null;
        }
    }
}
