package dev.skysurveyor.journal;

import java.nio.file.Path;
import java.util.concurrent.Executor;

// append per shaft, crash loses only the current one
public final class JournalFile extends JsonLinesFile<JournalEntry> {
	public JournalFile(Path file, Executor io) {
		super(file, JournalEntry.class, JournalFile::complete, io);
	}

	// in every entry since 0.2.x, null = npe in hud + stats
	private static boolean complete(JournalEntry entry) {
		return entry.exit() != null && entry.corpses() != null && entry.loots() != null && entry.mayhemBuffs() != null
				&& entry.campfiresAt() != null && entry.pristineItems() != null && entry.sacksAdded() != null
				&& entry.sacksRemoved() != null;
	}
}
