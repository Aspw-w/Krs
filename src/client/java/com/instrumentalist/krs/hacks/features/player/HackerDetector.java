package com.instrumentalist.krs.hacks.features.player;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.ReceivedPacketEvent;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.features.combat.AntiBot;
import com.instrumentalist.krs.utils.input.Keys;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public class HackerDetector extends Module {

    private static final int WARMUP_TICKS = 40;
    private static final Pattern PLAIN_MESSAGE = Pattern.compile("[A-Za-z0-9_ ]+");

    private final Map<UUID, PlayerData> players = new HashMap<>();
    private final Set<UUID> seen = new HashSet<>();

    private enum Check {
        FLY("Fly"),
        SCAFFOLD("Scaffold"),
        SPEED("Speed"),
        NO_SLOW("No Slow"),
        CHAT_BYPASS("Chat Bypass");

        final String label;

        Check(String label) {
            this.label = label;
        }
    }

    private static final class PlayerData {
        final EnumSet<Check> alerted = EnumSet.noneOf(Check.class);
        int ticks;
        double flyFlags;
        double speedFlags;
        double noSlowFlags;
        int jumpedTick = -100;
        int lastFlaggedTick = -100;
        float lastAirYaw;
        double lastMotionY;
    }

    public HackerDetector() {
        super("Hacker Detector", ModuleCategory.Player, Keys.KEY_UNKNOWN, false, true);
    }

    private void reset() {
        players.clear();
        seen.clear();
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        reset();
    }

    @Override
    public void onWorld(WorldEvent event) {
        reset();
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        if (mc.level == null || mc.player == null)
            return;

        seen.clear();
        for (Player player : mc.level.players()) {
            if (player == mc.player)
                continue;

            UUID id = player.getUUID();
            seen.add(id);
            PlayerData data = players.computeIfAbsent(id, ignored -> new PlayerData());
            data.ticks++;

            if (isIgnored(player) || data.ticks < WARMUP_TICKS)
                continue;

            double motionX = player.getX() - player.xo;
            double motionY = player.getY() - player.yo;
            double motionZ = player.getZ() - player.zo;
            double horizontal = Math.sqrt(motionX * motionX + motionZ * motionZ);
            boolean movementExempt = isMovementExempt(player);

            if (!movementExempt) {
                checkFly(player, data, horizontal, motionY);
                checkSpeed(player, data, motionX, motionY, motionZ, horizontal);
                checkNoSlow(player, data, horizontal);
            }

            data.lastMotionY = motionY;
        }

        players.keySet().removeIf(id -> !seen.contains(id));
    }

    private void checkFly(Player player, PlayerData data, double horizontal, double motionY) {
        if (horizontal > 0.23 && Math.abs(motionY) <= 0.005 && !player.isShiftKeyDown()
                && isAir(player, 0.45) && isAir(player, 1.35)) {
            data.flyFlags += player.onGround() ? 4 : 1;
            if (horizontal > defaultSpeed(player))
                data.flyFlags += horizontal * horizontal;

            if (data.flyFlags >= 35)
                alert(player, data, Check.FLY);

            ItemStack held = player.getMainHandItem();
            if (held.getItem() instanceof BlockItem && player.getXRot() >= 70f) {
                data.flyFlags += 5;
                if (data.flyFlags >= 30)
                    alert(player, data, Check.SCAFFOLD);
            }
            return;
        }

        if (player.onGround() && !isAir(player, 0.5)) {
            if (data.flyFlags < 10 && player.yo > player.getY())
                data.flyFlags = 0;
            else
                data.flyFlags = Math.max(0, data.flyFlags - 0.25);
        }
    }

    private void checkSpeed(Player player, PlayerData data, double motionX, double motionY, double motionZ, double horizontal) {
        float moveYaw = movementYaw(player, motionX, motionZ);
        boolean jumped = motionY > 0.1 && motionY < 0.5 && data.lastMotionY < 0.1;
        int airTicks = player.onGround() ? 0 : data.ticks - data.jumpedTick;
        double flags = 0;

        if (jumped) {
            data.jumpedTick = data.ticks;
            data.lastAirYaw = moveYaw;
            float diff = Math.abs(Mth.wrapDegrees(moveYaw - player.getYRot()));
            if (horizontal >= 0.355 && diff > 44.5f)
                flags += diff > 120 ? 15 : diff > 90 ? 7 : 3;
        }

        if (airTicks > 0 && airTicks < 10) {
            float diff = Math.abs(Mth.wrapDegrees(moveYaw - data.lastAirYaw));
            if (diff > 60 && horizontal >= 0.36 && horizontal < 2) {
                flags += diff > 100 ? 7 : 5;
                data.lastFlaggedTick = data.ticks;
                data.lastAirYaw = moveYaw;
            }
        }

        if (player.isBlocking())
            flags *= 1.5;

        data.speedFlags += flags;

        if (player.onGround() && data.ticks - data.jumpedTick > 20 && data.speedFlags > 0)
            data.speedFlags = Math.max(0, data.speedFlags - 5);

        if (data.speedFlags >= 25 && data.ticks - data.lastFlaggedTick < 5 && hasBlockBelow(player))
            alert(player, data, Check.SPEED);
    }

    private void checkNoSlow(Player player, PlayerData data, double horizontal) {
        boolean overGround = !isAir(player, 0.5) || !isAir(player, 1.0) || !isAir(player, 1.5);
        boolean slowedUse = player.isBlocking() || player.isUsingItem() && player.getTicksUsingItem() > 5;

        if (overGround && slowedUse && horizontal > 0.4 && horizontal < 7) {
            data.noSlowFlags += horizontal > 0.45 ? horizontal / 0.4 * 3 : 1;
            if (data.noSlowFlags > 25)
                alert(player, data, Check.NO_SLOW);
            return;
        }

        if (!slowedUse)
            data.noSlowFlags = Math.max(0, data.noSlowFlags - 0.5);
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
        if (event.packet instanceof ClientboundPlayerChatPacket chat) {
            String message = chat.body().content();
            mc.execute(() -> checkChatBypass(findPlayer(chat.sender()), message));
            return;
        }

        if (event.packet instanceof ClientboundSystemChatPacket system && !system.overlay()) {
            String text = system.content().getString();
            int split = text.indexOf(": ");
            if (split <= 0)
                return;

            String prefix = text.substring(0, split);
            String message = text.substring(split + 2);
            mc.execute(() -> {
                if (mc.level == null)
                    return;
                for (Player player : mc.level.players()) {
                    if (player != mc.player && prefix.contains(player.getGameProfile().name())) {
                        checkChatBypass(player, message);
                        return;
                    }
                }
            });
        }
    }

    private Player findPlayer(UUID id) {
        if (mc.level == null || id == null)
            return null;
        for (Player player : mc.level.players()) {
            if (id.equals(player.getUUID()))
                return player;
        }
        return null;
    }

    private void checkChatBypass(Player player, String message) {
        if (player == null || player == mc.player || message == null || message.isEmpty() || isIgnored(player))
            return;
        if (PLAIN_MESSAGE.matcher(message).matches())
            return;

        int invisible = 0;
        for (int i = 0; i < message.length(); i++) {
            char character = message.charAt(i);
            if (!Character.isLetter(character) && !Character.isWhitespace(character)
                    && mc.font.width(String.valueOf(character)) == 0) {
                invisible++;
                if (invisible > 2) {
                    PlayerData data = players.computeIfAbsent(player.getUUID(), ignored -> new PlayerData());
                    alert(player, data, Check.CHAT_BYPASS);
                    return;
                }
            }
        }
    }

    private boolean isIgnored(Player player) {
        return player.isDeadOrDying()
                || player.isCreative()
                || player.isSpectator()
                || player.isInvisible()
                || AntiBot.inBotList(player);
    }

    private static boolean isMovementExempt(Player player) {
        return player.isPassenger()
                || player.isFallFlying()
                || player.isAutoSpinAttack()
                || player.isInLiquid()
                || player.onClimbable()
                || player.isInPowderSnow
                || player.hurtTime > 0
                || player.hasEffect(MobEffects.LEVITATION)
                || player.hasEffect(MobEffects.SLOW_FALLING)
                || player.hasEffect(MobEffects.JUMP_BOOST);
    }

    private static double defaultSpeed(Player player) {
        double speed = 0.28;
        MobEffectInstance effect = player.getEffect(MobEffects.SPEED);
        if (effect != null)
            speed *= 1.0 + 0.2 * (effect.getAmplifier() + 1);
        return speed;
    }

    private static float movementYaw(Player player, double motionX, double motionZ) {
        if (Math.abs(motionX) < 1.0E-6 && Math.abs(motionZ) < 1.0E-6)
            return player.getYRot();
        return (float) Math.toDegrees(Math.atan2(-motionX, motionZ));
    }

    private boolean isAir(Player player, double below) {
        return mc.level.getBlockState(BlockPos.containing(player.getX(), player.getY() - below, player.getZ())).isAir();
    }

    private boolean hasBlockBelow(Player player) {
        int x = Mth.floor(player.getX());
        int z = Mth.floor(player.getZ());
        for (int y = Mth.floor(player.getY()); y >= mc.level.getMinY(); y--) {
            if (!mc.level.getBlockState(new BlockPos(x, y, z)).isAir())
                return true;
        }
        return false;
    }

    private void alert(Player player, PlayerData data, Check check) {
        if (!data.alerted.add(check))
            return;

        String name = player.getGameProfile().name();
        Client.notificationManager.addNotification("Hacker Detected", name + " using " + check.label);
    }
}
