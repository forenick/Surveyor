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
import dev.skysurveyor.detection.ItemCount;
import dev.skysurveyor.detection.ShaftEvent;
import dev.skysurveyor.detection.ShaftEvent.PickaxeAbility;
import dev.skysurveyor.detection.ShaftEvent.Pristine;
import dev.skysurveyor.detection.ShaftEvent.SacksChanged;
import dev.skysurveyor.detection.ShaftEvent.ShaftEntered;
import dev.skysurveyor.detection.ShaftEvent.ShaftLeft;
import dev.skysurveyor.detection.ShaftEvent.Supercraft;
import dev.skysurveyor.detection.TextPatterns;

// sacks gains while mining in the dwarven mines, not in a shaft (research/09)
// only msgs w/ mining in their window, so stash pickups, crafts etc when idle dont count
// removed ignored, sell sacks or withdrawing isnt income lost
public final class OutsideMining {
	// msg comes a moment after its window, display choice
	private static final long SLACK_MILLIS = 2_000L;

	private final TextPatterns patterns;
	private final BooleanSupplier inMiningArea;
	private final LongFunction<Journal.Session> session;
	private final Consumer<MiningEntry> listener;
	private boolean inShaft;
	private long lastMiningAt = -1;
	private final Map<String, Long> pristineSinceSacks = new LinkedHashMap<>();
	private final Map<String, Long> crafted = new HashMap<>();
	// per gem, rough + flawed reported outside, for the tails rough
	private final Map<String, long[]> reported = new HashMap<>();

	public OutsideMining(TextPatterns patterns, BooleanSupplier inMiningArea, LongFunction<Journal.Session> session,
			Consumer<MiningEntry> listener) {
		this.patterns = patterns;
		this.inMiningArea = inMiningArea;
		this.session = session;
		this.listener = listener;
	}

	public void onBlockHit(long now) {
		mining(now);
	}

	public void onEvent(ShaftEvent event, long now) {
		switch (event) {
			case PickaxeAbility ability -> mining(now);
			case ShaftEntered entered -> {
				inShaft = true;
				pristineSinceSacks.clear();
				crafted.clear();
			}
			case ShaftLeft left -> {
				inShaft = false;
				lastMiningAt = -1;
			}
			case Pristine pristine -> {
				if (outside()) add(pristineSinceSacks, pristine.gems());
			}
			case Supercraft craft -> {
				if (!inShaft) add(crafted, craft.result());
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
		if (!inShaft && !pristineSinceSacks.isEmpty() && minedSince(now - Journal.PAUSE_MILLIS)) {
			Map<String, Long> tail = tail();
			if (!tail.isEmpty()) emit(now, tail, true);
		}
		pristineSinceSacks.clear();
		crafted.clear();
	}

	private void onSacks(SacksChanged sacks, long now) {
		if (!outside()) return;
		Map<String, Long> items = new LinkedHashMap<>();
		for (ItemCount item : sacks.added()) {
			long count = item.count() - crafted.getOrDefault(item.item(), 0L);
			if (count > 0) items.merge(item.item(), count, Long::sum);
		}
		crafted.clear();
		pristineSinceSacks.clear();
		if (items.isEmpty() || !minedSince(windowStart(sacks, now))) return;
		items.forEach(this::remember);
		emit(now, items, false);
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

	private void mining(long now) {
		if (outside()) lastMiningAt = now;
	}

	private boolean outside() {
		return !inShaft && inMiningArea.getAsBoolean();
	}

	private boolean minedSince(long from) {
		return lastMiningAt >= 0 && lastMiningAt >= from;
	}

	private static void add(Map<String, Long> totals, ItemCount item) {
		totals.merge(item.item(), item.count(), Long::sum);
	}
}
