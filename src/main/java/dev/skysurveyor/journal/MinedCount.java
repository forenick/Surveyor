package dev.skysurveyor.journal;

import java.util.Map;
import java.util.function.Function;

import dev.skysurveyor.data.Ladder;

// per ladder tier: items in - items out. material = last one seen, null = none of the shafts own
record MinedCount(String material, long[] counts) {
	// materialOf: item -> its material if its the shafts own, else null
	static MinedCount of(Ladder ladder, Function<String, String> materialOf, Map<String, Long> in, Map<String, Long> out) {
		long[] counts = new long[ladder.tiers()];
		String material = add(ladder, materialOf, in, 1, counts, null);
		material = add(ladder, materialOf, out, -1, counts, material);
		return new MinedCount(material, counts);
	}

	private static String add(Ladder ladder, Function<String, String> materialOf, Map<String, Long> items, int sign, long[] counts,
			String material) {
		for (Map.Entry<String, Long> e : items.entrySet()) {
			String found = materialOf.apply(e.getKey());
			if (found == null) continue;
			Integer tier = ladder.tier(e.getKey(), found);
			if (tier == null) continue;
			material = found;
			counts[tier] += sign * e.getValue();
		}
		return material;
	}

	long at(int tier) {
		return Math.max(0, counts[tier]);
	}
}
