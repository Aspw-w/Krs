package com.instrumentalist.krs.hacks.features.dev;

import com.instrumentalist.krs.utils.entity.PlayerUtil;
import com.instrumentalist.krs.utils.render.RenderColors;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.krs.utils.world.QueuedMiner;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class Harvester extends QueuedMiner {
    private static final int SCAN_INTERVAL_TICKS = 4;
    private static final int REPLANTS_PER_TICK = 2;
    private static final long REPLANT_TIMEOUT_MILLIS = 3_000L;

    @Setting
    private final BooleanValue crops = new BooleanValue("Crops", true);

    @Setting
    private final BooleanValue fruits = new BooleanValue("Pumpkins & Melons", true);

    @Setting
    private final BooleanValue tallPlants = new BooleanValue("Tall Plants", true);

    @Setting
    private final BooleanValue replant = new BooleanValue("Replant", true);

    @Setting
    private final FloatValue range = new FloatValue("Range", 4.5f, 1f, 6f, "m");

    @Setting
    private final BooleanValue rotations = new BooleanValue("Rotations", false);

    @Setting
    private final IntValue instantSpeed = new IntValue("Instant Blocks/Tick", 4, 1, 16);

    private final LinkedHashMap<BlockPos, Replant> pendingReplants = new LinkedHashMap<>();
    private int scanCooldown;

    public Harvester() {
        super("Harvester", RenderColors.HARVESTER);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        pendingReplants.clear();
        scanCooldown = 0;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        pendingReplants.clear();
    }

    @Override
    protected float range() {
        return range.get();
    }

    @Override
    protected boolean rotations() {
        return rotations.get();
    }

    @Override
    protected int instantBlocksPerTick() {
        return instantSpeed.get();
    }

    @Override
    protected void onBlockBrokenByPlayer(BlockPos pos, BlockState state) {
    }

    @Override
    protected void beforeMining(LocalPlayer player, ClientLevel level, MultiPlayerGameMode gameMode) {
        processReplants(player, level, gameMode);

        if (--scanCooldown > 0)
            return;
        scanCooldown = SCAN_INTERVAL_TICKS;

        int radius = Mth.ceil(effectiveRange(player));
        BlockPos center = BlockPos.containing(player.getEyePosition());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.setWithOffset(center, dx, dy, dz);
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir() || !isHarvestable(level, cursor, state) || isQueued(cursor) || !isInReach(cursor))
                        continue;

                    enqueue(cursor.immutable());
                }
            }
        }
    }

    @Override
    protected void onMined(BlockPos pos, BlockState state) {
        if (!replant.get() || !(state.getBlock() instanceof CropBlock || state.getBlock() instanceof NetherWartBlock))
            return;

        ClientLevel level = mc.level;
        if (level == null)
            return;

        Item seed = state.getCloneItemStack(level, pos, false).getItem();
        pendingReplants.put(pos, new Replant(seed, state.getBlock(), System.currentTimeMillis()));
    }

    private void processReplants(LocalPlayer player, ClientLevel level, MultiPlayerGameMode gameMode) {
        if (pendingReplants.isEmpty())
            return;

        long now = System.currentTimeMillis();
        int budget = REPLANTS_PER_TICK;
        Iterator<Map.Entry<BlockPos, Replant>> iterator = pendingReplants.entrySet().iterator();
        while (iterator.hasNext() && budget > 0) {
            Map.Entry<BlockPos, Replant> entry = iterator.next();
            BlockPos pos = entry.getKey();
            Replant replantEntry = entry.getValue();
            if (now - replantEntry.createdMillis() > REPLANT_TIMEOUT_MILLIS || !level.getBlockState(pos).isAir()) {
                iterator.remove();
                continue;
            }

            BlockPos soil = pos.below();
            if (!isValidSoil(replantEntry.crop(), level.getBlockState(soil))) {
                iterator.remove();
                continue;
            }
            if (!isInReach(soil))
                continue;

            int slot = findHotbarSlot(player.getInventory(), replantEntry.seed());
            if (slot == -1) {
                iterator.remove();
                continue;
            }

            Inventory inventory = player.getInventory();
            int selected = inventory.getSelectedSlot();
            inventory.setSelectedSlot(slot);
            try {
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(soil).add(0.0D, 0.5D, 0.0D), Direction.UP, soil, false);
                if (gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction())
                    PlayerUtil.INSTANCE.swingHand(InteractionHand.MAIN_HAND);
            } finally {
                inventory.setSelectedSlot(selected);
            }

            iterator.remove();
            budget--;
        }
    }

    private boolean isHarvestable(ClientLevel level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (crops.get()) {
            if (block instanceof CropBlock crop)
                return crop.isMaxAge(state);
            if (block instanceof NetherWartBlock)
                return state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
            if (block instanceof CocoaBlock)
                return state.getValue(CocoaBlock.AGE) >= CocoaBlock.MAX_AGE;
        }

        if (fruits.get() && (block == Blocks.PUMPKIN || block == Blocks.MELON))
            return hasAttachedStem(level, pos);

        if (tallPlants.get() && (block == Blocks.SUGAR_CANE || block == Blocks.BAMBOO || block == Blocks.CACTUS))
            return level.getBlockState(pos.below()).is(block) && !level.getBlockState(pos.below(2)).is(block);

        return false;
    }

    private static boolean hasAttachedStem(ClientLevel level, BlockPos pos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockState neighbour = level.getBlockState(pos.relative(direction));
            if (neighbour.is(Blocks.ATTACHED_PUMPKIN_STEM) || neighbour.is(Blocks.ATTACHED_MELON_STEM))
                return true;
        }
        return false;
    }

    private static boolean isValidSoil(Block crop, BlockState soil) {
        if (crop instanceof NetherWartBlock)
            return soil.is(Blocks.SOUL_SAND);
        return soil.is(Blocks.FARMLAND);
    }

    private static int findHotbarSlot(Inventory inventory, Item item) {
        if (inventory.getItem(inventory.getSelectedSlot()).is(item))
            return inventory.getSelectedSlot();
        for (int slot = 0; slot < 9; slot++) {
            if (inventory.getItem(slot).is(item))
                return slot;
        }
        return -1;
    }

    private record Replant(Item seed, Block crop, long createdMillis) {
    }
}
