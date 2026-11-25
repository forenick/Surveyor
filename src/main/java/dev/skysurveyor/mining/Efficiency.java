package dev.skysurveyor.mining;

// own blocks / blocks nonstop mining would break (research/11s E w/o spread and setup)
public final class Efficiency {
	// display choices: skipped shafts show nothing, a record needs a whole shaft of mining
	public static final int MIN_BLOCKS = 100;
	public static final long RECORD_MILLIS = 5 * 60_000L;

	private Efficiency() {
	}

	// boost time and the rest each at their own break time. one missing = the other for both
	public static double nonstop(long millis, long boostMillis, Double ticks, Double boostTicks) {
		Double normal = ticks != null ? ticks : boostTicks;
		Double boost = boostTicks != null ? boostTicks : ticks;
		if (normal == null || normal <= 0 || boost <= 0) return 0;
		long boosted = Math.max(0, Math.min(boostMillis, millis));
		double tick = MiningMeter.TICK_MILLIS;
		return boosted / tick / boost + (millis - boosted) / tick / normal;
	}

	public static double nonstop(MiningStats stats) {
		return nonstop(stats.millis(), stats.boostMillis(), stats.ticks(), stats.boostTicks());
	}

	// 0.69 = 69%. null = nothing to say
	public static Double of(MiningStats stats) {
		if (stats == null || stats.blocks() <= 0) return null;
		return ratio(stats.blocks(), nonstop(stats));
	}

	// setup seconds in too, at the break time outside boost. the number itself leaves them out (nick, 2026-11-21)
	// null w/o a break time outside boost, boost ticks would undercount setup
	public static Double withSetup(MiningStats stats) {
		if (shown(stats) == null || stats.ticks() == null) return null;
		return ratio(stats.blocks(),
				nonstop(stats.millis() + Math.max(0, stats.setupMillis()), stats.boostMillis(), stats.ticks(), stats.boostTicks()));
	}

	private static Double ratio(int blocks, double nonstop) {
		return nonstop <= 0 ? null : blocks / nonstop;
	}

	// for the personal best, null = under RECORD_MILLIS of mining
	public static Double record(MiningStats stats) {
		return stats == null || stats.millis() < RECORD_MILLIS ? null : shown(stats);
	}

	// null under MIN_BLOCKS too, and for broken saves (see broken)
	public static Double shown(MiningStats stats) {
		return stats == null || stats.blocks() < MIN_BLOCKS || broken(stats) ? null : of(stats);
	}

	// alpha.12 to 16 counted insta-mined hard stone, its neighbours as spread: 4,475 spread on 1,949 blocks (recording 8)
	// real gem spread is 5 to 17% of the blocks. file stays as is, only hidden (nick, 2026-11-21)
	static boolean broken(MiningStats stats) {
		return stats.spread() > stats.blocks();
	}
}
