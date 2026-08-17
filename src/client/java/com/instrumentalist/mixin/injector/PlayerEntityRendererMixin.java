package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.utils.IMinecraft;
import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.render.ClientCape;
import com.instrumentalist.krs.hacks.features.render.OldHitting;
import com.instrumentalist.krs.hacks.features.render.Rotations;
import com.instrumentalist.krs.utils.entity.PlayerUtil;
import com.instrumentalist.krs.utils.render.GuiEntityRenderGuard;
import com.instrumentalist.mixin.oringo.IEntityRenderState;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.entity.ClientAvatarState;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class PlayerEntityRendererMixin implements IMinecraft {

    @Inject(method = "extractCapeState", at = @At("HEAD"), cancellable = true)
    private static void capeModifier(Avatar avatar, AvatarRenderState state, float tickDelta, CallbackInfo ci) {
        if (avatar instanceof LocalPlayer player && ModuleManager.getModuleState(ClientCape.class) && ClientCape.oldCapeMovement.get()) {
            ci.cancel();

            ClientAvatarState avatarState = player.avatarState();
            double d0 = avatarState.getInterpolatedCloakX(tickDelta)
                    - Mth.lerp((double)tickDelta, player.xo, player.getX());
            double d1 = avatarState.getInterpolatedCloakY(tickDelta)
                    - Mth.lerp((double)tickDelta, player.yo, player.getY());
            double d2 = avatarState.getInterpolatedCloakZ(tickDelta)
                    - Mth.lerp((double)tickDelta, player.zo, player.getZ());

            float angle = krs$getCapeBodyYaw(player, tickDelta, Mth.rotLerp(tickDelta, player.yBodyRotO, player.yBodyRot));
            double sin = Mth.sin(angle * ((float)Math.PI / 180F));
            double cos = -Mth.cos(angle * ((float)Math.PI / 180F));

            state.capeFlap = (float)d1 * 10.0F;
            state.capeFlap = Mth.clamp(state.capeFlap, -6.0F, 32.0F);

            state.capeLean = (float)(d0 * sin + d2 * cos) * 100.0F;
            if (state.capeLean < 0.0F) {
                state.capeLean = 0.0F;
            }
            state.capeLean = Mth.clamp(state.capeLean, 0.0F, 180.0F);

            state.capeLean2 = (float)(d0 * cos - d2 * sin) * 100.0F;

            float walkBob  = avatarState.getInterpolatedBob(tickDelta);
            float cameraBt = avatarState.getInterpolatedWalkDistance(tickDelta);
            state.capeFlap += Mth.sin(cameraBt * 6.0F) * 32.0F * walkBob;
        }
    }

    @ModifyExpressionValue(
            method = "extractCapeState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;rotLerp(FFF)F")
    )
    private float krs$rotationCapeYaw(float original, Avatar avatar, AvatarRenderState state, float tickDelta) {
        return krs$getCapeBodyYaw(avatar, tickDelta, original);
    }

    @Unique
    private static float krs$getCapeBodyYaw(Avatar avatar, float tickDelta, float fallback) {
        if (GuiEntityRenderGuard.isActive()
                || avatar != mc.player
                || Client.rotationManager == null
                || !Client.rotationManager.isRotating()
                || !ModuleManager.getModuleState(Rotations.class))
            return fallback;

        if (Rotations.vanilla.get())
            return Client.rotationManager.getInterpolatedBodyYaw(tickDelta);

        return Client.rotationManager.getInterpolatedYaw(tickDelta);
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("RETURN"))
    private void clientSideRotations(Avatar avatar, AvatarRenderState state, float f, CallbackInfo info) {
        ((IEntityRenderState) state).client$setEntity(avatar);

        if (!(avatar instanceof AbstractClientPlayer player)) return;
        if (player != mc.player) return;

        applyEmptyHandSpoofArmPose(state);
        applyOldHittingUseState(state);

        if (Client.rotationManager.isRotating() && ModuleManager.getModuleState(Rotations.class) && !Rotations.vanilla.get()) {
            state.yRot = 0f;
            state.bodyRot = Client.rotationManager.getInterpolatedYaw(f);
            state.xRot = Client.rotationManager.getInterpolatedPitch(f);
        }
    }

    @Unique
    private void applyOldHittingUseState(AvatarRenderState state) {
        if (mc.options.getCameraType().isFirstPerson()
                || GuiEntityRenderGuard.isActive()
                || !OldHitting.shouldBlock())
            return;

        state.isUsingItem = true;
        state.useItemHand = InteractionHand.MAIN_HAND;
        state.ticksUsingItem = Math.max(state.ticksUsingItem, 1.0F);

        if (state.mainArm == HumanoidArm.RIGHT)
            state.rightArmPose = HumanoidModel.ArmPose.BLOCK;
        else
            state.leftArmPose = HumanoidModel.ArmPose.BLOCK;
    }

    @Unique
    private void applyEmptyHandSpoofArmPose(AvatarRenderState state) {
        Integer spoofSlot = PlayerUtil.INSTANCE.getSpoofSlot();
        if (!PlayerUtil.INSTANCE.getSpoofing() || spoofSlot == null || spoofSlot < 0 || spoofSlot > 8 || mc.player == null)
            return;

        ItemStack spoofedStack = mc.player.getInventory().getItem(spoofSlot);
        if (!spoofedStack.isEmpty())
            return;

        if (state.mainArm == HumanoidArm.RIGHT)
            state.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        else
            state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
    }
}
