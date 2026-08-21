package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.MotionEvent;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.exploit.PortalScreen;
import com.instrumentalist.krs.hacks.features.movement.*;
import com.instrumentalist.krs.hacks.features.player.Phase;
import com.instrumentalist.krs.hacks.features.player.Freecam;
import com.instrumentalist.krs.hacks.features.render.EntityYawFix;
import com.instrumentalist.krs.hacks.features.render.Freelook;
import com.instrumentalist.krs.utils.move.InputUtil;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

@Mixin(LocalPlayer.class)
public abstract class ClientPlayerEntityMixin extends Player {
    @Unique
    private static ClientInput krs$freecamRealInput;

    @Unique
    private static int krs$freecamInputSwapDepth;

    public ClientPlayerEntityMixin(Level world, GameProfile gameProfile) {
        super(world, gameProfile);
    }

    @Shadow
    protected abstract void sendIsSprintingIfNeeded();

    @Shadow
    private double xLast;
    @Shadow
    private double yLast;
    @Shadow
    private double zLast;
    @Shadow
    private float yRotLast;
    @Shadow
    private float xRotLast;
    @Shadow
    private boolean lastOnGround;
    @Final
    @Shadow
    public ClientPacketListener connection;

    @Shadow
    protected abstract boolean isControlledCamera();

    @Shadow
    public abstract boolean isMovingSlowly();

    @Shadow
    private boolean isSlowDueToUsingItem() {
        return false;
    }

    @Shadow
    private boolean isSprintingPossible(boolean isFlying) {
        return false;
    }

    @Shadow
    private int positionReminder;
    @Shadow
    private boolean autoJumpEnabled;

    @Shadow
    private boolean lastHorizontalCollision;

    @Shadow
    @Final
    protected Minecraft minecraft;

    @Shadow public ClientInput input;

