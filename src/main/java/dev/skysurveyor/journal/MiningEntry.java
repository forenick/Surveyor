package dev.skysurveyor.journal;

import java.util.Map;

import dev.skysurveyor.money.ItemPrice;

// mining.jsonl line: sacks msg while mining outside shafts. epoch ms
// new fields at the end, bump VERSION if a meaning changes
// estimated = tail before a world change, from pristine msgs (research/09)
// prices = by skyblock id then, empty = none
public record MiningEntry(int version, long sessionId, long at, Map<String, Long> items, boolean estimated,
		Map<String, ItemPrice> prices) {
	public static final int VERSION = 1;

	public MiningEntry withPrices(Map<String, ItemPrice> prices) {
		return new MiningEntry(version, sessionId, at, items, estimated, prices);
	}
}
