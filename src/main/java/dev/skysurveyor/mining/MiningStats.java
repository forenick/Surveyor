package dev.skysurveyor.mining;

// saved w/ a journal entry (Docs/efficiency-design.md), new fields at the end
// millis = from the 1st block of the main kind to leaving, setupMillis = entering to that
// blocks = own, spread = gemstone spread on top. every kind till 0.26.0-alpha.16, the main block + its pane since
// ticks, boostTicks = break time averaged over the blocks, null = none broken in it
// boostMillis, pausedMillis both inside millis
// blockTicks = the main blocks own break time outside boost, null = none or before 0.26.0-alpha.15
public record MiningStats(long millis, long setupMillis, int blocks, int spread, String block,
		Double ticks, Double boostTicks, long boostMillis, long pausedMillis, Double blockTicks) {
	public MiningStats(long millis, long setupMillis, int blocks, int spread, String block, Double ticks, Double boostTicks, long boostMillis,
			long pausedMillis) {
		this(millis, setupMillis, blocks, spread, block, ticks, boostTicks, boostMillis, pausedMillis, null);
	}

	// the gems own time, stone or snow broken on the way isnt in it
	public Double shownTicks() {
		return blockTicks != null ? blockTicks : ticks;
	}
}
