package com.instrumentalist.krs.hacks.features.player;

import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.combat.AutoPot;
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
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.alchemy.PotionContents;
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
import net.minecraft.world.level.block.BasePressurePlateBlock;
import com.instrumentalist.krs.utils.input.Keys;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
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
    private final IntValue blockRefill = new IntValue("Block Refill", 16, 1, 64);

    @Setting
    private final BooleanValue cleaner = new BooleanValue("Cleaner", true);

    @Setting
    private final BooleanValue onlyInInventory = new BooleanValue("Only in Inventory", false);

    private int tickCounter = 0;
    private int startCounter = 0;
    private final ItemStack[] hotbarTargets = new ItemStack[9];
    private final ItemStack[] cleanerTargets = new ItemStack[9];
    private final EnumMap<EquipmentSlot, ArmorCandidate> armorCandidates = new EnumMap<>(EquipmentSlot.class);
    private final boolean[] excessSlots = new boolean[36];

    private static final int MAX_THROWABLE_STACKS = 2;
    private static final int MAX_BLOCK_STACKS = 3;
    private static final int MAX_HARMFUL_POTIONS = 3;
    private static final int GOLDEN_APPLE_SLOT = 4;
    private static final int ACTION_GAP_TICKS = 2;
    private static int lastClickTick = Integer.MIN_VALUE / 2;

    private static boolean cleaning = false;

    private final Map<Item, Double> swordAttackDamageFallback = Map.of(
            Items.NETHERITE_SWORD, 8.0,
            Items.DIAMOND_SWORD, 7.0,
            Items.IRON_SWORD, 6.0,
            Items.COPPER_SWORD, 5.0,
            Items.STONE_SWORD, 5.0,
            Items.GOLDEN_SWORD, 4.0,
            Items.WOODEN_SWORD, 4.0
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

        var inventory = player.getInventory();

        if (getTicksSinceClick(player) > 0 && handleBlockBorrow(player, gameMode, inventory)) {
            cleaning = true;
            return;
        }

        if (tickCounter > 0) {
            cleaning = true;
            tickCounter--;
            return;
        }

        if (isAutoPotActing(player)) {
            cleaning = false;
            return;
        }

        if (handlePotionBorrow(player, gameMode, inventory)) {
            cleaning = true;
            tickCounter = nextItemDelay();
            return;
        }

        Arrays.fill(hotbarTargets, null);

        for (int i = 0; i < Math.min(36, inventory.getContainerSize()); i++) {
            ItemStack stack = inventory.getItem(i);
            int slot = ToolUtil.INSTANCE.getToolCategory(stack.getItem());
            if (slot >= 0) {
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

        if (sortGoldenApple(player, gameMode, inventory)) {
            cleaning = true;
            tickCounter = nextItemDelay();
            return;
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
                markClick(player);
                tickCounter = nextArmorDelay();
                return;
            }
        }

        if (cleaner.get()) {
            Arrays.fill(cleanerTargets, null);
            markExcessSlots(inventory);

            for (int i = 0; i <= 35; i++) {
                ItemStack stack = inventory.getItem(i);
                int slot = ToolUtil.INSTANCE.getToolCategory(stack.getItem());
                if (slot >= 0) {
                    ItemStack targetStack = cleanerTargets[slot];
                    if (targetStack == null || compareItems(stack, targetStack) > 0)
                        cleanerTargets[slot] = stack;
                }
            }

            for (int i = 0; i <= 35; i++) {
                ItemStack stack = inventory.getItem(i);
                boolean excess = excessSlots[i];
                if (!stack.isEmpty() && (excess
                        || (!(stack.getItem() instanceof BlockItem) || stack.getItem() == Items.CHEST)
                        && !(stack.getItem() instanceof SpawnEggItem)
                        && !(stack.getItem() instanceof PotionItem)
                        && !(stack.getItem() instanceof SplashPotionItem)
                        && !(stack.getItem() instanceof LingeringPotionItem)
                        && !isAllowedCleanerItem(stack.getItem())
                        && !containsCleanerTarget(stack))) {
                    cleaning = true;
                    int slot = i < 9 ? i + 36 : i;
                    dropItem(player, gameMode, slot, excess || stack.getCount() > 1);
                    tickCounter = nextItemDelay();
                    return;
                }
            }
        }

        cleaning = false;
    }

    private boolean handleBlockBorrow(LocalPlayer player, MultiPlayerGameMode gameMode, Container inventory) {
        if (!ModuleManager.getModuleState(Scaffold.class) || Scaffold.countUsableHotbarBlocks(inventory) >= blockRefill.get())
            return false;

        int inventoryIndex = -1;
        for (int i = 9; i < Math.min(36, inventory.getContainerSize()); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!isScaffoldBlockStack(stack)) continue;

            if (inventoryIndex == -1 || stack.getCount() > inventory.getItem(inventoryIndex).getCount())
                inventoryIndex = i;
        }
        if (inventoryIndex == -1) return false;

        int hotbarSlot = findBorrowHotbarSlot(player, inventory);
        if (hotbarSlot == -1) return false;

        swapItems(player, gameMode, inventoryIndex, hotbarSlot);
        return true;
    }

    private static boolean isScaffoldBlockStack(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem && Scaffold.isScaffoldBlock(blockItem.getBlock());
    }

    private boolean isAutoPotActing(LocalPlayer player) {
        return ModuleManager.getModuleState(AutoPot.class)
                && (AutoPot.isThrowing() || AutoPot.getTicksSinceAction(player) < ACTION_GAP_TICKS);
    }

    public static int getTicksSinceClick(LocalPlayer player) {
        int ticks = player.tickCount - lastClickTick;
        return ticks < 0 ? Integer.MAX_VALUE : ticks;
    }

    private static void markClick(LocalPlayer player) {
        lastClickTick = player.tickCount;
    }

    private boolean handlePotionBorrow(LocalPlayer player, MultiPlayerGameMode gameMode, Container inventory) {
        if (!ModuleManager.getModuleState(AutoPot.class) || AutoPot.isThrowing() || AutoPot.getRequestedPotions().isEmpty() || hasRequestedPotionInHotbar(inventory))
            return false;

        int inventoryIndex = -1;
        for (int i = 9; i < Math.min(36, inventory.getContainerSize()); i++) {
            if (AutoPot.isRequestedPotion(inventory.getItem(i))) {
                inventoryIndex = i;
                break;
            }
        }
        if (inventoryIndex == -1) return false;

        int hotbarSlot = findBorrowHotbarSlot(player, inventory);
        if (hotbarSlot == -1) return false;

        swapItems(player, gameMode, inventoryIndex, hotbarSlot);
        return true;
    }

    private boolean hasRequestedPotionInHotbar(Container inventory) {
        for (int i = 0; i <= 8; i++) {
            if (AutoPot.isRequestedPotion(inventory.getItem(i)))
                return true;
        }
        return false;
    }

    private int findBorrowHotbarSlot(LocalPlayer player, Container inventory) {
        int selected = player.getInventory().getSelectedSlot();
        int fallback = -1;

        for (int i = 8; i >= 0; i--) {
            if (i == selected || i == GOLDEN_APPLE_SLOT || isToolHotbarSlot(i) || isScaffoldBlockStack(inventory.getItem(i))) continue;

            if (inventory.getItem(i).isEmpty())
                return i;

            if (fallback == -1)
                fallback = i;
        }
        return fallback;
    }

    private boolean sortGoldenApple(LocalPlayer player, MultiPlayerGameMode gameMode, Container inventory) {
        int bestIndex = -1;
        for (int i = 0; i < Math.min(36, inventory.getContainerSize()); i++) {
            ItemStack stack = inventory.getItem(i);
            if (getGoldenAppleRank(stack) == 0) continue;

            if (bestIndex == -1 || compareGoldenApples(stack, inventory.getItem(bestIndex)) > 0)
                bestIndex = i;
        }

        if (bestIndex == -1 || bestIndex == GOLDEN_APPLE_SLOT) return false;

        ItemStack current = inventory.getItem(GOLDEN_APPLE_SLOT);
        if (getGoldenAppleRank(current) >= getGoldenAppleRank(inventory.getItem(bestIndex))) return false;

        swapItems(player, gameMode, bestIndex, GOLDEN_APPLE_SLOT);
        return true;
    }

    private int compareGoldenApples(ItemStack apple1, ItemStack apple2) {
        int rank = Integer.compare(getGoldenAppleRank(apple1), getGoldenAppleRank(apple2));
        if (rank != 0) return rank;

        return Integer.compare(apple1.getCount(), apple2.getCount());
    }

    private static int getGoldenAppleRank(ItemStack stack) {
        if (stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE) return 2;
        if (stack.getItem() == Items.GOLDEN_APPLE) return 1;
        return 0;
    }

    private boolean isToolHotbarSlot(int slot) {
        return slot == ToolUtil.TOOL_SWORD || slot == ToolUtil.TOOL_PICKAXE || slot == ToolUtil.TOOL_AXE || slot == ToolUtil.TOOL_SHOVEL;
    }

    private void markExcessSlots(Container inventory) {
        Arrays.fill(excessSlots, false);

        List<Integer> throwables = new ArrayList<>();
        List<Integer> blocks = new ArrayList<>();
        List<Integer> harmfulPotions = new ArrayList<>();
        int bestRod = -1;
        boolean hasWaterBucket = false;

        for (int i = 0; i <= 35; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;

            Item item = stack.getItem();
            if (isLimitedThrowable(item)) {
                throwables.add(i);
            } else if (item == Items.FISHING_ROD) {
                if (bestRod == -1 || compareRods(stack, inventory.getItem(bestRod)) > 0)
                    bestRod = i;
            } else if (item == Items.WATER_BUCKET) {
                if (hasWaterBucket) excessSlots[i] = true;
                hasWaterBucket = true;
            } else if (isHarmfulPotion(stack)) {
                harmfulPotions.add(i);
            } else if (item instanceof BlockItem blockItem) {
                if (blockItem.getBlock() instanceof BasePressurePlateBlock)
                    excessSlots[i] = true;
                else if (Scaffold.isScaffoldBlock(blockItem.getBlock()))
                    blocks.add(i);
            }
        }

        for (int i = 0; i <= 35; i++) {
            if (i != bestRod && inventory.getItem(i).getItem() == Items.FISHING_ROD)
                excessSlots[i] = true;
        }

        markStacksOverLimit(inventory, throwables, MAX_THROWABLE_STACKS);
        if (!ModuleManager.getModuleState(Scaffold.class)) {
            int blockLimit = MAX_BLOCK_STACKS;
            if (mc.player != null && blocks.remove(Integer.valueOf(mc.player.getInventory().getSelectedSlot())))
                blockLimit--;
            markStacksOverLimit(inventory, blocks, blockLimit);
        }
        markItemsOverLimit(inventory, harmfulPotions, MAX_HARMFUL_POTIONS);
    }

    private void markItemsOverLimit(Container inventory, List<Integer> slots, int limit) {
        int kept = 0;
        for (int slot : slots) {
            int count = inventory.getItem(slot).getCount();
            if (kept + count <= limit) kept += count;
            else excessSlots[slot] = true;
        }
    }

    private static boolean isHarmfulPotion(ItemStack stack) {
        Item item = stack.getItem();
        if (!(item instanceof PotionItem) && !(item instanceof SplashPotionItem) && !(item instanceof LingeringPotionItem))
            return false;

        boolean harmful = false;
        PotionContents potionContents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        for (var effectInstance : potionContents.getAllEffects()) {
            MobEffectCategory category = effectInstance.getEffect().value().getCategory();
            if (category == MobEffectCategory.BENEFICIAL) return false;
            if (category == MobEffectCategory.HARMFUL) harmful = true;
        }
        return harmful;
    }

    private void markStacksOverLimit(Container inventory, List<Integer> slots, int limit) {
        if (slots.size() <= limit) return;

        slots.sort(Comparator.<Integer>comparingInt(i -> inventory.getItem(i).getCount()).reversed().thenComparingInt(i -> i));
        for (int i = limit; i < slots.size(); i++)
            excessSlots[slots.get(i)] = true;
    }

    private int compareRods(ItemStack rod1, ItemStack rod2) {
        int enchantments = Integer.compare(getEnchantmentScore(rod1), getEnchantmentScore(rod2));
        if (enchantments != 0) return enchantments;

        int durability1 = rod1.getMaxDamage() - rod1.getDamageValue();
        int durability2 = rod2.getMaxDamage() - rod2.getDamageValue();
        return Integer.compare(durability1, durability2);
    }

    private static boolean isLimitedThrowable(Item item) {
        return item == Items.SNOWBALL
                || item == Items.EGG
                || item == Items.BLUE_EGG
                || item == Items.BROWN_EGG;
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

        int category = ToolUtil.INSTANCE.getToolCategory(item1);
        if (category >= 0 && category == ToolUtil.INSTANCE.getToolCategory(item2)) {
            if (category == ToolUtil.TOOL_SWORD) {
                int attackDamageComparison = Double.compare(getSwordAttackDamage(stack1), getSwordAttackDamage(stack2));
                if (attackDamageComparison != 0)
                    return attackDamageComparison;
            }

            rank1 = ToolUtil.INSTANCE.getToolTier(item1);
            rank2 = ToolUtil.INSTANCE.getToolTier(item2);
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
                || item == Items.SNOWBALL
                || item == Items.EGG
                || item == Items.BLUE_EGG
                || item == Items.BROWN_EGG
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
        if (fromSlot != to + 36) {
            gameMode.handleContainerInput(syncId, fromSlot, to, ContainerInput.SWAP, player);
            markClick(player);
        }
    }

    private void dropItem(LocalPlayer player, MultiPlayerGameMode gameMode, int slot, boolean wholeStack) {
        markClick(player);
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
