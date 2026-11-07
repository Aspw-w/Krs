package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.utils.math.TimerUtil;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin {
    @ModifyExpressionValue(method = "itemCooldown", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/DeltaTracker;getGameTimeDeltaPartialTick(Z)F"))
    private float krs$selfCooldownPartialTicks(float original) {
        return TimerUtil.selfPartialTick(original);
    }
}
