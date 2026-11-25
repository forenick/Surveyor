package dev.skysurveyor.util;

import java.util.Locale;
import java.util.regex.Pattern;

public final class Format {
	private static final Pattern HEX = Pattern.compile("#[0-9A-Fa-f]{6}");

	private Format() {
	}

	// "#RRGGBB"
	public static String hex(int rgb) {
		return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
	}

	// null = not #RRGGBB
	public static Integer rgb(String hex) {
		if (hex == null || !HEX.matcher(hex).matches()) return null;
		return Integer.parseInt(hex.substring(1), 16);
	}

	// 75 min = "1h 15m", hud time differs on purpose
	public static String duration(long millis) {
		long seconds = Math.max(0, millis) / 1000;
		if (seconds < 60) return seconds + "s";
		if (seconds < 3600) return seconds / 60 + "m " + seconds % 60 + "s";
		return String.format(Locale.ROOT, "%dh %02dm", seconds / 3600, seconds % 3600 / 60);
	}

	public static String number(long value) {
		return String.format(Locale.ROOT, "%,d", value);
	}

	public static String decimal(double value) {
		return String.format(Locale.ROOT, "%.1f", value);
	}

	// 0.69 = "69%"
	public static String percent(double fraction) {
		return Math.round(fraction * 100) + "%";
	}

	public static String percent(long part, long of) {
		return percent(of == 0 ? 0 : part / (double) of);
	}
}
