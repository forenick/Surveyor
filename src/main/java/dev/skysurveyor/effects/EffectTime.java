package dev.skysurveyor.effects;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// "60m", "1h 11m", "51:12", "1:02:03", "4 hours", "6 days, 18 hours". step = the smallest unit shown
public record EffectTime(long millis, long step) {
	private static final Pattern CLOCK = Pattern.compile("^(\\d+)(?::(\\d\\d))(?::(\\d\\d))?$");
	private static final Pattern UNIT = Pattern.compile("(\\d+)\\s*(days?|d|hours?|h|minutes?|mins?|m|seconds?|secs?|s)(?![a-z])");

	public static EffectTime parse(String text) {
		if (text == null) return null;
		String t = text.strip().toLowerCase(Locale.ROOT);
		Matcher clock = CLOCK.matcher(t);
		if (clock.matches()) {
			long a = Long.parseLong(clock.group(1));
			long b = Long.parseLong(clock.group(2));
			long seconds = clock.group(3) == null ? a * 60 + b : (a * 60 + b) * 60 + Long.parseLong(clock.group(3));
			return new EffectTime(seconds * 1000, 1000);
		}
		Matcher unit = UNIT.matcher(t);
		long millis = 0;
		long step = 0;
		int end = 0;
		while (unit.find()) {
			// only units w/ nothing else between them
			if (!t.substring(end, unit.start()).replace(",", "").isBlank()) return null;
			end = unit.end();
			long size = switch (unit.group(2).charAt(0)) {
				case 'd' -> 86_400_000L;
				case 'h' -> 3_600_000L;
				case 'm' -> 60_000L;
				default -> 1000L;
			};
			millis += Long.parseLong(unit.group(1)) * size;
			step = step == 0 ? size : Math.min(step, size);
		}
		if (step == 0 || !t.substring(end).isBlank()) return null;
		return new EffectTime(millis, step);
	}
}
