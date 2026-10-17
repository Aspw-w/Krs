package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.render.WorldTime;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.ClientClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Since 26.3 clock time is read from each {@link ClientClockManager.ClientClockInstance} instead of
 * {@code ClientClockManager#getTotalTicks}.
 */
@Mixin(ClientClockManager.ClientClockInstance.class)
public abstract class ClientClockManagerMixin {

    @ModifyReturnValue(at = @At("RETURN"), method = "totalTicks")
    private long worldTimeHook(long original) {
        Long timeTicks = WorldTime.getActiveTimeTicks();
        return timeTicks != null ? timeTicks : original;
    }
}
