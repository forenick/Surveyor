package dev.skysurveyor.journal;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.regex.Matcher;

import dev.skysurveyor.data.Ladder;
import dev.skysurveyor.detection.Compaction;
import dev.skysurveyor.detection.ItemCount;
import dev.skysurveyor.detection.ShaftEvent;
import dev.skysurveyor.detection.ShaftEvent.Pristine;
import dev.skysurveyor.detection.ShaftEvent.SacksChanged;
import dev.skysurveyor.detection.ShaftEvent.ShaftEntered;
import dev.skysurveyor.detection.ShaftEvent.ShaftLeft;
import dev.skysurveyor.detection.ShaftEvent.Supercraft;
import dev.skysurveyor.detection.TextPatterns;

// sacks gains while mining in the dwarven mines, not in a shaft (research/09)
// only msgs w/ mining in their window, so stash pickups, crafts etc when idle dont count
// removed ignored, sell sacks or withdrawing isnt income lost. only compaction comes off (Compaction)
public final class OutsideMining {
	// msg comes a moment after its window, display choice
	private static final long SLACK_MILLIS = 2_000L;

	private final TextPatterns patterns;
	private final MiningClock clock;
	private final LongFunction<Journal.Session> session;
	private final Consumer<MiningEntry> listener;
	private final Map<String, Long> pristineSinceSacks = new LinkedHashMap<>();
	private final Map<String, Long> crafted = new HashMap<>();
	// owed carries on for the whole game, a leftover compacted later was counted here too
	private final Compaction compaction = new Compaction();
	// per gem, rough + flawed reported outside, for the tails rough
	private final Map<String, long[]> reported = new HashMap<>();

	public OutsideMining(TextPatterns patterns, BooleanSupplier inMiningArea, LongFunction<Journal.Session> session,
			Consumer<MiningEntry> listener) {
		this.patterns = patterns;
		this.clock = new MiningClock(MiningClock.Scope.MINES_OUTSIDE_SHAFT, inMiningArea);
		this.session = session;
		this.listener = listener;
	}

	public void onBlockHit(long now) {
		clock.onBlockHit(now);
	}

	public void onEvent(ShaftEvent event, long now) {
		clock.onEvent(event, now);
		switch (event) {
			case ShaftEntered entered -> {
				pristineSinceSacks.clear();
				crafted.clear();
			}
			case ShaftLeft left -> {
				// mining from before the shaft doesnt count for later msgs
				clock.reset();
				// leftovers from before the shaft compacted in it, counted out here first
				compaction.owe(left.shaft().compactionOwed());
			}
			case Pristine pristine -> {
				if (clock.inScope()) add(pristineSinceSacks, pristine.gems());
			}
			case Supercraft craft -> {
				if (!clock.inShaft()) add(crafted, craft.result());
			}
			case SacksChanged sacks -> onSacks(sacks, now);
			default -> {
			}
		}
	}

	// gem -> fine equivalent, biggest first
	public static Map<String, Double> fineGems(List<MiningEntry> mining, TextPatterns patterns) {
		Map<String, Double> gems = new HashMap<>();
		for (MiningEntry entry : mining) GemYield.fineByGem(entry.items(), patterns).forEach((gem, fine) -> gems.merge(gem, fine, Double::sum));
		Map<String, Double> sorted = new LinkedHashMap<>();
		gems.entrySet().stream().sorted(Map.Entry.<String, Double>comparingByValue().reversed())
				.forEach(e -> sorted.put(e.getKey(), e.getValue()));
		return sorted;
	}

	// last secs before it never show in sacks (research/06, 07)
	public void onWorldChange(long now) {
		if (!clock.inShaft() && !pristineSinceSacks.isEmpty() && clock.minedSince(now - Journal.PAUSE_MILLIS)) {
			Map<String, Long> tail = tail();
			if (!tail.isEmpty()) emit(now, tail, true);
		}
		pristineSinceSacks.clear();
		crafted.clear();
	}

	private void onSacks(SacksChanged sacks, long now) {
		if (!clock.inScope()) return;
		Map<String, Long> compacted = Compaction.of(sacks, crafted);
		Map<String, Long> items = new LinkedHashMap<>();
		for (ItemCount item : sacks.added()) {
			long count = item.count() - crafted.getOrDefault(item.item(), 0L);
			if (count > 0) items.merge(item.item(), count, Long::sum);
		}
		crafted.clear();
		pristineSinceSacks.clear();
		if (items.isEmpty() || !clock.minedSince(windowStart(sacks, now))) return;
		// tail ratio from the mix as mined, like GemYield
		Compaction.beforeCompacting(items, compacted).forEach(this::remember);
		items = compaction.net(items, compacted);
		if (!items.isEmpty()) emit(now, items, false);
	}

	// mining since then = the msgs items came from mining
	static long windowStart(SacksChanged sacks, long now) {
		return now - sacks.windowSeconds() * 1000L - SLACK_MILLIS;
	}

	private void emit(long now, Map<String, Long> items, boolean estimated) {
		Journal.Session current = session.apply(now);
		if (current == null) return;
		listener.accept(new MiningEntry(MiningEntry.VERSION, current.id(), now, Collections.unmodifiableMap(new LinkedHashMap<>(items)), estimated,
				null));
	}

	// pristine = flawed only, rough from what this gem gave so far (like GemYield)
	private Map<String, Long> tail() {
		Map<String, Long> tail = new LinkedHashMap<>();
		pristineSinceSacks.forEach((item, flawed) -> {
			tail.merge(item, flawed, Long::sum);
			Matcher m = patterns.match("gem.item", item);
			if (m == null || !"Flawed".equals(m.group("quality"))) return;
			long[] seen = reported.get(m.group("gem"));
			if (seen == null) return;
			long rough = GemYield.tailRough(flawed, seen[0], seen[1]);
			if (rough > 0) tail.merge(Ladder.gem().name(0, m.group("gem")), rough, Long::sum);
		});
		return tail;
	}

	private void remember(String item, long count) {
		Matcher m = patterns.match("gem.item", item);
		if (m == null) return;
		Integer tier = Ladder.gem().tier(item, m.group("gem"));
		if (tier != null && tier <= 1) reported.computeIfAbsent(m.group("gem"), gem -> new long[2])[tier] += count;
	}

	private static void add(Map<String, Long> totals, ItemCount item) {
		totals.merge(item.item(), item.count(), Long::sum);
	}
}
