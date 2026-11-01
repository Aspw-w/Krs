package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.player.Freecam;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugEntryPosition;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Locale;

@Mixin(DebugEntryPosition.class)
public abstract class DebugEntryPositionMixin {
    @Inject(method = "display", at = @At("HEAD"), cancellable = true)
    private void freecamPositionHook(DebugScreenDisplayer displayer, Level serverOrClientLevel, LevelChunk clientChunk, LevelChunk serverChunk, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ModuleManager.getModuleState(Freecam.class) || !Freecam.getCanFly() || minecraft.level == null)
            return;

        ci.cancel();
        Vec3 position = Freecam.getCamPos(minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true));
        float yaw = Freecam.getCamYaw();
        float pitch = Freecam.getCamPitch();
        BlockPos blockPos = BlockPos.containing(position);
        ChunkPos chunkPos = ChunkPos.containing(blockPos);
        Direction direction = Direction.fromYRot(yaw);
        String faceString = switch (direction) {
            case NORTH -> "Towards negative Z";
            case SOUTH -> "Towards positive Z";
            case WEST -> "Towards negative X";
            case EAST -> "Towards positive X";
            default -> "Invalid";
        };
        LongSet chunks = serverOrClientLevel instanceof ServerLevel serverLevel ? serverLevel.getForceLoadedChunks() : LongSets.EMPTY_SET;
        displayer.addToGroup(
                DebugEntryPosition.GROUP,
                List.of(
                        String.format(Locale.ROOT, "XYZ: %.3f / %.5f / %.3f", position.x, position.y, position.z),
                        String.format(Locale.ROOT, "Block: %d %d %d", blockPos.getX(), blockPos.getY(), blockPos.getZ()),
                        String.format(
                                Locale.ROOT,
                                "Chunk: %d %d %d [%d %d in r.%d.%d.mca]",
                                chunkPos.x(),
                                SectionPos.blockToSectionCoord(blockPos.getY()),
                                chunkPos.z(),
                                chunkPos.getRegionLocalX(),
                                chunkPos.getRegionLocalZ(),
                                chunkPos.getRegionX(),
                                chunkPos.getRegionZ()
                        ),
                        String.format(Locale.ROOT, "Facing: %s (%s) (%.1f / %.1f)", direction, faceString, Mth.wrapDegrees(yaw), Mth.wrapDegrees(pitch)),
                        minecraft.level.dimension().identifier() + " FC: " + chunks.size()
                )
        );
    }
}
