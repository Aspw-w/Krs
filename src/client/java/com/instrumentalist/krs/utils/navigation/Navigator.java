package com.instrumentalist.krs.utils.navigation;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.EventListener;
import com.instrumentalist.krs.events.features.CancelBlockBreakingEvent;
import com.instrumentalist.krs.events.features.HandleInputEvent;
import com.instrumentalist.krs.events.features.Render3DEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.events.features.WorldLeaveEvent;
import com.instrumentalist.krs.utils.ChatUtil;
import com.instrumentalist.krs.utils.entity.PlayerUtil;
import com.instrumentalist.krs.utils.move.InputUtil;
import com.instrumentalist.krs.utils.render.BlockHighlight;
import com.instrumentalist.krs.utils.render.PathLines;
import com.instrumentalist.krs.utils.render.RenderColors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class Navigator implements EventListener {
    private static final Navigator INSTANCE = new Navigator();
    private static final String USAGE = "Usage: goto <x> <y> <z> | goto <x> <z> | goto <player> | goto stop";
    private static final long IDLE_BUDGET_NANOS = 20_000_000L;
    private static final long BUSY_BUDGET_NANOS = 4_000_000L;
    private static final int EXTEND_AT = 400;
    private static final int HIGHLIGHT_LIMIT = 64;
    private static final float AIM_TOLERANCE = 1.5F;
    private static final int MOVE_TIMEOUT = 200;
    private static final int MAX_REPLANS = 8;
    private static final int FOLLOW_RADIUS = 2;
    private static final double TURN_RATE = 30.0D;
    private static final double MAX_TURN_SPEED = 1800.0D;
    private static final Direction[] PLACE_ORDER = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP};
    private static final double[] FACE_OFFSETS = {0.0D, 0.3D, -0.3D};

    private boolean active;
    private boolean controlling;
    private boolean registered;
    private NavGoal goal;
    private String label = "";
    private UUID targetId;
    private BlockPos plannedTarget;
    private int retargetCooldown;
    private List<NavMove> path;
    private int index;
    private boolean complete;
    private NavSearch search;
    private boolean extension;
    private boolean extensionFailed;
    private int emptySearches;
    private int retryDelay;
    private int replans;
    private int moveTicks;
    private final BlockHighlight mineHighlight = new BlockHighlight(0.25F, RenderColors.BREAKER);
    private final BlockHighlight placeHighlight = new BlockHighlight(0.25F, RenderColors.SCAFFOLD);
    private boolean aimSettled;
    private boolean internalStop;
    private boolean hasAim;
    private float aimYaw;
    private float aimPitch;
    private long lastFrameNanos;
    private int framesSinceTick;
    private int renderTicks;
    private boolean breaking;
    private NavWorld live;
    private boolean forward;
    private boolean backward;
    private boolean left;
    private boolean right;
    private boolean jump;
    private boolean sneak;
    private boolean sprint;

    private Navigator() {
    }

    public static String command(String arguments) {
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null)
            return "player is null";

        String trimmed = arguments == null ? "" : arguments.trim();
        if (trimmed.isEmpty())
            return USAGE;

        String[] parts = trimmed.split("\\s+");
        if (parts.length == 1 && (parts[0].equalsIgnoreCase("stop") || parts[0].equalsIgnoreCase("cancel"))) {
            if (!INSTANCE.active)
                return "Goto is not running";
            INSTANCE.halt(null);
            return "Goto stopped";
        }

        try {
            if (parts.length == 3) {
                int x = coordinate(parts[0], player.getX());
                int y = coordinate(parts[1], player.getY());
                int z = coordinate(parts[2], player.getZ());
                INSTANCE.begin(new NavGoal.Block(x, y, z), x + " " + y + " " + z, null, null);
                return "Going to " + INSTANCE.label;
            }

            if (parts.length == 2) {
                int x = coordinate(parts[0], player.getX());
                int z = coordinate(parts[1], player.getZ());
                INSTANCE.begin(new NavGoal.Column(x, z), x + " " + z, null, null);
                return "Going to " + INSTANCE.label;
            }
        } catch (NumberFormatException ignored) {
            return USAGE;
        }

        if (parts.length != 1)
            return USAGE;

        for (Player other : level.players()) {
            if (other == player || !other.getName().getString().equalsIgnoreCase(parts[0]))
                continue;

            BlockPos pos = other.blockPosition();
            INSTANCE.begin(new NavGoal.Near(pos.getX(), pos.getY(), pos.getZ(), FOLLOW_RADIUS), other.getName().getString(), other.getUUID(), pos);
            return "Going to " + INSTANCE.label;
        }

        return "Player " + parts[0] + " was not found nearby";
    }

    public static boolean suppressManualMining() {
        return INSTANCE.active && INSTANCE.controlling;
    }

    public static boolean isControlling() {
        return INSTANCE.active && INSTANCE.controlling;
    }

    public static boolean applyInput(ClientInput input) {
        Navigator navigator = INSTANCE;
        if (!navigator.active || !navigator.controlling || input == null)
            return false;

        InputUtil.applyInput(input, new Input(navigator.forward, navigator.backward, navigator.left, navigator.right, navigator.jump, navigator.sneak, navigator.sprint));
        return true;
    }

    private static int coordinate(String token, double origin) {
        String value = token.toLowerCase(Locale.ROOT);
        if (!value.startsWith("~"))
            return Mth.floor(Double.parseDouble(value));

        String offset = value.substring(1);
        return Mth.floor(origin + (offset.isEmpty() ? 0.0D : Double.parseDouble(offset)));
    }

    private void begin(NavGoal goal, String label, UUID targetId, BlockPos targetPos) {
        if (active)
            halt(null);

        this.goal = goal;
        this.label = label;
        this.targetId = targetId;
        this.plannedTarget = targetPos;
        this.retargetCooldown = 0;
        this.emptySearches = 0;
        this.retryDelay = 0;
        this.replans = 0;
        this.renderTicks = 0;
        this.active = true;
        resetPath();
        setRegistered(true);
    }

    private void halt(String message) {
        MultiPlayerGameMode gameMode = mc.gameMode;
        if (gameMode != null)
            stopBreaking(gameMode);

        active = false;
        controlling = false;
        breaking = false;
        hasAim = false;
        targetId = null;
        resetPath();
        clearControls();
        PathLines.hide(this, this);
        mineHighlight.release();
        placeHighlight.release();
        setRegistered(false);
        if (message != null) {
            ChatUtil.printChat(message);
            if (Client.notificationManager != null)
                Client.notificationManager.addNotification("Goto", message);
        }
    }

    private void resetPath() {
        path = null;
        index = 0;
        complete = false;
        search = null;
        extension = false;
        extensionFailed = false;
        moveTicks = 0;
    }

    private void setRegistered(boolean state) {
        if (registered == state || Client.eventManager == null)
            return;

        registered = state;
        if (state)
            Client.eventManager.register(this);
        else
            Client.eventManager.unregister(this);
    }

    private void clearControls() {
        forward = false;
        backward = false;
        left = false;
        right = false;
        jump = false;
        sneak = false;
        sprint = false;
    }

    @Override
    public void onWorld(WorldEvent event) {
        if (active)
            halt("Goto stopped: world changed");
    }

    @Override
    public void onWorldLeave(WorldLeaveEvent event) {
        if (active)
            halt(null);
    }

    @Override
    public void onCancelBlockBreaking(CancelBlockBreakingEvent event) {
        if (active && controlling && breaking && !internalStop)
            event.cancel();
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        long now = System.nanoTime();
        double seconds = lastFrameNanos == 0L ? 0.0D : Math.min(0.1D, (now - lastFrameNanos) / 1.0E9D);
        lastFrameNanos = now;
        framesSinceTick++;

        LocalPlayer player = mc.player;
        if (active && controlling && hasAim && player != null && seconds > 0.0D)
            turn(player, seconds);
    }

    @Override
    public void onHandleInput(HandleInputEvent event) {
        if (!active)
            return;

        boolean rendered = framesSinceTick > 0;
        framesSinceTick = 0;
        controlling = false;
        aimSettled = hasAim && mc.player != null
                && Math.abs(Mth.wrapDegrees(aimYaw - mc.player.getYRot())) <= AIM_TOLERANCE
                && Math.abs(aimPitch - mc.player.getXRot()) <= AIM_TOLERANCE;
        hasAim = false;
        clearControls();

        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        MultiPlayerGameMode gameMode = mc.gameMode;
        if (player == null || level == null || gameMode == null) {
            halt(null);
            return;
        }

        if (!player.isAlive()) {
            halt("Goto stopped: you died");
            return;
        }

        if (player.isSpectator() || player.isPassenger() || player.getAbilities().flying || player.isFallFlying()) {
            stopBreaking(gameMode);
            return;
        }

        controlling = true;

        updateTarget(level);

        BlockPos feet = BlockPos.containing(player.getX(), player.getY() + 0.5D, player.getZ());
        boolean grounded = player.onGround() || player.isInWater();
        if (grounded && goal.reached(feet.getX(), feet.getY(), feet.getZ())) {
            halt("Arrived at " + label);
            return;
        }

        live = NavWorld.live(player, level, gameMode);
        tickSearch(player, level, gameMode, feet, grounded);
        if (!active)
            return;

        if (path == null || index >= path.size()) {
            stopBreaking(gameMode);
            jump = player.isInWater();
            return;
        }

        follow(player, level, gameMode);
        if (!sprint && player.isSprinting())
            player.setSprinting(false);
        if (!rendered && hasAim)
            turn(player, 0.05D);
        showPath(player);
    }

    private void updateTarget(ClientLevel level) {
        if (targetId == null)
            return;

        if (retargetCooldown > 0)
            retargetCooldown--;

        Player target = level.getPlayerByUUID(targetId);
        if (target == null)
            return;

        BlockPos pos = target.blockPosition();
        if (retargetCooldown > 0 || plannedTarget != null && pos.distSqr(plannedTarget) <= 16.0D)
            return;

        plannedTarget = pos;
        retargetCooldown = 20;
        goal = new NavGoal.Near(pos.getX(), pos.getY(), pos.getZ(), FOLLOW_RADIUS);
        resetPath();
    }

    private void tickSearch(LocalPlayer player, ClientLevel level, MultiPlayerGameMode gameMode, BlockPos feet, boolean grounded) {
        if (search == null) {
            if (path == null || index >= path.size()) {
                if (!grounded)
                    return;
                if (retryDelay > 0) {
                    retryDelay--;
                    return;
                }

                path = null;
                extension = false;
                search = new NavSearch(NavWorld.snapshot(player, level, gameMode), feet, goal);
            } else if (!complete && !extensionFailed && path.size() - index <= EXTEND_AT) {
                extension = true;
                search = new NavSearch(NavWorld.snapshot(player, level, gameMode), path.get(path.size() - 1).to(), goal);
            } else {
                return;
            }
        }

        if (!search.step(extension ? BUSY_BUDGET_NANOS : IDLE_BUDGET_NANOS))
            return;

        List<NavMove> moves = search.moves();
        boolean reached = search.reachedGoal();
        search = null;

        if (extension) {
            extension = false;
            if (moves.isEmpty()) {
                extensionFailed = true;
            } else {
                path.addAll(moves);
                complete = reached;
            }
            return;
        }

        if (moves.isEmpty()) {
            if (++emptySearches >= 3)
                halt("Goto stopped: no path to " + label);
            else
                retryDelay = 40;
            return;
        }

        emptySearches = 0;
        path = new ArrayList<>(moves);
        index = 0;
        complete = reached;
        extensionFailed = false;
        moveTicks = 0;
    }

    private void replan(MultiPlayerGameMode gameMode) {
        stopBreaking(gameMode);
        resetPath();
        if (++replans > MAX_REPLANS)
            halt("Goto stopped: stuck on the way to " + label);
    }

    private void follow(LocalPlayer player, ClientLevel level, MultiPlayerGameMode gameMode) {
        int last = Math.min(path.size() - 1, index + 2);
        for (int i = last; i >= index; i--) {
            if (arrived(player, path.get(i))) {
                index = i + 1;
                moveTicks = 0;
                replans = 0;
                break;
            }
        }

        if (index >= path.size())
            return;

        NavMove move = path.get(index);
        if (offPath(player, move) || moveTicks > MOVE_TIMEOUT) {
            replan(gameMode);
            return;
        }

        execute(player, level, gameMode, move);
    }

    private boolean arrived(LocalPlayer player, NavMove move) {
        if (!player.onGround() && !player.isInWater())
            return false;

        BlockPos to = move.to();
        double dx = player.getX() - (to.getX() + 0.5D);
        double dz = player.getZ() - (to.getZ() + 0.5D);
        double dy = player.getY() - to.getY();
        boolean inColumn = Math.abs(dx) < 0.5D && Math.abs(dz) < 0.5D;
        return switch (move.type()) {
            case PILLAR -> inColumn && player.onGround() && dy > -0.05D;
            case SWIM_UP -> inColumn && dy > -0.15D && dy < 0.9D;
            default -> dx * dx + dz * dz < 0.35D * 0.35D && dy > -0.5D && dy < 0.8D;
        };
    }

    private boolean offPath(LocalPlayer player, NavMove move) {
        BlockPos from = move.from();
        BlockPos to = move.to();
        double y = player.getY();
        if (y < Math.min(from.getY(), to.getY()) - 1.5D || y > Math.max(from.getY(), to.getY()) + 2.0D)
            return true;

        double ax = from.getX() + 0.5D;
        double az = from.getZ() + 0.5D;
        double bx = to.getX() + 0.5D - ax;
        double bz = to.getZ() + 0.5D - az;
        double px = player.getX() - ax;
        double pz = player.getZ() - az;
        double lengthSqr = bx * bx + bz * bz;
        double t = lengthSqr < 1.0E-6D ? 0.0D : Mth.clamp((px * bx + pz * bz) / lengthSqr, 0.0D, 1.0D);
        double ox = px - bx * t;
        double oz = pz - bz * t;
        return ox * ox + oz * oz > 1.8D * 1.8D;
    }

    private void execute(LocalPlayer player, ClientLevel level, MultiPlayerGameMode gameMode, NavMove move) {
        BlockPos from = move.from();
        BlockPos to = move.to();

        for (BlockPos pos : obstacles(move)) {
            float space = live.space(pos);
            if (space == NavWorld.BLOCKED) {
                replan(gameMode);
                return;
            }
            if (space <= 0.0F)
                continue;

            if (move.type() == NavMove.Type.DOWN && !centerOn(player, from)) {
                stopBreaking(gameMode);
                moveTicks++;
                rotateTo(player, Vec3.atBottomCenterOf(from));
                return;
            }

            mine(player, level, gameMode, move, pos);
            return;
        }

        stopBreaking(gameMode);

        BlockPos placeAt = placement(move);
        if (placeAt != null) {
            place(player, level, gameMode, move, placeAt);
            return;
        }

        moveTicks++;
        Vec3 dest = Vec3.atBottomCenterOf(to);
        boolean water = player.isInWater();
        switch (move.type()) {
            case WALK -> {
                steer(player, dest);
                moveToward(player, dest, !water && straightAhead(move));
                if (water)
                    jump = player.getY() < to.getY() + 0.35D;
            }
            case ASCEND -> {
                steer(player, dest);
                moveToward(player, dest, false);
                if ((player.onGround() || water) && player.getY() < to.getY() - 0.05D)
                    jump = true;
            }
            case DESCEND -> {
                steer(player, dest);
                moveToward(player, dest, false);
                if (water)
                    jump = player.getY() < to.getY() + 0.35D;
            }
            case SWIM_UP -> {
                centerOn(player, to);
                jump = true;
            }
            case DOWN -> centerOn(player, to);
            case PILLAR -> {
            }
        }
    }

    private List<BlockPos> obstacles(NavMove move) {
        BlockPos from = move.from();
        BlockPos to = move.to();
        List<BlockPos> list = new ArrayList<>(6);
        switch (move.type()) {
            case WALK -> {
                if (from.getX() != to.getX() && from.getZ() != to.getZ()) {
                    BlockPos first = new BlockPos(to.getX(), from.getY(), from.getZ());
                    BlockPos second = new BlockPos(from.getX(), from.getY(), to.getZ());
                    list.add(first.above());
                    list.add(first);
                    list.add(second.above());
                    list.add(second);
                }
                list.add(to.above());
                list.add(to);
            }
            case ASCEND -> {
                list.add(from.above(2));
                list.add(to.above());
                list.add(to);
            }
            case DESCEND -> {
                BlockPos step = new BlockPos(to.getX(), from.getY(), to.getZ());
                list.add(step.above());
                list.add(step);
            }
            case PILLAR -> list.add(from.above(2));
            case DOWN -> list.add(from.below());
            case SWIM_UP -> {
            }
        }
        return list;
    }

    private BlockPos placement(NavMove move) {
        BlockPos to = move.to();
        if (move.type() == NavMove.Type.WALK)
            return live.space(to) != NavWorld.WATER && !live.standable(to.below()) ? to.below() : null;
        if (move.type() == NavMove.Type.PILLAR)
            return live.standable(move.from()) ? null : move.from();
        return null;
    }

    private void mine(LocalPlayer player, ClientLevel level, MultiPlayerGameMode gameMode, NavMove move, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Inventory inventory = player.getInventory();
        int slot = NavWorld.bestToolSlot(inventory, state, state.getDestroySpeed(level, pos));
        if (slot >= 0 && slot != inventory.getSelectedSlot())
            inventory.setSelectedSlot(slot);

        Vec3 eye = player.getEyePosition();
        double reach = player.blockInteractionRange();
        BlockHitResult look = look(player, level, eye, reach);
        boolean acted = false;
        if (look != null && !player.isUsingItem()) {
            BlockPos hit = look.getBlockPos();
            if (hit.equals(pos) || aimSettled && mayClear(move, hit)) {
                if (gameMode.continueDestroyBlock(hit, look.getDirection())) {
                    PlayerUtil.INSTANCE.swingHand(InteractionHand.MAIN_HAND);
                    acted = true;
                    breaking = true;
                }
            }
        }

        if (!acted) {
            stopBreaking(gameMode);
            moveTicks++;
        }

        Vec3 aim = mineAim(player, level, pos, state, eye, reach);
        rotateTo(player, aim);
        if (eye.distanceTo(aim) > reach)
            moveToward(player, aim, false);
        if (player.isInWater())
            jump = player.getY() < move.from().getY() + 0.35D;
    }

    private boolean mayClear(NavMove move, BlockPos hit) {
        if (hit.getY() < Math.min(move.from().getY(), move.to().getY()))
            return false;

        float space = live.space(hit);
        return space > 0.0F && space != NavWorld.BLOCKED;
    }

    private Vec3 mineAim(LocalPlayer player, ClientLevel level, BlockPos pos, BlockState state, Vec3 eye, double reach) {
        VoxelShape shape = state.getShape(level, pos);
        AABB box = shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos);
        Vec3 center = box.getCenter();
        if (visible(player, level, eye, center, pos, null, reach))
            return center;

        double hx = box.getXsize() * 0.5D - 0.05D;
        double hy = box.getYsize() * 0.5D - 0.05D;
        double hz = box.getZsize() * 0.5D - 0.05D;
        Vec3[] candidates = {
                center.add(0.0D, hy, 0.0D), center.add(0.0D, -hy, 0.0D),
                center.add(hx, 0.0D, 0.0D), center.add(-hx, 0.0D, 0.0D),
                center.add(0.0D, 0.0D, hz), center.add(0.0D, 0.0D, -hz)
        };
        for (Vec3 candidate : candidates) {
            if (visible(player, level, eye, candidate, pos, null, reach))
                return candidate;
        }
        return center;
    }

    private void place(LocalPlayer player, ClientLevel level, MultiPlayerGameMode gameMode, NavMove move, BlockPos target) {
        Inventory inventory = player.getInventory();
        int slot = NavWorld.throwawaySlot(inventory);
        if (slot < 0 || !live.replaceable(target)) {
            replan(gameMode);
            return;
        }

        if (inventory.getSelectedSlot() != slot)
            inventory.setSelectedSlot(slot);

        moveTicks++;
        Vec3 eye = player.getEyePosition();
        double reach = player.blockInteractionRange();
        boolean intersects = player.getBoundingBox().intersects(new AABB(target));
        Vec3 aim = intersects ? null : placeAim(player, level, target, eye, reach);
        if (aim != null && !player.isUsingItem()) {
            BlockHitResult look = look(player, level, eye, reach);
            if (look != null && look.getBlockPos().relative(look.getDirection()).equals(target) && live.clickable(look.getBlockPos())) {
                if (gameMode.useItemOn(player, InteractionHand.MAIN_HAND, look).consumesAction())
                    PlayerUtil.INSTANCE.swingHand(InteractionHand.MAIN_HAND);
            }
        }

        BlockPos from = move.from();
        if (move.type() == NavMove.Type.PILLAR) {
            rotate(player, player.getYRot(), 90.0F);
            Vec3 center = Vec3.atBottomCenterOf(from);
            double dx = center.x - player.getX();
            double dz = center.z - player.getZ();
            if (dx * dx + dz * dz > 0.2D * 0.2D) {
                sneak = true;
                moveToward(player, center, false);
            } else if (player.onGround())
                jump = true;
            return;
        }

        sneak = true;
        if (aim != null) {
            rotateTo(player, aim);
            return;
        }

        BlockPos to = move.to();
        float yaw = (float) Math.toDegrees(Math.atan2(-(from.getX() - to.getX()), from.getZ() - to.getZ()));
        rotate(player, yaw, 78.0F);
        moveToward(player, Vec3.atBottomCenterOf(to), false);
    }

    private Vec3 placeAim(LocalPlayer player, ClientLevel level, BlockPos target, Vec3 eye, double reach) {
        for (Direction direction : PLACE_ORDER) {
            BlockPos support = target.relative(direction);
            if (!live.clickable(support))
                continue;

            Direction face = direction.getOpposite();
            Vec3 base = Vec3.atCenterOf(support).add(face.getStepX() * 0.5D, face.getStepY() * 0.5D, face.getStepZ() * 0.5D);
            for (double u : FACE_OFFSETS) {
                for (double v : FACE_OFFSETS) {
                    Vec3 point = switch (face.getAxis()) {
                        case X -> base.add(0.0D, u, v);
                        case Y -> base.add(u, 0.0D, v);
                        case Z -> base.add(u, v, 0.0D);
                    };
                    if (visible(player, level, eye, point, support, face, reach))
                        return point;
                }
            }
        }
        return null;
    }

    private boolean visible(LocalPlayer player, ClientLevel level, Vec3 eye, Vec3 point, BlockPos pos, Direction face, double reach) {
        Vec3 delta = point.subtract(eye);
        double length = delta.length();
        if (length > reach || length < 1.0E-4D)
            return false;

        Vec3 end = point.add(delta.scale(0.2D / length));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos) && (face == null || hit.getDirection() == face);
    }

    private BlockHitResult look(LocalPlayer player, ClientLevel level, Vec3 eye, double reach) {
        Vec3 direction = Entity.calculateViewVector(player.getXRot(), player.getYRot());
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(direction.scale(reach)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    private void stopBreaking(MultiPlayerGameMode gameMode) {
        if (!breaking)
            return;

        internalStop = true;
        try {
            gameMode.stopDestroyBlock();
        } finally {
            internalStop = false;
        }
        breaking = false;
    }

    private boolean straightAhead(NavMove move) {
        if (index + 1 >= path.size())
            return false;

        NavMove next = path.get(index + 1);
        if (next.type() != NavMove.Type.WALK || !live.standable(next.to().below()))
            return false;

        return next.to().getX() - next.from().getX() == move.to().getX() - move.from().getX()
                && next.to().getZ() - next.from().getZ() == move.to().getZ() - move.from().getZ();
    }

    private boolean centerOn(LocalPlayer player, BlockPos pos) {
        Vec3 center = Vec3.atBottomCenterOf(pos);
        double dx = center.x - player.getX();
        double dz = center.z - player.getZ();
        if (dx * dx + dz * dz < 0.1D * 0.1D)
            return true;

        sneak = true;
        moveToward(player, center, false);
        return false;
    }

    private void steer(LocalPlayer player, Vec3 dest) {
        double dx = dest.x - player.getX();
        double dz = dest.z - player.getZ();
        float yaw = dx * dx + dz * dz > 0.09D ? (float) Math.toDegrees(Math.atan2(-dx, dz)) : player.getYRot();
        rotate(player, yaw, 10.0F);
    }

    private void moveToward(LocalPlayer player, Vec3 dest, boolean wantSprint) {
        double dx = dest.x - player.getX();
        double dz = dest.z - player.getZ();
        if (dx * dx + dz * dz < 0.05D * 0.05D)
            return;

        float diff = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-dx, dz)) - player.getYRot());
        float abs = Math.abs(diff);
        forward = abs <= 67.5F;
        backward = abs >= 112.5F;
        boolean side = abs > 22.5F && abs < 157.5F;
        right = side && diff > 0.0F;
        left = side && diff < 0.0F;
        sprint = wantSprint && abs <= 22.5F;
    }

    private void rotateTo(LocalPlayer player, Vec3 aim) {
        Vec3 eye = player.getEyePosition();
        double dx = aim.x - eye.x;
        double dy = aim.y - eye.y;
        double dz = aim.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = horizontal < 1.0E-3D ? player.getYRot() : (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        rotate(player, yaw, pitch);
    }

    private void rotate(LocalPlayer player, float yaw, float pitch) {
        aimYaw = yaw;
        aimPitch = Mth.clamp(pitch, -90.0F, 90.0F);
        hasAim = true;
    }

    private void turn(LocalPlayer player, double seconds) {
        double sensitivity = mc.options.sensitivity().get() * 0.6D + 0.2D;
        double unit = sensitivity * sensitivity * sensitivity * 8.0D * 0.15D;
        if (unit <= 0.0D || !Double.isFinite(unit))
            return;

        double ease = 1.0D - Math.exp(-seconds * TURN_RATE);
        double limit = MAX_TURN_SPEED * seconds;
        float yawStep = mouseStep(Mth.wrapDegrees(aimYaw - player.getYRot()), ease, limit, unit);
        float pitchStep = mouseStep(aimPitch - player.getXRot(), ease, limit, unit);
        float pitch = Mth.clamp(player.getXRot() + pitchStep, -90.0F, 90.0F);
        pitchStep = pitch - player.getXRot();

        player.setYRot(player.getYRot() + yawStep);
        player.setXRot(pitch);
        player.yRotO += yawStep;
        player.xRotO = Mth.clamp(player.xRotO + pitchStep, -90.0F, 90.0F);
    }

    private static float mouseStep(float diff, double ease, double limit, double unit) {
        double wanted = Mth.clamp(diff * ease, -limit, limit);
        long counts = Math.round(wanted / unit);
        if (counts == 0L && Math.abs(diff) > unit * 0.5D)
            counts = diff > 0.0F ? 1L : -1L;
        return (float) (counts * unit);
    }

    private void showPath(LocalPlayer player) {
        if (renderTicks++ % 5 != 0 || path == null || index >= path.size())
            return;

        List<Vec3> points = new ArrayList<>();
        List<BlockPos> mines = new ArrayList<>();
        List<BlockPos> places = new ArrayList<>();
        int lastDx = 0;
        int lastDy = 0;
        int lastDz = 0;
        for (int i = index; i < path.size(); i++) {
            NavMove move = path.get(i);
            int dx = move.to().getX() - move.from().getX();
            int dy = move.to().getY() - move.from().getY();
            int dz = move.to().getZ() - move.from().getZ();
            if (i > index && dx == lastDx && dy == lastDy && dz == lastDz && !points.isEmpty())
                points.remove(points.size() - 1);
            points.add(Vec3.atBottomCenterOf(move.to()));
            lastDx = dx;
            lastDy = dy;
            lastDz = dz;

            if (mines.size() < HIGHLIGHT_LIMIT) {
                for (BlockPos pos : obstacles(move)) {
                    float space = live.space(pos);
                    if (space > 0.0F && space != NavWorld.BLOCKED && !mines.contains(pos) && mines.size() < HIGHLIGHT_LIMIT)
                        mines.add(pos);
                }
            }

            if (places.size() < HIGHLIGHT_LIMIT) {
                BlockPos placeAt = placement(move);
                if (placeAt != null && !places.contains(placeAt))
                    places.add(placeAt);
            }
        }

        PathLines.show(this, this, player.position(), points, RenderColors.PATH_FINDER);
        mineHighlight.setTargets(mines);
        placeHighlight.setTargets(places);
    }
}
