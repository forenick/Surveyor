package dev.skysurveyor.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// dev client, vanilla font: "Box" 45, "Same as box" 89
class EditorFitTest {
	@Test
	void columnNeverShrinks() {
		assertEquals(62, EditorFit.widest(62, new int[] {24, 59, 30}));
		assertEquals(38, EditorFit.widest(38, new int[] {}));
	}

	@Test
	void columnGrowsForWideFont() {
		assertEquals(77, EditorFit.widest(62, new int[] {30, 77, 40}));
	}

	@Test
	void sameAsBoxOnlyInWidePanels() {
		// panels 204 (scale 3, 4), 240, 280 (scale 2 at 1080p), inner = panel - 10
		assertFalse(EditorFit.sameAsBoxFits(194, 194, 89, 45));
		assertFalse(EditorFit.sameAsBoxFits(230, 194, 89, 45));
		assertTrue(EditorFit.sameAsBoxFits(270, 194, 89, 45));
	}

	@Test
	void sameAsBoxFallsBackForVeryWideFont() {
		assertFalse(EditorFit.sameAsBoxFits(270, 194, 140, 50));
	}

	@Test
	void scrollStaysInRange() {
		assertEquals(0, EditorFit.clampScroll(-12, 260, 230));
		assertEquals(30, EditorFit.clampScroll(100, 260, 230));
		assertEquals(12, EditorFit.clampScroll(12, 260, 230));
		// fits, no scroll
		assertEquals(0, EditorFit.clampScroll(12, 200, 230));
	}
}
