package com.instrumentalist.krs.hacks.features.combat;

import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import com.instrumentalist.krs.utils.value.IntValue;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import org.lwjgl.glfw.GLFW;

public class FastEat extends Module {

    public FastEat() {
        super("Fast Eat", ModuleCategory.Combat, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    @Setting
    private final IntValue packets = new IntValue("Packets", 32, 1, 32, "x");

    @Setting
    private final IntValue chargeTicks = new IntValue("Charge Ticks", 1, 1, 20, "ticks");

    @Override
    public void onDisable() {
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        var player = mc.player;
        var gameMode = mc.gameMode;
        if (player == null || gameMode == null || !player.isUsingItem()) return;

        ItemStack useItem = player.getMainHandItem();
        if (!isFoodOrDrink(useItem)
                || player.getTicksUsingItem() < chargeTicks.get())
            return;

        boolean onGround = player.onGround();
        boolean horizontalCollision = player.horizontalCollision;
        int packetCount = packets.get();
        for (int i = 0; i < packetCount; i++) {
            PacketUtil.sendPacket(new ServerboundMovePlayerPacket.StatusOnly(onGround, horizontalCollision));
        }

        player.stopUsingItem();
        if (mc.options.keyUse.isDown() && isFoodOrDrink(player.getMainHandItem()))
            gameMode.useItem(player, net.minecraft.world.InteractionHand.MAIN_HAND);
    }

    private static boolean isFoodOrDrink(ItemStack stack) {
        ItemUseAnimation animation = stack.getUseAnimation();
        return animation == ItemUseAnimation.EAT || animation == ItemUseAnimation.DRINK;
    }
}
