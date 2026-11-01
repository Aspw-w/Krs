package com.instrumentalist.krs.hacks.features.dev;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.Render3DEvent;
import com.instrumentalist.krs.events.features.TickEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.combat.AntiBot;
import com.instrumentalist.krs.hacks.features.combat.Teams;
import com.instrumentalist.krs.utils.entity.PlayerUtil;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.math.BehaviorUtils;
import com.instrumentalist.krs.utils.render.BlockHighlight;
import com.instrumentalist.krs.utils.render.RenderColors;
import com.instrumentalist.krs.utils.world.PacketBreaker;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;

public class CityBreaker extends Module {
    private static final float HARD_BLOCK_RESISTANCE = 600.0F;
    private static final long DONE_FLASH_MILLIS = 400L;

    @Setting
    private final FloatValue targetRange = new FloatValue("Target Range", 6f, 2f, 10f, "m");

    @Setting
    private final FloatValue range = new FloatValue("Range", 6f, 1f, 6f, "m");

    @Setting
    private final BooleanValue hardBlocksOnly = new BooleanValue("Hard Blocks Only", true);

    @Setting
    private final BooleanValue bestTool = new BooleanValue("Best Tool", true);

    @Setting
    private final BooleanValue rotations = new BooleanValue("Rotations", false);

    private final PacketBreaker breaker = new PacketBreaker();
    private final BlockHighlight candidateHighlight = new BlockHighlight(0.2f, RenderColors.withAlpha(RenderColors.CITY_BREAKER, 90));
    private final BlockHighlight targetHighlight = new BlockHighlight(0.3f, RenderColors.CITY_BREAKER);
    private final ArrayList<BlockPos> candidates = new ArrayList<>(4);
    private Player targetPlayer;
    private boolean rotating;

    public CityBreaker() {
        super("City Breaker", ModuleCategory.Dev, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public String tag() {
        return targetPlayer != null ? targetPlayer.getName().getString() : null;
    }

    @Override
    public void onEnable() {
        breaker.reset();
        targetPlayer = null;
    }

    @Override
    public void onDisable() {
        breaker.abort();
        stopRotation();
        targetPlayer = null;
        candidates.clear();
        candidateHighlight.release();
        targetHighlight.release();
    }

    @Override
    public void onWorld(WorldEvent event) {
        breaker.reset();
        targetPlayer = null;
        candidates.clear();
        candidateHighlight.release();
        targetHighlight.release();
    }

    @Override
    public void onTick(TickEvent event) {
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.gameMode == null) {
            targetPlayer = null;
            candidates.clear();
            return;
        }

        if (mc.gameMode.getPlayerMode() == GameType.SPECTATOR || mc.gameMode.getPlayerMode() == GameType.ADVENTURE) {
            breaker.abort();
            targetPlayer = null;
            candidates.clear();
            stopRotation();
            return;
        }

        targetPlayer = findTarget(player);
        candidates.clear();
        if (targetPlayer != null)
            collectCandidates(player, level, targetPlayer);

        if (player.getAbilities().instabuild) {
            breakInCreative(player, level);
            return;
        }

        BlockPos current = breaker.target();
        boolean currentValid = current != null && candidates.contains(current);
        if (current != null && !currentValid && !breaker.isFinished())
            breaker.abort();

        PacketBreaker.State state = breaker.tick(bestTool.get(), range.get());
        if (state == PacketBreaker.State.BROKEN && breaker.lastTarget() != null)
            targetHighlight.flash(breaker.lastTarget(), DONE_FLASH_MILLIS);

        if (!breaker.isActive() && !candidates.isEmpty()) {
            BlockPos next = candidates.getFirst();
            Direction face = PlayerUtil.INSTANCE.blockHitResult(next).getDirection();
            if (rotations.get()) {
                Client.rotationManager.faceBlock(next, 180f);
                rotating = true;
            }
            breaker.start(next, face);
        }

        if (!breaker.isActive())
            stopRotation();
        else if (rotations.get() && breaker.target() != null) {
            Client.rotationManager.faceBlock(breaker.target(), 180f);
            rotating = true;
        }
    }

    private void breakInCreative(LocalPlayer player, ClientLevel level) {
        if (candidates.isEmpty())
            return;

        BlockPos next = candidates.getFirst();
        if (!player.getMainHandItem().canDestroyBlock(level.getBlockState(next), level, next, player))
            return;

        if (mc.gameMode.startDestroyBlock(next, PlayerUtil.INSTANCE.blockHitResult(next).getDirection()))
            PlayerUtil.INSTANCE.swingHand(InteractionHand.MAIN_HAND);
    }

    private Player findTarget(LocalPlayer player) {
        boolean antiBot = ModuleManager.getModuleState(AntiBot.class);
        boolean teams = ModuleManager.getModuleState(Teams.class);
        double maxDistance = targetRange.get() * targetRange.get();
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player other : mc.level.players()) {
            if (other == player || !other.isAlive() || other.isSpectator())
                continue;
            if (BehaviorUtils.isBot(other, antiBot) || BehaviorUtils.isTeammate(other, teams))
                continue;

            double distance = other.distanceToSqr(player);
            if (distance <= maxDistance && distance < bestDistance) {
                bestDistance = distance;
                best = other;
            }
        }
        return best;
    }

    private void collectCandidates(LocalPlayer player, ClientLevel level, Player target) {
        BlockPos feet = BlockPos.containing(target.getX(), target.getY() + 0.2D, target.getZ());
        var eye = player.getEyePosition();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos pos = feet.relative(direction);
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F)
                continue;
            if (hardBlocksOnly.get() && state.getBlock().getExplosionResistance() < HARD_BLOCK_RESISTANCE)
                continue;
            if (player.blockActionRestricted(level, pos, mc.gameMode.getPlayerMode()))
                continue;
            if (!PacketBreaker.isInRange(player, pos, range.get()))
                continue;

            candidates.add(pos);
        }
        candidates.sort((a, b) -> Double.compare(new AABB(a).distanceToSqr(eye), new AABB(b).distanceToSqr(eye)));
    }

    private void stopRotation() {
        if (rotating) {
            Client.rotationManager.stopRotation();
            rotating = false;
        }
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        BlockPos current = breaker.target();
        if (candidates.isEmpty())
            candidateHighlight.release();
        else
            candidateHighlight.setTargets(candidates);

        if (current == null)
            targetHighlight.release();
        else
            targetHighlight.setTarget(current);
    }
}
