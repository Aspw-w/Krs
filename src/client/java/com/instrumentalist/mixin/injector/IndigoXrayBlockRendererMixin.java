package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.level.CaveFinder;
import com.instrumentalist.krs.hacks.features.level.Xray;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.render.AltModelBlockRendererImpl", remap = false)
public abstract class IndigoXrayBlockRendererMixin {
    @Shadow
    private BlockPos pos;

    @Shadow
    private BlockState blockState;

    @Unique
    private boolean krs$hideBlock;

    @Unique
    private boolean krs$applyOpacity;

    @Unique
    private int krs$opacityAlpha;

    @Unique
    private boolean krs$fullBright;

    @Inject(method = "tesselateBlock", at = @At("HEAD"))
    private void krs$prepareXrayBlock(QuadEmitter output, float x, float y, float z,
                                      BlockAndTintGetter level, BlockPos pos, BlockState blockState,
                                      BlockStateModel model, long seed, CallbackInfo ci) {
        Boolean shouldDrawSide = Xray.shouldDrawSide(blockState, pos, level);
        krs$hideBlock = Boolean.FALSE.equals(shouldDrawSide)
                || shouldDrawSide == null && Boolean.FALSE.equals(CaveFinder.shouldDrawFace(level, blockState, pos, null));
        krs$applyOpacity = !krs$hideBlock && shouldDrawSide == null && Xray.isOpacityMode();
        krs$opacityAlpha = Math.round(Xray.getOpacityFloat() * 255.0F);
        krs$fullBright = Xray.shouldFullBright(blockState, pos, level);
    }

    @Inject(method = "transform", at = @At("HEAD"), cancellable = true)
    private void krs$applyXrayToIndigoQuad(MutableQuadView quad, CallbackInfoReturnable<Boolean> cir) {
        if (krs$hideBlock) {
            cir.setReturnValue(false);
            return;
        }

        if (krs$fullBright) {
            quad.emissive(true);
            quad.ambientOcclusion(TriState.FALSE);
        }

        if (!krs$applyOpacity)
            return;

        quad.chunkLayer(ChunkSectionLayer.TRANSLUCENT);
        for (int i = 0; i < 4; i++) {
            int color = quad.color(i);
            quad.color(i, ARGB.color(
                    ARGB.alpha(color) * krs$opacityAlpha / 255,
                    ARGB.red(color),
                    ARGB.green(color),
                    ARGB.blue(color)
            ));
        }
    }
}
