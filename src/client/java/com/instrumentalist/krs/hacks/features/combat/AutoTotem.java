package com.instrumentalist.krs.hacks.features.combat;

import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.math.MSTimer;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import com.instrumentalist.krs.utils.input.Keys;

public class AutoTotem extends Module {
    private final MSTimer swapTimer = new MSTimer();

    public AutoTotem() {
        super("Auto Totem", ModuleCategory.Combat, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
        swapTimer.reset();
    }

    @Override
    public void onWorld(WorldEvent event) {
        swapTimer.reset();
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        var player = mc.player;
        var gameMode = mc.gameMode;
        if (player == null || gameMode == null || gameMode.getPlayerMode() == GameType.SPECTATOR)
            return;

        if (mc.gui.screen() instanceof ContainerScreen)
            return;

        if (player.getOffhandItem().getItem() == Items.TOTEM_OF_UNDYING)
            return;

        if (!swapTimer.hasTimePassed(150L))
            return;

        int sourceSlot = findTotemInventorySlot();
        if (sourceSlot == -1)
            return;

        gameMode.handleContainerInput(player.containerMenu.containerId, sourceSlot, 40, ContainerInput.SWAP, player);
        swapTimer.reset();
    }

    private int findTotemInventorySlot() {
        var player = mc.player;
        if (player == null)
            return -1;

        for (int i = 0; i < Math.min(36, player.getInventory().getContainerSize()); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() == Items.TOTEM_OF_UNDYING)
                return inventoryIndexToMenuSlot(i);
        }

        return -1;
    }

    private int inventoryIndexToMenuSlot(int inventoryIndex) {
        return inventoryIndex < 9 ? inventoryIndex + 36 : inventoryIndex;
    }
}
