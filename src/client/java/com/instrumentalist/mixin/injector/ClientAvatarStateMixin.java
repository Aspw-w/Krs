package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.utils.math.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientAvatarState;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ClientAvatarState.class)
public abstract class ClientAvatarStateMixin {
    @Unique
    private boolean krs$isSelf() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && player.avatarState() == (Object) this;
    }

    @ModifyConstant(method = "updateBob", constant = @Constant(floatValue = 0.4F))
    private float krs$timerIndependentBob(float original) {
        return this.krs$isSelf() ? TimerUtil.smoothing(original) : original;
    }

    @ModifyVariable(method = "addWalkDistance", at = @At("HEAD"), argsOnly = true)
    private float krs$timerIndependentWalkDistance(float added) {
        return this.krs$isSelf() ? added * TimerUtil.realTickStep() : added;
    }
}
