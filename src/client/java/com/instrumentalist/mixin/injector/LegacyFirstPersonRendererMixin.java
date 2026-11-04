package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.render.LegacyRenderer;
import com.instrumentalist.mixin.oringo.ILegacySwing;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FirstPersonHandsAndItemsRenderer.class, priority = 500)
public abstract class LegacyFirstPersonRendererMixin {

    @Shadow
    protected abstract void renderPlayerArm(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, float inverseArmHeight, float attackValue, HumanoidArm arm, PlayerRenderState playerState);

    @Shadow
    protected abstract void renderOneHandedMap(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, float inverseArmHeight, HumanoidArm arm, float attackValue, ItemStack map, PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state);

    @Shadow
    protected abstract void renderTwoHandedMap(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, float xRot, float inverseArmHeight, float attackValue, PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state);

    @Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void legacyRendererFirstPersonHook(float partialTicks, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
        AvatarRenderState avatar = playerState.avatarRenderState;
        if (avatar == null || !LegacyRenderer.isFirstPersonActive()) return;

        ci.cancel();

        if (state.isScoping) return;

        LocalPlayer player = Minecraft.getInstance().player;
        float f = player != null ? ((ILegacySwing) player).krs$getLegacySwing(partialTicks) : avatar.swingAnimation;
        InteractionHand enumhand = state.attackHand;
        float f1 = avatar.xRot;
        boolean flag = true;
        boolean flag1 = true;

        if (avatar.isUsingItem) {
            ItemStack itemstack = avatar.useItemHand == InteractionHand.MAIN_HAND ? state.mainHandItem : state.offHandItem;

            if (itemstack.is(Items.BOW)) {
                flag = avatar.useItemHand == InteractionHand.MAIN_HAND;
                flag1 = !flag;
            }
        }

        poseStack.rotateDegrees(Axis.XP, ((player != null ? player.getXRot() : state.viewXRot) - state.xBob) * 0.1F);
        poseStack.rotateDegrees(Axis.YP, ((player != null ? player.getYRot() : state.viewYRot) - state.yBob) * 0.1F);

        if (flag) {
            float f3 = enumhand == InteractionHand.MAIN_HAND ? f : 0.0F;
            float f5 = 1.0F - Mth.lerp(partialTicks, state.oldMainHandHeight, state.mainHandHeight);
            krs$renderItemInFirstPerson(playerState, state, avatar, partialTicks, f1, InteractionHand.MAIN_HAND, f3, state.mainHandItem, f5, poseStack, submitNodeCollector);
        }

        if (flag1) {
            float f4 = enumhand == InteractionHand.OFF_HAND ? f : 0.0F;
            float f6 = 1.0F - Mth.lerp(partialTicks, state.oldOffHandHeight, state.offHandHeight);
            krs$renderItemInFirstPerson(playerState, state, avatar, partialTicks, f1, InteractionHand.OFF_HAND, f4, state.offHandItem, f6, poseStack, submitNodeCollector);
        }
    }

