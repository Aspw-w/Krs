package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.utils.math.TimerUtil;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DeltaTracker.Timer.class)
public abstract class RenderTickCounterDynamicMixin {
    @Shadow
    private float deltaTicks;

    @Shadow
    private float deltaTickResidual;

    @ModifyReturnValue(method = "advanceGameTime(J)I", at = @At("RETURN"))
    private int krs$scheduleTimerTicks(int original) {
        return TimerUtil.scheduleTicks(original, this.deltaTicks, this.deltaTickResidual);
    }
}
