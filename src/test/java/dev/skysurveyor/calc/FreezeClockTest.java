package dev.skysurveyor.calc;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.detection.Shaft.ColdStep;
import dev.skysurveyor.journal.EntryBuilder;
import dev.skysurveyor.journal.JournalEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

// made up from the formula, read 1/s like the scoreboard
class FreezeClockTest {
	// cold res 131: 763.3 s, 1008 w/ a campfire (ColdModelTest)
	private static final double PER_COLD = ColdModel.perCold(131);

	// cold 0 at 1 s, then 1st whole sec after each builds
	private static List<ColdStep> shaft(double perCold, int upTo) {
		List<ColdStep> steps = new ArrayList<>();
		steps.add(new ColdStep(1_000, 0));
		for (int cold = 1; cold <= upTo; cold++) steps.add(new ColdStep(seen(cold, perCold), -cold));
		return steps;
	}

	private static long seen(double built, double perCold) {
		return (long) Math.ceil(ColdModel.secondsUntil(built, perCold)) * 1000;
	}

	@Test
	void fitsThePaceOfThisShaft() {
		// error left = the 1 s scoreboard lag
		assertEquals(763_333, FreezeClock.freezeAt(shaft(PER_COLD, 10), null), 8_000);
		assertEquals(763_333, FreezeClock.freezeAt(shaft(PER_COLD, 50), null), 3_000);
		assertEquals(763_333, FreezeClock.freezeAt(shaft(PER_COLD, 99), null), 1_500);
	}

	@Test
	void needsAFewChangesWithoutAJournal() {
		assertNull(FreezeClock.freezeAt(List.of(), null));
		assertNull(FreezeClock.freezeAt(shaft(PER_COLD, 4), null));
		assertNotNull(FreezeClock.freezeAt(shaft(PER_COLD, 6), null));
	}

	@Test
	void startsFromTheJournal() {
		// on entering, before any cold
		assertEquals(763_333, FreezeClock.freezeAt(List.of(), PER_COLD), 1);
		// 1 change seen a second late = 4 s at the end
		assertEquals(763_333, FreezeClock.freezeAt(shaft(PER_COLD, 2), PER_COLD), 5_000);
	}

	@Test
	void thisShaftWinsOverTheJournal() {
		// journal says 131, +10 buff here: 785.6 s
		double buffed = ColdModel.perCold(141);
		assertEquals(785_556, FreezeClock.freezeAt(shaft(buffed, 60), PER_COLD), 4_000);
	}

	@Test
	void mobHitsDontCountAsPace() {
		// rec 5, 2nd jasper: 2 -> 4 -> 5 in 4 s
		List<ColdStep> steps = new ArrayList<>(shaft(PER_COLD, 2));
		long hit = steps.getLast().millis();
		steps.add(new ColdStep(hit + 1_000, -4));
		steps.add(new ColdStep(hit + 4_000, -5));
		double built = ColdModel.exposure((hit + 4_000) / 1000.0) / PER_COLD;
		for (int cold = 6; cold <= 40; cold++) steps.add(new ColdStep(seen(built + cold - 5, PER_COLD), -cold));
		// 3 cold early = about 35 s sooner
		long expected = Math.round(ColdModel.secondsUntil(built + 95, PER_COLD) * 1000);
		assertEquals(expected, FreezeClock.freezeAt(steps, null), 3_000);
	}

	@Test
	void aCampfireMovesTheFreeze() {
		List<ColdStep> steps = new ArrayList<>(shaft(PER_COLD, 80));
		steps.add(new ColdStep(steps.getLast().millis() + 2_000, -30));
		assertEquals(1_008_000, FreezeClock.freezeAt(steps, null), 4_000);
	}

	@Test
	void noGuessWhenTheModStartedMidShaft() {
		List<ColdStep> steps = List.of(new ColdStep(1_000, -40), new ColdStep(6_000, -41), new ColdStep(11_000, -42),
				new ColdStep(16_000, -43), new ColdStep(21_000, -44), new ColdStep(26_000, -45), new ColdStep(31_000, -46));
		assertNull(FreezeClock.freezeAt(steps, PER_COLD));
	}

	@Test
	void priorIsTheMedianOfTheJournalsFreezes() {
		List<JournalEntry> entries = List.of(
				frozen(763_333).build(),
				// left by the exit, says nothing
				EntryBuilder.entry().durationMillis(500_000).exit("BYE").build(),
				// campfire at 80 cold: 150 to build
				frozen(1_008_000).campfiresAt(List.of(700_000L)).campfireColds(List.of(-80)).build(),
				// campfire at 30 cold wasted 20 of it, left out
				frozen(900_000).campfiresAt(List.of(300_000L)).campfireColds(List.of(-30)).build(),
				frozen(763_333).build());
		assertEquals(PER_COLD, FreezeClock.prior(entries), 0.001);
		assertNull(FreezeClock.prior(List.of()));
	}

	private static EntryBuilder frozen(long millis) {
		return EntryBuilder.entry().durationMillis(millis).exit("DEATH");
	}
}
