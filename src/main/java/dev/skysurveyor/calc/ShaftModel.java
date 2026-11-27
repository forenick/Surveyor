package dev.skysurveyor.calc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// calculator-design.md, research/11 s3, s4, s17
// gain = shaft coins - its minutes x loop coins/min w/ the best rule
public final class ShaftModel {
	public static final int MAX_LAPIS = 4;

	private ShaftModel() {
	}

	// per lapis count. coins = gems + other. extraSecond = 1 s past freeze vs leaving
	// pristine = gems 1 more looted corpse adds, mined from start. campfire null = no campfire price
	// seconds = inside, shorter if the route ends 1st. away = secs it takes from the loop, way back in
	// route = mining secs till the route is done, infinity = no route
	public record Shaft(double seconds, double fine, double gems, double other, double gain, Double campfire, double extraSecond,
			double pristine, double away, double route) {
		public double coins() {
			return gems + other;
		}
	}

	// perFine null = no price, its row says so. mineFrom = 1st green count, null = never
	// chance = how often u spawn it
	public record Row(GameData.Gem gem, double ticks, double boostTicks, Double perFine, List<Shaft> lapis, Integer mineFrom, double chance) {
		// only goes up w/ lapis. null = never
		public Integer campfireFrom(double atLeast) {
			for (int k = 0; k < lapis.size(); k++) {
				Double campfire = lapis.get(k).campfire();
				if (campfire != null && campfire >= atLeast) return k;
			}
			return null;
		}

		// 0 lapis
		public double insidePerHour() {
			Shaft none = lapis.getFirst();
			return none.coins() / none.seconds() * 3600;
		}
	}

	// leaving = coins/s of the whole loop w/ the best rule (all >= 0 mined), gains are vs this
	// greenOnly = loop w/ only cells >= band mined, the headline (nick 11-26, research/11 s18)
	// minedShare = green cells, closeShare = yellow ones
	// keyCosts: type -> key, missing = no price. keyBand = close call band for keys
	// away = avg secs a green shaft takes from the loop: inside + way back. campfireCost null = no price
	// layouts: id -> row on that layouts own efficiency, rows = the gems mix of them
	public record Guide(double leaving, double greenOnly, double jasperOnly, double minedShare, double closeShare, double freeze,
			double campfire, double band, List<Row> rows, Map<String, Row> layouts, double[] lapisChance, Double campfireCost, double keyLoot,
			Map<String, Double> keyCosts, double keyBand, double away) {
		// key corpse looted at start: loot + pristine - key. null = no key price
		// same at every lapis count, 1 more corpse adds the same flawed share
		public Double keyGain(Row row, int lapis, String type) {
			Double key = keyCosts.get(type);
			if (key == null || row.perFine() == null) return null;
			return row.lapis().get(lapis).pristine() + keyLoot - key;
		}

		// skipped: loot - key, no pristine. null = no key price
		public Double keyGainSkipped(String type) {
			Double key = keyCosts.get(type);
			return key == null ? null : keyLoot - key;
		}

		public double perHour(double perSecond) {
			return perSecond * 3600;
		}

		public Row row(String code) {
			return rows.stream().filter(r -> r.gem().code().equals(code)).findFirst().orElse(null);
		}

		// the layout u are in, its gem if the layout has no number
		public Row row(String code, String variant) {
			Row layout = variant == null ? null : layouts.get(code + "_" + variant);
			return layout != null ? layout : row(code);
		}
	}

	// prices: gem -> coins per fine at sell settings. campfire = cost, null = no price
	public static Guide run(YourNumbers n, Map<String, Double> prices, Double campfireCost, GameData data) {
		return run(n, prices, campfireCost, Map.of(), data);
	}

	private record Pace(GameData.Gem gem, double ticks, double boostTicks, double blocksPerSecond) {
	}

	// inside + mining: cut where the route ends. campfire = secs of it b4 the route ends
	// route = mining secs till its done, infinity = none
	private record Time(double inside, double mining, double away, double campfire, double route) {
	}

	// a gem on 1 of its layouts. layout null = the gem w/o layout numbers
	private record Variant(String layout, double weight, Pace pace, Time time) {
	}

	// same for every row
	private record Loop(double otherPerSecond, double perTier, double perCorpse, double band, Double campfireCost, double leaving) {
	}

