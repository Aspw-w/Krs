package com.instrumentalist.krs.utils.entity;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.Vec3;

public final class ExplosionDamage {
    public static final float CRYSTAL_POWER = 6.0F;
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private ExplosionDamage() {
    }

    public static float crystal(LivingEntity entity, Vec3 center) {
        return estimate(entity, center, CRYSTAL_POWER);
    }

    public static float estimate(LivingEntity entity, Vec3 center, float power) {
        if (entity == null || !entity.isAlive() || entity.isInvulnerable())
            return 0.0F;
        if (entity instanceof Player player && (player.isCreative() || player.isSpectator()))
            return 0.0F;

        double diameter = power * 2.0D;
        double distance = Math.sqrt(entity.distanceToSqr(center)) / diameter;
        if (distance > 1.0D)
            return 0.0F;

        double exposure = ServerExplosion.getSeenPercent(center, entity);
        double impact = (1.0D - distance) * exposure;
        float damage = (float) ((impact * impact + impact) / 2.0D * 7.0D * diameter + 1.0D);
        if (impact <= 0.0D)
            return 0.0F;

        if (entity instanceof Player) {
            Difficulty difficulty = entity.level().getDifficulty();
            damage = switch (difficulty) {
                case PEACEFUL -> 0.0F;
                case EASY -> Math.min(damage / 2.0F + 1.0F, damage);
                case NORMAL -> damage;
                case HARD -> damage * 1.5F;
            };
        }

        damage = CombatRules.getDamageAfterAbsorb(
                entity,
                damage,
                entity.damageSources().explosion(null, null),
                entity.getArmorValue(),
                (float) entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS)
        );

        MobEffectInstance resistance = entity.getEffect(MobEffects.RESISTANCE);
        if (resistance != null)
            damage *= Math.max(0.0F, 1.0F - (resistance.getAmplifier() + 1) * 0.2F);

        int protection = 0;
        for (EquipmentSlot slot : ARMOR) {
            ItemStack stack = entity.getItemBySlot(slot);
            protection += level(stack, Enchantments.PROTECTION) + 2 * level(stack, Enchantments.BLAST_PROTECTION);
        }
        damage = CombatRules.getDamageAfterMagicAbsorb(damage, Math.min(20, protection));
        return Math.max(0.0F, damage);
    }

    private static int level(ItemStack stack, ResourceKey<Enchantment> enchantment) {
        for (var entry : stack.getEnchantments().entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            if (holder.is(enchantment))
                return entry.getIntValue();
        }
        return 0;
    }
}
