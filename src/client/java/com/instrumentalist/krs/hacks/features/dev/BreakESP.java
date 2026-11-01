package com.instrumentalist.krs.hacks.features.dev;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.ReceivedPacketEvent;
import com.instrumentalist.krs.events.features.Render3DEvent;
import com.instrumentalist.krs.events.features.TickEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.render.BlockHighlight;
import com.instrumentalist.krs.utils.render.RenderColors;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;

public class BreakESP extends Module {
    private static final long ENTRY_TIMEOUT_MILLIS = 6_000L;
    private static final int MAX_ENTRIES = 128;

    @Setting
    private final FloatValue range = new FloatValue("Range", 48f, 8f, 128f, "m");

    @Setting
    private final BooleanValue bedAlert = new BooleanValue("Bed Alert", true);

    private final Long2ObjectOpenHashMap<Entry> entries = new Long2ObjectOpenHashMap<>();
    private final ArrayList<Entry> sorted = new ArrayList<>();
    private final ArrayList<BlockPos> highlighted = new ArrayList<>();
    private final BlockHighlight highlight = new BlockHighlight(0.3f, RenderColors.BREAK_ESP);

    public BreakESP() {
        super("Break ESP", ModuleCategory.Dev, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public String tag() {
        return entries.isEmpty() ? null : String.valueOf(entries.size());
    }

    @Override
    public void onEnable() {
        clear();
    }

    @Override
    public void onDisable() {
        clear();
    }

    @Override
    public void onWorld(WorldEvent event) {
        clear();
    }

    private void clear() {
        entries.clear();
        sorted.clear();
        highlighted.clear();
        highlight.release();
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
        if (!(event.packet instanceof ClientboundBlockDestructionPacket packet))
            return;

        if (mc.isSameThread())
            handle(packet);
        else
            mc.execute(() -> handle(packet));
    }

    private void handle(ClientboundBlockDestructionPacket packet) {
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || packet.getId() == player.getId())
            return;

        BlockPos pos = packet.getPos().immutable();
        long key = pos.asLong();
        int stage = packet.getProgress();
        if (stage < 0 || stage >= 10) {
            entries.remove(key);
            return;
        }

        Entry entry = entries.get(key);
        if (entry == null) {
            if (entries.size() >= MAX_ENTRIES)
                return;
            entry = new Entry(pos);
            entries.put(key, entry);
        }

        entry.stage = stage;
        entry.updatedMillis = System.currentTimeMillis();
        Entity breaker = level.getEntity(packet.getId());
        if (breaker != null)
            entry.breakerName = breaker.getName().getString();

        BlockState state = level.getBlockState(pos);
        if (bedAlert.get() && !entry.alerted && state.getBlock() instanceof BedBlock) {
            entry.alerted = true;
            Client.notificationManager.addNotification("Bed Alert",
                    (entry.breakerName != null ? entry.breakerName : "Someone") + " is breaking a bed at " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
        }
    }

    @Override
    public void onTick(TickEvent event) {
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            entries.clear();
            return;
        }

        long now = System.currentTimeMillis();
        double maxDistance = range.get() * range.get();
        entries.values().removeIf(entry -> now - entry.updatedMillis > ENTRY_TIMEOUT_MILLIS
                || level.getBlockState(entry.pos).isAir()
                || entry.pos.distToCenterSqr(player.position()) > maxDistance);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || entries.isEmpty()) {
            highlight.release();
            return;
        }

        sorted.clear();
        sorted.addAll(entries.values());
        var position = player.position();
        sorted.sort(Comparator.comparingDouble(entry -> entry.pos.distToCenterSqr(position)));

        highlighted.clear();
        for (int i = 0; i < sorted.size() && i < 64; i++)
            highlighted.add(sorted.get(i).pos);
        highlight.setTargets(highlighted);
    }

    private static final class Entry {
        private final BlockPos pos;
        private String breakerName;
        private int stage;
        private long updatedMillis;
        private boolean alerted;

        private Entry(BlockPos pos) {
            this.pos = pos;
        }
    }
}
