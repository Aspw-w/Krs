package com.instrumentalist.krs.utils.notebot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Song {
    private final Map<Integer, List<Note>> notesByTick;
    private final Set<Note> requirements;
    private final String title;
    private final String author;
    private final int lastTick;
    private final int ignoredCustomNotes;
    private final int ignoredOutOfRangeNotes;
    private final int roundedOutOfRangeNotes;

    public Song(
            Map<Integer, List<Note>> notesByTick,
            String title,
            String author,
            int ignoredCustomNotes,
            int ignoredOutOfRangeNotes,
            int roundedOutOfRangeNotes
    ) {
        if (notesByTick == null || notesByTick.isEmpty())
            throw new IllegalArgumentException("Song has no playable notes");

        Map<Integer, List<Note>> copiedNotes = new LinkedHashMap<>();
        Set<Note> requiredNotes = new LinkedHashSet<>();
        int latestTick = 0;

        for (Map.Entry<Integer, List<Note>> entry : notesByTick.entrySet()) {
            if (entry.getKey() == null || entry.getKey() < 0 || entry.getValue() == null || entry.getValue().isEmpty())
                continue;

            List<Note> notes = Collections.unmodifiableList(new ArrayList<>(entry.getValue()));
            copiedNotes.put(entry.getKey(), notes);
            requiredNotes.addAll(notes);
            latestTick = Math.max(latestTick, entry.getKey());
        }

        if (copiedNotes.isEmpty())
            throw new IllegalArgumentException("Song has no playable notes");

        this.notesByTick = Collections.unmodifiableMap(copiedNotes);
        this.requirements = Collections.unmodifiableSet(requiredNotes);
        this.title = title == null ? "" : title;
        this.author = author == null ? "" : author;
        this.lastTick = latestTick;
        this.ignoredCustomNotes = ignoredCustomNotes;
        this.ignoredOutOfRangeNotes = ignoredOutOfRangeNotes;
        this.roundedOutOfRangeNotes = roundedOutOfRangeNotes;
    }

    public List<Note> notesAt(int tick) {
        return notesByTick.getOrDefault(tick, List.of());
    }

    public Set<Note> requirements() {
        return requirements;
    }

    public String title() {
        return title;
    }

    public String author() {
        return author;
    }

    public int lastTick() {
        return lastTick;
    }

    public int ignoredCustomNotes() {
        return ignoredCustomNotes;
    }

    public int ignoredOutOfRangeNotes() {
        return ignoredOutOfRangeNotes;
    }

    public int roundedOutOfRangeNotes() {
        return roundedOutOfRangeNotes;
    }
}
