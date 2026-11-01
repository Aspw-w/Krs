package com.instrumentalist.krs.hacks.features.level;

import com.instrumentalist.krs.events.features.Render3DEvent;
import com.instrumentalist.krs.events.features.SendPacketEvent;
import com.instrumentalist.krs.events.features.TickEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.world.PacketBreaker;
import com.instrumentalist.krs.hacks.features.dev.QueuedMiner;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.render.BlockHighlight;
import com.instrumentalist.krs.utils.render.RenderColors;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.level.block.state.BlockState;

public class PacketMine extends Module {
    private static final long DONE_FLASH_MILLIS = 350L;

    @Setting
    private final FloatValue range = new FloatValue("Range", 6f, 1f, 6f, "m");

    @Setting
    private final BooleanValue bestTool = new BooleanValue("Best Tool", true);

    private final PacketBreaker breaker = new PacketBreaker();
    private final BlockHighlight highlight = new BlockHighlight(0.3f, RenderColors.PACKET_MINE);

    public PacketMine() {
        super("Packet Mine", ModuleCategory.Level, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public String tag() {
        return breaker.isActive() ? Math.round(breaker.progress() * 100f) + "%" : null;
    }

    @Override
    public void onEnable() {
        breaker.reset();
    }

    @Override
    public void onDisable() {
        breaker.abort();
        highlight.release();
    }

    @Override
    public void onWorld(WorldEvent event) {
        breaker.reset();
        highlight.release();
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
        if (PacketBreaker.isSending() || QueuedMiner.isInternalMining() || !(event.packet instanceof ServerboundPlayerActionPacket packet))
            return;

        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null)
            return;

        BlockPos pos = packet.getPos();
        BlockPos target = breaker.target();
        switch (packet.getAction()) {
            case START_DESTROY_BLOCK -> {
                if (player.getAbilities().instabuild)
                    return;

                BlockState state = level.getBlockState(pos);
                if (!state.isAir() && state.getDestroySpeed(level, pos) >= 0.0F)
                    breaker.track(pos, packet.getDirection(), state.getBlock());
            }
            case ABORT_DESTROY_BLOCK -> {
                if (target != null && !breaker.isFinished() && pos.equals(target))
                    event.cancel();
            }
            case STOP_DESTROY_BLOCK -> {
                if (target != null && pos.equals(target))
                    breaker.markFinished();
            }
            default -> {
            }
        }
    }

    @Override
    public void onTick(TickEvent event) {
        PacketBreaker.State state = breaker.tick(bestTool.get(), range.get());
        if (state == PacketBreaker.State.BROKEN && breaker.lastTarget() != null)
            highlight.flash(breaker.lastTarget(), DONE_FLASH_MILLIS);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        BlockPos target = breaker.target();
        if (target == null)
            highlight.release();
        else
            highlight.setTarget(target);
    }
}
