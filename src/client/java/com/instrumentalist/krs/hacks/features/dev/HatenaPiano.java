package com.instrumentalist.krs.hacks.features.dev;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.ChatUtil;
import com.instrumentalist.krs.utils.math.BehaviorUtils;
import com.instrumentalist.krs.utils.math.RandomUtil;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.krs.utils.value.TextValue;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public class HatenaPiano extends Module {
    @Setting
    private final TextValue song = new TextValue("Song", "C D E F E D C R E F G A G F E R C C C C C C D D E E F F E D C");

    @Setting
    private final IntValue defaultOctave = new IntValue("Default Octave", 1, 1, 4);

    @Setting
    private final IntValue noteTicks = new IntValue("Note Ticks", 4, 2, 40);

    @Setting
    private final IntValue restTicks = new IntValue("Rest Ticks", 0, 0, 20);

    @Setting
    private final FloatValue maxRotationSpeed = new FloatValue("Max Rotation Speed", 180f, 1f, 180f);

    @Setting
    private final FloatValue minRotationSpeed = new FloatValue("Min Rotation Speed", 180f, 1f, 180f);

    @Setting
    private final BooleanValue loop = new BooleanValue("Loop", false);

    private List<Step> steps = List.of();
    private int stepIndex = 0;
    private int waitTicks = 0;
    private int skippedTokens = 0;
    private String firstSkippedToken = "";
    private Step activeStep = null;
    private PianoKey activeKey = null;
    private int rotationStartedTick = -1;
    private boolean waitingForClick = false;
    private boolean releaseRotation = false;

    public HatenaPiano() {
        super("Hatena Piano", ModuleCategory.Dev, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    private static final PianoKey[] WHITE_KEYS = new PianoKey[]{
            new PianoKey(29.1f, 20.1f),
            new PianoKey(31.1f, 21.2f),
            new PianoKey(32.3f, 22.2f),
            new PianoKey(35.0f, 23.4f),
            new PianoKey(37.2f, 24.7f),
            new PianoKey(40.3f, 26.4f),
            new PianoKey(43.7f, 28.0f),
            new PianoKey(47.2f, 29.2f),
            new PianoKey(50.8f, 31.2f),
            new PianoKey(55.6f, 32.8f),
            new PianoKey(61.1f, 34.4f),
            new PianoKey(68.1f, 35.1f),
            new PianoKey(74.4f, 37.2f),
            new PianoKey(80.9f, 37.6f),
            new PianoKey(89.1f, 37.8f),
            new PianoKey(96.5f, 38.1f),
            new PianoKey(104.6f, 37.9f),
            new PianoKey(110.7f, 36.8f),
            new PianoKey(117.3f, 35.1f),
            new PianoKey(123.1f, 33.8f),
            new PianoKey(126.3f, 31.2f),
            new PianoKey(130.9f, 29.8f),
            new PianoKey(134.4f, 27.8f),
            new PianoKey(138.1f, 26.2f),
            new PianoKey(141.2f, 25.0f),
            new PianoKey(143.8f, 23.5f),
            new PianoKey(145.6f, 22.2f),
            new PianoKey(147.3f, 21.1f),
            new PianoKey(148.5f, 20.3f)
    };

    @Override
    public String description() {
        return "White-key player. Use C D E F G A B, C2, K1..K29, R, and :ticks";
    }

    @Override
    public String tag() {
        if (steps.isEmpty()) return "Idle";
        return Math.min(stepIndex + 1, steps.size()) + "/" + steps.size();
    }

    private float getRotationSpeed() {
        float min = minRotationSpeed.get();
        float max = maxRotationSpeed.get();
        return RandomUtil.nextFloat(Math.min(min, max), Math.max(min, max));
    }

    @Override
    public void onEnable() {
        reloadSong();
    }

    @Override
    public void onDisable() {
        resetPlayback();
    }

    @Override
    public void onWorld(WorldEvent event) {
        setState(false);
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.level == null) return;

        if (steps.isEmpty()) {
            notifyPlayer("No playable notes in Song.");
            setState(false);
            return;
        }

        BehaviorUtils.noKillAura = true;

        if (releaseRotation) {
            Client.rotationManager.stopRotation();
            releaseRotation = false;
        }

        if (waitTicks > 0) {
            waitTicks--;
            return;
        }

        if (waitingForClick) {
            if (mc.player.tickCount > rotationStartedTick) {
                if (!rightClick(activeStep)) {
                    setState(false);
                    return;
                }

                waitTicks = Math.max(0, activeStep.lengthTicks() - 2 + restTicks.get());
                stepIndex++;
                activeStep = null;
                activeKey = null;
                rotationStartedTick = -1;
                waitingForClick = false;
                releaseRotation = true;
                return;
            }

            Client.rotationManager.startRotationUnclampedPitch(activeKey.yaw(), activeKey.pitch(), getRotationSpeed());
            return;
        }

        if (stepIndex >= steps.size()) {
            finishSong();
            return;
        }

        Step step = steps.get(stepIndex);
        if (step.isRest()) {
            waitTicks = Math.max(0, step.lengthTicks() - 1);
            stepIndex++;
            return;
        }

        activeStep = step;
        activeKey = WHITE_KEYS[step.keyIndex()];
        rotationStartedTick = mc.player.tickCount;
        waitingForClick = true;
        Client.rotationManager.startRotationUnclampedPitch(activeKey.yaw(), activeKey.pitch(), getRotationSpeed());
    }

    private void reloadSong() {
        skippedTokens = 0;
        firstSkippedToken = "";
        steps = parseSong(song.get());
        stepIndex = 0;
        waitTicks = 0;
        activeStep = null;
        activeKey = null;
        rotationStartedTick = -1;
        waitingForClick = false;
        releaseRotation = false;

        if (skippedTokens > 0) {
            notifyPlayer("Skipped " + skippedTokens + " invalid token(s). First: " + firstSkippedToken);
        }
    }

    private void resetPlayback() {
        stepIndex = 0;
        waitTicks = 0;
        activeStep = null;
        activeKey = null;
        rotationStartedTick = -1;
        waitingForClick = false;
        releaseRotation = false;
        BehaviorUtils.noKillAura = false;
        Client.rotationManager.stopRotation();
    }

    private void finishSong() {
        if (loop.get()) {
            stepIndex = 0;
            waitTicks = 0;
            return;
        }

        notifyPlayer("Song finished.");
        setState(false);
    }

    private boolean rightClick(Step step) {
        Entity target = findTarget(step.keyIndex());
        if (target == null) {
            UUID uuid = targetUuid(step.keyIndex());
            notifyPlayer("Target entity not found: " + uuid);
            return false;
        }

        PacketUtil.sendPacket(new ServerboundInteractPacket(target.getId(), InteractionHand.MAIN_HAND, interactionLocation(target), false));

        return true;
    }

    private Entity findTarget(int keyIndex) {
        UUID targetUuid = targetUuid(keyIndex);
        if (mc.level == null) return null;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (targetUuid.equals(entity.getUUID())) {
                return entity;
            }
        }

        return null;
    }

    private UUID targetUuid(int keyIndex) {
        return switch (Math.min(Math.max(keyIndex / 8, 0), 3)) {
            case 0 -> UUID.fromString("69285a07-1112-495d-9e57-d152bd6da41d");
            case 1 -> UUID.fromString("1cce0b06-b477-43b8-84e6-6dcbc94b478d");
            case 2 -> UUID.fromString("5b3f90ba-d8a4-458b-addc-e844c24b8c04");
            default -> UUID.fromString("18d37ef7-8630-4bb2-b4d5-aadc600f717a");
        };
    }

    private Vec3 interactionLocation(Entity target) {
        Vec3 fallback = target.getBoundingBox().getCenter().subtract(target.position());
        if (mc.player == null) return fallback;

        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 lookVec = getLookVecFromRotations(
                Client.rotationManager.getRotationYaw(),
                Client.rotationManager.getRotationPitch()
        );
        Optional<Vec3> hitPos = target.getBoundingBox().inflate(0.1D).clip(eyePos, eyePos.add(lookVec.scale(128.0D)));
        return hitPos.map(vec3 -> vec3.subtract(target.position())).orElse(fallback);
    }

    private Vec3 getLookVecFromRotations(float yaw, float pitch) {
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);

        float x = -Mth.cos(pitchRad) * Mth.sin(yawRad);
        float y = -Mth.sin(pitchRad);
        float z = Mth.cos(pitchRad) * Mth.cos(yawRad);

        return new Vec3(x, y, z);
    }

    private List<Step> parseSong(String input) {
        ArrayList<Step> parsed = new ArrayList<>();
        if (input == null || input.isBlank()) return parsed;

        String[] tokens = input.replace(',', ' ').replace(';', ' ').split("\\s+");
        for (String token : tokens) {
            Step step = parseStep(token);
            if (step == null) {
                skippedTokens++;
                if (firstSkippedToken.isEmpty()) firstSkippedToken = token;
                continue;
            }

            parsed.add(step);
        }

        return parsed;
    }

    private Step parseStep(String rawToken) {
        if (rawToken == null) return null;

        String token = rawToken.trim();
        if (token.isEmpty()) return null;

        int duration = noteTicks.get();
        int durationSeparator = findDurationSeparator(token);
        if (durationSeparator >= 0) {
            duration = parseDuration(token.substring(durationSeparator + 1), duration);
            token = token.substring(0, durationSeparator);
        }

        String upperToken = token.toUpperCase(Locale.ROOT);
        if (isRestToken(upperToken)) {
            return Step.rest(Math.max(1, duration));
        }

        if (durationSeparator < 0 && upperToken.length() > 1 && upperToken.charAt(0) == 'R' && isInteger(upperToken.substring(1))) {
            return Step.rest(Math.max(1, parseDuration(upperToken.substring(1), duration)));
        }

        int keyIndex = parseKeyIndex(upperToken);
        if (keyIndex >= 0) {
            return Step.note(keyIndex, Math.max(2, duration));
        }

        keyIndex = parseNaturalNoteIndex(upperToken);
        if (keyIndex >= 0) {
            return Step.note(keyIndex, Math.max(2, duration));
        }

        return null;
    }

    private int findDurationSeparator(String token) {
        int colon = token.indexOf(':');
        int slash = token.indexOf('/');

        if (colon < 0) return slash;
        if (slash < 0) return colon;
        return Math.min(colon, slash);
    }

    private int parseDuration(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;

        try {
            return Math.max(1, Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private boolean isRestToken(String token) {
        return token.equals("R")
                || token.equals("REST")
                || token.equals("-")
                || token.equals("_")
                || token.equals(".");
    }

    private int parseKeyIndex(String token) {
        String numeric = token;
        if (token.startsWith("K")) {
            numeric = token.substring(1);
        }

        if (!isInteger(numeric)) return -1;

        int keyNumber = Integer.parseInt(numeric);
        if (keyNumber < 1 || keyNumber > 29) return -1;
        return keyNumber - 1;
    }

    private int parseNaturalNoteIndex(String token) {
        int splitIndex = token.length();
        while (splitIndex > 0 && Character.isDigit(token.charAt(splitIndex - 1))) {
            splitIndex--;
        }

        String noteName = token.substring(0, splitIndex);
        int note = noteOffset(noteName);
        if (note < 0) return -1;

        int octave = defaultOctave.get();
        if (splitIndex < token.length()) {
            octave = parseDuration(token.substring(splitIndex), octave);
        }

        int keyIndex = (octave - 1) * 7 + note;
        if (keyIndex < 0 || keyIndex >= 29) return -1;
        return keyIndex;
    }

    private int noteOffset(String noteName) {
        return switch (noteName) {
            case "C", "DO" -> 0;
            case "D", "RE" -> 1;
            case "E", "MI" -> 2;
            case "F", "FA" -> 3;
            case "G", "SO", "SOL" -> 4;
            case "A", "LA" -> 5;
            case "B", "SI", "TI" -> 6;
            default -> -1;
        };
    }

    private boolean isInteger(String value) {
        if (value == null || value.isEmpty()) return false;

        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) return false;
        }

        return true;
    }

    private void notifyPlayer(String message) {
        if (mc.player != null) {
            ChatUtil.printChat("Piano Player: " + message);
        }
    }

    private record PianoKey(float yaw, float pitch) {
    }

    private record Step(int keyIndex, int lengthTicks) {
        private static Step note(int keyIndex, int lengthTicks) {
            return new Step(keyIndex, lengthTicks);
        }

        private static Step rest(int lengthTicks) {
            return new Step(-1, lengthTicks);
        }

        private boolean isRest() {
            return keyIndex < 0;
        }
    }
}
