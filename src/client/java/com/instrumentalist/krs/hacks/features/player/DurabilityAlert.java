package com.instrumentalist.krs.hacks.features.player;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.IntValue;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;

public class DurabilityAlert extends Module {
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };
    private static final EquipmentSlot[] HAND_SLOTS = {
            EquipmentSlot.MAINHAND,
            EquipmentSlot.OFFHAND
    };

    @Setting
    private final IntValue threshold = new IntValue("Threshold", 15, 1, 50, "%");

    @Setting
    private final IntValue repeatDelay = new IntValue("Repeat Delay", 30, 0, 300, "s");

    @Setting
    private final BooleanValue armor = new BooleanValue("Armor", true);

    @Setting
    private final BooleanValue hands = new BooleanValue("Hands", true);

    private final EnumMap<EquipmentSlot, SlotState> slotStates = new EnumMap<>(EquipmentSlot.class);
    private int checkTicks;

    public DurabilityAlert() {
        super("Durability Alert", ModuleCategory.Player, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    @Override
    public String tag() {
        return threshold.get() + "%";
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
        reset();
    }

    @Override
    public void onWorld(WorldEvent event) {
        reset();
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null) {
            reset();
            return;
        }

        if (++checkTicks < 5)
            return;
        checkTicks = 0;

        if (armor.get()) {
            for (EquipmentSlot slot : ARMOR_SLOTS)
                checkSlot(slot);
        } else {
            for (EquipmentSlot slot : ARMOR_SLOTS)
                slotStates.remove(slot);
        }

        if (hands.get()) {
            for (EquipmentSlot slot : HAND_SLOTS)
                checkSlot(slot);
        } else {
            for (EquipmentSlot slot : HAND_SLOTS)
                slotStates.remove(slot);
        }
    }

    private void checkSlot(EquipmentSlot slot) {
        ItemStack stack = mc.player.getItemBySlot(slot);
        if (stack.isEmpty() || !stack.isDamageableItem() || stack.getMaxDamage() <= 0) {
            slotStates.remove(slot);
            return;
        }

        int remaining = Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
        int remainingPercent = (int) Math.ceil(remaining * 100.0 / stack.getMaxDamage());
        SlotState state = slotStates.get(slot);
        if (state == null || state.item != stack.getItem() || state.maxDamage != stack.getMaxDamage()) {
            state = new SlotState(stack.getItem(), stack.getMaxDamage());
            slotStates.put(slot, state);
        }

        if (remainingPercent > Math.min(100, threshold.get() + 5)) {
            state.warned = false;
            state.lastAlertNanos = 0L;
        }

        long now = System.nanoTime();
        long repeatNanos = repeatDelay.get() * 1_000_000_000L;
        boolean repeatReady = repeatDelay.get() > 0
                && state.lastAlertNanos > 0L
                && now - state.lastAlertNanos >= repeatNanos;
        if (remainingPercent <= threshold.get() && (!state.warned || repeatReady)) {
            String message = slotName(slot) + " " + stack.getHoverName().getString()
                    + ": " + remaining + " durability (" + remainingPercent + "%)";
            if (Client.notificationManager != null)
                Client.notificationManager.addNotification("Low Durability", message);
            state.warned = true;
            state.lastAlertNanos = now;
        }
    }

    private String slotName(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> "Helmet";
            case CHEST -> "Chest";
            case LEGS -> "Leggings";
            case FEET -> "Boots";
            case MAINHAND -> "Main hand";
            case OFFHAND -> "Offhand";
            default -> slot.getName();
        };
    }

    private void reset() {
        slotStates.clear();
        checkTicks = 0;
    }

    private static final class SlotState {
        private final Item item;
        private final int maxDamage;
        private boolean warned;
        private long lastAlertNanos;

        private SlotState(Item item, int maxDamage) {
            this.item = item;
            this.maxDamage = maxDamage;
        }
    }
}
