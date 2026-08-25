package com.instrumentalist.krs.hacks.features.combat;

import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import com.instrumentalist.krs.utils.value.IntValue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import org.lwjgl.glfw.GLFW;

public class FastBow extends Module {

    public FastBow() {
        super("Fast Bow", ModuleCategory.Combat, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    @Setting
    private final IntValue packets = new IntValue("Packets", 20, 1, 20, "x");

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

        ItemStack useItem = player.getUseItem();
        if (useItem.getUseAnimation() != ItemUseAnimation.BOW
                || player.getTicksUsingItem() < chargeTicks.get())
            return;

        InteractionHand hand = player.getUsedItemHand();
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        boolean onGround = player.onGround();
        boolean horizontalCollision = player.horizontalCollision;
        int packetCount = packets.get();
        for (int i = 0; i < packetCount; i++) {
            PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Rot(yaw, pitch, onGround, horizontalCollision));
        }

        PacketUtil.sendPacket(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM,
                BlockPos.ZERO,
                Direction.DOWN
        ));

        // The old implementation only stopped use on the server. The client
        // consequently stayed in its using state and never sent the next use
        // packet while the key was held. Keep both sides in sync, then begin
        // the next draw immediately instead of waiting for the vanilla delay.
        player.releaseUsingItem();
        if (mc.options.keyUse.isDown()
                && player.getItemInHand(hand).getUseAnimation() == ItemUseAnimation.BOW)
            gameMode.useItem(player, hand);
    }
}
