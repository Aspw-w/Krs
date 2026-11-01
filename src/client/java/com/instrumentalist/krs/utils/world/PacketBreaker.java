package com.instrumentalist.krs.utils.world;

import com.instrumentalist.krs.hacks.features.dev.AutoToolSaver;
import com.instrumentalist.krs.utils.entity.PlayerUtil;
import com.instrumentalist.krs.utils.math.ToolUtil;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class PacketBreaker {
    public enum State {
        IDLE,
        MINING,
        CONFIRMING,
        BROKEN,
        FAILED
    }

    private static final int CONFIRM_TIMEOUT_TICKS = 20;
    private static boolean sending;

    private final Minecraft mc = Minecraft.getInstance();
    private BlockPos target;
    private BlockPos lastTarget;
    private Direction direction;
    private Block block;
    private int ticks;
    private float progress;
    private int ticksLeft;
    private boolean finished;
    private int confirmTicks;

    public static boolean isSending() {
        return sending;
    }

    public boolean isActive() {
        return target != null;
    }

    public boolean isFinished() {
        return finished;
    }

    public BlockPos target() {
        return target;
    }

    public BlockPos lastTarget() {
        return lastTarget;
    }

    public float progress() {
        return Math.clamp(progress, 0f, 1f);
    }

    public int ticksLeft() {
        return ticksLeft;
    }

    public void track(BlockPos pos, Direction face, Block trackedBlock) {
        target = pos.immutable();
        lastTarget = target;
        direction = face;
        block = trackedBlock;
        ticks = 0;
        progress = 0f;
        ticksLeft = 0;
        finished = false;
        confirmTicks = 0;
    }

    public boolean start(BlockPos pos, Direction face) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null)
            return false;

        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F)
            return false;

        track(pos, face, state.getBlock());
        send(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);
        PlayerUtil.INSTANCE.swingHand(InteractionHand.MAIN_HAND);
        return true;
    }

    public void markFinished() {
        finished = true;
        confirmTicks = 0;
        progress = 1f;
        ticksLeft = 0;
    }

    public State tick(boolean bestTool, double range) {
        if (target == null)
            return State.IDLE;

        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || player.getAbilities().instabuild || player.isSpectator()) {
            reset();
            return State.FAILED;
        }

        BlockState state = level.getBlockState(target);
        if (state.isAir()) {
            reset();
            return State.BROKEN;
        }

        if (finished) {
            if (++confirmTicks >= CONFIRM_TIMEOUT_TICKS) {
                reset();
                return State.FAILED;
            }
            return State.CONFIRMING;
        }

        if (state.getBlock() != block || !isInRange(player, target, range)) {
            abort();
            return State.FAILED;
        }

        int toolSlot = bestTool ? safeBestTool(player, target) : -1;
        float rate = destroyProgressWith(player, level, state, target, toolSlot);
        if (rate <= 0.0F) {
            abort();
            return State.FAILED;
        }

        ticks++;
        progress = rate * (ticks + 1);
        ticksLeft = Math.max(0, (int) Math.ceil(1.0F / rate) - 1 - ticks);
        if (progress < 1.0F)
            return State.MINING;

        finish(player, toolSlot);
        return State.CONFIRMING;
    }

    public void abort() {
        if (target != null && !finished)
            send(ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK);
        reset();
    }

    public void reset() {
        target = null;
        direction = null;
        block = null;
        ticks = 0;
        progress = 0f;
        ticksLeft = 0;
        finished = false;
        confirmTicks = 0;
    }

    public static boolean isInRange(LocalPlayer player, BlockPos pos, double range) {
        double reach = Math.min(range, player.blockInteractionRange() + 0.95D);
        return new AABB(pos).distanceToSqr(player.getEyePosition()) <= reach * reach;
    }

    private static int safeBestTool(LocalPlayer player, BlockPos pos) {
        int slot = ToolUtil.INSTANCE.findBestTool(pos);
        if (slot < 0 || AutoToolSaver.isAboutToBreak(player.getInventory().getItem(slot)))
            return -1;
        return slot;
    }

    private static float destroyProgressWith(LocalPlayer player, ClientLevel level, BlockState state, BlockPos pos, int slot) {
        Inventory inventory = player.getInventory();
        int selected = inventory.getSelectedSlot();
        if (slot < 0 || slot == selected)
            return state.getDestroyProgress(player, level, pos);

        inventory.setSelectedSlot(slot);
        try {
            return state.getDestroyProgress(player, level, pos);
        } finally {
            inventory.setSelectedSlot(selected);
        }
    }

    private void finish(LocalPlayer player, int toolSlot) {
        int selected = player.getInventory().getSelectedSlot();
        boolean swap = toolSlot >= 0 && toolSlot != selected;
        sending = true;
        try {
            if (swap)
                PacketUtil.sendPacket(new ServerboundSetCarriedItemPacket(toolSlot));
            PacketUtil.sendPacket(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, target, direction));
            PlayerUtil.INSTANCE.swingHand(InteractionHand.MAIN_HAND);
            if (swap)
                PacketUtil.sendPacket(new ServerboundSetCarriedItemPacket(selected));
        } finally {
            sending = false;
        }
        markFinished();
    }

    private void send(ServerboundPlayerActionPacket.Action action) {
        if (target == null || mc.getConnection() == null)
            return;

        sending = true;
        try {
            PacketUtil.sendPacket(new ServerboundPlayerActionPacket(action, target, direction != null ? direction : Direction.DOWN));
        } finally {
            sending = false;
        }
    }
}
