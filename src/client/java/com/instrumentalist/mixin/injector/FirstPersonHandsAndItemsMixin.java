package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.render.LegacyRenderer;
import com.instrumentalist.krs.hacks.features.render.OldHitting;
import com.instrumentalist.krs.hacks.features.render.ViewModel;
import com.instrumentalist.krs.utils.entity.PlayerUtil;
import com.instrumentalist.krs.utils.render.FirstPersonItemOverrides;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * First person held item state. Split out of {@code ItemInHandRenderer} in 26.3; the rendering half lives in
 * {@link HeldItemRendererMixin}.
 */
@Mixin(FirstPersonHandsAndItems.class)
public abstract class FirstPersonHandsAndItemsMixin {

    @Shadow private ItemStack mainHandItem;

    @Shadow private ItemStack offHandItem;

    @Shadow private float mainHandHeight;

    @Shadow private float oMainHandHeight;

    @Shadow private float offHandHeight;

    @Shadow private float oOffHandHeight;

    @Shadow protected abstract boolean shouldInstantlyReplaceVisibleItem(ItemStack currentlyVisibleItem, ItemStack expectedItem, LocalPlayer player);

    @Unique
    private boolean shouldKeepOldEatSwingItemVisible(LocalPlayer player) {
        return player != null
                && ModuleManager.getModuleState(ViewModel.class)
                && ViewModel.oldEatSwing.get()
                && player.isUsingItem()
                && player.getUseItem().getUseAnimation() != ItemUseAnimation.NONE;
    }

    @Unique
    private ItemStack getFirstPersonSpoofStack(LocalPlayer player) {
        Integer spoofSlot = PlayerUtil.INSTANCE.getSpoofSlot();
        if (player == null || spoofSlot == null || spoofSlot < 0 || spoofSlot > 8)
            return null;

        return player.getInventory().getItem(spoofSlot);
    }

    @Unique
    private ItemStack getMainHandTarget(LocalPlayer player) {
        if (OldHitting.shouldBlock() || shouldKeepOldEatSwingItemVisible(player))
            return player.getMainHandItem();

        if (PlayerUtil.INSTANCE.getSpoofSlot() != null) {
            ItemStack spoofedStack = getFirstPersonSpoofStack(player);
            return PlayerUtil.INSTANCE.getSpoofing() && spoofedStack != null ? spoofedStack : player.getMainHandItem();
        }

        return player.getMainHandItem();
    }

    @Unique
    private void replaceVisibleItemsWithoutEquipAnimation(LocalPlayer player) {
        if (player == null || !ViewModel.shouldSkipEquipAnimation())
            return;

        this.mainHandItem = getMainHandTarget(player);
        this.offHandItem = player.getOffhandItem();
    }

    @Unique
    private void updateOffhandHeldItems(LocalPlayer player) {
        this.oOffHandHeight = this.offHandHeight;
        ItemStack itemStack2 = player.getOffhandItem();

        if (this.shouldInstantlyReplaceVisibleItem(this.offHandItem, itemStack2, player)) {
            this.offHandItem = itemStack2;
        }

        float h = this.offHandItem != itemStack2 ? 0.0F : 1.0F;
        this.offHandHeight += Mth.clamp(h - this.offHandHeight, -0.4F, 0.4F);

        if (this.offHandHeight < 0.1F) {
            this.offHandItem = itemStack2;
        }
    }