    @Unique
    private void krs$renderItemInFirstPerson(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, AvatarRenderState avatar, float partialTicks, float pitch, InteractionHand hand, float swingProgress, ItemStack stack, float equippedProgress, PoseStack poseStack, SubmitNodeCollector submitNodeCollector) {
        boolean flag = hand == InteractionHand.MAIN_HAND;
        HumanoidArm side = flag ? avatar.mainArm : avatar.mainArm.getOpposite();
        int light = avatar.lightCoords;
        poseStack.pushPose();

        if (stack.isEmpty()) {
            if (flag && !avatar.isInvisible)
                this.renderPlayerArm(poseStack, submitNodeCollector, light, equippedProgress, swingProgress, side, playerState);
        } else if (stack.has(DataComponents.MAP_ID)) {
            if (flag && state.offHandItem.isEmpty())
                this.renderTwoHandedMap(poseStack, submitNodeCollector, light, pitch, equippedProgress, swingProgress, playerState, state);
            else
                this.renderOneHandedMap(poseStack, submitNodeCollector, light, equippedProgress, side, swingProgress, stack, playerState, state);
        } else {
            boolean flag1 = side == HumanoidArm.RIGHT;
            int useDuration = flag ? state.mainHandUseDuration : state.offHandUseDuration;

            if (avatar.isUsingItem && state.useItemRemainingTicks > 0 && avatar.useItemHand == hand) {
                int j = flag1 ? 1 : -1;

                switch (stack.getUseAnimation()) {
                    case EAT, DRINK -> {
                        krs$transformEatFirstPerson(poseStack, partialTicks, side, state.useItemRemainingTicks, useDuration);
                        krs$transformSideFirstPerson(poseStack, side, equippedProgress);
                    }
                    case BOW -> {
                        krs$transformSideFirstPerson(poseStack, side, equippedProgress);
                        poseStack.translate((float) j * -0.2785682F, 0.18344387F, 0.15731531F);
                        poseStack.rotateDegrees(Axis.XP, -13.935F);
                        poseStack.rotateDegrees(Axis.YP, (float) j * 35.3F);
                        poseStack.rotateDegrees(Axis.ZP, (float) j * -9.785F);
                        float f5 = (float) useDuration - ((float) state.useItemRemainingTicks - partialTicks + 1.0F);
                        float f6 = f5 / 20.0F;
                        f6 = (f6 * f6 + f6 * 2.0F) / 3.0F;

                        if (f6 > 1.0F) f6 = 1.0F;

                        if (f6 > 0.1F) {
                            float f7 = Mth.sin((f5 - 0.1F) * 1.3F);
                            float f3 = f6 - 0.1F;
                            float f4 = f7 * f3;
                            poseStack.translate(f4 * 0.0F, f4 * 0.004F, f4 * 0.0F);
                        }

                        poseStack.translate(f6 * 0.0F, f6 * 0.0F, f6 * 0.04F);
                        poseStack.scale(1.0F, 1.0F, 1.0F + f6 * 0.2F);
                        poseStack.rotateDegrees(Axis.YN, (float) j * 45.0F);
                    }
                    default -> krs$transformSideFirstPerson(poseStack, side, equippedProgress);
                }
            } else {
                float f = -0.4F * Mth.sin(Mth.sqrt(swingProgress) * (float) Math.PI);
                float f1 = 0.2F * Mth.sin(Mth.sqrt(swingProgress) * ((float) Math.PI * 2F));
                float f2 = -0.2F * Mth.sin(swingProgress * (float) Math.PI);
                int i = flag1 ? 1 : -1;
                poseStack.translate((float) i * f, f1, f2);
                krs$transformSideFirstPerson(poseStack, side, equippedProgress);
                krs$transformFirstPerson(poseStack, side, swingProgress);
            }

            (flag ? state.mainHandRenderState : state.offHandRenderState).submit(poseStack, submitNodeCollector, light, OverlayTexture.NO_OVERLAY, 0);
        }

        poseStack.popPose();
    }

    @Unique
    private static void krs$transformEatFirstPerson(PoseStack poseStack, float partialTicks, HumanoidArm side, int itemInUseCount, int maxItemUseDuration) {
        float f = (float) itemInUseCount - partialTicks + 1.0F;
        float f1 = f / (float) maxItemUseDuration;

        if (f1 < 0.8F) {
            float f2 = Mth.abs(Mth.cos(f / 4.0F * (float) Math.PI) * 0.1F);
            poseStack.translate(0.0F, f2, 0.0F);
        }

        float f3 = 1.0F - (float) Math.pow((double) f1, 27.0D);
        int i = side == HumanoidArm.RIGHT ? 1 : -1;
        poseStack.translate(f3 * 0.6F * (float) i, f3 * -0.5F, f3 * 0.0F);
        poseStack.rotateDegrees(Axis.YP, (float) i * f3 * 90.0F);
        poseStack.rotateDegrees(Axis.XP, f3 * 10.0F);
        poseStack.rotateDegrees(Axis.ZP, (float) i * f3 * 30.0F);
    }

    @Unique
    private static void krs$transformFirstPerson(PoseStack poseStack, HumanoidArm side, float swingProgress) {
        int i = side == HumanoidArm.RIGHT ? 1 : -1;
        float f = Mth.sin(swingProgress * swingProgress * (float) Math.PI);
        poseStack.rotateDegrees(Axis.YP, (float) i * (45.0F + f * -20.0F));
        float f1 = Mth.sin(Mth.sqrt(swingProgress) * (float) Math.PI);
        poseStack.rotateDegrees(Axis.ZP, (float) i * f1 * -20.0F);
        poseStack.rotateDegrees(Axis.XP, f1 * -80.0F);
        poseStack.rotateDegrees(Axis.YP, (float) i * -45.0F);
    }

    @Unique
    private static void krs$transformSideFirstPerson(PoseStack poseStack, HumanoidArm side, float equippedProgress) {
        int i = side == HumanoidArm.RIGHT ? 1 : -1;
        poseStack.translate((float) i * 0.56F, -0.52F + equippedProgress * -0.6F, -0.72F);
    }
}
