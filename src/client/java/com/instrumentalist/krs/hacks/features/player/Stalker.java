package com.instrumentalist.krs.hacks.features.player;



import com.instrumentalist.krs.events.features.*;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.ChatUtil;
import com.instrumentalist.krs.utils.entity.EntityExtension;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import com.instrumentalist.krs.utils.pathfinder.MainPathFinder;
import com.instrumentalist.krs.utils.render.RenderUtil;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class Stalker extends Module {

    public Stalker() {
        super("Stalker", ModuleCategory.Player, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    private int targetIndex = 0;
    private Player currentTarget = null;
    private final List<AbstractClientPlayer> players = new ArrayList<>();

    @Override
    public String description() {
        return "Switch target with left and right key";
    }

    @Override
    public void onDisable() {
        reset();
    }

    @Override
    public void onEnable() {
        reset();
        if (mc.player != null && mc.level != null) {
            updatePlayerList();
            selectClosestTarget();
        }
    }

    @Override
    public void onWorld(WorldEvent event) {
        reset();
    }

    @Override
    public void onMotion(MotionEvent event) {
        if (mc.player == null || mc.level == null) return;

        updatePlayerList();

        if (players.isEmpty())
            return;

        if (currentTarget == null)
            selectClosestTarget();

        if (currentTarget == null) return;

        if (EntityExtension.boundingDistanceTo(mc.player, currentTarget) >= 1.2f) {
            ArrayList<Vec3> paths = MainPathFinder.computePath(mc.player.position(), currentTarget.position());
            if (paths == null || paths.isEmpty()) return;

            for (Vec3 path : paths) {
                PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Pos(path.x, path.y, path.z, true, mc.player.horizontalCollision));
            }
        }

        mc.player.setPos(currentTarget.position());
    }

    private void updatePlayerList() {
        if (mc.player == null || mc.level == null) {
            reset();
            return;
        }

        UUID selectedTargetId = currentTarget == null ? null : currentTarget.getUUID();
        Map<UUID, AbstractClientPlayer> livePlayers = new HashMap<>();
        for (AbstractClientPlayer player : mc.level.players()) {
            if (!player.isRemoved() && !(player instanceof LocalPlayer))
                livePlayers.put(player.getUUID(), player);
        }

        ArrayList<AbstractClientPlayer> refreshedPlayers = new ArrayList<>(livePlayers.size());
        for (AbstractClientPlayer player : players) {
            AbstractClientPlayer refreshedPlayer = livePlayers.remove(player.getUUID());
            if (refreshedPlayer != null)
                refreshedPlayers.add(refreshedPlayer);
        }

        ArrayList<AbstractClientPlayer> newPlayers = new ArrayList<>(livePlayers.values());
        newPlayers.sort(Comparator
                .comparingDouble((AbstractClientPlayer player) -> mc.player.distanceToSqr(player))
                .thenComparing(AbstractClientPlayer::getUUID));
        refreshedPlayers.addAll(newPlayers);

        players.clear();
        players.addAll(refreshedPlayers);

        if (players.isEmpty()) {
            targetIndex = 0;
            currentTarget = null;
            return;
        }

        if (selectedTargetId != null) {
            for (int i = 0; i < players.size(); i++) {
                AbstractClientPlayer player = players.get(i);
                if (player.getUUID().equals(selectedTargetId)) {
                    targetIndex = i;
                    currentTarget = player;
                    return;
                }
            }
        }

        targetIndex = 0;
        currentTarget = null;
    }

    @Override
    public void onKey(KeyboardEvent event) {
        if (mc.player == null || mc.level == null || event.action != GLFW.GLFW_PRESS) return;
        if (event.key != GLFW.GLFW_KEY_LEFT && event.key != GLFW.GLFW_KEY_RIGHT) return;

        updatePlayerList();
        if (players.isEmpty()) return;

        if (currentTarget == null) {
            selectClosestTarget();
            return;
        }

        if (event.key == GLFW.GLFW_KEY_LEFT) {
            selectPreviousTarget();
        } else if (event.key == GLFW.GLFW_KEY_RIGHT) {
            selectNextTarget();
        }
    }

    private void reset() {
        targetIndex = 0;
        currentTarget = null;
        players.clear();
    }

    private void selectClosestTarget() {
        if (!players.isEmpty()) {
            int closestIndex = 0;
            double closestDistance = mc.player.distanceToSqr(players.getFirst());
            for (int i = 1; i < players.size(); i++) {
                double distance = mc.player.distanceToSqr(players.get(i));
                if (distance < closestDistance) {
                    closestIndex = i;
                    closestDistance = distance;
                }
            }

            targetIndex = closestIndex;
            currentTarget = players.get(targetIndex);
        }
    }

    private void selectNextTarget() {
        if (!players.isEmpty()) {
            targetIndex = (targetIndex + 1) % players.size();
            currentTarget = players.get(targetIndex);
        }
    }

    private void selectPreviousTarget() {
        if (!players.isEmpty()) {
            targetIndex = (targetIndex - 1 + players.size()) % players.size();
            currentTarget = players.get(targetIndex);
        }
    }
}
