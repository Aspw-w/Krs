package com.instrumentalist.krs.hacks.features.player;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.BlockEdgeEvent;
import com.instrumentalist.krs.events.features.TickEvent;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.movement.MovementFix;
import com.instrumentalist.krs.hacks.features.movement.speed.SpeedModule;
import com.instrumentalist.krs.hacks.features.movement.Sprint;
import com.instrumentalist.krs.utils.entity.PlayerUtil;
import com.instrumentalist.krs.utils.math.BehaviorUtils;
import com.instrumentalist.krs.utils.math.RandomUtil;
import com.instrumentalist.krs.utils.math.RotationScraper;
import com.instrumentalist.krs.utils.math.TimerUtil;
import com.instrumentalist.krs.utils.move.InputUtil;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.krs.utils.value.ListValue;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PlayerHeadItem;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.player.ClientInput;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public class Scaffold extends Module {

    private static final double MATH_MOVEMENT_ALIGNMENT_WEIGHT = 1.35D;
    private static final float MATH_MOVEMENT_SECTOR_DEGREES = 45.0F;
    private static final float MATH_MOVEMENT_ALIGNMENT_EPSILON = 0.01F;
    private static final double MATH_FACE_CENTER_WEIGHT = 0.12D;
    private static final double MATH_EDGE_HIT_PENALTY = 4.0D;
    private static final double MATH_SAFE_HIT_INSET = 0.0625D;
    private static final double MATH_ALIGNED_RAY_EPSILON = 1.0E-5D;
    private static final double MATH_SEARCH_BASE_ROTATION_SAVING_TICKS = 0.30D;
    private static final double MATH_SEARCH_DISTANCE_SAVING_TICKS = 0.42D;
    private static final double MATH_SEARCH_DISTANCE_SQUARED_SAVING_TICKS = 0.12D;
    private static final double MATH_SEARCH_ORIGIN_PRIORITY_TICKS = 0.30D;
    private static final double MATH_SEARCH_PATH_WEIGHT_TICKS = 0.24D;
    private static final double MATH_SEARCH_PLAYER_DISTANCE_WEIGHT_TICKS = 0.35D;
    private static final int MATH_FACE_SAMPLE_DIVISIONS = 16;

    @Setting
    private static final ListValue mode = new ListValue("Mode", new String[]{"Normal", "Telly"}, "Normal");

    @Setting
    private static final BooleanValue tower = new BooleanValue("Tower", true);

    @Setting
    private static final BooleanValue towerCenter = new BooleanValue("Tower Center", false, tower::get);

    @Setting
    private static final ListValue towerWhen = new ListValue("Tower When", new String[]{"Always", "Moving", "Standing"}, "Always", tower::get);

    @Setting
    private static final ListValue towerMode = new ListValue("Tower Mode", new String[]{"Vanilla", "NCP", "Hypixel"}, "Vanilla", tower::get);

    @Setting
    private static final FloatValue towerSpeed = new FloatValue("Tower Speed", 1f, 0.1f, 1f, () -> tower.get() && towerMode.get().equalsIgnoreCase("vanilla"));

    @Setting
    private static final ListValue tellyMode = new ListValue("Telly Mode", new String[]{"Normal", "Non Upwards"}, "Normal", Scaffold::canDisplayTellyMode);

    @Setting
    public static final ListValue rotationMode = new ListValue("Rotation Mode", new String[]{"Math", "Simple", "Backwards", "None"}, "Math");

    @Setting
    private static final FloatValue maxRotationSpeed = new FloatValue("Max Rotation Speed", 60f, 0f, 180f, Scaffold::usesRotationSpeed);

    @Setting
    private static final FloatValue minRotationSpeed = new FloatValue("Min Rotation Speed", 40f, 0f, 180f, Scaffold::usesRotationSpeed);

    @Setting
    private static final FloatValue preRotationLead = new FloatValue("Pre Rotation Lead", 1.0f, 0.0f, 5.0f, Scaffold::usesMathPreRotation);

    @Setting
    private static final BooleanValue snapRotation = new BooleanValue("Snap Rotation", false, () -> rotationMode.get().equalsIgnoreCase("math") || rotationMode.get().equalsIgnoreCase("simple"));

    @Setting
    private static final BooleanValue noPlaceWhenSnapping = new BooleanValue("No Place when Snapping", false, () -> (rotationMode.get().equalsIgnoreCase("math") || rotationMode.get().equalsIgnoreCase("simple")) && snapRotation.get());

    @Setting
    private static final BooleanValue noHitCheck = new BooleanValue("No Hit Check", false, Scaffold::hasNoHitCheckOption);

    @Setting
    private static final BooleanValue tellyAfterPlaceNoHitCheck = new BooleanValue("Telly After Place No Hit Check", false, Scaffold::canDisplayTellyAfterPlaceNoHitCheck);

    @Setting
    private static final IntValue searchRange = new IntValue("Search Range", 2, 0, 5, "m");

    @Setting
    private static final BooleanValue customTimer = new BooleanValue("Custom Timer", false);

    @Setting
    private static final FloatValue towerTimerSpeed = new FloatValue("Tower Timer Speed", 1.5f, 0.1f, 10f, () -> customTimer.get() && tower.get());

    @Setting
    private static final FloatValue normalTimerSpeed = new FloatValue("Normal Timer Speed", 1.5f, 0.1f, 10f, customTimer::get);

    @Setting
    private static final BooleanValue keepY = new BooleanValue("KeepY", true);

    @Setting
    private static final BooleanValue keepYOnlySpeed = new BooleanValue("KeepY Only Speed", true, keepY::get);

    @Setting
    private static final BooleanValue intelligentPicker = new BooleanValue("Intelligent Picker", true);

    @Setting
    public static final BooleanValue noSprint = new BooleanValue("No Sprint", false);

    @Setting
    private static final ListValue edgeSafe = new ListValue("Edge Safe", new String[]{"None", "Safewalk", "Sneak", "Auto Input Fix"}, "None");

    @Setting
    private static final BooleanValue down = new BooleanValue("Down", true);

    private static int hotbarStackSize = 0;
    private static boolean wasTowering = false;
    private static boolean startedScaffold = false;

    private boolean once = false;
    private double jumpGround = 0.0;
    private boolean checkGround = false;
    private Integer launchY = null;
    private static boolean autoSneaking = false;
    private boolean placedScaffoldBlock = false;
    private BlockPos lastPlacedScaffoldBlock = null;
    private boolean snapRotationResetPending = false;
    private boolean tellyAfterPlaceNoHitCheckActive = false;
    private PlacementTarget lockedMathPlacementTarget = null;
    private int tellyStage = 0;
    private int tellyNormalJumpIndex = 0;
    private boolean suppressTellyNormalJumpSprint = false;
    private boolean tellyNormalJumpedThisTick = false;
    private boolean tellyJumpedThisTick = false;
    private boolean wasTellyKeepYOnGround = false;
    private boolean tellyJumpDownOnLastGround = false;
    private boolean tellyClutchMode = false;
    private Integer lastSlot = null;

    private final Direction[] orderedHorizontalDirections = new Direction[4];
    private final Direction[] orderedPlaceDirections = new Direction[6];
    private final ArrayList<BlockPos> scaffoldCandidates = new ArrayList<>(64);
    private final ArrayList<BlockPos> scaffoldDistanceCandidates = new ArrayList<>(32);
    private final HashSet<BlockPos> scaffoldCandidateSet = new HashSet<>(64);

    private record PlacementTarget(BlockPos pos, BlockPos neighbour, Vec3 hitPos, Direction side, int searchDistance) {
    }

    private record PlacementHit(Vec3 hitPos, double rotationScore, double distanceSqr) {
    }

    public Scaffold() {
        super("Scaffold", ModuleCategory.Player, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    @Override
    public String tag() {
        return mode.get();
    }

    @Override
    public void onDisable() {
        var player = mc.player;
        if (player != null && mc.level != null) {
            if (wasTowering) {
                switch (towerMode.get().toLowerCase(Locale.ROOT)) {
                    case "vanilla" -> {
                        if (hasAroundBlock())
                            MovementUtil.setVelocityY(-0.4);
                    }
                    case "hypixel" -> launchY = player.blockPosition().getY() - 1;
                }
            }
            if (once) {
                Client.rotationManager.stopRotation();
                TimerUtil.reset();
                BehaviorUtils.noKillAura = false;
                if (lastSlot != null)
                    selectSlot(lastSlot);
            }
        }

        lastSlot = null;
        once = false;
        jumpGround = 0.0;
        wasTowering = false;
        checkGround = false;
        wasTowering = false;
        launchY = null;
        startedScaffold = false;
        placedScaffoldBlock = false;
        lastPlacedScaffoldBlock = null;
        snapRotationResetPending = false;
        tellyAfterPlaceNoHitCheckActive = false;
        clearMathPlacementTarget();
        tellyStage = 0;
        tellyNormalJumpIndex = 0;
        suppressTellyNormalJumpSprint = false;
        tellyNormalJumpedThisTick = false;
        tellyJumpedThisTick = false;
        wasTellyKeepYOnGround = false;
        tellyJumpDownOnLastGround = false;
        tellyClutchMode = false;
        updateAutoSneak(false);
        PlayerUtil.INSTANCE.stopSpoof();
    }

    @Override
    public void onEnable() {
        var player = mc.player;
        if (player == null || mc.level == null) return;

        launchY = player.blockPosition().getY();
        lastSlot = player.getInventory().getSelectedSlot();
        placedScaffoldBlock = false;
        lastPlacedScaffoldBlock = null;
        snapRotationResetPending = false;
        tellyAfterPlaceNoHitCheckActive = false;
        clearMathPlacementTarget();
        tellyStage = 0;
        tellyNormalJumpIndex = 0;
        suppressTellyNormalJumpSprint = false;
        tellyNormalJumpedThisTick = false;
        tellyJumpedThisTick = false;
        wasTellyKeepYOnGround = player.onGround();
        tellyJumpDownOnLastGround = isTellyMode() && player.onGround() && isPhysicalJumpDown();
        tellyClutchMode = false;
        countBlockItems(player.getInventory());
    }

    @Override
    public void onBlockEdge(BlockEdgeEvent event) {
        if (mc.player == null || mc.level == null) return;

        if (edgeSafe.get().equalsIgnoreCase("safewalk") && (!down.get() || !isPhysicalKeyDown(mc.options.keyShift.saveString())))
            event.cancel();
    }

    @Override
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.level == null) return;

        tellyJumpedThisTick = false;
        tellyNormalJumpedThisTick = false;
        boolean tellyGroundPhase = isTellyGroundPhase();

        if (tellyGroundPhase)
            faceTellyJumpRotation();

        if (down.get())
            mc.options.keyShift.setDown(false);

        if (tellyGroundPhase || wasTowering)
            mc.options.keyJump.setDown(false);

        if (!tellyGroundPhase
                && !wasTowering
                && isTellyUpwardJumpInputActive(mc.player)
                && !ModuleManager.getModuleState(SpeedModule.class))
            mc.options.keyJump.setDown(true);
    }

    public static void hookTellyJumpInput(ClientInput input) {
        Scaffold scaffold = ModuleManager.getModule(Scaffold.class);
        if (scaffold == null || !scaffold.tempEnabled)
            return;

        scaffold.handleTellyJumpInput(input);
    }

    private void handleTellyJumpInput(ClientInput input) {
        var player = mc.player;
        if (input == null || player == null || mc.level == null || player.input != input || !isTellyMode())
            return;

        updateTellyJumpGroundInput(player);
        updateTellyClutchMode(player);
        updateTellyNormalJumpIndex(player);
        if (shouldUseNormalScaffoldForTelly(player))
            return;

        if (shouldTowerBlockedTellyJump(player)) {
            InputUtil.setJumping(input, false);
            return;
        }

        boolean normalTellyJump = isTellyNormalJumpTiming(player);
        boolean suppressTellySprint = shouldSuppressTellyNormalJumpSprint();

        if ((normalTellyJump || suppressTellySprint) && player.isSprinting())
            player.setSprinting(false);

        if (isTellyMovementActive() && hotbarStackSize > 0 && !normalTellyJump && !suppressTellySprint)
            Sprint.tryStartSprinting(player, input.keyPresses == null ? Input.EMPTY : input.keyPresses, true);

        if (!isTellyGroundPhase(player))
            return;

        if (tellyStage <= 0) {
            InputUtil.setJumping(input, false);
            return;
        }

        faceTellyJumpRotation();
        if (!requestTellyJump(player, input))
            InputUtil.setJumping(input, false);
    }

    private boolean requestTellyJump(net.minecraft.client.player.LocalPlayer player, ClientInput input) {
        Input keyPresses = input.keyPresses == null ? Input.EMPTY : input.keyPresses;
        if (isTellyNormalJumpTiming(player))
            return requestNormalTellyJump(player, input, keyPresses);

        boolean sprinting = keyPresses.sprint() || player.isSprinting();
        if (Sprint.tryStartSprinting(player, keyPresses, true))
            sprinting = true;

        if (!sprinting)
            return false;

        InputUtil.applyInput(input, new Input(
                keyPresses.forward(),
                keyPresses.backward(),
                keyPresses.left(),
                keyPresses.right(),
                true,
                keyPresses.shift(),
                sprinting
        ));
        tellyJumpedThisTick = true;
        suppressTellyNormalJumpSprint = false;
        tellyNormalJumpedThisTick = false;
        recordTellyJump();
        return true;
    }

    private boolean requestNormalTellyJump(net.minecraft.client.player.LocalPlayer player, ClientInput input, Input keyPresses) {
        player.setSprinting(false);
        InputUtil.applyInput(input, new Input(
                keyPresses.forward(),
                keyPresses.backward(),
                keyPresses.left(),
                keyPresses.right(),
                true,
                keyPresses.shift(),
                false
        ));
        tellyJumpedThisTick = true;
        suppressTellyNormalJumpSprint = true;
        tellyNormalJumpedThisTick = true;
        recordTellyJump();
        return true;
    }

    private void updateTellyStage(net.minecraft.client.player.LocalPlayer player) {
        if (!isTellyMovementActive() || hotbarStackSize <= 0 || shouldUseNormalScaffoldForTelly(player)) {
            tellyStage = 0;
            tellyNormalJumpIndex = 0;
            suppressTellyNormalJumpSprint = false;
            tellyNormalJumpedThisTick = false;
            return;
        }

        if (!player.onGround())
            return;

        if (tellyStage > 0)
            tellyStage--;
        else if (tellyStage < 0)
            tellyStage++;

        if (tellyStage == 0)
            tellyStage = 1;

        faceTellyJumpRotation();
    }

    private boolean shouldTellyAvoidAiming() {
        var player = mc.player;
        if (shouldUseNormalScaffoldForTelly(player))
            return false;

        if (isTellyGroundPhase(player))
            return !wasTowering;

        return player != null
                && isTellyMode()
                && MovementUtil.isMoving()
                && hotbarStackSize > 0
                && MovementUtil.fallTicks <= 0
                && !wasTowering;
    }

    private boolean isTellyGroundPhase() {
        return isTellyGroundPhase(mc.player);
    }

    private boolean isTellyGroundPhase(net.minecraft.client.player.LocalPlayer player) {
        return player != null
                && isTellyMovementActive()
                && hotbarStackSize > 0
                && player.onGround()
                && !shouldUseNormalScaffoldForTelly(player);
    }

    private boolean shouldUseNormalScaffoldForTelly(net.minecraft.client.player.LocalPlayer player) {
        return isTellyMode()
                && player != null
                && (player.onGround() && isTellyHeadBlocked(player) || isTellyClutchMode(player));
    }

    private void updateTellyClutchMode(net.minecraft.client.player.LocalPlayer player) {
        if (!isTellyMode() || player == null || player.onGround()) {
            tellyClutchMode = false;
            return;
        }

        if (isTellyClutchTrigger(player))
            tellyClutchMode = true;
    }

    private boolean isTellyClutchMode(net.minecraft.client.player.LocalPlayer player) {
        return tellyClutchMode && player != null && !player.onGround();
    }

    private boolean isTellyClutchTrigger(net.minecraft.client.player.LocalPlayer player) {
        return player.getDeltaMovement().y <= -0.65D
                || isFarFromLaunchY(player)
                || player.hurtTime != 0;
    }

    private boolean isFarFromLaunchY(net.minecraft.client.player.LocalPlayer player) {
        return launchY != null && Math.abs(player.getY() - launchY) >= 4.0D;
    }

    private boolean isTellyHeadBlocked(net.minecraft.client.player.LocalPlayer player) {
        var level = mc.level;
        if (player == null || level == null)
            return false;

        AABB jumpCheckBox = player.getBoundingBox().move(0.0D, 0.42D, 0.0D);
        return !level.noCollision(player, jumpCheckBox);
    }

    private boolean isTellyMovementActive() {
        return isTellyMode() && isTellyMoving();
    }

    private static boolean canDisplayTellyMode() {
        return isTellyMode() && !tower.get();
    }

    private boolean isTellyNormalJumpModeActive() {
        return !tellyMode.get().equalsIgnoreCase("normal")
                && canDisplayTellyMode()
                && isPhysicalJumpDown()
                && isTellyMovementActive()
                && hotbarStackSize > 0;
    }

    private void updateTellyNormalJumpIndex(net.minecraft.client.player.LocalPlayer player) {
        if (!isTellyNormalJumpModeActive() || shouldUseNormalScaffoldForTelly(player)) {
            tellyNormalJumpIndex = 0;
            suppressTellyNormalJumpSprint = false;
            return;
        }

        if (suppressTellyNormalJumpSprint && player != null && player.onGround() && !isTellyNormalJumpQueued())
            suppressTellyNormalJumpSprint = false;
    }

    private boolean isTellyNormalJumpQueued() {
        if (!isTellyNormalJumpModeActive())
            return false;

        return tellyMode.get().equalsIgnoreCase("non upwards");
    }

    private boolean isTellyNormalJumpTiming(net.minecraft.client.player.LocalPlayer player) {
        return isTellyNormalJumpQueued()
                && isTellyGroundPhase(player)
                && tellyStage > 0;
    }

    private boolean isTellyNormalJumpRotationTiming(net.minecraft.client.player.LocalPlayer player) {
        return isTellyNormalJumpTiming(player)
                || tellyNormalJumpedThisTick && isTellyNormalJumpModeActive() && player != null;
    }

    private boolean shouldSuppressTellyNormalJumpSprint() {
        return isTellyNormalJumpModeActive()
                && !shouldUseNormalScaffoldForTelly(mc.player)
                && suppressTellyNormalJumpSprint;
    }

    private void recordTellyJump() {
        if (isTellyNormalJumpModeActive())
            tellyNormalJumpIndex++;
        else
            tellyNormalJumpIndex = 0;
    }

    private static boolean isTellyMoving() {
        return MovementUtil.isMoving();
    }

    private boolean shouldFreezeTellyKeepY(net.minecraft.client.player.LocalPlayer player) {
        return isTellyMode()
                && player != null
                && !shouldUseNormalScaffoldForTelly(player)
                && !isTellyUpwardJumpInputActive(player)
                && (isTellyMoving() || !isPhysicalMovementInputDown());
    }

    private boolean shouldUpdateTellyGroundKeepY(net.minecraft.client.player.LocalPlayer player) {
        return keepY.get()
                && isTellyMode()
                && player != null
                && player.onGround()
                && isPhysicalMovementInputDown()
                && !shouldUseNormalScaffoldForTelly(player)
                && !wasTellyKeepYOnGround;
    }

    private void updateTellyKeepYGroundState(net.minecraft.client.player.LocalPlayer player) {
        wasTellyKeepYOnGround = player != null && player.onGround();
    }

    private void updateTellyJumpGroundInput(net.minecraft.client.player.LocalPlayer player) {
        if (!isTellyMode() || player == null) {
            tellyJumpDownOnLastGround = false;
            return;
        }

        if (player.onGround())
            tellyJumpDownOnLastGround = isPhysicalJumpDown();
    }

    private static boolean isPhysicalJumpDown() {
        return isPhysicalKeyDown(mc.options.keyJump.saveString());
    }

    private static boolean isPhysicalMovementInputDown() {
        return isPhysicalKeyDown(mc.options.keyUp.saveString())
                || isPhysicalKeyDown(mc.options.keyDown.saveString())
                || isPhysicalKeyDown(mc.options.keyLeft.saveString())
                || isPhysicalKeyDown(mc.options.keyRight.saveString());
    }

    private boolean isTowerJumpInputActive(net.minecraft.client.player.LocalPlayer player) {
        return isTellyUpwardJumpInputActive(player) && !isTellyJumpPriorityActive(player);
    }

    private boolean shouldTowerBlockedTellyJump(net.minecraft.client.player.LocalPlayer player) {
        return tower.get()
                && isTellyMode()
                && player != null
                && player.horizontalCollision
                && isPhysicalJumpDown()
                && isTellyMoving();
    }

    private boolean isTellyUpwardJumpInputActive(net.minecraft.client.player.LocalPlayer player) {
        if (!isPhysicalJumpDown())
            return false;
        if (!isTellyMode() || player == null || player.onGround())
            return true;

        return tellyJumpDownOnLastGround;
    }

    private boolean isTellyJumpPriorityActive(net.minecraft.client.player.LocalPlayer player) {
        return (isTellyGroundPhase(player) && !shouldTowerBlockedTellyJump(player)) || tellyJumpedThisTick;
    }

    private static boolean shouldAutoSneakAtEdge(ClientInput clientInput) {
        var player = mc.player;
        var level = mc.level;
        if (player == null || level == null) return false;
        if (down.get() && isPhysicalKeyDown(mc.options.keyShift.saveString())) return false;
        if (!player.onGround() || player.getAbilities().flying || player.isPassenger() || player.isSwimming() || player.isFallFlying())
            return false;

        Input input = clientInput != null && clientInput.keyPresses != null ? clientInput.keyPresses : Input.EMPTY;
        if (!isMovingInput(input))
            return false;

        Vec3 direction = getAutoSneakDirection(input);
        if (direction.lengthSqr() <= 1.0E-6)
            return false;

        double distance = edgeCheckDistance();
        Vec3 position = player.position();
        return wouldBeCloseToFallOff(position)
                || wouldBeCloseToFallOff(position.add(direction.scale(distance)));
    }

    private static Vec3 getAutoSneakDirection(Input input) {
        var player = mc.player;
        if (player == null || !isMovingInput(input)) return Vec3.ZERO;

        float movementYaw = MovementFix.shouldFixMovement() ? MovementFix.getMovementYaw() : player.getYRot();
        return MovementUtil.getHorizontalDirectionVector(getMovementDirection(movementYaw, input));
    }

    private static double edgeCheckDistance() {
        var player = mc.player;
        if (player == null) return 0.45D;

        Vec3 motion = player.getDeltaMovement();
        double speed = MovementUtil.getSpeed(motion.x, motion.z);
        return Mth.clamp(Math.max(speed + 0.1D, player.isSprinting() ? 0.45D : 0.4D), 0.1D, 0.6D);
    }

    private static boolean wouldBeCloseToFallOff(Vec3 position) {
        var player = mc.player;
        var level = mc.level;
        if (player == null || level == null) return false;

        AABB hitbox = player.getDimensions(Pose.STANDING)
                .makeBoundingBox(position)
                .inflate(-0.05D, 0.0D, -0.05D)
                .move(0.0D, player.fallDistance - player.maxUpStep(), 0.0D);
        return level.noCollision(player, hitbox);
    }

    public static void hookEdgeSafeSneakInput(ClientInput input) {
        if (input == null || mc.player == null || mc.level == null)
            return;

        if (!ModuleManager.getModuleState(Scaffold.class) || !edgeSafe.get().equalsIgnoreCase("sneak")) {
            autoSneaking = false;
            return;
        }

        boolean shouldSneak = shouldAutoSneakAtEdge(input);
        autoSneaking = shouldSneak;

        if (shouldSneak)
            setInputSneak(input, true);
        else if (down.get() && isPhysicalKeyDown(mc.options.keyShift.saveString()))
            setInputSneak(input, false);
    }

    public static void hookAutoInputFix(ClientInput input) {
        Scaffold scaffold = ModuleManager.getModule(Scaffold.class);
        if (scaffold == null || !scaffold.tempEnabled)
            return;

        scaffold.handleAutoInputFix(input);
    }

    private void handleAutoInputFix(ClientInput clientInput) {
        var player = mc.player;
        var level = mc.level;
        if (clientInput == null || player == null || level == null)
            return;

        if (!edgeSafe.get().equalsIgnoreCase("auto input fix"))
            return;

        if (!player.onGround()
                || hotbarStackSize <= 0
                || player.getAbilities().flying
                || player.isPassenger()
                || player.isSwimming()
                || player.isFallFlying()
                || player.isSpectator())
            return;

        Input input = clientInput.keyPresses == null ? Input.EMPTY : clientInput.keyPresses;
        Vec3 position = player.position();
        double distance = autoInputFixDistance();
        if (!shouldApplyAutoInputFix(position, input, distance))
            return;

        InputUtil.applyInput(clientInput, stopAutoInputFixMovement(input));
    }

    private boolean shouldApplyAutoInputFix(Vec3 position, Input input, double distance) {
        if (isUnsupportedFallRisk(position))
            return true;

        if (isMovingInput(input) && isUnsupportedFallRisk(projectAutoInputFixPosition(position, input, distance)))
            return true;

        Vec3 motionPosition = projectAutoInputFixMotionPosition(position, distance);
        return !motionPosition.equals(position) && isUnsupportedFallRisk(motionPosition);
    }

    private boolean isUnsupportedFallRisk(Vec3 position) {
        return wouldBeCloseToFallOff(position) && !hasScaffoldSupportForPosition(position);
    }

    private Vec3 projectAutoInputFixPosition(Vec3 position, Input input, double distance) {
        Vec3 direction = getAutoInputFixDirection(input);
        if (direction.lengthSqr() <= 1.0E-6)
            return position;

        return position.add(direction.scale(distance));
    }

    private Vec3 projectAutoInputFixMotionPosition(Vec3 position, double distance) {
        var player = mc.player;
        if (player == null)
            return position;

        Vec3 motion = player.getDeltaMovement();
        double speed = MovementUtil.getSpeed(motion.x, motion.z);
        if (speed <= 1.0E-4)
            return position;

        Vec3 direction = MovementUtil.normalizeHorizontal(motion.x, motion.z);
        double motionDistance = Mth.clamp(speed + 0.1D, 0.25D, distance);
        return position.add(direction.scale(motionDistance));
    }

    private static Input stopAutoInputFixMovement(Input source) {
        return new Input(
                false,
                false,
                false,
                false,
                source != null && source.jump(),
                source != null && source.shift(),
                source != null && source.sprint()
        );
    }

    private boolean hasScaffoldSupportForPosition(Vec3 position) {
        var level = mc.level;
        if (level == null)
            return false;

        BlockPos targetBlock = scaffoldTargetBlockAt(position);
        if (!isScaffoldReplaceable(targetBlock))
            return true;

        PlacementTarget target = findPlacementTarget(targetBlock);
        return target != null && isRotationReadyForPlacement(target);
    }

    private BlockPos scaffoldTargetBlockAt(Vec3 position) {
        var player = mc.player;
        double targetY = launchY != null ? launchY : player != null ? player.blockPosition().getY() : position.y;
        return new BlockPos.MutableBlockPos(Mth.floor(position.x), scaffoldTargetBlockY(targetY), Mth.floor(position.z));
    }

    private static int scaffoldTargetBlockY(double y) {
        return Mth.floor(y - 0.75D);
    }

    private static double autoInputFixDistance() {
        var player = mc.player;
        if (player == null)
            return 0.45D;

        Vec3 motion = player.getDeltaMovement();
        double speed = MovementUtil.getSpeed(motion.x, motion.z);
        return Mth.clamp(Math.max(speed * 2.0D + 0.15D, player.isSprinting() ? 0.55D : 0.4D), 0.25D, 0.85D);
    }

    private static Vec3 getAutoInputFixDirection(Input input) {
        var player = mc.player;
        if (player == null || !isMovingInput(input))
            return Vec3.ZERO;

        float movementYaw = MovementFix.shouldFixMovement() ? MovementFix.getMovementYaw() : player.getYRot();
        return MovementUtil.getHorizontalDirectionVector(getMovementDirection(movementYaw, input));
    }

    private static void updateAutoSneak(boolean shouldSneak) {
        if (mc.player == null) {
            autoSneaking = false;
            return;
        }

        if (shouldSneak || autoSneaking) {
            boolean physicalSneak = isPhysicalKeyDown(mc.options.keyShift.saveString());
            setSneakDown(shouldSneak || physicalSneak && !down.get());
        }

        autoSneaking = shouldSneak;
    }

    private static void setSneakDown(boolean sneaking) {
        mc.options.keyShift.setDown(sneaking);

        var player = mc.player;
        if (player == null || player.input == null)
            return;

        setInputSneak(player.input, sneaking);
    }

    private static void setInputSneak(ClientInput clientInput, boolean sneaking) {
        if (clientInput == null)
            return;

        Input input = clientInput.keyPresses == null ? Input.EMPTY : clientInput.keyPresses;
        if (input.shift() == sneaking)
            return;

        InputUtil.applyInput(clientInput, new Input(
                input.forward(),
                input.backward(),
                input.left(),
                input.right(),
                input.jump(),
                sneaking,
                input.sprint()
        ));
    }

    private static boolean isMovingInput(Input input) {
        return input != null && (input.forward() != input.backward() || input.left() != input.right());
    }

    public static boolean isScaffoldBlock(Block block) {
        if (block.defaultBlockState().isAir()) return false;

        var item = block.asItem();
        return !(block instanceof ChestBlock || block instanceof WebBlock || block instanceof CakeBlock || block instanceof CandleCakeBlock || block instanceof BrewingStandBlock || block instanceof EnderChestBlock || block instanceof ShulkerBoxBlock || block instanceof FurnaceBlock
                || block instanceof CraftingTableBlock || block instanceof CrafterBlock || block instanceof SmokerBlock || block instanceof BlastFurnaceBlock
                || block instanceof CartographyTableBlock || block instanceof AnvilBlock || block instanceof BellBlock || block instanceof BeaconBlock
                || block instanceof DragonEggBlock || block instanceof LeverBlock || block instanceof EnchantingTableBlock || block instanceof ButtonBlock
                || block instanceof GrindstoneBlock || block instanceof LoomBlock || block instanceof NoteBlock || block instanceof FenceGateBlock
                || block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof StonecutterBlock || block instanceof StandingSignBlock
                || block instanceof WallSignBlock || block instanceof CeilingHangingSignBlock || block instanceof WallHangingSignBlock || block instanceof RepeaterBlock
                || block instanceof ComparatorBlock || block instanceof DispenserBlock || block instanceof JigsawBlock || block instanceof CommandBlock
                || block instanceof StructureBlock || block instanceof HopperBlock || block instanceof BedBlock || block instanceof FenceBlock || block instanceof SlabBlock || block instanceof PressurePlateBlock || block instanceof WallBlock || block instanceof StairBlock || block instanceof LadderBlock || block instanceof ChainBlock || block instanceof CarpetBlock || block instanceof BarrelBlock || block instanceof RailBlock || block instanceof PoweredRailBlock || block instanceof DetectorRailBlock || item instanceof PlayerHeadItem || block instanceof HugeMushroomBlock || item == Items.TORCH || item == Items.REDSTONE || item == Items.REDSTONE_TORCH || item == Items.STRING || item == Items.TRIPWIRE_HOOK || item == Items.DECORATED_POT || item == Items.MELON_SEEDS || item == Items.WHEAT_SEEDS || item == Items.PUMPKIN_SEEDS || item == Items.BEETROOT_SEEDS || item == Items.TORCHFLOWER_SEEDS || item == Items.IRON_BARS);
    }

    public static int countUsableHotbarBlocks(Container inventory) {
        if (inventory == null) return 0;

        int stackSize = 0;
        if (inventory.getContainerSize() > 40) {
            var offhandStack = inventory.getItem(40);
            if (isUsableScaffoldStack(offhandStack))
                stackSize += offhandStack.getCount();
        }

        for (int i = 0; i < Math.min(9, inventory.getContainerSize()); i++) {
            var stack = inventory.getItem(i);
            if (isUsableScaffoldStack(stack))
                stackSize += stack.getCount();
        }

        return stackSize;
    }

    private static boolean isUsableScaffoldStack(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) return false;
        return isScaffoldBlock(blockItem.getBlock());
    }

    private void countBlockItems(Container inventory) {
        hotbarStackSize = countUsableHotbarBlocks(inventory);
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        var player = mc.player;
        var level = mc.level;
        if (player == null || level == null) return;

        countBlockItems(player.getInventory());
        updateTellyClutchMode(player);
        updateTellyAfterPlaceNoHitCheckState(player);
        boolean updateTellyGroundKeepY = shouldUpdateTellyGroundKeepY(player);
        updateTellyKeepYGroundState(player);
        updateTellyJumpGroundInput(player);

        if (hotbarStackSize <= 0) {
            if (wasTowering) {
                switch (towerMode.get().toLowerCase(Locale.ROOT)) {
                    case "vanilla" -> {
                        if (hasAroundBlock())
                            MovementUtil.setVelocityY(-0.4);
                    }
                    case "hypixel" -> launchY = player.blockPosition().getY() - 1;
                }
            }
            jumpGround = 0.0;
            wasTowering = false;
            checkGround = false;
            wasTowering = false;
            if (once) {
                Client.rotationManager.stopRotation();
                TimerUtil.reset();
                BehaviorUtils.noKillAura = false;
                if (lastSlot != null) {
                    selectSlot(lastSlot);
                    PlayerUtil.INSTANCE.stopSpoof();
                    lastSlot = null;
                }
            }
            once = false;
            placedScaffoldBlock = false;
            lastPlacedScaffoldBlock = null;
            snapRotationResetPending = false;
            tellyAfterPlaceNoHitCheckActive = false;
            clearMathPlacementTarget();
            tellyStage = 0;
            tellyNormalJumpIndex = 0;
            suppressTellyNormalJumpSprint = false;
            tellyNormalJumpedThisTick = false;
            tellyJumpedThisTick = false;
            wasTellyKeepYOnGround = false;
            tellyJumpDownOnLastGround = false;
            tellyClutchMode = false;
            return;
        }

        updateTellyStage(player);

        once = true;
        boolean skipScaffoldActionThisTick = consumeSnapRotationReset();

        if (noSprint.get() && !isTellyJumpPriorityActive(player))
            player.setSprinting(false);

        boolean hypixelPlaced = false;

        if (customTimer.get())
            TimerUtil.timerSpeed = wasTowering ? towerTimerSpeed.get() : normalTimerSpeed.get();

        if (isTellyJumpPriorityActive(player) && wasTowering) {
            jumpGround = 0.0;
            wasTowering = false;
        }

        if (tower.get()
                && (!towerMode.get().equalsIgnoreCase("hypixel") || !player.hasEffect(MobEffects.JUMP_BOOST) && !ModuleManager.getModuleState(SpeedModule.class) || player.hasEffect(MobEffects.SPEED))
                && (towerWhen.get().equalsIgnoreCase("always") || towerWhen.get().equalsIgnoreCase("standing") && !MovementUtil.isMoving() || towerWhen.get().equalsIgnoreCase("moving") && MovementUtil.isMoving())) {
            if (isTowerJumpInputActive(player)) {
                if (!towerMode.get().equalsIgnoreCase("hypixel") || player.onGround())
                    wasTowering = true;
                if (hasAroundBlock() && wasTowering) {
                    if (towerWhen.get().equalsIgnoreCase("always") && !MovementUtil.isMoving() || towerWhen.get().equalsIgnoreCase("standing"))
                        MovementUtil.stopMoving();
                    if (towerCenter.get() && !MovementUtil.isMoving()) {
                        Vec3 center = Vec3.atCenterOf(player.blockPosition());
                        player.setPos(center.x, player.getY(), center.z);
                    }
                    switch (towerMode.get().toLowerCase(Locale.ROOT)) {
                        case "vanilla" -> {
                            if (MovementUtil.isMoving() && towerSpeed.get() >= 0.6)
                                MovementUtil.setVelocityY(0.5);
                            else MovementUtil.setVelocityY(towerSpeed.get().doubleValue());
                        }
                        case "ncp" -> {
                            if (player.onGround()) {
                                jumpGround = player.getY();
                                MovementUtil.setVelocityY(0.42);
                            }
                            if (player.getY() > jumpGround + 0.79f) {
                                player.setPos(player.getX(), Mth.floor(player.getY()), player.getZ());
                                MovementUtil.setVelocityY(0.42);
                                jumpGround = player.getY();
                            }
                        }
                        case "hypixel" -> {
                            if (MovementUtil.isMoving()) {
                                if (player.onGround())
                                    checkGround = true;
                                if (checkGround) {
                                    if (MovementUtil.fallTicks >= 18) {
                                        wasTowering = false;
                                        hypixelPlaced = true;
                                    } else {
                                        switch (MovementUtil.fallTicks % 3) {
                                            case 0 -> {
                                                var speedEffect = player.getEffect(MobEffects.SPEED);
                                                if (speedEffect != null)
                                                    MovementUtil.strafe(0.22f + ((speedEffect.getAmplifier() + 1) * (speedEffect.getAmplifier() == 0 ? 0.036f : 0.042f)));
                                                else MovementUtil.strafe(0.22f);
                                                MovementUtil.setVelocityY(0.42);
                                            }
                                            case 1 -> MovementUtil.setVelocityY(0.33);
                                            case 2 -> MovementUtil.setVelocityY((player.blockPosition().getY() + 1.0) - player.getY());
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (wasTowering) {
                    switch (towerMode.get().toLowerCase(Locale.ROOT)) {
                        case "vanilla" -> {
                            if (hasAroundBlock())
                                MovementUtil.setVelocityY(-0.4);
                        }
                        case "hypixel" -> launchY = player.blockPosition().getY() - 1;
                    }
                }
            } else if (wasTowering) {
                switch (towerMode.get().toLowerCase(Locale.ROOT)) {
                    case "vanilla" -> {
                        if (hasAroundBlock())
                            MovementUtil.setVelocityY(-0.4);
                    }
                    case "hypixel" -> launchY = player.blockPosition().getY() - 1;
                }
                jumpGround = 0.0;
                wasTowering = false;
            }
        } else if (wasTowering) {
            switch (towerMode.get().toLowerCase(Locale.ROOT)) {
                case "vanilla" -> {
                    if (hasAroundBlock())
                        MovementUtil.setVelocityY(-0.4);
                }
                case "hypixel" -> launchY = player.blockPosition().getY() - 1;
            }
            jumpGround = 0.0;
            wasTowering = false;
        }

        if (wasTowering)
            startedScaffold = true;

        if (updateTellyGroundKeepY) {
            launchY = player.blockPosition().getY();
        } else if (!shouldFreezeTellyKeepY(player)) {
            if (keepY.get()) {
                if (keepYOnlySpeed.get()) {
                    if ((!ModuleManager.getModuleState(SpeedModule.class) || isTellyUpwardJumpInputActive(player))
                            && (wasTowering || MovementUtil.fallTicks >= 4))
                        launchY = player.blockPosition().getY();
                }
            } else if (wasTowering || MovementUtil.fallTicks >= 4) launchY = player.blockPosition().getY();
        }

        if (launchY == null)
            launchY = player.blockPosition().getY();

        if (player.distanceToSqr(player.getX(), launchY, player.getZ()) > 15)
            launchY = player.blockPosition().getY();

        if (down.get() && isPhysicalKeyDown(mc.options.keyShift.saveString()) && !wasTowering)
            launchY = player.blockPosition().getY() - 1;

        BlockPos playerBlockPos = player.blockPosition();
        BlockPos targetBlock = new BlockPos.MutableBlockPos(playerBlockPos.getX(), scaffoldTargetBlockY(launchY), playerBlockPos.getZ());

        BehaviorUtils.noKillAura = true;

        boolean targetBlockReplaceable = isScaffoldReplaceable(targetBlock);
        PlacementTarget placementTarget = targetBlockReplaceable ? findPlacementTarget(targetBlock) : null;
        PlacementTarget preRotationTarget = !skipScaffoldActionThisTick && !isSnapRotationActive() && placementTarget == null ? findPreRotationTarget(targetBlock) : null;
        if (!skipScaffoldActionThisTick) {
            faceScaffoldRotation(targetBlock, placementTarget, preRotationTarget, targetBlockReplaceable);

            if (targetBlockReplaceable && rotationMode.get().equalsIgnoreCase("math")) {
                PlacementTarget rotationTarget = findRotationPriorityPlacementTarget(targetBlock);
                placementTarget = selectAdaptiveMathSearchTarget(targetBlock, placementTarget, rotationTarget);
                lockedMathPlacementTarget = placementTarget;
            }
        }

        PlayerUtil.INSTANCE.doSpoof(lastSlot);

        if (skipScaffoldActionThisTick)
            return;

        if (isTellyGroundPhase())
            return;

        if (!targetBlockReplaceable || placementTarget == null || !isRotationReadyForPlacement(placementTarget))
            return;

        startedScaffold = true;

        if (!hypixelPlaced) {
            placeBlock(placementTarget);
        }
    }

    private void placeBlock(PlacementTarget target) {
        var player = mc.player;
        if (player == null) return;
        Integer slot = findBlockSlot(player.getInventory());
        if (slot == null) return;

        selectSlot(slot);
        tryPlaceBlock(target, slot);
    }

    private PlacementTarget findPlacementTarget(BlockPos pos) {
        if (usesRayTracePlacementSearch())
            return findRayTracePlacementTarget(pos);

        clearMathPlacementTarget();
        for (BlockPos candidate : buildScaffoldCandidates(pos)) {
            PlacementTarget target = findPlacementTargetForCandidate(pos, candidate, scaffoldSearchDistance(pos, candidate));
            if (target != null)
                return target;
        }

        return null;
    }

    private PlacementTarget findRayTracePlacementTarget(BlockPos pos) {
        PlacementTarget rotationTarget = findRotationPriorityPlacementTarget(pos);
        PlacementTarget lockedTarget = reusableMathPlacementTarget(pos);
        PlacementTarget nearestTarget = null;
        double bestScore = Double.MAX_VALUE;

        for (BlockPos candidate : buildScaffoldCandidates(pos)) {
            int searchDistance = scaffoldSearchDistance(pos, candidate);
            if (nearestTarget != null && searchDistance > nearestTarget.searchDistance())
                break;

            PlacementTarget target = findRayTracePlacementTargetForCandidate(pos, candidate, searchDistance);
            if (target == null)
                continue;

            double score = placementTargetScore(pos, target);
            if (score < bestScore) {
                nearestTarget = target;
                bestScore = score;
            }
        }

        PlacementTarget positionTarget = selectMathPositionTarget(pos, lockedTarget, nearestTarget);
        PlacementTarget selectedTarget = selectAdaptiveMathSearchTarget(pos, positionTarget, rotationTarget);
        lockedMathPlacementTarget = selectedTarget;
        return selectedTarget;
    }

    private PlacementTarget findRotationPriorityPlacementTarget(BlockPos searchOrigin) {
        var level = mc.level;
        var player = mc.player;
        if (searchOrigin == null || level == null || player == null || !rotationMode.get().equalsIgnoreCase("math"))
            return null;

        BlockHitResult hit = Client.rotationManager.rayTraceBlocks(
                Client.rotationManager.getRotationYaw(),
                Client.rotationManager.getRotationPitch(),
                placementReach()
        );
        if (hit.getType() != HitResult.Type.BLOCK)
            return null;

        BlockPos neighbour = hit.getBlockPos();
        Direction sideToClick = hit.getDirection();
        BlockPos placementPos = neighbour.relative(sideToClick);
        if (placementPos.getY() != searchOrigin.getY())
            return null;

        int searchDistance = scaffoldSearchDistance(searchOrigin, placementPos);
        if (searchDistance > currentSearchRange()
                || !isScaffoldReplaceable(placementPos)
                || !isPlacementPositionClearForPlayer(placementPos)
                || !isScaffoldSupport(level.getBlockState(neighbour)))
            return null;

        Vec3 hitPos = hit.getLocation();
        if (player.getEyePosition().distanceToSqr(hitPos) > placementReachSqr() + 1.0E-4D)
            return null;

        return new PlacementTarget(placementPos, neighbour, hitPos, sideToClick, searchDistance);
    }

    private PlacementTarget reusableMathPlacementTarget(BlockPos currentTargetBlock) {
        PlacementTarget target = lockedMathPlacementTarget;
        var level = mc.level;
        var player = mc.player;
        if (target == null || currentTargetBlock == null || level == null || player == null)
            return null;
        if (!rotationMode.get().equalsIgnoreCase("math"))
            return null;
        if (target.pos().getY() != currentTargetBlock.getY())
            return null;

        int searchDistance = scaffoldSearchDistance(currentTargetBlock, target.pos());
        if (searchDistance > currentSearchRange())
            return null;
        if (!isScaffoldReplaceable(target.pos()))
            return null;

        var neighbourState = level.getBlockState(target.neighbour());
        if (!isScaffoldSupport(neighbourState))
            return null;
        if (player.getEyePosition().distanceToSqr(target.hitPos()) > placementReachSqr() + 1.0E-4D)
            return null;

        PlacementTarget updatedTarget = target.searchDistance() == searchDistance
                ? target
                : new PlacementTarget(target.pos(), target.neighbour(), target.hitPos(), target.side(), searchDistance);
        return isIdealPlacementHitReachable(updatedTarget) ? updatedTarget : null;
    }

    private PlacementTarget selectMathPositionTarget(BlockPos currentTargetBlock, PlacementTarget lockedTarget, PlacementTarget bestTarget) {
        if (lockedTarget == null)
            return bestTarget;
        if (bestTarget == null)
            return lockedTarget;
        if (samePlacementTarget(lockedTarget, bestTarget))
            return preferMovementAlignedTarget(lockedTarget, bestTarget);

        if (lockedTarget.searchDistance() != bestTarget.searchDistance())
            return bestTarget.searchDistance() < lockedTarget.searchDistance() ? bestTarget : lockedTarget;

        boolean lockedReady = currentPlacementHit(lockedTarget) != null;
        boolean bestReady = currentPlacementHit(bestTarget) != null;
        if (lockedReady && !bestReady)
            return lockedTarget;
        if (!lockedReady && bestReady)
            return bestTarget;

        double lockedScore = placementTargetScore(currentTargetBlock, lockedTarget);
        double bestScore = placementTargetScore(currentTargetBlock, bestTarget);
        return bestScore + 10.0D < lockedScore ? bestTarget : lockedTarget;
    }

    private PlacementTarget selectAdaptiveMathSearchTarget(BlockPos searchOrigin, PlacementTarget positionTarget, PlacementTarget rotationTarget) {
        if (positionTarget == null)
            return rotationTarget;
        if (rotationTarget == null)
            return positionTarget;
        if (samePlacementTarget(positionTarget, rotationTarget))
            return preferMovementAlignedTarget(rotationTarget, positionTarget);

        int distanceGap = rotationTarget.searchDistance() - positionTarget.searchDistance();
        if (distanceGap <= 0)
            return rotationTarget;

        if (!isStablePlacementHit(rotationTarget.hitPos(), rotationTarget.neighbour(), rotationTarget.side()))
            return positionTarget;

        double positionRotationTicks = placementRotationTicks(positionTarget);
        double rotationTargetTicks = placementRotationTicks(rotationTarget);
        double preRotationTicks = availablePreRotationTicks(searchOrigin, positionTarget);
        double rotationTimeSaving = Math.max(0.0D, positionRotationTicks - preRotationTicks)
                - rotationTargetTicks;

        double requiredRotationSavingTicks = MATH_SEARCH_BASE_ROTATION_SAVING_TICKS
                + distanceGap * MATH_SEARCH_DISTANCE_SAVING_TICKS
                + distanceGap * distanceGap * MATH_SEARCH_DISTANCE_SQUARED_SAVING_TICKS;

        if (positionTarget.searchDistance() == 0)
            requiredRotationSavingTicks += MATH_SEARCH_ORIGIN_PRIORITY_TICKS;

        requiredRotationSavingTicks += mathSearchPathPenaltyTicks(searchOrigin, rotationTarget.pos(), distanceGap);

        double playerDistanceLoss = horizontalDistanceToBlock(rotationTarget.pos())
                - horizontalDistanceToBlock(positionTarget.pos());
        if (playerDistanceLoss > 0.0D)
            requiredRotationSavingTicks += playerDistanceLoss * MATH_SEARCH_PLAYER_DISTANCE_WEIGHT_TICKS;

        var player = mc.player;
        if (player != null && !player.onGround() && player.getDeltaMovement().y < 0.0D)
            requiredRotationSavingTicks += distanceGap * MATH_SEARCH_PATH_WEIGHT_TICKS;

        return rotationTimeSaving >= requiredRotationSavingTicks ? rotationTarget : positionTarget;
    }

    private PlacementTarget preferMovementAlignedTarget(PlacementTarget currentTarget, PlacementTarget candidateTarget) {
        Float movementYaw = movementFixMathInputYaw();
        if (movementYaw == null)
            return currentTarget;

        float currentError = mathMovementAlignmentError(rotationsToHitPos(currentTarget.hitPos())[0], movementYaw);
        float candidateError = mathMovementAlignmentError(rotationsToHitPos(candidateTarget.hitPos())[0], movementYaw);
        return candidateError + MATH_MOVEMENT_ALIGNMENT_EPSILON < currentError ? candidateTarget : currentTarget;
    }

    private double mathSearchPathPenaltyTicks(BlockPos origin, BlockPos candidate, int distanceGap) {
        if (origin == null || candidate == null || distanceGap <= 0)
            return 0.0D;

        Vec3 direction = getPreRotationDirection();
        double directionLength = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        if (directionLength <= 1.0E-6D)
            return distanceGap * MATH_SEARCH_PATH_WEIGHT_TICKS;

        double offsetX = candidate.getX() - origin.getX();
        double offsetZ = candidate.getZ() - origin.getZ();
        double offsetLength = Math.sqrt(offsetX * offsetX + offsetZ * offsetZ);
        if (offsetLength <= 1.0E-6D)
            return 0.0D;

        double alignment = (offsetX * direction.x + offsetZ * direction.z) / (offsetLength * directionLength);
        return (1.0D - Mth.clamp(alignment, -1.0D, 1.0D))
                * distanceGap
                * MATH_SEARCH_PATH_WEIGHT_TICKS;
    }

    private double placementRotationTicks(PlacementTarget target) {
        float[] rotations = rotationsToHitPos(target.hitPos());
        float yawDiff = Math.abs(Mth.wrapDegrees(rotations[0] - Client.rotationManager.getRotationYaw()));
        float pitchDiff = Math.abs(rotations[1] - Client.rotationManager.getRotationPitch());
        double yawTicks = rotationTicks(yawDiff, expectedMathYawSpeed());
        double pitchTicks = rotationTicks(pitchDiff, expectedMathPitchSpeed());
        double movementAlignmentTicks = mathMovementAlignmentScore(rotations[0])
                / Math.max(1.0D, expectedMathYawSpeed());
        return Math.max(yawTicks, pitchTicks) + movementAlignmentTicks;
    }

    private double availablePreRotationTicks(BlockPos searchOrigin, PlacementTarget target) {
        if (!usesMathPreRotation() || searchOrigin == null || target == null || target.pos().equals(searchOrigin))
            return 0.0D;

        var player = mc.player;
        if (player == null)
            return 0.0D;

        double horizontalSpeed = MovementUtil.getSpeed(
                player.getDeltaMovement().x,
                player.getDeltaMovement().z
        );
        if (horizontalSpeed <= 1.0E-4D)
            return 0.0D;

        Vec3 direction = getPreRotationDirection();
        double directionLength = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        double offsetX = target.pos().getX() - searchOrigin.getX();
        double offsetZ = target.pos().getZ() - searchOrigin.getZ();
        double offsetLength = Math.sqrt(offsetX * offsetX + offsetZ * offsetZ);
        if (directionLength <= 1.0E-6D || offsetLength <= 1.0E-6D)
            return 0.0D;

        double alignment = (offsetX * direction.x + offsetZ * direction.z) / (offsetLength * directionLength);
        if (alignment <= 0.0D)
            return 0.0D;

        double leadDistance = mathPreRotationLeadDistance(horizontalSpeed);
        return Mth.clamp(leadDistance / horizontalSpeed, 0.0D, 8.0D)
                * Mth.clamp(alignment, 0.0D, 1.0D);
    }

    private static double rotationTicks(float angleDifference, double degreesPerTick) {
        if (angleDifference <= 1.0E-3F)
            return 0.0D;
        if (degreesPerTick <= 1.0E-3D)
            return Double.POSITIVE_INFINITY;
        return angleDifference / degreesPerTick;
    }

    private static double expectedMathYawSpeed() {
        double min = Math.min(minRotationSpeed.get(), maxRotationSpeed.get());
        double max = Math.max(minRotationSpeed.get(), maxRotationSpeed.get());
        return (min + max) * 0.5D;
    }

    private static double expectedMathPitchSpeed() {
        double min = Math.min(minRotationSpeed.get(), maxRotationSpeed.get());
        double max = Math.max(minRotationSpeed.get(), maxRotationSpeed.get());
        double safeMax = Math.min(max, 18.0D);
        if (safeMax <= 0.0D)
            return 0.0D;

        double safeMin = min > 18.0D
                ? Math.min(12.0D, safeMax)
                : Math.min(min, safeMax);
        safeMin = Math.max(Math.min(1.0D, safeMax), safeMin);
        return (safeMin + safeMax) * 0.5D;
    }

    private static double horizontalDistanceToBlock(BlockPos pos) {
        var player = mc.player;
        if (player == null || pos == null)
            return 0.0D;

        double xDistance = Math.max(Math.max(pos.getX() - player.getX(), player.getX() - (pos.getX() + 1.0D)), 0.0D);
        double zDistance = Math.max(Math.max(pos.getZ() - player.getZ(), player.getZ() - (pos.getZ() + 1.0D)), 0.0D);
        return Math.sqrt(xDistance * xDistance + zDistance * zDistance);
    }

    private boolean isIdealPlacementHitReachable(PlacementTarget target) {
        float[] rotations = rotationsToHitPos(target.hitPos());
        BlockHitResult hit = Client.rotationManager.rayTraceBlocks(rotations[0], rotations[1], placementReach());
        return isMatchingPlacementHit(hit, target);
    }

    private boolean shouldConserveMathTellyBlocks() {
        return rotationMode.get().equalsIgnoreCase("math")
                && isTellyMode()
                && !shouldUseNormalScaffoldForTelly(mc.player)
                && !isDownScaffoldActive();
    }

    private double mathTellyConservationScore(BlockPos origin, PlacementTarget target) {
        if (!shouldConserveMathTellyBlocks() || origin == null || target == null || target.pos().equals(origin))
            return 0.0D;

        double alignment = mathTellyFallbackAlignment(origin, target.pos());
        double directionPenalty = (1.0D - Mth.clamp(alignment, -1.0D, 1.0D)) * 8.0D;
        return target.searchDistance() * 3.0D + directionPenalty;
    }

    private double mathTellyFallbackAlignment(BlockPos origin, BlockPos candidate) {
        Vec3 direction = getPreRotationDirection();
        double directionLength = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        if (directionLength <= 1.0E-6D)
            return -1.0D;

        double offsetX = candidate.getX() - origin.getX();
        double offsetZ = candidate.getZ() - origin.getZ();
        double offsetLength = Math.sqrt(offsetX * offsetX + offsetZ * offsetZ);
        if (offsetLength <= 1.0E-6D)
            return 1.0D;

        return (offsetX * direction.x + offsetZ * direction.z) / (offsetLength * directionLength);
    }

    private static boolean samePlacementTarget(PlacementTarget first, PlacementTarget second) {
        return first != null
                && second != null
                && first.pos().equals(second.pos())
                && first.neighbour().equals(second.neighbour())
                && first.side() == second.side();
    }

    private void clearMathPlacementTarget() {
        lockedMathPlacementTarget = null;
    }

    private List<BlockPos> buildScaffoldCandidates(BlockPos pos) {
        scaffoldCandidates.clear();
        scaffoldCandidateSet.clear();
        addScaffoldCandidate(pos);

        int range = currentSearchRange();
        float searchYaw = scaffoldSearchYaw();
        Direction[] searchDirections = orderedHorizontalDirections(searchYaw);
        if (range >= 1) {
            for (Direction side : searchDirections)
                addScaffoldCandidate(pos.relative(side));
        }

        if (range >= 2) {
            for (Direction side : searchDirections) {
                for (Direction side2 : searchDirections) {
                    if (side2 == side.getOpposite()) continue;
                    addScaffoldCandidate(pos.relative(side).relative(side2));
                }
            }
        }

        if (range > 2) {
            for (int distance = 3; distance <= range; distance++)
                addScaffoldCandidatesAtDistance(pos, distance, searchYaw);
        }

        return scaffoldCandidates;
    }

    private void addScaffoldCandidatesAtDistance(BlockPos pos, int distance, float searchYaw) {
        scaffoldDistanceCandidates.clear();

        for (int x = -distance; x <= distance; x++) {
            for (int z = -distance; z <= distance; z++) {
                if (Math.abs(x) + Math.abs(z) != distance) continue;
                scaffoldDistanceCandidates.add(pos.offset(x, 0, z));
            }
        }

        scaffoldDistanceCandidates.sort((first, second) -> compareScaffoldCandidateDirection(pos, first, second, searchYaw));

        for (BlockPos candidate : scaffoldDistanceCandidates)
            addScaffoldCandidate(candidate);
    }

    private Direction[] orderedHorizontalDirections(float searchYaw) {
        Direction[] horizontalDirections = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        System.arraycopy(horizontalDirections, 0, orderedHorizontalDirections, 0, horizontalDirections.length);

        for (int i = 1; i < orderedHorizontalDirections.length; i++) {
            Direction direction = orderedHorizontalDirections[i];
            float directionScore = horizontalDirectionYawDistance(direction, searchYaw);
            int j = i - 1;

            while (j >= 0 && horizontalDirectionYawDistance(orderedHorizontalDirections[j], searchYaw) > directionScore) {
                orderedHorizontalDirections[j + 1] = orderedHorizontalDirections[j];
                j--;
            }

            orderedHorizontalDirections[j + 1] = direction;
        }

        return orderedHorizontalDirections;
    }

    private Direction[] orderedPlaceDirections(BlockPos searchOrigin, BlockPos candidate) {
        Direction preferredBridgeDirection = lastPlacedBridgeSupportDirection(searchOrigin, candidate);
        Direction[] horizontalDirections = orderedHorizontalDirections(scaffoldSearchYaw());
        int index = 0;

        if (preferredBridgeDirection != null)
            orderedPlaceDirections[index++] = preferredBridgeDirection;

        for (Direction direction : horizontalDirections) {
            if (direction == preferredBridgeDirection)
                continue;
            orderedPlaceDirections[index++] = direction;
        }

        orderedPlaceDirections[index++] = Direction.DOWN;
        orderedPlaceDirections[index] = Direction.UP;

        return orderedPlaceDirections;
    }

    private float scaffoldSearchYaw() {
        Float inputYaw = rotationMode.get().equalsIgnoreCase("math") && !isTellyMode()
                ? scaffoldInputDirection()
                : null;
        float movementYaw = inputYaw != null ? inputYaw : MovementUtil.getMotionDirection();
        return normalizeYaw(movementYaw + 180.0F);
    }

    private static float normalizeYaw(float yaw) {
        return (yaw % 360.0F + 360.0F) % 360.0F;
    }

    private static int compareScaffoldCandidateDirection(BlockPos origin, BlockPos first, BlockPos second, float searchYaw) {
        int yawCompare = Float.compare(scaffoldCandidateYawDistance(origin, first, searchYaw), scaffoldCandidateYawDistance(origin, second, searchYaw));
        if (yawCompare != 0)
            return yawCompare;

        int xCompare = Integer.compare(Math.abs(first.getX() - origin.getX()), Math.abs(second.getX() - origin.getX()));
        if (xCompare != 0)
            return xCompare;

        return Integer.compare(Math.abs(first.getZ() - origin.getZ()), Math.abs(second.getZ() - origin.getZ()));
    }

    private static float scaffoldCandidateYawDistance(BlockPos origin, BlockPos candidate, float searchYaw) {
        int x = candidate.getX() - origin.getX();
        int z = candidate.getZ() - origin.getZ();
        if (x == 0 && z == 0)
            return 0.0F;

        float yaw = blockOffsetYaw(x, z);
        return Math.abs(Mth.wrapDegrees(yaw - searchYaw));
    }

    private static float blockOffsetYaw(int x, int z) {
        return (float) ((Math.toDegrees(Math.atan2(-x, z)) + 360.0D) % 360.0D);
    }

    private Direction lastPlacedBridgeSupportDirection(BlockPos searchOrigin, BlockPos candidate) {
        BlockPos lastPlaced = usableLastPlacedScaffoldBlock(searchOrigin);
        if (lastPlaced == null || candidate == null || lastPlaced.getY() != candidate.getY())
            return null;

        return horizontalDirectionBetween(candidate, lastPlaced);
    }

    private double lastPlacedBridgePlacementScore(BlockPos searchOrigin, BlockPos candidate, BlockPos neighbour) {
        BlockPos lastPlaced = usableLastPlacedScaffoldBlock(searchOrigin);
        if (lastPlaced == null || candidate == null || neighbour == null)
            return 0.0D;

        double score = lastPlacedBridgePathPenalty(searchOrigin, candidate, lastPlaced);
        if (neighbour.equals(lastPlaced))
            score -= 1.0D;

        return score;
    }

    private double lastPlacedBridgePathPenalty(BlockPos origin, BlockPos candidate, BlockPos lastPlaced) {
        int bridgeDistance = scaffoldSearchDistance(lastPlaced, origin);
        if (bridgeDistance <= 1)
            return 0.0D;

        int distanceFromLastPlaced = scaffoldSearchDistance(lastPlaced, candidate);
        int detour = scaffoldSearchDistance(lastPlaced, candidate) + scaffoldSearchDistance(candidate, origin) - bridgeDistance;
        return (Math.max(0, distanceFromLastPlaced - 1) + Math.max(0, detour)) * 0.25D;
    }

    private BlockPos usableLastPlacedScaffoldBlock(BlockPos origin) {
        var level = mc.level;
        if (origin == null || lastPlacedScaffoldBlock == null || level == null)
            return null;
        if (lastPlacedScaffoldBlock.getY() != origin.getY())
            return null;
        if (scaffoldSearchDistance(origin, lastPlacedScaffoldBlock) > currentSearchRange() + 1)
            return null;

        return isScaffoldSupport(level.getBlockState(lastPlacedScaffoldBlock)) ? lastPlacedScaffoldBlock : null;
    }

    private static Direction horizontalDirectionBetween(BlockPos from, BlockPos to) {
        if (from == null || to == null || from.getY() != to.getY())
            return null;

        int x = to.getX() - from.getX();
        int z = to.getZ() - from.getZ();
        if (Math.abs(x) + Math.abs(z) != 1)
            return null;

        if (x > 0) return Direction.EAST;
        if (x < 0) return Direction.WEST;
        if (z > 0) return Direction.SOUTH;
        return Direction.NORTH;
    }

    private static float horizontalDirectionYawDistance(Direction direction, float searchYaw) {
        return Math.abs(Mth.wrapDegrees(horizontalDirectionYaw(direction) - searchYaw));
    }

    private static float horizontalDirectionYaw(Direction direction) {
        return switch (direction) {
            case SOUTH -> 0.0F;
            case WEST -> 90.0F;
            case NORTH -> 180.0F;
            case EAST -> 270.0F;
            default -> 0.0F;
        };
    }

    private void addScaffoldCandidate(BlockPos pos) {
        if (scaffoldCandidateSet.add(pos))
            scaffoldCandidates.add(pos);
    }

    private int currentSearchRange() {
        if (down.get() && isPhysicalKeyDown(mc.options.keyShift.saveString()))
            return 1;
        if (isPhysicalKeyDown(mc.options.keyJump.saveString()) && !MovementUtil.hasXZMotion() && MovementUtil.isBlockBelow())
            return 0;
        return searchRange.get();
    }

    private static int scaffoldSearchDistance(BlockPos origin, BlockPos candidate) {
        return Math.abs(candidate.getX() - origin.getX()) + Math.abs(candidate.getZ() - origin.getZ());
    }

    private static boolean isScaffoldReplaceable(BlockPos pos) {
        var level = mc.level;
        return level != null && isScaffoldReplaceable(level.getBlockState(pos));
    }

    private static boolean isScaffoldReplaceable(BlockState state) {
        return state.canBeReplaced();
    }

    private static boolean isScaffoldSupport(BlockState state) {
        return !state.isAir() && !state.canBeReplaced() && state.getFluidState().isEmpty();
    }

    private PlacementTarget findPlacementTargetForCandidate(BlockPos searchOrigin, BlockPos pos, int searchDistance) {
        var level = mc.level;
        var player = mc.player;
        if (level == null || player == null) return null;

        if (!isScaffoldReplaceable(pos)) return null;
        if (!isPlacementPositionClearForPlayer(pos)) return null;

        Vec3 eyesPos = player.getEyePosition();
        Vec3 targetCenter = Vec3.atCenterOf(pos);

        for (Direction side : orderedPlaceDirections(searchOrigin, pos)) {
            BlockPos neighbour = pos.relative(side);
            Direction sideToClick = side.getOpposite();
            var neighbourState = level.getBlockState(neighbour);

            if (!isScaffoldSupport(neighbourState)) continue;

            Vec3 neighbourCenter = Vec3.atCenterOf(neighbour);

            if (!isDownwardsPlacementSupport(side) && eyesPos.distanceToSqr(targetCenter) >= eyesPos.distanceToSqr(neighbourCenter))
                continue;

            Vec3 hitPos = neighbourCenter.add(
                    sideToClick.getStepX() * 0.5,
                    sideToClick.getStepY() * 0.5,
                    sideToClick.getStepZ() * 0.5
            );

            PlacementTarget target = new PlacementTarget(pos, neighbour, hitPos, sideToClick, searchDistance);
            if (shouldSkipBackwardsPlacementTarget(target))
                continue;

            boolean centerInReach = eyesPos.distanceToSqr(hitPos) <= placementReachSqr();
            if (isNoHitCheckActive(target) || isRotationTargetHitCheckMode(target)) {
                if (centerInReach)
                    return target;
                continue;
            }

            PlacementHit rayTraceHit = findRayTraceablePlacementHit(neighbour, sideToClick);
            if (rayTraceHit != null)
                return new PlacementTarget(pos, neighbour, rayTraceHit.hitPos(), sideToClick, searchDistance);
        }

        return null;
    }

    private PlacementTarget findRayTracePlacementTargetForCandidate(BlockPos searchOrigin, BlockPos pos, int searchDistance) {
        var level = mc.level;
        var player = mc.player;
        if (level == null || player == null) return null;

        if (!isScaffoldReplaceable(pos)) return null;
        if (!isPlacementPositionClearForPlayer(pos)) return null;

        Vec3 eyesPos = player.getEyePosition();
        Vec3 targetCenter = Vec3.atCenterOf(pos);
        PlacementTarget bestTarget = null;
        double bestScore = Double.MAX_VALUE;

        for (Direction side : orderedPlaceDirections(searchOrigin, pos)) {
            BlockPos neighbour = pos.relative(side);
            Direction sideToClick = side.getOpposite();
            var neighbourState = level.getBlockState(neighbour);

            if (!isScaffoldSupport(neighbourState)) continue;

            Vec3 neighbourCenter = Vec3.atCenterOf(neighbour);
            PlacementHit rayTraceHit = findRayTraceablePlacementHit(neighbour, sideToClick);
            if (rayTraceHit == null)
                continue;

            double score = rayTraceHit.rotationScore()
                    + rayTraceHit.distanceSqr() * 0.01D
                    + Math.abs(eyesPos.distanceToSqr(targetCenter) - eyesPos.distanceToSqr(neighbourCenter)) * 0.001D
                    + placementSideScore(sideToClick)
                    + lastPlacedBridgePlacementScore(searchOrigin, pos, neighbour);
            if (score < bestScore) {
                bestTarget = new PlacementTarget(pos, neighbour, rayTraceHit.hitPos(), sideToClick, searchDistance);
                bestScore = score;
            }
        }

        return bestTarget;
    }

    private PlacementHit findRayTraceablePlacementHit(BlockPos neighbour, Direction sideToClick) {
        var player = mc.player;
        if (player == null || neighbour == null || sideToClick == null)
            return null;

        Vec3 eyesPos = player.getEyePosition();
        Vec3 faceCenter = placementFaceCenter(neighbour, sideToClick);
        double reach = placementReach();
        double reachSqr = reach * reach;
        float currentYaw = Client.rotationManager.getRotationYaw();
        float currentPitch = Client.rotationManager.getRotationPitch();
        float[] centerRotations = rotationsToHitPos(faceCenter);
        BlockHitResult currentHit = Client.rotationManager.rayTraceBlocks(currentYaw, currentPitch, reach);
        PlacementHit currentPlacementHit = null;
        Vec3 bestHitPos = null;
        double bestDistanceSqr = 0.0D;
        double bestScore = Double.MAX_VALUE;
        if (isMatchingPlacementHit(currentHit, neighbour, sideToClick)) {
            Vec3 hitPos = currentHit.getLocation();
            currentPlacementHit = new PlacementHit(hitPos, 0.0D, eyesPos.distanceToSqr(hitPos));
            if (isStablePlacementHit(hitPos, neighbour, sideToClick)) {
                bestHitPos = hitPos;
                bestDistanceSqr = currentPlacementHit.distanceSqr();
                bestScore = smartPlacementHitScore(
                        hitPos,
                        new float[]{currentYaw, currentPitch},
                        currentYaw,
                        currentPitch,
                        eyesPos,
                        faceCenter,
                        sideToClick,
                        true
                );
            }
        }

        PlacementHit movementAlignedHit = findMovementAlignedPlacementHit(
                neighbour,
                sideToClick,
                eyesPos,
                faceCenter,
                reach,
                currentYaw,
                currentPitch,
                currentPlacementHit != null
        );
        if (movementAlignedHit != null && movementAlignedHit.rotationScore() < bestScore) {
            bestHitPos = movementAlignedHit.hitPos();
            bestDistanceSqr = movementAlignedHit.distanceSqr();
            bestScore = movementAlignedHit.rotationScore();
        }

        BlockHitResult centerHit = Client.rotationManager.rayTraceBlocks(centerRotations[0], centerRotations[1], reach);
        if (isMatchingPlacementHit(centerHit, neighbour, sideToClick)) {
            Vec3 hitPos = centerHit.getLocation();
            double score = smartPlacementHitScore(hitPos, centerRotations, currentYaw, currentPitch, eyesPos, faceCenter, sideToClick, currentPlacementHit != null);
            if (score < bestScore) {
                bestHitPos = hitPos;
                bestDistanceSqr = eyesPos.distanceToSqr(hitPos);
                bestScore = score;
            }
        }

        int xCount = fixedOffsetCount(sideToClick, Direction.EAST, Direction.WEST);
        int yCount = fixedOffsetCount(sideToClick, Direction.UP, Direction.DOWN);
        int zCount = fixedOffsetCount(sideToClick, Direction.SOUTH, Direction.NORTH);
        for (int xIndex = 0; xIndex < xCount; xIndex++) {
            double dx = placementOffset(sideToClick, Direction.EAST, Direction.WEST, xIndex);
            for (int yIndex = 0; yIndex < yCount; yIndex++) {
                double dy = placementOffset(sideToClick, Direction.UP, Direction.DOWN, yIndex);
                for (int zIndex = 0; zIndex < zCount; zIndex++) {
                    double dz = placementOffset(sideToClick, Direction.SOUTH, Direction.NORTH, zIndex);
                    Vec3 hitPos = new Vec3(neighbour.getX() + dx, neighbour.getY() + dy, neighbour.getZ() + dz);
                    double distanceSqr = eyesPos.distanceToSqr(hitPos);
                    if (distanceSqr > reachSqr)
                        continue;

                    float[] rotations = rotationsToHitPos(hitPos);
                    BlockHitResult hit = Client.rotationManager.rayTraceBlocks(rotations[0], rotations[1], reach);
                    if (!isMatchingPlacementHit(hit, neighbour, sideToClick))
                        continue;

                    Vec3 actualHitPos = hit.getLocation();
                    double score = smartPlacementHitScore(actualHitPos, rotations, currentYaw, currentPitch, eyesPos, faceCenter, sideToClick, currentPlacementHit != null);
                    if (score < bestScore) {
                        bestScore = score;
                        bestDistanceSqr = eyesPos.distanceToSqr(actualHitPos);
                        bestHitPos = actualHitPos;
                    }
                }
            }
        }

        if (bestHitPos != null)
            return new PlacementHit(bestHitPos, bestScore, bestDistanceSqr);

        return currentPlacementHit;
    }

    private PlacementHit findMovementAlignedPlacementHit(BlockPos neighbour, Direction sideToClick, Vec3 eyesPos, Vec3 faceCenter,
                                                          double reach, float currentYaw, float currentPitch, boolean currentCanHit) {
        Float movementYaw = movementFixMathInputYaw();
        if (movementYaw == null)
            return null;

        PlacementHit bestHit = null;
        double bestScore = Double.MAX_VALUE;
        double reachSqr = reach * reach;
        for (int sector = 0; sector < 8; sector++) {
            float alignedYaw = Mth.wrapDegrees(movementYaw + sector * MATH_MOVEMENT_SECTOR_DEGREES);
            Vec3 targetHitPos = movementAlignedHitPos(neighbour, sideToClick, eyesPos, faceCenter, alignedYaw, currentPitch);
            if (targetHitPos == null || eyesPos.distanceToSqr(targetHitPos) > reachSqr)
                continue;

            float alignedPitch = rotationsToHitPos(targetHitPos)[1];
            BlockHitResult hit = Client.rotationManager.rayTraceBlocks(alignedYaw, alignedPitch, reach);
            if (!isMatchingPlacementHit(hit, neighbour, sideToClick))
                continue;

            Vec3 actualHitPos = hit.getLocation();
            if (!isStablePlacementHit(actualHitPos, faceCenter, sideToClick))
                continue;

            double distanceSqr = eyesPos.distanceToSqr(actualHitPos);
            double score = smartPlacementHitScore(
                    actualHitPos,
                    new float[]{alignedYaw, alignedPitch},
                    currentYaw,
                    currentPitch,
                    eyesPos,
                    faceCenter,
                    sideToClick,
                    currentCanHit
            );
            if (score < bestScore) {
                bestHit = new PlacementHit(actualHitPos, score, distanceSqr);
                bestScore = score;
            }
        }

        return bestHit;
    }

    private static Vec3 movementAlignedHitPos(BlockPos neighbour, Direction sideToClick, Vec3 eyesPos, Vec3 faceCenter,
                                              float yaw, float currentPitch) {
        double yawRadians = Math.toRadians(yaw);
        double directionX = -Math.sin(yawRadians);
        double directionZ = Math.cos(yawRadians);
        double safeInset = MATH_SAFE_HIT_INSET + MATH_ALIGNED_RAY_EPSILON;
        double minX = neighbour.getX() + safeInset;
        double maxX = neighbour.getX() + 1.0D - safeInset;
        double minY = neighbour.getY() + safeInset;
        double maxY = neighbour.getY() + 1.0D - safeInset;
        double minZ = neighbour.getZ() + safeInset;
        double maxZ = neighbour.getZ() + 1.0D - safeInset;
        double pitchTangent = Math.tan(Math.toRadians(currentPitch));

        if (sideToClick.getAxis() == Direction.Axis.X) {
            if (Math.abs(directionX) <= MATH_ALIGNED_RAY_EPSILON)
                return null;

            double planeX = sideToClick == Direction.EAST ? neighbour.getX() + 1.0D : neighbour.getX();
            double distance = (planeX - eyesPos.x) / directionX;
            if (distance <= MATH_ALIGNED_RAY_EPSILON)
                return null;

            double hitZ = eyesPos.z + directionZ * distance;
            if (hitZ < minZ || hitZ > maxZ)
                return null;

            double hitY = Mth.clamp(eyesPos.y - pitchTangent * distance, minY, maxY);
            return new Vec3(planeX, hitY, hitZ);
        }

        if (sideToClick.getAxis() == Direction.Axis.Z) {
            if (Math.abs(directionZ) <= MATH_ALIGNED_RAY_EPSILON)
                return null;

            double planeZ = sideToClick == Direction.SOUTH ? neighbour.getZ() + 1.0D : neighbour.getZ();
            double distance = (planeZ - eyesPos.z) / directionZ;
            if (distance <= MATH_ALIGNED_RAY_EPSILON)
                return null;

            double hitX = eyesPos.x + directionX * distance;
            if (hitX < minX || hitX > maxX)
                return null;

            double hitY = Mth.clamp(eyesPos.y - pitchTangent * distance, minY, maxY);
            return new Vec3(hitX, hitY, planeZ);
        }

        double[] xInterval = horizontalRayAxisInterval(eyesPos.x, directionX, minX, maxX);
        double[] zInterval = horizontalRayAxisInterval(eyesPos.z, directionZ, minZ, maxZ);
        if (xInterval == null || zInterval == null)
            return null;

        double minDistance = Math.max(MATH_ALIGNED_RAY_EPSILON, Math.max(xInterval[0], zInterval[0]));
        double maxDistance = Math.min(xInterval[1], zInterval[1]);
        if (minDistance > maxDistance)
            return null;

        double planeY = sideToClick == Direction.UP ? neighbour.getY() + 1.0D : neighbour.getY();
        double preferredDistance = (faceCenter.x - eyesPos.x) * directionX + (faceCenter.z - eyesPos.z) * directionZ;
        if (Math.abs(pitchTangent) > MATH_ALIGNED_RAY_EPSILON) {
            double currentPitchDistance = (eyesPos.y - planeY) / pitchTangent;
            if (Double.isFinite(currentPitchDistance) && currentPitchDistance > 0.0D)
                preferredDistance = currentPitchDistance;
        }

        double distance = Mth.clamp(preferredDistance, minDistance, maxDistance);
        return new Vec3(
                eyesPos.x + directionX * distance,
                planeY,
                eyesPos.z + directionZ * distance
        );
    }

    private static double[] horizontalRayAxisInterval(double origin, double direction, double min, double max) {
        if (Math.abs(direction) <= MATH_ALIGNED_RAY_EPSILON)
            return origin >= min && origin <= max
                    ? new double[]{Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}
                    : null;

        double first = (min - origin) / direction;
        double second = (max - origin) / direction;
        return new double[]{Math.min(first, second), Math.max(first, second)};
    }

    private static int fixedOffsetCount(Direction sideToClick, Direction positive, Direction negative) {
        return sideToClick == positive || sideToClick == negative ? 1 : MATH_FACE_SAMPLE_DIVISIONS;
    }

    private static double placementOffset(Direction sideToClick, Direction positive, Direction negative, int index) {
        if (sideToClick == positive)
            return 1.0D;
        if (sideToClick == negative)
            return 0.0D;
        return (index + 0.5D) / MATH_FACE_SAMPLE_DIVISIONS;
    }

    private boolean usesRayTracePlacementSearch() {
        return rotationMode.get().equalsIgnoreCase("math") && !isNoHitCheckActive();
    }

    private static double placementReach() {
        return mc.player != null ? mc.player.blockInteractionRange() : 4.5D;
    }

    private static double placementReachSqr() {
        double reach = placementReach();
        return reach * reach;
    }

    private double placementRotationScore(float[] rotations, float currentYaw, float currentPitch) {
        float yawDiff = Math.abs(Mth.wrapDegrees(rotations[0] - currentYaw));
        float pitchDiff = Math.abs(rotations[1] - currentPitch);
        return yawDiff + pitchDiff + mathMovementAlignmentScore(rotations[0]);
    }

    private double smartPlacementHitScore(Vec3 hitPos, float[] rotations, float currentYaw, float currentPitch, Vec3 eyesPos, Vec3 faceCenter, Direction side, boolean currentCanHit) {
        double centerScore = normalizedFaceCenterDistanceSqr(hitPos, faceCenter, side);
        double score = placementRotationScore(rotations, currentYaw, currentPitch)
                + eyesPos.distanceToSqr(hitPos) * 0.001D
                + centerScore * MATH_FACE_CENTER_WEIGHT;

        if (!isStablePlacementHit(hitPos, faceCenter, side))
            score += MATH_EDGE_HIT_PENALTY;

        if (currentCanHit)
            score += centerScore * 1.5D;

        return score;
    }

    private double mathMovementAlignmentScore(float targetYaw) {
        Float movementYaw = movementFixMathInputYaw();
        if (movementYaw == null)
            return 0.0D;

        return mathMovementAlignmentError(targetYaw, movementYaw) * MATH_MOVEMENT_ALIGNMENT_WEIGHT;
    }

    private Float movementFixMathInputYaw() {
        if (!ModuleManager.getModuleState(MovementFix.class))
            return null;

        return scaffoldInputDirection();
    }

    private static float mathMovementAlignmentError(float targetYaw, float movementYaw) {
        float relativeYaw = Math.abs(Mth.wrapDegrees(movementYaw - targetYaw));
        float sectorOffset = relativeYaw % MATH_MOVEMENT_SECTOR_DEGREES;
        // MovementFix reproduces the intended direction without switching corrected inputs at 45-degree sector centers.
        return Math.min(sectorOffset, MATH_MOVEMENT_SECTOR_DEGREES - sectorOffset);
    }

    private static boolean isStablePlacementHit(Vec3 hitPos, BlockPos neighbour, Direction side) {
        return isStablePlacementHit(hitPos, placementFaceCenter(neighbour, side), side);
    }

    private static boolean isStablePlacementHit(Vec3 hitPos, Vec3 faceCenter, Direction side) {
        double xOffset = Math.abs(hitPos.x - faceCenter.x);
        double yOffset = Math.abs(hitPos.y - faceCenter.y);
        double zOffset = Math.abs(hitPos.z - faceCenter.z);
        double safeRadius = 0.5D - MATH_SAFE_HIT_INSET;

        return switch (side.getAxis()) {
            case X -> yOffset <= safeRadius && zOffset <= safeRadius;
            case Y -> xOffset <= safeRadius && zOffset <= safeRadius;
            case Z -> xOffset <= safeRadius && yOffset <= safeRadius;
        };
    }

    private static double normalizedFaceCenterDistanceSqr(Vec3 hitPos, Vec3 faceCenter, Direction side) {
        double x = (hitPos.x - faceCenter.x) * 2.0D;
        double y = (hitPos.y - faceCenter.y) * 2.0D;
        double z = (hitPos.z - faceCenter.z) * 2.0D;

        return switch (side.getAxis()) {
            case X -> y * y + z * z;
            case Y -> x * x + z * z;
            case Z -> x * x + y * y;
        };
    }

    private static Vec3 placementFaceCenter(BlockPos neighbour, Direction side) {
        return Vec3.atCenterOf(neighbour).add(
                side.getStepX() * 0.5D,
                side.getStepY() * 0.5D,
                side.getStepZ() * 0.5D
        );
    }

    private double placementTargetScore(PlacementTarget target) {
        float[] rotations = rotationsToHitPos(target.hitPos());
        return placementRotationScore(rotations, Client.rotationManager.getRotationYaw(), Client.rotationManager.getRotationPitch())
                + minimumPlacementTargetScore(target.searchDistance())
                + placementSideScore(target.side());
    }

    private double placementTargetScore(BlockPos currentTargetBlock, PlacementTarget target) {
        return placementTargetScore(target)
                + mathTellyConservationScore(currentTargetBlock, target)
                + lastPlacedBridgePlacementScore(currentTargetBlock, target.pos(), target.neighbour());
    }

    private static double minimumPlacementTargetScore(int searchDistance) {
        return searchDistance * 4.0D;
    }

    private static double placementSideScore(Direction side) {
        return side == Direction.UP ? 0.0D : side == Direction.NORTH || side == Direction.SOUTH || side == Direction.WEST || side == Direction.EAST ? 0.05D : 0.1D;
    }

    private boolean isDownwardsPlacementSupport(Direction side) {
        return side == Direction.UP && isDownScaffoldActive();
    }

    private boolean tryPlaceBlock(PlacementTarget target, int slot) {
        var level = mc.level;
        var player = mc.player;
        var gameMode = mc.gameMode;
        if (level == null || player == null || gameMode == null) return false;

        if (!isScaffoldReplaceable(target.pos())) return true;
        if (!isScaffoldSupport(level.getBlockState(target.neighbour()))) return false;

        try {
            InteractionHand hand = slot == -1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            ItemStack stack = hand == InteractionHand.OFF_HAND ? player.getOffhandItem() : player.getInventory().getItem(slot);
            if (!canPlaceScaffoldStackAt(target.pos(), stack))
                return false;

            if (hand == InteractionHand.MAIN_HAND)
                selectSlot(slot);

            BlockHitResult bhr = new BlockHitResult(target.hitPos(), target.side(), target.neighbour(), false);
            if (!rotationMode.get().equalsIgnoreCase("none") && !isPlacementBypassRotationMode(target) && !isNoHitCheckActive(target)) {
                facePlacementTarget(target);
                BlockHitResult currentHit = currentPlacementHit(target);
                if (currentHit == null)
                    return false;

                bhr = currentHit;
            }

            boolean placed = gameMode.useItemOn(player, hand, bhr).consumesAction();

            if (placed) {
                placedScaffoldBlock = true;
                lastPlacedScaffoldBlock = new BlockPos(target.pos().getX(), target.pos().getY(), target.pos().getZ());
                startedScaffold = true;
                if (shouldActivateTellyAfterPlaceNoHitCheck(player))
                    tellyAfterPlaceNoHitCheckActive = true;
                clearMathPlacementTarget();
                if (isSnapRotationActive())
                    snapRotationResetPending = true;
                PacketUtil.sendPacket(new ServerboundSwingPacket(hand));
            }

            return placed;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean canPlaceScaffoldStackAt(BlockPos pos, ItemStack stack) {
        if (pos == null || stack == null || !(stack.getItem() instanceof BlockItem blockItem))
            return false;

        return canPlaceScaffoldStateAt(pos, blockItem.getBlock().defaultBlockState());
    }

    private boolean canPlaceScaffoldStateAt(BlockPos pos, BlockState state) {
        var level = mc.level;
        var player = mc.player;
        if (level == null || player == null || pos == null || state == null)
            return false;

        var shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty())
            return true;

        return !doesShapeIntersectBox(pos, shape.toAabbs(), player.getBoundingBox());
    }

    private static boolean isPlacementPositionClearForPlayer(BlockPos pos) {
        var player = mc.player;
        if (player == null || pos == null)
            return false;

        AABB blockBox = new AABB(pos);
        return !intersectsPlayerBox(blockBox, player.getBoundingBox());
    }

    private static boolean doesShapeIntersectBox(BlockPos pos, List<AABB> boxes, AABB playerBox) {
        if (boxes == null || playerBox == null)
            return true;

        for (AABB box : boxes) {
            if (intersectsPlayerBox(box.move(pos), playerBox))
                return true;
        }

        return false;
    }

    private static boolean intersectsPlayerBox(AABB blockBox, AABB playerBox) {
        return blockBox != null && playerBox != null && blockBox.intersects(playerBox.deflate(1.0E-7D));
    }

    private boolean isRotationReadyForPlacement(PlacementTarget target) {
        if (rotationMode.get().equalsIgnoreCase("none"))
            return true;
        if (isNoHitCheckActive(target))
            return true;
        if (isRotationTargetHitCheckMode(target))
            return Client.rotationManager.isRotating() && ModuleManager.rotTick > 1 && isFacingRotationTarget(target);

        return Client.rotationManager.isRotating() && ModuleManager.rotTick > 1 && currentPlacementHit(target) != null;
    }

    private boolean isNoHitCheckActive() {
        if (isDownScaffoldActive())
            return true;

        if (isTellyAfterPlaceNoHitCheckActive())
            return true;

        return hasNoHitCheckOption() && noHitCheck.get();
    }

    private boolean isNoHitCheckActive(PlacementTarget target) {
        return isNoHitCheckActive() || isBackwardsMathNoHitCheckTarget(target);
    }

    private boolean consumeSnapRotationReset() {
        if (!snapRotationResetPending)
            return false;

        snapRotationResetPending = false;
        Client.rotationManager.stopRotation();
        return noPlaceWhenSnapping.get();
    }

    private static boolean usesRotationSpeed() {
        return rotationMode.get().equalsIgnoreCase("math")
                || rotationMode.get().equalsIgnoreCase("simple")
                || rotationMode.get().equalsIgnoreCase("backwards");
    }

    private static boolean usesMathPreRotation() {
        return rotationMode.get().equalsIgnoreCase("math") && !isSnapRotationActive();
    }

    private static boolean hasNoHitCheckOption() {
        return usesRotationSpeed();
    }

    private static boolean canDisplayTellyAfterPlaceNoHitCheck() {
        return mode.get().equalsIgnoreCase("telly")
                && rotationMode.get().equalsIgnoreCase("math")
                && hasNoHitCheckOption()
                && !noHitCheck.get();
    }

    private boolean canUseTellyAfterPlaceNoHitCheck() {
        return tellyAfterPlaceNoHitCheck.get()
                && canDisplayTellyAfterPlaceNoHitCheck();
    }

    private void updateTellyAfterPlaceNoHitCheckState(net.minecraft.client.player.LocalPlayer player) {
        if (!canUseTellyAfterPlaceNoHitCheck() || player == null || player.onGround())
            tellyAfterPlaceNoHitCheckActive = false;
    }

    private boolean shouldActivateTellyAfterPlaceNoHitCheck(net.minecraft.client.player.LocalPlayer player) {
        return canUseTellyAfterPlaceNoHitCheck()
                && player != null
                && !player.onGround();
    }

    private boolean isTellyAfterPlaceNoHitCheckActive() {
        var player = mc.player;
        return tellyAfterPlaceNoHitCheckActive
                && canUseTellyAfterPlaceNoHitCheck()
                && player != null
                && !player.onGround();
    }

    private boolean isPlacementBypassRotationMode(PlacementTarget target) {
        return rotationMode.get().equalsIgnoreCase("simple")
                || rotationMode.get().equalsIgnoreCase("backwards") && !shouldBackwardsUseMathPlacement(target);
    }

    private boolean isRotationTargetHitCheckMode(PlacementTarget target) {
        return rotationMode.get().equalsIgnoreCase("simple")
                || rotationMode.get().equalsIgnoreCase("backwards") && !shouldBackwardsUseMathPlacement(target);
    }

    private boolean shouldBackwardsUseMathPlacement(PlacementTarget target) {
        return isBackwardsSearchMathPlacementTarget(target);
    }

    private boolean isBackwardsMathNoHitCheckTarget(PlacementTarget target) {
        return isBackwardsSearchMathPlacementTarget(target);
    }

    private boolean shouldSkipBackwardsPlacementTarget(PlacementTarget target) {
        return isBackwardsPlacementBehindRotation(target);
    }

    private static boolean isBackwardsSearchMathPlacementTarget(PlacementTarget target) {
        return target != null
                && rotationMode.get().equalsIgnoreCase("backwards")
                && target.searchDistance() >= 2;
    }

    private boolean isBackwardsPlacementBehindRotation(PlacementTarget target) {
        if (target == null || !rotationMode.get().equalsIgnoreCase("backwards"))
            return false;

        float placementYaw = rotationsToHitPos(target.hitPos())[0];
        float yawDiff = Math.abs(Mth.wrapDegrees(placementYaw - Client.rotationManager.getRotationYaw()));
        return yawDiff >= 90.0F;
    }

    private static boolean isSnapRotationActive() {
        return (rotationMode.get().equalsIgnoreCase("math") || rotationMode.get().equalsIgnoreCase("simple")) && snapRotation.get();
    }

    private static float getRotationSpeed() {
        float min = minRotationSpeed.get();
        float max = maxRotationSpeed.get();
        return RandomUtil.nextFloat(Math.min(min, max), Math.max(min, max));
    }

    private static float getMathPitchRotationSpeed() {
        float min = Math.min(minRotationSpeed.get(), maxRotationSpeed.get());
        float max = Math.max(minRotationSpeed.get(), maxRotationSpeed.get());
        float safeMax = Math.min(max, 18.0F);
        if (safeMax <= 0.0F)
            return 0.0F;

        float safeMin = min > 18.0F
                ? Math.min(12.0F, safeMax)
                : Math.min(min, safeMax);
        safeMin = Math.max(Math.min(1.0F, safeMax), safeMin);
        return RandomUtil.nextFloat(safeMin, safeMax);
    }

    private static boolean isTellyMode() {
        return mode.get().equalsIgnoreCase("telly");
    }

    private static boolean isDownScaffoldActive() {
        return down.get() && isPhysicalKeyDown(mc.options.keyShift.saveString());
    }

    private void faceScaffoldRotation(BlockPos targetBlock, PlacementTarget placementTarget, PlacementTarget preRotationTarget, boolean targetBlockReplaceable) {
        if (rotationMode.get().equalsIgnoreCase("none"))
            return;

        if (shouldTellyAvoidAiming()) {
            faceTellyJumpRotation(placementTarget != null ? placementTarget : preRotationTarget);
            return;
        }

        if (rotationMode.get().equalsIgnoreCase("simple")) {
            if (isSnapRotationActive() && (!targetBlockReplaceable || placementTarget == null))
                return;

            faceSimpleRotation();
            return;
        }

        if (rotationMode.get().equalsIgnoreCase("backwards")) {
            if (targetBlockReplaceable && placementTarget != null && shouldBackwardsUseMathPlacement(placementTarget))
                facePlacementTarget(placementTarget);
            else
                faceBackwardsRotation(targetBlockReplaceable ? placementTarget : null);
            return;
        }

        if (!isSnapRotationActive() && !placedScaffoldBlock && preRotationTarget == null && (!targetBlockReplaceable || placementTarget == null)) {
            faceSimpleRotation();
            return;
        }

        if (targetBlockReplaceable && placementTarget != null)
            facePlacementTarget(placementTarget);
        else if (preRotationTarget != null)
            facePlacementTarget(preRotationTarget);
    }

    private PlacementTarget findPreRotationTarget(BlockPos currentTargetBlock) {
        if (!rotationMode.get().equalsIgnoreCase("math"))
            return null;

        var player = mc.player;
        var level = mc.level;
        if (player == null || level == null)
            return null;

        Vec3 direction = getPreRotationDirection();
        if (direction.lengthSqr() <= 1.0E-6)
            return null;

        double speed = MovementUtil.getSpeed(player.getDeltaMovement().x, player.getDeltaMovement().z);
        double leadDistance = mathPreRotationLeadDistance(speed);
        if (leadDistance <= 1.0E-6D)
            return null;

        Vec3 predictedPosition = player.position().add(direction.normalize().scale(leadDistance));
        BlockPos predictedTargetBlock = new BlockPos.MutableBlockPos(
                Mth.floor(predictedPosition.x),
                scaffoldTargetBlockY(launchY),
                Mth.floor(predictedPosition.z)
        );

        if (predictedTargetBlock.equals(currentTargetBlock) || !isScaffoldReplaceable(predictedTargetBlock))
            return null;

        return findPlacementTarget(predictedTargetBlock);
    }

    private static double mathPreRotationLeadDistance(double speed) {
        double baseLeadDistance = Mth.clamp(Math.max(0.35D, speed * 2.0D + 0.15D), 0.35D, 0.85D);
        return baseLeadDistance * Mth.clamp((double) preRotationLead.get(), 0.0D, 2.0D);
    }

    private Vec3 getPreRotationDirection() {
        var player = mc.player;
        if (player == null)
            return Vec3.ZERO;

        Float inputYaw = !isTellyMode() ? scaffoldInputDirection() : null;
        if (inputYaw != null)
            return MovementUtil.getHorizontalDirectionVector(inputYaw);

        Vec3 motion = player.getDeltaMovement();
        Vec3 horizontalMotion = new Vec3(motion.x, 0.0D, motion.z);
        if (horizontalMotion.lengthSqr() > 1.0E-4)
            return horizontalMotion;

        inputYaw = scaffoldInputDirection();
        return inputYaw != null ? MovementUtil.getHorizontalDirectionVector(inputYaw) : Vec3.ZERO;
    }

    private void faceSimpleRotation() {
        var player = mc.player;
        if (player == null) return;

        float[] rotations = simpleRotationTarget();
        Client.rotationManager.startRotation(rotations[0], rotations[1], getRotationSpeed());
    }

    private void faceBackwardsRotation(PlacementTarget target) {
        var player = mc.player;
        if (player == null) return;

        float[] rotations = backwardsRotationTarget(target);
        Client.rotationManager.startRotation(rotations[0], rotations[1], getRotationSpeed());
    }

    private boolean isFacingRotationTarget(PlacementTarget placementTarget) {
        float[] rotations = scaffoldRotationTarget(placementTarget);
        if (rotations == null)
            return false;

        float yawDiff = Math.abs(Mth.wrapDegrees(Client.rotationManager.getRotationYaw() - rotations[0]));
        float pitchDiff = Math.abs(Client.rotationManager.getRotationPitch() - rotations[1]);
        return yawDiff <= 5.0F && pitchDiff <= 5.0F;
    }

    private float[] scaffoldRotationTarget(PlacementTarget placementTarget) {
        if (rotationMode.get().equalsIgnoreCase("simple"))
            return simpleRotationTarget();
        if (rotationMode.get().equalsIgnoreCase("backwards"))
            return backwardsRotationTarget(placementTarget);

        return null;
    }

    private float[] simpleRotationTarget() {
        var player = mc.player;
        float yaw = RotationScraper.INSTANCE.simpleSnap(getScaffoldMovementDirection() - 180f);
        float pitch = player != null && player.onGround() ? 70f : 80f;
        return new float[]{yaw, pitch};
    }

    private float[] backwardsRotationTarget(PlacementTarget target) {
        float yaw = Mth.wrapDegrees(getScaffoldMovementDirection() - 180f);
        float pitch = target != null ? rotationsToHitPos(target.hitPos())[1] : 85f;
        return new float[]{yaw, pitch};
    }

    private void faceTellyJumpRotation() {
        faceTellyJumpRotation(null);
    }

    private void faceTellyJumpRotation(PlacementTarget mathPitchTarget) {
        var player = mc.player;
        if (player == null || rotationMode.get().equalsIgnoreCase("none")) return;
        if (isTellyNormalJumpRotationTiming(player)) return;

        Float preparedPitch = mathTellyPreparationPitch(mathPitchTarget);
        if (preparedPitch != null)
            Client.rotationManager.startRotation(tellyJumpRotationYaw(), preparedPitch, 180f, getRotationSpeed());
        else
            Client.rotationManager.startRotation(tellyJumpRotationYaw(), Client.rotationManager.getRotationPitch(), 180f);
    }

    private Float mathTellyPreparationPitch(PlacementTarget target) {
        if (!rotationMode.get().equalsIgnoreCase("math") || isSnapRotationActive())
            return null;

        return target != null ? rotationsToHitPos(target.hitPos())[1] : simpleRotationTarget()[1];
    }

    private float tellyJumpRotationYaw() {
        float movementYaw = getScaffoldMovementDirection();
        if (!usesDiagonalTellyJumpRotation())
            return movementYaw;

        return Mth.wrapDegrees(movementYaw);
    }

    private static boolean usesDiagonalTellyJumpRotation() {
        return rotationMode.get().equalsIgnoreCase("simple")
                || rotationMode.get().equalsIgnoreCase("backwards");
    }

    private BlockHitResult currentPlacementHit(PlacementTarget target) {
        BlockHitResult hit = Client.rotationManager.rayTraceBlocks(
                Client.rotationManager.getRotationYaw(),
                Client.rotationManager.getRotationPitch(),
                mc.player != null ? mc.player.blockInteractionRange() : 4.5D
        );

        return isMatchingPlacementHit(hit, target) ? hit : null;
    }

    private boolean isMatchingPlacementHit(BlockHitResult hit, PlacementTarget target) {
        return isMatchingPlacementHit(hit, target.neighbour(), target.side());
    }

    private boolean isMatchingPlacementHit(BlockHitResult hit, BlockPos neighbour, Direction side) {
        return hit != null
                && hit.getType() == HitResult.Type.BLOCK
                && hit.getBlockPos().equals(neighbour)
                && hit.getDirection() == side;
    }

    private void facePlacementTarget(PlacementTarget target) {
        faceHitPos(target.hitPos(), getRotationSpeed());
    }

    private float getScaffoldMovementDirection() {
        var player = mc.player;
        if (player == null) return 0.0f;

        if (MovementFix.shouldFixMovement())
            return getMovementDirection(player.getYRot(), MovementFix.getRawInput());

        return MovementUtil.getPlayerDirection();
    }

    private Float scaffoldInputDirection() {
        var player = mc.player;
        if (player == null)
            return null;

        Input input;
        if (ModuleManager.getModuleState(MovementFix.class)) {
            input = MovementFix.getRawInput();
            if (!isMovingInput(input) && !MovementFix.shouldFixMovement())
                input = player.input != null ? player.input.keyPresses : Input.EMPTY;
        } else {
            input = player.input != null ? player.input.keyPresses : Input.EMPTY;
        }

        return isMovingInput(input) ? getMovementDirection(player.getYRot(), input) : null;
    }

    private static float getMovementDirection(float facingYaw, Input input) {
        if (input == null)
            return facingYaw;

        float yaw = facingYaw;
        float forwardMultiplier;

        if (input.backward() && !input.forward()) {
            yaw += 180f;
            forwardMultiplier = -0.5f;
        } else if (input.forward() && !input.backward()) {
            forwardMultiplier = 0.5f;
        } else {
            forwardMultiplier = 1f;
        }

        if (input.left() && !input.right())
            yaw -= 90f * forwardMultiplier;
        if (input.right() && !input.left())
            yaw += 90f * forwardMultiplier;

        return (yaw % 360f + 360f) % 360f;
    }

    private void faceHitPos(Vec3 hitPos, float speed) {
        float[] rotations = rotationsToHitPos(hitPos);
        if (rotationMode.get().equalsIgnoreCase("math") && !isDownScaffoldActive())
            Client.rotationManager.startRotation(rotations[0], rotations[1], speed, getMathPitchRotationSpeed());
        else
            Client.rotationManager.startRotation(rotations[0], rotations[1], speed);
    }

    private float[] rotationsToHitPos(Vec3 hitPos) {
        var player = mc.player;
        if (player == null) return new float[]{0.0f, 0.0f};

        Vec3 diff = hitPos.subtract(player.getEyePosition());
        double xzDist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);

        float yaw = (float) (Mth.atan2(-diff.x, diff.z) * (180.0 / Math.PI));
        float pitch = (float) (-(Mth.atan2(diff.y, xzDist) * (180.0 / Math.PI)));

        yaw = Mth.wrapDegrees(yaw);
        pitch = Mth.clamp(pitch, -90.0f, 90.0f);

        return new float[]{yaw, pitch};
    }

    private void selectSlot(int slot) {
        var player = mc.player;
        if (player == null) return;
        if (slot < 0 || slot > 8 || player.getInventory().getSelectedSlot() == slot) return;
        player.getInventory().setSelectedSlot(slot);
    }

    private Integer findBlockSlot(Container inv) {
        var player = mc.player;
        if (player != null && isUsableScaffoldStack(player.getOffhandItem()))
            return -1;

        Integer maxStackSlot = null;
        int maxStackSize = 0;

        for (int i = 0; i < Math.min(9, inv.getContainerSize()); i++) {
            if (i < 0 || i > 8) continue;
            var stack = inv.getItem(i);
            if (isUsableScaffoldStack(stack)) {
                if (intelligentPicker.get()) {
                    if (stack.getCount() > maxStackSize) {
                        maxStackSize = stack.getCount();
                        maxStackSlot = i;
                    }
                } else return i;
            }
        }

        return maxStackSlot;
    }

    private boolean hasAroundBlock() {
        var player = mc.player;
        var level = mc.level;
        if (player == null || level == null) return false;
        BlockPos base = player.blockPosition();

        for (int dy = 0; dy >= -2; dy--) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (isScaffoldSupport(level.getBlockState(base.offset(dx, dy, dz))))
                        return true;
                }
            }
        }

        return false;
    }

    private static boolean isPhysicalKeyDown(String keyName) {
        return InputConstants.isKeyDown(mc.getWindow(), InputConstants.getKey(keyName).getValue());
    }

    public static boolean shouldSuppressTellyNormalJumpSprintHook() {
        Scaffold scaffold = ModuleManager.getModule(Scaffold.class);
        return scaffold != null && scaffold.tempEnabled && scaffold.shouldSuppressTellyNormalJumpSprint();
    }

    public static int getHotbarStackSize() {
        return hotbarStackSize;
    }

    public static void setHotbarStackSize(int value) {
        hotbarStackSize = value;
    }

    public static boolean getWasTowering() {
        return wasTowering;
    }

    public static void setWasTowering(boolean value) {
        wasTowering = value;
    }

    public static boolean getStartedScaffold() {
        return startedScaffold;
    }

    public static void setStartedScaffold(boolean value) {
        startedScaffold = value;
    }

}
