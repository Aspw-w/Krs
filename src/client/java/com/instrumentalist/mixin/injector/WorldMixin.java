package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.render.WorldTime;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Level.class)
public abstract class WorldMixin implements LevelAccessor, AutoCloseable {

    @ModifyReturnValue(at = @At("RETURN"), method = {"getRainLevel(F)F", "getThunderLevel(F)F"})
    private float worldTimeWeatherLevelHook(float original) {
        return WorldTime.shouldClearWeather() ? 0F : original;
    }

    @ModifyReturnValue(at = @At("RETURN"), method = {"isRaining", "isThundering", "isRainingAt"})
    private boolean worldTimeWeatherStateHook(boolean original) {
        return original && !WorldTime.shouldClearWeather();
    }

    @ModifyReturnValue(at = @At("RETURN"), method = {"getDefaultClockTime", "getOverworldClockTime"})
    private long worldTimeClockHook(long original) {
        Long timeTicks = WorldTime.getActiveTimeTicks();
        return timeTicks != null ? timeTicks : original;
    }
}
