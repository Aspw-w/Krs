package com.instrumentalist.krs.hacks.features.level;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.KeyboardEvent;
import com.instrumentalist.krs.events.features.MotionEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.combat.KillAura;
import com.instrumentalist.krs.hacks.features.player.AutoTool;
import com.instrumentalist.krs.hacks.features.player.Scaffold;
import com.instrumentalist.krs.utils.entity.PlayerUtil;
import com.instrumentalist.krs.utils.math.BehaviorUtils;
import com.instrumentalist.krs.utils.math.ToolUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.ListValue;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DragonEggBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import com.instrumentalist.krs.utils.input.Keys;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class Breaker extends Module {

    @Setting
    private static final ListValue mode = new ListValue("Mode", new String[]{"Normal", "Hypixel", "Cubecraft Eggwars"}, "Normal");

    @Setting
    private static final ListValue clickMode = new ListValue("Click Mode", new String[]{"Break", "Place"}, "Break", () -> mode.get().equalsIgnoreCase("normal"));

    @Setting
    private static final ListValue block = new ListValue("Block", new String[]{"Bed", "Egg"}, "Bed", () -> mode.get().equalsIgnoreCase("normal"));

    @Setting
    private static final FloatValue range = new FloatValue("Range", 4f, 1f, 6f, "m");

    @Setting
    private static final BooleanValue autoWhitelist = new BooleanValue("Auto Whitelist", false);

    private static boolean wasBreaking = false;

    private int hypTick = 0;
    private BlockPos cachedBedPos = null;
    private BlockPos cachedSecondPos = null;
    private int originalSlot = -1;
    private boolean progress = false;
    private boolean secondProgress = false;
    private boolean breakerNoKillAura = false;
    private boolean breakerSpoofing = false;
    private static final Set<BlockPos> autoWhitelistedTargets = new HashSet<>();
    private static boolean autoWhitelistSearching = false;
    private static boolean autoWhitelistHasBed = false;
    private static long autoWhitelistLastScanTime = 0L;

    public Breaker() {
        super("Breaker", ModuleCategory.Level, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public String description() {
        return "Clear whitelist with right control";
    }

    @Override
    public void onDisable() {
        resetBreakingState(mc.player);
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onWorld(WorldEvent event) {
        resetBreakingState(mc.player);

        if (resetAutoWhitelist(autoWhitelist.get()))
            Client.notificationManager.addNotification("Auto Whitelist", "Reset bed whitelist");
    }

    @Override
    public void onKey(KeyboardEvent event) {
        if (event.action == Keys.PRESS && event.key == Keys.KEY_RIGHT_CONTROL && resetAutoWhitelist(true))
            Client.notificationManager.addNotification("Auto Whitelist", "Reset bed whitelist");
    }

    public static boolean resetAutoWhitelist(boolean searchAgain) {
        boolean hadBed = autoWhitelistHasBed;
        autoWhitelistedTargets.clear();
        autoWhitelistSearching = searchAgain;
        autoWhitelistHasBed = false;
        autoWhitelistLastScanTime = 0L;
        return hadBed;
    }

    private void restoreOriginalSlot(LocalPlayer player) {
        if (originalSlot != -1) {
            if (player != null && originalSlot != player.getInventory().getSelectedSlot())
                player.getInventory().setSelectedSlot(originalSlot);
            originalSlot = -1;
        }
    }

    private void selectBestTool(LocalPlayer player, BlockPos pos) {
        if (!ModuleManager.getModuleState(AutoTool.class)) return;

        resetAutoToolState();

        int bestToolSlot = ToolUtil.INSTANCE.findBestTool(pos);
        if (bestToolSlot != -1 && bestToolSlot != player.getInventory().getSelectedSlot()) {
            if (originalSlot == -1)
                originalSlot = player.getInventory().getSelectedSlot();
            doBreakerSpoof();
            player.getInventory().setSelectedSlot(bestToolSlot);
        } else if (bestToolSlot != -1 && originalSlot != -1) {
            doBreakerSpoof();
        }
    }

    private BlockHitResult hitResult(BlockPos pos) {
        return PlayerUtil.INSTANCE.blockHitResult(pos);
    }

    private boolean isWithinRange(LocalPlayer player, BlockPos pos, float rangeLimit) {
        return rangeLimit >= player.distanceToSqr(Vec3.atCenterOf(pos));
    }

    private void blockKillAura() {
        BehaviorUtils.noKillAura = true;
        breakerNoKillAura = true;
    }

    private void releaseKillAura() {
        if (!breakerNoKillAura)
            return;

        BehaviorUtils.noKillAura = false;
        breakerNoKillAura = false;
    }

    private void doBreakerSpoof() {
        if (originalSlot < 0 || originalSlot > 8)
            return;

        PlayerUtil.INSTANCE.doSpoof(originalSlot);
        breakerSpoofing = true;
    }

    private void releaseBreakerSpoof() {
        if (!breakerSpoofing)
            return;

        PlayerUtil.INSTANCE.stopSpoof();
        breakerSpoofing = false;
    }

    private void resetBreakingState(LocalPlayer player) {
        boolean hasBreakerState = wasBreaking || progress || secondProgress || hypTick != 0 || originalSlot != -1 || breakerSpoofing;

        if (hasBreakerState)
            PlayerUtil.INSTANCE.stopDestroyBlock();

        if (hasBreakerState)
            resetAutoToolState();
        restoreOriginalSlot(player);
        releaseBreakerSpoof();

        if (hasBreakerState || breakerNoKillAura)
            Client.rotationManager.stopRotation();

        releaseKillAura();
        cachedSecondPos = null;
        cachedBedPos = null;
        hypTick = 0;
        progress = false;
        secondProgress = false;
        wasBreaking = false;
    }

    private void resetAutoToolState() {
        AutoTool autoTool = ModuleManager.getModule(AutoTool.class);
        if (autoTool != null)
            autoTool.resetState();
    }

    private boolean shouldTargetBed() {
        String modeName = mode.get();
        return modeName.equalsIgnoreCase("hypixel")
                || modeName.equalsIgnoreCase("normal") && block.get().equalsIgnoreCase("bed");
    }

    private boolean shouldTargetEgg() {
        String modeName = mode.get();
        return modeName.equalsIgnoreCase("cubecraft eggwars")
                || modeName.equalsIgnoreCase("normal") && block.get().equalsIgnoreCase("egg");
    }

    private boolean isTargetBlock(BlockState blockState) {
        return shouldTargetBed() && blockState.getBlock() instanceof BedBlock
                || shouldTargetEgg() && blockState.getBlock() instanceof DragonEggBlock;
    }

    private boolean isAutoWhitelistedTarget(ClientLevel level, BlockPos pos, BlockState blockState) {
        if (!autoWhitelist.get() || autoWhitelistedTargets.isEmpty())
            return false;

        if (autoWhitelistedTargets.contains(pos))
            return true;

        if (blockState.getBlock() instanceof BedBlock) {
            BlockPos otherPartPos = findOtherBedPart(level, pos, blockState);
            return otherPartPos != null && autoWhitelistedTargets.contains(otherPartPos);
        }

        return false;
    }

    private boolean isTargetAvailable(ClientLevel level, BlockPos pos) {
        BlockState blockState = level.getBlockState(pos);
        return isTargetBlock(blockState) && !isAutoWhitelistedTarget(level, pos, blockState);
    }

    private void updateAutoWhitelist(ClientLevel level, LocalPlayer player) {
        if (!autoWhitelist.get() || !autoWhitelistSearching || !autoWhitelistedTargets.isEmpty())
            return;

        long currentTime = System.currentTimeMillis();
        if (autoWhitelistLastScanTime != 0L && currentTime - autoWhitelistLastScanTime < 2_000L)
            return;

        autoWhitelistLastScanTime = currentTime;

        BlockPos targetPos = findAutoWhitelistTarget(level, player.blockPosition());
        if (targetPos == null)
            return;

        boolean addedBed = addAutoWhitelistTarget(level, targetPos);
        autoWhitelistSearching = false;

        if (addedBed)
            Client.notificationManager.addNotification("Auto Whitelist", "Added bed to whitelist: " + formatBlockPos(targetPos));

        if (cachedBedPos != null && !isTargetAvailable(level, cachedBedPos)) {
            cachedBedPos = null;
            cachedSecondPos = null;
            progress = false;
            secondProgress = false;
        }
    }

    private boolean addAutoWhitelistTarget(ClientLevel level, BlockPos targetPos) {
        autoWhitelistedTargets.add(new BlockPos(targetPos.getX(), targetPos.getY(), targetPos.getZ()));

        BlockState blockState = level.getBlockState(targetPos);
        if (blockState.getBlock() instanceof BedBlock) {
            BlockPos otherPartPos = findOtherBedPart(level, targetPos, blockState);
            if (otherPartPos != null)
                autoWhitelistedTargets.add(new BlockPos(otherPartPos.getX(), otherPartPos.getY(), otherPartPos.getZ()));
            autoWhitelistHasBed = true;
            return true;
        }

        return false;
    }

    private String formatBlockPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private BlockPos findAutoWhitelistTarget(ClientLevel level, BlockPos playerPos) {
        BlockPos closest = null;
        double closestDistance = Double.MAX_VALUE;
        int horizontalRange = 10;
        int verticalRange = 5;

        for (int x = -horizontalRange; x <= horizontalRange; x++) {
            for (int y = -verticalRange; y <= verticalRange; y++) {
                for (int z = -horizontalRange; z <= horizontalRange; z++) {
                    BlockPos pos = playerPos.offset(x, y, z);
                    var blockState = level.getBlockState(pos);
                    if (!isTargetBlock(blockState))
                        continue;

                    double distance = pos.distSqr(playerPos);
                    if (distance < closestDistance) {
                        closest = pos;
                        closestDistance = distance;
                    }
                }
            }
        }

        return closest;
    }

    @Override
    public void onMotion(MotionEvent event) {
        var player = mc.player;
        var level = mc.level;
        var gameMode = mc.gameMode;
        if (player == null || level == null || gameMode == null || gameMode.getPlayerMode() == GameType.ADVENTURE || gameMode.getPlayerMode() == GameType.SPECTATOR) {
            resetBreakingState(player);
            return;
        }
        if (ModuleManager.getModuleState(Scaffold.class)) {
            resetBreakingState(player);
            return;
        }

        updateAutoWhitelist(level, player);

        float rangeValue = range.get();
        float rangeLimit = rangeValue * rangeValue;
        float targetLockRange = rangeValue + 2f;
        float targetLockRangeLimit = targetLockRange * targetLockRange;

        BlockPos currentTargetPos = cachedBedPos;
        if (currentTargetPos != null && (!isTargetAvailable(level, currentTargetPos) || !isWithinRange(player, currentTargetPos, targetLockRangeLimit))) {
            cachedBedPos = null;
            cachedSecondPos = null;
            progress = false;
            secondProgress = false;
        }

        if (cachedBedPos == null)
            cachedBedPos = findNearbyTargets(level, player, (int) Math.ceil(rangeValue), rangeLimit);

        BlockPos bedPos = cachedBedPos;
        if (bedPos != null && isTargetAvailable(level, bedPos) && isWithinRange(player, bedPos, rangeLimit)) {
            wasBreaking = true;
            switch (mode.get().toLowerCase(Locale.ROOT)) {
                case "normal" -> {
                    blockKillAura();
                    selectBestTool(player, bedPos);
                    Client.rotationManager.faceBlock(bedPos, 180f);
                    BlockHitResult hitResult = hitResult(bedPos);
                    switch (clickMode.get().toLowerCase(Locale.ROOT)) {
                        case "break" -> {
                            PlayerUtil.INSTANCE.destroyBlock(hitResult);
                            progress = true;
                        }
                        case "place" -> {
                            if (gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hitResult).consumesAction())
                                PlayerUtil.INSTANCE.swingInteractHand(InteractionHand.MAIN_HAND);
                        }
                    }
                }
                case "hypixel" -> {
                    boolean bedTouchingAir = isBedTouchingAir(level, bedPos);
                    if (cachedSecondPos == null && !bedTouchingAir) {
                        cachedSecondPos = new BlockPos(bedPos.getX(), bedPos.getY() + 1, bedPos.getZ());
                        hypTick = 0;
                    }
                    BlockPos secondPos = cachedSecondPos;
                    if (secondProgress && progress) {
                        PlayerUtil.INSTANCE.stopDestroyBlock();
                        cachedSecondPos = null;
                        secondProgress = false;
                        progress = false;
                        hypTick = 0;
                    } else if (!bedTouchingAir && secondPos != null && !level.getBlockState(secondPos).isAir() && isWithinRange(player, secondPos, rangeLimit)) {
                        selectBestTool(player, secondPos);
                        BlockHitResult hitResult = hitResult(secondPos);
                        hypTick++;
                        if (secondProgress) {
                            PlayerUtil.INSTANCE.destroyBlock(hitResult);
                            if (hypTick >= 6) {
                                if (!ModuleManager.getModuleState(KillAura.class) || KillAura.closestEntity == null)
                                    Client.rotationManager.faceBlock(secondPos, 180f);
                            }
                        } else {
                            blockKillAura();
                            Client.rotationManager.faceBlock(secondPos, 180f);
                            PlayerUtil.INSTANCE.destroyBlock(hitResult);
                            secondProgress = true;
                        }
                    } else {
                        if (secondProgress && secondPos != null) {
                            PlayerUtil.INSTANCE.stopDestroyBlock();
                            resetAutoToolState();
                            restoreOriginalSlot(player);
                            releaseBreakerSpoof();
                            cachedSecondPos = null;
                            secondProgress = false;
                            hypTick = 0;
                        }
                        selectBestTool(player, bedPos);
                        BlockHitResult hitResult = hitResult(bedPos);
                        hypTick++;
                        if (progress) {
                            PlayerUtil.INSTANCE.destroyBlock(hitResult);
                            if (hypTick >= 6) {
                                if (!ModuleManager.getModuleState(KillAura.class) || KillAura.closestEntity == null)
                                    Client.rotationManager.faceBlock(bedPos, 180f);
                            }
                        } else {
                            blockKillAura();
                            Client.rotationManager.faceBlock(bedPos, 180f);
                            PlayerUtil.INSTANCE.destroyBlock(hitResult);
                            progress = true;
                        }
                    }
                }
                case "cubecraft eggwars" -> {
                    blockKillAura();
                    if (cachedSecondPos == null)
                        cachedSecondPos = new BlockPos(bedPos.getX(), bedPos.getY() + 1, bedPos.getZ());
                    BlockPos secondPos = cachedSecondPos;
                    if (secondProgress && progress) {
                        PlayerUtil.INSTANCE.stopDestroyBlock();
                        cachedSecondPos = null;
                        secondProgress = false;
                        progress = false;
                    } else if (secondPos != null && !level.getBlockState(secondPos).isAir() && isWithinRange(player, secondPos, rangeLimit)) {
                        selectBestTool(player, secondPos);
                        Client.rotationManager.faceBlock(secondPos, 180f);
                        BlockHitResult hitResult = hitResult(secondPos);
                        if (progress) {
                            PlayerUtil.INSTANCE.destroyBlock(hitResult);
                        } else {
                            PlayerUtil.INSTANCE.destroyBlock(hitResult);
                            progress = true;
                        }
                    } else {
                        if (secondProgress && secondPos != null) {
                            PlayerUtil.INSTANCE.stopDestroyBlock();
                            resetAutoToolState();
                            restoreOriginalSlot(player);
                            releaseBreakerSpoof();
                            cachedSecondPos = null;
                            secondProgress = false;
                        }
                        Client.rotationManager.faceBlock(bedPos, 180f);
                        BlockHitResult hitResult = hitResult(bedPos);
                        if (gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hitResult).consumesAction())
                            PlayerUtil.INSTANCE.swingInteractHand(InteractionHand.MAIN_HAND);
                    }
                }
            }
        } else if (wasBreaking) {
            resetAutoToolState();
            restoreOriginalSlot(player);
            releaseBreakerSpoof();
            PlayerUtil.INSTANCE.stopDestroyBlock();
            if (!mode.get().equalsIgnoreCase("hypixel")) {
                releaseKillAura();
                Client.rotationManager.stopRotation();
            }
            boolean keepHypixelKillAura = false;
            BlockPos secondPos = cachedSecondPos;
            if (secondProgress && secondPos != null) {
                if (mode.get().equalsIgnoreCase("hypixel")) {
                    blockKillAura();
                    Client.rotationManager.faceBlock(secondPos, 180f);
                    hypTick = 810;
                    keepHypixelKillAura = true;
                }
            }
            BlockPos currentBedPos = cachedBedPos;
            if (progress && currentBedPos != null) {
                if (mode.get().equalsIgnoreCase("hypixel")) {
                    blockKillAura();
                    Client.rotationManager.faceBlock(currentBedPos, 180f);
                    hypTick = 810;
                    keepHypixelKillAura = true;
                }
            }
            if (mode.get().equalsIgnoreCase("hypixel") && !keepHypixelKillAura) {
                releaseKillAura();
                Client.rotationManager.stopRotation();
            }
            cachedSecondPos = null;
            secondProgress = false;
            progress = false;
            wasBreaking = false;
            if (bedPos == null || !isTargetAvailable(level, bedPos) || !isWithinRange(player, bedPos, targetLockRangeLimit))
                cachedBedPos = null;
            if (hypTick != 810)
                hypTick = 0;
        } else {
            cachedSecondPos = null;
            secondProgress = false;
            progress = false;
            if (bedPos == null || !isTargetAvailable(level, bedPos) || !isWithinRange(player, bedPos, targetLockRangeLimit))
                cachedBedPos = null;
            if (hypTick == 0 && breakerNoKillAura) {
                releaseKillAura();
                Client.rotationManager.stopRotation();
            }
            if (hypTick != 0) {
                if (mode.get().equalsIgnoreCase("hypixel")) {
                    if (hypTick >= 810)
                        hypTick++;
                    if (hypTick >= 812) {
                        releaseKillAura();
                        Client.rotationManager.stopRotation();
                        hypTick = 0;
                    }
                } else hypTick = 0;
            }
        }
    }

    private boolean isBedTouchingAir(ClientLevel level, BlockPos bedPos) {
        BlockState bedState = level.getBlockState(bedPos);
        if (!(bedState.getBlock() instanceof BedBlock))
            return false;

        if (hasAdjacentAir(level, bedPos))
            return true;

        BlockPos otherPartPos = findOtherBedPart(level, bedPos, bedState);
        return otherPartPos != null && hasAdjacentAir(level, otherPartPos);
    }

    private BlockPos findOtherBedPart(ClientLevel level, BlockPos bedPos, BlockState bedState) {
        BedPart otherPart = bedState.getValue(BedBlock.PART) == BedPart.HEAD ? BedPart.FOOT : BedPart.HEAD;

        BlockPos north = otherBedPartAt(level, bedPos, Direction.NORTH, otherPart);
        if (north != null) return north;
        BlockPos south = otherBedPartAt(level, bedPos, Direction.SOUTH, otherPart);
        if (south != null) return south;
        BlockPos west = otherBedPartAt(level, bedPos, Direction.WEST, otherPart);
        if (west != null) return west;
        return otherBedPartAt(level, bedPos, Direction.EAST, otherPart);
    }

    private BlockPos otherBedPartAt(ClientLevel level, BlockPos bedPos, Direction direction, BedPart otherPart) {
        BlockPos pos = bedPos.relative(direction);
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == otherPart ? pos : null;
    }

    private boolean hasAdjacentAir(ClientLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (level.getBlockState(pos.relative(direction)).isAir())
                return true;
        }

        return false;
    }

    private BlockPos findNearbyTargets(ClientLevel level, LocalPlayer player, int radius, float rangeLimit) {
        BlockPos playerPos = player.blockPosition();
        BlockPos closest = null;
        double closestDistance = Double.MAX_VALUE;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = playerPos.offset(x, y, z);
                    if (!isTargetAvailable(level, pos) || !isWithinRange(player, pos, rangeLimit))
                        continue;

                    double distance = player.distanceToSqr(Vec3.atCenterOf(pos));
                    if (distance < closestDistance) {
                        closest = pos;
                        closestDistance = distance;
                    }
                }
            }
        }

        return closest;
    }

    public static boolean getWasBreaking() {
        return wasBreaking;
    }

}
