package com.instrumentalist.krs.utils.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Optional;

public class RotationManager {
    private final Minecraft mc = Minecraft.getInstance();

    private float clientYaw;
    private float clientPitch;
    private float prevClientYaw;
    private float prevClientPitch;
    private float serverYaw;
    private float serverPitch;
    private float prevRenderYaw;
    private float renderYaw;
    private float prevRenderBodyYaw;
    private float renderBodyYaw;
    private float prevRenderHeadPitch;
    private float renderHeadPitch;
    private long lastRotationUpdate = 0L;
    private boolean isRotating = false;
    private boolean isReturning = false;
    private boolean returnFramePrepared = false;
    private boolean initialized = false;
    private boolean serverRotationInitialized = false;

    public RotationManager() {
        if (mc.player != null) {
            syncWithPlayerRotation();
        }
    }

    public void update() {
        tick();
    }

    public void tick() {
        if (!ensureInitializedFromPlayer()) {
            return;
        }

        long elapsedSinceRotation = elapsedRotationMillis();

        if (isRotating) {
            if (elapsedSinceRotation > 0L) {
                updateRotations();
            }

            if (elapsedSinceRotation > 1000L) {
                beginReturnToPlayerRotation();
            } else {
                return;
            }
        }

        if (isReturning && mc.player != null) {
            updateRotations();
            clientYaw += Mth.wrapDegrees(mc.player.getYRot() - clientYaw);
            clientPitch = mc.player.getXRot();

            if (returnFramePrepared) {
                isReturning = false;
                returnFramePrepared = false;
            }
        } else if (mc.player != null) {
            updateRotations();
            clientYaw = mc.player.getYRot();
            clientPitch = mc.player.getXRot();
        }
    }

    public void postMotionVisualTick() {
        if (!isRotating()) {
            if (mc.player == null) {
                prevRenderHeadPitch = 0.0f;
                renderHeadPitch = 0.0f;
                prevRenderYaw = 0.0f;
                renderYaw = 0.0f;
                prevRenderBodyYaw = 0.0f;
                renderBodyYaw = 0.0f;
            } else {
                prevRenderHeadPitch = renderHeadPitch;
                renderHeadPitch = mc.player.getXRot();
                prevRenderYaw = renderYaw;
                renderYaw = mc.player.getYRot();
                prevRenderBodyYaw = renderBodyYaw;
                renderBodyYaw = mc.player.yBodyRot;
            }
            return;
        }

        prevRenderHeadPitch = renderHeadPitch;
        prevRenderYaw = renderYaw;

        if (isReturning()) {
            renderHeadPitch = mc.player != null ? mc.player.getXRot() : getClientPitch();
            if (mc.player != null) {
                renderYaw += Mth.wrapDegrees(mc.player.getYRot() - renderYaw);
                prevRenderBodyYaw = renderBodyYaw;
                renderBodyYaw += Mth.wrapDegrees(mc.player.yBodyRot - renderBodyYaw);
            } else {
                renderYaw = getClientYaw();
            }
            returnFramePrepared = true;
        } else {
            renderHeadPitch = getClientPitch();
            renderYaw = getClientYaw();
            updateVanillaRenderBodyYaw(renderYaw);
        }
    }

    public void resetRotationsInstantly() {
        isRotating = false;
        isReturning = false;

        if (mc.player != null) {
            updateRotations();
            clientYaw = mc.player.getYRot();
            clientPitch = mc.player.getXRot();
            prevClientYaw = clientYaw;
            prevClientPitch = clientPitch;
            prevRenderYaw = renderYaw = clientYaw;
            prevRenderHeadPitch = renderHeadPitch = clientPitch;
            prevRenderBodyYaw = renderBodyYaw = mc.player.yBodyRot;
        }

        returnFramePrepared = false;
    }

    public void stopRotation() {
        if (!ensureInitializedFromPlayer() || (!isRotating && !isReturning)) {
            return;
        }

        beginReturnToPlayerRotation();
    }

