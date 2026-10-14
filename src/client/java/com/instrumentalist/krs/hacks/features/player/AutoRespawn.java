package com.instrumentalist.krs.hacks.features.player;

import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.krs.utils.value.ListValue;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import com.instrumentalist.krs.utils.input.Keys;

import java.util.Locale;

public class AutoRespawn extends Module {
    @Setting
    private final ListValue mode = new ListValue("Mode", new String[]{"Packet", "Client", "Both"}, "Packet");

    @Setting
    private final IntValue delay = new IntValue("Delay", 0, 0, 20, "t");

    @Setting
    private final BooleanValue closeDeathScreen = new BooleanValue("Close Death Screen", true);

    private int deathTicks = 0;
    private boolean requested = false;

    public AutoRespawn() {
        super("Auto Respawn", ModuleCategory.Player, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        reset();
    }

    @Override
    public String tag() {
        return mode.get();
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        var player = mc.player;
        if (player == null || !player.isDeadOrDying()) {
            reset();
            return;
        }

        if (deathTicks < delay.get()) {
            deathTicks++;
            return;
        }

        if (requested)
            return;

        switch (mode.get().toLowerCase(Locale.ROOT)) {
            case "client" -> player.respawn();
            case "both" -> {
                PacketUtil.sendPacket(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
                player.respawn();
            }
            default -> PacketUtil.sendPacket(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
        }
        if (closeDeathScreen.get() && mc.gui.screen() instanceof DeathScreen)
            mc.gui.setScreen(null);
        requested = true;
    }

    private void reset() {
        deathTicks = 0;
        requested = false;
    }
}
