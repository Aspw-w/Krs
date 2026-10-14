package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.render.WorldTime;
import net.minecraft.client.ClientClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Since 26.3 clock time is read from each {@link ClientClockManager.ClientClockInstance} instead of
 * {@code ClientClockManager#getTotalTicks}.
 */
@Mixin(ClientClockManager.ClientClockInstance.class)
public abstract class ClientClockManagerMixin {

    @Inject(at = @At("HEAD"), method = "totalTicks", cancellable = true)
    private void worldTimeHook(CallbackInfoReturnable<Long> cir) {
        Long timeTicks = WorldTime.getActiveTimeTicks();
        if (timeTicks != null)
            cir.setReturnValue(timeTicks);
    }
}
