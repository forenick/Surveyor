package dev.skysurveyor.calc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.skysurveyor.calc.YourNumbers.LayoutNum;
import dev.skysurveyor.calc.YourNumbers.Num;
import dev.skysurveyor.calc.YourNumbers.Source;
import dev.skysurveyor.data.ShaftNames;
import dev.skysurveyor.detection.Shaft;
import dev.skysurveyor.journal.JournalEntry;
import dev.skysurveyor.mining.Efficiency;
import dev.skysurveyor.util.Format;
import dev.skysurveyor.util.Stats;

// efficiency per shaft layout (calculator-design.md, research/17)
public final class Layouts {
	// from the 1st shaft, like the one efficiency (nick 11-26). 1 shaft is off by 2 to 2.5 pts (research/17 s1)
	public static final int MIN_SHAFTS = 1;
	// a layouts own numbers: its newest few shafts whatever the range. u get 1 or 2 a week on a layout,
	// 2 sessions w/o a mined shaft took 2 to 3 layouts out (nick 11-27). display choice
	public static final int LAST_SHAFTS = 5;
	// own layout odds per gem from this many shafts of it. display choice
	public static final int MIN_GEM_SHAFTS = 10;
	// config.json keys, never rename: efficiency_PERI_2, efficiency_CRYS_9 (crystals per bp since alpha.19)
	private static final Pattern KEY = Pattern.compile("^" + NumberId.EFFICIENCY.key() + "_([A-Z]{4}_\\w)$");

	// route_CITR_1, route_CRYS (every crystal: 1 map, 1 route). own blocks, 0 = no limit
	private static final String ROUTE = "route";
	private static final Pattern ROUTE_KEY = Pattern.compile("^" + ROUTE + "_([A-Z]{4}(_\\w)?)$");
	// left w/ cold to spare this often on a layout = its route ended (research/17 s9)
	public static final int MIN_ROUTE_SHAFTS = 2;
	// warmer than this at the exit = didnt leave for the cold. display choice
	static final int ROUTE_COLD = -90;
	// typo guard, the longest route is 1,937
	private static final double MAX_ROUTE = 100_000;

	private Layouts() {
	}

	public static String routeGroup(GameData.Layout layout) {
		return crystalGroup(layout) ? "CRYS" : layout.id();
	}

	public static String routeKey(GameData.Layout layout) {
		return ROUTE + "_" + routeGroup(layout);
	}

	// "Peridot 2", "Crystal"
	public static String routeName(ShaftNames names, GameData.Layout layout) {
		return crystalGroup(layout) ? "Crystal" : name(names, layout);
	}

	public static boolean isRouteKey(String key) {
		return ROUTE_KEY.matcher(key).matches();
	}

	public static boolean validRoute(double value) {
		return Double.isFinite(value) && value >= 0 && value <= MAX_ROUTE;
	}

