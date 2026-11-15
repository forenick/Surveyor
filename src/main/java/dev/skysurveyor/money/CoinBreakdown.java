package dev.skysurveyor.money;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Stream;

import dev.skysurveyor.data.Ladder;
import dev.skysurveyor.journal.JournalStats;
import dev.skysurveyor.journal.JournalStats.Drops;
import dev.skysurveyor.journal.JournalStats.ItemDrop;
import dev.skysurveyor.money.MoneyStats.CorpseCoins;
import dev.skysurveyor.money.MoneyStats.ItemCoins;
import dev.skysurveyor.money.MoneyStats.TypeCoins;
import dev.skysurveyor.money.ShaftValue.Part;
import dev.skysurveyor.settings.MoneySettings;
import dev.skysurveyor.settings.MoneySettings.GemTier;
import dev.skysurveyor.settings.MoneySettings.SellPrice;
import dev.skysurveyor.util.Format;

// stats tooltips as §-coded lines, "" = small gap, items cut until it fits maxHeight
public final class CoinBreakdown {
	public static final String SHIFT_HINT = "§7Hold §fShift §7for the short version";
	// vanilla tooltip line, 2 px under the title
	public static final int LINE_HEIGHT = 10;
	private static final int TITLE_GAP = 2;
	// display choice
	public static final int GAP_HEIGHT = 4;
	// display choice
	private static final int MOST_ITEMS = 5;

	private CoinBreakdown() {
	}

	// beforeOutside = has sessions from before outside coins were saved
	public static List<String> detailed(MoneyStats money, Double perHour, long timeMillis, boolean beforeOutside, Double tax,
			int maxHeight) {
		return fit(MOST_ITEMS, maxHeight, items -> detailedWith(money, perHour, timeMillis, beforeOutside, tax, items));
	}

	// session coins tile, tax null = none taken off, breakMillis = not counted per hour
	public static List<String> session(MoneyStats money, Double perHour, long breakMillis, Double tax, MoneySettings settings,
			int maxHeight) {
		return fit(MOST_ITEMS, maxHeight, items -> sessionWith(money, perHour, breakMillis, tax, settings, items, true));
	}

	// shift: parts + items left out
	public static List<String> sessionShort(MoneyStats money, Double perHour, long breakMillis, Double tax, MoneySettings settings) {
		return sessionWith(money, perHour, breakMillis, tax, settings, 0, false);
	}

	private static List<String> sessionWith(MoneyStats money, Double perHour, long breakMillis, Double tax, MoneySettings settings,
			int items, boolean detailed) {
		List<String> lines = new ArrayList<>();
		lines.add("§fCoins this session");
		gap(lines);
		boolean outside = !money.outsideItems().isEmpty();
		if (!money.items().isEmpty()) {
			lines.add("§fShafts " + coinsOrNoPrice(money.coins(), money.ironman()));
			if (detailed) {
				for (Part part : shownParts(money)) {
					lines.add("  §7" + part.displayName() + " " + coinsOrNoPrice(money.parts().get(part), money.ironman()));
				}
			}
		}
		if (outside) {
			lines.add("§fOutside shafts " + coinsOrNoPrice(money.outside(), money.ironman()));
			if (detailed) addRows(lines, outsideRows(money), items, "  ", true, money.ironman());
		}
		gap(lines);
		double income = money.income();
		double costs = money.costs();
		lines.add("§7Earned " + coins(income));
		if (Math.round(costs) != 0) {
			lines.add("§7Spent " + coins(costs));
			lines.add("§fNet " + coins(income + costs));
		}
		gap(lines);
		lines.add("§7Per hour " + (perHour == null ? "§8after " + JournalStats.MIN_RATE_MILLIS / 60_000 + " minutes" : coins(perHour)));
		if (perHour != null && breakMillis > 0) lines.add("§8Breaks left out: " + Format.duration(breakMillis));
		if (money.perShaft() != null) lines.add("§7Per shaft " + coins(money.perShaft()) + (outside ? " §8(shafts only)" : ""));
		gap(lines);
		lines.add(pricing(settings));
		if (tax != null && Math.round(tax) > 0) lines.add(taxLine(tax));
		long unpriced = unpriced(money);
		if (unpriced > 0) lines.add(unpriced(unpriced, money.ironman()));
		if (detailed) {
			gap(lines);
			lines.add(SHIFT_HINT);
		}
		return lines;
	}

