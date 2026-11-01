package com.instrumentalist.krs.hacks.features.dev;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.CancelBlockBreakingEvent;
import com.instrumentalist.krs.events.features.MotionEvent;
import com.instrumentalist.krs.events.features.Render3DEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.player.AutoTool;
import com.instrumentalist.krs.hacks.features.player.Scaffold;
import com.instrumentalist.krs.utils.entity.PlayerUtil;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.math.ToolUtil;
import com.instrumentalist.krs.utils.render.BlockHighlight;
import com.instrumentalist.krs.utils.render.RenderColors;
import com.instrumentalist.mixin.injector.MultiPlayerGameModeAccessor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class QueuedMiner extends Module {
    private static final List<QueuedMiner> MINERS = new ArrayList<>();
    private static final int MAX_QUEUE = 256;
    private static final int MAX_HIGHLIGHTED = 64;
    private static final int MAX_EXPECTED_TICKS = 400;
    private static final long QUEUE_EXPIRY_MILLIS = 30_000L;
    private static final double QUEUE_DISCARD_DISTANCE_SQUARED = 32.0D * 32.0D;
    private static boolean internalMining;
    private static QueuedMiner activeMiner;

    private final LinkedHashMap<BlockPos, Long> queue = new LinkedHashMap<>();
    private final ArrayList<BlockPos> highlightedQueue = new ArrayList<>(MAX_HIGHLIGHTED);
    private final BlockHighlight queueHighlight;
    private final BlockHighlight targetHighlight;
    private final Color accent;
    private BlockPos current;
    private int currentTicks;
    private int currentTickLimit;
    private int originalSlot = -1;
    private boolean rotating;

    protected QueuedMiner(String name, Color accent) {
        super(name, ModuleCategory.Dev, Keys.KEY_UNKNOWN, false, true);
        this.accent = accent;
        this.queueHighlight = new BlockHighlight(0.2f, RenderColors.withAlpha(accent, 95));
        this.targetHighlight = new BlockHighlight(0.25f, accent);
        MINERS.removeIf(miner -> miner.getClass() == getClass());
        MINERS.add(this);
    }

    protected abstract float range();

    protected abstract boolean rotations();

    protected abstract int instantBlocksPerTick();

    protected abstract void onBlockBrokenByPlayer(BlockPos pos, BlockState state);

    public static boolean isInternalMining() {
        return internalMining;
    }

    public static boolean shouldSuppressManualMining() {
        QueuedMiner miner = activeMiner;
        return miner != null && miner.tempEnabled && miner.current != null;
    }

    public static void onBlockDestroyed(BlockPos pos, BlockState state) {
        if (internalMining || pos == null || state == null || state.isAir())
            return;

        for (QueuedMiner miner : MINERS) {
            if (miner.tempEnabled && mc.player != null && mc.level != null)
                miner.onBlockBrokenByPlayer(pos.immutable(), state);
        }
    }

    @Override
    public String tag() {
        int remaining = queue.size() + (current != null ? 1 : 0);
        return remaining > 0 ? String.valueOf(remaining) : null;
    }

    @Override
    public void onEnable() {
        clearAll();
    }

    @Override
    public void onDisable() {
        clearAll();
    }

    @Override
    public void onWorld(WorldEvent event) {
        clearAll();
    }

    private void clearAll() {
        stopWork();
        queue.clear();
        highlightedQueue.clear();
        queueHighlight.release();
        targetHighlight.release();
    }

    protected final void enqueue(BlockPos pos) {
        if (pos == null || queue.size() >= MAX_QUEUE || pos.equals(current))
            return;

        queue.putIfAbsent(pos.immutable(), System.currentTimeMillis());
    }

    protected final boolean isQueued(BlockPos pos) {
        return queue.containsKey(pos) || pos.equals(current);
    }

    protected final boolean canBreak(BlockPos pos) {
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        MultiPlayerGameMode gameMode = mc.gameMode;
        if (player == null || level == null || gameMode == null)
            return false;

        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getBlock() instanceof LiquidBlock)
            return false;
        if (state.getDestroySpeed(level, pos) < 0.0F)
            return false;
        if (!level.getWorldBorder().isWithinBounds(pos))
            return false;

        return !player.blockActionRestricted(level, pos, gameMode.getPlayerMode());
    }

    protected final boolean isInReach(BlockPos pos) {
        LocalPlayer player = mc.player;
        if (player == null)
            return false;

        double reach = effectiveRange(player);
        return new AABB(pos).distanceToSqr(player.getEyePosition()) <= reach * reach;
    }

    protected final double effectiveRange(LocalPlayer player) {
        return Math.min(range(), player.blockInteractionRange() + 0.95D);
    }

    @Override
    public void onCancelBlockBreaking(CancelBlockBreakingEvent event) {
        if (!internalMining && activeMiner == this && current != null)
            event.cancel();
    }

    @Override
    public void onMotion(MotionEvent event) {
        if (event.post)
            return;

        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        MultiPlayerGameMode gameMode = mc.gameMode;
        if (player == null || level == null || gameMode == null)
            return;

        pruneQueue(player);

        if (activeMiner != null && activeMiner != this && activeMiner.tempEnabled && activeMiner.current != null)
            return;

        String pauseReason = pauseReason(player, gameMode);
        if (pauseReason != null) {
            stopWork();
            return;
        }

        beforeMining(player, level, gameMode);

        if (player.getAbilities().instabuild)
            mineInstantly(player, level, gameMode);
        else
            mineProgressively(player, level, gameMode);
    }

    protected void beforeMining(LocalPlayer player, ClientLevel level, MultiPlayerGameMode gameMode) {
    }

    protected void onMined(BlockPos pos, BlockState state) {
    }

    private String pauseReason(LocalPlayer player, MultiPlayerGameMode gameMode) {
        GameType gameType = gameMode.getPlayerMode();
        if (gameType == GameType.SPECTATOR || player.isSpectator())
            return "Spectator";
        if (mc.gui.screen() != null)
            return "Paused";
        if (player.isUsingItem())
            return "Using item";
        if (ModuleManager.getModuleState(Scaffold.class))
            return "Scaffold active";
        return null;
    }

    private void mineProgressively(LocalPlayer player, ClientLevel level, MultiPlayerGameMode gameMode) {
        if (current != null && (!canBreak(current) || !isInReach(current) || currentTicks > currentTickLimit))
            abortCurrent();

        if (current == null) {
            int budget = Math.max(1, instantBlocksPerTick());
            boolean minedAny = false;
            while (budget > 0) {
                BlockPos next = pickNext(player, level);
                if (next == null)
                    break;

                selectTool(player, next);
                if (ToolSaver.isAboutToBreak(player.getMainHandItem())) {
                    queue.put(next, System.currentTimeMillis());
                    stopWork();
                    return;
                }

                BlockState state = level.getBlockState(next);
                if (state.getDestroyProgress(player, level, next) < 1.0F) {
                    current = next;
                    currentTicks = 0;
                    break;
                }

                if (breakInstantly(gameMode, level, next, state)) {
                    minedAny = true;
                    activeMiner = this;
                }
                budget--;
            }

            if (current == null) {
                if (!minedAny)
                    stopWork();
                return;
            }
        }

        activeMiner = this;
        selectTool(player, current);
        if (ToolSaver.isAboutToBreak(player.getMainHandItem())) {
            BlockPos keep = current;
            abortCurrent();
            queue.put(keep, System.currentTimeMillis());
            stopWork();
            return;
        }

        BlockState state = level.getBlockState(current);
        float rate = state.getDestroyProgress(player, level, current);
        currentTickLimit = expectedTicks(rate) * 2 + 40;

        if (rotations()) {
            Client.rotationManager.faceBlock(current, 180f);
            rotating = true;
        }

        Direction direction = PlayerUtil.INSTANCE.blockHitResult(current).getDirection();
        internalMining = true;
        try {
            if (gameMode.continueDestroyBlock(current, direction))
                PlayerUtil.INSTANCE.swingHand(InteractionHand.MAIN_HAND);
        } finally {
            internalMining = false;
        }

        currentTicks++;
        if (level.getBlockState(current).isAir()) {
            BlockPos mined = current;
            current = null;
            currentTicks = 0;
            onMined(mined, state);
        }
    }

    private boolean breakInstantly(MultiPlayerGameMode gameMode, ClientLevel level, BlockPos pos, BlockState state) {
        if (rotations()) {
            Client.rotationManager.faceBlock(pos, 180f);
            rotating = true;
        }

        Direction direction = PlayerUtil.INSTANCE.blockHitResult(pos).getDirection();
        internalMining = true;
        try {
            if (gameMode.startDestroyBlock(pos, direction))
                PlayerUtil.INSTANCE.swingHand(InteractionHand.MAIN_HAND);
        } finally {
            internalMining = false;
        }

        if (!level.getBlockState(pos).isAir())
            return false;

        onMined(pos, state);
        return true;
    }

    private void mineInstantly(LocalPlayer player, ClientLevel level, MultiPlayerGameMode gameMode) {
        if (current != null)
            abortCurrent();

        int budget = Math.max(1, instantBlocksPerTick());
        boolean minedAny = false;
        for (int i = 0; i < budget; i++) {
            BlockPos next = pickNext(player, level);
            if (next == null)
                break;

            BlockState state = level.getBlockState(next);
            if (!player.getMainHandItem().canDestroyBlock(state, level, next, player)) {
                queue.put(next, System.currentTimeMillis());
                break;
            }

            if (breakInstantly(gameMode, level, next, state))
                minedAny = true;
        }

        if (minedAny)
            activeMiner = this;
        else
            stopWork();
    }

    private BlockPos pickNext(LocalPlayer player, ClientLevel level) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        var eye = player.getEyePosition();
        Iterator<Map.Entry<BlockPos, Long>> iterator = queue.entrySet().iterator();
        while (iterator.hasNext()) {
            BlockPos pos = iterator.next().getKey();
            if (!canBreak(pos)) {
                iterator.remove();
                continue;
            }
            if (!player.getAbilities().instabuild
                    && expectedTicks(level.getBlockState(pos).getDestroyProgress(player, level, pos)) > MAX_EXPECTED_TICKS) {
                iterator.remove();
                continue;
            }
            if (!isInReach(pos))
                continue;

            double distance = new AABB(pos).distanceToSqr(eye);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos;
            }
        }

        if (best != null)
            queue.remove(best);
        return best;
    }

    private void pruneQueue(LocalPlayer player) {
        if (queue.isEmpty())
            return;

        long now = System.currentTimeMillis();
        var eye = player.getEyePosition();
        queue.entrySet().removeIf(entry -> now - entry.getValue() > QUEUE_EXPIRY_MILLIS
                || new AABB(entry.getKey()).distanceToSqr(eye) > QUEUE_DISCARD_DISTANCE_SQUARED
                || mc.level.getBlockState(entry.getKey()).isAir());
    }

    private static int expectedTicks(float rate) {
        if (rate <= 0.0F)
            return Integer.MAX_VALUE / 4;
        return (int) Math.ceil(1.0F / rate);
    }

    private void abortCurrent() {
        if (current == null)
            return;

        MultiPlayerGameMode gameMode = mc.gameMode;
        if (gameMode != null && ((MultiPlayerGameModeAccessor) gameMode).krs$isDestroying()) {
            internalMining = true;
            try {
                gameMode.stopDestroyBlock();
            } finally {
                internalMining = false;
            }
        }
        current = null;
        currentTicks = 0;
    }

    private void stopWork() {
        abortCurrent();
        if (activeMiner == this)
            activeMiner = null;
        if (rotating) {
            Client.rotationManager.stopRotation();
            rotating = false;
        }
        restoreSlot();
    }

    private void selectTool(LocalPlayer player, BlockPos pos) {
        if (!ModuleManager.getModuleState(AutoTool.class))
            return;

        int bestSlot = ToolUtil.INSTANCE.findBestTool(pos);
        int selected = player.getInventory().getSelectedSlot();
        if (bestSlot == -1 || bestSlot == selected)
            return;

        if (originalSlot == -1)
            originalSlot = selected;
        PlayerUtil.INSTANCE.doSpoof(originalSlot);
        player.getInventory().setSelectedSlot(bestSlot);
    }

    private void restoreSlot() {
        if (originalSlot == -1)
            return;

        LocalPlayer player = mc.player;
        if (player != null && player.getInventory().getSelectedSlot() != originalSlot)
            player.getInventory().setSelectedSlot(originalSlot);
        originalSlot = -1;
        PlayerUtil.INSTANCE.stopSpoof();
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        highlightedQueue.clear();
        for (BlockPos pos : queue.keySet()) {
            if (highlightedQueue.size() >= MAX_HIGHLIGHTED)
                break;
            highlightedQueue.add(pos);
        }

        if (highlightedQueue.isEmpty())
            queueHighlight.release();
        else
            queueHighlight.setTargets(highlightedQueue);

        if (current != null)
            targetHighlight.setTarget(current);
        else
            targetHighlight.release();

    }
}
