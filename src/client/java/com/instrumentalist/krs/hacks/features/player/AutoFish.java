package com.instrumentalist.krs.hacks.features.player;



import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.math.MSTimer;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import com.instrumentalist.krs.utils.value.ListValue;
import org.lwjgl.glfw.GLFW;

import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class AutoFish extends Module {

    public AutoFish() {
        super("Auto Fish", ModuleCategory.Player, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    @Setting
    private static final ListValue handMode = new ListValue("Interaction Hand Mode", new String[]{"Mainhand", "Offhand"}, "Mainhand");

    private final MSTimer fishTimer = new MSTimer();

    @Override
    public void onDisable() {
    }

    @Override
    public void onEnable() {
    }

    private static InteractionHand fishHand(String mode) {
        return mode.equalsIgnoreCase("offhand") ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        var player = mc.player;
        var gameMode = mc.gameMode;
        if (player == null || gameMode == null) return;

        String mode = handMode.get();
        InteractionHand hand = fishHand(mode);
        if ((hand == InteractionHand.MAIN_HAND ? player.getMainHandItem() : player.getOffhandItem()).getItem() != Items.FISHING_ROD)
            return;

        if (fishTimer.hasTimePassed(500L)) {
            var hook = player.fishing;
            boolean canFish = hook == null;
            if (!canFish) {
                Vec3 delta = hook.getDeltaMovement();
                canFish = delta.x == 0.0 && -0.2 >= delta.y && delta.z == 0.0;
            }

            if (!canFish)
                return;

            Client.notificationManager.addNotification(hook == null ? "Wait" : "Success", hook == null ? "Started fishing... (" + mode + ")" : "Fished (" + mode + ")");
            if (gameMode.useItem(player, hand).consumesAction())
                PacketUtil.sendPacket(new ServerboundSwingPacket(hand));
            fishTimer.reset();
        }
    }
}
