package com.instrumentalist.krs.hacks.features.combat;



import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.HandleInputEvent;
import com.instrumentalist.krs.events.features.SendPacketEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.movement.NoSlow;
import com.instrumentalist.krs.utils.entity.EntityExtension;
import com.instrumentalist.krs.utils.entity.PlayerUtil;
import com.instrumentalist.krs.utils.math.BehaviorUtils;
import com.instrumentalist.krs.utils.math.RandomUtil;
import com.instrumentalist.krs.utils.math.ToolUtil;
import com.instrumentalist.krs.utils.packet.BlinkUtil;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import com.instrumentalist.krs.utils.pathfinder.MainPathFinder;
import com.instrumentalist.krs.utils.value.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class KillAura extends Module {
    private static final double[] SMART_ROTATION_OFFSETS = {
            0.0D, 0.03125D, 0.0625D, 0.09375D,
            0.125D, 0.15625D, 0.1875D, 0.21875D,
            0.25D, 0.28125D, 0.3125D, 0.34375D,
            0.375D, 0.40625D, 0.4375D, 0.46875D,
            0.5D, 0.53125D, 0.5625D, 0.59375D,
            0.625D, 0.65625D, 0.6875D, 0.71875D,
            0.75D, 0.78125D, 0.8125D, 0.84375D,
            0.875D, 0.90625D, 0.9375D, 0.96875D,
            1.0D
    };

    public KillAura() {
        super("Kill Aura", ModuleCategory.Combat, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    @Setting
    private final ListValue targetMode = new ListValue(
            "Target Mode",
            new String[]{"Single", "Multi"},
            "Single"
    );

    @Setting
    private final IntValue maxTargets = new IntValue(
            "Max Targets",
            3,
            2,
            10,
            () -> targetMode.get().equalsIgnoreCase("multi")
    );

    @Setting
    private final FloatValue targetRange = new FloatValue(
            "Target Range",
            4f,
            0f,
            8f
    );

    @Setting
    public final FloatValue attackRange = new FloatValue(
            "Attack Range",
            3f,
            0f,
            6f
    );

    @Setting
    private final ListValue apsMode = new ListValue(
            "APS Mode",
            new String[]{"Cooldown", "Randomized CPS", "Hurt Math", "No Delay"},
            "Cooldown"
    );

    @Setting
    private final BooleanValue quickCooldown = new BooleanValue(
            "Quick Cooldown",
            true,
            () -> apsMode.get().equalsIgnoreCase("cooldown")
    );

    @Setting
    private final IntValue maxCps = new IntValue(
            "Max CPS",
            12,
            1,
            20,
            () -> apsMode.get().equalsIgnoreCase("randomized cps")
    );

    @Setting
    private final IntValue minCps = new IntValue(
            "Min CPS",
            6,
            1,
            20,
            () -> apsMode.get().equalsIgnoreCase("randomized cps")
    );

    @Setting
    private final ListValue swingOrderMode = new ListValue(
            "Swing Order Mode",
            new String[]{"1.8", "1.9.x", "No Order"},
            "1.9.x"
    );

    @Setting
    public static final ListValue autoBlockMode = new ListValue(
            "Auto Block Mode",
            new String[]{"Vanilla", "Blink1", "Blink2", "Hypixel NCP", "Swap", "Legit", "Packet", "Interact1", "Interact2", "Basic", "Spoof", "Delayed", "Post Attack", "No Order"},
            "No Order"
    );

    @Setting
    private final FloatValue autoBlockCps = new FloatValue(
            "Auto Block CPS",
            8f,
            1f,
            10f,
            () -> isAutoBlockMode(autoBlockMode.get())
    );

    @Setting
    private final FloatValue autoBlockRange = new FloatValue(
            "Auto Block Range",
            6f,
            3f,
            8f,
            () -> isAutoBlockMode(autoBlockMode.get())
    );

    @Setting
    public static final BooleanValue rotations = new BooleanValue(
            "Rotations",
            true
    );

    @Setting
    private final FloatValue maxRotationSpeed = new FloatValue(
            "Max Rotation Speed",
            60f,
            1f,
            180f,
            rotations::get
    );

    @Setting
    private final FloatValue minRotationSpeed = new FloatValue(
            "Min Rotation Speed",
            40f,
            1f,
            180f,
            rotations::get
    );

    @Setting
    private final BooleanValue noHitCheck = new BooleanValue(
            "No Hit Check",
            false,
            rotations::get
    );

    @Setting
    private final BooleanValue throughWalls = new BooleanValue(
            "Through Walls",
            true
    );

    @Setting
    private final BooleanValue onlyPlayers = new BooleanValue(
            "Only Players",
            false
    );

    @Setting
    private final BooleanValue visualSwing = new BooleanValue(
            "Visual Swing",
            false
    );

    @Setting
    public static final BooleanValue tpReach = new BooleanValue(
            "TP Reach",
            false
    );

    @Setting
    private final FloatValue tpExtendedReach = new FloatValue(
            "TP Extended Reach",
            40f,
            0f,
            200f,
            tpReach::get
    );

    @Setting
    private final IntValue tpTicks = new IntValue(
            "TP Ticks",
            0,
            0,
            60,
            "tick",
            tpReach::get
    );

    @Setting
    private final BooleanValue tpBack = new BooleanValue(
            "TP Back",
            true,
            tpReach::get
    );

    @Setting
    private final BooleanValue tpOnGroundPacket = new BooleanValue(
            "TP OnGround Packet",
            true,
            tpReach::get
    );

    @Setting
    private final TextValue preferTargetId = new TextValue("Prefer Target ID", "1zun4");

    public static Entity closestEntity = null;
    public static final List<Entity> multiTargets = new ArrayList<>();
    private final ArrayList<TargetCandidate> targetCandidateBuffer = new ArrayList<>();
    private final ArrayList<TargetCandidate> targetCandidatePool = new ArrayList<>();
    private int targetCandidatePoolCursor;
    private PendingTeleportSequence pendingTeleportSequence;
    private boolean sendingTeleportSequencePacket;

    private long lastAttackTime = 0;
    private long lastOpenBlockTime = 0;
    private int randomDelay = 0;
    private int abTick = 0;
    private int blockTick = 0;
    private boolean wasBlinking = false;
    private boolean wasPacketBlocking = false;
    private boolean wasBlocking = false;
    public static boolean isBlocking = false;

    private static final class TargetCandidate {
        private Entity entity;
        private double squaredDistance;
        private boolean preferred;

        private TargetCandidate set(Entity entity, double squaredDistance, boolean preferred) {
            this.entity = entity;
            this.squaredDistance = squaredDistance;
            this.preferred = preferred;
            return this;
        }

        private Entity entity() {
            return entity;
        }

        private double squaredDistance() {
            return squaredDistance;
        }

        private boolean preferred() {
            return preferred;
        }
    }

    private record SmartRotationTarget(float yaw, float pitch) {
    }

    private interface TeleportStep {
    }

    private record TeleportMoveStep(Vec3 position) implements TeleportStep {
    }

    private record TeleportAttackStep(Entity target, Vec3 clientPosition) implements TeleportStep {
    }

    private static final class PendingTeleportSequence {
        private final ArrayList<TeleportStep> steps;
        private final ArrayList<Vec3> sentPositions = new ArrayList<>();
        private final int totalMovePackets;
        private final int totalTicks;
        private final String swingMode;
        private final String autoBlockMode;
        private final float yaw;
        private final float pitch;
        private final Entity blockTarget;
        private final boolean canAutoBlock;
        private final boolean onGround;
        private final boolean horizontalCollision;
        private int nextStep;
        private int sentMovePackets;
        private int elapsedTicks;

        private PendingTeleportSequence(ArrayList<TeleportStep> steps, int totalMovePackets, int totalTicks,
                                        String swingMode, String autoBlockMode, float yaw, float pitch,
                                        Entity blockTarget, boolean canAutoBlock, boolean onGround,
                                        boolean horizontalCollision) {
            this.steps = steps;
            this.totalMovePackets = totalMovePackets;
            this.totalTicks = totalTicks;
            this.swingMode = swingMode;
            this.autoBlockMode = autoBlockMode;
            this.yaw = yaw;
            this.pitch = pitch;
            this.blockTarget = blockTarget;
            this.canAutoBlock = canAutoBlock;
            this.onGround = onGround;
            this.horizontalCollision = horizontalCollision;
        }
    }

    private float getRealTargetReach() {
        float reach = targetRange.get();

        if (isAutoBlockMode(autoBlockMode.get()))
            reach = Math.max(reach, autoBlockRange.get());

        if (mc.player.isCreative() && reach <= 4.5f)
            reach += 1.5f;

        if (tpReach.get())
            reach += tpExtendedReach.get();

        return reach;
    }

    private float getRealAttackReach() {
        float reach = attackRange.get();

        if (mc.player.isCreative() && reach <= 4.5f)
            reach += 1.5f;

        if (tpReach.get())
            reach += tpExtendedReach.get();

        return reach;
    }

    private float getRotationSpeed() {
        float min = minRotationSpeed.get();
        float max = maxRotationSpeed.get();
        return RandomUtil.nextFloat(Math.min(min, max), Math.max(min, max));
    }

    @Override
    public String tag() {
        if (apsMode.get().equalsIgnoreCase("randomized cps"))
            return minCps.get() + "-" + maxCps.get();
        else return apsMode.get();
    }

    @Override
    public void onDisable() {
        cancelPendingTeleportSequence();

        if (mc.player != null)
            resetPacketUnblocking();

        wasPacketBlocking = false;

        reset();
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
        if (pendingTeleportSequence != null
                && !sendingTeleportSequencePacket
                && event.packet instanceof ServerboundMovePlayerPacket)
            event.cancel();
    }

    @Override
    public void onHandleInput(HandleInputEvent event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            pendingTeleportSequence = null;
            sendingTeleportSequencePacket = false;
            return;
        }

        if (mc.gameMode.getPlayerMode() == GameType.SPECTATOR) {
            reset();
            return;
        }

        if (BehaviorUtils.noKillAura || mc.player.isSpectator()) {
            reset();
            return;
        }

        if (pendingTeleportSequence != null) {
            if (!tpReach.get())
                reset();
            else
                advancePendingTeleportSequence();
            return;
        }

        float realTargetReach = getRealTargetReach();
        float realAttackReach = getRealAttackReach();
        double realTargetReachSquared = square(realTargetReach);
        double realAttackReachSquared = square(realAttackReach);
        double baseAttackReachSquared = square(attackRange.get());
        boolean multiMode = targetMode.get().equalsIgnoreCase("multi");
        boolean canAttackThroughWalls = throughWalls.get();
        String preferredTarget = preferTargetId.get();
        int multiTargetLimit = multiMode ? maxTargets.get() : 0;
        ArrayList<TargetCandidate> candidates = multiMode ? targetCandidateBuffer : null;
        if (candidates != null)
            candidates.clear();
        targetCandidatePoolCursor = 0;
        TargetCandidate closest = null;
        boolean antiBotEnabled = ModuleManager.getModuleState(AntiBot.class);
        boolean teamsEnabled = ModuleManager.getModuleState(Teams.class);

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity livingEntity)
                    || livingEntity.isDeadOrDying()
                    || entity instanceof ArmorStand
                    || entity instanceof LocalPlayer
                    || BehaviorUtils.isBot(livingEntity, antiBotEnabled)
                    || BehaviorUtils.isTeammate(livingEntity, teamsEnabled)
                    || onlyPlayers.get() && !(entity instanceof Player)) continue;

            double squaredDistance = EntityExtension.squaredBoxedDistanceTo(entity, mc.player);
            if (squaredDistance > realTargetReachSquared) continue;

            TargetCandidate candidate = acquireTargetCandidate(
                    entity,
                    squaredDistance,
                    entity.getName().getString().equalsIgnoreCase(preferredTarget)
            );

            if (isBetterTarget(candidate, closest))
                closest = candidate;
            if (multiMode)
                insertBoundedTarget(candidates, candidate, multiTargetLimit);
        }

        if (closest == null) {
            if (autoBlockMode.get().equalsIgnoreCase("vanilla"))
                resetPacketUnblocking();

            reset();
            return;
        }

        closestEntity = closest.entity();

        if (multiMode) {
            multiTargets.clear();
            int targetLimit = candidates.size();
            for (int i = 0; i < targetLimit; i++) {
                TargetCandidate candidate = candidates.get(i);
                Entity target = candidate.entity();
                if (candidate.squaredDistance() <= realAttackReachSquared && (canAttackThroughWalls || mc.player.hasLineOfSight(target)))
                    multiTargets.add(target);
            }
        } else {
            multiTargets.clear();
        }

        boolean canHitTargetAtRotation = false;
        if (rotations.get())
            canHitTargetAtRotation = faceSmartRotation(closestEntity, getRotationSpeed(), realTargetReach, canAttackThroughWalls);

        String currentAutoBlockMode = autoBlockMode.get().toLowerCase(Locale.ROOT);
        boolean autoBlock = isAutoBlockMode(currentAutoBlockMode);
        float yaw = rotations.get() ? Client.rotationManager.getRotationYaw() : mc.player.getYRot();
        float pitch = rotations.get() ? Client.rotationManager.getRotationPitch() : mc.player.getXRot();
        boolean hasTargetVisibility = mc.player.hasLineOfSight(closestEntity)
                || canHitTargetAtRotation
                || Client.rotationManager.canHitEntityAtRotation(closestEntity, yaw, pitch, realTargetReach, false);
        boolean canAutoBlock = canAutoBlock(currentAutoBlockMode, closest.squaredDistance(), canAttackThroughWalls || hasTargetVisibility);

        if (!autoBlock && wasBlocking)
            resetAutoBlock();

        if (autoBlock) {
            isBlocking = canAutoBlock || wasBlocking;

            if (!canAutoBlock) {
                resetAutoBlock();
            } else if (wasBlocking) {
                if (shouldFinishAutoBlock(currentAutoBlockMode)) {
                    finishAutoBlock(currentAutoBlockMode, yaw, pitch);
                } else {
                    blockTick++;
                }
                return;
            }
        } else {
            isBlocking = true;
        }

        boolean canHitVisibleAtRotation = Client.rotationManager.canHitEntityAtRotation(
                closestEntity,
                yaw,
                pitch,
                realAttackReach,
                false
        );
        boolean canAttackTarget = closest.squaredDistance() <= realAttackReachSquared
                && (canAttackThroughWalls || hasTargetVisibility || canHitVisibleAtRotation);

        if (!canAttackTarget) {
            switch (currentAutoBlockMode) {
                case "vanilla":
                    resetPacketUnblocking();
                    break;

                case "blink1":
                    if (wasBlinking) {
                        BlinkUtil.INSTANCE.sync(true, true);
                        BlinkUtil.INSTANCE.stopBlink();
                        wasBlinking = false;
                    }
                    break;

                case "hypixel ncp":
                case "blink2":
                case "swap":
                case "legit":
                case "packet":
                case "interact1":
                case "basic":
                case "interact2":
                case "spoof":
                case "delayed":
                    if (canAutoBlock)
                        startAutoBlock(currentAutoBlockMode, yaw, pitch, closestEntity, false);
                    break;
            }

            return;
        }

        boolean attackReady = attackCooldownMath(closestEntity);
        boolean canHitAtRotation = !rotations.get() || noHitCheck.get() || tpReach.get();
        if (!canHitAtRotation) {
            canHitAtRotation = canAttackThroughWalls
                    ? Client.rotationManager.canHitEntityAtRotation(closestEntity, yaw, pitch, realAttackReach, true)
                    : canHitVisibleAtRotation;
        }

        if (autoBlock && canAutoBlock && (!attackReady || !canHitAtRotation)) {
            startAutoBlock(currentAutoBlockMode, yaw, pitch, closestEntity, false);
            return;
        }

        if (canHitAtRotation) {
            if (autoBlock && canAutoBlock && !canStartAutoBlockCycle())
                return;

            if (attackReady) {
                abTick++;

                switch (currentAutoBlockMode) {
                    case "vanilla":
                        if (mc.player.getOffhandItem().getItem() instanceof ShieldItem)
                            PacketUtil.sendPacket(new ServerboundUseItemPacket(InteractionHand.OFF_HAND, 0, yaw, pitch));
                        else if (ToolUtil.INSTANCE.isSword(mc.player.getMainHandItem()) && !wasPacketBlocking)
                            PacketUtil.sendPacket(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 0, yaw, pitch));
                        break;

                    case "blink1":
                        if (ToolUtil.INSTANCE.isSword(mc.player.getMainHandItem()) && abTick >= 1) {
                            BlinkUtil.INSTANCE.doBlink();
                            PacketUtil.sendPacket(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 0, yaw, pitch));
                            wasBlinking = true;
                        }
                        break;
                }

                ArrayList<Entity> attackTargets = new ArrayList<>(multiMode ? multiTargets.size() : 1);
                switch (targetMode.get().toLowerCase(Locale.ROOT)) {
                    case "single":
                        attackTargets.add(closestEntity);
                        break;

                    case "multi":
                        attackTargets.addAll(multiTargets);
                        break;
                }

                if (startSpreadTeleportSequence(
                        attackTargets,
                        baseAttackReachSquared,
                        currentAutoBlockMode,
                        yaw,
                        pitch,
                        closestEntity,
                        canAutoBlock
                )) return;

                for (Entity target : attackTargets) {
                    double squaredDistance = EntityExtension.squaredBoxedDistanceTo(target, mc.player);
                    attackToEntity(target, tpReach.get() && squaredDistance >= baseAttackReachSquared);
                }

                finishAttackCycle(currentAutoBlockMode, yaw, pitch, closestEntity, canAutoBlock);
            } else if (visualSwing.get()) {
                PlayerUtil.INSTANCE.swingHandWithoutPacket(InteractionHand.MAIN_HAND);
                renderParticles(closestEntity);
            }
        }
    }

    private void finishAttackCycle(String autoBlockMode, float yaw, float pitch, Entity blockTarget, boolean canAutoBlock) {
        switch (autoBlockMode) {
            case "vanilla":
                if (mc.player.getOffhandItem().getItem() instanceof ShieldItem)
                    PacketUtil.sendPacket(new ServerboundUseItemPacket(InteractionHand.OFF_HAND, 0, yaw, pitch));
                else if (ToolUtil.INSTANCE.isSword(mc.player.getMainHandItem()) && wasPacketBlocking)
                    PacketUtil.sendPacket(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 0, yaw, pitch));
                wasPacketBlocking = true;
                break;

            case "blink1":
                if (ToolUtil.INSTANCE.isSword(mc.player.getMainHandItem()) && abTick >= 1) {
                    BlinkUtil.INSTANCE.sync(true, true);
                    PacketUtil.sendPacket(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 0, yaw, pitch));
                    BlinkUtil.INSTANCE.stopBlink();
                    abTick = 0;
                }
                break;

            case "hypixel ncp":
            case "blink2":
            case "swap":
            case "legit":
            case "packet":
            case "interact1":
            case "basic":
            case "interact2":
            case "spoof":
            case "delayed":
            case "post attack":
                if (canAutoBlock)
                    startAutoBlock(autoBlockMode, yaw, pitch, blockTarget, true);
                break;
        }
    }

    private static int compareTargets(TargetCandidate first, TargetCandidate second) {
        if (first.preferred() != second.preferred())
            return first.preferred() ? -1 : 1;

        return Double.compare(first.squaredDistance(), second.squaredDistance());
    }

    public static double square(double value) {
        return value * value;
    }

    private static boolean isBetterTarget(TargetCandidate candidate, TargetCandidate currentBest) {
        return currentBest == null || compareTargets(candidate, currentBest) < 0;
    }

    private TargetCandidate acquireTargetCandidate(Entity entity, double squaredDistance, boolean preferred) {
        while (targetCandidatePool.size() <= targetCandidatePoolCursor)
            targetCandidatePool.add(new TargetCandidate());

        return targetCandidatePool.get(targetCandidatePoolCursor++).set(entity, squaredDistance, preferred);
    }

    private static void insertBoundedTarget(ArrayList<TargetCandidate> candidates, TargetCandidate candidate, int limit) {
        if (limit <= 0)
            return;

        int insertIndex = 0;
        int size = candidates.size();
        while (insertIndex < size && compareTargets(candidates.get(insertIndex), candidate) <= 0) {
            insertIndex++;
        }

        if (insertIndex >= limit)
            return;

        candidates.add(insertIndex, candidate);
        if (candidates.size() > limit)
            candidates.remove(candidates.size() - 1);
    }

    private boolean faceSmartRotation(Entity target, float rotationSpeed, double maxRange, boolean ignoreBlocks) {
        SmartRotationTarget rotationTarget = findSmartRotationTarget(target, maxRange, ignoreBlocks);
        if (rotationTarget == null) {
            Client.rotationManager.faceEntity(target, rotationSpeed, false, false, maxRange);
            return Client.rotationManager.canHitEntityAtRotation(
                    target,
                    Client.rotationManager.getRotationYaw(),
                    Client.rotationManager.getRotationPitch(),
                    maxRange,
                    ignoreBlocks
            );
        }

        Client.rotationManager.startRotation(rotationTarget.yaw(), rotationTarget.pitch(), rotationSpeed);
        return Client.rotationManager.canHitEntityAtRotation(
                target,
                Client.rotationManager.getRotationYaw(),
                Client.rotationManager.getRotationPitch(),
                maxRange,
                ignoreBlocks
        );
    }

    private SmartRotationTarget findSmartRotationTarget(Entity target, double maxRange, boolean ignoreBlocks) {
        var player = mc.player;
        if (player == null || target == null)
            return null;

        float currentYaw = Client.rotationManager.getRotationYaw();
        float currentPitch = Client.rotationManager.getRotationPitch();
        Vec3 eyesPos = player.getEyePosition();
        double eyeX = eyesPos.x;
        double eyeY = eyesPos.y;
        double eyeZ = eyesPos.z;
        AABB box = target.getBoundingBox().inflate(0.1D, 0.1D, 0.1D);
        double centerX = (box.minX + box.maxX) * 0.5D;
        double centerY = (box.minY + box.maxY) * 0.5D;
        double centerZ = (box.minZ + box.maxZ) * 0.5D;
        float centerYaw = rotationYawTo(centerX, centerZ, eyeX, eyeZ);
        float centerPitch = rotationPitchTo(centerX, centerY, centerZ, eyeX, eyeY, eyeZ);
        boolean currentCanHit = Client.rotationManager.canHitEntityAtRotation(target, currentYaw, currentPitch, maxRange, ignoreBlocks);
        if (currentCanHit && rotationScore(centerYaw, centerPitch, currentYaw, currentPitch) <= 6.0D)
            return new SmartRotationTarget(currentYaw, currentPitch);

        if (Client.rotationManager.canHitEntityAtRotation(target, centerYaw, centerPitch, maxRange, ignoreBlocks))
            return new SmartRotationTarget(centerYaw, centerPitch);

        float bestYaw = 0.0f;
        float bestPitch = 0.0f;
        double bestScore = Double.MAX_VALUE;
        boolean foundTarget = false;

        // Prime the exact search with each face center. A good early result lets the
        // score lower-bound reject most of the 1/32-grid points before ray tracing.
        for (int fixedAxis = 0; fixedAxis < 3; fixedAxis++) {
            for (int fixedSide = 0; fixedSide < 2; fixedSide++) {
                double x = fixedAxis == 0 ? (fixedSide == 0 ? box.minX : box.maxX) : centerX;
                double y = fixedAxis == 1 ? (fixedSide == 0 ? box.minY : box.maxY) : centerY;
                double z = fixedAxis == 2 ? (fixedSide == 0 ? box.minZ : box.maxZ) : centerZ;
                float yaw = rotationYawTo(x, z, eyeX, eyeZ);
                float pitch = rotationPitchTo(x, y, z, eyeX, eyeY, eyeZ);
                double score = smartRotationScore(
                        x, y, z,
                        yaw, pitch,
                        currentYaw, currentPitch,
                        eyeX, eyeY, eyeZ,
                        box, centerX, centerY, centerZ,
                        currentCanHit
                );

                if (score < bestScore && Client.rotationManager.canHitEntityAtRotation(target, yaw, pitch, maxRange, ignoreBlocks)) {
                    bestScore = score;
                    bestYaw = yaw;
                    bestPitch = pitch;
                    foundTarget = true;
                }
            }
        }

        for (int fixedAxis = 0; fixedAxis < 3; fixedAxis++) {
            for (int fixedSide = 0; fixedSide < 2; fixedSide++) {
                double fixedOffset = fixedSide;
                for (double offsetA : SMART_ROTATION_OFFSETS) {
                    for (double offsetB : SMART_ROTATION_OFFSETS) {
                        double x = fixedAxis == 0 ? lerp(box.minX, box.maxX, fixedOffset) : lerp(box.minX, box.maxX, offsetA);
                        double y = fixedAxis == 1 ? lerp(box.minY, box.maxY, fixedOffset) : lerp(box.minY, box.maxY, fixedAxis == 0 ? offsetA : offsetB);
                        double z = fixedAxis == 2 ? lerp(box.minZ, box.maxZ, fixedOffset) : lerp(box.minZ, box.maxZ, offsetB);
                        float yaw = rotationYawTo(x, z, eyeX, eyeZ);
                        float pitch = rotationPitchTo(x, y, z, eyeX, eyeY, eyeZ);
                        double score = smartRotationScore(
                                x, y, z,
                                yaw, pitch,
                                currentYaw, currentPitch,
                                eyeX, eyeY, eyeZ,
                                box, centerX, centerY, centerZ,
                                currentCanHit
                        );

                        if (score >= bestScore
                                || !Client.rotationManager.canHitEntityAtRotation(target, yaw, pitch, maxRange, ignoreBlocks))
                            continue;

                        bestScore = score;
                        bestYaw = yaw;
                        bestPitch = pitch;
                        foundTarget = true;
                    }
                }
            }
        }

        return foundTarget ? new SmartRotationTarget(bestYaw, bestPitch) : null;
    }

    private static double lerp(double start, double end, double delta) {
        return start + (end - start) * delta;
    }

    private static double rotationScore(float yaw, float pitch, float currentYaw, float currentPitch) {
        return Math.abs(Mth.wrapDegrees(yaw - currentYaw)) + Math.abs(pitch - currentPitch);
    }

    private static double normalizedBoxCenterDistanceSqr(double hitX, double hitY, double hitZ,
                                                         AABB box, double centerX, double centerY, double centerZ) {
        double halfX = Math.max(1.0E-3D, (box.maxX - box.minX) * 0.5D);
        double halfY = Math.max(1.0E-3D, (box.maxY - box.minY) * 0.5D);
        double halfZ = Math.max(1.0E-3D, (box.maxZ - box.minZ) * 0.5D);
        double x = (hitX - centerX) / halfX;
        double y = (hitY - centerY) / halfY;
        double z = (hitZ - centerZ) / halfZ;

        return x * x + y * y + z * z;
    }

    private static double smartRotationScore(double hitX, double hitY, double hitZ,
                                             float yaw, float pitch, float currentYaw, float currentPitch,
                                             double eyeX, double eyeY, double eyeZ,
                                             AABB box, double centerX, double centerY, double centerZ,
                                             boolean currentCanHit) {
        double dx = hitX - eyeX;
        double dy = hitY - eyeY;
        double dz = hitZ - eyeZ;
        double distanceSqr = dx * dx + dy * dy + dz * dz;
        double centerScore = normalizedBoxCenterDistanceSqr(hitX, hitY, hitZ, box, centerX, centerY, centerZ);
        double score = rotationScore(yaw, pitch, currentYaw, currentPitch)
                + distanceSqr * 0.001D
                + centerScore * 0.02D;
        return currentCanHit ? score + centerScore * 1.5D : score;
    }

    private static float rotationYawTo(double hitX, double hitZ, double eyeX, double eyeZ) {
        return Mth.wrapDegrees((float) (Mth.atan2(-(hitX - eyeX), hitZ - eyeZ) * (180.0D / Math.PI)));
    }

    private static float rotationPitchTo(double hitX, double hitY, double hitZ,
                                         double eyeX, double eyeY, double eyeZ) {
        double diffX = hitX - eyeX;
        double diffY = hitY - eyeY;
        double diffZ = hitZ - eyeZ;
        double xzDist = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float pitch = (float) (-(Mth.atan2(diffY, xzDist) * (180.0D / Math.PI)));
        return Mth.clamp(pitch, -90.0f, 90.0f);
    }

    private void renderParticles(Entity target) {
        mc.player.magicCrit(target);
        if (mc.player.getDeltaMovement().y < -0.08)
            mc.player.crit(target);
    }

    private boolean attackCooldownMath(Entity target) {
        switch (apsMode.get().toLowerCase(Locale.ROOT)) {
            case "cooldown":
                return mc.player.getAttackStrengthScale(0f) >= (quickCooldown.get() ? 0.8f : 1f);

            case "randomized cps":
                long currentTime = System.currentTimeMillis();
                if (lastAttackTime == 0 || currentTime - lastAttackTime >= randomDelay) {
                    lastAttackTime = currentTime;
                    randomDelay = nextRandomizedCpsDelay();
                    return true;
                }
                break;

            case "no delay":
                return true;

            case "hurt math":
                return ((LivingEntity) target).hurtTime <= 5;
        }

        return false;
    }

    private int nextRandomizedCpsDelay() {
        int min = minCps.get();
        int max = maxCps.get();
        int lower = Math.min(min, max);
        int upper = Math.max(min, max);
        int cps = RandomUtil.nextInt(lower, upper + 1);

        return 1000 / cps;
    }

    private static boolean isAutoBlockMode(String mode) {
        if (mode == null) return false;

        return switch (mode.toLowerCase(Locale.ROOT)) {
            case "hypixel ncp", "blink2", "swap", "legit", "packet", "interact1", "interact2", "basic", "spoof", "delayed", "post attack" -> true;
            default -> false;
        };
    }

    public static boolean shouldCancelUseItemOnWhileBlinking() {
        String mode = autoBlockMode.get();
        return mode.equalsIgnoreCase("vanilla")
                || mode.equalsIgnoreCase("blink1")
                || usesBlink(mode);
    }

    public static boolean isSendingSpreadTeleportPacket() {
        KillAura killAura = ModuleManager.getModule(KillAura.class);
        return killAura != null
                && killAura.pendingTeleportSequence != null
                && killAura.sendingTeleportSequencePacket;
    }

    public static boolean shouldSuppressMovementDuringSpreadTeleport() {
        KillAura killAura = ModuleManager.getModule(KillAura.class);
        return killAura != null
                && killAura.pendingTeleportSequence != null
                && !killAura.sendingTeleportSequencePacket;
    }

    private boolean canAutoBlock(String mode, double squaredDistance, boolean targetVisible) {
        return mc.player != null
                && targetVisible
                && squaredDistance <= square(autoBlockRange.get())
                && (usesItemInteractionMode(mode)
                ? findBlockableHand() != null
                : ToolUtil.INSTANCE.isSword(mc.player.getMainHandItem()));
    }

    private boolean canStartAutoBlockCycle() {
        long now = System.currentTimeMillis();
        long interval = Math.max(50L, (long) (1000.0f / autoBlockCps.get()));
        return lastOpenBlockTime == 0L || now - lastOpenBlockTime >= interval;
    }

    private boolean startAutoBlock(String mode, float yaw, float pitch, Entity target, boolean attacked) {
        if (mc.player == null || wasBlocking || !canStartAutoBlockCycle())
            return false;

        InteractionHand blockHand = InteractionHand.MAIN_HAND;
        if (usesItemInteractionMode(mode)) {
            if (mc.player.isUsingItem())
                return false;

            blockHand = findBlockableHand();
            if (blockHand == null)
                return false;
        } else if (!ToolUtil.INSTANCE.isSword(mc.player.getMainHandItem())) {
            return false;
        }

        if ("swap".equals(mode) && findSwordSlot(mc.player.getInventory().getSelectedSlot()) == -1)
            return false;

        if ("post attack".equals(mode) && !attacked)
            return false;

        switch (mode) {
            case "packet":
                sendUseItemPacketAutoBlock(yaw, pitch);
                break;

            case "interact1":
                sendInteractAutoBlock(target, yaw, pitch);
                break;

            case "basic":
                useItemStrictAutoBlock(blockHand);
                break;

            case "interact2":
                interactWithFacingAutoBlock(yaw, pitch, blockHand);
                break;

            default:
                sendUseItemAutoBlock(yaw, pitch);
                break;
        }

        if ("spoof".equals(mode))
            spoofAutoBlockSlot();

        wasPacketBlocking = true;
        wasBlocking = true;
        blockTick = 1;
        lastOpenBlockTime = System.currentTimeMillis();

        if (usesBlink(mode)) {
            BlinkUtil.INSTANCE.doBlink();
            wasBlinking = true;
        }
        return true;
    }

    private boolean shouldFinishAutoBlock(String mode) {
        return switch (mode) {
            case "delayed" -> blockTick >= 2;
            default -> blockTick >= 1;
        };
    }

    private void finishAutoBlock(String mode, float yaw, float pitch) {
        if (!wasBlocking) {
            blockTick = 0;
            return;
        }

        switch (mode) {
            case "hypixel ncp":
                if (ModuleManager.getModuleState(NoSlow.class))
                    spoofHypixelNoSlowSlot();
                releaseAutoBlock();
                break;

            case "blink2":
            case "legit":
            case "packet":
            case "interact1":
            case "basic":
            case "interact2":
            case "spoof":
            case "delayed":
            case "post attack":
                releaseAutoBlock();
                break;

            case "swap":
                swapToAlternateSwordAndBlock(yaw, pitch);
                clearAutoBlockState();
                break;

            default:
                resetAutoBlock();
        }
    }

    private static boolean usesBlink(String mode) {
        if (mode == null) return false;

        return switch (mode.toLowerCase(Locale.ROOT)) {
            case "hypixel ncp", "blink2" -> true;
            default -> false;
        };
    }

    private static boolean usesItemInteractionMode(String mode) {
        if (mode == null) return false;

        return switch (mode.toLowerCase(Locale.ROOT)) {
            case "basic", "interact2" -> true;
            default -> false;
        };
    }

    private void sendUseItemAutoBlock(float yaw, float pitch) {
        syncSelectedSlot();
        sendSwordBlockPackets(yaw, pitch);
    }

    private void sendUseItemPacketAutoBlock(float yaw, float pitch) {
        syncSelectedSlot();
        PacketUtil.sendPacket(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 0, yaw, pitch));
    }

    private void sendInteractAutoBlock(Entity target, float yaw, float pitch) {
        syncSelectedSlot();
        if (target == null) {
            sendSwordBlockPackets(yaw, pitch);
            return;
        }

        PacketUtil.sendPacket(new ServerboundInteractPacket(
                target.getId(),
                InteractionHand.MAIN_HAND,
                interactionLocation(target, yaw, pitch),
                false
        ));
        sendSwordBlockPackets(yaw, pitch);
    }

    private void sendSwordBlockPackets(float yaw, float pitch) {
        PacketUtil.sendPacket(new ServerboundUseItemOnPacket(
                InteractionHand.MAIN_HAND,
                BlockHitResult.miss(new Vec3(-1.0D, -1.0D, -1.0D), Direction.DOWN, new BlockPos(-1, -1, -1)),
                0
        ));
        PacketUtil.sendPacket(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 0, yaw, pitch));
    }

    private boolean interactWithFacingAutoBlock(float yaw, float pitch, InteractionHand blockHand) {
        double reach = autoBlockRange.get();
        EntityHitResult entityHit = rayTraceEntity(yaw, pitch, reach);
        if (entityHit != null && interactEntityLikeUseItem(entityHit, blockHand))
            return true;

        if (entityHit != null)
            return useItemStrictAutoBlock(blockHand);

        BlockHitResult blockHit = Client.rotationManager.rayTraceBlocks(yaw, pitch, reach);
        if (blockHit.getType() != HitResult.Type.BLOCK)
            return useItemStrictAutoBlock(blockHand);

        return interactBlockLikeUseItem(blockHit, blockHand) || useItemStrictAutoBlock(blockHand);
    }

    private boolean interactEntityLikeUseItem(EntityHitResult hitResult, InteractionHand blockHand) {
        if (mc.player == null || mc.gameMode == null)
            return false;

        Entity entity = hitResult.getEntity();
        Boolean mainHandResult = interactEntityLikeUseItem(entity, hitResult, InteractionHand.MAIN_HAND, blockHand);
        if (mainHandResult != null) return mainHandResult;
        Boolean offHandResult = interactEntityLikeUseItem(entity, hitResult, InteractionHand.OFF_HAND, blockHand);
        return offHandResult != null && offHandResult;
    }

    private Boolean interactEntityLikeUseItem(Entity entity, EntityHitResult hitResult, InteractionHand hand, InteractionHand blockHand) {
        InteractionResult interactResult = mc.gameMode.interact(mc.player, entity, hitResult, hand);
        if (interactResult.consumesAction())
            return false;

        return useItemAutoBlock(hand) ? hand == blockHand : null;
    }

    private boolean interactBlockLikeUseItem(BlockHitResult hitResult, InteractionHand blockHand) {
        if (mc.player == null || mc.gameMode == null)
            return false;

        Boolean mainHandResult = interactBlockLikeUseItem(hitResult, InteractionHand.MAIN_HAND, blockHand);
        if (mainHandResult != null) return mainHandResult;
        Boolean offHandResult = interactBlockLikeUseItem(hitResult, InteractionHand.OFF_HAND, blockHand);
        return offHandResult != null && offHandResult;
    }

    private Boolean interactBlockLikeUseItem(BlockHitResult hitResult, InteractionHand hand, InteractionHand blockHand) {
        InteractionResult interactResult = mc.gameMode.useItemOn(mc.player, hand, hitResult);
        if (interactResult.consumesAction() || interactResult == InteractionResult.FAIL)
            return false;

        return useItemAutoBlock(hand) ? hand == blockHand : null;
    }

    private boolean useItemStrictAutoBlock(InteractionHand blockHand) {
        if (useItemAutoBlock(InteractionHand.MAIN_HAND))
            return blockHand == InteractionHand.MAIN_HAND;
        return useItemAutoBlock(InteractionHand.OFF_HAND) && blockHand == InteractionHand.OFF_HAND;
    }

    private boolean useItemAutoBlock(InteractionHand hand) {
        if (mc.player == null || mc.gameMode == null)
            return false;

        return mc.gameMode.useItem(mc.player, hand).consumesAction();
    }

    private InteractionHand findBlockableHand() {
        if (mc.player == null)
            return null;

        if (isBlockableStack(itemInHand(InteractionHand.MAIN_HAND)))
            return InteractionHand.MAIN_HAND;
        return isBlockableStack(itemInHand(InteractionHand.OFF_HAND)) ? InteractionHand.OFF_HAND : null;
    }

    private ItemStack itemInHand(InteractionHand hand) {
        if (mc.player == null)
            return ItemStack.EMPTY;

        return hand == InteractionHand.MAIN_HAND ? mc.player.getMainHandItem() : mc.player.getOffhandItem();
    }

    private boolean isBlockableStack(ItemStack stack) {
        return mc.player != null
                && mc.level != null
                && !stack.isEmpty()
                && (stack.getUseAnimation() == ItemUseAnimation.BLOCK
                || ToolUtil.INSTANCE.isSword(stack)
                || stack.getItem() instanceof ShieldItem)
                && stack.isItemEnabled(mc.level.enabledFeatures())
                && !mc.player.getCooldowns().isOnCooldown(stack);
    }

    private EntityHitResult rayTraceEntity(float yaw, float pitch, double reach) {
        if (mc.player == null || mc.level == null || reach <= 0.0D || !Double.isFinite(reach))
            return null;

        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 lookVec = getLookVecFromRotations(yaw, pitch);
        Vec3 endPos = eyePos.add(lookVec.scale(reach));
        AABB searchBox = mc.player.getBoundingBox().expandTowards(lookVec.scale(reach)).inflate(1.0D);
        Entity closestEntity = null;
        Vec3 closestHit = null;
        double closestDistance = reach * reach;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player || !entity.isPickable() || entity.isSpectator())
                continue;

            AABB box = entity.getBoundingBox().inflate(Math.max(0.0D, entity.getPickRadius()));
            if (!searchBox.intersects(box))
                continue;

            Vec3 hit = box.contains(eyePos)
                    ? eyePos
                    : box.clip(eyePos, endPos).orElse(null);
            if (hit == null)
                continue;

            double distance = eyePos.distanceToSqr(hit);
            if (distance <= closestDistance) {
                closestEntity = entity;
                closestHit = hit;
                closestDistance = distance;
            }
        }

        return closestEntity != null ? new EntityHitResult(closestEntity, closestHit) : null;
    }

    private void syncSelectedSlot() {
        if (mc.player == null) return;

        PacketUtil.sendPacket(new ServerboundSetCarriedItemPacket(mc.player.getInventory().getSelectedSlot()));
    }

    private Vec3 interactionLocation(Entity target, float yaw, float pitch) {
        Vec3 fallback = target.getBoundingBox().getCenter().subtract(target.position());
        if (mc.player == null) return fallback;

        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 lookVec = getLookVecFromRotations(yaw, pitch);
        return target.getBoundingBox()
                .inflate(0.1D)
                .clip(eyePos, eyePos.add(lookVec.scale(128.0D)))
                .map(vec3 -> vec3.subtract(target.position()))
                .orElse(fallback);
    }

    private Vec3 getLookVecFromRotations(float yaw, float pitch) {
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);
        float x = -Mth.cos(pitchRad) * Mth.sin(yawRad);
        float y = -Mth.sin(pitchRad);
        float z = Mth.cos(pitchRad) * Mth.cos(yawRad);
        return new Vec3(x, y, z).normalize();
    }

    private void releaseAutoBlock() {
        releaseUsingItem();
        clearAutoBlockState();
    }

    private void clearAutoBlockState() {
        wasPacketBlocking = false;
        wasBlocking = false;
        blockTick = 0;

        if (wasBlinking) {
            BlinkUtil.INSTANCE.sync(true, true);
            BlinkUtil.INSTANCE.stopBlink();
            wasBlinking = false;
        }
    }

    private void resetAutoBlock() {
        if (wasBlocking)
            releaseUsingItem();

        wasPacketBlocking = false;
        wasBlocking = false;
        blockTick = 0;
        lastOpenBlockTime = 0;

        if (wasBlinking) {
            BlinkUtil.INSTANCE.sync(true, true);
            BlinkUtil.INSTANCE.stopBlink();
            wasBlinking = false;
        }
    }

    private void releaseUsingItem() {
        if (mc.player != null && mc.gameMode != null && mc.player.isUsingItem()) {
            mc.gameMode.releaseUsingItem(mc.player);
            return;
        }

        PacketUtil.sendPacket(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM,
                BlockPos.ZERO,
                Direction.DOWN
        ));
    }

    private void spoofHypixelNoSlowSlot() {
        if (mc.player == null) return;

        int oldSlot = mc.player.getInventory().getSelectedSlot();
        int spoofSlot = RandomUtil.nextInt(0, 9);
        if (spoofSlot == oldSlot)
            spoofSlot = (oldSlot + 1) % 9;

        PacketUtil.sendPacket(new ServerboundSetCarriedItemPacket(spoofSlot));
        PacketUtil.sendPacket(new ServerboundSetCarriedItemPacket(oldSlot));
    }

    private void spoofAutoBlockSlot() {
        if (mc.player == null) return;

        int oldSlot = mc.player.getInventory().getSelectedSlot();
        int spoofSlot = findEmptySlot(oldSlot);
        PacketUtil.sendPacket(new ServerboundSetCarriedItemPacket(spoofSlot));
        PacketUtil.sendPacket(new ServerboundSetCarriedItemPacket(oldSlot));
    }

    private void swapToAlternateSwordAndBlock(float yaw, float pitch) {
        if (mc.player == null) return;

        int swordSlot = findSwordSlot(mc.player.getInventory().getSelectedSlot());
        if (swordSlot == -1) return;

        PacketUtil.sendPacket(new ServerboundSetCarriedItemPacket(swordSlot));
        sendSwordBlockPackets(yaw, pitch);
    }

    private int findEmptySlot(int currentSlot) {
        if (mc.player == null) return Math.floorMod(currentSlot - 1, 9);

        for (int i = 0; i < 9; i++) {
            if (i != currentSlot && mc.player.getInventory().getItem(i).isEmpty())
                return i;
        }

        for (int i = 0; i < 9; i++) {
            if (i != currentSlot)
                return i;
        }

        return Math.floorMod(currentSlot - 1, 9);
    }

    private int findSwordSlot(int currentSlot) {
        if (mc.player == null) return -1;

        for (int i = 0; i < 9; i++) {
            if (i != currentSlot && ToolUtil.INSTANCE.isSword(mc.player.getInventory().getItem(i)))
                return i;
        }

        return -1;
    }

    private void resetPacketUnblocking() {
        if (wasBlocking) {
            resetAutoBlock();
            return;
        }

        if (wasPacketBlocking) {
            int oldSlot = mc.player.getInventory().getSelectedSlot();
            if (oldSlot == 0) {
                PacketUtil.sendPacket(new ServerboundSetCarriedItemPacket(oldSlot + 1));
                PacketUtil.sendPacket(new ServerboundSetCarriedItemPacket(oldSlot));
            } else {
                PacketUtil.sendPacket(new ServerboundSetCarriedItemPacket(oldSlot - 1));
                PacketUtil.sendPacket(new ServerboundSetCarriedItemPacket(oldSlot));
            }
            wasPacketBlocking = false;
        }
    }

    private boolean startSpreadTeleportSequence(List<Entity> attackTargets, double baseAttackReachSquared,
                                                String autoBlockMode, float yaw, float pitch,
                                                Entity blockTarget, boolean canAutoBlock) {
        int spreadTicks = Math.clamp(tpTicks.get(), 0, 60);
        if (spreadTicks == 0 || attackTargets.isEmpty())
            return false;
        int totalTicks = spreadTicks + 1;

        boolean returnAfterAttack = tpBack.get();
        ArrayList<TeleportStep> steps = new ArrayList<>();
        Vec3 simulatedPosition = mc.player.position();
        Vec3 simulatedEyePosition = mc.player.getEyePosition();
        double eyeHeight = mc.player.getEyeHeight();
        int totalMovePackets = 0;

        for (Entity target : attackTargets) {
            boolean teleport = tpReach.get()
                    && EntityExtension.squaredBoxedDistanceTo(target, simulatedEyePosition) >= baseAttackReachSquared;
            if (!teleport) {
                steps.add(new TeleportAttackStep(target, null));
                continue;
            }

            Vec3 destination = target.position();
            ArrayList<Vec3> paths = MainPathFinder.computePath(simulatedPosition, destination);
            if (paths == null || paths.isEmpty())
                continue;

            for (Vec3 path : paths) {
                steps.add(new TeleportMoveStep(path));
                totalMovePackets++;
            }

            steps.add(new TeleportAttackStep(target, returnAfterAttack ? null : destination));

            if (returnAfterAttack) {
                for (int i = paths.size() - 1; i >= 0; i--) {
                    steps.add(new TeleportMoveStep(paths.get(i)));
                    totalMovePackets++;
                }
            } else {
                simulatedPosition = destination;
                simulatedEyePosition = destination.add(0.0D, eyeHeight, 0.0D);
            }
        }

        if (totalMovePackets == 0)
            return false;

        if (wasBlinking)
            BlinkUtil.INSTANCE.sync(true, true);

        pendingTeleportSequence = new PendingTeleportSequence(
                steps,
                totalMovePackets,
                totalTicks,
                swingOrderMode.get().toLowerCase(Locale.ROOT),
                autoBlockMode,
                yaw,
                pitch,
                blockTarget,
                canAutoBlock,
                tpOnGroundPacket.get(),
                mc.player.horizontalCollision
        );
        advancePendingTeleportSequence();
        return true;
    }

    private void advancePendingTeleportSequence() {
        PendingTeleportSequence sequence = pendingTeleportSequence;
        if (sequence == null || mc.player == null || mc.gameMode == null)
            return;

        sequence.elapsedTicks = Math.min(sequence.elapsedTicks + 1, sequence.totalTicks);
        int movePacketTarget = (int) ((long) sequence.totalMovePackets
                * sequence.elapsedTicks / sequence.totalTicks);

        while (sequence.nextStep < sequence.steps.size()) {
            TeleportStep step = sequence.steps.get(sequence.nextStep);
            if (step instanceof TeleportMoveStep moveStep) {
                if (sequence.sentMovePackets >= movePacketTarget)
                    break;

                sendTeleportMovePacket(moveStep.position(), sequence.onGround, sequence.horizontalCollision);
                sequence.sentPositions.add(moveStep.position());
                sequence.sentMovePackets++;
            } else if (step instanceof TeleportAttackStep attackStep) {
                if (attackStep.clientPosition() != null) {
                    mc.player.setPos(attackStep.clientPosition());
                    sequence.sentPositions.clear();
                }
                performSpreadTeleportAttack(attackStep.target(), sequence.swingMode);
            }
            sequence.nextStep++;
        }

        if (sequence.nextStep < sequence.steps.size())
            return;

        pendingTeleportSequence = null;
        finishAttackCycle(
                sequence.autoBlockMode,
                sequence.yaw,
                sequence.pitch,
                sequence.blockTarget,
                sequence.canAutoBlock
        );
    }

    private void cancelPendingTeleportSequence() {
        PendingTeleportSequence sequence = pendingTeleportSequence;
        if (sequence == null)
            return;

        try {
            if (mc.player == null || sequence.sentPositions.isEmpty())
                return;

            for (int i = sequence.sentPositions.size() - 1; i >= 0; i--)
                sendTeleportMovePacket(sequence.sentPositions.get(i), sequence.onGround, sequence.horizontalCollision);

            sendTeleportMovePacket(mc.player.position(), sequence.onGround, mc.player.horizontalCollision);
        } finally {
            pendingTeleportSequence = null;
            sendingTeleportSequencePacket = false;
        }
    }

    private void sendTeleportMovePacket(Vec3 position, boolean onGround, boolean horizontalCollision) {
        boolean wasSendingTeleportSequencePacket = sendingTeleportSequencePacket;
        sendingTeleportSequencePacket = true;
        try {
            PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Pos(
                    position.x,
                    position.y,
                    position.z,
                    onGround,
                    horizontalCollision
            ));
        } finally {
            sendingTeleportSequencePacket = wasSendingTeleportSequencePacket;
        }
    }

    private void performSpreadTeleportAttack(Entity target, String currentSwingMode) {
        boolean wasSendingTeleportSequencePacket = sendingTeleportSequencePacket;
        sendingTeleportSequencePacket = true;
        try {
            performAttack(target, currentSwingMode);
        } finally {
            sendingTeleportSequencePacket = wasSendingTeleportSequencePacket;
        }
    }

    private void attackToEntity(Entity target, boolean tpMode) {
        ArrayList<Vec3> paths = null;

        if (tpMode) {
            paths = MainPathFinder.computePath(mc.player.position(), target.position());

            if (paths == null || paths.isEmpty()) return;

            for (Vec3 path : paths) {
                PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Pos(path.x, path.y, path.z, tpOnGroundPacket.get(), mc.player.horizontalCollision));
            }

            if (!tpBack.get())
                mc.player.setPos(target.position());
        }

        performAttack(target, swingOrderMode.get().toLowerCase(Locale.ROOT));

        if (tpMode && !paths.isEmpty() && tpBack.get()) {
            List<Vec3> reversedPaths = paths.reversed();

            for (Vec3 path : reversedPaths) {
                PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Pos(path.x, path.y, path.z, tpOnGroundPacket.get(), mc.player.horizontalCollision));
            }
        }
    }

    private void performAttack(Entity target, String currentSwingMode) {
        switch (currentSwingMode) {
            case "1.8":
                mc.player.swing(InteractionHand.MAIN_HAND);
                mc.gameMode.attack(mc.player, target);
                break;

            case "1.9.x":
                mc.gameMode.attack(mc.player, target);
                mc.player.swing(InteractionHand.MAIN_HAND);
                break;

            case "no order":
                mc.gameMode.attack(mc.player, target);
                PlayerUtil.INSTANCE.swingHandWithoutPacket(InteractionHand.MAIN_HAND);
                break;
        }

        renderParticles(target);
    }

    private void reset() {
        cancelPendingTeleportSequence();

        if (closestEntity != null)
            Client.rotationManager.stopRotation();

        if (wasBlocking) {
            resetAutoBlock();
        } else if (autoBlockMode.get().equalsIgnoreCase("blink1") && wasBlinking) {
            BlinkUtil.INSTANCE.sync(true, true);
            BlinkUtil.INSTANCE.stopBlink();
        }

        lastAttackTime = 0;
        lastOpenBlockTime = 0;
        randomDelay = 0;
        abTick = 0;
        blockTick = 0;

        closestEntity = null;
        multiTargets.clear();
        targetCandidateBuffer.clear();
        for (TargetCandidate candidate : targetCandidatePool)
            candidate.set(null, 0.0D, false);
        targetCandidatePoolCursor = 0;
        wasBlinking = false;
        wasBlocking = false;

        isBlocking = false;
    }
}
