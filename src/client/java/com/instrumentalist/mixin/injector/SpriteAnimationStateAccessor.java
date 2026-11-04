package com.instrumentalist.mixin.injector;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SpriteContents.AnimationState.class)
public interface SpriteAnimationStateAccessor {

    @Accessor("frameTexturesByIndex")
    Int2ObjectMap<GpuTextureView> krs$getFrameTexturesByIndex();

    @Accessor("isDirty")
    void krs$setDirty(boolean dirty);
}
