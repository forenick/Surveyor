package dev.skysurveyor.money;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import dev.skysurveyor.data.CorpseType;
import dev.skysurveyor.data.Ladder;
import dev.skysurveyor.detection.CorpseLoot;
import dev.skysurveyor.detection.ItemCount;
import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.journal.GemYield;
import dev.skysurveyor.journal.JournalEntry;
import dev.skysurveyor.journal.MetalYield;
import dev.skysurveyor.settings.MoneySettings;
import dev.skysurveyor.settings.MoneySettings.Crystals;
import dev.skysurveyor.settings.MoneySettings.GemTier;
import dev.skysurveyor.settings.MoneySettings.OwnMetal;

// rules in docs/money-design.md
public record ShaftValue(List<Line> lines, List<CorpseValue> corpses) {
	// mining fiesta drop, own row bc its worth so much
	public static final String GLOSSY = "Glossy Gemstone";
	private static final String FLAWLESS = "Flawless ";
	private static final String FINE = "Fine ";
	private static final String GEMSTONE = " Gemstone";

	public enum Part {
		MINED("Mined gems"),
		MINED_METAL("Mined metal"),
		GLOSSY("Glossy Gemstones"),
		OTHER("Other drops"),
		CORPSES("Corpse loot"),
		SCRAP("Suspicious Scrap"),
		CRYSTALS("Crystals"),
		KEYS("Keys"),
		CAMPFIRES("Campfires");

		private final String displayName;

		Part(String displayName) {
			this.displayName = displayName;
		}

		public String displayName() {
			return displayName;
		}

	}

	// coins null = no price, costs negative
	public record Line(Part part, String item, double count, Double coins) {
	}

	// per looted corpse, key negative, unpriced = items w/o a price (counted as 0)
	public record CorpseValue(String type, double loot, double key, int unpriced, List<Line> items) {
		public double net() {
			return loot + key;
		}
	}

	public interface Prices {
		ItemPrice get(String id);
	}

	public double total(Part part) {
		return lines.stream().filter(l -> l.part == part && l.coins != null).mapToDouble(Line::coins).sum();
	}

	public double net() {
		return net(lines);
	}

	static double net(List<Line> lines) {
		return lines.stream().filter(l -> l.coins != null).mapToDouble(Line::coins).sum();
	}

	public List<Line> unpriced() {
		return lines.stream().filter(l -> l.coins == null).toList();
	}

	// saved prices first, todays for anything they miss
	public static Prices layered(Map<String, ItemPrice> saved, Prices today) {
		if (saved == null || saved.isEmpty()) return today;
		return id -> {
			ItemPrice price = saved.get(id);
			return price != null ? price : today.get(id);
		};
	}

	public static ShaftValue of(JournalEntry entry, Prices prices, MoneySettings settings, ItemIds ids, TextPatterns patterns) {
		PriceRules v = new PriceRules(prices, settings, ids, patterns);
		List<Line> lines = new ArrayList<>();

		if (entry.gems() != null && entry.gems().fineEquivalent() > 0) {
			lines.add(minedGem(v, settings, entry.gems().gem(), entry.gems().fineEquivalent()));
		}

		MetalYield metal = entry.metal();
		Ladder ladder = metal == null ? null : metal.ladder();
		if (ladder != null) {
			int tier = settings.metalTierUsed().tier();
			Double perRaw = v.metalPerRaw(ladder);
			lines.add(new Line(Part.MINED_METAL, ladder.name(tier, null), metal.in(tier),
					perRaw == null ? null : perRaw * metal.in(MetalYield.RAW)));
		} else if (metal != null) {
			metal.items().forEach((item, count) -> lines.add(line(v, Part.MINED_METAL, item, count)));
		}

		otherDrops(entry, patterns).forEach((item, count) -> lines.add(drop(v, item, count)));

		Map<String, Long> loot = new LinkedHashMap<>();
		Map<String, Long> keys = new LinkedHashMap<>();
		List<CorpseValue> corpses = new ArrayList<>();
		for (int i = 0; i < entry.loots().size(); i++) {
			CorpseLoot corpse = entry.loots().get(i);
			double lootCoins = 0;
			int unpriced = 0;
			List<Line> items = new ArrayList<>();
			for (ItemCount item : corpse.items()) {
				loot.merge(item.item(), item.count(), Long::sum);
				Line line = line(v, Part.CORPSES, item.item(), item.count());
				items.add(line);
				if (line.coins() == null) unpriced++;
				else lootCoins += line.coins();
			}
			// resourceful: key kept, costs nothing
			String key = entry.keySaved(i) ? null : CorpseType.key(corpse.type());
			Double keyCost = null;
			if (key != null) {
				keys.merge(key, 1L, Long::sum);
				keyCost = v.key(key);
				if (keyCost == null) unpriced++;
			}
			corpses.add(new CorpseValue(corpse.type(), lootCoins, keyCost == null ? 0 : -keyCost, unpriced, List.copyOf(items)));
		}
		loot.forEach((item, count) -> lines.add(line(v, Part.CORPSES, item, count)));

		if (entry.scrapFound() > 0) lines.add(line(v, Part.SCRAP, "Suspicious Scrap", entry.scrapFound()));

		if (entry.crystals() != null) {
			Map<String, Long> crystals = new LinkedHashMap<>();
			for (String crystal : entry.crystals()) crystals.merge(crystal, 1L, Long::sum);
			crystals.forEach((item, count) -> lines.add(line(v, Part.CRYSTALS, item, count)));
		}

		keys.forEach((key, count) -> {
			Double each = v.key(key);
			lines.add(new Line(Part.KEYS, key, count, each == null ? null : -each * count));
		});

		int campfires = entry.campfiresAt().size();
		if (campfires > 0) {
			Double each = v.campfireCost();
			lines.add(new Line(Part.CAMPFIRES, "Portable Campfire", campfires, each == null ? null : -each * campfires));
		}
		return new ShaftValue(List.copyOf(lines), List.copyOf(corpses));
	}

