package dev.skysurveyor.calc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.skysurveyor.util.Format;

public final class FiestaText {
	private FiestaText() {
	}

	// flaskLeft in ms, null = not running
	public static String tile(Fiesta.Reading reading, Long flaskLeft) {
		if (reading.on() == null) return "§eUnknown";
		if (!reading.on()) return "§7Off";
		return flaskLeft == null ? "§aOn" : "§aOn + flask §7" + minutes(flaskLeft);
	}

	public static List<String> tip(Fiesta.Reading reading, Long flaskLeft, long now) {
		List<String> lines = new ArrayList<>();
		lines.add("§fMining Fiesta " + (reading.on() == null ? "unknown" : reading.on() ? "on" : "off"));
		lines.add("§7Cole's perk. Gems per block, other drops and");
		lines.add("§7coins per spawn are tracked apart with it on");
		if (reading.on() == null) {
			lines.add("");
			lines.add("§8Checked when you enter a shaft or open the");
			lines.add("§8journal. Or open the tab list in the Hub");
			return lines;
		}
		if (reading.on() && flaskLeft != null) lines.add("§7Fiesta Flask §f" + minutes(flaskLeft) + " §7left");
		lines.add("");
		lines.add("§8" + switch (reading.source()) {
			case ELECTION -> "From Hypixel's election data, " + Format.ago(now - reading.at());
			case DROP -> "From a Glossy Gemstone or Refined Mineral, " + Format.ago(now - reading.at());
			case TAB_LIST -> (reading.on() ? "Cole in the tab list, " : "No Cole in the tab list, ") + Format.ago(now - reading.at());
		});
		return lines;
	}

	public static String debug(Fiesta.Reading reading, Long flaskLeft, long now) {
		if (reading.on() == null) return "unknown";
		return (reading.on() ? "on" : "off") + " (" + reading.source().name().toLowerCase(Locale.ROOT) + ", " + Format.ago(now - reading.at())
				+ ")" + (flaskLeft == null ? "" : ", flask " + minutes(flaskLeft));
	}

	private static String minutes(long millis) {
		long minutes = Math.max(0, millis) / 60_000;
		return minutes < 1 ? "<1m" : minutes + "m";
	}
}
