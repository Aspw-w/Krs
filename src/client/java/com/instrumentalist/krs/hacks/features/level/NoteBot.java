package com.instrumentalist.krs.hacks.features.level;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.TickEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.ChatUtil;
import com.instrumentalist.krs.utils.entity.PlayerUtil;
import com.instrumentalist.krs.utils.math.BehaviorUtils;
import com.instrumentalist.krs.utils.notebot.NbsSongDecoder;
import com.instrumentalist.krs.utils.notebot.Note;
import com.instrumentalist.krs.utils.notebot.Song;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.krs.utils.value.ListValue;
import com.instrumentalist.krs.utils.value.TextValue;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class NoteBot extends Module {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long LOAD_TIMEOUT_NANOS = 60_000_000_000L;

    @Setting
    private final TextValue nbsPath = new TextValue("NBS Path", "");

    @Setting
    private final ListValue mode = new ListValue(
            "Mode",
            new String[]{"Exact Instruments", "Any Instrument"},
            "Exact Instruments"
    );

    @Setting
    private final ListValue instrumentDetect = new ListValue(
            "Instrument Detect",
            new String[]{"Block State", "Below Block"},
            "Block State",
            this::isExactModeSelected
    );

    @Setting
    private final IntValue tuneDelay = new IntValue("Tune Delay", 1, 1, 20, "t");

    @Setting
    private final IntValue concurrentTuneBlocks = new IntValue("Concurrent Tune Blocks", 1, 1, 20);

    @Setting
    private final IntValue recheckDelay = new IntValue("Recheck Delay", 10, 1, 40, "t");

    @Setting
    private final BooleanValue polyphonic = new BooleanValue("Polyphonic", true);

    @Setting
    private final BooleanValue autoRotate = new BooleanValue("Auto Rotate", true);

    @Setting
    private final BooleanValue swingArm = new BooleanValue("Swing Arm", true);

    @Setting
    private final BooleanValue roundOutOfRange = new BooleanValue("Round Out Of Range", false);

    @Setting
    private final BooleanValue loop = new BooleanValue("Loop", false);

    private final Map<Note, BlockPos> noteBlockPositions = new LinkedHashMap<>();
    private final Map<BlockPos, Integer> tuneHits = new LinkedHashMap<>();
    private CompletableFuture<Song> loadingSong;
    private Song song;
    private Stage stage = Stage.Idle;
    private long loadStartedAt;
    private int currentTick;
    private int tuneTickCounter;
    private int waitTicks;
    private boolean tunedSinceCheck;
    private boolean activeExactInstruments;
    private boolean activeBelowBlockDetection;
    private boolean controllingRotation;

    public NoteBot() {
        super("Note Bot", ModuleCategory.Level, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    @Override
    public String description() {
        return "Loads an NBS file, tunes nearby note blocks, and plays the song";
    }

    @Override
    public String tag() {
        if (stage == Stage.Playing && song != null)
            return currentTick + "/" + song.lastTick();
        return stage.displayName;
    }

    @Override
    public void onEnable() {
        resetRuntime();

        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            failAndDisable("Join a world before enabling the module.", null);
            return;
        }

        Path path = resolveSongPath();
        if (path == null)
            return;

        activeExactInstruments = isExactModeSelected();
        activeBelowBlockDetection = instrumentDetect.get().equalsIgnoreCase("Below Block");
        boolean exactInstruments = activeExactInstruments;
        boolean shouldRoundOutOfRange = roundOutOfRange.get();
        stage = Stage.Loading;
        loadStartedAt = System.nanoTime();
        notifyPlayer("Loading " + path.getFileName() + "...");
        loadingSong = CompletableFuture.supplyAsync(() -> {
            try {
                return NbsSongDecoder.decode(path, exactInstruments, shouldRoundOutOfRange);
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        });
    }

    @Override
    public void onDisable() {
        boolean shouldReleaseRotation = controllingRotation;
        resetRuntime();
        BehaviorUtils.noKillAura = false;
        if (shouldReleaseRotation && Client.rotationManager != null)
            Client.rotationManager.stopRotation();
    }

    @Override
    public void onWorld(WorldEvent event) {
        if (tempEnabled)
            setState(false);
    }

    @Override
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            failAndDisable("World is no longer available.", null);
            return;
        }

        BehaviorUtils.noKillAura = true;
        if (!autoRotate.get() && controllingRotation) {
            Client.rotationManager.stopRotation();
            controllingRotation = false;
        }

        switch (stage) {
            case Loading -> pollSongLoad();
            case SetUp -> setUpNoteBlocks();
            case Tuning -> tuneNoteBlocks();
            case WaitingToVerify -> waitToVerifyTuning();
            case Playing -> playCurrentTick();
            case Idle -> {
            }
        }
    }

    private Path resolveSongPath() {
        String rawPath = nbsPath.get() == null ? "" : nbsPath.get().trim();
        if (rawPath.length() >= 2) {
            char first = rawPath.charAt(0);
            char last = rawPath.charAt(rawPath.length() - 1);
            if (first == last && (first == '\"' || first == '\''))
                rawPath = rawPath.substring(1, rawPath.length() - 1).trim();
        }

        if (rawPath.isEmpty()) {
            failAndDisable("Set NBS Path before enabling the module.", null);
            return null;
        }

        final Path configuredPath;
        try {
            configuredPath = Path.of(rawPath);
        } catch (InvalidPathException exception) {
            failAndDisable("NBS Path is invalid: " + exception.getMessage(), exception);
            return null;
        }

        Path resolvedPath = configuredPath.isAbsolute()
                ? configuredPath.normalize()
                : mc.gameDirectory.toPath().resolve(configuredPath).normalize();
        String fileName = resolvedPath.getFileName() == null ? "" : resolvedPath.getFileName().toString();

        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".nbs")) {
            failAndDisable("NBS Path must point to an .nbs file.", null);
            return null;
        }
        if (!Files.isRegularFile(resolvedPath) || !Files.isReadable(resolvedPath)) {
            failAndDisable("NBS file was not found or is not readable: " + resolvedPath, null);
            return null;
        }

        return resolvedPath;
    }

    private void pollSongLoad() {
        CompletableFuture<Song> future = loadingSong;
        if (future == null) {
            failAndDisable("Song loading stopped unexpectedly.", null);
            return;
        }

        if (!future.isDone()) {
            if (System.nanoTime() - loadStartedAt > LOAD_TIMEOUT_NANOS) {
                future.cancel(true);
                failAndDisable("Loading the NBS file timed out.", null);
            }
            return;
        }

        loadingSong = null;
        try {
            song = future.join();
        } catch (CancellationException exception) {
            failAndDisable("Loading the NBS file was cancelled.", exception);
            return;
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            failAndDisable("Could not load the NBS file: " + readableMessage(cause), cause);
            return;
        }

        String songName = song.title().isBlank() ? "NBS song" : displayMetadata(song.title());
        String author = song.author().isBlank() ? "" : " by " + displayMetadata(song.author());
        notifyPlayer("Loaded " + songName + author + " (" + song.requirements().size() + " unique notes).");

        if (song.ignoredCustomNotes() > 0)
            notifyPlayer("Ignored " + song.ignoredCustomNotes() + " custom-instrument note(s).");
        if (song.ignoredOutOfRangeNotes() > 0)
            notifyPlayer("Ignored " + song.ignoredOutOfRangeNotes() + " out-of-range note(s).");
        if (song.roundedOutOfRangeNotes() > 0)
            notifyPlayer("Rounded " + song.roundedOutOfRangeNotes() + " out-of-range note(s).");

        stage = Stage.SetUp;
    }

    private void setUpNoteBlocks() {
        List<ScannedNoteBlock> scannedBlocks = scanForNoteBlocks();
        if (scannedBlocks.isEmpty()) {
            failAndDisable("No reachable note blocks with air above them were found.", null);
            return;
        }

        mapRequiredNotes(scannedBlocks);
        if (noteBlockPositions.isEmpty()) {
            failAndDisable("Nearby note blocks cannot play any notes required by this song.", null);
            return;
        }

        if (!prepareTuneHits())
            return;

        int missingNotes = song.requirements().size() - noteBlockPositions.size();
        String missingSuffix = missingNotes > 0 ? "; " + missingNotes + " missing" : "";
        notifyPlayer("Mapped " + noteBlockPositions.size() + "/" + song.requirements().size() + " unique notes" + missingSuffix + ".");
        stage = Stage.Tuning;
    }

    private List<ScannedNoteBlock> scanForNoteBlocks() {
        List<ScannedNoteBlock> scannedBlocks = new ArrayList<>();
        int radius = (int) Math.ceil(mc.player.blockInteractionRange()) + 1;
        BlockPos origin = mc.player.blockPosition();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    BlockState state = mc.level.getBlockState(pos);
                    if (state.getBlock() != Blocks.NOTE_BLOCK || !mc.level.getBlockState(pos.above()).isAir())
                        continue;
                    if (!mc.player.isWithinBlockInteractionRange(pos, 1.0))
                        continue;

                    NoteBlockInstrument instrument = null;
                    if (activeExactInstruments) {
                        instrument = activeBelowBlockDetection
                                ? mc.level.getBlockState(pos.below()).instrument()
                                : state.getValue(NoteBlock.INSTRUMENT);
                    }

                    scannedBlocks.add(new ScannedNoteBlock(
                            pos.immutable(),
                            new Note(instrument, state.getValue(NoteBlock.NOTE)),
                            mc.player.distanceToSqr(Vec3.atCenterOf(pos))
                    ));
                }
            }
        }

        scannedBlocks.sort(Comparator.comparingDouble(ScannedNoteBlock::distanceSquared));
        return scannedBlocks;
    }

    private void mapRequiredNotes(List<ScannedNoteBlock> scannedBlocks) {
        noteBlockPositions.clear();
        List<Note> requiredNotes = new ArrayList<>(song.requirements());
        requiredNotes.sort(Comparator
                .comparingInt((Note note) -> note.instrument() == null ? -1 : note.instrument().ordinal())
                .thenComparingInt(Note::level));

        Set<BlockPos> usedPositions = new LinkedHashSet<>();

        for (Note required : requiredNotes) {
            ScannedNoteBlock exactMatch = findAvailableBlock(scannedBlocks, usedPositions, required, true);
            if (exactMatch != null) {
                noteBlockPositions.put(required, exactMatch.pos());
                usedPositions.add(exactMatch.pos());
            }
        }

        for (Note required : requiredNotes) {
            if (noteBlockPositions.containsKey(required))
                continue;

            ScannedNoteBlock tunableMatch = findAvailableBlock(scannedBlocks, usedPositions, required, false);
            if (tunableMatch != null) {
                noteBlockPositions.put(required, tunableMatch.pos());
                usedPositions.add(tunableMatch.pos());
            }
        }

        if (noteBlockPositions.size() < requiredNotes.size()) {
            List<String> examples = new ArrayList<>();
            for (Note required : requiredNotes) {
                if (noteBlockPositions.containsKey(required))
                    continue;
                examples.add(formatNote(required));
                if (examples.size() == 5)
                    break;
            }
            notifyPlayer("Missing note blocks for: " + String.join(", ", examples)
                    + (requiredNotes.size() - noteBlockPositions.size() > examples.size() ? ", ..." : ""));
        }
    }

    private ScannedNoteBlock findAvailableBlock(
            List<ScannedNoteBlock> scannedBlocks,
            Set<BlockPos> usedPositions,
            Note required,
            boolean requireCurrentNote
    ) {
        for (ScannedNoteBlock scanned : scannedBlocks) {
            if (usedPositions.contains(scanned.pos()))
                continue;
            if (activeExactInstruments && scanned.note().instrument() != required.instrument())
                continue;
            if (requireCurrentNote && scanned.note().level() != required.level())
                continue;
            return scanned;
        }
        return null;
    }

    private boolean prepareTuneHits() {
        tuneHits.clear();

        for (Map.Entry<Note, BlockPos> entry : noteBlockPositions.entrySet()) {
            BlockState state = mc.level.getBlockState(entry.getValue());
            if (state.getBlock() != Blocks.NOTE_BLOCK) {
                failAndDisable("A mapped note block is no longer available.", null);
                return false;
            }

            int currentLevel = state.getValue(NoteBlock.NOTE);
            int requiredHits = Math.floorMod(entry.getKey().level() - currentLevel, 25);
            if (requiredHits > 0)
                tuneHits.put(entry.getValue(), requiredHits);
        }

        tuneTickCounter = 0;
        return true;
    }

    private void tuneNoteBlocks() {
        if (tuneHits.isEmpty()) {
            if (tunedSinceCheck) {
                tunedSinceCheck = false;
                waitTicks = recheckDelay.get();
                stage = Stage.WaitingToVerify;
                return;
            }

            startPlayback();
            return;
        }

        if (++tuneTickCounter < tuneDelay.get())
            return;
        tuneTickCounter = 0;

        int tunedThisTick = 0;
        Iterator<Map.Entry<BlockPos, Integer>> iterator = tuneHits.entrySet().iterator();
        while (iterator.hasNext() && tunedThisTick < concurrentTuneBlocks.get()) {
            Map.Entry<BlockPos, Integer> entry = iterator.next();
            BlockPos pos = entry.getKey();

            if (!canReachMappedBlock(pos)) {
                failAndDisable("Move closer to the mapped note blocks and try again.", null);
                return;
            }

            if (autoRotate.get())
                rotateTo(pos);

            try {
                BlockHitResult hitResult = PlayerUtil.INSTANCE.blockHitResult(pos);
                mc.gameMode.startPrediction(mc.level, sequence ->
                        new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, hitResult, sequence));
            } catch (RuntimeException exception) {
                failAndDisable("Could not tune a note block: " + readableMessage(exception), exception);
                return;
            }

            int remainingHits = entry.getValue() - 1;
            if (remainingHits <= 0)
                iterator.remove();
            else
                entry.setValue(remainingHits);

            tunedSinceCheck = true;
            tunedThisTick++;
        }

        if (tunedThisTick > 0 && swingArm.get())
            mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private void waitToVerifyTuning() {
        if (--waitTicks > 0)
            return;

        if (!prepareTuneHits())
            return;

        stage = Stage.Tuning;
    }

    private void startPlayback() {
        if (mc.player.getAbilities().instabuild) {
            failAndDisable("Survival or adventure mode is required; creative attacks break note blocks.", null);
            return;
        }

        currentTick = 0;
        stage = Stage.Playing;
        notifyPlayer("Tuning complete. Playing.");
    }

    private void playCurrentTick() {
        if (song == null) {
            failAndDisable("No song is loaded.", null);
            return;
        }

        if (currentTick > song.lastTick()) {
            onSongFinished();
            return;
        }

        List<Note> notes = song.notesAt(currentTick);
        if (!notes.isEmpty()) {
            List<BlockPos> playablePositions = new ArrayList<>(notes.size());
            for (Note note : notes) {
                BlockPos pos = noteBlockPositions.get(note);
                if (pos != null)
                    playablePositions.add(pos);
            }

            if (!playablePositions.isEmpty()) {
                int noteLimit = polyphonic.get() ? playablePositions.size() : 1;
                for (int i = 0; i < noteLimit; i++) {
                    if (!canReachMappedBlock(playablePositions.get(i))) {
                        failAndDisable("Move closer to the mapped note blocks and try again.", null);
                        return;
                    }
                }

                if (autoRotate.get())
                    rotateTo(playablePositions.getFirst());
                if (swingArm.get())
                    mc.player.swing(InteractionHand.MAIN_HAND);

                for (int i = 0; i < noteLimit; i++) {
                    BlockPos pos = playablePositions.get(i);
                    try {
                        mc.gameMode.startPrediction(mc.level, sequence -> new ServerboundPlayerActionPacket(
                                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                                pos,
                                Direction.DOWN,
                                sequence
                        ));
                    } catch (RuntimeException exception) {
                        failAndDisable("Could not play a note block: " + readableMessage(exception), exception);
                        return;
                    }
                }
            }
        }

        currentTick++;
    }

    private void onSongFinished() {
        if (loop.get()) {
            if (!prepareTuneHits())
                return;
            tunedSinceCheck = false;
            stage = Stage.Tuning;
            notifyPlayer("Song finished; starting the next loop.");
            return;
        }

        notifyPlayer("Song finished.");
        setState(false);
    }

    private boolean canReachMappedBlock(BlockPos pos) {
        return mc.level.getBlockState(pos).getBlock() == Blocks.NOTE_BLOCK
                && mc.level.getBlockState(pos.above()).isAir()
                && mc.player.isWithinBlockInteractionRange(pos, 1.0);
    }

    private void rotateTo(BlockPos pos) {
        float[] rotations = Client.rotationManager.getRotationsTo(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5
        );
        Client.rotationManager.startRotation(rotations[0], rotations[1], 180.0f);
        controllingRotation = true;
        PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Rot(
                rotations[0],
                rotations[1],
                mc.player.onGround(),
                mc.player.horizontalCollision
        ));
    }

    private boolean isExactModeSelected() {
        return mode.get().equalsIgnoreCase("Exact Instruments");
    }

    private void resetRuntime() {
        CompletableFuture<Song> future = loadingSong;
        loadingSong = null;
        if (future != null)
            future.cancel(true);

        noteBlockPositions.clear();
        tuneHits.clear();
        song = null;
        stage = Stage.Idle;
        loadStartedAt = 0L;
        currentTick = 0;
        tuneTickCounter = 0;
        waitTicks = 0;
        tunedSinceCheck = false;
        activeExactInstruments = false;
        activeBelowBlockDetection = false;
        controllingRotation = false;
    }

    private void failAndDisable(String message, Throwable failure) {
        notifyPlayer(message);
        if (failure != null)
            LOGGER.error("Note Bot failed: {}", message, failure);
        if (tempEnabled)
            setState(false);
    }

    private void notifyPlayer(String message) {
        ChatUtil.printChat("[Note Bot] " + message);
    }

    private static String readableMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private static String displayMetadata(String value) {
        String normalized = value.replace('\r', ' ').replace('\n', ' ').trim();
        return normalized.length() <= 96 ? normalized : normalized.substring(0, 93) + "...";
    }

    private static String formatNote(Note note) {
        String instrument = note.instrument() == null ? "Any" : note.instrument().name();
        return instrument + ":" + note.level();
    }

    private record ScannedNoteBlock(BlockPos pos, Note note, double distanceSquared) {
    }

    private enum Stage {
        Idle("Idle"),
        Loading("Loading"),
        SetUp("Setup"),
        Tuning("Tuning"),
        WaitingToVerify("Verifying"),
        Playing("Playing");

        private final String displayName;

        Stage(String displayName) {
            this.displayName = displayName;
        }
    }
}
