package com.instrumentalist.krs.utils.pathfinder;

import com.instrumentalist.krs.utils.IMinecraft;
import net.minecraft.world.level.block.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.BarrierBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TintedGlassBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

public class PathFinder implements IMinecraft {
    private final Vec3 startVec3Path;
    private final Vec3 endVec3Path;
    private static final CompareHub COMPARE_HUB = new CompareHub();
    private final ArrayList<PathHub> pathHubs = new ArrayList<>();
    private final ArrayList<PathHub> expansionBatch = new ArrayList<>(4);
    private final PriorityQueue<PathHub> workingPathHubList = new PriorityQueue<>(COMPARE_HUB);
    private final Map<Vec3, PathHub> pathHubByLocation = new HashMap<>();
    private ArrayList<Vec3> path = new ArrayList<>();
    private long nextSequence;

    public PathFinder(final Vec3 startVec3Path, final Vec3 endVec3Path) {
        this.startVec3Path = normalizePathPosition(startVec3Path);
        this.endVec3Path = normalizePathPosition(endVec3Path);
    }

    private static Vec3 floorVec3d(Vec3 vec) {
        return new Vec3(Math.floor(vec.x), Math.floor(vec.y), Math.floor(vec.z));
    }

    static Vec3 normalizePathPosition(Vec3 vec) {
        if (vec == null)
            return new Vec3(Double.NaN, Double.NaN, Double.NaN);
        if (!Double.isFinite(vec.x) || !Double.isFinite(vec.y) || !Double.isFinite(vec.z))
            return vec;

        Vec3 floored = floorVec3d(vec);
        if (mc.level == null)
            return floored;

        BlockPos feetBlock = BlockPos.containing(vec);
        var feetState = mc.level.getBlockState(feetBlock);
        if (feetState.getBlock() instanceof CarpetBlock)
            return floored;

        VoxelShape collisionShape = feetState.getCollisionShape(mc.level, feetBlock);
        if (collisionShape.isEmpty())
            return floored;

        double localY = vec.y - feetBlock.getY();
        double collisionTop = collisionTopAt(
                collisionShape,
                vec.x - feetBlock.getX(),
                vec.z - feetBlock.getZ()
        );
        if (Double.isFinite(collisionTop)
                && collisionTop < 1.0D
                && localY + 1.0E-4D >= collisionTop)
            return new Vec3(floored.x, floored.y + 1.0D, floored.z);

        return floored;
    }

    public static boolean isValid(final Vec3 loc, final boolean checkGround) {
        return isValid((int) loc.x, (int) loc.y, (int) loc.z, checkGround);
    }

    public static boolean isValid(final int x, final int y, final int z, final boolean checkGround) {
        if (mc.level == null) return false;

        final BlockPos block1 = new BlockPos(x, y, z);
        final BlockPos block2 = new BlockPos(x, y + 1, z);
        final BlockPos block3 = new BlockPos(x, y - 1, z);
        return !isNotPassable(block1) && !isNotPassable(block2)
                && (isNotPassable(block3) || !checkGround)
                && canWalkOn(block3);
    }

    private static boolean isNotPassable(final BlockPos block) {
        if (mc.level == null) return true;
        var state = mc.level.getBlockState(block);
        final Block b = state.getBlock();

        // These occupy the player's feet block without acting as a full cube.
        // Carpet supplies a thin floor and ladders remain traversable/climbable.
        if (b instanceof CarpetBlock || b instanceof LadderBlock)
            return false;
        if (b instanceof FenceGateBlock)
            return !state.getValue(FenceGateBlock.OPEN);
        if (b instanceof FenceBlock)
            return true;

        return !state.getCollisionShape(mc.level, block).isEmpty()
                || state.isRedstoneConductor(mc.level, block)
                || b == Blocks.GLASS
                || Blocks.STAINED_GLASS.asList().contains(b)
                || b == Blocks.GLASS_PANE
                || Blocks.STAINED_GLASS_PANE.asList().contains(b)
                || b instanceof AbstractSkullBlock
                || b instanceof SnowLayerBlock
                || b == Blocks.GRASS_BLOCK
                || b == Blocks.MYCELIUM
                || b == Blocks.PODZOL
                || b instanceof DoorBlock
                || b instanceof LeavesBlock
                || b instanceof SlabBlock
                || b instanceof StairBlock
                || b instanceof CactusBlock
                || b instanceof ChestBlock
                || b instanceof EnderChestBlock
                || b instanceof SkullBlock
                || b instanceof IronBarsBlock
                || b instanceof FenceBlock
                || b instanceof WallBlock
                || b instanceof TintedGlassBlock
                || b instanceof StainedGlassBlock
                || b instanceof PistonBaseBlock
                || b instanceof MovingPistonBlock
                || b instanceof PistonHeadBlock
                || b instanceof TrapDoorBlock
                || b instanceof EndPortalFrameBlock
                || b instanceof EndPortalBlock
                || b instanceof BedBlock
                || b instanceof WebBlock
                || b instanceof BarrierBlock;
    }