	// gem tier + sell price the coins used
	private static String pricing(MoneySettings settings) {
		if (settings.isIronman()) return "§7Ironman: NPC sell prices";
		return "§7Gems as " + gemTierName(settings) + ", " + sellName(settings);
	}

	public static String gemTierName(MoneySettings settings) {
		return settings.gemTier() == GemTier.FINE ? "Fine" : "Flawless";
	}

	public static String sellName(MoneySettings settings) {
		return settings.sellPrice() == SellPrice.INSTA_SELL ? "insta-sell" : "sell offer";
	}

	// earned + this = before tax, what hypixels sell screens show
	private static String taxLine(double tax) {
		return "§7Bazaar tax taken off: " + Coins.format(tax);
	}

	private static long unpriced(MoneyStats money) {
		return Stream.concat(money.items().values().stream(), money.outsideItems().values().stream())
				.flatMap(m -> m.values().stream()).filter(i -> i.coins() == null).count();
	}

	// session tables outside row
	public static List<String> outside(MoneyStats money, int maxHeight) {
		return fit(MOST_ITEMS * 2, maxHeight, items -> {
			List<String> lines = new ArrayList<>();
			outsidePart(lines, money, null, items);
			lines.add("");
			lines.add("§8Mined in the Dwarven Mines, not in a shaft");
			return lines;
		});
	}

	// income null = no share
	private static void outsidePart(List<String> lines, MoneyStats money, Double income, int items) {
		if (money.outsideItems().isEmpty()) return;
		Double total = money.outside();
		String share = total != null && total > 0 && income != null && income > 0 ? " §8" + share(total / income) : "";
		gap(lines);
		lines.add("§fOutside shafts " + coinsOrNoPrice(total, money.ironman()) + share);
		addRows(lines, outsideRows(money), items, "  ", true, money.ironman());
	}

	private static List<Row> outsideRows(MoneyStats money) {
		List<Row> rows = new ArrayList<>();
		money.outsideItems().forEach((part, partItems) -> rows.addAll(itemRows(part, partItems)));
		return rows;
	}

	// parts w/ items, minus ones that round to 0 w/ every item priced
	private static List<Part> shownParts(MoneyStats money) {
		List<Part> parts = new ArrayList<>();
		for (Part part : Part.values()) {
			Map<String, ItemCoins> partItems = money.items().getOrDefault(part, Map.of());
			if (partItems.isEmpty()) continue;
			Double total = money.parts().get(part);
			if (total != null && Math.round(total) == 0 && partItems.values().stream().allMatch(i -> i.coins() != null)) continue;
			parts.add(part);
		}
		return parts;
	}

	// fewer items until it fits
	private static List<String> fit(int most, int maxHeight, IntFunction<List<String>> build) {
		List<String> lines = List.of();
		for (int items = most; items >= 0; items--) {
			lines = build.apply(items);
			if (height(lines) <= maxHeight) break;
		}
		return lines;
	}

	public static int height(List<String> lines) {
		int height = lines.size() > 1 ? TITLE_GAP : 0;
		for (String line : lines) height += line.isEmpty() ? GAP_HEIGHT : LINE_HEIGHT;
		return height;
	}

	private static void gap(List<String> lines) {
		if (!lines.isEmpty() && !lines.getLast().isEmpty()) lines.add("");
	}

