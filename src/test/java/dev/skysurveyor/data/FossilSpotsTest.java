package dev.skysurveyor.data;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FossilSpotsTest {
	@Test
	void everyLayoutWithAFossil() {
		FossilSpots spots = FossilSpots.defaults();
		assertEquals(List.of("AQUA_2", "CITR_2", "JADE_1", "JASP_1", "PERI_1", "PERI_2", "TOPA_1", "TUNG_1"), spots.codes());
		// every jade essence block in recordings 2026-11-17 + 11-21 sits in here
		assertEquals(new FossilSpots.Spot("JADE_1", -115, 18, -187, -106, 23, -175, 48), spots.get("JADE_1"));
		assertEquals(-110, spots.get("JADE_1").centerX());
		// wiki vs save disagree, left out
		assertFalse(spots.has("AMET_1"));
		assertFalse(spots.has("OPAL_C"));
		assertNull(spots.get(null));
	}

	@Test
	void skipsCommentsAndBadLines() {
		FossilSpots spots = new FossilSpots(List.of("# JADE_1 = 1 2 3 4 5 6, 7 blocks", "TOPA_1 = 1 2 3, 4 blocks",
				"TUNG_1 = -179 11 -172 -175 15 -169, 13 blocks  # save, wiki"));
		assertEquals(List.of("TUNG_1"), spots.codes());
		assertTrue(spots.has("TUNG_1"));
	}
}
