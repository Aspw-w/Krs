package com.instrumentalist.mixin.injector;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("autoSpinAttackTicks")
    void krs$setAutoSpinAttackTicks(int ticks);

    @Accessor("swingState")
    LivingEntity.SwingState krs$getSwingState();

    @Invoker("setLivingEntityFlag")
    void krs$invokeSetLivingEntityFlag(int flag, boolean value);
}
