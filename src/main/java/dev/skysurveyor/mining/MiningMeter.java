package dev.skysurveyor.mining;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import dev.skysurveyor.detection.ShaftEvent;
import dev.skysurveyor.detection.ShaftEvent.AbilityExpired;
import dev.skysurveyor.detection.ShaftEvent.PickaxeAbility;

// measured, never from mining speed (tab list off for gems)
public final class MiningMeter {
	// display choices, not game numbers
	static final int MODE_MIN = 10;
	static final long PAUSE_MILLIS = 5_000;
	static final String BOOST = "Mining Speed Boost";
	static final long TICK_MILLIS = 50;
	// a glance doesnt make it main
	private static final int TIME_CAP = 40;
	// game min, research/11 s8
	static final int MIN_TICKS = 4;

	// clean = no pause till it went
	private record Break(long at, String block, int ticks, boolean clean, boolean boost, int spread) {
	}

	private record Span(long from, long to) {
		long inside(long start, long end) {
			return Math.max(0, Math.min(to, end) - Math.max(from, start));
		}
	}

	private record Kind(String block, boolean boost) {
	}

	private final List<Break> breaks = new ArrayList<>();
	private final List<Span> boosts = new ArrayList<>();
	private final List<Span> pauses = new ArrayList<>();
	private long boostSince = -1;
	private long lastHitAt = -1;

	// no expired msg after a warp (3/3 recs)
	public void onWorldChange() {
		breaks.clear();
		boosts.clear();
		pauses.clear();
		boostSince = -1;
		lastHitAt = -1;
	}

	public void onEvent(ShaftEvent event, long now) {
		switch (event) {
			case PickaxeAbility used when BOOST.equals(used.ability()) -> {
				// missed the expired msg
				endBoost(now);
				boostSince = now;
			}
			case AbilityExpired expired when BOOST.equals(expired.ability()) -> {
				endBoost(now);
				boostSince = -1;
			}
			default -> {
			}
		}
	}

	private void endBoost(long now) {
		if (boostSince >= 0) boosts.add(new Span(boostSince, now));
	}

	public boolean boosting() {
		return boostSince >= 0;
	}

	// -1 = none yet
	public long lastAt() {
		long last = lastHitAt;
		if (!breaks.isEmpty()) last = Math.max(last, breaks.getLast().at());
		return last;
	}

	// boost stays on
	public void startOver() {
		breaks.clear();
		boosts.clear();
		pauses.clear();
		lastHitAt = -1;
	}

	// + every held tick
	public void onHit(long now) {
		if (lastHitAt >= 0 && now - lastHitAt > PAUSE_MILLIS) pauses.add(new Span(lastHitAt, now));
		lastHitAt = now;
	}

	// ticks = 1st hit to gone
	public void onBreak(long now, String block, int ticks, boolean clean, int spread) {
		breaks.add(new Break(now, block, Math.max(0, ticks), clean, boosting(), Math.max(0, spread)));
	}

	// gem = block + pane (rec 7)
	static boolean sameKind(String a, String b) {
		return a.equals(b) || a.equals(b + "_pane") || b.equals(a + "_pane");
	}

	// spread
	public static int spread(String block, List<String> gone) {
		int spread = 0;
		for (String other : gone) {
			if (sameKind(block, other)) spread++;
		}
		return spread;
	}

