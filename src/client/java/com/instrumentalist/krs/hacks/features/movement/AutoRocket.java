package com.instrumentalist.krs.hacks.features.movement;

import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.math.MSTimer;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.krs.utils.value.ListValue;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public class AutoRocket extends Module {
    @Setting
    private final ListValue mode = new ListValue("Mode", new String[]{"Low Speed", "Jump", "Always"}, "Low Speed");

    @Setting
    private final FloatValue speed = new FloatValue("Speed", 0.7f, 0.1f, 3.0f, () -> mode.get().equalsIgnoreCase("low speed"));

    @Setting
    private final IntValue delay = new IntValue("Delay", 1500, 100, 5000, "ms");

    private final MSTimer rocketTimer = new MSTimer();

    public AutoRocket() {
        super("Auto Rocket", ModuleCategory.Movement, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    @Override
    public String tag() {
        return mode.get();
    }

    @Override
    public void onEnable() {
        rocketTimer.reset();
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        var player = mc.player;
        var gameMode = mc.gameMode;
        if (player == null || gameMode == null || !player.isFallFlying() || mc.gui.screen() != null || !rocketTimer.hasTimePassed(delay.get()) || !shouldUse())
            return;

        int slot = findRocketSlot();
        if (slot == -1)
            return;

        int oldSlot = player.getInventory().getSelectedSlot();
        if (slot != oldSlot) {
            player.getInventory().setSelectedSlot(slot);
            PacketUtil.sendPacket(new ServerboundSetCarriedItemPacket(slot));
        }

        if (gameMode.useItem(player, InteractionHand.MAIN_HAND).consumesAction())
            PacketUtil.sendPacket(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));

        if (slot != oldSlot) {
            player.getInventory().setSelectedSlot(oldSlot);
            PacketUtil.sendPacket(new ServerboundSetCarriedItemPacket(oldSlot));
        }

        rocketTimer.reset();
    }

    private boolean shouldUse() {
        String currentMode = mode.get().toLowerCase(Locale.ROOT);
        return switch (currentMode) {
            case "jump" -> mc.options.keyJump.isDown();
            case "always" -> true;
            default -> MovementUtil.getSpeed() <= speed.get();
        };
    }

    private int findRocketSlot() {
        var player = mc.player;
        if (player == null)
            return -1;

        for (int i = 0; i <= 8; i++) {
            if (player.getInventory().getItem(i).getItem() == Items.FIREWORK_ROCKET)
                return i;
        }
        return -1;
    }
}
