package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.combat.Velocity;
import com.instrumentalist.krs.hacks.features.render.LegacyRenderer;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @WrapOperation(method = "handleSwingAnimation", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z"))
    private boolean legacyRendererServerSwingHook(LivingEntity instance, InteractionHand hand, SwingAnimation animation, boolean sendToSwingingEntity, Operation<Boolean> original) {
        if (!(instance instanceof LocalPlayer))
            return original.call(instance, hand, animation, sendToSwingingEntity);

        return LegacyRenderer.callWithoutSwing(() -> original.call(instance, hand, animation, sendToSwingingEntity));
    }

    @ModifyExpressionValue(method = "handleExplosion", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundExplodePacket;playerKnockback()Ljava/util/Optional;"))
    private Optional<Vec3> velocityExplosionHook(Optional<Vec3> original) {
        return Velocity.hookExplosionKnockback(original);
    }
}
