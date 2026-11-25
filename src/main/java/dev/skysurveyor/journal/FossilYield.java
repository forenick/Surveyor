package dev.skysurveyor.journal;

import dev.skysurveyor.detection.Shaft;

// blocks = fossil blocks u broke, samples + shown = the action bars sampled after one (it only shows the last blocks amount)
// saved raw so the estimate can change later
public record FossilYield(int blocks, int samples, long shown) {
	public static final String ITEM = "Fossil Essence";

	// null = no fossil block, no bar
	public static FossilYield of(Shaft shaft) {
		if (shaft.fossilBlocks() == 0 && shaft.essenceSamples() == 0) return null;
		return new FossilYield(shaft.fossilBlocks(), shaft.essenceSamples(), shaft.essenceShown());
	}

	// blocks x avg bar, 0 = no bar seen
	public long essence() {
		if (samples == 0) return 0;
		return Math.round((double) blocks * shown / samples);
	}
}