	public static Guide run(YourNumbers n, Map<String, Double> prices, Double campfireCost, Map<String, Double> keyCosts, GameData data) {
		double freeze = n.get(NumberId.FREEZE);
		double campfireSeconds = n.get(NumberId.CAMPFIRE);
		double perTier = data.value("per_tier");
		double perCorpse = data.value("per_corpse");
		double otherPerSecond = n.get(NumberId.OTHER) / 60;
		double[] chance = lapisChance(n, data);
		double band = n.get(NumberId.CLOSE_CALL);

		Map<GameData.Gem, List<Variant>> variants = new LinkedHashMap<>();
		for (GameData.Gem gem : data.gems()) variants.put(gem, variants(n, gem, data));

		List<Option> options = new ArrayList<>();
		variants.forEach((gem, list) -> {
			Double perFine = prices.get(gem.code());
			if (perFine == null) return;
			double type = typeChance(n, gem.code(), data);
			for (Variant variant : list) {
				double[] fine = fine(n, variant.pace(), variant.time().mining(), perTier, perCorpse);
				for (int k = 0; k <= MAX_LAPIS; k++) {
					double coins = fine[k] * perFine + otherPerSecond * variant.time().inside();
					options.add(new Option(gem.code(), coins / variant.time().away(), type * variant.weight() * chance[k], variant.time().away()));
				}
			}
		});
		double expectedLapis = 0;
		for (int k = 0; k <= MAX_LAPIS; k++) expectedLapis += k * chance[k];
		double base = n.get(NumberId.COINS_PER_SPAWN) + n.get(NumberId.LAPIS_LOOT) * expectedLapis;
		// 0 + 0 typed = div by 0
		double cycle = Math.max(1, n.get(NumberId.SPAWN) + n.get(NumberId.NOT_MINING));
		double leaving = best(options, base, cycle);
		double jasperOnly = rate(options.stream().filter(o -> o.code().equals("JASP")).toList(), base, cycle);

		Loop loop = new Loop(otherPerSecond, perTier, perCorpse, band, campfireCost, leaving);
		List<Row> rows = new ArrayList<>();
		Map<String, Row> layouts = new LinkedHashMap<>();
		double minedShare = 0;
		double closeShare = 0;
		List<Option> green = new ArrayList<>();
		for (Map.Entry<GameData.Gem, List<Variant>> entry : variants.entrySet()) {
			GameData.Gem gem = entry.getKey();
			Double perFine = prices.get(gem.code());
			double type = typeChance(n, gem.code(), data);
			List<Row> own = new ArrayList<>();
			for (Variant variant : entry.getValue()) {
				Row row = row(n, variant.pace(), variant.time(), perFine, type * variant.weight(), loop);
				own.add(row);
				if (variant.layout() != null) layouts.put(variant.layout(), row);
			}
			Row row = mix(own, entry.getValue(), type, band, perFine != null);
			rows.add(row);
			if (perFine == null) continue;
			for (int k = 0; k <= MAX_LAPIS; k++) {
				double gain = row.lapis().get(k).gain();
				if (gain >= band) {
					minedShare += type * chance[k];
					// the gems cell decides, each layout w/ its own time
					for (Row layout : own) {
						Shaft shaft = layout.lapis().get(k);
						green.add(new Option(gem.code(), shaft.coins() / shaft.away(), layout.chance() * chance[k], shaft.away()));
					}
				} else if (gain >= 0) {
					closeShare += type * chance[k];
				}
			}
		}
		double greenOnly = rate(green, base, cycle);
		// cycle has NOT_MINING per shaft (skip time inside + short way back),
		// a mined one swaps in its longer way back
		double away = Math.max(1, freeze + n.get(NumberId.BACK_MINED) - n.get(NumberId.NOT_MINING));
		double chances = green.stream().mapToDouble(Option::chance).sum();
		if (chances > 0) away = green.stream().mapToDouble(o -> o.chance() * o.seconds()).sum() / chances;
		return new Guide(leaving, greenOnly, jasperOnly, minedShare, closeShare, freeze, campfireSeconds, band, List.copyOf(rows),
				Collections.unmodifiableMap(layouts), chance, campfireCost, n.get(NumberId.KEY_LOOT), Map.copyOf(keyCosts),
				n.get(NumberId.KEY_CLOSE_CALL), away);
	}

	// a gems layouts by how often u get each. none = the one efficiency, no route
	private static List<Variant> variants(YourNumbers n, GameData.Gem gem, GameData data) {
		List<Variant> out = new ArrayList<>();
		double total = 0;
		for (GameData.Layout layout : data.layoutsOf(gem.code())) {
			YourNumbers.LayoutNum num = n.layouts().get(layout.id());
			if (num != null) total += num.weight();
		}
		if (total <= 0) {
			Pace pace = pace(n, gem, timeMining(n, data), data);
			return List.of(new Variant(null, 1, pace, time(n, pace, 0)));
		}
		for (GameData.Layout layout : data.layoutsOf(gem.code())) {
			YourNumbers.LayoutNum num = n.layouts().get(layout.id());
			if (num == null) continue;
			Pace pace = pace(n, gem, num.efficiency().value() * toShare(n, gem.strength(), data), data);
			out.add(new Variant(layout.id(), num.weight() / total, pace, time(n, pace, num.route().value())));
		}
		return out;
	}