    public void setRotationsInstantly(float yaw, float pitch) {
        ensureInitializedFromPlayer();
        updateRotations();

        float[] fixedRotation = normalizeRotation(yaw, pitch, clientYaw, clientPitch);
        clientYaw = fixedRotation[0];
        clientPitch = fixedRotation[1];
    }

    public void syncWithServerRotation(float yaw, float pitch) {
        ensureInitializedFromPlayer();
        updateRotations();

        clientYaw = yaw;
        clientPitch = Mth.clamp(pitch, -90.0f, 90.0f);
        recordServerRotation(clientYaw, clientPitch);
        isRotating = false;
        isReturning = false;
        lastRotationUpdate = System.nanoTime();
    }

    public void startRotation(float yaw, float pitch, float speed) {
        rotateToward(yaw, pitch, speed, true);
    }

    public void startRotation(float yaw, float pitch, float yawSpeed, float pitchSpeed) {
        rotateToward(yaw, pitch, yawSpeed, pitchSpeed, true, true);
    }

    public void startRotationUnclampedPitch(float yaw, float pitch, float speed) {
        rotateToward(yaw, pitch, speed, true, false);
    }

    public void facePosition(double x, double y, double z, float rotationSpeed) {
        if (mc.player == null) {
            return;
        }

        float[] rotations = getRotationsTo(x, y, z);
        rotateToward(rotations[0], rotations[1], rotationSpeed, false);
    }

    public boolean faceEntity(Entity entity, float rotationSpeed) {
        return faceEntity(entity, rotationSpeed, false, true, getEntityReachDistance());
    }

    public boolean faceEntity(Entity entity, float rotationSpeed, boolean noRotationJitters, boolean reducedPitchRotation, double maxRange) {
        if (mc.player == null || entity == null) {
            return false;
        }

        final double xDif = entity.getX() - mc.player.getX();
        final double zDif = entity.getZ() - mc.player.getZ();
        final double toDegrees = 180.0D / StrictMath.PI;

        final AABB entityBB = entity.getBoundingBox().inflate(0.1D, 0.1D, 0.1D);
        final double playerEyePos = mc.player.getEyeY();
        final double yDif = playerEyePos > entityBB.maxY ? entityBB.maxY - playerEyePos :
                playerEyePos < entityBB.minY ? entityBB.minY - playerEyePos :
                        0.0D;

        final double fDist = Math.sqrt(xDif * xDif + zDif * zDif);

        float targetYaw = (float) (StrictMath.atan2(zDif, xDif) * toDegrees) - 90.0F;
        float targetPitch = (float) (-(StrictMath.atan2(yDif, fDist) * toDegrees));
        float currentPitch = getClientPitch();

        if (reducedPitchRotation) {
            rotateToward(targetYaw, currentPitch, rotationSpeed, noRotationJitters);

            if (canHitEntityAtRotation(entity, getClientYaw(), getClientPitch(), maxRange)) {
                return true;
            }
        }

        rotateToward(targetYaw, targetPitch, rotationSpeed, noRotationJitters);
        return canHitEntityAtRotation(entity, getClientYaw(), getClientPitch(), maxRange);
    }

    public boolean faceBlock(BlockPos pos, float rotationSpeed) {
        return faceBlock(pos, rotationSpeed, true, getBlockReachDistance());
    }

