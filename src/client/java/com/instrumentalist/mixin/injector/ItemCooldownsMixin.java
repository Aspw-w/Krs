package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.utils.math.TimerUtil;
import com.instrumentalist.mixin.oringo.ITimerScaledTick;
import net.minecraft.world.item.ItemCooldowns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ItemCooldowns.class)
public abstract class ItemCooldownsMixin implements ITimerScaledTick {
    @Shadow
    private int tickCount;

    @Shadow
    public abstract void tick();

    @Unique
    private float krs$preciseTickCount;

    @Unique
    private boolean krs$clientScaled;

    @Override
    public void krs$tickScaled(float step) {
        this.krs$clientScaled = true;

        if ((int) this.krs$preciseTickCount != this.tickCount)
            this.krs$preciseTickCount = this.tickCount;

        this.krs$preciseTickCount += step;
        int target = (int) this.krs$preciseTickCount;

        while (this.tickCount < target)
            this.tick();
    }

    @ModifyVariable(method = "getCooldownPercent", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float krs$scalePartialTicks(float a) {
        if (!this.krs$clientScaled || (int) this.krs$preciseTickCount != this.tickCount)
            return a;

        return (this.krs$preciseTickCount - this.tickCount) + a * TimerUtil.realTickStep();
    }
}
