package dev.skysurveyor.mining;

// own / nonstop blocks, research/11 E w/o spread + setup
public final class Efficiency {
	// display choices
	public static final int MIN_BLOCKS = 100;
	public static final long RECORD_MILLIS = 5 * 60_000L;

	private Efficiency() {
	}

	// one break time missing = other one for both
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

	// 0.69 = 69%
	public static Double of(MiningStats stats) {
		if (stats == null || stats.blocks() <= 0) return null;
		return ratio(stats.blocks(), nonstop(stats));
	}

	// w/ setup, main number leaves it out (nick 11-21)
	// null w/o non-boost time, boost undercounts setup
	public static Double withSetup(MiningStats stats) {
		if (shown(stats) == null || stats.ticks() == null) return null;
		return ratio(stats.blocks(),
				nonstop(stats.millis() + Math.max(0, stats.setupMillis()), stats.boostMillis(), stats.ticks(), stats.boostTicks()));
	}

	private static Double ratio(int blocks, double nonstop) {
		return nonstop <= 0 ? null : blocks / nonstop;
	}

	// pb, null under RECORD_MILLIS
	public static Double record(MiningStats stats) {
		return stats == null || stats.millis() < RECORD_MILLIS ? null : shown(stats);
	}

	// null under MIN_BLOCKS or broken
	public static Double shown(MiningStats stats) {
		return stats == null || stats.blocks() < MIN_BLOCKS || broken(stats) ? null : of(stats);
	}

	// alpha.12-16 counted hard stone, 4,475 spread on 1,949 blocks lol (rec 8)
	// file stays, only hidden (nick 11-21)
	static boolean broken(MiningStats stats) {
		return stats.spread() > stats.blocks();
	}
}
