package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.render.OldHitting;
import com.instrumentalist.krs.utils.IMinecraft;
import com.instrumentalist.krs.utils.render.GuiEntityRenderGuard;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.properties.conditional.IsUsingItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(IsUsingItem.class)
public abstract class IsUsingItemMixin implements IMinecraft {

    @ModifyReturnValue(
            method = "get(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/world/entity/LivingEntity;ILnet/minecraft/world/item/ItemDisplayContext;)Z",
            at = @At("RETURN")
    )
    private boolean krs$oldHittingThirdPersonUseState(boolean original, ItemStack stack, ClientLevel level, LivingEntity entity, int seed, ItemDisplayContext displayContext) {
        if (original
                || GuiEntityRenderGuard.isActive()
                || !(entity instanceof LocalPlayer)
                || entity != mc.player
                || stack != entity.getMainHandItem()
                || displayContext != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                && displayContext != ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || !OldHitting.shouldBlock())
            return original;

        return true;
    }
}
