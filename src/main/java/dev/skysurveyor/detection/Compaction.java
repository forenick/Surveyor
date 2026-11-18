package dev.skysurveyor.detection;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import dev.skysurveyor.data.Ladder;
import dev.skysurveyor.detection.ShaftEvent.SacksChanged;

// compactors work inside sacks since skyblock 0.27.3: a msg adds the gross amount + removes what got compacted
// f.e. +4,133 Glacite +32 Enchanted Glacite, -4,160 Glacite (recording 6 at 0:01.8)
// any other remove (sell sacks, withdraw) stays ignored, no income lost
public final class Compaction {
	// per item, short from earlier msgs, comes off its next adds
	private final Map<String, Long> owed = new HashMap<>();

	// per item, how many this msg compacted. crafted = supercraft results since the last msg
	// compaction removes whole steps (always 160s / 576s in recording 6), at most one per compacted add
	// anything else = sell or withdraw, f.e. selling rough gems while pristine adds flawed
	// a msg w/ compaction + a sell of the same item counts as a plain remove, rare
	public static Map<String, Long> of(SacksChanged sacks, Map<String, Long> crafted) {
		Map<String, Long> added = new HashMap<>();
		sacks.added().forEach(item -> added.merge(item.item(), item.count(), Long::sum));
		Map<String, Long> removed = new LinkedHashMap<>();
		sacks.removed().forEach(item -> removed.merge(item.item(), item.count(), Long::sum));
		Map<String, Long> compacted = new LinkedHashMap<>();
		removed.forEach((item, count) -> {
			Ladder.Step step = Ladder.compactsInto(item);
			if (step == null) return;
			long craftedInto = crafted.getOrDefault(step.into(), 0L);
			// no compacted form in the same msg = plain remove
			long into = added.getOrDefault(step.into(), 0L) - craftedInto;
			// supercraft inputs arent compaction
			long taken = count - craftedInto * step.per();
			if (into <= 0 || taken <= 0 || taken % step.per() != 0 || taken / step.per() > into) return;
			compacted.put(item, taken);
		});
		return compacted;
	}

	// added - what compaction made from it, never below 0. the tier mix before compacting, for tail ratios
	public static Map<String, Long> beforeCompacting(Map<String, Long> added, Map<String, Long> compacted) {
		Map<String, Long> made = new LinkedHashMap<>(added);
		compacted.forEach((item, count) -> {
			Ladder.Step step = Ladder.compactsInto(item);
			if (step != null) made.computeIfPresent(step.into(), (into, has) -> Math.max(0, has - count / step.per()));
		});
		return made;
	}

	// added - compacted, never below 0. compaction can use what earlier msgs added (4,133 in, 4,160 out),
	// that shortfall is owed and comes off the next adds of the same item
	public Map<String, Long> net(Map<String, Long> added, Map<String, Long> compacted) {
		Set<String> items = new LinkedHashSet<>(added.keySet());
		items.addAll(compacted.keySet());
		Map<String, Long> net = new LinkedHashMap<>();
		for (String item : items) {
			long count = added.getOrDefault(item, 0L) - compacted.getOrDefault(item, 0L) - owed.getOrDefault(item, 0L);
			if (count < 0) {
				owed.put(item, -count);
			} else {
				owed.remove(item);
				if (count > 0) net.put(item, count);
			}
		}
		return net;
	}

	// still short after the last net
	public Map<String, Long> owed() {
		return Map.copyOf(owed);
	}

	// f.e. a shafts shortfall, paid by the adds after it
	public void owe(Map<String, Long> more) {
		more.forEach((item, count) -> owed.merge(item, count, Long::sum));
	}
}
