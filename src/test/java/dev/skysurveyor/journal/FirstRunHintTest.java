package dev.skysurveyor.journal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FirstRunHintTest {
	@Test
	void showsOnceInTheMinesWithNoShafts() {
		FirstRunHint hint = new FirstRunHint();
		assertTrue(hint.check(true, true, 0));
		assertFalse(hint.check(true, true, 0));
	}

	@Test
	void notOutsideTheMines() {
		FirstRunHint hint = new FirstRunHint();
		assertFalse(hint.check(false, true, 0));
		assertTrue(hint.check(true, true, 0));
	}

	@Test
	void waitsForTheJournalToLoad() {
		FirstRunHint hint = new FirstRunHint();
		assertFalse(hint.check(true, false, 0));
		assertTrue(hint.check(true, true, 0));
	}

	@Test
	void neverWithShafts() {
		FirstRunHint hint = new FirstRunHint();
		assertFalse(hint.check(true, true, 1));
		assertFalse(hint.check(true, true, 250));
	}

	@Test
	void notAfterTheFirstShaftOfThisRun() {
		FirstRunHint hint = new FirstRunHint();
		assertFalse(hint.check(false, true, 0));
		assertFalse(hint.check(true, true, 1));
	}

	@Test
	void notWhenLoadingFailed() {
		FirstRunHint hint = new FirstRunHint();
		for (int i = 0; i < 5; i++) assertFalse(hint.check(true, false, 0));
	}
}
