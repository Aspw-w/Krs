package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.events.features.TickEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.mojang.math.Transformation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class Imposter extends Module {
    private static final AtomicInteger NEXT_ENTITY_ID = new AtomicInteger(Integer.MIN_VALUE);
    private static final float HEIGHT_ABOVE_PLAYER = 0.25f;
    private static final int PART_COUNT = 9;

    private final List<FollowingBlockDisplay> parts = new ArrayList<>(PART_COUNT);
    private ClientLevel spawnedLevel;

    public Imposter() {
        super("Imposter", ModuleCategory.Render, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    @Override
    public String description() {
        return "Spawns an Among Us Imposter above you.";
    }

    @Override
    public void onEnable() {
        spawnIfPossible();
    }

    @Override
    public void onDisable() {
        removeImposter();
    }

    @Override
    public void onWorld(WorldEvent event) {
        removeImposter();
    }

    @Override
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.level == null) {
            removeImposter();
            return;
        }

        if (spawnedLevel != mc.level || !hasCompleteImposter())
            spawnIfPossible();
    }

    private void spawnIfPossible() {
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null)
            return;

        removeImposter();
        spawnedLevel = level;

        // Legs and suit body.
        addPart(level, player, Blocks.CONCRETE.red(), -0.37f, 0.00f, -0.18f, 0.28f, 0.43f, 0.36f);
        addPart(level, player, Blocks.CONCRETE.red(), 0.09f, 0.00f, -0.18f, 0.28f, 0.43f, 0.36f);
        addPart(level, player, Blocks.CONCRETE.red(), -0.42f, 0.35f, -0.22f, 0.84f, 0.72f, 0.44f);

        // Stepped head gives the block model a rounded Among Us silhouette.
        addPart(level, player, Blocks.CONCRETE.red(), -0.44f, 0.90f, -0.23f, 0.88f, 0.43f, 0.46f);
        addPart(level, player, Blocks.CONCRETE.red(), -0.34f, 1.28f, -0.21f, 0.68f, 0.17f, 0.42f);

        // Backpack, visor border, visor, and its highlight. Local +Z is forward.
        addPart(level, player, Blocks.DYED_TERRACOTTA.red(), -0.34f, 0.57f, -0.40f, 0.68f, 0.55f, 0.18f);
        addPart(level, player, Blocks.CONCRETE.black(), -0.34f, 1.01f, 0.23f, 0.68f, 0.29f, 0.08f);
        addPart(level, player, Blocks.CONCRETE.lightBlue(), -0.30f, 1.05f, 0.31f, 0.60f, 0.21f, 0.055f);
        addPart(level, player, Blocks.CONCRETE.white(), -0.23f, 1.19f, 0.366f, 0.23f, 0.045f, 0.025f);
    }

    private void addPart(ClientLevel level, LocalPlayer player, Block block,
                         float x, float y, float z, float width, float height, float depth) {
        FollowingBlockDisplay part = new FollowingBlockDisplay(
                level,
                player,
                block,
                new Transformation(
                        new Vector3f(x, y, z),
                        new Quaternionf(),
                        new Vector3f(width, height, depth),
                        new Quaternionf()
                )
        );

        parts.add(part);
        level.addEntity(part);
    }

    private boolean hasCompleteImposter() {
        if (parts.size() != PART_COUNT)
            return false;

        for (FollowingBlockDisplay part : parts) {
            if (part.isRemoved() || part.level() != spawnedLevel)
                return false;
        }

        return true;
    }

    private void removeImposter() {
        for (FollowingBlockDisplay part : parts) {
            if (!part.isRemoved())
                part.discard();
        }

        parts.clear();
        spawnedLevel = null;
    }

    private static final class FollowingBlockDisplay extends Display.BlockDisplay {
        private final LocalPlayer player;

        private FollowingBlockDisplay(ClientLevel level, LocalPlayer player, Block block, Transformation transformation) {
            super(EntityTypes.BLOCK_DISPLAY, level);
            this.player = player;

            setId(NEXT_ENTITY_ID.getAndIncrement());
            setBlockState(block.defaultBlockState());
            setTransformation(transformation);
            setNoGravity(true);
            setSilent(true);
            setInvulnerable(true);

            followPlayer();
            setOldPosAndRot();
        }

        @Override
        public void tick() {
            super.tick();

            if (player.isRemoved() || player.level() != level()) {
                discard();
                return;
            }

            followPlayer();
        }

        private void followPlayer() {
            Vec3 position = player.position();
            setPos(position.x, position.y + player.getBbHeight() + HEIGHT_ABOVE_PLAYER, position.z);
            setYRot(player.getYRot());
            setXRot(0.0f);
        }
    }
}