    @Unique
    private void updateSpoofedHeldItems(LocalPlayer player, ItemStack mainHandTarget, boolean forceNoCooldown) {
        updateOffhandHeldItems(player);

        this.oMainHandHeight = this.mainHandHeight;
        if (mainHandTarget == null)
            return;

        if (forceNoCooldown) {
            this.mainHandItem = mainHandTarget;
            this.oMainHandHeight = 1.0F;
            this.mainHandHeight = 1.0F;
            return;
        }

        if (this.shouldInstantlyReplaceVisibleItem(this.mainHandItem, mainHandTarget, player))
            this.mainHandItem = mainHandTarget;

        if (player.isHandsBusy()) {
            this.mainHandHeight = Mth.clamp(this.mainHandHeight - 0.4F, 0.0F, 1.0F);
        } else {
            float targetHeight = this.mainHandItem != mainHandTarget ? 0.0F : 1.0F;
            this.mainHandHeight += Mth.clamp(targetHeight - this.mainHandHeight, -0.4F, 0.4F);
        }

        if (this.mainHandHeight < 0.1F)
            this.mainHandItem = mainHandTarget;
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void firstPersonItemSpoofHook(LocalPlayer player, CallbackInfo ci) {
        PlayerUtil.INSTANCE.tickSpoofRelease();

        if (OldHitting.shouldBlock() || shouldKeepOldEatSwingItemVisible(player)) {
            ci.cancel();

            if (player == null) return;
            updateOffhandHeldItems(player);

            this.oMainHandHeight = this.mainHandHeight;
            ItemStack itemStack = player.getMainHandItem();

            if (ItemStack.matches(this.mainHandItem, itemStack))
                this.mainHandItem = itemStack;

            this.mainHandHeight += Mth.clamp((this.mainHandItem == itemStack ? 1f : 0.0F) - this.mainHandHeight, -0.4F, 0.4F);

            if (this.mainHandHeight < 0.1F)
                this.mainHandItem = itemStack;

            replaceVisibleItemsWithoutEquipAnimation(player);
        } else if (PlayerUtil.INSTANCE.getSpoofSlot() != null && player != null) {
            ci.cancel();

            ItemStack spoofedStack = getFirstPersonSpoofStack(player);
            ItemStack itemStack = PlayerUtil.INSTANCE.getSpoofing() && spoofedStack != null ? spoofedStack : player.getMainHandItem();
            boolean spoofStateActive = PlayerUtil.INSTANCE.getSpoofSlot() != null;
            updateSpoofedHeldItems(player, itemStack, spoofStateActive);
            replaceVisibleItemsWithoutEquipAnimation(player);
        }
    }

    @Unique
    private static boolean krs$legacyEquals(ItemStack a, ItemStack b) {
        return a == b || a.isEmpty() && b.isEmpty();
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void legacyRendererSwapScaleHook(LocalPlayer player, float partialTicks, FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
        if (!LegacyRenderer.isEquipAnimationActive()) return;

        state.mainHandSwapScale = 1.0F;
        state.offHandSwapScale = 1.0F;
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void legacyRendererEquipHook(LocalPlayer player, CallbackInfo ci) {
        if (player == null || !LegacyRenderer.isEquipAnimationActive()) return;

        ci.cancel();
        this.oMainHandHeight = this.mainHandHeight;
        this.oOffHandHeight = this.offHandHeight;
        ItemStack itemstack = getMainHandTarget(player);
        ItemStack itemstack1 = player.getOffhandItem();

        if (player.isHandsBusy()) {
            LegacyRenderer.tickCooledAttackStrength(player);
            this.mainHandHeight = Mth.clamp(this.mainHandHeight - 0.4F, 0.0F, 1.0F);
            this.offHandHeight = Mth.clamp(this.offHandHeight - 0.4F, 0.0F, 1.0F);
        } else {
            float f = LegacyRenderer.tickCooledAttackStrength(player);
            this.mainHandHeight += Mth.clamp((krs$legacyEquals(this.mainHandItem, itemstack) ? f * f * f : 0.0F) - this.mainHandHeight, -0.4F, 0.4F);
            this.offHandHeight += Mth.clamp((float) (krs$legacyEquals(this.offHandItem, itemstack1) ? 1 : 0) - this.offHandHeight, -0.4F, 0.4F);
        }

        if (this.mainHandHeight < 0.1F)
            this.mainHandItem = itemstack;

        if (this.offHandHeight < 0.1F)
            this.offHandItem = itemstack1;

        if (this.krs$pendingMainHandReset) {
            this.mainHandHeight = 0.0F;
            this.krs$pendingMainHandReset = false;
        }

        if (this.krs$pendingOffHandReset) {
            this.offHandHeight = 0.0F;
            this.krs$pendingOffHandReset = false;
        }
    }

    @Unique
    private boolean krs$pendingMainHandReset;

    @Unique
    private boolean krs$pendingOffHandReset;

    @Inject(method = "itemUsed", at = @At("HEAD"), cancellable = true)
    private void legacyRendererItemUsedHook(InteractionHand hand, CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (!LegacyRenderer.isEquipAnimationActive() || player == null || OldHitting.shouldBlock()
                || shouldKeepOldEatSwingItemVisible(player) || PlayerUtil.INSTANCE.getSpoofSlot() != null)
            return;

        ci.cancel();

        if (hand == InteractionHand.MAIN_HAND)
            this.krs$pendingMainHandReset = true;
        else
            this.krs$pendingOffHandReset = true;
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void noEquipAnimationTickHook(LocalPlayer player, CallbackInfo ci) {
        replaceVisibleItemsWithoutEquipAnimation(player);
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void noEquipAnimationExtractHook(LocalPlayer player, float partialTicks, FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
        replaceVisibleItemsWithoutEquipAnimation(player);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void extractSpoofedItem(LocalPlayer player, float partialTicks, FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
        ItemStack spoofedStack = getFirstPersonSpoofStack(player);
        if (spoofedStack == null || spoofedStack.isEmpty()) {
            FirstPersonItemOverrides.clearSpoof();
            return;
        }

        ItemDisplayContext displayContext = player.getMainArm() == HumanoidArm.RIGHT
                ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(
                FirstPersonItemOverrides.beginSpoofExtraction(spoofedStack),
                spoofedStack,
                displayContext,
                player.level(),
                player,
                player.getId() + displayContext.ordinal()
        );
    }
}
