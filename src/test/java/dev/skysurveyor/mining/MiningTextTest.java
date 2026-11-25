package dev.skysurveyor.mining;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MiningTextTest {
	// nicks jasper shaft of 2026-11-22 13:01 as 0.26.0-alpha.28 saved it
	private static final MiningStats JASPER = new MiningStats(710_457, 50_722, 1255, 210, "magenta_stained_glass", 10.0, 4.0, 155_778, 0, 10.0);

	@Test
	void theRowAndItsHover() {
		MiningText.Lines lines = MiningText.of(JASPER);
		assertEquals("66%", lines.value());
		assertEquals("63%", lines.withSetup());
		assertEquals(List.of(
				"§fEfficiency 66%",
				"§7Blocks you broke §f1,255",
				"§7Nonstop mining would break §f1,888",
				"§8Over 11m 50s, from your first gem block",
				"§8At 10 ticks a block, 4 in boost",
				"§8Boost 2m 35s (22%)",
				"",
				"§7Not counted",
				"§8Setup 50s, 63% with it",
				"§8Gemstone Spread 210 blocks, 17% on top"), lines.tooltip());
	}

	@Test
	void pausedLineAndMixedTicks() {
		MiningText.Lines lines = MiningText.of(new MiningStats(600_000, 500, 900, 0, "x", 12.4, null, 0, 14_000));
		assertNull(lines.withSetup());
		assertEquals(List.of("§fEfficiency 93%", "§7Blocks you broke §f900", "§7Nonstop mining would break §f968",
				"§8Over 10m 0s, from your first gem block", "§8At 12.4 ticks a block", "§8Paused 14s"), lines.tooltip());
	}

	@Test
	void allBoostShowsSetupWithoutANumber() {
		MiningText.Lines lines = MiningText.of(new MiningStats(20_000, 30_000, 100, 0, "x", null, 4.0, 20_000, 0));
		assertNull(lines.withSetup());
		assertEquals(List.of("§fEfficiency 100%", "§7Blocks you broke §f100", "§7Nonstop mining would break §f100",
				"§8Over 20s, from your first gem block", "§8At 4 ticks a block in boost", "§8Boost 20s (100%)", "", "§7Not counted",
				"§8Setup 30s"), lines.tooltip());
	}

	@Test
	void hoverTicksMatchTheNonstopCount() {
		// pane faster than the block: ticks 9 over both, blockTicks 10 for the block alone
		MiningText.Lines lines = MiningText.of(new MiningStats(600_000, 0, 900, 0, "x", 9.0, null, 0, 0, 10.0));
		assertEquals("§7Nonstop mining would break §f1,333", lines.tooltip().get(2));
		assertEquals("§8At 9 ticks a block", lines.tooltip().get(4));
	}
}
