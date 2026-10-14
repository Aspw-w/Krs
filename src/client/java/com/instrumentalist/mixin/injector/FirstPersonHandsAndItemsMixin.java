package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.render.OldHitting;
import com.instrumentalist.krs.hacks.features.render.ViewModel;
import com.instrumentalist.krs.utils.entity.PlayerUtil;
import com.instrumentalist.krs.utils.render.FirstPersonItemOverrides;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.util.Mth;
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
        } else if (PlayerUtil.INSTANCE.getSpoofSlot() != null && player != null) {
            ci.cancel();

            ItemStack spoofedStack = getFirstPersonSpoofStack(player);
            ItemStack itemStack = PlayerUtil.INSTANCE.getSpoofing() && spoofedStack != null ? spoofedStack : player.getMainHandItem();
            boolean spoofStateActive = PlayerUtil.INSTANCE.getSpoofSlot() != null;
            updateSpoofedHeldItems(player, itemStack, spoofStateActive);
        }
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
