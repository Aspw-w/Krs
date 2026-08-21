package com.instrumentalist.krs.hacks.features.player;

import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.math.RandomUtil;
import com.instrumentalist.krs.utils.math.ToolUtil;
import com.instrumentalist.krs.utils.packet.BlinkUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.IntValue;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;

public class ChestStealer extends Module {

    @Setting
    private static final IntValue startDelay = new IntValue("Start Delay", 2, 0, 10);

    @Setting
    private static final IntValue maxDelay = new IntValue("Max Delay", 2, 0, 10);

    @Setting
    private static final IntValue minDelay = new IntValue("Min Delay", 0, 0, 10);

    @Setting
    public static final BooleanValue blink = new BooleanValue("Blink", false);

    @Setting
    private static final BooleanValue cameraMove = new BooleanValue("Camera Move", false);

    private int tickCounter = 0;
    private int startCounter = 0;
    private int closeCounter = 0;
    private int closeDelayTarget = -1;
    private boolean stealing = false;
    private final ArrayList<Integer> slotIndices = new ArrayList<>(27);

    public ChestStealer() {
        super("Chest Stealer", ModuleCategory.Player, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    public static boolean shouldMoveCamera() {
        return ModuleManager.getModuleState(ChestStealer.class)
                && cameraMove.get()
                && mc.player != null
                && mc.player.containerMenu instanceof ChestMenu
                && mc.gui.screen() instanceof ContainerScreen;
    }

    @Override
    public void onDisable() {
        tickCounter = 0;
        startCounter = 0;
        closeCounter = 0;
        closeDelayTarget = -1;
        stealing = false;
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        var player = mc.player;
        var gameMode = mc.gameMode;
        if (player == null || gameMode == null) return;

        if (!stealing && BlinkUtil.INSTANCE.getBlinking()) {
            BlinkUtil.INSTANCE.sync(true, false);
            BlinkUtil.INSTANCE.stopBlink();
        }

        if (tickCounter > 0) {
            tickCounter--;
            return;
        }

        if (player.containerMenu instanceof ChestMenu screenHandler) {
            if (startCounter < startDelay.get()) {
                startCounter++;
                return;
            }

            if (blink.get() && !stealing) {
                BlinkUtil.INSTANCE.doBlink();
                stealing = true;
            }

            slotIndices.clear();
            for (int i = 0; i < Math.min(27, screenHandler.slots.size()); i++)
                slotIndices.add(i);
            Collections.shuffle(slotIndices);

            boolean itemStolen = false;
            for (int i : slotIndices) {
                var slot = screenHandler.getSlot(i);
                if (slot.hasItem() && shouldSteal(slot.getItem(), player)) {
                    gameMode.handleContainerInput(screenHandler.containerId, i, 0, ContainerInput.QUICK_MOVE, player);
                    itemStolen = true;
                    tickCounter = nextDelay();
                    closeCounter = 0;
                    closeDelayTarget = -1;
                    break;
                }
            }

            if (!itemStolen) {
                if (closeDelayTarget < 0)
                    closeDelayTarget = nextDelay();

                if (closeCounter < closeDelayTarget) {
                    closeCounter++;
                    return;
                }

                closeCounter = 0;
                closeDelayTarget = -1;
                player.closeContainer();
                stealing = false;
            }
        } else {
            closeCounter = 0;
            closeDelayTarget = -1;
            startCounter = 0;
            stealing = false;
        }
    }

    private boolean shouldSteal(ItemStack stack, LocalPlayer player) {
        if (stack.getItem() instanceof BlockItem
                || stack.getItem() instanceof PotionItem
                || stack.getItem() instanceof SplashPotionItem
                || stack.getItem() instanceof LingeringPotionItem
                || stack.getItem() == Items.GOLDEN_APPLE
                || stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE
                || stack.getItem() == Items.ENDER_PEARL
                || stack.getItem() == Items.ENDER_EYE)
            return true;

        if (ToolUtil.INSTANCE.isArmor(stack)) {
            var armorSlot = ToolUtil.INSTANCE.getArmorEquipmentSlot(stack);
            ItemStack currentArmor = player.getItemBySlot(armorSlot);
            if (!currentArmor.isEmpty() && !ToolUtil.INSTANCE.isBetterArmor(stack, currentArmor))
                return false;
        }

        var inventory = player.getInventory();
        for (int i = 0; i < Math.min(36, inventory.getContainerSize()); i++) {
            ItemStack invStack = inventory.getItem(i);
            if (ItemStack.matches(invStack, stack) && invStack.getCount() >= stack.getCount())
                return false;
        }

        return true;
    }

    private int nextDelay() {
        int min = minDelay.get();
        int max = maxDelay.get();
        int lower = Math.min(min, max);
        int upper = Math.max(min, max);

        return RandomUtil.nextInt(lower, upper + 1);
    }

}
