package dev.skysurveyor.journal;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.skysurveyor.data.ItemNames;
import dev.skysurveyor.data.Ladder;
import dev.skysurveyor.detection.Shaft;
import dev.skysurveyor.util.Format;

// TUNG_1 tungsten, UMBE_1 umber, like GemYield. no pristine, no tail
public record MetalYield(String metal, long raw, long enchanted, long refined) {
	public static final int RAW = 0;
	public static final int ENCHANTED = 1;
	public static final int REFINED = 2;

	// tungsten + umber same ratios
	public static double rawPer(int tier) {
		return Ladder.metalOf("TUNG").base(tier);
	}

	// null = not a metal shaft or none mined
	public static MetalYield of(Shaft shaft) {
		Ladder ladder = Ladder.metalOf(shaft.id() == null ? null : shaft.id().type());
		if (ladder == null) return null;
		String metal = ladder.name(RAW, null);
		MinedCount mined = MinedCount.of(ladder, item -> metal, shaft.sacksNet(), shaft.notMinedInSacks());
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

	// f.e. 301.3 enchanted
	public double in(int tier) {
		return in(ladder(), tier);
	}

	private double in(Ladder ladder, int tier) {
		return ladder == null ? 0 : ladder.in(new long[] {raw, enchanted, refined}, tier);
	}

	// no ladder anymore = each tier by name
	public Map<String, Long> items() {
		Map<String, Long> items = new LinkedHashMap<>();
		if (raw > 0) items.put(metal, raw);
		if (enchanted > 0) items.put("Enchanted " + metal, enchanted);
		if (refined > 0) items.put("Refined " + metal, refined);
		return items;
	}

	// raw whole, others 1 decimal. f.e. "301.3 Enchanted Tungsten"
	public String text(int tier) {
		Ladder ladder = ladder();
		if (ladder == null) {
			return String.join(", ", items().entrySet().stream().map(e -> Format.number(e.getValue()) + " " + e.getKey()).toList());
		}
		return amount(ladder, tier) + " " + ladder.name(tier, null);
	}

	public String styledText(int tier, ItemNames names) {
		Ladder ladder = ladder();
		if (ladder == null) {
			return String.join("§7, ", items().entrySet().stream().map(e -> names.styled(e.getKey(), Format.number(e.getValue()))).toList());
		}
		return names.styled(ladder.name(tier, null), amount(ladder, tier));
	}

	// f.e. "301.3 Enchanted", "48,531 Tungsten"
	public String shortText(int tier) {
		if (ladder() == null || tier == RAW) return amount(RAW) + " " + metal;
		return amount(tier) + (tier == ENCHANTED ? " Enchanted" : " Refined");
	}

	// f.e. "301.3". null ladder = raw count
	public String amount(int tier) {
		Ladder ladder = ladder();
		return ladder == null ? Format.number(raw) : amount(ladder, tier);
	}

	private String amount(Ladder ladder, int tier) {
		if (tier == RAW) return Format.number(Math.round(in(ladder, RAW)));
		return Format.decimal(in(ladder, tier));
	}
}
