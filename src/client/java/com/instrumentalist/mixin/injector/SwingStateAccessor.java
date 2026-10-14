package com.instrumentalist.mixin.injector;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.SwingState.class)
public interface SwingStateAccessor {
    @Accessor("animation")
    float krs$getAnimation();
}
