package dev.skysurveyor.mining;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.detection.ShaftEvent.AbilityExpired;
import dev.skysurveyor.detection.ShaftEvent.PickaxeAbility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// hand made ticks (efficiency-design.md slice 1)
class MiningMeterTest {
	private static final String GEM = "magenta_stained_glass";
	private static final String STONE = "stone";

	// block takes `ticks`, one every `every`. returns time after the last
	private static long mine(MiningMeter meter, long at, int blocks, int ticks, int every) {
		long now = at;
		for (int i = 0; i < blocks; i++) {
			now += every * 50L;
			meter.onHit(now);
			meter.onBreak(now, GEM, ticks, true, 0);
		}
		return now;
	}

	@Test
	void nothingBrokenIsNull() {
		assertNull(new MiningMeter().stats(0, 60_000));
	}

	@Test
	void nonstopMiningIsAHundredPercent() {
		MiningMeter meter = new MiningMeter();
		long end = mine(meter, 0, 200, 13, 13);
		MiningStats stats = meter.stats(0, end);
		assertEquals(200, stats.blocks());
		assertEquals(13.0, stats.ticks());
		assertNull(stats.boostTicks());
		assertEquals(0, stats.setupMillis());
		assertEquals(1.0, Efficiency.of(stats), 1e-9);
	}

	@Test
	void blocksInBoostAreCountedApart() {
		MiningMeter meter = new MiningMeter();
		long end = mine(meter, 0, 100, 13, 13);
		meter.onEvent(new PickaxeAbility(MiningMeter.BOOST), end);
		end = mine(meter, end, 60, 4, 6);
		meter.onEvent(new AbilityExpired(MiningMeter.BOOST), end);
		end = mine(meter, end, 40, 13, 13);
		MiningStats stats = meter.stats(0, end);
		assertEquals(200, stats.blocks());
		assertEquals(60, stats.boostBlocks());
		assertEquals(60 * 6 * 50L, stats.boostMillis());
	}

	@Test
	void slowerThanTheBreakTimeIsLess() {
		MiningMeter meter = new MiningMeter();
		// 13 tick blocks, one every 20 ticks
		long end = mine(meter, 0, 200, 13, 20);
		assertEquals(13.0 / 20, Efficiency.of(meter.stats(0, end)), 0.01);
	}

	@Test
	void setupBeforeTheFirstGemIsNotCounted() {
		MiningMeter meter = new MiningMeter();
		// stone on the way in, then 40 s of looting
		meter.onHit(2_000);
		meter.onBreak(2_000, STONE, 1, true, 0);
		// the 1st hit on the gem ends the wait
		meter.onHit(40_000);
		long end = mine(meter, 40_000, 200, 13, 13);
		MiningStats stats = meter.stats(0, end);
		assertEquals(GEM, stats.block());
		// 1st gem went at 40.65 s after 13 ticks, so it was first hit at 40 s
		assertEquals(40_000, stats.setupMillis());
		assertEquals(200, stats.blocks());
		assertEquals(1.0, Efficiency.of(stats), 1e-9);
		// the 38 s between the stone and the gems is setup, not a pause
		assertEquals(0, stats.pausedMillis());
	}

	@Test
	void boostTimeCountsAtItsOwnBreakTime() {
		MiningMeter meter = new MiningMeter();
		long now = mine(meter, 0, 100, 13, 13);
		meter.onEvent(new PickaxeAbility("Mining Speed Boost"), now);
		assertTrue(meter.boosting());
		now = mine(meter, now, 100, 4, 4);
		meter.onEvent(new AbilityExpired("Mining Speed Boost"), now);
		assertFalse(meter.boosting());
		now = mine(meter, now, 100, 13, 13);
		MiningStats stats = meter.stats(0, now);
		assertEquals(13.0, stats.ticks());
		assertEquals(4.0, stats.boostTicks());
		assertEquals(100 * 4 * 50, stats.boostMillis());
		assertEquals(1.0, Efficiency.of(stats), 1e-9);
	}

