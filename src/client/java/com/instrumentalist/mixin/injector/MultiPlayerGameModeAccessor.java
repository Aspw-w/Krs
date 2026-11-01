package com.instrumentalist.mixin.injector;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MultiPlayerGameMode.class)
public interface MultiPlayerGameModeAccessor {
    @Accessor("destroyProgress")
    float krs$getDestroyProgress();

    @Accessor("destroyBlockPos")
    BlockPos krs$getDestroyBlockPos();

    @Accessor("isDestroying")
    boolean krs$isDestroying();
}
