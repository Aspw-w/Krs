package com.instrumentalist.krs.hacks.features.dev;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.ReceivedPacketEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.ChatUtil;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.value.BooleanValue;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PopCounter extends Module {
    @Setting
    private final BooleanValue chat = new BooleanValue("Chat", true);

    @Setting
    private final BooleanValue notify = new BooleanValue("Notify", true);

    @Setting
    private final BooleanValue self = new BooleanValue("Self", false);

    private static final Map<UUID, Integer> POPS = new HashMap<>();

    public PopCounter() {
        super("Pop Counter", ModuleCategory.Dev, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onEnable() {
        POPS.clear();
    }

    @Override
    public void onDisable() {
        POPS.clear();
    }

    @Override
    public void onWorld(WorldEvent event) {
        POPS.clear();
    }

    public static int getPops(Entity entity) {
        return entity == null ? 0 : POPS.getOrDefault(entity.getUUID(), 0);
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
        if (!(event.packet instanceof ClientboundEntityEventPacket packet))
            return;

        byte id = packet.getEventId();
        if (id != EntityEvent.PROTECTED_FROM_DEATH && id != EntityEvent.DEATH)
            return;

        if (mc.isSameThread())
            handle(packet, id);
        else
            mc.execute(() -> handle(packet, id));
    }

    private void handle(ClientboundEntityEventPacket packet, byte id) {
        ClientLevel level = mc.level;
        if (level == null || !(packet.getEntity(level) instanceof Player player))
            return;
        if (player == mc.player && !self.get())
            return;

        String name = player == mc.player ? "You" : player.getName().getString();
        if (id == EntityEvent.PROTECTED_FROM_DEATH) {
            int pops = POPS.merge(player.getUUID(), 1, Integer::sum);
            report("Totem Pop", name + " popped " + pops + (pops == 1 ? " totem" : " totems"));
            return;
        }

        Integer pops = POPS.remove(player.getUUID());
        if (pops != null && pops > 0)
            report("Totem Pop", name + " died after popping " + pops + (pops == 1 ? " totem" : " totems"));
    }

    private void report(String title, String message) {
        if (chat.get())
            ChatUtil.printChat(message);
        if (notify.get())
            Client.notificationManager.addNotification(title, message);
    }
}
