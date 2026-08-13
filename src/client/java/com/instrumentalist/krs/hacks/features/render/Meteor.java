package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.events.features.TickEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.mojang.math.Transformation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public final class Meteor extends Module {
    private static final AtomicInteger NEXT_ENTITY_ID = new AtomicInteger(-1_500_000_000);
    private static final int MAX_ACTIVE_METEORS = 4;
    private static final int MAX_METEOR_AGE = 160;
    private static final double METEOR_SPEED = 1.2;

    @Setting
    private final IntValue interval = new IntValue("Interval", 5, 1, 15, "s");

    @Setting
    private final IntValue radius = new IntValue("Radius", 32, 8, 64, "m");

    @Setting
    private final BooleanValue impactSound = new BooleanValue("Impact Sound", true);

    private final List<ClientMeteor> meteors = new ArrayList<>();
    private int spawnCooldown;

    public Meteor() {
        super("Meteor", ModuleCategory.Render, GLFW.GLFW_KEY_UNKNOWN, false, false);
    }

    @Override
    public String description() {
        return "Drops client-side meteors into the world.";
    }

    @Override
    public void onEnable() {
        clearMeteors();
        spawnCooldown = 0;
    }

    @Override
    public void onDisable() {
        clearMeteors();
    }

    @Override
    public void onWorld(WorldEvent event) {
        clearMeteors();
        spawnCooldown = 0;
    }

    @Override
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.level == null) {
            clearMeteors();
            spawnCooldown = 0;
            return;
        }

        meteors.removeIf(ClientMeteor::isRemoved);

        if (spawnCooldown > 0)
            spawnCooldown--;

        if (spawnCooldown <= 0) {
            if (meteors.size() < MAX_ACTIVE_METEORS)
                spawnMeteor(mc.level, mc.player);

            spawnCooldown = Math.max(1, interval.get() * 20);
        }
    }

    private void spawnMeteor(ClientLevel level, LocalPlayer player) {
        Vec3 impactPosition = findNearestSurface(level, player);
        if (impactPosition == null)
            return;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        double incomingAngle = random.nextDouble(Math.PI * 2.0);
        double horizontalRun = random.nextDouble(10.0, 19.0);
        double spawnHeight = random.nextDouble(36.0, 49.0);
        Vec3 startPosition = impactPosition.add(
                -Math.cos(incomingAngle) * horizontalRun,
                spawnHeight,
                -Math.sin(incomingAngle) * horizontalRun
        );
        Vec3 velocity = impactPosition.subtract(startPosition).normalize().scale(METEOR_SPEED);

        meteors.add(new ClientMeteor(level, startPosition, impactPosition, velocity));
    }

    private Vec3 findNearestSurface(ClientLevel level, LocalPlayer player) {
        Vec3 playerPos = player.position();
        Vec3 closest = null;
        double closestDistSq = Double.MAX_VALUE;

        Vec3 floorHit = clipSurface(level, player.getEyePosition(), playerPos.subtract(0.0, 256.0, 0.0));
        if (floorHit != null) {
            closest = floorHit;
            closestDistSq = playerPos.distanceToSqr(floorHit);
        }

        int originX = player.getBlockX();
        int originZ = player.getBlockZ();
        int spawnRadius = radius.get();

        for (int dx = -spawnRadius; dx <= spawnRadius; dx++) {
            for (int dz = -spawnRadius; dz <= spawnRadius; dz++) {
                if (dx * dx + dz * dz > spawnRadius * spawnRadius)
                    continue;

                int x = originX + dx;
                int z = originZ + dz;
                if (!level.isLoaded(BlockPos.containing(x, playerPos.y, z)))
                    continue;

                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                Vec3 surface = new Vec3(x + 0.5, surfaceY + 0.05, z + 0.5);
                double distSq = playerPos.distanceToSqr(surface);
                if (distSq < closestDistSq) {
                    closestDistSq = distSq;
                    closest = surface;
                }
            }
        }

        if (closest != null)
            return closest;

        BlockPos fallback = player.blockPosition();
        if (level.isLoaded(fallback))
            return new Vec3(player.getX(), player.getY(), player.getZ());

        return null;
    }

    private static Vec3 clipSurface(ClientLevel level, Vec3 start, Vec3 end) {
        HitResult hit = level.clip(new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                CollisionContext.empty()
        ));

        if (hit.getType() != HitResult.Type.BLOCK)
            return null;

        Vec3 location = hit.getLocation();
        return new Vec3(location.x, location.y + 0.05, location.z);
    }

    private void clearMeteors() {
        Iterator<ClientMeteor> iterator = meteors.iterator();
        while (iterator.hasNext()) {
            iterator.next().remove();
            iterator.remove();
        }
    }

    private void createTrail(ClientMeteor meteor) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Vec3 backwards = meteor.velocity.normalize().scale(-1.0);

        for (int i = 0; i < 3; i++) {
            double distance = random.nextDouble(0.2, 2.8);
            Vec3 particlePosition = meteor.position.add(backwards.scale(distance)).add(
                    random.nextDouble(-0.35, 0.35),
                    random.nextDouble(-0.35, 0.35),
                    random.nextDouble(-0.35, 0.35)
            );

            meteor.level.addParticle(
                    ParticleTypes.FLAME,
                    particlePosition.x,
                    particlePosition.y,
                    particlePosition.z,
                    backwards.x * 0.04,
                    backwards.y * 0.04,
                    backwards.z * 0.04
            );
        }

        if ((meteor.age & 1) == 0) {
            Vec3 smokePosition = meteor.position.add(backwards.scale(1.4));
            meteor.level.addParticle(
                    ParticleTypes.LARGE_SMOKE,
                    smokePosition.x,
                    smokePosition.y,
                    smokePosition.z,
                    backwards.x * 0.025,
                    0.03,
                    backwards.z * 0.025
            );
        }

        if (meteor.age % 4 == 0) {
            meteor.level.addParticle(
                    ParticleTypes.LAVA,
                    meteor.position.x,
                    meteor.position.y,
                    meteor.position.z,
                    0.0,
                    0.0,
                    0.0
            );
        }
    }

    private void createImpact(ClientLevel level, Vec3 position) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        level.addParticle(
                ParticleTypes.EXPLOSION_EMITTER,
                true,
                true,
                position.x,
                position.y,
                position.z,
                0.0,
                0.0,
                0.0
        );

        for (int i = 0; i < 48; i++) {
            double angle = random.nextDouble(Math.PI * 2.0);
            double horizontal = random.nextDouble(0.08, 0.65);
            double vertical = random.nextDouble(0.05, 0.7);
            double velocityX = Math.cos(angle) * horizontal;
            double velocityZ = Math.sin(angle) * horizontal;

            level.addParticle(
                    i % 3 == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME,
                    position.x + random.nextDouble(-0.5, 0.5),
                    position.y + random.nextDouble(0.0, 0.5),
                    position.z + random.nextDouble(-0.5, 0.5),
                    velocityX,
                    vertical,
                    velocityZ
            );

            if (i % 4 == 0) {
                level.addParticle(
                        ParticleTypes.LARGE_SMOKE,
                        position.x,
                        position.y + 0.25,
                        position.z,
                        velocityX * 0.35,
                        vertical * 0.35,
                        velocityZ * 0.35
                );
            }
        }

        if (impactSound.get()) {
            level.playLocalSound(
                    position.x,
                    position.y,
                    position.z,
                    SoundEvents.GENERIC_EXPLODE.value(),
                    SoundSource.BLOCKS,
                    4.0f,
                    random.nextFloat(0.62f, 0.78f),
                    false
            );
        }
    }

    private final class ClientMeteor {
        private static final int PART_COUNT = 5;

        private final ClientLevel level;
        private final Vec3 expectedImpactPosition;
        private final Vec3 velocity;
        private final List<MeteorPart> parts = new ArrayList<>(PART_COUNT);

        private Vec3 position;
        private long lastUpdatedGameTick = Long.MIN_VALUE;
        private int age;
        private float yaw;
        private float pitch;
        private boolean removed;

        private ClientMeteor(ClientLevel level, Vec3 startPosition, Vec3 expectedImpactPosition, Vec3 velocity) {
            this.level = level;
            this.position = startPosition;
            this.expectedImpactPosition = expectedImpactPosition;
            this.velocity = velocity;

            addPart(Blocks.MAGMA_BLOCK, -0.58f, -0.58f, -0.58f, 1.16f, 1.16f, 1.16f);
            addPart(Blocks.BLACKSTONE, -0.82f, -0.24f, -0.22f, 0.52f, 0.58f, 0.48f);
            addPart(Blocks.OBSIDIAN, 0.30f, -0.46f, -0.18f, 0.48f, 0.55f, 0.50f);
            addPart(Blocks.BASALT, -0.22f, 0.34f, 0.26f, 0.52f, 0.48f, 0.46f);
            addPart(Blocks.MAGMA_BLOCK, 0.26f, 0.16f, -0.66f, 0.46f, 0.44f, 0.48f);
        }

        private void addPart(Block block, float x, float y, float z, float width, float height, float depth) {
            MeteorPart part = new MeteorPart(
                    this,
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

        private boolean advance() {
            if (removed)
                return false;

            long gameTick = level.getGameTime();
            if (lastUpdatedGameTick == gameTick)
                return true;

            lastUpdatedGameTick = gameTick;

            if (level != mc.level || hasMissingPart()) {
                remove();
                return false;
            }

            Vec3 nextPosition = position.add(velocity);
            HitResult collision = level.clip(new ClipContext(
                    position,
                    nextPosition,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    CollisionContext.empty()
            ));

            if (collision.getType() == HitResult.Type.BLOCK || nextPosition.y <= expectedImpactPosition.y) {
                Vec3 impactPosition = collision.getType() == HitResult.Type.BLOCK
                        ? collision.getLocation()
                        : expectedImpactPosition;
                createImpact(level, impactPosition);
                remove();
                return false;
            }

            position = nextPosition;
            yaw = (yaw + 14.0f) % 360.0f;
            pitch = (pitch + 9.0f) % 360.0f;
            age++;
            createTrail(this);

            if (age > MAX_METEOR_AGE) {
                remove();
                return false;
            }

            return true;
        }

        private boolean hasMissingPart() {
            if (parts.size() != PART_COUNT)
                return true;

            for (MeteorPart part : parts) {
                if (part.isRemoved() || part.level() != level)
                    return true;
            }

            return false;
        }

        private void remove() {
            if (removed)
                return;

            removed = true;
            for (MeteorPart part : parts) {
                if (!part.isRemoved())
                    part.discard();
            }
        }

        private boolean isRemoved() {
            return removed;
        }
    }

    private static final class MeteorPart extends Display.BlockDisplay {
        private final ClientMeteor meteor;

        private MeteorPart(ClientMeteor meteor, Block block, Transformation transformation) {
            super(EntityTypes.BLOCK_DISPLAY, meteor.level);
            this.meteor = meteor;

            setId(NEXT_ENTITY_ID.getAndIncrement());
            setBlockState(block.defaultBlockState());
            setTransformation(transformation);
            setBrightnessOverride(Brightness.FULL_BRIGHT);
            setViewRange(4.0f);
            setNoGravity(true);
            setSilent(true);
            setInvulnerable(true);

            applyMeteorPose();
            setOldPosAndRot();
        }

        @Override
        public void tick() {
            super.tick();

            if (!meteor.advance()) {
                discard();
                return;
            }

            applyMeteorPose();
        }

        private void applyMeteorPose() {
            setPos(meteor.position);
            setYRot(meteor.yaw);
            setXRot(meteor.pitch);
        }
    }
}
