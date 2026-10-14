package com.instrumentalist.krs.hacks.features.player;

import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.features.exploit.disabler.features.HypixelDisabler;
import com.instrumentalist.krs.utils.math.RandomUtil;
import com.instrumentalist.krs.utils.math.ToolUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.IntValue;
import net.minecraft.util.Prediction;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.FurnaceScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import com.instrumentalist.krs.utils.input.Keys;

import java.util.EnumMap;
import java.util.Map;

public class InvManager extends Module {

    @Setting
    private final IntValue startDelay = new IntValue("Start Delay", 2, 0, 10);

    @Setting
    private final IntValue maxItemDelay = new IntValue("Max Item Delay", 2, 0, 10);

    @Setting
    private final IntValue minItemDelay = new IntValue("Min Item Delay", 0, 0, 10);

    @Setting
    private final IntValue maxArmorDelay = new IntValue("Max Armor Delay", 2, 0, 10);

    @Setting
    private final IntValue minArmorDelay = new IntValue("Min Armor Delay", 0, 0, 10);

    @Setting
    private final BooleanValue cleaner = new BooleanValue("Cleaner", true);

    @Setting
    private final BooleanValue onlyInInventory = new BooleanValue("Only in Inventory", false);

    private int tickCounter = 0;
    private int startCounter = 0;
    private final ItemStack[] hotbarTargets = new ItemStack[9];
    private final ItemStack[] cleanerTargets = new ItemStack[9];
    private final EnumMap<EquipmentSlot, ArmorCandidate> armorCandidates = new EnumMap<>(EquipmentSlot.class);

    private static boolean cleaning = false;

    private final Map<Item, Integer> itemSlots = Map.ofEntries(
            Map.entry(Items.NETHERITE_SWORD, 0),
            Map.entry(Items.DIAMOND_SWORD, 0),
            Map.entry(Items.IRON_SWORD, 0),
            Map.entry(Items.GOLDEN_SWORD, 0),
            Map.entry(Items.STONE_SWORD, 0),
            Map.entry(Items.WOODEN_SWORD, 0),
            Map.entry(Items.NETHERITE_PICKAXE, 1),
            Map.entry(Items.DIAMOND_PICKAXE, 1),
            Map.entry(Items.IRON_PICKAXE, 1),
            Map.entry(Items.GOLDEN_PICKAXE, 1),
            Map.entry(Items.STONE_PICKAXE, 1),
            Map.entry(Items.WOODEN_PICKAXE, 1),
            Map.entry(Items.NETHERITE_AXE, 2),
            Map.entry(Items.DIAMOND_AXE, 2),
            Map.entry(Items.IRON_AXE, 2),
            Map.entry(Items.GOLDEN_AXE, 2),
            Map.entry(Items.STONE_AXE, 2),
            Map.entry(Items.WOODEN_AXE, 2),
            Map.entry(Items.NETHERITE_SHOVEL, 3),
            Map.entry(Items.DIAMOND_SHOVEL, 3),
            Map.entry(Items.IRON_SHOVEL, 3),
            Map.entry(Items.GOLDEN_SHOVEL, 3),
            Map.entry(Items.STONE_SHOVEL, 3),
            Map.entry(Items.WOODEN_SHOVEL, 3)
    );

    private final Map<Item, Integer> swordMaterialRank = Map.of(
            Items.NETHERITE_SWORD, 6,
            Items.DIAMOND_SWORD, 5,
            Items.IRON_SWORD, 4,
            Items.GOLDEN_SWORD, 3,
            Items.STONE_SWORD, 2,
            Items.WOODEN_SWORD, 1
    );

    private final Map<Item, Double> swordAttackDamageFallback = Map.of(
            Items.NETHERITE_SWORD, 8.0,
            Items.DIAMOND_SWORD, 7.0,
            Items.IRON_SWORD, 6.0,
            Items.STONE_SWORD, 5.0,
            Items.GOLDEN_SWORD, 4.0,
            Items.WOODEN_SWORD, 4.0
    );

    private final Map<Item, Integer> pickaxeMaterialRank = Map.of(
            Items.NETHERITE_PICKAXE, 6,
            Items.DIAMOND_PICKAXE, 5,
            Items.IRON_PICKAXE, 4,
            Items.GOLDEN_PICKAXE, 3,
            Items.STONE_PICKAXE, 2,
            Items.WOODEN_PICKAXE, 1
    );

    private final Map<Item, Integer> axeMaterialItem = Map.of(
            Items.NETHERITE_AXE, 6,
            Items.DIAMOND_AXE, 5,
            Items.IRON_AXE, 4,
            Items.GOLDEN_AXE, 3,
            Items.STONE_AXE, 2,
            Items.WOODEN_AXE, 1
    );