	// null = not a block count. "784", "1,479"
	public static Double parseRoute(String text) {
		String t = NumberId.commas(text.strip().replace(" ", ""));
		if (t == null || t.isEmpty()) return null;
		try {
			double value = Double.parseDouble(t);
			return validRoute(value) ? value : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	public static String formatRoute(double blocks) {
		return blocks <= 0 ? "0" : Format.number(Math.round(blocks));
	}

	// he left himself w/ cold to spare: the route was done
	static boolean routeDone(JournalEntry entry) {
		Shaft.ExitReason exit = entry.exitReason();
		return (exit == Shaft.ExitReason.BYE || exit == Shaft.ExitReason.WARP) && entry.coldAtExit() != null && entry.coldAtExit() > ROUTE_COLD
				&& entry.mining() != null && entry.mining().blocks() > 0;
	}

	public static String typedKey(GameData.Layout layout) {
		return NumberId.EFFICIENCY.key() + "_" + layout.group();
	}

	// efficiency_JASP_C (alpha.14 to 18) -> efficiency_CRYS_9. null = not an old crystal key
	public static String movedCrystalKey(String key) {
		String id = layoutOf(key);
		GameData.Layout layout = id == null ? null : GameData.defaults().layout(id);
		return layout == null || !crystalGroup(layout) ? null : typedKey(layout);
	}

	public static boolean crystalGroup(GameData.Layout layout) {
		return !layout.group().equals(layout.id());
	}

	// null = not a layout key
	public static String layoutOf(String key) {
		Matcher m = KEY.matcher(key);
		return m.matches() ? m.group(1) : null;
	}

	// 1 decimal, layouts are 1 to 5 pts apart
	public static String format(double efficiency) {
		return Format.decimal(efficiency * 100) + "%";
	}

	// "Peridot 2", "BP9 crystal"
	public static String groupName(ShaftNames names, GameData.Layout layout, GameData data) {
		return crystalGroup(layout) ? "BP" + data.gem(layout.gem()).breakingPower() + " crystal" : name(names, layout);
	}

	// "Peridot 2", "Peridot crystal"
	public static String name(ShaftNames names, GameData.Layout layout) {
		return names.shaftName(layout.gem(), layout.variant());
	}

	// n = every other number, ticks + lost ticks in. mined = the shafts behind the mining numbers,
	// everMined = every mined shaft oldest first, allTime = every shaft
	// per group (a layout, crystals per bp): typed, tracked from MIN_SHAFTS on it, else ur level
	// a typed Efficiency is the level + beats tracked layouts
	static Map<String, LayoutNum> learn(YourNumbers n, List<JournalEntry> mined, List<JournalEntry> everMined, List<JournalEntry> allTime,
			Map<String, Double> typed, GameData data) {
		boolean typedLevel = n.num(NumberId.EFFICIENCY).source() == Source.SET;
		// level = share of the time mining, so gems w/ other ticks compare
		List<Double> levels = new ArrayList<>();
		for (JournalEntry entry : mined) {
			Double efficiency = Efficiency.shown(entry.mining());
			GameData.Gem gem = data.gem(entry.shaftType());
			if (efficiency != null && Double.isFinite(efficiency) && gem != null) levels.add(efficiency * ShaftModel.toShare(n, gem.strength(), data));
		}
		double level = typedLevel || levels.isEmpty() ? ShaftModel.timeMining(n, data) : Stats.median(levels);
		Map<String, List<Double>> own = new HashMap<>();
		Map<String, List<Double>> routes = new HashMap<>();
		for (JournalEntry entry : everMined) {
			Double efficiency = Efficiency.shown(entry.mining());
			GameData.Layout layout = data.layout(entry.shaftId());
			if (layout == null) continue;
			if (routeDone(entry)) routes.computeIfAbsent(routeGroup(layout), id -> new ArrayList<>()).add((double) entry.mining().blocks());
			if (efficiency != null && Double.isFinite(efficiency)) own.computeIfAbsent(layout.group(), id -> new ArrayList<>()).add(efficiency);
		}
		own.replaceAll((group, list) -> last(list));
		routes.replaceAll((group, list) -> last(list));

		Map<String, Integer> seen = new HashMap<>();
		Map<String, Integer> perGem = new HashMap<>();
		for (JournalEntry entry : allTime) {
			GameData.Layout layout = data.layout(entry.shaftId());
			if (layout == null) continue;
			seen.merge(layout.id(), 1, Integer::sum);
			perGem.merge(layout.gem(), 1, Integer::sum);
		}

		Map<String, LayoutNum> out = new LinkedHashMap<>();
		for (GameData.Layout layout : data.layouts()) {
			GameData.Gem gem = data.gem(layout.gem());
			if (gem == null) continue;
			List<Double> mine = own.get(layout.group());
			Double tracked = mine == null ? null : Stats.median(mine);
			int basis = mine == null ? 0 : mine.size();
			String unit = mine == null ? null : "shafts";
			Double set = typed.get(typedKey(layout));
			Num num;
			if (set != null && NumberId.EFFICIENCY.valid(set)) {
				num = new Num(set, Source.SET, tracked, basis, unit);
			} else if (!typedLevel && basis >= MIN_SHAFTS) {
				num = new Num(tracked, Source.TRACKED, tracked, basis, unit);
			} else {
				num = new Num(level / ShaftModel.toShare(n, gem.strength(), data), Source.CALCULATED, tracked, basis, unit);
			}
			int ofGem = perGem.getOrDefault(layout.gem(), 0);
			int here = seen.getOrDefault(layout.id(), 0);
			// even till MIN_GEM_SHAFTS, variant odds unconfirmed
			double weight = ofGem >= MIN_GEM_SHAFTS ? (double) here / ofGem : 1.0 / data.layoutsOf(layout.gem()).size();
			out.put(layout.id(), new LayoutNum(num, route(layout, routes.get(routeGroup(layout)), typed), weight, here, ofGem));
		}
		return out;
	}

	private static List<Double> last(List<Double> oldestFirst) {
		return oldestFirst.subList(Math.max(0, oldestFirst.size() - LAST_SHAFTS), oldestFirst.size());
	}

	// typed, his own from MIN_ROUTE_SHAFTS left early, else the simulated count or no limit
	private static Num route(GameData.Layout layout, List<Double> left, Map<String, Double> typed) {
		Double tracked = left == null ? null : Stats.median(left);
		int basis = left == null ? 0 : left.size();
		String unit = left == null ? null : "shafts";
		Double set = typed.get(routeKey(layout));
		if (set != null && validRoute(set)) return new Num(set, Source.SET, tracked, basis, unit);
		if (basis >= MIN_ROUTE_SHAFTS) return new Num(tracked, Source.TRACKED, tracked, basis, unit);
		// 0 in calculator.txt = no simulated count to trust (crystals), no limit till his own
		return new Num(layout.route(), layout.route() > 0 ? Source.CALCULATED : Source.START, tracked, basis, unit);
	}
}
