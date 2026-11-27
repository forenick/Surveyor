package dev.skysurveyor.journal;

import dev.skysurveyor.detection.ShaftEvent;
import dev.skysurveyor.detection.ShaftEvent.SacksChanged;
import dev.skysurveyor.detection.ShaftEvent.ShaftEntered;

// sacks msg in a shaft w/ mining in window = check prices (money-tracker-design 3.6)
// outside shafts a saved MiningEntry asks. LiveMoney only fetches stale
public final class PriceCheck {
	// shaft scope never asks the tab list
	private final MiningClock clock = new MiningClock(MiningClock.Scope.SHAFT, () -> false);

	public void onBlockHit(long now) {
		clock.onBlockHit(now);
	}

	// true = ask now
	public boolean onEvent(ShaftEvent event, long now) {
		clock.onEvent(event, now);
		if (event instanceof ShaftEntered) clock.reset();
		return event instanceof SacksChanged sacks && clock.inShaft() && !sacks.added().isEmpty()
				&& clock.minedSince(OutsideMining.windowStart(sacks, now));
	}
}
