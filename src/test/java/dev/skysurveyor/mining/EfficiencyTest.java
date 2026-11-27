package dev.skysurveyor.mining;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class EfficiencyTest {
	// rec 5 jasper shaft at 16:58 (efficiency-design.md s6)
	private static final MiningStats JASPER = new MiningStats(705_000, 36_000, 1168, 63, "magenta_stained_glass", 13.0, 4.0, 188_000, 0);

	@Test
	void theDesignDocsShaftIs67Percent() {
		// 188 s x 20 / 4 + 517 s x 20 / 13
		assertEquals(1735.4, Efficiency.nonstop(JASPER), 0.1);
		assertEquals(0.673, Efficiency.of(JASPER), 0.001);
	}

	@Test
	void oneBreakTimeMissingUsesTheOther() {
		MiningStats noBoostBlocks = new MiningStats(100_000, 0, 100, 0, "x", 10.0, null, 20_000, 0);
		assertEquals(100_000 / 50.0 / 10, Efficiency.nonstop(noBoostBlocks), 1e-9);
		MiningStats onlyBoostBlocks = new MiningStats(20_000, 0, 100, 0, "x", null, 4.0, 20_000, 0);
		assertEquals(100.0, Efficiency.nonstop(onlyBoostBlocks), 1e-9);
	}

	@Test
	void nothingToSay() {
		assertNull(Efficiency.of(null));
		assertNull(Efficiency.of(new MiningStats(60_000, 0, 0, 0, null, null, null, 0, 0)));
		assertNull(Efficiency.of(new MiningStats(0, 0, 5, 0, "x", 13.0, null, 0, 0)));
	}

	// nicks PERI_C 11-21 19:22 as alpha.16 saved it (hard stone counted)
	@Test
	void stoneCountedSavesAreHidden() {
		MiningStats saved = new MiningStats(712_790, 41_776, 1949, 4475, "stone", 7.6747776033490345, 3.937777777777778, 138_392, 55_449,
				4.0);
		assertEquals(0.886, Efficiency.of(saved), 0.001, "what alpha.16 showed");
		assertNull(Efficiency.shown(saved));
		assertNull(Efficiency.record(saved));
		assertNull(MiningText.of(saved));
		// a real shaft: 180 spread on 1,054 blocks
		assertEquals(0.60, Efficiency.shown(new MiningStats(712_790, 39_000, 1054, 180, "green_stained_glass", 8.6, 4.0, 141_000, 55_449, 11.0)),
				0.1);
	}

	@Test
	void skippedShaftsShowNothing() {
		MiningStats few = new MiningStats(60_000, 0, Efficiency.MIN_BLOCKS - 1, 0, "x", 8.0, null, 0, 0);
		assertNotNull(Efficiency.of(few));
		assertNull(Efficiency.shown(few));
		assertNull(MiningText.of(few));
	}

	@Test
	void setupCountsAtTheBreakTimeOutsideBoost() {
		// 1,735.4 nonstop + 36 s x 20 / 13
		assertEquals(1168 / (Efficiency.nonstop(JASPER) + 36.0 * 20 / 13), Efficiency.withSetup(JASPER), 1e-6);
	}

	@Test
	void noWithSetupForSkippedOrAllBoostShafts() {
		assertNull(Efficiency.withSetup(new MiningStats(60_000, 30_000, 20, 0, "x", 10.0, null, 0, 0)));
		assertNull(Efficiency.withSetup(null));
		assertNull(Efficiency.withSetup(new MiningStats(20_000, 30_000, 100, 0, "x", null, 4.0, 20_000, 0)));
	}
}