	// every id the value could use, every gem + metal tier, so settings can change later
	public static Map<String, ItemPrice> snapshot(JournalEntry entry, Prices prices, MoneySettings settings, ItemIds ids,
			TextPatterns patterns) {
		return snapshot(prices, (recording, used) -> {
			for (GemTier tier : GemTier.values()) {
				for (OwnMetal own : OwnMetal.values()) {
					// ironman uses a subset of these ids
					of(entry, recording, settings.withGemTier(tier).withCrystals(Crystals.PERFECT_MINUS_FLAWLESS).withIronman(false)
							.withOwnMetal(own), ids, patterns);
				}
			}
			Ladder ladder = entry.metal() == null ? null : entry.metal().ladder();
			if (ladder != null) {
				for (int tier = 0; tier < ladder.tiers(); tier++) {
					String id = ids.id(ladder.name(tier, null));
					if (id != null) used.add(id);
				}
			}
		});
	}

	// runs the valuing w/ prices that note every id asked for, keeps the found ones
	static Map<String, ItemPrice> snapshot(Prices prices, BiConsumer<Prices, Set<String>> run) {
		Set<String> used = new HashSet<>();
		run.accept(id -> {
			used.add(id);
			return prices.get(id);
		}, used);
		Map<String, ItemPrice> saved = new HashMap<>();
		for (String id : used) {
			ItemPrice price = prices.get(id);
			if (price != null) saved.put(id, price);
		}
		return saved;
	}

	// pre 0.14.0 entries didnt save it: sacks - all corpse loot (a bit low) - sacks removed
	// supercrafts werent saved, removed = their inputs, so only the result counts
	static Map<String, Long> otherDrops(JournalEntry entry, TextPatterns patterns) {
		if (entry.otherDrops() != null) return entry.otherDrops();
		Map<String, Long> drops = new LinkedHashMap<>(entry.sacksAdded());
		for (CorpseLoot corpse : entry.loots()) {
			for (ItemCount item : corpse.items()) drops.merge(item.item(), -item.count(), Long::sum);
		}
		if (entry.sacksRemoved() != null) entry.sacksRemoved().forEach((item, count) -> drops.merge(item, -count, Long::sum));
		String type = entry.shaftType();
		drops.entrySet().removeIf(e -> e.getValue() <= 0 || GemYield.isShaftGem(e.getKey(), type, patterns));
		return drops;
	}

	// fine gems (or flawless) as one line, like the setting says
	static Line minedGem(PriceRules v, MoneySettings settings, String gem, double fine) {
		boolean flawless = !settings.isIronman() && settings.gemTier() == GemTier.FLAWLESS;
		Double perFine = v.gemPerFine(gem);
		return new Line(Part.MINED, (flawless ? FLAWLESS : FINE) + gem + GEMSTONE, flawless ? fine / GemYield.RECIPE : fine,
				perFine == null ? null : perFine * fine);
	}

	// gem of a minedGem line, null = not one
	public static String minedGemName(Line line) {
		if (line.part != Part.MINED || !line.item.endsWith(GEMSTONE)) return null;
		String tier = line.item.startsWith(FLAWLESS) ? FLAWLESS : line.item.startsWith(FINE) ? FINE : null;
		return tier == null ? null : line.item.substring(tier.length(), line.item.length() - GEMSTONE.length());
	}

	// a minedGem lines count back in fine
	public static double minedFine(Line line) {
		return line.item.startsWith(FLAWLESS) ? line.count * GemYield.RECIPE : line.count;
	}

	static Line drop(PriceRules v, String item, long count) {
		return line(v, item.equals(GLOSSY) ? Part.GLOSSY : Part.OTHER, item, count);
	}

	private static Line line(PriceRules v, Part part, String item, long count) {
		Double each = v.each(item);
		return new Line(part, item, count, each == null ? null : each * count);
	}
}
