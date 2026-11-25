package dev.skysurveyor.mining;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.skysurveyor.journal.JournalEntry;

// the Stats tabs efficiency: median over mined shafts, per shaft type + variant. plain java
// median not mean, a shaft w/ a break inside (research/11: 0.44 on 11-18) would drag a mean down
public final class EfficiencyStats {
	// a few shafts w/ a number, by median
	public record Group(double median, int shafts, Double ticks) {
	}

	// type = shaft code, f.e. JASP. variants by their code letter, f.e. C
	public record Type(String type, Group all, Map<String, Group> variants) {
	}

	// all = null when no shaft in the range has a number. types best first
	public record Summary(Group all, List<Type> types) {
		public static final Summary NONE = new Summary(null, List.of());
	}

	private EfficiencyStats() {
	}

	public static Summary of(List<JournalEntry> entries) {
		List<JournalEntry> mined = entries.stream().filter(e -> Efficiency.shown(e.mining()) != null).toList();
		if (mined.isEmpty()) return Summary.NONE;
		Map<String, List<JournalEntry>> byType = new LinkedHashMap<>();
		for (JournalEntry entry : mined) {
			if (entry.shaftType() != null) byType.computeIfAbsent(entry.shaftType(), t -> new ArrayList<>()).add(entry);
		}
		List<Type> types = new ArrayList<>();
		byType.forEach((type, list) -> {
			Map<String, List<JournalEntry>> byVariant = new LinkedHashMap<>();
			for (JournalEntry entry : list) byVariant.computeIfAbsent(String.valueOf(entry.variant()), v -> new ArrayList<>()).add(entry);
			Map<String, Group> variants = new LinkedHashMap<>();
			byVariant.forEach((variant, of) -> variants.put(variant, group(of)));
			types.add(new Type(type, group(list), variants));
		});
		types.sort(Comparator.comparingDouble((Type t) -> -t.all().median()));
		return new Summary(group(mined), List.copyOf(types));
	}

	private static Group group(List<JournalEntry> entries) {
		List<Double> efficiencies = entries.stream().map(e -> Efficiency.shown(e.mining())).toList();
		List<Double> ticks = entries.stream().map(e -> e.mining().shownTicks()).filter(t -> t != null).toList();
		return new Group(median(efficiencies), entries.size(), ticks.isEmpty() ? null : median(ticks));
	}

	static double median(List<Double> values) {
		List<Double> sorted = values.stream().sorted().toList();
		int n = sorted.size();
		return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2;
	}
}
