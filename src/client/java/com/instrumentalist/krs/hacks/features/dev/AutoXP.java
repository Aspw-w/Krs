package com.instrumentalist.krs.hacks.features.dev;

import com.instrumentalist.krs.events.features.TickEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.entity.CombatTargets;
import com.instrumentalist.krs.utils.entity.PlayerUtil;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.IntValue;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;

public class AutoXP extends Module {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    @Setting
    private final FloatValue threshold = new FloatValue("Durability", 75f, 10f, 99f, "%");

    @Setting
    private final IntValue delay = new IntValue("Delay", 2, 1, 10, "ticks");

    @Setting
    private final BooleanValue onlyMending = new BooleanValue("Only Mending", true);

    @Setting
    private final BooleanValue onlyOnGround = new BooleanValue("Only On Ground", true);

    @Setting
    private final FloatValue enemyRange = new FloatValue("Pause Near Enemy", 0f, 0f, 16f, "m");

    private int restoreSlot = -1;
    private int bottleSlot = -1;
    private int swapTick;
    private int lastThrowTick;

    public AutoXP() {
        super("Auto XP", ModuleCategory.Dev, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onEnable() {
        restoreSlot = -1;
        bottleSlot = -1;
    }

    @Override
    public void onDisable() {
        restore();
    }

    @Override
    public void onWorld(WorldEvent event) {
        restoreSlot = -1;
        bottleSlot = -1;
    }

    @Override
    public void onTick(TickEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null || mc.gameMode.getPlayerMode() == GameType.SPECTATOR) {
            restoreSlot = -1;
            bottleSlot = -1;
            return;
        }

        if (!shouldRepair(player)) {
            restore();
            return;
        }

        Inventory inventory = player.getInventory();
        if (bottleSlot == -1 || !inventory.getItem(bottleSlot).is(Items.EXPERIENCE_BOTTLE)) {
            int slot = findBottle(inventory);
            if (slot == -1) {
                restore();
                return;
            }

            int selected = inventory.getSelectedSlot();
            if (slot != selected && restoreSlot == -1) {
                restoreSlot = selected;
                PlayerUtil.INSTANCE.doSpoof(selected);
            }
            bottleSlot = slot;
            inventory.setSelectedSlot(slot);
            swapTick = player.tickCount;
            return;
        }

        if (inventory.getSelectedSlot() != bottleSlot) {
            inventory.setSelectedSlot(bottleSlot);
            swapTick = player.tickCount;
            return;
        }
        if (player.tickCount <= swapTick || player.tickCount - lastThrowTick < delay.get())
            return;
        if (player.getCooldowns().isOnCooldown(inventory.getItem(bottleSlot)))
            return;

        PacketUtil.sendPacket(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 0, player.getYRot(), 90f));
        PlayerUtil.INSTANCE.swingHand(InteractionHand.MAIN_HAND);
        lastThrowTick = player.tickCount;
    }

    private boolean shouldRepair(LocalPlayer player) {
        if (mc.gui.screen() != null || player.isUsingItem())
            return false;
        if (onlyOnGround.get() && !player.onGround())
            return false;
        if (enemyRange.get() > 0f && CombatTargets.nearest(enemyRange.get(), true) != null)
            return false;

        for (EquipmentSlot slot : ARMOR) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isDamageableItem() || !stack.isDamaged())
                continue;
            if (onlyMending.get() && !hasMending(stack))
                continue;

            float durability = 100f * (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage();
            if (durability < threshold.get())
                return true;
        }
        return false;
    }

    private static boolean hasMending(ItemStack stack) {
        for (var entry : stack.getEnchantments().entrySet()) {
            if (entry.getKey().is(Enchantments.MENDING))
                return true;
        }
        return false;
    }

    private static int findBottle(Inventory inventory) {
        if (inventory.getItem(inventory.getSelectedSlot()).is(Items.EXPERIENCE_BOTTLE))
            return inventory.getSelectedSlot();
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            if (inventory.getItem(slot).is(Items.EXPERIENCE_BOTTLE))
                return slot;
        }
        return -1;
    }

    private void restore() {
        LocalPlayer player = mc.player;
        if (player != null && restoreSlot != -1) {
            Inventory inventory = player.getInventory();
            if (inventory.getSelectedSlot() == bottleSlot)
                inventory.setSelectedSlot(restoreSlot);
            PlayerUtil.INSTANCE.stopSpoof();
        }
        restoreSlot = -1;
        bottleSlot = -1;
    }
}