	@Test
	void wastedBoostCostsMoreThanWastedNormalTime() {
		MiningMeter idleInBoost = new MiningMeter();
		long now = mine(idleInBoost, 0, 100, 13, 13);
		idleInBoost.onEvent(new PickaxeAbility("Mining Speed Boost"), now);
		// 1 block in boost so it has a break time, then 20 s of nothing
		now = mine(idleInBoost, now, 20, 4, 4);
		idleInBoost.onEvent(new AbilityExpired("Mining Speed Boost"), now + 20_000);
		double boostWasted = Efficiency.of(idleInBoost.stats(0, now + 20_000));

		MiningMeter idleOutside = new MiningMeter();
		now = mine(idleOutside, 0, 100, 13, 13);
		idleOutside.onEvent(new PickaxeAbility("Mining Speed Boost"), now);
		now = mine(idleOutside, now, 20, 4, 4);
		idleOutside.onEvent(new AbilityExpired("Mining Speed Boost"), now);
		double normalWasted = Efficiency.of(idleOutside.stats(0, now + 20_000));
		assertTrue(boostWasted < normalWasted, boostWasted + " < " + normalWasted);
	}

	@Test
	void anOtherAbilityIsNotABoost() {
		MiningMeter meter = new MiningMeter();
		meter.onEvent(new PickaxeAbility("Pickobulus"), 0);
		assertFalse(meter.boosting());
	}

	@Test
	void breakTimeIsTheMostCommonCleanOne() {
		MiningMeter meter = new MiningMeter();
		long now = 0;
		// 12 at 13 ticks, 5 at 14, 3 that came back to the block (not clean, short)
		int[] ticks = {13, 13, 14, 13, 13, 14, 13, 13, 14, 13, 13, 14, 13, 13, 14, 13, 13};
		for (int t : ticks) {
			now += 13 * 50L;
			meter.onBreak(now, GEM, t, true, 0);
		}
		for (int i = 0; i < 3; i++) {
			now += 13 * 50L;
			meter.onBreak(now, GEM, 5, false, 0);
		}
		assertEquals(13.0, meter.stats(0, now).ticks());
	}

	@Test
	void underTenBreaksUseTheirAverage() {
		MiningMeter meter = new MiningMeter();
		meter.onBreak(1_000, GEM, 12, true, 0);
		meter.onBreak(2_000, GEM, 14, true, 0);
		assertEquals(13.0, meter.stats(0, 2_000).ticks());
	}

	@Test
	void spreadIsCountedApart() {
		MiningMeter meter = new MiningMeter();
		long now = 0;
		for (int i = 0; i < 150; i++) {
			now += 13 * 50L;
			meter.onBreak(now, GEM, 13, true, i % 10 == 0 ? 1 : 0);
		}
		MiningStats stats = meter.stats(0, now);
		assertEquals(150, stats.blocks());
		assertEquals(15, stats.spread());
		// spread doesnt lift the number
		assertEquals(1.0, Efficiency.of(stats), 1e-9);
	}

	// rec 7 (PERI_2, 11-21): gem = glass or pane, stone went w/ stone 9 times
	@Test
	void spreadIsTheSameGemOnly() {
		assertEquals(1, MiningMeter.spread("green_stained_glass", List.of("green_stained_glass_pane")));
		assertEquals(2, MiningMeter.spread("green_stained_glass_pane", List.of("green_stained_glass", "green_stained_glass_pane", "stone")));
		assertEquals(0, MiningMeter.spread("green_stained_glass", List.of("stone", "ice", "snow")));
		// another gems glass isnt this gem, snow_block isnt snow
		assertEquals(0, MiningMeter.spread("green_stained_glass", List.of("lime_stained_glass", "lime_stained_glass_pane")));
		assertEquals(0, MiningMeter.spread("snow", List.of("snow_block")));
	}

