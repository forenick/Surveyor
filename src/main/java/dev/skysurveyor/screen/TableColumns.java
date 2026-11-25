package dev.skysurveyor.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import dev.skysurveyor.journal.CorpseCounts;
import dev.skysurveyor.journal.JournalEntry;
import dev.skysurveyor.journal.JournalStats;
import dev.skysurveyor.journal.JournalStats.CorpseCount;
import dev.skysurveyor.mining.Efficiency;
import dev.skysurveyor.util.Format;

// which columns fit + how wide, the shaft sort. plain java
final class TableColumns {
	// higher dropOrder hides first, 0 = never. tight = no spare room. extra = spare room handed out by fit()
	record Column(String key, String title, int width, boolean right, int dropOrder, boolean tight, int extra) {
		Column(String key, String title, int width, boolean right, int dropOrder) {
			this(key, title, width, right, dropOrder, false, 0);
		}

		int total() {
			return width + extra;
		}

		Column withWidth(int width) {
			return new Column(key, title, width, right, dropOrder, tight, 0);
		}

		Column asTight() {
			return new Column(key, title, width, right, dropOrder, true, extra);
		}
	}

	// width = least, measured per table (0 = all measured). drops: campfire, eff, #, entered, inside, powder, spawn
	static final List<Column> SHAFTS = List.of(
			new Column("number", "#", 14, false, 5), new Column("shaft", "Shaft", 44, false, 0),
			new Column("time", "Entered", 0, false, 4), new Column("spawn", "Spawn", 66, false, 1),
			new Column("inside", "Inside", 0, true, 3), new Column("corpses", "Corpses", 0, false, 0).asTight(),
			new Column("powder", "Powder", 44, true, 2), new Column("mined", "Mined", 40, true, 0),
			new Column("eff", "Eff.", 0, true, 6), new Column("coins", "Coins", 0, true, 0),
			new Column("campfire", "Campfire", 0, false, 7));

	// overview. by day: start time goes under the days date, no start column
	static final List<Column> SESSIONS = List.of(
			new Column("date", "Date", 56, false, 0), new Column("start", "Start", 32, false, 5),
			new Column("length", "Length", 44, true, 4), new Column("shafts", "Shafts", 36, true, 0),
			new Column("spawn", "Avg spawn", 78, false, 0), new Column("rate", "Shafts / h", 0, true, 3),
			new Column("powder", "Powder", 58, true, 0), new Column("lapis", "Lapis", 36, true, 6),
			new Column("coins", "Coins", 0, true, 0), new Column("coinRate", "Coins / h", 0, true, 2));

	private TableColumns() {
	}

	static List<Column> fit(List<Column> columns, int available, int gap) {
		List<Column> visible = new ArrayList<>(columns);
		while (visible.stream().mapToInt(Column::width).sum() + gap * (visible.size() - 1) > available) {
			Column drop = visible.stream().filter(c -> c.dropOrder() > 0)
					.max(Comparator.comparingInt(Column::dropOrder)).orElse(null);
			if (drop == null) break;
			visible.remove(drop);
		}
		// wide screens (gui scale 2) spread the spare room, up to 2x each
		int spare = available - visible.stream().mapToInt(Column::width).sum() - gap * (visible.size() - 1);
		int base = visible.stream().filter(c -> !c.tight()).mapToInt(Column::width).sum();
		if (spare <= 0 || base <= 0) return visible;
		List<Column> spread = new ArrayList<>();
		for (Column c : visible) {
			int extra = c.tight() ? 0 : Math.min(c.width(), spare * c.width() / base);
			spread.add(new Column(c.key(), c.title(), c.width(), c.right(), c.dropOrder(), c.tight(), extra));
		}
		return spread;
	}

	// widest text + 1 slack, never below the fixed width: vanilla font stays put, wider fonts grow it
	static int atLeast(int fixed, int widest) {
		return Math.max(fixed, widest + 1);
	}

	// width 0 = all measured
	static Column measure(Column column, int widest) {
		return column.withWidth(atLeast(column.width(), widest));
	}

	// sorted by a column thats not shown = played order, sort comes back w/ the column
	static String shownSort(String sort, List<Column> shown) {
		if (sort == null) return null;
		return shown.stream().anyMatch(c -> c.key().equals(sort)) ? sort : null;
	}

	// shaft names a to z + fastest spawn first, rest biggest first. flipped = 2nd click
	static boolean ascending(String key, boolean flipped) {
		boolean first = key.equals("shaft") || key.equals("spawn");
		return first != flipped;
	}

	// display row -> entry index. nulls last both ways, ties in played order. key null = played order
	static int[] shaftOrder(List<JournalEntry> shafts, List<Double> coins, String key, boolean flipped, int metalTier) {
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < shafts.size(); i++) order.add(i);
		if (key != null) {
			boolean ascending = ascending(key, flipped);
			order.sort((a, b) -> {
				Comparable<?> ka = sortKey(key, a, shafts.get(a), coins.get(a), metalTier);
				Comparable<?> kb = sortKey(key, b, shafts.get(b), coins.get(b), metalTier);
				if (ka == null || kb == null) return ka == kb ? Integer.compare(a, b) : ka == null ? 1 : -1;
				int compared = compare(ka, kb);
				if (!ascending) compared = -compared;
				return compared != 0 ? compared : Integer.compare(a, b);
			});
		}
		return order.stream().mapToInt(Integer::intValue).toArray();
	}

	@SuppressWarnings("unchecked")
	private static int compare(Comparable<?> a, Comparable<?> b) {
		return ((Comparable<Object>) a).compareTo(b);
	}

	private static Comparable<?> sortKey(String key, int index, JournalEntry entry, Double coins, int metalTier) {
		return switch (key) {
			case "shaft" -> entry.shaftId();
			case "spawn" -> entry.searchMillis();
			case "inside" -> entry.durationMillis();
			case "corpses" -> {
				int looted = 0;
				int total = 0;
				for (CorpseCount count : CorpseCounts.of(entry).values()) {
					looted += count.looted();
					total += count.total();
				}
				yield looted * 1000L + total;
			}
			case "powder" -> JournalStats.powder(entry);
			case "mined" -> entry.gems() != null ? (Comparable<?>) entry.gems().fineEquivalent()
					: entry.metal() != null ? entry.metal().in(metalTier) : null;
			case "coins" -> coins;
			case "eff" -> Efficiency.shown(entry.mining());
			case "campfire" -> campfires(entry);
			default -> index;
		};
	}

	// eff cell, § coded. - = skipped shaft or mined before 0.26.0
	static String efficiency(JournalEntry entry) {
		Double efficiency = Efficiency.shown(entry.mining());
		return efficiency == null ? "§8-" : "§f" + Format.percent(efficiency);
	}

	// placed or used, whichever saw more
	static int campfires(JournalEntry entry) {
		return Math.max(entry.campfiresAt().size(), entry.campfireColds() == null ? 0 : entry.campfireColds().size());
	}
}
