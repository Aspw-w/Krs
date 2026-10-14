package com.instrumentalist.krs.hacks.features.level;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.MotionEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.player.AutoTool;
import com.instrumentalist.krs.utils.entity.PlayerUtil;
import com.instrumentalist.krs.utils.math.MSTimer;
import com.instrumentalist.krs.utils.math.ToolUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.krs.utils.value.ListValue;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import com.instrumentalist.krs.utils.input.Keys;

import java.util.ArrayDeque;
import java.util.ArrayList;

public class Nuker extends Module {

    @Setting
    private static final ListValue mode = new ListValue("Mode", new String[]{"Normal", "Floor"}, "Normal");

    @Setting
    private static final ListValue clickMode = new ListValue("Click Mode", new String[]{"Break", "Place"}, "Break");

    @Setting
    private static final IntValue range = new IntValue("Range", 4, 2, 6, "m");

    @Setting
    private static final BooleanValue singleBlock = new BooleanValue("Single Block", false);

    @Setting
    private static final BooleanValue rotations = new BooleanValue("Rotations", true, singleBlock::get);

    @Setting
    private static final IntValue nukeSpeed = new IntValue("Nuke Speed", 10, 1, 10, () -> !singleBlock.get());

    @Setting
    private static final BooleanValue noteBlockOnly = new BooleanValue("Note Block Only", false);

    private static boolean wasBreaking = false;

    private final MSTimer nukeTimer = new MSTimer();
    private BlockPos currentBlock = null;
    private final ArrayDeque<BlockPos> blockQueue = new ArrayDeque<>(256);
    private final ArrayList<BlockPos> potentialBlocks = new ArrayList<>(256);
    private long breakStartTime = 0L;
    private int originalSlot = -1;

