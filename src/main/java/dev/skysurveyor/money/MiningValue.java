package dev.skysurveyor.money;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.journal.GemYield;
import dev.skysurveyor.journal.MiningEntry;
import dev.skysurveyor.money.ShaftValue.Line;
import dev.skysurveyor.settings.MoneySettings;
import dev.skysurveyor.settings.MoneySettings.GemTier;

// mining outside shafts, same item prices as a shafts other drops. unpriced items count 0
public final class MiningValue {
	private MiningValue() {
	}

	public static double coins(MiningEntry entry, ShaftValue.Prices prices, MoneySettings settings, ItemIds ids,
			TextPatterns patterns) {
		return total(lines(entry, prices, settings, ids, patterns));
	}

	public static double total(List<Line> lines) {
		return ShaftValue.net(lines);
	}

	// gems merged per gem in fine (or flawless) like a shafts mined gems, rest per item
	public static List<Line> lines(MiningEntry entry, ShaftValue.Prices prices, MoneySettings settings, ItemIds ids,
			TextPatterns patterns) {
		PriceRules rules = new PriceRules(prices, settings, ids, patterns);
		List<Line> lines = new ArrayList<>();
		GemYield.fineByGem(entry.items(), patterns).forEach((gem, fine) -> lines.add(ShaftValue.minedGem(rules, settings, gem, fine)));
		entry.items().forEach((item, count) -> {
			if (patterns.match("gem.item", item) == null) lines.add(ShaftValue.drop(rules, item, count));
		});
		return List.copyOf(lines);
	}

	// every id any gem tier could use, so settings can change later
	public static Map<String, ItemPrice> snapshot(MiningEntry entry, ShaftValue.Prices prices, MoneySettings settings,
			ItemIds ids, TextPatterns patterns) {
		return ShaftValue.snapshot(prices, (recording, used) -> {
			for (GemTier tier : GemTier.values()) {
				coins(entry, recording, settings.withGemTier(tier).withIronman(false), ids, patterns);
			}
		});
	}
}
