package com.instrumentalist.krs.utils.navigation;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.EndGatewayBlock;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.MagmaBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.WitherRoseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Set;

public final class NavWorld {
    public static final float FREE = 0.0F;
    public static final float WATER = -1.0F;
    public static final float BLOCKED = Float.POSITIVE_INFINITY;

    private static final float MAX_BREAK_TICKS = 600.0F;
    private static final float BREAK_PENALTY = 4.0F;
    private static final Direction[] LIQUID_CHECK = {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    private static final Set<Block> THROWAWAY = Set.of(
            Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.COBBLESTONE, Blocks.COBBLED_DEEPSLATE, Blocks.NETHERRACK,
            Blocks.STONE, Blocks.DEEPSLATE, Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE, Blocks.TUFF,
            Blocks.END_STONE, Blocks.BLACKSTONE, Blocks.SANDSTONE
    );

    private final ClientLevel level;
    private final ItemStack[] hotbar = new ItemStack[9];
    private final boolean cached;
    private final Long2FloatOpenHashMap spaceCache;
    private final Long2ByteOpenHashMap floorCache;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();

    public final boolean allowBreak;
    public final boolean allowPlace;
    public final boolean creative;
    public final int minY;
    public final int maxY;

    private NavWorld(LocalPlayer player, ClientLevel level, MultiPlayerGameMode gameMode, boolean cached) {
        this.level = level;
        this.cached = cached;
        this.spaceCache = cached ? new Long2FloatOpenHashMap(4096) : null;
        this.floorCache = cached ? new Long2ByteOpenHashMap(4096) : null;
        if (cached) {
            spaceCache.defaultReturnValue(Float.NaN);
            floorCache.defaultReturnValue((byte) -1);
        }

        Inventory inventory = player.getInventory();
        for (int i = 0; i < 9; i++)
            hotbar[i] = inventory.getItem(i);

        GameType mode = gameMode.getPlayerMode();
        this.creative = mode == GameType.CREATIVE;
        this.allowBreak = mode == GameType.SURVIVAL || mode == GameType.CREATIVE;
        this.allowPlace = allowBreak && throwawaySlot(inventory) >= 0;
        this.minY = level.getMinY();
        this.maxY = level.getMinY() + level.getHeight() - 1;
    }

    public static NavWorld snapshot(LocalPlayer player, ClientLevel level, MultiPlayerGameMode gameMode) {
        return new NavWorld(player, level, gameMode, true);
    }

    public static NavWorld live(LocalPlayer player, ClientLevel level, MultiPlayerGameMode gameMode) {
        return new NavWorld(player, level, gameMode, false);
    }

    public float space(BlockPos pos) {
        return space(pos.getX(), pos.getY(), pos.getZ());
    }

    public float space(int x, int y, int z) {
        if (!cached)
            return computeSpace(x, y, z);

        long key = BlockPos.asLong(x, y, z);
        float value = spaceCache.get(key);
        if (Float.isNaN(value)) {
            value = computeSpace(x, y, z);
            spaceCache.put(key, value);
        }
        return value;
    }

    public boolean standable(BlockPos pos) {
        return standable(pos.getX(), pos.getY(), pos.getZ());
    }

    public boolean standable(int x, int y, int z) {
        if (!cached)
            return computeStandable(x, y, z);

        long key = BlockPos.asLong(x, y, z);
        byte value = floorCache.get(key);
        if (value < 0) {
            value = (byte) (computeStandable(x, y, z) ? 1 : 0);
            floorCache.put(key, value);
        }
        return value == 1;
    }

    public boolean replaceable(BlockPos pos) {
        return replaceable(pos.getX(), pos.getY(), pos.getZ());
    }

    public boolean replaceable(int x, int y, int z) {
        if (y < minY || y > maxY || !level.hasChunk(x >> 4, z >> 4))
            return false;

        BlockState state = level.getBlockState(cursor.set(x, y, z));
        return state.canBeReplaced() && !isLava(state.getFluidState());
    }

    public boolean clickable(BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isCollisionShapeFullBlock(level, pos)
                && !state.hasBlockEntity()
                && !(state.getBlock() instanceof NoteBlock)
                && state.getMenuProvider(level, pos) == null;
    }

    private float computeSpace(int x, int y, int z) {
        if (y < minY || y > maxY || !level.hasChunk(x >> 4, z >> 4))
            return BLOCKED;

        BlockPos.MutableBlockPos pos = cursor.set(x, y, z);
        BlockState state = level.getBlockState(pos);
        if (state.isAir())
            return FREE;

        Block block = state.getBlock();
        if (block instanceof BaseFireBlock || block instanceof NetherPortalBlock || block instanceof EndPortalBlock || block instanceof EndGatewayBlock)
            return BLOCKED;

        FluidState fluid = state.getFluidState();
        if (isLava(fluid))
            return BLOCKED;

        VoxelShape shape = state.getCollisionShape(level, pos);
        boolean open = shape.isEmpty() || shape.max(Direction.Axis.Y) <= 0.125D;
        if (open) {
            if (block instanceof WebBlock || block instanceof SweetBerryBushBlock || block instanceof WitherRoseBlock || block instanceof PowderSnowBlock)
                return breakCost(state, pos);
            return fluid.isEmpty() ? FREE : WATER;
        }

        return breakCost(state, pos);
    }

    private float breakCost(BlockState state, BlockPos pos) {
        if (!allowBreak || state.hasBlockEntity())
            return BLOCKED;

        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0.0F)
            return BLOCKED;

        for (Direction direction : LIQUID_CHECK) {
            neighbor.setWithOffset(pos, direction);
            if (!level.getBlockState(neighbor).getFluidState().isEmpty())
                return BLOCKED;
        }

        if (creative)
            return 6.0F + BREAK_PENALTY;

        float best = Float.MAX_VALUE;
        for (ItemStack stack : hotbar)
            best = Math.min(best, breakTicks(state, hardness, stack));

        return best > MAX_BREAK_TICKS ? BLOCKED : best + BREAK_PENALTY;
    }