    public Nuker() {
        super("Nuker", ModuleCategory.Level, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onDisable() {
        blockQueue.clear();
        breakStartTime = 0;
        currentBlock = null;
        if (wasBreaking) {
            PlayerUtil.INSTANCE.stopDestroyBlock();
            Client.rotationManager.stopRotation();
        }
        wasBreaking = false;
        if (mc.player != null)
            restoreOriginalSlot(mc.player);
        originalSlot = -1;
        PlayerUtil.INSTANCE.stopSpoof();
    }

    @Override
    public void onEnable() {
    }

    private void restoreOriginalSlot(LocalPlayer player) {
        if (originalSlot != -1) {
            if (originalSlot != player.getInventory().getSelectedSlot())
                player.getInventory().setSelectedSlot(originalSlot);
            originalSlot = -1;
        }
    }

    private boolean shouldHandleBlock(ClientLevel level, BlockPos targetPos, boolean breaking, boolean placing, boolean noteOnly) {
        var state = level.getBlockState(targetPos);

        if (breaking)
            return (!noteOnly || state.getBlock() instanceof NoteBlock) && !state.isAir() && !level.getFluidState(targetPos).is(Fluids.WATER);

        if (!placing) return false;
        return noteOnly ? state.getBlock() instanceof NoteBlock : state.isAir();
    }

    private void collectPotentialBlocks(ClientLevel level, BlockPos playerPos, Vec3 playerPosVec, int startY, int rangeValue, boolean breaking, boolean placing, boolean noteOnly, boolean skipCloseAirPlace) {
        potentialBlocks.clear();
        int rangeSquared = rangeValue * rangeValue;

        for (int x = -rangeValue; x <= rangeValue; x++) {
            for (int y = startY; y <= rangeValue; y++) {
                for (int z = -rangeValue; z <= rangeValue; z++) {
                    if (x * x + y * y + z * z > rangeSquared) continue;

                    BlockPos targetPos = playerPos.offset(x, y, z);
                    if (skipCloseAirPlace && placing && !noteOnly && playerPosVec != null) {
                        double dx = playerPosVec.x - (targetPos.getX() + 0.5);
                        double dy = playerPosVec.y - (targetPos.getY() + 0.5);
                        double dz = playerPosVec.z - (targetPos.getZ() + 0.5);
                        if (dx * dx + dy * dy + dz * dz < 3.0) continue;
                    }

                    if (shouldHandleBlock(level, targetPos, breaking, placing, noteOnly))
                        potentialBlocks.add(targetPos);
                }
            }
        }
    }

    private boolean isWithinRange(LocalPlayer player, BlockPos pos, int rangeValue) {
        return player.distanceToSqr(Vec3.atCenterOf(pos)) <= rangeValue * rangeValue;
    }

    private void selectBestTool(LocalPlayer player, BlockPos pos) {
        if (!ModuleManager.getModuleState(AutoTool.class)) return;

        int bestToolSlot = ToolUtil.INSTANCE.findBestTool(pos);
        if (bestToolSlot != -1 && bestToolSlot != player.getInventory().getSelectedSlot()) {
            if (originalSlot == -1)
                originalSlot = player.getInventory().getSelectedSlot();
            PlayerUtil.INSTANCE.doSpoof(originalSlot);
            player.getInventory().setSelectedSlot(bestToolSlot);
        }
    }

    private void resetBreaking(LocalPlayer player, boolean stopDestroying) {
        if (stopDestroying)
            PlayerUtil.INSTANCE.stopDestroyBlock();

        restoreOriginalSlot(player);
        PlayerUtil.INSTANCE.stopSpoof();
        currentBlock = null;
        breakStartTime = 0L;
        blockQueue.clear();
        wasBreaking = false;
    }

    private void handleMultiBlock(LocalPlayer player, ClientLevel level, BlockPos playerPos, int startY, int rangeValue, boolean breaking, boolean placing, boolean noteOnly) {
        if (currentBlock != null && breaking)
            PlayerUtil.INSTANCE.stopDestroyBlock();

        currentBlock = null;
        breakStartTime = 0L;

        collectPotentialBlocks(level, playerPos, null, startY, rangeValue, breaking, placing, noteOnly, false);

        if (potentialBlocks.isEmpty()) {
            resetBreaking(player, breaking);
            Client.rotationManager.stopRotation();
            return;
        }

        potentialBlocks.sort((a, b) -> Double.compare(a.distSqr(playerPos), b.distSqr(playerPos)));
        blockQueue.clear();
        blockQueue.addAll(potentialBlocks);

        if (!nukeTimer.hasTimePassed(1000L / (nukeSpeed.get() + 3)))
            return;

        boolean handledBlock = false;
        var gameMode = mc.gameMode;
        if (gameMode == null) return;

        while (!blockQueue.isEmpty()) {
            BlockPos targetPos = blockQueue.poll();
            if (!shouldHandleBlock(level, targetPos, breaking, placing, noteOnly))
                continue;

            BlockHitResult hitResult = PlayerUtil.INSTANCE.blockHitResult(targetPos);

            if (breaking) {
                selectBestTool(player, targetPos);
                PlayerUtil.INSTANCE.nukeBlockWithPacket(hitResult);
                handledBlock = true;
            } else if (placing) {
                if (gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hitResult).consumesAction()) {
                    PlayerUtil.INSTANCE.swingInteractHand(InteractionHand.MAIN_HAND);
                    handledBlock = true;
                }
            }
        }

        if (handledBlock) {
            wasBreaking = true;
            PlayerUtil.INSTANCE.doSpoof(originalSlot);
        } else {
            resetBreaking(player, breaking);
            Client.rotationManager.stopRotation();
        }

        nukeTimer.reset();
    }

    @Override
    public void onMotion(MotionEvent event) {
        var player = mc.player;
        var level = mc.level;
        var gameMode = mc.gameMode;
        if (player == null || level == null || gameMode == null) return;

        if (wasBreaking)
            PlayerUtil.INSTANCE.doSpoof(originalSlot);

        int rangeValue = range.get();
        BlockPos playerPos = player.blockPosition();
        int startY = mode.get().equalsIgnoreCase("normal") ? -rangeValue : 0;
        boolean breaking = clickMode.get().equalsIgnoreCase("break");
        boolean placing = clickMode.get().equalsIgnoreCase("place");
        boolean noteOnly = noteBlockOnly.get();
        boolean single = singleBlock.get();

        if (!single) {
            handleMultiBlock(player, level, playerPos, startY, rangeValue, breaking, placing, noteOnly);
            return;
        }

        BlockPos current = currentBlock;
        if (current != null) {
            if (!isWithinRange(player, current, rangeValue) || !shouldHandleBlock(level, current, breaking, placing, noteOnly)) {
                resetBreaking(player, breaking);
                current = null;
            } else if (breaking && System.currentTimeMillis() - breakStartTime > 5000L) {
                resetBreaking(player, true);
                current = null;
            } else if (placing && !level.getBlockState(current).isAir()) {
                resetBreaking(player, false);
                current = null;
            }
        }

        if (current == null) {
            collectPotentialBlocks(level, playerPos, single ? player.position() : null, startY, rangeValue, breaking, placing, noteOnly, single);

            if (potentialBlocks.isEmpty()) {
                resetBreaking(player, breaking);
                Client.rotationManager.stopRotation();
                return;
            }

            potentialBlocks.sort((a, b) -> Double.compare(a.distSqr(playerPos), b.distSqr(playerPos)));
            currentBlock = potentialBlocks.get(0);
            current = currentBlock;
            breakStartTime = System.currentTimeMillis();
        }

        if (current == null || !shouldHandleBlock(level, current, breaking, placing, noteOnly))
            return;

        wasBreaking = true;
        PlayerUtil.INSTANCE.doSpoof(originalSlot);

        if (single && rotations.get())
            Client.rotationManager.faceBlock(current, 180f);

        BlockHitResult hitResult = PlayerUtil.INSTANCE.blockHitResult(current);

        if (breaking) {
            selectBestTool(player, current);

            if (single || nukeTimer.hasTimePassed(1000L / (nukeSpeed.get() + 3))) {
                PlayerUtil.INSTANCE.destroyBlock(hitResult);
                nukeTimer.reset();
            }

            if (level.getBlockState(current).isAir()) {
                currentBlock = null;
                breakStartTime = 0L;
            }
        } else if (placing) {
            if (gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hitResult).consumesAction()) {
                PlayerUtil.INSTANCE.swingInteractHand(InteractionHand.MAIN_HAND);
                currentBlock = null;
                breakStartTime = 0L;
            }
        }
    }

    public static boolean getWasBreaking() {
        return wasBreaking;
    }

}
