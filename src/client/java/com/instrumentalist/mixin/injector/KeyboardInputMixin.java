package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.combat.Velocity;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.movement.MovementFix;
import com.instrumentalist.krs.hacks.features.player.LookTP;
import com.instrumentalist.krs.hacks.features.player.Scaffold;
import com.instrumentalist.krs.hacks.features.combat.WTap;
import com.instrumentalist.krs.utils.GuiInputBlocker;
import com.instrumentalist.krs.utils.move.InputUtil;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void fixMovementAndStopWhenImguiCapturesKeyboard(CallbackInfo ci) {
        ClientInput input = (ClientInput) (Object) this;
        LookTP lookTP = ModuleManager.getModule(LookTP.class);

        if (lookTP != null && lookTP.shouldStopClientInput()) {
            InputUtil.stop(input);
            MovementFix.updateInput(input);
            Scaffold.hookEdgeSafeSneakInput(input);
            return;
        }

        if (!GuiInputBlocker.shouldBlockGameMovement()) {
            MovementFix.updateInput(input);
            Scaffold.hookEdgeSafeSneakInput(input);
            Velocity.hookJumpResetInput(input);
            Scaffold.hookTellyJumpInput(input);
            Scaffold.hookAutoInputFix(input);
            WTap.hookWTapInput(input);
            return;
        }

        GuiInputBlocker.releaseMovementKeys();
        InputUtil.stop(input);
        MovementFix.updateInput(input);
        Scaffold.hookEdgeSafeSneakInput(input);
    }
}