    public boolean faceBlock(BlockPos pos, float rotationSpeed, boolean noRotationJitters, double maxRange) {
        if (mc.player == null || pos == null) {
            return false;
        }

        float[] rotations = getRotationsTo(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        rotateToward(rotations[0], rotations[1], rotationSpeed, noRotationJitters);
        return canHitBlockAtRotationWithoutFacing(pos, getClientYaw(), getClientPitch(), maxRange);
    }

    public boolean faceBlockSurface(BlockPos pos, float speed) {
        Direction face = findBestSupportFace(pos);
        if (face == null) {
            return false;
        }

        return faceBlockWithFacing(pos, face, speed, false, true, false);
    }

    public boolean faceBlockWithFacingScaffold(BlockPos pos, Direction facing, float rotationSpeed, boolean noRotationJitters) {
        if (mc.player == null || pos == null || facing == null) {
            return false;
        }

        float bestYaw = getClientYaw();
        float bestPitch = getClientPitch();
        float bestDiff = Float.MAX_VALUE;
        Vec3 eyePos = mc.player.getEyePosition();

        int xCount = fixedOffsetCount(facing, Direction.EAST, Direction.WEST);
        int yCount = fixedOffsetCount(facing, Direction.UP, Direction.DOWN);
        int zCount = fixedOffsetCount(facing, Direction.SOUTH, Direction.NORTH);
        for (int xIndex = 0; xIndex < xCount; xIndex++) {
            double dx = placementOffset(facing, Direction.EAST, Direction.WEST, xIndex);
            for (int yIndex = 0; yIndex < yCount; yIndex++) {
                double dy = placementOffset(facing, Direction.UP, Direction.DOWN, yIndex);
                for (int zIndex = 0; zIndex < zCount; zIndex++) {
                    double dz = placementOffset(facing, Direction.SOUTH, Direction.NORTH, zIndex);
                    Vec3 hitVec = new Vec3(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    Vec3 diff = hitVec.subtract(eyePos);
                    double distance = diff.length();

                    if (distance > getBlockReachDistance()) {
                        continue;
                    }

                    double dist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
                    float calcYaw = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0F;
                    float calcPitch = (float) -Math.toDegrees(Math.atan2(diff.y, dist));

                    if (calcPitch < -90.0F || calcPitch > 90.0F) {
                        continue;
                    }

                    BlockHitResult hit = rayTraceBlocks(calcYaw, calcPitch, getBlockReachDistance());

                    if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos) && hit.getDirection() == facing) {
                        float yawDiff = Math.abs(Mth.wrapDegrees(calcYaw - getClientYaw()));
                        float pitchDiff = Math.abs(calcPitch - getClientPitch());
                        float totalDiff = yawDiff + pitchDiff;

                        if (totalDiff < bestDiff) {
                            bestYaw = calcYaw;
                            bestPitch = calcPitch;
                            bestDiff = totalDiff;
                        }
                    }
                }
            }
        }

        if (bestDiff != Float.MAX_VALUE) {
            rotateToward(bestYaw, bestPitch, rotationSpeed, noRotationJitters);
            return canHitBlockAtRotation(pos, facing, getClientYaw(), getClientPitch());
        }

        return false;
    }

    private static int fixedOffsetCount(Direction facing, Direction positive, Direction negative) {
        return facing == positive || facing == negative ? 1 : 16;
    }

    private static double placementOffset(Direction facing, Direction positive, Direction negative, int index) {
        if (facing == positive) {
            return 1.0D;
        }
        if (facing == negative) {
            return 0.0D;
        }
        return ((index << 1) + 1) * 0.03125D;
    }

    public boolean faceBlockWithFacing(BlockPos pos, Direction facing, float rotationSpeed, boolean simple, boolean noRotationJitters, boolean antiBackSprint) {
        if (mc.player == null || mc.level == null || pos == null || facing == null) {
            return false;
        }

        if (simple) {
            Vec3 eyePos = mc.player.getEyePosition();
            AABB boundingBox = getBlockBoundingBox(pos);
            double clampedX = Mth.clamp(eyePos.x, boundingBox.minX, boundingBox.maxX);
            double clampedY = Mth.clamp(eyePos.y, boundingBox.minY, boundingBox.maxY);
            double clampedZ = Mth.clamp(eyePos.z, boundingBox.minZ, boundingBox.maxZ);

            Vec3 hitVec = new Vec3(clampedX, clampedY, clampedZ);
            Vec3 diff = hitVec.subtract(eyePos);
            double diffXZ = Math.sqrt(diff.x * diff.x + diff.z * diff.z);

            float targetYaw = (float) (Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0F);
            float targetPitch = (float) -Math.toDegrees(Math.atan2(diff.y, diffXZ));

            targetYaw = Mth.wrapDegrees(targetYaw + (antiBackSprint ? 80.0f : 0.0f));
            targetPitch = Mth.wrapDegrees(targetPitch);

            rotateToward(targetYaw, targetPitch, rotationSpeed, noRotationJitters);
        } else {
            Vec3 target = faceCenter(pos, facing);
            Vec3 diff = target.subtract(mc.player.getEyePosition());
            double dist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);

            float targetYaw = (float) (Math.atan2(diff.z, diff.x) * 180.0D / Math.PI) - 90.0f;
            float targetPitch = (float) -(Math.atan2(diff.y, dist) * 180.0D / Math.PI);

            rotateToward(targetYaw, targetPitch, rotationSpeed, noRotationJitters);
        }

