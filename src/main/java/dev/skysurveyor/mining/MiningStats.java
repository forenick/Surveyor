package dev.skysurveyor.mining;

// saved in the journal, new fields at the end
// millis = 1st main block to leaving, setupMillis = entering to that
// blocks = own, spread on top. all kinds till 0.26.0-alpha.16, main + pane since
// ticks, boostTicks = avg break time, null = none
// boostMillis, pausedMillis inside millis
// blockTicks = main block outside boost, null before 0.26.0-alpha.15
// boostBlocks inside blocks, null before 0.27.0-alpha.3
public record MiningStats(long millis, long setupMillis, int blocks, int spread, String block,
		Double ticks, Double boostTicks, long boostMillis, long pausedMillis, Double blockTicks, Integer boostBlocks) {
	public MiningStats(long millis, long setupMillis, int blocks, int spread, String block, Double ticks, Double boostTicks, long boostMillis,
			long pausedMillis) {
		this(millis, setupMillis, blocks, spread, block, ticks, boostTicks, boostMillis, pausedMillis, null, null);
	}

	public MiningStats(long millis, long setupMillis, int blocks, int spread, String block, Double ticks, Double boostTicks, long boostMillis,
			long pausedMillis, Double blockTicks) {
		this(millis, setupMillis, blocks, spread, block, ticks, boostTicks, boostMillis, pausedMillis, blockTicks, null);
	}

	// gem only, no stone or snow
	public Double shownTicks() {
		return blockTicks != null ? blockTicks : ticks;
	}
}
