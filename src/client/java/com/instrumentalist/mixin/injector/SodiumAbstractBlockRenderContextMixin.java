package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.level.CaveFinder;
import com.instrumentalist.krs.hacks.features.level.Xray;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext", remap = false)
public abstract class SodiumAbstractBlockRenderContextMixin {
    @Shadow
    protected BlockState state;

    @Shadow
    protected BlockPos pos;

    @Shadow
    protected BlockAndTintGetter level;

    @ModifyReturnValue(method = "isFaceCulled", at = @At("RETURN"), require = 0)
    private boolean krs$xrayIsFaceCulled(boolean original, Direction face) {
        Boolean shouldDrawSide = Xray.shouldDrawSide(state, pos);
        if (shouldDrawSide == null)
            shouldDrawSide = CaveFinder.shouldDrawFace(level, state, pos, face);
        return shouldDrawSide != null ? !shouldDrawSide : original;
    }

    protected boolean krs$shouldApplyXrayFullBright() {
        return Xray.shouldFullBright(state, pos, Minecraft.getInstance().level);
    }

    protected boolean krs$shouldApplyXrayOpacity() {
        return Xray.isOpacityMode() && state != null && !Xray.isVisible(state, pos);
    }
}