    private static boolean canWalkOn(final BlockPos block) {
        if (mc.level == null) return false;
        final Block b = mc.level.getBlockState(block).getBlock();
        return !(b instanceof FenceBlock) && !(b instanceof FenceGateBlock) && !(b instanceof WallBlock) && b != Blocks.BARRIER;
    }

    static double collisionTopAt(VoxelShape shape, double localX, double localZ) {
        if (shape == null || shape.isEmpty())
            return Double.NEGATIVE_INFINITY;

        double top = Double.NEGATIVE_INFINITY;
        double sampleX = Math.clamp(localX, 0.0D, 1.0D);
        double sampleZ = Math.clamp(localZ, 0.0D, 1.0D);
        for (AABB box : shape.toAabbs()) {
            if (sampleX + 1.0E-6D < box.minX || sampleX - 1.0E-6D > box.maxX
                    || sampleZ + 1.0E-6D < box.minZ || sampleZ - 1.0E-6D > box.maxZ)
                continue;
            top = Math.max(top, box.maxY);
        }
        return top;
    }

    public ArrayList<Vec3> getPath() {
        return this.path;
    }

    public void compute() {
        this.compute(100, 4);
    }

    public void compute(final int loops, final int depth) {
        this.path.clear();
        this.pathHubs.clear();
        this.expansionBatch.clear();
        this.workingPathHubList.clear();
        this.pathHubByLocation.clear();
        this.nextSequence = 0L;

        if (loops <= 0 || depth <= 0 || mc.level == null
                || !isFinite(this.startVec3Path) || !isFinite(this.endVec3Path)) return;

        PathHub startHub = new PathHub(this.startVec3Path, null,
                this.startVec3Path.distanceToSqr(this.endVec3Path), 0.0, 0.0, nextSequence++);
        this.workingPathHubList.add(startHub);
        this.pathHubByLocation.put(this.startVec3Path, startHub);

        for (int i = 0; i < loops; ++i) {
            if (this.workingPathHubList.isEmpty()) break;

            final int nodesToExpand = Math.min(depth, this.workingPathHubList.size());
            for (int j = 0; j < nodesToExpand; j++) {
                final PathHub pathHub = this.workingPathHubList.poll();
                if (pathHub == null) break;
                this.expansionBatch.add(pathHub);
            }

            for (int j = 0, n = this.expansionBatch.size(); j < n; j++) {
                final PathHub pathHub = this.expansionBatch.get(j);
                this.pathHubs.add(pathHub);

                if (tryDirection(pathHub, 1.0, 0.0, 0.0)
                        || tryDirection(pathHub, -1.0, 0.0, 0.0)
                        || tryDirection(pathHub, 0.0, 0.0, 1.0)
                        || tryDirection(pathHub, 0.0, 0.0, -1.0)
                        || tryDirection(pathHub, 0.0, 1.0, 0.0)
                        || tryDirection(pathHub, 0.0, -1.0, 0.0)) {
                    return;
                }

            }
            this.expansionBatch.clear();
        }

        if (!this.pathHubs.isEmpty()) {
            PathHub closestHub = this.pathHubs.getFirst();
            for (int i = 1; i < this.pathHubs.size(); i++) {
                PathHub candidate = this.pathHubs.get(i);
                if (COMPARE_HUB.compare(candidate, closestHub) < 0)
                    closestHub = candidate;
            }
            this.path = closestHub.getPathway();
        }
    }

    private boolean tryDirection(PathHub pathHub, double x, double y, double z) {
        final Vec3 loc = floorVec3d(pathHub.getLoc().add(x, y, z));
        return isValid(loc, false) && this.putHub(pathHub, loc, 0.0);
    }

    public boolean putHub(final PathHub parent, final Vec3 loc, final double cost) {
        final PathHub existingPathHub = this.doesHubExistAt(loc);
        double totalCost = cost;

        if (parent != null) {
            totalCost += parent.getMaxCost();
        }

        if (existingPathHub == null) {
            if (loc.distanceToSqr(this.endVec3Path) <= 1) {
                PathHub destination = new PathHub(loc, parent,
                        0.0, cost, totalCost, nextSequence++);
                this.path = destination.getPathway();
                return true;
            }

            PathHub newHub = new PathHub(loc, parent,
                    loc.distanceToSqr(this.endVec3Path), cost, totalCost, nextSequence++);
            this.workingPathHubList.add(newHub);
            this.pathHubByLocation.put(loc, newHub);
        }
        return false;
    }

    public PathHub doesHubExistAt(final Vec3 loc) {
        return this.pathHubByLocation.get(loc);
    }

    private static boolean isFinite(Vec3 vec) {
        return Double.isFinite(vec.x)
                && Double.isFinite(vec.y)
                && Double.isFinite(vec.z);
    }

    public static class CompareHub implements Comparator<PathHub> {
        @Override
        public int compare(final PathHub o1, final PathHub o2) {
            int scoreComparison = Double.compare(o1.getSqDist() + o1.getMaxCost(),
                    o2.getSqDist() + o2.getMaxCost());
            return scoreComparison != 0
                    ? scoreComparison
                    : Long.compare(o1.getSequence(), o2.getSequence());
        }
    }
}