    @Override
    public void turn(double deltaYaw, double deltaPitch) {
        if (ModuleManager.getModuleState(Freecam.class) && Freecam.getCanFly()) {
            Freecam.turn(deltaYaw, deltaPitch);
            return;
        }

        if (ModuleManager.getModuleState(Freelook.class) && Freelook.isActive()) {
            Freelook.turn(deltaYaw, deltaPitch);
            return;
        }

        super.turn(deltaYaw, deltaPitch);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void freecamTickInputHead(CallbackInfo ci) {
        krs$swapFreecamInput();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void freecamTickInputReturn(CallbackInfo ci) {
        krs$restoreFreecamInput();
    }

    @Inject(method = "rideTick", at = @At("HEAD"))
    private void freecamRideTickInputHead(CallbackInfo ci) {
        krs$swapFreecamInput();
    }

    @Inject(method = "rideTick", at = @At("RETURN"))
    private void freecamRideTickInputReturn(CallbackInfo ci) {
        krs$restoreFreecamInput();
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void freecamStopInput(CallbackInfo ci) {
        if (ModuleManager.getModuleState(Freecam.class) && Freecam.getCanFly())
            InputUtil.stop(this.input);
    }

    @Inject(method = "isShiftKeyDown()Z", at = @At("HEAD"), cancellable = true)
    private void freecamShiftKeyHook(CallbackInfoReturnable<Boolean> cir) {
        if (ModuleManager.getModuleState(Freecam.class) && Freecam.getCanFly())
            cir.setReturnValue(false);
    }

    @Inject(method = "raycastHitResult(FLnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/phys/HitResult;", at = @At("HEAD"), cancellable = true)
    private void freecamRaycastHook(float tickDelta, Entity cameraEntity, CallbackInfoReturnable<HitResult> cir) {
        if (!ModuleManager.getModuleState(Freecam.class) || !Freecam.getCanFly())
            return;

        Vec3 camStart = Freecam.getCamPos(tickDelta);
        Vec3 camEnd = camStart.add(Freecam.getScaledCamDir(this.blockInteractionRange()));
        cir.setReturnValue(this.level().clip(new ClipContext(
                camStart,
                camEnd,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                (Entity) (Object) this
        )));
    }

    @Unique
    private void krs$swapFreecamInput() {
        if (!ModuleManager.getModuleState(Freecam.class) || !Freecam.getCanFly())
            return;

        krs$freecamInputSwapDepth++;
        if (krs$freecamInputSwapDepth > 1)
            return;

        krs$freecamRealInput = this.input;
        this.input.tick();
        this.input = new ClientInput();
    }

    @Unique
    private void krs$restoreFreecamInput() {
        if (krs$freecamInputSwapDepth > 0)
            krs$freecamInputSwapDepth--;

        if (krs$freecamInputSwapDepth > 0 || krs$freecamRealInput == null)
            return;

        this.input = krs$freecamRealInput;
        krs$freecamRealInput = null;
    }

    @ModifyExpressionValue(method = "handlePortalTransitionEffect", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;isAllowedInPortal()Z"))
    private boolean portalScreenHook(boolean original) {
        return PortalScreen.screenOpenLimitHook(original);
    }

    @ModifyExpressionValue(method = "modifyInput(Lnet/minecraft/world/phys/Vec2;)Lnet/minecraft/world/phys/Vec2;", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;itemUseSpeedMultiplier()F"))
    private float noSlowItemUseMultiplierHook(float original) {
        return NoSlow.shouldNoSlowUseItem() ? 1.0F : original;
    }

    @ModifyExpressionValue(method = "modifyInput(Lnet/minecraft/world/phys/Vec2;)Lnet/minecraft/world/phys/Vec2;", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isMovingSlowly()Z"))
    private boolean noSlowSneakMultiplierHook(boolean original) {
        return original && !NoSlow.shouldNoSlowSneak();
    }

    @WrapOperation(method = "getViewYRot(F)F", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;getViewYRot(F)F"))
    private float entityHorizontalFixYawHook(LocalPlayer instance, float delta, Operation<Float> original) {
        if (ModuleManager.getModuleState(EntityYawFix.class))
            return delta == 1.0F ? this.yRotO : Mth.lerp(delta, this.yRotO, this.getYRot());

        return original.call(instance, delta);
    }

    @ModifyExpressionValue(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;hasForwardImpulse()Z"))
    private boolean movementFixSprintStartImpulse(boolean original) {
        if (MovementFix.shouldFixSprintCondition() || Sprint.shouldSprintOmnidirectional())
            return MovementFix.hasForwardImpulseForSprint(this.input);

        return original;
    }

    @ModifyReturnValue(method = "canStartSprinting()Z", at = @At("RETURN"))
    private boolean canStartSprintingHook(boolean original) {
        if (original)
            return original;

        boolean noSlowActive = NoSlow.noSlowHook();
        boolean shouldFixForwardImpulse = MovementFix.shouldFixSprintCondition() || Sprint.shouldSprintOmnidirectional();

        if (!noSlowActive && !shouldFixForwardImpulse)
            return false;

        if (this.isSprinting() || !MovementFix.hasForwardImpulseForSprint(this.input) || !this.isSprintingPossible(this.getAbilities().flying))
            return false;

        if (this.isSlowDueToUsingItem() && !noSlowActive)
            return false;

        if (this.isFallFlying() && !this.isUnderWater())
            return false;

        return !this.isMovingSlowly() || this.isUnderWater() || noSlowActive && NoSlow.shouldNoSlowSneak();
    }

    @ModifyExpressionValue(method = {"shouldStopRunSprinting", "shouldStopSwimSprinting"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;hasForwardImpulse()Z"))
    private boolean movementFixSprintStopImpulse(boolean original) {
        if (MovementFix.shouldFixSprintCondition() || Sprint.shouldSprintOmnidirectional())
            return MovementFix.hasForwardImpulseForSprint(this.input);

        return original;
    }

    @Inject(method = "moveTowardsClosestSpace(DD)V", at = @At("HEAD"), cancellable = true)
    private void hookNoPush(double x, double z, CallbackInfo ci) {
        if (ModuleManager.getModuleState(NoPush.class) || ModuleManager.getModuleState(Phase.class))
            ci.cancel();
    }

    @ModifyReturnValue(method = "getJumpRidingScale", at = @At("RETURN"))
    private float horseJumpHook(float original) {
        return PerfectHorseJump.modifiedHorseJump(original);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void invMoveHook(CallbackInfo ci) {
        InventoryMove.moveFreely();
    }

    @Inject(method = "sendPosition", at = @At("HEAD"), cancellable = true)
    private void krs$sendPosition(CallbackInfo ci) {
        ci.cancel();

        this.sendIsSprintingIfNeeded();

        if (this.isControlledCamera()) {
            MotionEvent event = new MotionEvent(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot(), this.onGround(), false, false, false);

            double initialXDiff = event.x - this.xLast;
            double initialYDiff = event.y - this.yLast;
            double initialZDiff = event.z - this.zLast;
            double initialYawDiff = (double) (event.yaw - this.yRotLast);
            double initialPitchDiff = (double) (event.pitch - this.xRotLast);
            ++this.positionReminder;
            boolean initialPositionChanged = Mth.lengthSquared(initialXDiff, initialYDiff, initialZDiff) > Mth.square(2.0E-4) || this.positionReminder >= 20;
            boolean initialRotationChanged = initialYawDiff != 0.0 || initialPitchDiff != 0.0;
            float vanillaYaw = event.yaw;
            float vanillaPitch = event.pitch;

            event.isMoving = initialPositionChanged || initialRotationChanged;

            if (Client.eventManager != null) Client.eventManager.call(event);
            if (event.isCancelled()) return;
            boolean eventRotationChanged = event.yaw != vanillaYaw || event.pitch != vanillaPitch;
            if (Client.rotationManager != null && (Client.rotationManager.isRotating() || eventRotationChanged)) {
                float[] fixedRotation = Client.rotationManager.normalizeRotation(event.yaw, event.pitch, this.yRotLast, this.xRotLast);
                event.yaw = fixedRotation[0];
                event.pitch = fixedRotation[1];
            }

            double finalXDiff = event.x - this.xLast;
            double finalYDiff = event.y - this.yLast;
            double finalZDiff = event.z - this.zLast;
            double finalYawDiff = (double) (event.yaw - this.yRotLast);
            double finalPitchDiff = (double) (event.pitch - this.xRotLast);
            boolean positionChanged = Mth.lengthSquared(finalXDiff, finalYDiff, finalZDiff) > Mth.square(2.0E-4) || this.positionReminder >= 20;
            boolean rotationChanged = finalYawDiff != 0.0 || finalPitchDiff != 0.0;
            boolean movementSuppressed = (initialPositionChanged || initialRotationChanged) && !event.isMoving;
            boolean shouldSendMovePacket = !movementSuppressed && (positionChanged || rotationChanged);

            if (positionChanged && rotationChanged && shouldSendMovePacket) {
                this.connection.send(new ServerboundMovePlayerPacket.PosRot(event.x, event.y, event.z, event.yaw, event.pitch, event.onGround, this.horizontalCollision));
            } else if (positionChanged && shouldSendMovePacket) {
                this.connection.send(new ServerboundMovePlayerPacket.Pos(event.x, event.y, event.z, event.onGround, this.horizontalCollision));
            } else if (rotationChanged && shouldSendMovePacket) {
                this.connection.send(new ServerboundMovePlayerPacket.Rot(event.yaw, event.pitch, event.onGround, this.horizontalCollision));
            } else if (this.lastOnGround != event.onGround || this.lastHorizontalCollision != this.horizontalCollision) {
                this.connection.send(new ServerboundMovePlayerPacket.StatusOnly(event.onGround, this.horizontalCollision));
            }

            if (positionChanged) {
                this.xLast = event.x;
                this.yLast = event.y;
                this.zLast = event.z;
                this.positionReminder = 0;
            }

            if (rotationChanged) {
                this.yRotLast = event.yaw;
                this.xRotLast = event.pitch;
            }

            this.lastOnGround = event.onGround;
            this.lastHorizontalCollision = this.horizontalCollision;
            this.autoJumpEnabled = (Boolean) this.minecraft.options.autoJump().get();

            MotionEvent postEvent = new MotionEvent(event.x, event.y, event.z, event.yaw, event.pitch, event.onGround, false, false, true);
            if (Client.eventManager != null && event.callInPost) Client.eventManager.call(postEvent);

            Client.rotationManager.postMotionVisualTick();
        }
    }
}