    private final Map<Item, Integer> shovelMaterialItem = Map.of(
            Items.NETHERITE_SHOVEL, 6,
            Items.DIAMOND_SHOVEL, 5,
            Items.IRON_SHOVEL, 4,
            Items.GOLDEN_SHOVEL, 3,
            Items.STONE_SHOVEL, 2,
            Items.WOODEN_SHOVEL, 1
    );

    public InvManager() {
        super("Inv Manager", ModuleCategory.Player, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onDisable() {
        tickCounter = 0;
        startCounter = 0;
        cleaning = false;
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        var player = mc.player;
        var gameMode = mc.gameMode;
        if (player == null || gameMode == null || gameMode.getPlayerMode() == GameType.SPECTATOR) return;

        if (onlyInInventory.get() && !(mc.gui.screen() instanceof InventoryScreen) || mc.gui.screen() instanceof ContainerScreen || mc.gui.screen() instanceof FurnaceScreen || HypixelDisabler.isInventoryMovePausedForChest()) {
            cleaning = false;
            tickCounter = 0;
            startCounter = 0;
            return;
        }

        if (startCounter < startDelay.get()) {
            cleaning = false;
            startCounter++;
            return;
        }

        if (tickCounter > 0) {
            cleaning = true;
            tickCounter--;
            return;
        }

        var inventory = player.getInventory();
        java.util.Arrays.fill(hotbarTargets, null);

        for (int i = 0; i < Math.min(36, inventory.getContainerSize()); i++) {
            ItemStack stack = inventory.getItem(i);
            Integer slot = itemSlots.get(stack.getItem());
            if (slot != null) {
                ItemStack targetStack = hotbarTargets[slot];
                if (targetStack == null || compareItems(stack, targetStack) > 0)
                    hotbarTargets[slot] = stack;
            }
        }

        for (int slot = 0; slot < hotbarTargets.length; slot++) {
            ItemStack targetStack = hotbarTargets[slot];
            if (targetStack == null) continue;
            int hotbarIndex = findItemInHotbar(inventory, targetStack);

            if (hotbarIndex != slot) {
                int inventoryIndex = -1;
                for (int i = 0; i < Math.min(36, inventory.getContainerSize()); i++) {
                    ItemStack stack = inventory.getItem(i);
                    if (stack.getItem() == targetStack.getItem() && compareItems(stack, targetStack) == 0) {
                        inventoryIndex = i;
                        break;
                    }
                }

                if (inventoryIndex != -1) {
                    cleaning = true;
                    swapItems(player, gameMode, inventoryIndex, slot);
                    tickCounter = nextItemDelay();
                    return;
                }
            }
        }

        armorCandidates.clear();

        for (int i = 0; i <= 35; i++) {
            ItemStack stack = inventory.getItem(i);
            if (ToolUtil.INSTANCE.isArmor(stack)) {
                EquipmentSlot armorEquipSlot = ToolUtil.INSTANCE.getArmorEquipmentSlot(stack);
                ArmorCandidate currentBest = armorCandidates.get(armorEquipSlot);
                if (currentBest == null) {
                    armorCandidates.put(armorEquipSlot, new ArmorCandidate(stack, i));
                } else if (ToolUtil.INSTANCE.isBetterArmor(stack, currentBest.stack)) {
                    currentBest.stack = stack;
                    currentBest.inventoryIndex = i;
                }
            }
        }

        for (Map.Entry<EquipmentSlot, ArmorCandidate> entry : armorCandidates.entrySet()) {
            EquipmentSlot slot = entry.getKey();
            ArmorCandidate candidate = entry.getValue();
            ItemStack bestArmor = candidate.stack;
            int inventoryIndex = candidate.inventoryIndex;
            ItemStack currentArmor = player.getItemBySlot(slot);

            if (currentArmor.isEmpty() || ToolUtil.INSTANCE.isBetterArmor(bestArmor, currentArmor)) {
                if (!currentArmor.isEmpty()) {
                    cleaning = true;
                    int armorSlot = ToolUtil.INSTANCE.getItemSlotId(currentArmor);
                    dropItem(player, gameMode, armorSlot, false);
                    tickCounter = nextArmorDelay();
                    return;
                }

                cleaning = true;
                int slotIndex = inventoryIndex < 9 ? inventoryIndex + 36 : inventoryIndex;
                gameMode.handleContainerInput(player.containerMenu.containerId, slotIndex, 0, ContainerInput.QUICK_MOVE, player);
                tickCounter = nextArmorDelay();
                return;
            }
        }

        if (cleaner.get()) {
            java.util.Arrays.fill(cleanerTargets, null);

            for (int i = 0; i <= 35; i++) {
                ItemStack stack = inventory.getItem(i);
                Integer slot = itemSlots.get(stack.getItem());
                if (slot != null) {
                    ItemStack targetStack = cleanerTargets[slot];
                    if (targetStack == null || compareItems(stack, targetStack) > 0)
                        cleanerTargets[slot] = stack;
                }
            }

            for (int i = 0; i <= 35; i++) {
                ItemStack stack = inventory.getItem(i);
                if (!stack.isEmpty()
                        && (!(stack.getItem() instanceof BlockItem) || stack.getItem() == Items.CHEST)
                        && !(stack.getItem() instanceof SpawnEggItem)
                        && !(stack.getItem() instanceof PotionItem)
                        && !(stack.getItem() instanceof SplashPotionItem)
                        && !(stack.getItem() instanceof LingeringPotionItem)
                        && !isAllowedCleanerItem(stack.getItem())
                        && !containsCleanerTarget(stack)) {
                    cleaning = true;
                    int slot = i < 9 ? i + 36 : i;
                    dropItem(player, gameMode, slot, stack.getCount() > 1);
                    tickCounter = nextItemDelay();
                    return;
                }
            }
        }

        cleaning = false;
    }

    private int findItemInHotbar(Container inventory, ItemStack targetStack) {
        for (int i = 0; i <= 8; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() == targetStack.getItem() && compareItems(stack, targetStack) == 0)
                return i;
        }
        return -1;
    }

    private int compareItems(ItemStack stack1, ItemStack stack2) {
        Item item1 = stack1.getItem();
        Item item2 = stack2.getItem();

        int rank1 = 0;
        int rank2 = 0;

        if (swordMaterialRank.containsKey(item1) && swordMaterialRank.containsKey(item2)) {
            int attackDamageComparison = Double.compare(getSwordAttackDamage(stack1), getSwordAttackDamage(stack2));
            if (attackDamageComparison != 0)
                return attackDamageComparison;

            rank1 = swordMaterialRank.getOrDefault(item1, 0);
            rank2 = swordMaterialRank.getOrDefault(item2, 0);
        } else if (pickaxeMaterialRank.containsKey(item1) && pickaxeMaterialRank.containsKey(item2)) {
            rank1 = pickaxeMaterialRank.getOrDefault(item1, 0);
            rank2 = pickaxeMaterialRank.getOrDefault(item2, 0);
        } else if (axeMaterialItem.containsKey(item1) && axeMaterialItem.containsKey(item2)) {
            rank1 = axeMaterialItem.getOrDefault(item1, 0);
            rank2 = axeMaterialItem.getOrDefault(item2, 0);
        } else if (shovelMaterialItem.containsKey(item1) && shovelMaterialItem.containsKey(item2)) {
            rank1 = shovelMaterialItem.getOrDefault(item1, 0);
            rank2 = shovelMaterialItem.getOrDefault(item2, 0);
        }

        if (rank1 != rank2)
            return Integer.compare(rank1, rank2);

        if (item1 == item2) {
            int enchantments1 = getEnchantmentScore(stack1);
            int enchantments2 = getEnchantmentScore(stack2);
            if (enchantments1 != enchantments2)
                return Integer.compare(enchantments1, enchantments2);
        }

        return 0;
    }

    private int getEnchantmentScore(ItemStack stack) {
        int score = 0;
        for (var entry : stack.getEnchantments().entrySet())
            score += entry.getIntValue();
        return score;
    }

    private boolean containsCleanerTarget(ItemStack stack) {
        for (ItemStack target : cleanerTargets) {
            if (stack.equals(target))
                return true;
        }
        return false;
    }

    private static boolean isAllowedCleanerItem(Item item) {
        return item == Items.ENDER_PEARL
                || item == Items.ENDER_EYE
                || item == Items.TRIDENT
                || item == Items.MACE
                || item == Items.BOW
                || item == Items.CROSSBOW
                || item == Items.ARROW
                || item == Items.GOLDEN_APPLE
                || item == Items.ENCHANTED_GOLDEN_APPLE
                || item == Items.APPLE
                || item == Items.MUSHROOM_STEW
                || item == Items.BREAD
                || item == Items.PORKCHOP
                || item == Items.COOKED_PORKCHOP
                || item == Items.GOLDEN_CARROT
                || item == Items.CARROT
                || item == Items.POTATO
                || item == Items.BAKED_POTATO
                || item == Items.COOKED_BEEF
                || item == Items.BEEF
                || item == Items.COOKED_CHICKEN
                || item == Items.CHICKEN
                || item == Items.COOKED_MUTTON
                || item == Items.MUTTON
                || item == Items.COOKED_RABBIT
                || item == Items.RABBIT
                || item == Items.RABBIT_STEW
                || item == Items.BEETROOT
                || item == Items.BEETROOT_SOUP
                || item == Items.MELON_SLICE
                || item == Items.PUMPKIN_PIE
                || item == Items.COOKIE
                || item == Items.SWEET_BERRIES
                || item == Items.COD
                || item == Items.COOKED_COD
                || item == Items.SALMON
                || item == Items.COOKED_SALMON
                || item == Items.TROPICAL_FISH
                || item == Items.PUFFERFISH
                || item == Items.HONEY_BOTTLE
                || item == Items.GLOW_BERRIES
                || item == Items.DRIED_KELP
                || item == Items.ROTTEN_FLESH
                || item == Items.POISONOUS_POTATO
                || item == Items.COMPASS
                || item == Items.RECOVERY_COMPASS
                || item == Items.WATER_BUCKET
                || item == Items.ELYTRA
                || item == Items.IRON_INGOT
                || item == Items.DIAMOND
                || item == Items.EMERALD
                || item == Items.GOLD_INGOT
                || item == Items.NETHERITE_INGOT
                || item == Items.SHIELD
                || item == Items.BLAZE_POWDER
                || item == Items.PAPER
                || item == Items.FISHING_ROD
                || item == Items.BUCKET;
    }

    private double getSwordAttackDamage(ItemStack stack) {
        double baseAttackDamage = mc.player != null ? mc.player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) : 1.0;
        double[] attackDamage = {baseAttackDamage};
        boolean[] hasAttackDamageModifier = {false};

        stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.is(Attributes.ATTACK_DAMAGE)) {
                hasAttackDamageModifier[0] = true;
                double amount = modifier.amount();

                double addition = switch (modifier.operation()) {
                    case ADD_VALUE -> amount;
                    case ADD_MULTIPLIED_BASE -> amount * baseAttackDamage;
                    case ADD_MULTIPLIED_TOTAL -> amount * attackDamage[0];
                };
                attackDamage[0] += addition;
            }
        });

        if (!hasAttackDamageModifier[0])
            attackDamage[0] = swordAttackDamageFallback.getOrDefault(stack.getItem(), 0.0);

        return attackDamage[0] + getSharpnessDamageBonus(stack.getEnchantments());
    }

    private double getSharpnessDamageBonus(ItemEnchantments itemEnchantments) {
        int level = getEnchantmentLevel(itemEnchantments, Enchantments.SHARPNESS);
        if (level <= 0) return 0.0;

        return 1.0 + 0.5 * (level - 1);
    }

    private int getEnchantmentLevel(ItemEnchantments itemEnchantments, ResourceKey<Enchantment> enchantment) {
        for (var entry : itemEnchantments.entrySet()) {
            if (entry.getKey().is(enchantment)) return entry.getIntValue();
        }
        return 0;
    }

    private int nextItemDelay() {
        return nextDelay(minItemDelay.get(), maxItemDelay.get());
    }

    private int nextArmorDelay() {
        return nextDelay(minArmorDelay.get(), maxArmorDelay.get());
    }

    private int nextDelay(int min, int max) {
        int lower = Math.min(min, max);
        int upper = Math.max(min, max);

        return RandomUtil.nextInt(lower, upper + 1);
    }

    private void swapItems(LocalPlayer player, MultiPlayerGameMode gameMode, int from, int to) {
        int syncId = player.containerMenu.containerId;
        int fromSlot = from < 9 ? from + 36 : from;
        if (fromSlot != to + 36)
            gameMode.handleContainerInput(syncId, fromSlot, to, ContainerInput.SWAP, player);
    }

    private void dropItem(LocalPlayer player, MultiPlayerGameMode gameMode, int slot, boolean wholeStack) {
        if (!player.hasInfiniteMaterials()) {
            int button = wholeStack ? 1 : 0;
            gameMode.handleContainerInput(player.containerMenu.containerId, slot, button, ContainerInput.THROW, player);
            return;
        }

        // A creative THROW click sends both the creative drop packet and the regular container
        // click packet. Mirror CreativeModeInventoryScreen so the server processes only one drop.
        var inventorySlot = player.inventoryMenu.getSlot(slot);
        if (!inventorySlot.hasItem()) return;

        int amount = wholeStack ? inventorySlot.getItem().getMaxStackSize() : 1;
        ItemStack droppedStack = inventorySlot.remove(amount);
        if (droppedStack.isEmpty()) return;

        player.drop(droppedStack, true, Prediction.PREDICTED);
        gameMode.handleCreativeModeItemDrop(droppedStack);
        gameMode.handleCreativeModeItemAdd(inventorySlot.getItem(), inventorySlot.index);
    }

    public static boolean getCleaning() {
        return cleaning;
    }

    public static void setCleaning(boolean value) {
        cleaning = value;
    }

    private static final class ArmorCandidate {
        private ItemStack stack;
        private int inventoryIndex;

        private ArmorCandidate(ItemStack stack, int inventoryIndex) {
            this.stack = stack;
            this.inventoryIndex = inventoryIndex;
        }
    }
}
