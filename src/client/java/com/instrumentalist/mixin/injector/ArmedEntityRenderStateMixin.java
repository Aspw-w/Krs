package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.utils.IMinecraft;
import com.instrumentalist.krs.utils.entity.PlayerUtil;
import com.instrumentalist.krs.utils.render.GuiEntityRenderGuard;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArmedEntityRenderState.class)
public abstract class ArmedEntityRenderStateMixin implements IMinecraft {

    @Inject(method = "extractArmedEntityRenderState", at = @At("RETURN"))
    private static void hookThirdPersonItemSpoof(LivingEntity entity, ArmedEntityRenderState state, ItemModelResolver itemModelResolver, float tickDelta, CallbackInfo ci) {
        if (GuiEntityRenderGuard.isActive())
            return;

        var player = mc.player;
        Integer spoofSlot = PlayerUtil.INSTANCE.getSpoofSlot();
        if (player == null || entity != player || !PlayerUtil.INSTANCE.getSpoofing() || spoofSlot == null || spoofSlot < 0 || spoofSlot > 8)
            return;

        ItemStack spoofedStack = player.getInventory().getItem(spoofSlot);
        ItemStack visibleStack = spoofedStack.copy();
        if (state.mainArm == HumanoidArm.RIGHT) {
            itemModelResolver.updateForLiving(state.rightHandItemState, visibleStack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, entity);
            state.rightHandItemStack = visibleStack;
        } else {
            itemModelResolver.updateForLiving(state.leftHandItemState, visibleStack, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, entity);
            state.leftHandItemStack = visibleStack;
        }
    }
}
