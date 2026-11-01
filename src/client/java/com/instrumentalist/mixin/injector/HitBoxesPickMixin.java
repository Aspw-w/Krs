package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.combat.HitBoxes;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LocalPlayer.class)
public abstract class HitBoxesPickMixin {
    @ModifyArg(
            method = "pick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/ProjectileUtil;getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;"),
            index = 3
    )
    private static AABB hitBoxesSearchAreaHook(AABB box) {
        float expansion = HitBoxes.searchExpansion();
        return expansion > 0f ? box.inflate(expansion) : box;
    }
}
