package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.render.LegacyRenderer;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Random;

@Mixin(WeatherEffectRenderer.class)
public abstract class WeatherEffectRendererMixin {

    @Inject(method = "createRainColumnInstance", at = @At("HEAD"), cancellable = true)
    private void legacyRendererRainHook(RandomSource random, long ticks, int x, int bottomY, int topY, int z, int lightCoords, float partialTicks, CallbackInfoReturnable<WeatherEffectRenderer.ColumnInstance> cir) {
        if (!LegacyRenderer.isWeatherActive()) return;

        Random legacyRandom = new Random((long) (x * x * 3121 + x * 45238971 ^ z * z * 418711 + z * 13761));
        int offset = (int) ticks + x * x * 3121 + x * 45238971 + z * z * 418711 + z * 13761 & 31;
        double textureOffset = -((double) offset + (double) partialTicks) / 32.0D * (3.0D + legacyRandom.nextDouble());
        cir.setReturnValue(new WeatherEffectRenderer.ColumnInstance(x, z, bottomY, topY, 0.0F, (float) textureOffset, lightCoords));
    }
}
