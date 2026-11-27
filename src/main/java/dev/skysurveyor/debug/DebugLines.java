package dev.skysurveyor.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.skysurveyor.util.Format;

public final class DebugLines {
	private DebugLines() {
	}

	// null = unknown/none
	public record Values(String version, boolean inMines, String shaft, Long sessionId, Integer sessionShafts, Long sinceActivity,
			Long spawnTimer, String lastEvent, Long sinceLastEvent, int unmatched, long errors, Long bazaarAge, Long binsAge, Long ping,
			Double tps, String recording) {
	}

	public static List<String> of(Values v) {
		List<String> lines = new ArrayList<>();
		lines.add("§eSurveyor " + v.version());
		lines.add("§7Area: §f" + (v.shaft() != null ? "shaft " + v.shaft() : v.inMines() ? "mining area" : "elsewhere"));
		lines.add("§7Session: §f" + (v.sessionId() == null ? "none"
				: v.sessionShafts() + " shafts, last mining " + ago(v.sinceActivity())));
		lines.add("§7Spawn timer: §f" + (v.spawnTimer() == null ? "off" : Format.duration(v.spawnTimer())));
		lines.add("§7Last event: §f" + (v.lastEvent() == null ? "none" : v.lastEvent() + " §7" + ago(v.sinceLastEvent())));
		lines.add("§7Unmatched chat: " + (v.unmatched() > 0 ? "§e" : "§f") + v.unmatched() + "§7  Errors: " + (v.errors() > 0 ? "§c" : "§f")
				+ v.errors());
		lines.add("§7Prices: §fBazaar " + ago(v.bazaarAge()) + ", BIN " + ago(v.binsAge()));
		lines.add("§7Ping: §f" + (v.ping() == null ? "-" : v.ping() + " ms") + "§7  TPS: §f"
				+ (v.tps() == null ? "-" : String.format(Locale.ROOT, "%.1f", v.tps())));
		lines.add("§7Recording: §f" + v.recording());
		return lines;
	}

	static String ago(Long millis) {
		if (millis == null || millis < 0) return "never";
		return Format.duration(millis) + " ago";
	}
}
