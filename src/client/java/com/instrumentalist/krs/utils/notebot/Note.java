package com.instrumentalist.krs.utils.notebot;

import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

public record Note(NoteBlockInstrument instrument, int level) {
}