	@Test
	void stoneSpreadIsNotTheShaftsSpread() {
		MiningMeter meter = new MiningMeter();
		long now = 0;
		for (int i = 0; i < 120; i++) {
			now += 11 * 50L;
			meter.onBreak(now, i % 2 == 0 ? "green_stained_glass" : "green_stained_glass_pane", 11, true, i % 6 == 0 ? 1 : 0);
		}
		// 6 stone went w/ a stone block, as in the recording
		meter.onBreak(now + 200, STONE, 4, true, MiningMeter.spread(STONE, List.of(STONE, STONE, STONE, STONE, STONE, STONE)));
		MiningStats stats = meter.stats(0, now + 200);
		assertEquals(120, stats.blocks(), "the stone isnt the shafts block");
		assertEquals(20, stats.spread());
	}

	@Test
	void theFirstPaneStartsTheClockToo() {
		MiningMeter meter = new MiningMeter();
		// pane first, then mostly blocks
		meter.onBreak(30_000, "green_stained_glass_pane", 11, true, 0);
		long now = 30_000;
		for (int i = 0; i < 20; i++) {
			now += 11 * 50L;
			meter.onBreak(now, "green_stained_glass", 11, true, 0);
		}
		MiningStats stats = meter.stats(0, now);
		assertEquals("green_stained_glass", stats.block());
		assertEquals(30_000 - 11 * 50, stats.setupMillis());
		assertEquals(21, stats.blocks());
	}

	// rec 7: 30 stone + snow pulled avg to 10.8, gem took 11
	@Test
	void theGemsOwnBreakTimeIsSavedApart() {
		MiningMeter meter = new MiningMeter();
		long now = 0;
		for (int i = 0; i < 40; i++) {
			now += 11 * 50L;
			meter.onBreak(now, "green_stained_glass", 11, true, 0);
			if (i % 4 == 0) meter.onBreak(now + 200, STONE, 4, true, 0);
		}
		MiningStats stats = meter.stats(0, now + 200);
		assertEquals(11.0, stats.blockTicks());
		assertEquals(11.0, stats.shownTicks());
		assertEquals(11.0, stats.ticks(), "stone isnt counted since alpha.17");
	}

	// rec 8 (PERI_C, 11-21): 821 insta-mined hard stone, 554 peridot blocks + 500 panes
	@Test
	void instaMinedStoneIsNotTheMainBlock() {
		MiningMeter meter = new MiningMeter();
		long now = 0;
		for (int i = 0; i < 100; i++) {
			now += 11 * 50L;
			meter.onBreak(now, i % 2 == 0 ? "green_stained_glass" : "green_stained_glass_pane", 11, true, 0);
			// 2 stone a gem block, 1 tick each, 6 more going w/ each
			meter.onBreak(now + 100, STONE, 1, false, 6);
			meter.onBreak(now + 200, STONE, 1, false, 6);
		}
		MiningStats stats = meter.stats(0, now + 200);
		assertEquals("green_stained_glass", stats.block());
		assertEquals(100, stats.blocks());
		assertEquals(0, stats.spread());
		assertEquals(11.0, stats.ticks());
		assertEquals(1.0, Efficiency.of(stats), 0.01);
	}

	@Test
	void pausesOverFiveSecondsAddUp() {
		MiningMeter meter = new MiningMeter();
		long now = mine(meter, 0, 100, 13, 13);
		// 4 s is no pause, 12 s is
		now = mine(meter, now + 4_000, 50, 13, 13);
		now = mine(meter, now + 12_000, 50, 13, 13);
		MiningStats stats = meter.stats(0, now);
		assertEquals(12_000 + 13 * 50, stats.pausedMillis());
		// still standing there when asked
		assertEquals(12_000 + 13 * 50 + 30_000, meter.stats(0, now + 30_000).pausedMillis());
	}

	@Test
	void worldChangeStartsOver() {
		MiningMeter meter = new MiningMeter();
		long now = mine(meter, 0, 100, 13, 13);
		meter.onEvent(new PickaxeAbility("Mining Speed Boost"), now);
		meter.onWorldChange();
		assertFalse(meter.boosting());
		assertNull(meter.stats(now, now + 1_000));
	}

	@Test
	void modeTiesGoToTheShorterTime() {
		assertEquals(12, MiningMeter.mode(List.of(13, 12, 13, 12)));
		assertEquals(4, MiningMeter.mode(List.of(3, 4, 4, 5, 4)));
	}
}
