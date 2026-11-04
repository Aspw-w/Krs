package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.render.LegacyRenderer;
import com.instrumentalist.krs.utils.render.LegacyWater;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;

@Mixin(SpriteContents.class)
public abstract class SpriteContentsMixin {

    @Inject(method = "<init>(Lnet/minecraft/resources/Identifier;Lnet/minecraft/client/resources/metadata/animation/FrameSize;Lcom/mojang/blaze3d/platform/NativeImage;Ljava/util/Optional;Ljava/util/List;Ljava/util/Optional;)V", at = @At("RETURN"))
    private void legacyRendererWaterLoadHook(Identifier name, FrameSize frameSize, NativeImage image, Optional<AnimationMetadataSection> animationInfo, List<MetadataSectionType.WithValue<?>> additionalMetadata, Optional<TextureMetadataSection> textureInfo, CallbackInfo ci) {
        LegacyWater.onSpriteLoaded((SpriteContents) (Object) this, image, LegacyRenderer.isWaterConfigured());
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void legacyRendererWaterCloseHook(CallbackInfo ci) {
        LegacyWater.onSpriteClosed((SpriteContents) (Object) this);
    }

    @ModifyReturnValue(method = "createAnimationState", at = @At("RETURN"))
    private SpriteContents.AnimationState legacyRendererWaterAnimationHook(SpriteContents.AnimationState state, GpuBufferSlice uboSlice, int spriteUboSize) {
        LegacyWater.onAnimationCreated((SpriteContents) (Object) this, state);
        return state;
    }
}