	// route = own blocks till its done, 0 = no limit. u leave when it ends (nick 11-26, research/17 s9)
	private static Time time(YourNumbers n, Pace pace, double route) {
		double freeze = n.get(NumberId.FREEZE);
		double setup = Math.min(n.get(NumberId.SETUP), freeze);
		double mining = freeze - setup;
		double inside = freeze;
		double campfire = n.get(NumberId.CAMPFIRE);
		double own = pace.blocksPerSecond() / (1 + n.get(NumberId.SPREAD));
		double routeSeconds = Double.POSITIVE_INFINITY;
		if (route > 0 && own > 0) {
			routeSeconds = route / own;
			campfire = Math.max(0, Math.min(campfire, routeSeconds - mining));
			if (routeSeconds < mining) {
				mining = routeSeconds;
				inside = setup + mining;
			}
		}
		return new Time(inside, mining, Math.max(1, inside + n.get(NumberId.BACK_MINED) - n.get(NumberId.NOT_MINING)), campfire, routeSeconds);
	}

	// a gems row = its layouts by weight
	private static Row mix(List<Row> rows, List<Variant> variants, double chance, double band, boolean priced) {
		Row first = rows.getFirst();
		List<Shaft> lapis = new ArrayList<>();
		Integer mineFrom = null;
		for (int k = 0; k <= MAX_LAPIS; k++) {
			double[] sum = new double[8];
			boolean campfire = true;
			for (int i = 0; i < rows.size(); i++) {
				Shaft shaft = rows.get(i).lapis().get(k);
				double w = variants.get(i).weight();
				double[] parts = {shaft.seconds(), shaft.fine(), shaft.gems(), shaft.other(), shaft.gain(), shaft.campfire() == null ? 0 : shaft.campfire(),
						shaft.extraSecond(), shaft.pristine()};
				for (int j = 0; j < parts.length; j++) sum[j] += w * parts[j];
				if (shaft.campfire() == null) campfire = false;
			}
			double away = 0;
			double route = 0;
			for (int i = 0; i < rows.size(); i++) {
				double w = variants.get(i).weight();
				away += w * rows.get(i).lapis().get(k).away();
				// 0 x infinity = NaN
				if (w > 0) route += w * rows.get(i).lapis().get(k).route();
			}
			lapis.add(new Shaft(sum[0], sum[1], sum[2], sum[3], sum[4], campfire ? sum[5] : null, sum[6], sum[7], away, route));
			if (priced && sum[4] >= band && mineFrom == null) mineFrom = k;
		}
		return new Row(first.gem(), first.ticks(), first.boostTicks(), first.perFine(), List.copyOf(lapis), mineFrom, chance);
	}

	// share = of the time really mining
	private static Pace pace(YourNumbers n, GameData.Gem gem, double share, GameData data) {
		double ticks = n.get(YourNumbers.ticksOf(gem.strength()));
		double boostTicks = boostTicks(n, gem.strength(), ticks, data);
		return new Pace(gem, ticks, boostTicks, share * (1 + n.get(NumberId.SPREAD)) * perfect(n, ticks, boostTicks, n.get(NumberId.LOST_TICKS)));
	}

	// fine per lapis count
	private static double[] fine(YourNumbers n, Pace pace, double mining, double perTier, double perCorpse) {
		double[] perLapis = new double[MAX_LAPIS + 1];
		for (int k = 0; k <= MAX_LAPIS; k++) {
			double flawed = n.get(NumberId.FLAWED) + perCorpse / 100 * k;
			perLapis[k] = pace.blocksPerSecond() * mining * n.get(NumberId.GEMS_PER_BLOCK) * (1 + (perTier - 1) * flawed) / (perTier * perTier);
		}
		return perLapis;
	}

	private static Row row(YourNumbers n, Pace pace, Time time, Double perFine, double chance, Loop loop) {
		double[] fine = fine(n, pace, time.mining(), loop.perTier(), loop.perCorpse());
		double fineDrops = loop.perTier() * loop.perTier();
		List<Shaft> lapis = new ArrayList<>();
		Integer mineFrom = null;
		for (int k = 0; k <= MAX_LAPIS; k++) {
			double gems = perFine == null ? 0 : fine[k] * perFine;
			double other = loop.otherPerSecond() * time.inside();
			double gain = gems + other - time.away() * loop.leaving();
			// extra minutes are all mining, no setup (research/11 s17, v6)
			double extraRate = time.mining() <= 0 ? 0 : gems / time.mining() + loop.otherPerSecond();
			Double campfire = loop.campfireCost() == null ? null : time.campfire() * (extraRate - loop.leaving()) - loop.campfireCost();
			double blocks = pace.blocksPerSecond() * time.mining();
			double pristine = perFine == null ? 0
					: blocks * n.get(NumberId.GEMS_PER_BLOCK) * (loop.perTier() - 1) * loop.perCorpse() / 100 / fineDrops * perFine;
			lapis.add(new Shaft(time.inside(), fine[k], gems, other, gain, campfire, extraRate - loop.leaving(), pristine, time.away(), time.route()));
			if (perFine != null && gain >= loop.band() && mineFrom == null) mineFrom = k;
		}
		return new Row(pace.gem(), pace.ticks(), pace.boostTicks(), perFine, List.copyOf(lapis), mineFrom, chance);
	}

