package dev.skysurveyor.journal;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;

import dev.skysurveyor.data.Ladder;
import dev.skysurveyor.detection.Shaft;
import dev.skysurveyor.detection.TextPatterns;

// sacks added - corpse loot - supercrafts, tail estimated (research/06, 07)
// silent gemstone spread in the tail is missing, slight undercount
// mob drops of the same gem count as mined (research/07)
public record GemYield(String gem, long rough, long flawed, long fine, long tailRough, long tailFlawed) {
	private static final int ROUGH = 0;
	private static final int FLAWED = 1;
	private static final int FINE = 2;
	private static final int FLAWLESS = 3;
	// 80:1 per step (research/05)
	public static final int RECIPE = (int) Ladder.gem().per(FINE, FLAWLESS);

	// incl estimated tail
	public double fineEquivalent() {
		return toFine(rough + tailRough, flawed + tailFlawed, fine);
	}

	public double reportedFine() {
		return toFine(rough, flawed, fine);
	}

	private static double toFine(long rough, long flawed, long fine) {
		return Ladder.gem().in(new long[] {rough, flawed, fine, 0}, FINE);
	}

	// quality from gem.item, anything else counts as fine
	public static double toFine(String quality, long count) {
		return count / countPerFine(quality);
	}

	// how many of this quality make 1 fine
	public static double countPerFine(String quality) {
		return switch (quality) {
			case "Rough" -> Ladder.gem().per(ROUGH, FINE);
			case "Flawed" -> Ladder.gem().per(FLAWED, FINE);
			default -> 1;
		};
	}

	public static GemYield of(Shaft shaft, TextPatterns patterns) {
		if (shaft.id() == null) return null;
		String type = shaft.id().type();
		Function<String, String> gemOf = item -> {
			Matcher m = shaftGem(item, type, patterns);
			return m == null ? null : m.group("gem");
		};
		MinedCount mined = MinedCount.of(Ladder.gem(), gemOf, shaft.sacksAdded(), shaft.notMinedInSacks());
		MinedCount tail = MinedCount.of(Ladder.gem(), gemOf, shaft.pristineSinceSacks(), Map.of());
		String gem = mined.material() != null ? mined.material() : tail.material();
		if (gem == null) return null;

		long rough = mined.at(ROUGH);
		long flawed = mined.at(FLAWED);
		long tailFlawed = tail.counts()[FLAWED];
		return new GemYield(gem, rough, flawed, mined.at(FINE), tailRough(tailFlawed, rough, flawed), tailFlawed);
	}

	// tail is flawed only, rough in the same ratio as reported
	static long tailRough(long tailFlawed, long rough, long flawed) {
		return flawed > 0 ? Math.round(tailFlawed * (double) rough / flawed) : 0;
	}

	// gem -> fine equivalent, in item order
	public static Map<String, Double> fineByGem(Map<String, Long> items, TextPatterns patterns) {
		Map<String, Double> gems = new LinkedHashMap<>();
		items.forEach((item, count) -> {
			Matcher m = patterns.match("gem.item", item);
			if (m != null) gems.merge(m.group("gem"), toFine(m.group("quality"), count), Double::sum);
		});
		return gems;
	}

	public static boolean isShaftGem(String item, String type, TextPatterns patterns) {
		return shaftGem(item, type, patterns) != null;
	}

	// ROOT bc turkish locale uppercases "Citrine" to "CİTRİNE"
	private static Matcher shaftGem(String item, String type, TextPatterns patterns) {
		if (type == null) return null;
		Matcher m = patterns.match("gem.item", item);
		return m != null && m.group("gem").toUpperCase(Locale.ROOT).startsWith(type) ? m : null;
	}
}
