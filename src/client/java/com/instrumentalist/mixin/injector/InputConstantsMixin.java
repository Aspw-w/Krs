package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.utils.GuiInputBlocker;
import com.mojang.blaze3d.platform.InputConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InputConstants.class)
public abstract class InputConstantsMixin {

    @Inject(method = "isKeyDown", at = @At("HEAD"), cancellable = true)
    private static void blockMovementKeyPollingWhenImguiCapturesKeyboard(int keyCode, CallbackInfoReturnable<Boolean> cir) {
        if (GuiInputBlocker.shouldBlockMovementKey(keyCode))
            cir.setReturnValue(false);
    }
}