	// null = nothing or only insta breaks
	public MiningStats stats(long entered, long now) {
		String main = mainBlock();
		if (main == null) return null;
		long start = entered;
		for (Break b : breaks) {
			if (sameKind(b.block(), main)) {
				// 1st hit, not when it went
				double ticks = b.clean() ? b.ticks() : breakTime(new Kind(main, b.boost()), entered);
				start = Math.max(entered, b.at() - Math.round(ticks * TICK_MILLIS));
				break;
			}
		}
		long from = start;
		Map<Kind, Double> times = new HashMap<>();
		int blocks = 0;
		int spread = 0;
		double normalTicks = 0;
		double boostTicks = 0;
		int normal = 0;
		int boosted = 0;
		for (Break b : breaks) {
			// main block only, hard stone was 5,400 in 1 shaft (rec 8)
			if (b.at() < start || !sameKind(b.block(), main)) continue;
			blocks++;
			spread += b.spread();
			double time = times.computeIfAbsent(new Kind(b.block(), b.boost()), kind -> breakTime(kind, from));
			if (b.boost()) {
				boostTicks += time;
				boosted++;
			} else {
				normalTicks += time;
				normal++;
			}
		}
		Span openPause = lastHitAt >= 0 && now - lastHitAt > PAUSE_MILLIS ? new Span(lastHitAt, now) : null;
		Kind mainNormal = new Kind(main, false);
		Double blockTicks = breaks.stream().anyMatch(b -> b.at() >= from && !b.boost() && b.block().equals(main))
				? times.computeIfAbsent(mainNormal, kind -> breakTime(kind, from)) : null;
		return new MiningStats(Math.max(0, now - start), start - entered, blocks, spread, main,
				normal == 0 ? null : normalTicks / normal, boosted == 0 ? null : boostTicks / boosted, boostMillis(start, now),
				total(pauses, openPause, start, now), blockTicks, boosted);
	}

	// [from, to)
	public Double window(long from, long to, String main, Double ticks, Double boostTicks) {
		if (to <= from) return null;
		int blocks = 0;
		for (Break b : breaks) {
			if (b.at() >= from && b.at() < to && sameKind(b.block(), main)) blocks++;
		}
		double nonstop = Efficiency.nonstop(to - from, boostMillis(from, to), ticks, boostTicks);
		return nonstop <= 0 ? null : blocks / nonstop;
	}

	long boostMillis(long start, long now) {
		return total(boosts, boostSince >= 0 ? new Span(boostSince, now) : null, start, now);
	}

	// open = still going
	private static long total(List<Span> closed, Span open, long start, long end) {
		long sum = open == null ? 0 : open.inside(start, end);
		for (Span span : closed) sum += span.inside(start, end);
		return sum;
	}

	// most ticks not count, hard stone goes in 1 (rec 8)
	// block + pane together, ties = first seen
	private String mainBlock() {
		Map<String, Long> time = new LinkedHashMap<>();
		// over game min only, stone + quartz + boosted gems take 1-4 (rec 9)
		Set<String> slow = new HashSet<>();
		for (Break b : breaks) {
			if (b.ticks() > MIN_TICKS) slow.add(base(b.block()));
		}
		for (Break b : breaks) {
			if (slow.contains(base(b.block()))) time.merge(base(b.block()), (long) Math.min(b.ticks(), TIME_CAP), Long::sum);
		}
		String kind = null;
		for (Map.Entry<String, Long> e : time.entrySet()) {
			if (kind == null || e.getValue() > time.get(kind)) kind = e.getKey();
		}
		Map<String, Integer> counts = new LinkedHashMap<>();
		String main = null;
		for (Break b : breaks) {
			if (!base(b.block()).equals(kind)) continue;
			int count = counts.merge(b.block(), 1, Integer::sum);
			if (main == null || count > counts.get(main)) main = b.block();
		}
		return main;
	}

	private static String base(String block) {
		return block.endsWith("_pane") ? block.substring(0, block.length() - "_pane".length()) : block;
	}

	// MODE_MIN+ clean = mode, fewer = avg, none = shortest unclean
	private double breakTime(Kind kind, long start) {
		List<Integer> clean = new ArrayList<>();
		int shortest = Integer.MAX_VALUE;
		for (Break b : breaks) {
			if (b.at() < start || b.boost() != kind.boost() || !b.block().equals(kind.block())) continue;
			shortest = Math.min(shortest, b.ticks());
			if (b.clean()) clean.add(b.ticks());
		}
		if (clean.size() >= MODE_MIN) return mode(clean);
		if (clean.isEmpty()) return Math.max(1, shortest == Integer.MAX_VALUE ? 1 : shortest);
		return Math.max(1, clean.stream().mapToInt(Integer::intValue).average().orElse(1));
	}

	// ties = the shorter one
	static int mode(List<Integer> values) {
		Map<Integer, Integer> counts = new TreeMap<>();
		for (int value : values) counts.merge(value, 1, Integer::sum);
		int best = 0;
		int most = 0;
		for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
			if (e.getValue() > most) {
				most = e.getValue();
				best = e.getKey();
			}
		}
		return Math.max(1, best);
	}
}
