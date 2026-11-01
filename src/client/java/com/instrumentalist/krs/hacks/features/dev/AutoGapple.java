package com.instrumentalist.krs.hacks.features.dev;

import com.instrumentalist.krs.events.features.TickEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.entity.ItemUseHold;
import com.instrumentalist.krs.utils.entity.PlayerUtil;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

public class AutoGapple extends Module {
    private static final int START_GRACE_TICKS = 3;
    private static final int TIMEOUT_TICKS = 80;

    @Setting
    private final FloatValue health = new FloatValue("Health", 10f, 1f, 19f, "hp");

    @Setting
    private final BooleanValue countAbsorption = new BooleanValue("Count Absorption", true);

    @Setting
    private final BooleanValue enchantedFirst = new BooleanValue("Enchanted First", false);

    private boolean eating;
    private int restoreSlot = -1;
    private int gappleSlot = -1;
    private int startTick;

    public AutoGapple() {
        super("Auto Gapple", ModuleCategory.Dev, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public String tag() {
        return String.valueOf(Math.round(health.get()));
    }

    @Override
    public void onEnable() {
        eating = false;
        restoreSlot = -1;
    }

    @Override
    public void onDisable() {
        finish(true);
    }

    @Override
    public void onWorld(WorldEvent event) {
        ItemUseHold.release(this);
        eating = false;
        restoreSlot = -1;
        gappleSlot = -1;
    }

    @Override
    public void onTick(TickEvent event) {
        LocalPlayer player = mc.player;
        MultiPlayerGameMode gameMode = mc.gameMode;
        if (player == null || gameMode == null) {
            onWorld(null);
            return;
        }

        if (eating) {
            int elapsed = player.tickCount - startTick;
            boolean stillEating = player.isUsingItem() && player.getInventory().getSelectedSlot() == gappleSlot;
            if ((!stillEating && elapsed > START_GRACE_TICKS) || elapsed > TIMEOUT_TICKS)
                finish(false);
            return;
        }

        if (gameMode.getPlayerMode() == GameType.SPECTATOR || player.isUsingItem() || mc.gui.screen() != null)
            return;

        float currentHealth = player.getHealth() + (countAbsorption.get() ? player.getAbsorptionAmount() : 0f);
        if (currentHealth > health.get())
            return;

        Inventory inventory = player.getInventory();
        int slot = findGapple(player, inventory);
        if (slot == -1)
            return;

        int selected = inventory.getSelectedSlot();
        if (slot != selected) {
            restoreSlot = selected;
            PlayerUtil.INSTANCE.doSpoof(selected);
            inventory.setSelectedSlot(slot);
        }

        gappleSlot = slot;
        startTick = player.tickCount;
        eating = true;
        ItemUseHold.hold(this);
        gameMode.useItem(player, InteractionHand.MAIN_HAND);
    }

    private int findGapple(LocalPlayer player, Inventory inventory) {
        Item first = enchantedFirst.get() ? Items.ENCHANTED_GOLDEN_APPLE : Items.GOLDEN_APPLE;
        Item second = enchantedFirst.get() ? Items.GOLDEN_APPLE : Items.ENCHANTED_GOLDEN_APPLE;
        int slot = findSlot(player, inventory, first);
        return slot != -1 ? slot : findSlot(player, inventory, second);
    }

    private static int findSlot(LocalPlayer player, Inventory inventory, Item item) {
        int selected = inventory.getSelectedSlot();
        if (usable(player, inventory.getItem(selected), item))
            return selected;

        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            if (usable(player, inventory.getItem(slot), item))
                return slot;
        }
        return -1;
    }

    private static boolean usable(LocalPlayer player, ItemStack stack, Item item) {
        return stack.is(item) && !player.getCooldowns().isOnCooldown(stack);
    }

    private void finish(boolean force) {
        LocalPlayer player = mc.player;
        ItemUseHold.release(this);
        if (player != null && eating) {
            if (force && player.isUsingItem() && mc.gameMode != null)
                ItemUseHold.forceRelease(() -> mc.gameMode.releaseUsingItem(player));

            Inventory inventory = player.getInventory();
            if (restoreSlot != -1 && inventory.getSelectedSlot() == gappleSlot)
                inventory.setSelectedSlot(restoreSlot);
            if (restoreSlot != -1)
                PlayerUtil.INSTANCE.stopSpoof();
        }

        eating = false;
        restoreSlot = -1;
        gappleSlot = -1;
    }
}
