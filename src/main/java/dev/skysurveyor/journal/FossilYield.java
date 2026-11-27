package dev.skysurveyor.journal;

import dev.skysurveyor.detection.Shaft;

// blocks = fossil blocks broken, samples + shown = bars after one (bar shows last block only)
// saved raw, estimate can change later
public record FossilYield(int blocks, int samples, long shown) {
	public static final String ITEM = "Fossil Essence";

	// null = no fossil block, no bar
	public static FossilYield of(Shaft shaft) {
		if (shaft.fossilBlocks() == 0 && shaft.essenceSamples() == 0) return null;
		return new FossilYield(shaft.fossilBlocks(), shaft.essenceSamples(), shaft.essenceShown());
	}

	// 0 = no bar seen
	public long essence() {
		if (samples == 0) return 0;
		return Math.round((double) blocks * shown / samples);
	}
}
