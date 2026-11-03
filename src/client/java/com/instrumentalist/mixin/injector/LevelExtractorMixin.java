package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.render.LegacyRenderer;
import com.instrumentalist.krs.hacks.features.render.AntiBlind;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {

    @Shadow
    @Final
    private LevelRenderState levelRenderState;

    @Inject(method = "extract", at = @At("RETURN"))
    private void legacyRendererCloudsHook(DeltaTracker deltaTracker, Camera camera, float worldPartialTicks, CallbackInfo ci) {
        var level = Minecraft.getInstance().level;
        if (level == null || !LegacyRenderer.isCloudsActive() || ARGB.alpha(this.levelRenderState.cloudColor) <= 0) return;

        this.levelRenderState.cloudColor = LegacyRenderer.cloudColor(level, worldPartialTicks);
        this.levelRenderState.cloudHeight = LegacyRenderer.cloudHeight(level, this.levelRenderState.cloudHeight);
    }

    // Since 26.3 the portal effect intensity is interpolated while the player state is extracted.
    @WrapOperation(method = "extractPlayerState", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;lerp(FFF)F", ordinal = 0))
    private float antiBlindHook(float delta, float start, float end, Operation<Float> original) {
        if (ModuleManager.getModuleState(AntiBlind.class) && AntiBlind.effects.get())
            return 0;

        return original.call(delta, start, end);
    }
}
