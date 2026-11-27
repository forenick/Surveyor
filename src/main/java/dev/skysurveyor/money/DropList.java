package dev.skysurveyor.money;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.regex.Matcher;

import dev.skysurveyor.data.ItemNames;
import dev.skysurveyor.data.Ladder;
import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.journal.GemYield;
import dev.skysurveyor.money.ShaftValue.Line;
import dev.skysurveyor.money.ShaftValue.Part;
import dev.skysurveyor.util.Format;

// hud drops line. items + spent = the lines net
// spent = keys + campfires (negative). unpriced = count, not rows
// sources = same coins per part, outside as 1
public record DropList(List<Row> items, List<Row> spent, int unpriced, boolean ironman, boolean noPrices, List<Source> sources) {
	public static final DropList NONE = new DropList(List.of(), List.of(), 0, false, false);
	public static final DropList NO_PRICES = new DropList(List.of(), List.of(), 0, false, true);
	// sorted after keys
	public static final String CAMPFIRE = "Portable Campfire";
	// journal tooltips name
	public static final String OUTSIDE = "Outside shafts";

	public enum Kind {
		GEM, METAL, ITEM, SOURCE, MORE, SPENT
	}

	// gems in fine, named fine. color = § code char
	public record Row(Kind kind, String name, double count, double coins, char color) {
	}

	// gems merged per gem
	public record Source(Row row, List<Row> items) {
		public Source {
			items = List.copyOf(items);
		}
	}

	public DropList {
		items = List.copyOf(items);
		spent = List.copyOf(spent);
		sources = List.copyOf(sources);
	}

	public DropList(List<Row> items, List<Row> spent, int unpriced, boolean ironman, boolean noPrices) {
		this(items, spent, unpriced, ironman, noPrices, List.of());
	}

	private static final class Sum {
		final Kind kind;
		final String name;
		final char color;
		double count;
		Double coins;

		Sum(Kind kind, String name, char color) {
			this.kind = kind;
			this.name = name;
			this.color = color;
		}

		Row row() {
			return new Row(kind, name, count, coins, color);
		}
	}

	// kept apart for by source later
	public static DropList of(List<Line> shafts, List<Line> outside, boolean ironman, TextPatterns patterns) {
		Map<String, Sum> sums = new LinkedHashMap<>();
		Map<String, Map<String, Sum>> bySource = new LinkedHashMap<>();
		for (Line line : shafts) {
			add(sums, line, patterns);
			if (line.part() != Part.KEYS && line.part() != Part.CAMPFIRES) {
				add(bySource.computeIfAbsent(line.part().displayName(), k -> new LinkedHashMap<>()), line, patterns);
			}
		}
		for (Line line : outside) {
			add(sums, line, patterns);
			add(bySource.computeIfAbsent(OUTSIDE, k -> new LinkedHashMap<>()), line, patterns);
		}
		List<Row> items = new ArrayList<>();
		List<Row> spent = new ArrayList<>();
		int unpriced = 0;
		for (Sum sum : sums.values()) {
			if (sum.coins == null) {
				// unpriced keys arent items
				if (sum.kind != Kind.SPENT) unpriced++;
				continue;
			}
			Row row = sum.row();
			if (sum.kind != Kind.SPENT) items.add(row);
			// dont count, ironman: free
			else if (sum.coins != 0) spent.add(row);
		}
		items.sort(BIGGEST);
		// keys first, biggest cost first
		spent.sort(Comparator.comparing((Row r) -> r.name().equals(CAMPFIRE)).thenComparingDouble(Row::coins));
		List<Source> sources = new ArrayList<>();
		bySource.forEach((name, sourceSums) -> {
			List<Row> sourceItems = new ArrayList<>();
			double coins = 0;
			for (Sum sum : sourceSums.values()) {
				if (sum.coins == null) continue;
				sourceItems.add(sum.row());
				coins += sum.coins;
			}
			// earned nothing = no row
			if (coins == 0) return;
			sourceItems.sort(BIGGEST);
			sources.add(new Source(new Row(Kind.SOURCE, name, 0, coins, 'f'), sourceItems));
		});
		sources.sort(Comparator.comparing(Source::row, BIGGEST));
		return new DropList(items, spent, unpriced, ironman, false, sources);
	}

	// any = something to count, priced = some had prices
	public static final class Lines {
		private final List<Line> shafts = new ArrayList<>();
		private final List<Line> outside = new ArrayList<>();
		private boolean any;
		private boolean priced;

