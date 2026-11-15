package dev.skysurveyor.money;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import dev.skysurveyor.journal.JournalEntry;
import dev.skysurveyor.journal.JournalStats.SessionSummary;
import dev.skysurveyor.journal.MiningEntry;
import dev.skysurveyor.money.ShaftValue.CorpseValue;
import dev.skysurveyor.money.ShaftValue.Part;

// values null = no prices at all yet
// parts = all-time total per part, in Part order, items = the same per item. ironman = only npc prices, for the wording
// coins = shafts only, outside = mining outside shafts (null = none or no prices), outsideItems per part like items
public record MoneyStats(Double coins, int valued, Map<Part, Double> parts, Map<String, CorpseCoins> corpses,
		Map<Part, Map<String, ItemCoins>> items, Map<String, TypeCoins> types, boolean ironman, Double outside,
		Map<Part, Map<String, ItemCoins>> outsideItems) {
	public record CorpseCoins(int corpses, double loot, double keys, int unpriced, Map<String, ItemCoins> items) {
		public double net() {
			return loot + keys;
		}

		public double each() {
			return net() / corpses;
		}
	}

	// per shaft type code, variants by variant letter
	public record TypeCoins(int shafts, double coins, double best, Map<Part, Double> parts, Map<String, Average> variants) {
		public double each() {
			return coins / shafts;
		}
	}

	public record Average(int shafts, double coins) {
		public double each() {
			return coins / shafts;
		}
	}

	// coins null = never had a price
	public record ItemCoins(double count, Double coins) {
		ItemCoins plus(ShaftValue.Line line) {
			return new ItemCoins(count + line.count(), MoneyStats.plus(coins, line.coins()));
		}
	}

	public Double perShaft() {
		return coins == null || valued == 0 ? null : coins / valued;
	}

	// shafts + outside
	public Double total() {
		return plus(coins, outside);
	}

	// positive parts + outside, after tax
	public double income() {
		double income = 0;
		for (double part : parts.values()) if (part > 0) income += part;
		if (outside != null && outside > 0) income += outside;
		return income;
	}

	// keys, campfires, negative
	public double costs() {
		double costs = 0;
		for (double part : parts.values()) if (part < 0) costs += part;
		return costs;
	}

	// same entries valued w/o tax, so income + tax = what hypixels sell screens show
	public static double tax(MoneyStats taxed, MoneyStats untaxed) {
		return untaxed.income() - taxed.income();
	}

	public static Double net(ShaftValue value) {
		return value == null ? null : value.net();
	}

	// null when none of them has a value
	public static Double coins(List<JournalEntry> entries, Function<JournalEntry, ShaftValue> values) {
		Double total = null;
		for (JournalEntry entry : entries) total = plus(total, net(values.apply(entry)));
		return total;
	}

	public static Double plus(Double a, Double b) {
		if (a == null) return b;
		return b == null ? a : a + b;
	}

	// null when none of them has a value
	public static Double outside(List<MiningEntry> mining, Function<MiningEntry, Double> values) {
		Double total = null;
		for (MiningEntry entry : mining) total = plus(total, values.apply(entry));
		return total;
	}

	// shafts + outside
	public static Double session(SessionSummary session, Function<JournalEntry, ShaftValue> values, Function<MiningEntry, Double> outside) {
		return plus(coins(session.entries(), values), outside(session.mining(), outside));
	}

	public static Double perHour(SessionSummary session, Function<JournalEntry, ShaftValue> values, Function<MiningEntry, Double> outside) {
		Double coins = session(session, values, outside);
		return coins == null ? null : session.perHour(coins, session.end());
	}

	public static MoneyStats of(List<JournalEntry> entries, Function<JournalEntry, ShaftValue> values, boolean ironman) {
		return of(entries, values, List.of(), e -> null, ironman);
	}

	// outside null = no prices for that entry
	public static MoneyStats of(List<JournalEntry> entries, Function<JournalEntry, ShaftValue> values, List<MiningEntry> mining,
			Function<MiningEntry, List<ShaftValue.Line>> outside, boolean ironman) {
		Double coins = null;
		int valued = 0;
		Map<Part, Double> parts = new EnumMap<>(Part.class);
		Map<String, CorpseCoins> corpses = new LinkedHashMap<>();
		Map<Part, Map<String, ItemCoins>> items = new EnumMap<>(Part.class);
		Map<String, TypeCoins> types = new LinkedHashMap<>();
		for (JournalEntry entry : entries) {
			ShaftValue value = values.apply(entry);
			if (value == null) continue;
			valued++;
			coins = plus(coins, value.net());
			for (ShaftValue.Line line : value.lines()) {
				if (line.coins() != null) parts.merge(line.part(), line.coins(), Double::sum);
				add(items.computeIfAbsent(line.part(), part -> new LinkedHashMap<>()), line);
			}
			for (CorpseValue corpse : value.corpses()) {
				CorpseCoins had = corpses.get(corpse.type());
				Map<String, ItemCoins> loot = had == null ? new LinkedHashMap<>() : had.items();
				for (ShaftValue.Line line : corpse.items()) add(loot, line);
				corpses.put(corpse.type(), had == null ? new CorpseCoins(1, corpse.loot(), corpse.key(), corpse.unpriced(), loot)
						: new CorpseCoins(had.corpses() + 1, had.loot() + corpse.loot(), had.keys() + corpse.key(),
								had.unpriced() + corpse.unpriced(), loot));
			}
			String type = entry.shaftType();
			if (type != null) types.put(type, plus(types.get(type), entry.variant(), value));
		}
		Double outsideCoins = null;
		Map<Part, Map<String, ItemCoins>> outsideItems = new EnumMap<>(Part.class);
		for (MiningEntry entry : mining) {
			List<ShaftValue.Line> lines = outside.apply(entry);
			if (lines == null) continue;
			outsideCoins = plus(outsideCoins, MiningValue.total(lines));
			for (ShaftValue.Line line : lines) add(outsideItems.computeIfAbsent(line.part(), part -> new LinkedHashMap<>()), line);
		}
		return new MoneyStats(coins, valued, parts, corpses, items, types, ironman, outsideCoins, outsideItems);
	}

	private static TypeCoins plus(TypeCoins had, String variant, ShaftValue value) {
		double net = value.net();
		Map<Part, Double> parts = had == null ? new EnumMap<>(Part.class) : had.parts();
		for (ShaftValue.Line line : value.lines()) {
			if (line.coins() != null) parts.merge(line.part(), line.coins(), Double::sum);
		}
		Map<String, Average> variants = had == null ? new LinkedHashMap<>() : had.variants();
		if (variant != null) {
			variants.merge(variant, new Average(1, net), (a, b) -> new Average(a.shafts() + 1, a.coins() + net));
		}
		if (had == null) return new TypeCoins(1, net, net, parts, variants);
		return new TypeCoins(had.shafts() + 1, had.coins() + net, Math.max(had.best(), net), parts, variants);
	}

	private static void add(Map<String, ItemCoins> items, ShaftValue.Line line) {
		items.merge(line.item(), new ItemCoins(line.count(), line.coins()), (had, one) -> had.plus(line));
	}
}
