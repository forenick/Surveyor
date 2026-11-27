package dev.skysurveyor.mining;

import java.util.ArrayList;
import java.util.List;

import dev.skysurveyor.util.Format;

// § coded
public final class MiningText {
	// "" = empty line
	public record Lines(String value, String withSetup, List<String> tooltip) {
	}

	private MiningText() {
	}

	// null = skipped or old entry
	public static Lines of(MiningStats stats) {
		Double efficiency = Efficiency.shown(stats);
		if (efficiency == null) return null;
		String value = Format.percent(efficiency);
		List<String> tooltip = new ArrayList<>();
		tooltip.add("§fEfficiency " + value);
		tooltip.add("§7Blocks you broke §f" + Format.number(stats.blocks()));
		tooltip.add("§7Nonstop mining would break §f" + Format.number(Math.round(Efficiency.nonstop(stats))));
		tooltip.add("§8Over " + Format.duration(stats.millis()) + ", from your first gem block");
		// same ticks as nonstop or the hover wont add up
		Double ticks = stats.ticks();
		Double boostTicks = stats.boostTicks();
		if (ticks != null || boostTicks != null) {
			String line = "§8At " + ticks(ticks != null ? ticks : boostTicks) + " ticks a block";
			if (boostTicks != null) line += ticks != null ? ", " + ticks(boostTicks) + " in boost" : " in boost";
			tooltip.add(line);
		}
		if (stats.boostMillis() >= 1000 && stats.millis() > 0) {
			tooltip.add("§8Boost " + Format.duration(stats.boostMillis()) + " (" + Format.percent(stats.boostMillis(), stats.millis()) + ")");
		}
		if (stats.pausedMillis() >= 1000) tooltip.add("§8Paused " + Format.duration(stats.pausedMillis()));
		boolean setup = stats.setupMillis() >= 1000;
		Double withSetup = setup ? Efficiency.withSetup(stats) : null;
		if (setup || stats.spread() > 0) {
			tooltip.add("");
			tooltip.add("§7Not counted");
			if (setup) {
				tooltip.add("§8Setup " + Format.duration(stats.setupMillis()) + (withSetup == null ? "" : ", " + Format.percent(withSetup) + " with it"));
			}
			if (stats.spread() > 0) {
				tooltip.add("§8Gemstone Spread " + Format.number(stats.spread()) + (stats.spread() == 1 ? " block, " : " blocks, ")
						+ Format.percent(stats.spread(), stats.blocks()) + " on top");
			}
		}
		return new Lines(value, withSetup == null ? null : Format.percent(withSetup), List.copyOf(tooltip));
	}

	// avg can land between ticks
	public static String ticks(double ticks) {
		return Math.abs(ticks - Math.rint(ticks)) < 0.05 ? String.valueOf((long) Math.rint(ticks)) : Format.decimal(ticks);
	}
}
