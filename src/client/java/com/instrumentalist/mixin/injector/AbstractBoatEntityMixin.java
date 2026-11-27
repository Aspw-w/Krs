package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.movement.EntityControl;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(AbstractBoat.class)
public abstract class AbstractBoatEntityMixin {

    @Shadow private boolean inputLeft;
    @Shadow private boolean inputRight;
    @Shadow private boolean inputUp;
    @Shadow private boolean inputDown;
    @Shadow private float deltaRotation;

    @ModifyArgs(method = "controlBoat", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/vehicle/boat/AbstractBoat;setPaddleState(ZZ)V"))
    private void updatePaddlesHook(Args args) {
        AbstractBoat boat = (AbstractBoat) (Object) this;
        args.set(0, EntityControl.hookUpdatePaddles(boat, args.get(0)));
        args.set(1, EntityControl.hookUpdatePaddles(boat, args.get(1)));
    }

    @Inject(method = "setInput", at = @At("HEAD"), cancellable = true)
    private void stopEntityFlyBoatSteering(boolean pressingLeft, boolean pressingRight, boolean pressingForward, boolean pressingBack, CallbackInfo ci) {
        if (!EntityControl.shouldIgnoreBoatSteering((AbstractBoat) (Object) this))
            return;

        this.inputLeft = false;
        this.inputRight = false;
        this.inputUp = pressingForward;
        this.inputDown = pressingBack;
        this.deltaRotation = 0.0f;
        ci.cancel();
    }
}
