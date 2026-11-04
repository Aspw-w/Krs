package com.instrumentalist.mixin.injector;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.MipmapStrategy;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SpriteContents.class)
public interface SpriteContentsAccessor {

    @Accessor("byMipLevel")
    NativeImage[] krs$getByMipLevel();

    @Accessor("mipmapStrategy")
    MipmapStrategy krs$getMipmapStrategy();

    @Accessor("alphaCutoffBias")
    float krs$getAlphaCutoffBias();
}
