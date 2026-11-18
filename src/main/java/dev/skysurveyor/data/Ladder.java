package dev.skysurveyor.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.skysurveyor.util.Resources;

// a materials tiers, low to high. base = how many of the lowest tier make one
public record Ladder(String key, List<String> names, List<Long> base) {
	public static final String GEM = "gem";
	private static final String MATERIAL = "{gem}";
	// compaction only, never a shafts own
	private static final String COMPACT = "compact.";

	// one tier up: what into, how many of the tier below make one
	public record Step(String into, long per) {
	}

	private static Map<String, Ladder> defaults;

	static Map<String, Ladder> parse(List<String> lines) {
		Map<String, Ladder> ladders = new HashMap<>();
		for (String line : lines) {
			line = line.strip();
			int eq = line.indexOf('=');
			if (eq < 0 || line.startsWith("#")) continue;
			String key = line.substring(0, eq).strip();
			List<String> names = new ArrayList<>();
			List<Long> base = new ArrayList<>();
			long size = 1;
			for (String tier : line.substring(eq + 1).split(",")) {
				tier = tier.strip();
				int space = tier.indexOf(' ');
				if (!names.isEmpty() && space > 0 && tier.substring(0, space).matches("\\d+")) {
					size *= Long.parseLong(tier.substring(0, space));
					tier = tier.substring(space + 1).strip();
				}
				names.add(tier);
				base.add(size);
			}
			ladders.put(key, new Ladder(key, List.copyOf(names), List.copyOf(base)));
		}
		return ladders;
	}

	private static synchronized Map<String, Ladder> defaults() {
		if (defaults == null) {
			List<String> lines = Resources.lines("/skysurveyor/ladders.txt");
			// gem maths needs it, fail loud not w/ wrong counts
			if (lines == null) throw new IllegalStateException("skysurveyor/ladders.txt missing");
			defaults = parse(lines);
			if (!defaults.containsKey(GEM)) throw new IllegalStateException("skysurveyor/ladders.txt has no gem ladder");
		}
		return defaults;
	}

	public static Ladder gem() {
		return defaults().get(GEM);
	}

	// metal shafts only, null = gem or unknown shaft
	public static Ladder metalOf(String shaftType) {
		return shaftType == null ? null : defaults().get(shaftType);
	}

	// by the lowest tiers name, f.e. "Tungsten"
	public static Ladder metal(String metal) {
		for (Ladder ladder : defaults().values()) {
			if (ladder.isMetal() && ladder.names.getFirst().equals(metal)) return ladder;
		}
		return null;
	}

	// metal ladder w/ this item at any tier, f.e. "Enchanted Umber". null = none
	public static Ladder metalOfItem(String item) {
		for (Ladder ladder : defaults().values()) {
			if (ladder.isMetal() && ladder.tier(item, null) != null) return ladder;
		}
		return null;
	}

	// tier above item on any ladder, null = top tier or on none
	public static Step compactsInto(String item) {
		for (Ladder ladder : defaults().values()) {
			Step step = ladder.stepUp(item);
			if (step != null) return step;
		}
		return null;
	}

	private Step stepUp(String item) {
		for (int i = 0; i + 1 < names.size(); i++) {
			String material = material(i, item);
			if (material != null) return new Step(name(i + 1, material), base(i + 1) / base(i));
		}
		return null;
	}

	// what {gem} stands for if item is this tier, the item itself for plain names. null = not this tier
	private String material(int tier, String item) {
		String name = names.get(tier);
		int at = name.indexOf(MATERIAL);
		if (at < 0) return name.equals(item) ? item : null;
		String prefix = name.substring(0, at);
		String suffix = name.substring(at + MATERIAL.length());
		if (item.length() <= prefix.length() + suffix.length() || !item.startsWith(prefix) || !item.endsWith(suffix)) return null;
		return item.substring(prefix.length(), item.length() - suffix.length());
	}

	private boolean isMetal() {
		return !key.equals(GEM) && !key.startsWith(COMPACT);
	}

	public int tiers() {
		return names.size();
	}

	// material = gem name, ignored by metals. null = no tier
	public Integer tier(String item, String material) {
		for (int i = 0; i < names.size(); i++) {
			if (name(i, material).equals(item)) return i;
		}
		return null;
	}

	public String name(int tier, String material) {
		String name = names.get(tier);
		return material == null ? name : name.replace(MATERIAL, material);
	}

	public long base(int tier) {
		return base.get(tier);
	}

	// how many of from make one of to
	public double per(int from, int to) {
		return (double) base(to) / base(from);
	}

	// counts per tier, worth in units of one tier. whole ratios, so gems stay bit for bit what GemYield did
	public double in(long[] counts, int unit) {
		double total = 0;
		for (int i = 0; i < counts.length; i++) {
			total += base(i) >= base(unit) ? counts[i] * (double) (base(i) / base(unit)) : counts[i] / (double) (base(unit) / base(i));
		}
		return total;
	}
}
