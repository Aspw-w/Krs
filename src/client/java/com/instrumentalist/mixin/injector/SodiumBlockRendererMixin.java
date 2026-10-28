package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.level.Xray;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer", remap = false)
public abstract class SodiumBlockRendererMixin extends SodiumAbstractBlockRenderContextMixin {
    @Unique
    private static final float[] KRS$FULL_BRIGHTNESS = {1.0F, 1.0F, 1.0F, 1.0F};

    @ModifyExpressionValue(
            method = "processQuad",
            at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/model/MutableQuadViewImpl;getRenderType()Lnet/minecraft/client/renderer/chunk/ChunkSectionLayer;"),
            require = 0
    )
    private ChunkSectionLayer krs$xrayForceTranslucentLayer(ChunkSectionLayer original) {
        if (krs$shouldApplyXrayOpacity()) {
            return ChunkSectionLayer.TRANSLUCENT;
        }

        return original;
    }

    @ModifyExpressionValue(
            method = "processQuad",
            at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/model/MutableQuadViewImpl;emissive()Z"),
            require = 0
    )
    private boolean krs$xrayFullBright(boolean original) {
        return original || krs$shouldApplyXrayFullBright();
    }

    @ModifyArg(
            method = "processQuad",
            at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/BlockRenderer;bufferQuad(Lnet/caffeinemc/mods/sodium/client/render/model/MutableQuadViewImpl;[FLnet/caffeinemc/mods/sodium/client/render/chunk/terrain/material/Material;)V"),
            index = 1,
            require = 0
    )
    private float[] krs$xrayFullBrightShade(float[] brightness) {
        return krs$shouldApplyXrayFullBright() ? KRS$FULL_BRIGHTNESS : brightness;
    }

    @ModifyExpressionValue(
            method = "bufferQuad(Lnet/caffeinemc/mods/sodium/client/render/model/MutableQuadViewImpl;[FLnet/caffeinemc/mods/sodium/client/render/chunk/terrain/material/Material;)V",
            at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/model/MutableQuadViewImpl;baseColor(I)I"),
            require = 0
    )
    private int krs$xrayModifyQuadColor(int original) {
        if (krs$shouldApplyXrayOpacity()) {
            return original & Xray.getOpacityColorMask();
        }

        return original;
    }
}
