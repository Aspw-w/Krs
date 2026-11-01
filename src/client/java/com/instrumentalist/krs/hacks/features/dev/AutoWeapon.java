package com.instrumentalist.krs.hacks.features.dev;

import com.instrumentalist.krs.events.features.AttackEvent;
import com.instrumentalist.krs.events.features.TickEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.entity.PlayerUtil;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.value.BooleanValue;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public class AutoWeapon extends Module {
    @Setting
    private final BooleanValue maceSmash = new BooleanValue("Mace Smash", true);

    @Setting
    private final BooleanValue onlyLiving = new BooleanValue("Only Living", true);

    @Setting
    private final BooleanValue switchBack = new BooleanValue("Switch Back", true);

    private int restoreSlot = -1;
    private int weaponSlot = -1;
    private int swapTick = Integer.MIN_VALUE;

    public AutoWeapon() {
        super("Auto Weapon", ModuleCategory.Dev, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onEnable() {
        clear();
    }

    @Override
    public void onDisable() {
        restore(true);
    }

    @Override
    public void onWorld(WorldEvent event) {
        clear();
    }

    private void clear() {
        restoreSlot = -1;
        weaponSlot = -1;
    }

    @Override
    public void onTick(TickEvent event) {
        restore(false);
    }

    @Override
    public void onAttack(AttackEvent event) {
        LocalPlayer player = mc.player;
        Entity target = event.entity;
        if (player == null || target == null || player.isSpectator() || player.isUsingItem())
            return;
        if (onlyLiving.get() && !(target instanceof LivingEntity))
            return;

        Inventory inventory = player.getInventory();
        int selected = inventory.getSelectedSlot();
        if (target instanceof LivingEntity living && living.isBlocking() && disablesShield(inventory.getItem(selected)))
            return;

        int bestSlot = selected;
        double bestDamage = damage(player, inventory.getItem(selected), target);
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            if (slot == selected)
                continue;

            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || ToolSaver.isAboutToBreak(stack))
                continue;

            double damage = damage(player, stack, target);
            if (damage > bestDamage + 0.01D) {
                bestDamage = damage;
                bestSlot = slot;
            }
        }

        if (bestSlot == selected)
            return;

        if (switchBack.get() && restoreSlot == -1) {
            restoreSlot = selected;
            PlayerUtil.INSTANCE.doSpoof(selected);
        }
        weaponSlot = bestSlot;
        swapTick = player.tickCount;
        inventory.setSelectedSlot(bestSlot);
    }

    private void restore(boolean force) {
        LocalPlayer player = mc.player;
        if (restoreSlot == -1 || player == null) {
            clear();
            return;
        }
        if (!force && player.tickCount <= swapTick)
            return;

        Inventory inventory = player.getInventory();
        if (inventory.getSelectedSlot() == weaponSlot)
            inventory.setSelectedSlot(restoreSlot);
        clear();
        PlayerUtil.INSTANCE.stopSpoof();
    }

    private double damage(LocalPlayer player, ItemStack stack, Entity target) {
        double base = player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
        double[] total = {base};
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (!attribute.is(Attributes.ATTACK_DAMAGE))
                return;

            total[0] += switch (modifier.operation()) {
                case ADD_VALUE -> modifier.amount();
                case ADD_MULTIPLIED_BASE -> modifier.amount() * base;
                case ADD_MULTIPLIED_TOTAL -> modifier.amount() * total[0];
            };
        });

        ItemEnchantments enchantments = stack.getEnchantments();
        int sharpness = level(enchantments, Enchantments.SHARPNESS);
        if (sharpness > 0)
            total[0] += 1.0D + 0.5D * (sharpness - 1);
        if (target.is(EntityTypeTags.SENSITIVE_TO_SMITE))
            total[0] += 2.5D * level(enchantments, Enchantments.SMITE);
        if (target.is(EntityTypeTags.SENSITIVE_TO_BANE_OF_ARTHROPODS))
            total[0] += 2.5D * level(enchantments, Enchantments.BANE_OF_ARTHROPODS);

        if (maceSmash.get() && stack.getItem() instanceof MaceItem && MaceItem.canSmashAttack(player)) {
            double fall = player.fallDistance;
            double smash = fall <= 3.0D ? 4.0D * fall : fall <= 8.0D ? 12.0D + 2.0D * (fall - 3.0D) : 22.0D + fall - 8.0D;
            total[0] += smash + 0.5D * level(enchantments, Enchantments.DENSITY) * fall;
        }

        return total[0];
    }

    private static boolean disablesShield(ItemStack stack) {
        Weapon weapon = stack.get(DataComponents.WEAPON);
        return weapon != null && weapon.disableBlockingForSeconds() > 0.0F;
    }

    private static int level(ItemEnchantments enchantments, ResourceKey<Enchantment> enchantment) {
        for (var entry : enchantments.entrySet()) {
            if (entry.getKey().is(enchantment))
                return entry.getIntValue();
        }
        return 0;
    }
}
