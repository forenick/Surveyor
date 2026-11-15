package dev.skysurveyor.journal;

import dev.skysurveyor.detection.ShaftEvent;
import dev.skysurveyor.detection.ShaftEvent.PickaxeAbility;
import dev.skysurveyor.detection.ShaftEvent.SacksChanged;
import dev.skysurveyor.detection.ShaftEvent.ShaftEntered;
import dev.skysurveyor.detection.ShaftEvent.ShaftLeft;

// sacks msg in a shaft w/ mining in its window = time to check prices (money-tracker-design 3.6)
// outside shafts a saved MiningEntry asks instead. LiveMoney only fetches whats stale
public final class PriceCheck {
	private boolean inShaft;
	private long lastMiningAt = -1;

	public void onBlockHit(long now) {
		if (inShaft) lastMiningAt = now;
	}

	// true = ask for stale prices now
	public boolean onEvent(ShaftEvent event, long now) {
		switch (event) {
			case PickaxeAbility ability -> {
				if (inShaft) lastMiningAt = now;
			}
			case ShaftEntered entered -> {
				inShaft = true;
				lastMiningAt = -1;
			}
			case ShaftLeft left -> inShaft = false;
			case SacksChanged sacks -> {
				return inShaft && !sacks.added().isEmpty() && lastMiningAt >= 0 && lastMiningAt >= OutsideMining.windowStart(sacks, now);
			}
			default -> {
			}
		}
		return false;
	}
}
