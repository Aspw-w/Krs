package com.instrumentalist.krs.utils.notebot;

import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class NbsSongDecoder {
    private static final int NOTE_OFFSET = 33;
    private static final int MAX_STRING_BYTES = 16 * 1024 * 1024;

    private NbsSongDecoder() {
    }

    public static Song decode(Path songPath, boolean exactInstruments, boolean roundOutOfRange) throws IOException {
        try (InputStream input = Files.newInputStream(songPath);
             DataInputStream data = new DataInputStream(new BufferedInputStream(input))) {
            return decode(data, exactInstruments, roundOutOfRange);
        }
    }

    private static Song decode(DataInputStream data, boolean exactInstruments, boolean roundOutOfRange) throws IOException {
        int songLength = readUnsignedShortLE(data);
        int version = 0;

        if (songLength == 0) {
            version = data.readUnsignedByte();
            data.readUnsignedByte(); // First custom instrument index.
            if (version >= 3)
                songLength = readUnsignedShortLE(data);
        }

        readUnsignedShortLE(data); // Song height.
        String title = readString(data);
        String author = readString(data);
        readString(data); // Original author.
        readString(data); // Description.

        float tempo = readUnsignedShortLE(data) / 100.0f;
        if (!Float.isFinite(tempo) || tempo <= 0.0f)
            throw new IOException("NBS song has an invalid tempo");

        data.readUnsignedByte(); // Auto-save enabled.
        data.readUnsignedByte(); // Auto-save duration.
        data.readUnsignedByte(); // Time signature.
        readIntLE(data); // Minutes spent on project.
        readIntLE(data); // Left clicks.
        readIntLE(data); // Right clicks.
        readIntLE(data); // Blocks added.
        readIntLE(data); // Blocks removed.
        readString(data); // MIDI/schematic source file.

        if (version >= 4) {
            data.readUnsignedByte(); // Loop enabled.
            data.readUnsignedByte(); // Maximum loop count.
            readUnsignedShortLE(data); // Loop start tick.
        }

        Map<Integer, List<Note>> notesByTick = new LinkedHashMap<>();
        int ignoredCustomNotes = 0;
        int ignoredOutOfRangeNotes = 0;
        int roundedOutOfRangeNotes = 0;
        double minecraftTick = -1.0;

        while (true) {
            int jumpTicks = readUnsignedShortLE(data);
            if (jumpTicks == 0)
                break;

            minecraftTick += jumpTicks * (20.0 / tempo);
            if (!Double.isFinite(minecraftTick) || minecraftTick > Integer.MAX_VALUE)
                throw new IOException("NBS song duration is too large");

            while (true) {
                int jumpLayers = readUnsignedShortLE(data);
                if (jumpLayers == 0)
                    break;

                int instrumentId = data.readUnsignedByte();
                int key = data.readUnsignedByte();

                if (version >= 4) {
                    data.readUnsignedByte(); // Velocity.
                    data.readUnsignedByte(); // Panning.
                    readUnsignedShortLE(data); // Fine pitch.
                }

                NoteBlockInstrument instrument = fromNbsInstrument(instrumentId);
                if (instrument == null) {
                    ignoredCustomNotes++;
                    continue;
                }

                int noteLevel = key - NOTE_OFFSET;
                if (noteLevel < 0 || noteLevel > 24) {
                    if (!roundOutOfRange) {
                        ignoredOutOfRangeNotes++;
                        continue;
                    }

                    noteLevel = Math.max(0, Math.min(24, noteLevel));
                    roundedOutOfRangeNotes++;
                }

                int tick = (int) Math.round(minecraftTick);
                Note note = new Note(exactInstruments ? instrument : null, noteLevel);
                notesByTick.computeIfAbsent(tick, ignored -> new ArrayList<>()).add(note);
            }
        }

        try {
            return new Song(
                    notesByTick,
                    title,
                    author,
                    ignoredCustomNotes,
                    ignoredOutOfRangeNotes,
                    roundedOutOfRangeNotes
            );
        } catch (IllegalArgumentException exception) {
            throw new IOException(exception.getMessage(), exception);
        }
    }

    private static int readUnsignedShortLE(DataInputStream data) throws IOException {
        int low = data.readUnsignedByte();
        int high = data.readUnsignedByte();
        return low | high << 8;
    }

    private static int readIntLE(DataInputStream data) throws IOException {
        int byte1 = data.readUnsignedByte();
        int byte2 = data.readUnsignedByte();
        int byte3 = data.readUnsignedByte();
        int byte4 = data.readUnsignedByte();
        return byte1 | byte2 << 8 | byte3 << 16 | byte4 << 24;
    }

    private static String readString(DataInputStream data) throws IOException {
        int length = readIntLE(data);
        if (length < 0)
            throw new EOFException("NBS string length cannot be negative: " + length);
        if (length > MAX_STRING_BYTES)
            throw new IOException("NBS string is too large: " + length + " bytes");

        byte[] bytes = data.readNBytes(length);
        if (bytes.length != length)
            throw new EOFException("Unexpected end of NBS string");

        return new String(bytes, StandardCharsets.UTF_8).replace('\r', ' ');
    }

    private static NoteBlockInstrument fromNbsInstrument(int instrument) {
        return switch (instrument) {
            case 0 -> NoteBlockInstrument.HARP;
            case 1 -> NoteBlockInstrument.BASS;
            case 2 -> NoteBlockInstrument.BASEDRUM;
            case 3 -> NoteBlockInstrument.SNARE;
            case 4 -> NoteBlockInstrument.HAT;
            case 5 -> NoteBlockInstrument.GUITAR;
            case 6 -> NoteBlockInstrument.FLUTE;
            case 7 -> NoteBlockInstrument.BELL;
            case 8 -> NoteBlockInstrument.CHIME;
            case 9 -> NoteBlockInstrument.XYLOPHONE;
            case 10 -> NoteBlockInstrument.IRON_XYLOPHONE;
            case 11 -> NoteBlockInstrument.COW_BELL;
            case 12 -> NoteBlockInstrument.DIDGERIDOO;
            case 13 -> NoteBlockInstrument.BIT;
            case 14 -> NoteBlockInstrument.BANJO;
            case 15 -> NoteBlockInstrument.PLING;
            default -> null;
        };
    }
}
