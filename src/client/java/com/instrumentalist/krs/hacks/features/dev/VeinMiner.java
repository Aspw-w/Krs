package com.instrumentalist.krs.hacks.features.dev;

import com.instrumentalist.krs.utils.render.RenderColors;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.krs.utils.value.ListValue;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public class VeinMiner extends QueuedMiner {
    private static final List<TagKey<Block>> ORE_FAMILIES = List.of(
            BlockItemTags.COAL_ORES.block(),
            BlockTags.IRON_ORES,
            BlockTags.COPPER_ORES,
            BlockTags.GOLD_ORES,
            BlockItemTags.REDSTONE_ORES.block(),
            BlockItemTags.LAPIS_ORES.block(),
            BlockItemTags.DIAMOND_ORES.block(),
            BlockItemTags.EMERALD_ORES.block()
    );
    private static final int SEARCH_RADIUS = 12;

    @Setting
    private final ListValue activation = new ListValue("Activation", new String[]{"Ores & Logs", "Sneaking", "Always"}, "Ores & Logs");

    @Setting
    private final IntValue maxBlocks = new IntValue("Max Blocks", 64, 1, 256);

    @Setting
    private final BooleanValue diagonal = new BooleanValue("Diagonal", true);

    @Setting
    private final BooleanValue oreFamilies = new BooleanValue("Ore Families", true);

    @Setting
    private final FloatValue range = new FloatValue("Range", 4.5f, 1f, 6f, "m");

    @Setting
    private final BooleanValue rotations = new BooleanValue("Rotations", false);

    @Setting
    private final IntValue instantSpeed = new IntValue("Instant Blocks/Tick", 4, 1, 16);

    private final ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
    private final HashSet<BlockPos> visited = new HashSet<>();

    public VeinMiner() {
        super("Vein Miner", RenderColors.VEIN_MINER);
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
    protected void onBlockBrokenByPlayer(BlockPos origin, BlockState state) {
        var player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || !shouldActivate(state, player.isShiftKeyDown()))
            return;

        frontier.clear();
        visited.clear();
        frontier.add(origin);
        visited.add(origin);
        int limit = maxBlocks.get();
        int added = 0;
        boolean diagonals = diagonal.get();
        BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();

        while (!frontier.isEmpty() && added < limit) {
            BlockPos pos = frontier.poll();
            for (int dx = -1; dx <= 1 && added < limit; dx++) {
                for (int dy = -1; dy <= 1 && added < limit; dy++) {
                    for (int dz = -1; dz <= 1 && added < limit; dz++) {
                        int axes = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                        if (axes == 0 || !diagonals && axes > 1)
                            continue;

                        neighbour.setWithOffset(pos, dx, dy, dz);
                        if (Math.abs(neighbour.getX() - origin.getX()) > SEARCH_RADIUS
                                || Math.abs(neighbour.getY() - origin.getY()) > SEARCH_RADIUS
                                || Math.abs(neighbour.getZ() - origin.getZ()) > SEARCH_RADIUS)
                            continue;
                        if (visited.contains(neighbour))
                            continue;

                        BlockPos candidate = neighbour.immutable();
                        visited.add(candidate);
                        if (!matches(state, level.getBlockState(candidate)) || !canBreak(candidate))
                            continue;

                        frontier.add(candidate);
                        if (!isQueued(candidate)) {
                            enqueue(candidate);
                            added++;
                        }
                    }
                }
            }
        }

        frontier.clear();
        visited.clear();
    }

    private boolean shouldActivate(BlockState state, boolean sneaking) {
        return switch (activation.get().toLowerCase(Locale.ROOT)) {
            case "always" -> true;
            case "sneaking" -> sneaking;
            default -> isOre(state) || state.is(BlockTags.LOGS);
        };
    }

    private boolean matches(BlockState origin, BlockState candidate) {
        if (candidate.getBlock() == origin.getBlock())
            return true;
        if (!oreFamilies.get())
            return false;

        for (TagKey<Block> family : ORE_FAMILIES) {
            if (origin.is(family) && candidate.is(family))
                return true;
        }
        return false;
    }

    private static boolean isOre(BlockState state) {
        for (TagKey<Block> family : ORE_FAMILIES) {
            if (state.is(family))
                return true;
        }
        return state.is(BlockTags.ORES)
                || state.is(Blocks.NETHER_QUARTZ_ORE)
                || state.is(Blocks.NETHER_GOLD_ORE)
                || state.is(Blocks.ANCIENT_DEBRIS)
                || state.is(Blocks.GLOWSTONE)
                || state.is(Blocks.AMETHYST_CLUSTER);
    }
}