	private static List<String> detailedWith(MoneyStats money, Double perHour, long timeMillis, boolean beforeOutside, Double tax,
			int items) {
		List<String> lines = new ArrayList<>();
		lines.add("§fWhere your coins came from");
		double income = money.income();
		double costs = money.costs();
		for (Part part : shownParts(money)) {
			Double total = money.parts().get(part);
			String share = total != null && total > 0 && income > 0 ? " §8" + share(total / income) : "";
			gap(lines);
			lines.add("§f" + part.displayName() + " " + coinsOrNoPrice(total, money.ironman()) + share);
			List<Row> rows = part == Part.CORPSES ? corpseRows(money) : itemRows(part, money.items().get(part));
			addRows(lines, rows, items, "  ", true, money.ironman());
		}
		outsidePart(lines, money, income, items);
		gap(lines);
		if (Math.round(costs) != 0) {
			lines.add("§7Earned " + coins(income));
			lines.add("§7Spent " + coins(costs));
			lines.add("§fNet " + coins(income + costs));
			gap(lines);
		}
		if (money.perShaft() != null) {
			lines.add("§7Avg per shaft " + coins(money.perShaft()) + " §8(" + money.valued() + " shafts)");
		}
		if (perHour != null) {
			lines.add("§7Avg per hour shafting " + coins(perHour) + " §8(" + Format.duration(timeMillis) + ")");
		}
		if (tax != null && Math.round(tax) > 0) lines.add(taxLine(tax));
		if (beforeOutside) lines.add("§8Outside shafts counted since 0.24.0");
		long unpriced = unpriced(money);
		if (unpriced > 0) lines.add(unpriced(unpriced, money.ironman()));
		gap(lines);
		lines.add(SHIFT_HINT);
		return lines;
	}

	public static List<String> basic(MoneyStats money, Double perHour, long timeMillis) {
		List<String> lines = new ArrayList<>();
		lines.add("§fWhere your coins came from");
		gap(lines);
		money.parts().forEach((part, coins) -> {
			if (Math.round(coins) != 0) lines.add("§7" + part.displayName() + " " + coins(coins));
		});
		if (money.outside() != null && Math.round(money.outside()) != 0) lines.add("§7Outside shafts " + coins(money.outside()));
		gap(lines);
		if (money.perShaft() != null) {
			lines.add("§7Avg per shaft " + coins(money.perShaft()) + " §8(" + money.valued() + " shafts)");
		}
		if (perHour != null) {
			lines.add("§7Avg per hour shafting " + coins(perHour) + " §8(" + Format.duration(timeMillis) + ")");
		}
		return lines;
	}

	// corpse null = no prices, drops null = no loot seen
	public static List<String> corpse(String type, int looted, CorpseCoins corpse, Drops drops, int maxHeight, boolean ironman) {
		return fit(MOST_ITEMS * 2, maxHeight, items -> corpseWith(type, looted, corpse, drops, items, ironman));
	}

	private static List<String> corpseWith(String type, int looted, CorpseCoins corpse, Drops drops, int items, boolean ironman) {
		List<String> lines = new ArrayList<>();
		lines.add("§f" + type + " corpses");
		gap(lines);
		if (corpse != null) {
			String counted = corpse.corpses() == looted ? corpse.corpses() + " corpses"
					: corpse.corpses() + " of " + looted + " with loot seen";
			lines.add("§7Total " + coins(corpse.net()) + " §8(" + counted + ")");
			lines.add("§7Avg " + coins(corpse.each()) + " §7each");
			if (Math.round(corpse.keys()) != 0) {
				lines.add("  §7Loot " + coins(corpse.loot() / corpse.corpses()) + " §7each");
				lines.add("  §7Key " + coins(corpse.keys() / corpse.corpses()) + " §7each");
			}
		} else {
			lines.add("§8No prices yet");
		}
		if (drops != null && !drops.items().isEmpty() && items > 0) {
			gap(lines);
			lines.add("§fLoot §8drop chance" + (drops.corpses() == looted ? "" : " (" + drops.corpses() + " corpses seen)"));
			List<Row> rows = new ArrayList<>();
			drops.items().entrySet().stream()
					.sorted(Comparator.comparingInt((Map.Entry<String, ItemDrop> e) -> -e.getValue().corpses()))
					.forEach(e -> {
						ItemCoins priced = corpse == null ? null : corpse.items().get(e.getKey());
						rows.add(new Row("§b" + chance(e.getValue().chance(drops.corpses())) + " §7"
								+ Format.number(e.getValue().count()) + " " + e.getKey(), priced == null ? null : priced.coins()));
					});
			addRows(lines, rows, items, "", corpse != null, ironman);
		}
		if (corpse != null && corpse.unpriced() > 0) lines.add(unpriced(corpse.unpriced(), ironman));
		return lines;
	}