		// null = no prices yet
		public Lines shaft(ShaftValue value) {
			any = true;
			if (value != null) {
				priced = true;
				shafts.addAll(value.lines());
			}
			return this;
		}

		// null = no prices yet
		public Lines outside(List<Line> lines) {
			any = true;
			if (lines != null) {
				priced = true;
				outside.addAll(lines);
			}
			return this;
		}

		// new object, both kept
		public Lines plus(Lines other) {
			Lines sum = new Lines();
			sum.shafts.addAll(shafts);
			sum.shafts.addAll(other.shafts);
			sum.outside.addAll(outside);
			sum.outside.addAll(other.outside);
			sum.any = any || other.any;
			sum.priced = priced || other.priced;
			return sum;
		}

		// 1 line per part + item, keeps all-time small
		public Lines summed() {
			Lines sum = new Lines();
			sum.shafts.addAll(sum(shafts));
			sum.outside.addAll(sum(outside));
			sum.any = any;
			sum.priced = priced;
			return sum;
		}

		public DropList list(boolean ironman, TextPatterns patterns) {
			if (!any) return NONE;
			if (!priced) return NO_PRICES;
			return of(shafts, outside, ironman, patterns);
		}
	}

	static List<Line> sum(List<Line> lines) {
		Map<String, Line> sums = new LinkedHashMap<>();
		for (Line line : lines) {
			sums.merge(line.part() + ":" + line.item(), line, (a, b) -> new Line(a.part(), a.item(), a.count() + b.count(),
					MoneyStats.plus(a.coins(), b.coins())));
		}
		return List.copyOf(sums.values());
	}

	private static final Comparator<Row> BIGGEST = Comparator.comparingDouble((Row r) -> -r.coins()).thenComparing(Row::name);

	private static void add(Map<String, Sum> sums, Line line, TextPatterns patterns) {
		Kind kind;
		String name;
		double count = line.count();
		String mined = ShaftValue.minedGemName(line);
		Matcher gem = mined != null ? null : patterns.match("gem.item", line.item());
		if (line.part() == Part.KEYS || line.part() == Part.CAMPFIRES) {
			kind = Kind.SPENT;
			name = line.item();
		} else if (mined != null || gem != null) {
			kind = Kind.GEM;
			// counted in fine, so named fine
			name = GemYield.fineName(mined != null ? mined : gem.group("gem"));
			count = mined != null ? ShaftValue.minedFine(line) : count / GemYield.countPerFine(gem.group("quality"));
		} else {
			kind = line.part() == Part.MINED_METAL ? Kind.METAL : Kind.ITEM;
			name = line.item();
		}
		char color = ItemNames.current().color(name);
		Sum sum = sums.get(kind + ":" + name);
		if (sum == null) sums.put(kind + ":" + name, sum = new Sum(kind, name, color));
		sum.count += count;
		sum.coins = MoneyStats.plus(sum.coins, line.coins());
	}

	// 0 = all
	public List<Source> shownSources(int rows) {
		return fold(sources, rows, s -> s.row().coins(), more -> new Source(more, List.of()));
	}

	// 0 = all. 1 left over shows itself, no "1 more"
	private static <T> List<T> fold(List<T> all, int rows, ToDoubleFunction<T> coins, Function<Row, T> wrap) {
		if (rows <= 0 || all.size() <= rows + 1) return all;
		List<T> shown = new ArrayList<>(all.subList(0, rows));
		double rest = 0;
		for (T t : all.subList(rows, all.size())) rest += coins.applyAsDouble(t);
		shown.add(wrap.apply(new Row(Kind.MORE, all.size() - rows + " more", 0, rest, '7')));
		return shown;
	}

	public boolean isEmpty() {
		return items.isEmpty() && spent.isEmpty() && unpriced == 0;
	}

	// = the coins line
	public double total() {
		double total = 0;
		for (Row row : items) total += row.coins();
		for (Row row : spent) total += row.coins();
		return total;
	}

	public List<Row> shown(int rows) {
		return fold(items, rows, Row::coins, more -> more);
	}

	// 1 decimal under 10, raw metal whole. no amount on more + source rows
	public static String amount(Row row) {
		if (row.kind() == Kind.MORE || row.kind() == Kind.SOURCE) return "";
		boolean decimal = row.kind() == Kind.GEM || row.kind() == Kind.METAL && Ladder.metal(row.name()) == null;
		// a few rough gems in fine
		if (decimal && row.count() != 0 && Math.abs(row.count()) < 0.05) return "<0.1";
		if (decimal && Math.abs(row.count()) < 9.95) return Format.decimal(row.count());
		return Format.number(Math.round(row.count()));
	}
}
