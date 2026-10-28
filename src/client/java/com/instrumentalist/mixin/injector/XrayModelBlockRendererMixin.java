package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.level.CaveFinder;
import com.instrumentalist.krs.hacks.features.level.Xray;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(ModelBlockRenderer.class)
public abstract class XrayModelBlockRendererMixin {
    private static final ThreadLocal<float[]> krs$currentOpacity = ThreadLocal.withInitial(() -> new float[]{1.0F});

    @WrapOperation(
            method = "shouldRenderFace(Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/core/BlockPos;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;shouldRenderFace(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Z")
    )
    private boolean krs$xrayShouldRenderFace(BlockState state, BlockState otherState, Direction side, Operation<Boolean> original,
                                             BlockAndTintGetter level, BlockState currentState, Direction currentSide, BlockPos neighborPos) {
        if (!Xray.isActive()) {
            krs$currentOpacity.get()[0] = 1.0F;
            if (CaveFinder.isActive() && Boolean.FALSE.equals(CaveFinder.shouldDrawFace(level, state, neighborPos.relative(side.getOpposite()), side)))
                return false;
            return original.call(state, otherState, side);
        }

        BlockPos pos = neighborPos.relative(side.getOpposite());
        Boolean shouldDrawSide = Xray.shouldDrawSide(state, pos, level);

        if (!Xray.isOpacityMode() || Xray.isVisible(state, pos, level)) {
            krs$currentOpacity.get()[0] = 1.0F;
        } else {
            krs$currentOpacity.get()[0] = Xray.getOpacityFloat();
        }

        if (shouldDrawSide != null) {
            return shouldDrawSide;
        }

        if (Boolean.FALSE.equals(CaveFinder.shouldDrawFace(level, state, pos, side)))
            return false;

        return original.call(state, otherState, side);
    }

    @WrapOperation(
            method = "putQuadWithTint(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/client/resources/model/geometry/BakedQuad;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/BlockQuadOutput;put(FFFLnet/minecraft/client/resources/model/geometry/BakedQuad;Lcom/mojang/blaze3d/vertex/QuadInstance;)V")
    )
    private void krs$xrayModifyOpacity(BlockQuadOutput output, float x, float y, float z, BakedQuad quad, QuadInstance instance,
                                       Operation<Void> original, BlockQuadOutput outerOutput, float outerX, float outerY, float outerZ,
                                       BlockAndTintGetter level, BlockState state, BlockPos pos) {
        if (Xray.shouldFullBright(state, pos, level)) {
            instance.setLightCoords(LightCoordsUtil.FULL_BRIGHT);
            for (int i = 0; i < 4; i++)
                instance.setColor(i, ARGB.color(ARGB.alpha(instance.getColor(i)), 255, 255, 255));
        }

        float opacity = krs$currentOpacity.get()[0];
        if (opacity < 1.0F) {
            for (int i = 0; i < 4; i++) {
                int color = instance.getColor(i);
                instance.setColor(i, ARGB.color(
                        Math.round(ARGB.alpha(color) * opacity),
                        ARGB.red(color),
                        ARGB.green(color),
                        ARGB.blue(color)
                ));
            }
        }

        original.call(output, x, y, z, quad, instance);
    }

    @WrapOperation(
            method = {
                    "tesselateFlat(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLjava/util/List;Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V",
                    "tesselateAmbientOcclusion(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLjava/util/List;Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/dispatch/BlockStateModelPart;getQuads(Lnet/minecraft/core/Direction;)Ljava/util/List;", ordinal = 1)
    )
    private List<BakedQuad> krs$xrayHideUnculledQuads(BlockStateModelPart part, Direction direction, Operation<List<BakedQuad>> original,
                                                      BlockQuadOutput output, float x, float y, float z, List<BlockStateModelPart> parts,
                                                      BlockAndTintGetter level, BlockState state, BlockPos pos) {
        if (Xray.isActive() && Boolean.FALSE.equals(Xray.shouldDrawSide(state, pos, level))) {
            return List.of();
        }

        if (CaveFinder.isActive() && Boolean.FALSE.equals(CaveFinder.shouldDrawFace(level, state, pos, null))) {
            return List.of();
        }

        return original.call(part, direction);
    }
}
