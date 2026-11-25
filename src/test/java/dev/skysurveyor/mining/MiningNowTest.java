package dev.skysurveyor.mining;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.detection.Shaft;
import dev.skysurveyor.detection.ShaftEvent.PickaxeAbility;
import dev.skysurveyor.detection.ShaftEvent.ShaftEntered;
import dev.skysurveyor.detection.ShaftEvent.ShaftLeft;
import dev.skysurveyor.detection.TestShafts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// the hud line: this shaft or this tunnel loop (Docs/efficiency-design.md section 5)
class MiningNowTest {
	private static final String GEM = "lime_stained_glass";
	private boolean mines = true;
	private final MiningNow now = new MiningNow(() -> mines);

	// a block every `every` ticks that takes `ticks`, from `at`. returns the time after the last one
	private long mine(long at, int blocks, int ticks, int every) {
		long t = at;
		for (int i = 0; i < blocks; i++) {
			t += every * 50L;
			// 1st hit when u start on it, like AttackBlockCallback
			now.onHit(t - ticks * 50L);
			now.onBreak(t, GEM, ticks, true, 0);
		}
		return t;
	}

	@Test
	void nothingYetIsDash() {
		assertNull(now.percent(10_000));
	}

	@Test
	void tunnelLoopCountsFromItsFirstBlock() {
		// 8 tick jade, one every 10 ticks = 80%
		long end = mine(60_000, 50, 8, 10);
		assertEquals(80L, now.percent(end));
	}

	@Test
	void underTenBlocksIsDash() {
		long end = mine(0, MiningNow.HUD_MIN_BLOCKS - 1, 8, 8);
		assertNull(now.percent(end));
		end = mine(end, 1, 8, 8);
		assertEquals(100L, now.percent(end));
	}

	@Test
	void hubBlocksDontCount() {
		mines = false;
		long end = mine(0, 50, 8, 8);
		assertNull(now.percent(end));
	}

	@Test
	void twoMinutesWithoutMiningStartsANewLoop() {
		// 50 blocks of 8 ticks from 0.4 s to 40 s: 50 of 99
		long end = mine(0, 50, 8, 16);
		assertEquals(51L, now.percent(end));
		// standing still counts as lost time till the gap, then its a new loop
		assertEquals(49L, now.percent(end + 1_000));
		assertNull(now.percent(end + MiningNow.LOOP_GAP_MILLIS + 1));
		// the new loop doesnt carry the old one
		long next = mine(end + 3 * 60_000L, 50, 8, 8);
		assertEquals(100L, now.percent(next));
	}

	@Test
	void enteringAShaftSwitchesToTheShaft() {
		long end = mine(0, 50, 8, 16);
		Shaft shaft = TestShafts.entered(end + 5_000);
		now.onEvent(new ShaftEntered(shaft), end + 6_000);
		assertTrue(now.inShaft());
		assertNull(now.percent(end + 6_000));
		// 20 s looting, then 13 tick jasper nonstop: setup isnt counted
		long mined = mine(end + 25_000, 100, 13, 13);
		assertEquals(100L, now.percent(mined));
		assertEquals(100, now.shaftStats(shaft.enteredAt(), mined).blocks());
	}

	@Test
	void leavingStartsAFreshLoop() {
		Shaft shaft = TestShafts.entered(0);
		now.onEvent(new ShaftEntered(shaft), 0);
		long end = mine(1_000, 100, 13, 26);
		now.onEvent(new ShaftLeft(shaft), end);
		now.onWorldChange();
		assertNull(now.percent(end + 1_000));
		long tunnels = mine(end + 10_000, 30, 8, 8);
		assertEquals(100L, now.percent(tunnels));
	}

	@Test
	void trackSamplesTheLastMinuteEveryTenSeconds() {
		// 1 min at 100%, then 1 min at 50%
		long end = mine(0, 150, 8, 8);
		assertEquals(1, now.track(end).size());
		assertEquals(1.0, now.track(end).getFirst(), 0.02);
		assertEquals(1, now.track(end + 5_000).size(), "not before 10 s");
		end = mine(end, 75, 8, 16);
		List<Double> points = now.track(end);
		assertEquals(2, points.size());
		assertEquals(0.5, points.getLast(), 0.03, "the last minute only");
		// a new loop starts the line over
		long next = mine(end + MiningNow.LOOP_GAP_MILLIS + 60_000, 30, 8, 8);
		assertEquals(1, now.track(next).size());
	}

	// recording 9 (PERI_2, 2026-11-21 20:03): looting first, ~50 quartz blocks + slabs broken in 1 to 3 ticks
	@Test
	void instaBrokenBlocksWhileLootingShowNothing() {
		Shaft shaft = TestShafts.entered(0);
		now.onEvent(new ShaftEntered(shaft), 0);
		long t = 20_000;
		for (int i = 0; i < 50; i++) {
			t += 200;
			now.onHit(t - 100);
			now.onBreak(t, i % 2 == 0 ? "quartz_block" : "quartz_slab", 1 + i % 3, false, 0);
		}
		assertNull(now.percent(t), "no real mining yet");
		// hard stone at the 4 tick minimum, 7 going w/ it (recording 9, AQUA_1)
		for (int i = 0; i < 20; i++) {
			t += 300;
			now.onHit(t - 200);
			now.onBreak(t, "stone", 4, true, 0);
		}
		assertNull(now.percent(t), "4 ticks is the floor, still insta-mined");
		long end = mine(t + 40_000, 100, 13, 13);
		assertEquals(100L, now.percent(end), "counted from the first gem block, quartz left out");
	}

	@Test
	void boostIsSeenInTheTunnelsToo() {
		now.onEvent(new PickaxeAbility("Mining Speed Boost"), 0);
		assertTrue(now.boosting());
	}
}
