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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import com.instrumentalist.krs.utils.input.Keys;

public class FastUse extends Module {
    private static final int MAX_FOOD_LEVEL = 20;

    public FastUse() {
        super("Fast Use", ModuleCategory.Combat, Keys.KEY_UNKNOWN, false, true);
    }

    @Setting
    private final BooleanValue fastBow = new BooleanValue("Fast Bow", false);

    @Setting
    private final IntValue bowPackets = new IntValue("Bow Packets", 20, 1, 20, "x", fastBow::get);

    @Setting
    private final IntValue bowChargeTicks = new IntValue("Bow Charge Ticks", 1, 1, 20, "ticks", fastBow::get);

    @Setting
    private static final BooleanValue fastEat = new BooleanValue("Fast Eat", false);

    @Setting
    private final IntValue eatPackets = new IntValue("Eat Packets", 32, 1, 32, "x", fastEat::get);

    @Setting
    private final IntValue eatChargeTicks = new IntValue("Eat Charge Ticks", 1, 1, 20, "ticks", fastEat::get);

    @Setting
    private final BooleanValue eatAdaptive = new BooleanValue("Eat Adaptive", true, fastEat::get);

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
        if (!waitingForRelease || !ModuleManager.getModuleState(FastUse.class) || !fastEat.get())
            return false;

        LocalPlayer player = mc.player;
        return player != null && isFoodOrDrink(player.getMainHandItem());
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        if (fastBow.get())
            updateBow();

        if (fastEat.get())
            updateEat();
        else
            resetAdaptive();
    }

    private void updateBow() {
        var player = mc.player;
        var gameMode = mc.gameMode;
        if (player == null || gameMode == null || !player.isUsingItem()) return;

        ItemStack useItem = player.getUseItem();
        if (useItem.getUseAnimation() != ItemUseAnimation.BOW
                || player.getTicksUsingItem() < bowChargeTicks.get())
            return;

        InteractionHand hand = player.getUsedItemHand();
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        boolean onGround = player.onGround();
        boolean horizontalCollision = player.horizontalCollision;
        int packetCount = bowPackets.get();
        for (int i = 0; i < packetCount; i++) {
            PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Rot(yaw, pitch, onGround, horizontalCollision));
        }

        PacketUtil.sendPacket(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM,
                BlockPos.ZERO,
                Direction.DOWN
        ));

        player.releaseUsingItem();
        if (mc.options.keyUse.isDown()
                && player.getItemInHand(hand).getUseAnimation() == ItemUseAnimation.BOW)
            gameMode.useItem(player, hand);
    }

    private void updateEat() {
        var player = mc.player;
        var gameMode = mc.gameMode;
        if (player == null || gameMode == null) return;

        if (!eatAdaptive.get() || !mc.options.keyUse.isDown())
            resetAdaptive();

        if (!player.isUsingItem()) return;

        ItemStack useItem = player.getMainHandItem();
        if (!isFoodOrDrink(useItem)
                || player.getTicksUsingItem() < eatChargeTicks.get())
            return;

        if (eatAdaptive.get() && remainingEats < 0)
            remainingEats = plannedEats(player, useItem);

        boolean onGround = player.onGround();
        boolean horizontalCollision = player.horizontalCollision;
        int packetCount = eatPackets.get();
        for (int i = 0; i < packetCount; i++) {
            PacketUtil.sendPacket(new ServerboundMovePlayerPacket.StatusOnly(onGround, horizontalCollision));
        }

        player.stopUsingItem();

        if (eatAdaptive.get() && --remainingEats <= 0) {
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
