package com.instrumentalist.krs.utils.math;

import com.instrumentalist.krs.utils.IMinecraft;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.HashMap;
import java.util.Map;

public final class ToolUtil implements IMinecraft {
    public static final ToolUtil INSTANCE = new ToolUtil();

    public static final int TOOL_SWORD = 0;
    public static final int TOOL_PICKAXE = 1;
    public static final int TOOL_AXE = 2;
    public static final int TOOL_SHOVEL = 3;

    private static final Map<Item, int[]> TOOL_INFO = new HashMap<>();

    static {
        Item[][] tools = {
                {Items.WOODEN_SWORD, Items.WOODEN_PICKAXE, Items.WOODEN_AXE, Items.WOODEN_SHOVEL},
                {Items.STONE_SWORD, Items.STONE_PICKAXE, Items.STONE_AXE, Items.STONE_SHOVEL},
                {Items.GOLDEN_SWORD, Items.GOLDEN_PICKAXE, Items.GOLDEN_AXE, Items.GOLDEN_SHOVEL},
                {Items.COPPER_SWORD, Items.COPPER_PICKAXE, Items.COPPER_AXE, Items.COPPER_SHOVEL},
                {Items.IRON_SWORD, Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_SHOVEL},
                {Items.DIAMOND_SWORD, Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.DIAMOND_SHOVEL},
                {Items.NETHERITE_SWORD, Items.NETHERITE_PICKAXE, Items.NETHERITE_AXE, Items.NETHERITE_SHOVEL}
        };
        for (int tier = 0; tier < tools.length; tier++) {
            for (int category = 0; category < tools[tier].length; category++)
                TOOL_INFO.put(tools[tier][category], new int[]{category, tier + 1});
        }
    }

    public final Minecraft mc = Minecraft.getInstance();

    private ToolUtil() {
    }

    public int findBestTool(BlockPos pos) {
        var player = mc.player;
        if (player == null || player.isCreative()) return -1;

        var blockState = player.level().getBlockState(pos);
        int bestSlot = -1;
        double bestSpeed = 0.0;

        for (int i = 0; i <= 8; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.has(DataComponents.TOOL) || stack.getItem() instanceof ShearsItem) {
                float speed = stack.getDestroySpeed(blockState);
                if (speed > bestSpeed) {
                    bestSpeed = speed;
                    bestSlot = i;
                }
            }
        }

        return bestSpeed > 1.0 ? bestSlot : -1;
    }

    public int findBestSword() {
        var player = mc.player;
        if (player == null) return -1;

        int bestSwordSlot = -1;
        double highestStrength = -1.0;

        for (int i = 0; i <= 8; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            int strength = materialStrength(stack.getItem());
            if (strength >= 0 && strength > highestStrength) {
                highestStrength = strength;
                bestSwordSlot = i;
            }
        }

        return bestSwordSlot;
    }

    public boolean isSword(ItemStack stack) {
        return materialStrength(stack.getItem()) >= 0;
    }

    private int materialStrength(Item item) {
        return getToolCategory(item) == TOOL_SWORD ? getToolTier(item) : -1;
    }

    public int getToolCategory(Item item) {
        int[] info = TOOL_INFO.get(item);
        return info != null ? info[0] : -1;
    }

    public int getToolTier(Item item) {
        int[] info = TOOL_INFO.get(item);
        return info != null ? info[1] : 0;
    }

    private int getEnchantmentLevel(ItemEnchantments itemEnchantments, ResourceKey<Enchantment> enchantment) {
        for (var entry : itemEnchantments.entrySet()) {
            if (entry.getKey().is(enchantment)) return entry.getIntValue();
        }
        return 0;
    }

    public int getItemSlotId(ItemStack itemStack) {
        var data = itemStack.get(DataComponents.EQUIPPABLE);
        if (data == null) return -1;

        EquipmentSlot slot = data.slot();
        return switch (slot) {
            case HEAD -> 5;
            case CHEST -> 6;
            case LEGS -> 7;
            case FEET -> 8;
            default -> slot.getId();
        };
    }

    public boolean isArmor(ItemStack itemStack) {
        if (itemStack.isEmpty() || !itemStack.has(DataComponents.EQUIPPABLE)) return false;

        var equippable = itemStack.get(DataComponents.EQUIPPABLE);
        if (equippable == null || !equippable.slot().isArmor()) return false;

        var modifiers = itemStack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (modifiers == null) return false;

        for (var modifier : modifiers.modifiers()) {
            if (modifier.attribute() == Attributes.ARMOR || modifier.attribute() == Attributes.ARMOR_TOUGHNESS)
                return true;
        }

        return false;
    }

    public boolean isBetterArmor(ItemStack newArmor, ItemStack currentArmor) {
        if (!isArmor(newArmor) || !isArmor(currentArmor)) return false;
        return getArmorScore(newArmor) > getArmorScore(currentArmor);
    }

    public EquipmentSlot getArmorEquipmentSlot(ItemStack stack) {
        var armorItem = stack.get(DataComponents.EQUIPPABLE);
        return armorItem.slot();
    }

    private int getArmorScore(ItemStack itemStack) {
        if (itemStack.isEmpty()) return 0;

        int score = 0;
        ItemEnchantments enchantments = itemStack.getEnchantments();

        score += getEnchantmentLevel(enchantments, Enchantments.PROTECTION);
        score += getEnchantmentLevel(enchantments, Enchantments.BLAST_PROTECTION);
        score += getEnchantmentLevel(enchantments, Enchantments.FIRE_PROTECTION);
        score += getEnchantmentLevel(enchantments, Enchantments.PROJECTILE_PROTECTION);
        score += getEnchantmentLevel(enchantments, Enchantments.UNBREAKING);
        score += 2 * getEnchantmentLevel(enchantments, Enchantments.MENDING);

        if (itemStack.has(DataComponents.ATTRIBUTE_MODIFIERS)) {
            var component = itemStack.get(DataComponents.ATTRIBUTE_MODIFIERS);
            for (var modifier : component.modifiers()) {
                if (modifier.attribute() == Attributes.ARMOR || modifier.attribute() == Attributes.ARMOR_TOUGHNESS) {
                    double e = modifier.modifier().amount();

                    double addition = switch (modifier.modifier().operation()) {
                        case ADD_VALUE -> e;
                        case ADD_MULTIPLIED_BASE -> e * (mc.player != null ? mc.player.getAttributeBaseValue(modifier.attribute()) : 0.0);
                        case ADD_MULTIPLIED_TOTAL -> e * score;
                    };
                    score += (int) addition;
                }
            }
        }

        return score;
    }
}
