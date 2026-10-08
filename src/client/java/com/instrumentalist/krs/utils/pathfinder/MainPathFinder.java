package com.instrumentalist.krs.utils.pathfinder;

import com.instrumentalist.krs.utils.IMinecraft;
import net.minecraft.client.multiplayer.ClientLevel;
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
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Locale;

public class MainPathFinder implements IMinecraft {
    private final ArrayList<Vec3> path = new ArrayList<>();

    public MainPathFinder(final Vec3 startVec3, final Vec3 endVec3) {
        Vec3 startVec31 = new Vec3(startVec3.x, startVec3.y, startVec3.z);
        Vec3 endVec31 = new Vec3(endVec3.x, endVec3.y, endVec3.z);
    }

    public ArrayList<Vec3> getPath() {
        return this.path;
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
        ClientLevel level = mc.level;
        if (level == null) return true;
        Block b = level.getBlockState(block).getBlock();

        return b.defaultBlockState().isRedstoneConductor(level, block)
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
                || b instanceof StainedGlassBlock
                || b instanceof TintedGlassBlock
                || b instanceof PistonBaseBlock
                || b instanceof PistonHeadBlock
                || b instanceof TrapDoorBlock
                || b instanceof EndPortalBlock
                || b instanceof EndPortalFrameBlock
                || b instanceof BedBlock
                || b instanceof WebBlock
                || b instanceof BarrierBlock
                || b instanceof LadderBlock
                || b instanceof CarpetBlock;
    }

    private static boolean canWalkOn(final BlockPos block) {
        ClientLevel level = mc.level;
        if (level == null) return false;
        Block b = level.getBlockState(block).getBlock();

        return !(b instanceof FenceBlock) && !(b instanceof WallBlock);
    }

    private static ArrayList<Vec3> getVerticalPassThroughPath(final Vec3 from, final Vec3 to) {
        final int fromX = (int) Math.floor(from.x);
        final int fromZ = (int) Math.floor(from.z);
        final int toX = (int) Math.floor(to.x);
        final int toZ = (int) Math.floor(to.z);
        final double yDistance = Math.abs(to.y - from.y);

        if (fromX != toX || fromZ != toZ || yDistance <= 0.0 || yDistance > com.instrumentalist.krs.hacks.features.exploit.PathFinder.maxVerticalRange.get()) {
            return null;
        }

        final ArrayList<Vec3> path = new ArrayList<>();
        final int steps = Math.max(1, (int) Math.ceil(yDistance));

        for (int i = 1; i <= steps; i++) {
            final double progress = (double) i / steps;
            path.add(new Vec3(from.x, from.y + (to.y - from.y) * progress, from.z));
        }

        return path;
    }

    public static ArrayList<Vec3> computePath(Vec3 topFrom, final Vec3 to) {
        if (!isFinite(topFrom) || !isFinite(to) || mc.level == null)
            return new ArrayList<>();

        switch (com.instrumentalist.krs.hacks.features.exploit.PathFinder.mode.get().toLowerCase(Locale.ROOT)) {
            case "reconstruct":
                if (com.instrumentalist.krs.hacks.features.exploit.PathFinder.verticalPassThrough.get()) {
                    final ArrayList<Vec3> verticalPath = getVerticalPassThroughPath(topFrom, to);
                    if (verticalPath != null) return verticalPath;
                }

                final PathFinder pathfinder = new PathFinder(topFrom, to);
                pathfinder.compute();

                int i = 0;
                Vec3 lastLoc = null;
                Vec3 lastDashLoc = null;
                final ArrayList<Vec3> path = new ArrayList<>();
                final ArrayList<Vec3> pathFinderPath = pathfinder.getPath();

                for (final Vec3 pathElm : pathFinderPath) {
                    if (i == 0 || i == pathFinderPath.size() - 1) {
                        if (lastLoc != null) {
                            path.add(lastLoc.add(0.6, 0.0, 0.6));
                        }
                        path.add(pathElm.add(0.6, 0.0, 0.6));
                        lastDashLoc = pathElm;
                    } else {
                        boolean canContinue = true;
                        if (pathElm.distanceToSqr(lastDashLoc) > 5 * 5) {
                            canContinue = false;
                        } else {
                            search:
                            for (int x = Math.min((int) lastDashLoc.x, (int) pathElm.x);
                                 x <= Math.max((int) lastDashLoc.x, (int) pathElm.x); x++) {
                                for (int y = Math.min((int) lastDashLoc.y, (int) pathElm.y);
                                     y <= Math.max((int) lastDashLoc.y, (int) pathElm.y); y++) {
                                    for (int z = Math.min((int) lastDashLoc.z, (int) pathElm.z);
                                         z <= Math.max((int) lastDashLoc.z, (int) pathElm.z); z++) {
                                        if (!isValid(x, y, z, false)) {
                                            canContinue = false;
                                            break search;
                                        }
                                    }
                                }
                            }
                        }

                        if (!canContinue) {
                            path.add(lastLoc.add(0.6, 0.0, 0.6));
                            lastDashLoc = lastLoc;
                        }
                    }
                    lastLoc = pathElm;
                    i++;
                }

                return path;

            case "linear":
                return LinearPathFinder.INSTANCE.getPaths(new BlockPos((int) Math.floor(topFrom.x), (int) Math.floor(topFrom.y), (int) Math.floor(topFrom.z)), new BlockPos((int) Math.floor(to.x), (int) Math.floor(to.y), (int) Math.floor(to.z)), com.instrumentalist.krs.hacks.features.exploit.PathFinder.linearSteps.get(), 4);
        }

        return new ArrayList<>();
    }

    private static boolean isFinite(Vec3 vec) {
        return vec != null
                && Double.isFinite(vec.x)
                && Double.isFinite(vec.y)
                && Double.isFinite(vec.z);
    }
}