	// boost + rest, each w/ lost ticks per block (research/11 s17)
	static double perfect(YourNumbers n, double ticks, double boostTicks, double lost) {
		double boost = n.get(NumberId.BOOST);
		return boost * 20 / (boostTicks + lost) + (1 - boost) * 20 / (ticks + lost);
	}

	// efficiency counts blocks vs pure ticks, this takes lost ticks back out
	// the one efficiency: measured on ur most mined gems
	static double timeMining(YourNumbers n, GameData data) {
		return n.get(NumberId.EFFICIENCY) * toShare(n, reference(n, data), data);
	}

	// strongest w/ a tracked number
	static int reference(YourNumbers n, GameData data) {
		int reference = data.strengths().getFirst();
		int basis = -1;
		for (int strength : data.strengths()) {
			YourNumbers.Num ticks = n.num(YourNumbers.ticksOf(strength));
			if (ticks.basis() > basis) {
				basis = ticks.basis();
				reference = strength;
			}
		}
		return reference;
	}

	// efficiency on a gem x this = share of the time mining
	static double toShare(YourNumbers n, int strength, GameData data) {
		double ticks = n.get(YourNumbers.ticksOf(strength));
		double boostTicks = boostTicks(n, strength, ticks, data);
		return perfect(n, ticks, boostTicks, 0) / perfect(n, ticks, boostTicks, n.get(NumberId.LOST_TICKS));
	}

	// for the strongest gems, rest scale w/ strength. capped at non-boost
	static double boostTicks(YourNumbers n, int strength, double ticks, GameData data) {
		int strongest = data.strengths().getFirst();
		double scaled = Math.max(data.value("min_ticks"), Math.floor(n.get(NumberId.BOOST_TICKS) * strength / strongest + 0.5));
		return Math.min(ticks, scaled);
	}

	// 2 corpses, 50% a 3rd, extra corpse perk 1 more (research/11 s10)
	public static double[] lapisChance(YourNumbers n, GameData data) {
		double extra = n.get(NumberId.EXTRA_CORPSE);
		double third = data.value("third_corpse");
		double lapis = n.get(NumberId.LAPIS_SHARE);
		int base = (int) data.value("corpses_base");
		double[] corpses = new double[base + 3];
		corpses[base] = (1 - third) * (1 - extra);
		corpses[base + 1] = third * (1 - extra) + (1 - third) * extra;
		corpses[base + 2] = third * extra;
		double[] chance = new double[MAX_LAPIS + 1];
		for (int count = base; count < corpses.length; count++) {
			for (int k = 0; k <= Math.min(count, MAX_LAPIS); k++) {
				chance[k] += corpses[count] * choose(count, k) * Math.pow(lapis, k) * Math.pow(1 - lapis, count - k);
			}
		}
		return chance;
	}

	// spawn gem this often, others share the rest (last block rule, research/14)
	static double typeChance(YourNumbers n, String code, GameData data) {
		double types = data.value("shaft_types");
		double spawnGem = n.get(NumberId.SPAWN_GEM);
		if (n.spawnGem() == null) return 1 / types;
		return code.equals(n.spawnGem()) ? spawnGem : (1 - spawnGem) / (types - 1);
	}

	// a gem w/ k lapis: coins/s inside + how often + secs it takes
	private record Option(String code, double rate, double chance, double seconds) {
	}

	// mine all options at or above a rate, best rate wins
	private static double best(List<Option> options, double base, double cycle) {
		List<Option> sorted = new ArrayList<>(options);
		sorted.sort(Comparator.comparingDouble((Option o) -> -o.rate()));
		double coins = base;
		double time = cycle;
		double best = base / cycle;
		for (Option option : sorted) {
			coins += option.rate() * option.chance() * option.seconds();
			time += option.chance() * option.seconds();
			best = Math.max(best, coins / time);
		}
		return best;
	}

	private static double rate(List<Option> mined, double base, double cycle) {
		double coins = base;
		double time = cycle;
		for (Option option : mined) {
			coins += option.rate() * option.chance() * option.seconds();
			time += option.chance() * option.seconds();
		}
		return coins / time;
	}

	private static double choose(int n, int k) {
		double result = 1;
		for (int i = 1; i <= k; i++) result = result * (n - k + i) / i;
		return result;
	}
}