        return canHitBlockAtRotation(pos, facing, getClientYaw(), getClientPitch());
    }

    public float tellySwap(float yaw) {
        float snappedBase = Math.round(yaw / 45.0f) * 45.0f;

        float lowerOffset;
        float upperOffset;

        if (Math.abs(snappedBase % 90.0f) < 0.001f) {
            lowerOffset = 0.0f;
            upperOffset = 0.0f;
        } else {
            lowerOffset = 45.0f;
            upperOffset = 45.0f;
        }

        float lowerCandidate = snappedBase - lowerOffset;
        float upperCandidate = snappedBase + upperOffset;

        return Math.abs(yaw - lowerCandidate) <= Math.abs(upperCandidate - yaw) ? lowerCandidate : upperCandidate;
    }

    public void rotateToward(float targetYaw, float targetPitch, float rotationSpeed, boolean noRotationJitters) {
        rotateToward(targetYaw, targetPitch, rotationSpeed, noRotationJitters, true);
    }

    private void rotateToward(float targetYaw, float targetPitch, float rotationSpeed, boolean noRotationJitters, boolean clampPitch) {
        rotateToward(targetYaw, targetPitch, rotationSpeed, rotationSpeed, noRotationJitters, clampPitch);
    }

    private void rotateToward(float targetYaw, float targetPitch, float yawSpeed, float pitchSpeed, boolean noRotationJitters, boolean clampPitch) {
        if (!ensureInitializedFromPlayer()) {
            return;
        }

        float adjustedYawSpeed = Math.max(0.0f, yawSpeed);
        float adjustedPitchSpeed = Math.max(0.0f, pitchSpeed);
        float yawDiff = Mth.wrapDegrees(targetYaw - clientYaw);
        float pitchDiff = clampPitch ? Mth.clamp(targetPitch, -90.0f, 90.0f) - clientPitch : targetPitch - clientPitch;
        float yawStep = Mth.clamp(yawDiff, -adjustedYawSpeed, adjustedYawSpeed);
        float pitchStep = Mth.clamp(pitchDiff, -adjustedPitchSpeed, adjustedPitchSpeed);
        float[] fixedRotation = normalizeRotation(
                clientYaw + yawStep,
                clientPitch + pitchStep,
                clientYaw,
                clientPitch,
                clampPitch
        );

        updateRotations();

        clientYaw = fixedRotation[0];
        clientPitch = fixedRotation[1];
        lastRotationUpdate = System.nanoTime();
        isRotating = true;
        isReturning = false;
    }

    public boolean canAttackEntity(Entity entity) {
        return canHitEntityAtRotation(entity, getClientYaw(), getClientPitch(), getEntityReachDistance());
    }

    public boolean canHitEntityAtRotation(Entity target, float yaw, float pitch, double maxRange) {
        return canHitEntityAtRotation(target, yaw, pitch, maxRange, false);
    }

    public boolean canHitEntityAtRotation(Entity target, float yaw, float pitch, double maxRange, boolean ignoreBlocks) {
        if (mc.player == null || mc.level == null || target == null || !target.isPickable() || maxRange <= 0.0D || !Double.isFinite(maxRange)) {
            return false;
        }

        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 lookVec = getLookVecFromRotations(yaw, pitch);
        Vec3 reachVec = eyePos.add(lookVec.scale(maxRange));
        AABB box = target.getBoundingBox().inflate(Math.max(0.0D, target.getPickRadius()));
        if (box.contains(eyePos)) {
            return true;
        }

        Optional<Vec3> hit = box.clip(eyePos, reachVec);
        if (hit.isEmpty()) {
            return false;
        }

        double entityHitDistanceSqr = eyePos.distanceToSqr(hit.get());
        if (entityHitDistanceSqr - maxRange * maxRange > 1.0E-7D) {
            return false;
        }

        if (ignoreBlocks) {
            return true;
        }

        BlockHitResult blockHit = rayTraceBlocks(yaw, pitch, maxRange);
        if (blockHit.getType() != HitResult.Type.BLOCK) {
            return true;
        }

        double blockHitDistanceSqr = eyePos.distanceToSqr(blockHit.getLocation());
        return entityHitDistanceSqr <= blockHitDistanceSqr + 1.0E-7D;
    }

    public boolean canPlaceBlockAt(BlockPos pos) {
        return canHitBlockAtRotationWithoutFacing(pos, getClientYaw(), getClientPitch(), getBlockReachDistance());
    }

    public boolean canHitBlockAtRotation(BlockPos targetPos, Direction facing, float yaw, float pitch) {
        if (targetPos == null || facing == null) {
            return false;
        }

        BlockHitResult hit = rayTraceBlocks(yaw, pitch, getBlockReachDistance());
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(targetPos) && hit.getDirection() == facing;
    }

    public boolean canHitBlockAtRotationWithoutFacing(BlockPos targetPos, float yaw, float pitch, double maxRange) {
        if (targetPos == null) {
            return false;
        }

        BlockHitResult hit = rayTraceBlocks(yaw, pitch, maxRange);
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(targetPos);
    }

    public float[] getRotationsTo(double x, double y, double z) {
        if (mc.player == null) {
            return new float[]{getClientYaw(), getClientPitch()};
        }

        Vec3 eyePos = mc.player.getEyePosition();

        double diffX = x - eyePos.x;
        double diffY = y - eyePos.y;
        double diffZ = z - eyePos.z;
        double distXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float yaw = (float) (Math.atan2(diffZ, diffX) * (180.0D / Math.PI)) - 90.0F;
        float pitch = (float) -(Math.atan2(diffY, distXZ) * (180.0D / Math.PI));

        yaw = mc.player.getYRot() + Mth.wrapDegrees(yaw - mc.player.getYRot());
        pitch = mc.player.getXRot() + Mth.wrapDegrees(pitch - mc.player.getXRot());

        return new float[]{yaw, pitch};
    }

    public float[] getAngleDifferenceTo(Entity entity) {
        if (mc.player == null || entity == null) {
            return new float[]{0.0f, 0.0f};
        }

        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 target = entity.getBoundingBox().getCenter();

        double diffX = target.x - eyePos.x;
        double diffY = target.y - eyePos.y;
        double diffZ = target.z - eyePos.z;
        double distXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float targetYaw = (float) (Math.atan2(diffZ, diffX) * (180.0D / Math.PI)) - 90.0F;
        float targetPitch = (float) -(Math.atan2(diffY, distXZ) * (180.0D / Math.PI));

        float yawDiff = Mth.wrapDegrees(targetYaw - mc.player.getYRot());
        float pitchDiff = Mth.wrapDegrees(targetPitch - mc.player.getXRot());

        return new float[]{Math.abs(yawDiff), Math.abs(pitchDiff)};
    }

    public float getInterpolatedYaw(float partialTicks) {
        return Mth.rotLerp(partialTicks, prevRenderYaw, renderYaw);
    }

    public float getInterpolatedBodyYaw(float partialTicks) {
        return Mth.rotLerp(partialTicks, prevRenderBodyYaw, renderBodyYaw);
    }

    public float getInterpolatedPitch(float partialTicks) {
        return Mth.lerp(partialTicks, prevRenderHeadPitch, renderHeadPitch);
    }

    public float getInterpolatedReturningBodyYaw(float partialTicks) {
        if (!isReturning() || !returnFramePrepared) {
            return getInterpolatedYaw(partialTicks);
        }

        return Mth.rotLerp(partialTicks, prevRenderYaw, renderBodyYaw);
    }

    public boolean isRotating() {
        return isRotating || isReturning;
    }

    public boolean isReturning() {
        return !isRotating && isReturning;
    }

    public float getRotationYaw() {
        return getClientYaw();
    }

    public float getRotationPitch() {
        return getClientPitch();
    }

    public float getClientYaw() {
        ensureInitializedFromPlayer();
        return clientYaw;
    }

    public float getClientPitch() {
        ensureInitializedFromPlayer();
        return clientPitch;
    }

    public float getPrevClientYaw() {
        return prevClientYaw;
    }

    public float getPrevClientPitch() {
        return prevClientPitch;
    }

    public float[] normalizeRotation(float yaw, float pitch, float currentYaw, float currentPitch) {
        return normalizeRotation(yaw, pitch, currentYaw, currentPitch, true);
    }

    public float[] normalizeRotation(float yaw, float pitch, float currentYaw, float currentPitch, boolean clampPitch) {
        double multiplier = getMouseSensitivityMultiplier();
        float targetPitch = clampPitch ? Mth.clamp(pitch, -90.0f, 90.0f) : pitch;
        if (multiplier <= 0.0D || !Double.isFinite(multiplier)) {
            return new float[]{yaw, targetPitch};
        }

        float yawDiff = Mth.wrapDegrees(yaw - currentYaw);
        float pitchDiff = targetPitch - currentPitch;
        float fixedYaw = currentYaw + mouseDegreesFromDelta(yawDiff, multiplier);
        float fixedPitch = currentPitch + mouseDegreesFromDelta(pitchDiff, multiplier);

        if (clampPitch) {
            fixedPitch = Mth.clamp(fixedPitch, -90.0f, 90.0f);
        }

        return new float[]{fixedYaw, fixedPitch};
    }

    public Packet<?> normalizeOutgoingPacket(Packet<?> packet) {
        if (!ensureServerRotationInitialized()) {
            return packet;
        }

        if (packet instanceof ServerboundMovePlayerPacket movePacket && movePacket.hasRotation()) {
            float[] fixedRotation = normalizeRotation(
                    movePacket.getYRot(serverYaw),
                    movePacket.getXRot(serverPitch),
                    serverYaw,
                    serverPitch
            );

            if (movePacket.hasPosition()) {
                return new ServerboundMovePlayerPacket.PosRot(
                        movePacket.getX(0.0D),
                        movePacket.getY(0.0D),
                        movePacket.getZ(0.0D),
                        fixedRotation[0],
                        fixedRotation[1],
                        movePacket.isOnGround(),
                        movePacket.horizontalCollision()
                );
            }

            return new ServerboundMovePlayerPacket.Rot(
                    fixedRotation[0],
                    fixedRotation[1],
                    movePacket.isOnGround(),
                    movePacket.horizontalCollision()
            );
        }

        if (packet instanceof ServerboundUseItemPacket useItemPacket) {
            float[] fixedRotation = normalizeRotation(
                    useItemPacket.getYRot(),
                    useItemPacket.getXRot(),
                    serverYaw,
                    serverPitch
            );
            return new ServerboundUseItemPacket(
                    useItemPacket.getHand(),
                    useItemPacket.getSequence(),
                    fixedRotation[0],
                    fixedRotation[1]
            );
        }

        return packet;
    }

    public void recordOutgoingRotation(Packet<?> packet) {
        if (packet instanceof ServerboundMovePlayerPacket movePacket && movePacket.hasRotation()) {
            ensureServerRotationInitialized();
            recordServerRotation(movePacket.getYRot(serverYaw), movePacket.getXRot(serverPitch));
            return;
        }

        if (packet instanceof ServerboundUseItemPacket useItemPacket) {
            recordServerRotation(useItemPacket.getYRot(), useItemPacket.getXRot());
        }
    }

    private boolean ensureInitializedFromPlayer() {
        if (initialized) {
            return true;
        }

        if (mc.player == null) {
            return false;
        }

        syncWithPlayerRotation();
        return true;
    }

    private void syncWithPlayerRotation() {
        clientYaw = mc.player.getYRot();
        clientPitch = mc.player.getXRot();
        prevClientYaw = clientYaw;
        prevClientPitch = clientPitch;
        if (!serverRotationInitialized) {
            serverYaw = clientYaw;
            serverPitch = clientPitch;
            serverRotationInitialized = true;
        }
        renderYaw = clientYaw;
        prevRenderYaw = clientYaw;
        renderBodyYaw = mc.player.yBodyRot;
        prevRenderBodyYaw = renderBodyYaw;
        renderHeadPitch = clientPitch;
        prevRenderHeadPitch = clientPitch;
        returnFramePrepared = false;
        initialized = true;
    }

    private void beginReturnToPlayerRotation() {
        isRotating = false;
        if (isReturning) {
            return;
        }

        isReturning = true;
        returnFramePrepared = false;
    }

    private void updateVanillaRenderBodyYaw(float headYaw) {
        prevRenderBodyYaw = renderBodyYaw;
        if (mc.player == null) {
            renderBodyYaw = headYaw;
            return;
        }

        float bodyTarget = renderBodyYaw;
        double deltaX = mc.player.getX() - mc.player.xo;
        double deltaZ = mc.player.getZ() - mc.player.zo;
        float horizontalDistanceSqr = (float) (deltaX * deltaX + deltaZ * deltaZ);

        if (horizontalDistanceSqr > 0.0025000002F) {
            bodyTarget = (float) (Mth.atan2(deltaZ, deltaX) * (180.0D / Math.PI)) - 90.0F;
            float movementYawDiff = Math.abs(Mth.wrapDegrees(headYaw - bodyTarget));
            if (movementYawDiff >= 95.0F && movementYawDiff < 265.0F)
                bodyTarget -= 180.0F;
        }

        if (mc.player.attackAnim > 0.0F)
            bodyTarget = headYaw;

        renderBodyYaw += Mth.wrapDegrees(bodyTarget - renderBodyYaw) * 0.3F;

        float headBodyDiff = Mth.wrapDegrees(headYaw - renderBodyYaw);
        float maxHeadRotation = 50.0F;
        if (Math.abs(headBodyDiff) > maxHeadRotation)
            renderBodyYaw += headBodyDiff - Math.signum(headBodyDiff) * maxHeadRotation;

        fixRenderBodyYawInterpolation();
    }

    private void fixRenderBodyYawInterpolation() {
        while (renderBodyYaw - prevRenderBodyYaw < -180.0F)
            prevRenderBodyYaw -= 360.0F;

        while (renderBodyYaw - prevRenderBodyYaw >= 180.0F)
            prevRenderBodyYaw += 360.0F;
    }

    private boolean ensureServerRotationInitialized() {
        if (serverRotationInitialized) {
            return true;
        }

        if (mc.player == null) {
            return false;
        }

        serverYaw = mc.player.getYRot();
        serverPitch = mc.player.getXRot();
        serverRotationInitialized = true;
        return true;
    }

    private void recordServerRotation(float yaw, float pitch) {
        serverYaw = yaw;
        serverPitch = Mth.clamp(pitch, -90.0f, 90.0f);
        serverRotationInitialized = true;
    }

    private long elapsedRotationMillis() {
        if (lastRotationUpdate == 0L)
            return Long.MAX_VALUE;

        return (System.nanoTime() - lastRotationUpdate) / 1_000_000L;
    }

    private void updateRotations() {
        prevClientYaw = clientYaw;
        prevClientPitch = clientPitch;
    }

    private double getMouseSensitivityMultiplier() {
        double sensitivity = mc.options.sensitivity().get();
        double f = sensitivity * 0.6F + 0.2F;
        double multiplier = f * f * f;

        if (mc.player != null && mc.options.getCameraType().isFirstPerson() && mc.player.isScoping()) {
            return multiplier;
        }

        return multiplier * 8.0D;
    }

    private float mouseDegreesFromDelta(float delta, double multiplier) {
        if (!Float.isFinite(delta)) {
            return 0.0f;
        }

        long mouseCounts = Math.round(delta / 0.15F / multiplier);
        return (float) (mouseCounts * multiplier) * 0.15F;
    }

    private Vec3 getLookVecFromRotations(float yaw, float pitch) {
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);

        float x = -Mth.cos(pitchRad) * Mth.sin(yawRad);
        float y = -Mth.sin(pitchRad);
        float z = Mth.cos(pitchRad) * Mth.cos(yawRad);

        return new Vec3(x, y, z);
    }

    public BlockHitResult rayTraceBlocks(float yaw, float pitch, double reach) {
        if (mc.player == null || mc.level == null) {
            return BlockHitResult.miss(Vec3.ZERO, Direction.DOWN, BlockPos.ZERO);
        }

        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 lookVec = getLookVecFromRotations(yaw, pitch);
        Vec3 targetPos = eyePos.add(lookVec.scale(reach));

        return mc.level.clip(new ClipContext(
                eyePos,
                targetPos,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                mc.player
        ));
    }

    private Direction findBestSupportFace(BlockPos pos) {
        if (mc.player == null || mc.level == null || pos == null) {
            return null;
        }

        BlockPos playerBlockPos = mc.player.blockPosition();
        Vec3 playerCenter = Vec3.atCenterOf(playerBlockPos);
        Direction bestFace = null;
        double closestDistance = Double.MAX_VALUE;

        double north = closerSupportFaceDistance(pos, playerCenter, Direction.NORTH, closestDistance);
        if (!Double.isNaN(north)) {
            closestDistance = north;
            bestFace = Direction.NORTH;
        }
        double south = closerSupportFaceDistance(pos, playerCenter, Direction.SOUTH, closestDistance);
        if (!Double.isNaN(south)) {
            closestDistance = south;
            bestFace = Direction.SOUTH;
        }
        double west = closerSupportFaceDistance(pos, playerCenter, Direction.WEST, closestDistance);
        if (!Double.isNaN(west)) {
            closestDistance = west;
            bestFace = Direction.WEST;
        }
        double east = closerSupportFaceDistance(pos, playerCenter, Direction.EAST, closestDistance);
        if (!Double.isNaN(east)) {
            closestDistance = east;
            bestFace = Direction.EAST;
        }

        if (bestFace != null) {
            return bestFace;
        }

        double down = closerSupportFaceDistance(pos, playerCenter, Direction.DOWN, closestDistance);
        if (!Double.isNaN(down)) {
            closestDistance = down;
            bestFace = Direction.DOWN;
        }
        double up = closerSupportFaceDistance(pos, playerCenter, Direction.UP, closestDistance);
        if (!Double.isNaN(up))
            bestFace = Direction.UP;

        return bestFace;
    }

    private double closerSupportFaceDistance(BlockPos pos, Vec3 playerCenter, Direction direction, double closestDistance) {
        BlockPos neighbor = pos.relative(direction);
        BlockState neighborState = mc.level.getBlockState(neighbor);

        if (!isValidSupport(neighborState))
            return Double.NaN;

        Vec3 faceCenter = faceCenter(pos, direction);
        double distance = playerCenter.distanceTo(faceCenter);
        return distance < closestDistance ? distance : Double.NaN;
    }

    private boolean isValidSupport(BlockState state) {
        return state != null && !state.isAir() && state.getBlock() != Blocks.BARRIER;
    }

    private Vec3 faceCenter(BlockPos pos, Direction facing) {
        return Vec3.atCenterOf(pos).add(
                facing.getStepX() * 0.5D,
                facing.getStepY() * 0.5D,
                facing.getStepZ() * 0.5D
        );
    }

    private AABB getBlockBoundingBox(BlockPos pos) {
        if (mc.level == null) {
            return new AABB(pos);
        }

        BlockState blockState = mc.level.getBlockState(pos);
        VoxelShape collisionShape = blockState.getCollisionShape(mc.level, pos);
        if (collisionShape.isEmpty()) {
            return new AABB(pos);
        }

        AABB boundingBox = collisionShape.bounds().move(pos);
        if (boundingBox.getSize() <= 0.0D) {
            return new AABB(pos);
        }

        return boundingBox;
    }

    private double getBlockReachDistance() {
        if (mc.player == null) {
            return 4.5D;
        }

        return mc.player.blockInteractionRange();
    }

    private double getEntityReachDistance() {
        if (mc.player == null) {
            return 3.0D;
        }

        return mc.player.entityInteractionRange();
    }
}
