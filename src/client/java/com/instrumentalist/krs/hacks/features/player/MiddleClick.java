package com.instrumentalist.krs.hacks.features.player;

import com.instrumentalist.krs.events.features.MouseClickEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.math.MSTimer;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.krs.utils.value.ListValue;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import com.instrumentalist.krs.utils.input.Keys;

import java.util.Locale;

public class MiddleClick extends Module {
    @Setting
    private final ListValue itemMode = new ListValue("Item", new String[]{"Pearl", "Firework", "Wind Charge"}, "Pearl");

    @Setting
    private final IntValue delay = new IntValue("Delay", 250, 0, 2000, "ms");

    private final MSTimer useTimer = new MSTimer();

    public MiddleClick() {
        super("Middle Click", ModuleCategory.Player, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onEnable() {
        useTimer.reset();
    }

    @Override
    public void onDisable() {
    }

    @Override
    public String tag() {
        return itemMode.get();
    }

    @Override
    public void onMouseClick(MouseClickEvent event) {
        var player = mc.player;
        var gameMode = mc.gameMode;
        if (player == null || gameMode == null || mc.gui.screen() != null || event.button != Keys.MOUSE_BUTTON_MIDDLE || event.action != Keys.PRESS || !useTimer.hasTimePassed(delay.get()))
            return;

        int slot = findItemSlot(targetItem());
        if (slot == -1)
            return;

        int oldSlot = player.getInventory().getSelectedSlot();
        if (slot != oldSlot) {
            player.getInventory().setSelectedSlot(slot);
            PacketUtil.sendPacket(new ServerboundSetCarriedItemPacket(slot));
        }

        // The server broadcasts the swing for successful item use on its own since 26.3.
        gameMode.useItem(player, InteractionHand.MAIN_HAND);

        if (slot != oldSlot) {
            player.getInventory().setSelectedSlot(oldSlot);
            PacketUtil.sendPacket(new ServerboundSetCarriedItemPacket(oldSlot));
        }

        useTimer.reset();
        event.cancel();
    }

    private Item targetItem() {
        return switch (itemMode.get().toLowerCase(Locale.ROOT)) {
            case "firework" -> Items.FIREWORK_ROCKET;
            case "wind charge" -> Items.WIND_CHARGE;
            default -> Items.ENDER_PEARL;
        };
    }

    private int findItemSlot(Item item) {
        var player = mc.player;
        if (player == null)
            return -1;

        for (int i = 0; i <= 8; i++) {
            if (player.getInventory().getItem(i).getItem() == item)
                return i;
        }
        return -1;
    }
}
