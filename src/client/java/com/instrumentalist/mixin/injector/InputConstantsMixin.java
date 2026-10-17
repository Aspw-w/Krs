package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.utils.GuiInputBlocker;
import com.mojang.blaze3d.platform.InputConstants;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(InputConstants.class)
public abstract class InputConstantsMixin {

    @ModifyReturnValue(method = "isKeyDown", at = @At("RETURN"))
    private static boolean blockMovementKeyPollingWhenImguiCapturesKeyboard(boolean original, int keyCode) {
        return original && !GuiInputBlocker.shouldBlockMovementKey(keyCode);
    }
}