    private boolean computeStandable(int x, int y, int z) {
        if (y < minY || y > maxY || !level.hasChunk(x >> 4, z >> 4))
            return false;

        BlockPos.MutableBlockPos pos = cursor.set(x, y, z);
        BlockState state = level.getBlockState(pos);
        if (state.isAir())
            return false;

        Block block = state.getBlock();
        if (block instanceof MagmaBlock || block instanceof CampfireBlock || block instanceof CactusBlock || isLava(state.getFluidState()))
            return false;

        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty())
            return false;

        AABB bounds = shape.bounds();
        return bounds.maxY >= 0.85D && bounds.maxY <= 1.0D
                && bounds.maxX - bounds.minX >= 0.6D
                && bounds.maxZ - bounds.minZ >= 0.6D;
    }

    private static boolean isLava(FluidState fluid) {
        return !fluid.isEmpty() && (fluid.getType().isSame(Fluids.LAVA) || fluid.getType().isSame(Fluids.FLOWING_LAVA));
    }

    private static float breakTicks(BlockState state, float hardness, ItemStack stack) {
        if (hardness == 0.0F)
            return 1.0F;

        float speed = Math.max(1.0F, stack.getDestroySpeed(state));
        boolean correct = !state.requiresCorrectToolForDrops() || stack.isCorrectToolForDrops(state);
        return (float) Math.ceil(hardness * (correct ? 30.0F : 100.0F) / speed);
    }

    public static int bestToolSlot(Inventory inventory, BlockState state, float hardness) {
        int bestSlot = -1;
        float best = breakTicks(state, hardness, inventory.getItem(inventory.getSelectedSlot()));
        for (int i = 0; i < 9; i++) {
            float ticks = breakTicks(state, hardness, inventory.getItem(i));
            if (ticks < best) {
                best = ticks;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    public static int throwawaySlot(Inventory inventory) {
        int selected = inventory.getSelectedSlot();
        if (isThrowaway(inventory.getItem(selected)))
            return selected;

        for (int i = 0; i < 9; i++) {
            if (isThrowaway(inventory.getItem(i)))
                return i;
        }
        return -1;
    }

    private static boolean isThrowaway(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem && THROWAWAY.contains(blockItem.getBlock());
    }
}
