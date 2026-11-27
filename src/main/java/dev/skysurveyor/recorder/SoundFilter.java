package dev.skysurveyor.recorder;

import java.util.List;

// extra mode: sounds hypixel might ping u with, not every block + step
public final class SoundFilter {
	private static final List<String> BLOCK_NOISE = List.of(".step", ".hit", ".break", ".place", ".fall");

	private SoundFilter() {
	}

	// f.e. block.note_block.pling. note blocks stay, hypixel dings w/ them
	public static boolean keep(String id) {
		if (id.startsWith("ambient.") || id.startsWith("music.") || id.endsWith(".step")) return false;
		if (id.startsWith("block.") && !id.startsWith("block.note_block.")) {
			for (String noise : BLOCK_NOISE) {
				if (id.endsWith(noise)) return false;
			}
		}
		return true;
	}
}
