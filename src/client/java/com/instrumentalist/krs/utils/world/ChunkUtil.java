package com.instrumentalist.krs.utils.world;

import com.instrumentalist.krs.utils.IMinecraft;
import java.util.stream.Stream;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;

public enum ChunkUtil implements IMinecraft {;

    public static Stream<BlockEntity> getLoadedBlockEntities() {
        Stream.Builder<BlockEntity> blockEntities = Stream.builder();
        getLoadedChunks().forEach(chunk -> {
            for (BlockEntity blockEntity : chunk.getBlockEntities().values())
                blockEntities.add(blockEntity);
        });
        return blockEntities.build();
    }

    public static int getManhattanDistance(ChunkPos a, ChunkPos b)
    {
        return Math.abs(a.x() - b.x()) + Math.abs(a.z() - b.z());
    }

    public static ChunkPos getAffectedChunk(Packet<?> packet) {
        if (packet instanceof ClientboundBlockUpdatePacket p)
            return new ChunkPos(p.getPos().getX() >> 4, p.getPos().getZ() >> 4);
        if (packet instanceof ClientboundSectionBlocksUpdatePacket p)
            return p.sectionPos.chunk();
        if (packet instanceof ClientboundLevelChunkWithLightPacket p)
            return new ChunkPos(p.x(), p.z());

        return null;
    }

    public static Stream<LevelChunk> getLoadedChunks() {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null)
            return Stream.empty();

        int radius = Math.max(2, mc.options.getEffectiveRenderDistance()) + 3;
        ChunkPos center = mc.player.chunkPosition();
        int minX = center.x() - radius;
        int maxX = center.x() + radius;
        int minZ = center.z() - radius;
        int maxZ = center.z() + radius;

        Stream.Builder<LevelChunk> chunks = Stream.builder();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (level.hasChunk(x, z)) {
                    LevelChunk chunk = level.getChunk(x, z);
                    if (chunk != null)
                        chunks.add(chunk);
                }
            }
        }

        return chunks.build();
    }

    public static int getHighestNonEmptySectionYOffset(ChunkAccess chunk) {
        int i = chunk.getHighestFilledSectionIndex();
        if (i == -1)
            return chunk.getMinY();

        return SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
    }
}
