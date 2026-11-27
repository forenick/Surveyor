package dev.skysurveyor.journal;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;

import dev.skysurveyor.data.Ladder;
import dev.skysurveyor.detection.Shaft;
import dev.skysurveyor.detection.TextPatterns;

// sacks - compaction - corpse loot - rare drops - supercrafts, tail estimated (research/06, 07)
// misses silent spread in the tail, bit low
// same gem mob drops w/o RARE DROP! count as mined (research/07)
public record GemYield(String gem, long rough, long flawed, long fine, long tailRough, long tailFlawed) {
	private static final int ROUGH = 0;
	private static final int FLAWED = 1;
	private static final int FINE = 2;
	private static final int FLAWLESS = 3;
	// 80:1 per step (research/05)
	public static final int RECIPE = (int) Ladder.gem().per(FINE, FLAWLESS);

	// incl tail
	public double fineEquivalent() {
		return toFine(rough + tailRough, flawed + tailFlawed, fine);
	}

	public double reportedFine() {
		return toFine(rough, flawed, fine);
	}

	private static double toFine(long rough, long flawed, long fine) {
		return Ladder.gem().in(new long[] {rough, flawed, fine, 0}, FINE);
	}

	// anything else counts as fine
	public static double toFine(String quality, long count) {
		return count / countPerFine(quality);
	}

	// name from ladders.txt
	public static String fineName(String gem) {
		return Ladder.gem().name(FINE, gem);
	}

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
		MinedCount mined = MinedCount.of(Ladder.gem(), gemOf, shaft.sacksNet(), shaft.notMinedInSacks());
		MinedCount tail = MinedCount.of(Ladder.gem(), gemOf, shaft.pristineSinceSacks(), Map.of());
		String gem = mined.material() != null ? mined.material() : tail.material();
		if (gem == null) return null;

		// tail ratio from gross mix, compaction would skew it
		MinedCount gross = MinedCount.of(Ladder.gem(), gemOf, shaft.sacksBeforeCompacting(), shaft.notMinedInSacks());
		long tailFlawed = tail.counts()[FLAWED];
		GemYield yield = new GemYield(gem, mined.at(ROUGH), mined.at(FLAWED), mined.at(FINE),
				tailRough(tailFlawed, gross.at(ROUGH), gross.at(FLAWED)), tailFlawed);
		// loot only = none mined, not 0.0
		return yield.rough + yield.flawed + yield.fine + yield.tailRough + yield.tailFlawed > 0 ? yield : null;
	}

	// tail flawed only, rough in reported ratio
	static long tailRough(long tailFlawed, long rough, long flawed) {
		return flawed > 0 ? Math.round(tailFlawed * (double) rough / flawed) : 0;
	}

	// item order
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

	// ROOT bc turkish locale gives "CİTRİNE" lol
	private static Matcher shaftGem(String item, String type, TextPatterns patterns) {
		if (type == null) return null;
		Matcher m = patterns.match("gem.item", item);
		return m != null && m.group("gem").toUpperCase(Locale.ROOT).startsWith(type) ? m : null;
	}
}
