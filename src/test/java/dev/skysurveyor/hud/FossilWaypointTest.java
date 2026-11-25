package dev.skysurveyor.hud;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.data.FossilSpots;
import dev.skysurveyor.data.FossilSpots.Spot;
import dev.skysurveyor.detection.Shaft;
import dev.skysurveyor.detection.ShaftTracker;
import dev.skysurveyor.detection.TextPatterns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FossilWaypointTest {
	private static final Spot JADE = FossilSpots.defaults().get("JADE_1");

	private static Shaft shaft(String scoreboard) {
		ShaftTracker tracker = new ShaftTracker(TextPatterns.defaults(), event -> {
		});
		tracker.onWorldChange(0);
		tracker.onScoreboard(0, List.of(scoreboard));
		return tracker.current();
	}

	@Test
	void onlyInAShaftWithAKnownFossil() {
		assertEquals(JADE, FossilWaypoint.spot(shaft("§711/21/26 §8m113BD JADE_1"), FossilSpots.defaults()));
		assertNull(FossilWaypoint.spot(shaft("§711/21/26 §8m113BD AMET_1"), FossilSpots.defaults()));
		assertNull(FossilWaypoint.spot(null, FossilSpots.defaults()));
	}

	// tung fossil 13 blocks: 11 broken (recording 2026-11-21 23:03) hides it, 10 doesnt
	@Test
	void goneOnceMostlyMined() {
		Spot tung = FossilSpots.defaults().get("TUNG_1");
		assertFalse(FossilWaypoint.mined(10, tung));
		assertTrue(FossilWaypoint.mined(11, tung));
	}

	// fake box: 1st 13 blocks of the x, y, z walk are fossil, gone = removed from the set
	@Test
	void minedCountsBoxBlocksSeenThenGone() {
		Spot tung = FossilSpots.defaults().get("TUNG_1");
		Shaft shaft = shaft("§711/21/26 §8m113BD TUNG_1");
		int dy = tung.maxY() - tung.minY() + 1;
		int dz = tung.maxZ() - tung.minZ() + 1;
		Set<Integer> fossil = new HashSet<>();
		for (int i = 0; i < 13; i++) fossil.add(i);
		FossilWaypoint.Mined.Blocks blocks = (x, y, z) -> fossil.contains(((x - tung.minX()) * dy + y - tung.minY()) * dz
				+ z - tung.minZ());
		FossilWaypoint.Mined mined = new FossilWaypoint.Mined();
		// unloaded chunk reads as air, never seen = not mined
		mined.update(shaft, tung, (x, y, z) -> false);
		assertFalse(mined.mined(shaft));
		mined.update(shaft, tung, blocks);
		for (int i = 0; i < 11; i++) fossil.remove(i);
		mined.update(shaft, tung, blocks);
		assertTrue(mined.mined(shaft));
		assertFalse(mined.mined(shaft("§711/21/26 §8m113BD TUNG_1")), "a new shaft starts over");
	}

	@Test
	void distanceToTheNearestBlock() {
		// box x -115..-106 (blocks, so up to -105.0), y 18..23, z -187..-175
		assertEquals(0, FossilWaypoint.distance(JADE, -110, 20, -180), 1e-9);
		assertEquals(5, FossilWaypoint.distance(JADE, -100, 20, -180), 1e-9);
		assertEquals(5, FossilWaypoint.distance(JADE, -120, 20, -180), 1e-9);
		assertEquals(Math.sqrt(9 + 16), FossilWaypoint.distance(JADE, -110, 27, -191), 1e-9);
		assertEquals("Fossil 23m", FossilWaypoint.label(22.6));
	}

	@Test
	void textGrowsFarOffAndNoFillUpClose() {
		assertEquals(0.4f, FossilWaypoint.textScale(3), 1e-6);
		assertEquals(1.6f, FossilWaypoint.textScale(20), 1e-6);
		assertFalse(FossilWaypoint.filled(0));
		assertTrue(FossilWaypoint.filled(10));
	}
}
