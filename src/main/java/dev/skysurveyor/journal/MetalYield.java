package dev.skysurveyor.journal;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.skysurveyor.data.Ladder;
import dev.skysurveyor.detection.Shaft;
import dev.skysurveyor.util.Format;

// the shafts own metal (TUNG_1 tungsten, UMBE_1 umber), counted like GemYield. no pristine, no tail
public record MetalYield(String metal, long raw, long enchanted, long refined) {
	public static final int RAW = 0;
	public static final int ENCHANTED = 1;
	public static final int REFINED = 2;

	// null = not a metal shaft or none mined
	public static MetalYield of(Shaft shaft) {
		Ladder ladder = Ladder.metalOf(shaft.id() == null ? null : shaft.id().type());
		if (ladder == null) return null;
		String metal = ladder.name(RAW, null);
		MinedCount mined = MinedCount.of(ladder, item -> metal, shaft.sacksAdded(), shaft.notMinedInSacks());
		MetalYield yield = new MetalYield(metal, mined.at(RAW), mined.at(ENCHANTED), mined.at(REFINED));
		return yield.raw + yield.enchanted + yield.refined > 0 ? yield : null;
	}

	public static boolean isShaftMetal(String item, String type) {
		Ladder ladder = Ladder.metalOf(type);
		return ladder != null && ladder.tier(item, null) != null;
	}

	public Ladder ladder() {
		return Ladder.metal(metal);
	}

	// in units of one tier, f.e. 301.3 enchanted
	public double in(int tier) {
		return in(ladder(), tier);
	}

	private double in(Ladder ladder, int tier) {
		return ladder == null ? 0 : ladder.in(new long[] {raw, enchanted, refined}, tier);
	}

	// no ladder for this metal anymore, each tier by its item name
	public Map<String, Long> items() {
		Map<String, Long> items = new LinkedHashMap<>();
		if (raw > 0) items.put(metal, raw);
		if (enchanted > 0) items.put("Enchanted " + metal, enchanted);
		if (refined > 0) items.put("Refined " + metal, refined);
		return items;
	}

	// raw whole, others ≈ like fine gems. f.e. "≈301.3 Enchanted Tungsten"
	public String text(int tier) {
		Ladder ladder = ladder();
		if (ladder == null) {
			return String.join(", ", items().entrySet().stream().map(e -> Format.number(e.getValue()) + " " + e.getKey()).toList());
		}
		String name = ladder.name(tier, null);
		if (tier == RAW) return Format.number(Math.round(in(ladder, RAW))) + " " + name;
		return "≈" + Format.decimal(in(ladder, tier)) + " " + name;
	}
}
