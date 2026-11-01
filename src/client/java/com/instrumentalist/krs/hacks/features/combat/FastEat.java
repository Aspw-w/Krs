package com.instrumentalist.krs.hacks.features.combat;

import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.IntValue;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import com.instrumentalist.krs.utils.input.Keys;

public class FastEat extends Module {
    private static final int MAX_FOOD_LEVEL = 20;

    public FastEat() {
        super("Fast Eat", ModuleCategory.Combat, Keys.KEY_UNKNOWN, false, true);
    }

    @Setting
    private final IntValue packets = new IntValue("Packets", 32, 1, 32, "x");

    @Setting
    private final IntValue chargeTicks = new IntValue("Charge Ticks", 1, 1, 20, "ticks");

    @Setting
    private final BooleanValue adaptive = new BooleanValue("Adaptive", true);

    private static boolean waitingForRelease;
    private int remainingEats = -1;

    @Override
    public void onDisable() {
        resetAdaptive();
    }

    @Override
    public void onEnable() {
        resetAdaptive();
    }

    @Override
    public void onWorld(WorldEvent event) {
        resetAdaptive();
    }

    public static boolean shouldBlockUse() {
        if (!waitingForRelease || !ModuleManager.getModuleState(FastEat.class))
            return false;

        LocalPlayer player = mc.player;
        return player != null && isFoodOrDrink(player.getMainHandItem());
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        var player = mc.player;
        var gameMode = mc.gameMode;
        if (player == null || gameMode == null) return;

        if (!adaptive.get() || !mc.options.keyUse.isDown())
            resetAdaptive();

        if (!player.isUsingItem()) return;

        ItemStack useItem = player.getMainHandItem();
        if (!isFoodOrDrink(useItem)
                || player.getTicksUsingItem() < chargeTicks.get())
            return;

        if (adaptive.get() && remainingEats < 0)
            remainingEats = plannedEats(player, useItem);

        boolean onGround = player.onGround();
        boolean horizontalCollision = player.horizontalCollision;
        int packetCount = packets.get();
        for (int i = 0; i < packetCount; i++) {
            PacketUtil.sendPacket(new ServerboundMovePlayerPacket.StatusOnly(onGround, horizontalCollision));
        }

        player.stopUsingItem();

        if (adaptive.get() && --remainingEats <= 0) {
            waitingForRelease = true;
            return;
        }

        if (mc.options.keyUse.isDown() && isFoodOrDrink(player.getMainHandItem()))
            gameMode.useItem(player, InteractionHand.MAIN_HAND);
    }

    private void resetAdaptive() {
        remainingEats = -1;
        waitingForRelease = false;
    }

    private static int plannedEats(LocalPlayer player, ItemStack stack) {
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food == null || food.canAlwaysEat() || food.nutrition() <= 0)
            return 1;

        int missing = MAX_FOOD_LEVEL - player.getFoodData().getFoodLevel();
        if (missing <= 0)
            return 1;

        int eats = (missing + food.nutrition() - 1) / food.nutrition();
        return Math.clamp(eats, 1, Math.max(1, stack.getCount()));
    }

    private static boolean isFoodOrDrink(ItemStack stack) {
        ItemUseAnimation animation = stack.getUseAnimation();
        return animation == ItemUseAnimation.EAT || animation == ItemUseAnimation.DRINK;
    }
}