	// share null = no shaft type made coins
	public static List<String> shaftType(String name, TypeCoins type, Double share, Function<String, String> variantNames) {
		List<String> lines = new ArrayList<>();
		lines.add("§f" + name + " shafts");
		gap(lines);
		lines.add("§7Avg " + coins(type.each()) + " §7per shaft §8(" + type.shafts() + (type.shafts() == 1 ? " shaft)" : " shafts)"));
		lines.add("§7Best " + coins(type.best()));
		lines.add("§7Total " + coins(type.coins()));
		if (share != null) lines.add("§7Share of all shaft coins §f" + (share <= 0 ? "0%" : share(share)) + " §8(the bar)");
		List<String> parts = new ArrayList<>();
		type.parts().forEach((part, coins) -> {
			if (Math.round(coins / type.shafts()) != 0) parts.add("§7" + part.displayName() + " " + coins(coins / type.shafts()));
		});
		if (!parts.isEmpty()) {
			gap(lines);
			lines.add("§fPer shaft §8(avg)");
			lines.addAll(parts);
		}
		if (type.variants().size() > 1) {
			gap(lines);
			lines.add("§fPer variant §8(avg)");
			type.variants().forEach((variant, avg) -> lines.add("§7" + variantNames.apply(variant) + " " + coins(avg.each())
					+ " §8(" + avg.shafts() + (avg.shafts() == 1 ? " shaft)" : " shafts)")));
		}
		return lines;
	}

	// under 10% w/ a decimal, rare drops matter
	static String chance(double fraction) {
		double percent = 100 * fraction;
		return percent >= 9.95 ? Math.round(percent) + "%" : String.format(Locale.ROOT, "%.1f%%", percent);
	}

	private record Row(String text, Double coins) {
	}

	private static List<Row> itemRows(Part part, Map<String, ItemCoins> items) {
		List<Row> rows = new ArrayList<>();
		items.forEach((item, coins) -> rows.add(new Row(count(part, item, coins.count()) + " " + item, coins.coins())));
		return rows;
	}

	private static List<Row> corpseRows(MoneyStats money) {
		List<Row> rows = new ArrayList<>();
		money.corpses().forEach((type, corpse) -> rows.add(new Row(corpse.corpses() + " " + type
				+ (corpse.corpses() == 1 ? " corpse" : " corpses"), corpse.loot())));
		return rows;
	}

	// biggest first either way, no price last
	private static void addRows(List<String> lines, List<Row> rows, int items, String indent, boolean withCoins, boolean ironman) {
		if (items == 0) return;
		List<Row> sorted = rows.stream().sorted(Comparator.comparing((Row r) -> r.coins() == null)
				.thenComparing(r -> r.coins() == null ? 0 : -Math.abs(r.coins()))).toList();
		// a "1 more" line would take the same room as the item
		int shown = sorted.size() <= items + 1 ? sorted.size() : items;
		for (Row row : sorted.subList(0, shown)) {
			String coins = !withCoins ? "" : " " + coinsOrNoPrice(row.coins(), ironman);
			lines.add(indent + "§7" + row.text() + coins);
		}
		if (shown == sorted.size()) return;
		Double rest = null;
		for (Row row : sorted.subList(shown, sorted.size())) rest = MoneyStats.plus(rest, row.coins());
		lines.add(indent + "§8" + (sorted.size() - shown) + " more" + (rest == null || !withCoins ? "" : " " + coins(rest)));
	}

	private static String coinsOrNoPrice(Double coins, boolean ironman) {
		return coins == null ? noPrice(ironman) : coins(coins);
	}

	public static String noPrice(boolean ironman) {
		return ironman ? "§8no NPC price" : "§8no price";
	}

	// ironman: expected, so not red
	public static String unpriced(long count, boolean ironman) {
		String items = count + (count == 1 ? " item" : " items");
		return ironman ? "§8" + items + " with no NPC price" : "§c" + items + " without a price";
	}

	private static String share(double fraction) {
		long percent = Math.round(100 * fraction);
		return percent == 0 ? "<1%" : percent + "%";
	}

	// mined 1 decimal like fine gems, raw metal stays whole
	public static String count(Part part, String item, double count) {
		boolean decimal = part == Part.MINED || part == Part.MINED_METAL && Ladder.metal(item) == null;
		if (decimal) return Format.decimal(count);
		return Format.number(Math.round(count));
	}

	public static String coins(double coins) {
		return (Math.round(coins) < 0 ? "§c" : "§6") + Coins.signed(coins);
	}
}
