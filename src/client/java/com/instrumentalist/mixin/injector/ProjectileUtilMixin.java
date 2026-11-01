package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.combat.HitBoxes;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ProjectileUtil.class)
public abstract class ProjectileUtilMixin {
    @ModifyVariable(
            method = "getManyEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;FLnet/minecraft/world/level/ClipContext$Block;ZZ)Ljava/util/Collection;",
            at = @At("HEAD"),
            argsOnly = true
    )
    private static AABB hitBoxesManySearchAreaHook(AABB area, @Local(argsOnly = true) Entity source) {
        if (source == null || source != Minecraft.getInstance().player)
            return area;
        float expansion = HitBoxes.searchExpansion();
        return expansion > 0f ? area.inflate(expansion) : area;
    }

    @ModifyExpressionValue(
            method = "getManyEntityHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;FLnet/minecraft/world/level/ClipContext$Block;ZZ)Ljava/util/Collection;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;", ordinal = 0)
    )
    private static AABB hitBoxesManyEntityBoxHook(AABB box, @Local(argsOnly = true) Entity source, @Local(ordinal = 1) Entity entity) {
        if (source == null || source != Minecraft.getInstance().player)
            return box;
        float extra = HitBoxes.extraRadius(entity);
        return extra > 0f ? box.inflate(extra) : box;
    }
}
