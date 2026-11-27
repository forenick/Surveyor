package dev.skysurveyor.hud;

import dev.skysurveyor.data.ShaftNames;
import dev.skysurveyor.detection.Shaft;
import dev.skysurveyor.hud.HudData.SessionTotals;
import dev.skysurveyor.hud.HudData.Totals;
import dev.skysurveyor.journal.MetalYield;

// metal in enchanted, the default
final class HudFixtures {
	private HudFixtures() {
	}

	static HudData live(SessionTotals session, Totals allTime, Totals thisShaft, Long spawnTimer, Shaft shaft, long now,
			ShaftNames names) {
		return HudData.live(session, allTime, thisShaft, spawnTimer, shaft, now, names, MetalYield.ENCHANTED);
	}
}
