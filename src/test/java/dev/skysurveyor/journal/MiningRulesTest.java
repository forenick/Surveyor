package dev.skysurveyor.journal;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.detection.ItemCount;
import dev.skysurveyor.detection.ShaftEvent;
import dev.skysurveyor.detection.ShaftEvent.PickaxeAbility;
import dev.skysurveyor.detection.ShaftEvent.SacksChanged;
import dev.skysurveyor.detection.ShaftEvent.ShaftEntered;
import dev.skysurveyor.detection.ShaftEvent.ShaftLeft;
import dev.skysurveyor.detection.ShaftTracker;
import dev.skysurveyor.detection.TextPatterns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// "both" = tab says dwarven mines in a shaft
class MiningRulesTest {
	private static final long MINUTE = 60_000;
	// rec 5
	private static final List<String> TUNG_1 = List.of("§8§711/15/26 §8m10§8§u§8§88A TUNG_1", "§b §7 §bGlacite M§b§p§b§bineshafts");
	private static final String STASH = "§r§f             §7You have §37,726,738 §7materials stashed away!";

	private boolean inMines = true;
	private long now;
	private final List<JournalEntry> entries = new ArrayList<>();
	private final List<MiningEntry> mining = new ArrayList<>();
	private final List<StashEntry> stashed = new ArrayList<>();
	private final Journal journal = new Journal(TextPatterns.defaults(), () -> inMines, entries::add);
	private final OutsideMining outside = new OutsideMining(TextPatterns.defaults(), () -> inMines, journal::activeSession, mining::add);
	private final Stash stash = new Stash(TextPatterns.defaults(), () -> inMines, journal::activeSession, stashed::add);
	private final PriceCheck priceCheck = new PriceCheck();
	private final List<Boolean> checks = new ArrayList<>();
	private final ShaftTracker tracker = new ShaftTracker(TextPatterns.defaults(), this::event);

	@Test
	void journalHitsOutsideMinesAndShaftsDoNothing() {
		inMines = false;
		hit(0);
		assertNull(journal.activeSession(0));
		assertFalse(journal.searchRunning());
	}

	@Test
	void journalHitsInAShaftKeepTheSessionButDontTime() {
		enterShaft(0);
		inMines = false;
		hit(14 * MINUTE);
		assertNotNull(journal.activeSession(14 * MINUTE + Journal.SESSION_GAP_MILLIS), "hit in the shaft kept it");
		assertFalse(journal.searchRunning());
	}

	@Test
	void journalBothTimesTheSpawn() {
		enterShaft(0);
		hit(5_000);
		assertTrue(journal.searchRunning(), "spawn timer asks the tab list only");
	}

	@Test
	void journalPickaxeAbilityIsMining() {
		event(new PickaxeAbility("Pickobulus"), 0);
		assertTrue(journal.searchRunning());
		inMines = false;
		event(new PickaxeAbility("Pickobulus"), Journal.SESSION_GAP_MILLIS + MINUTE);
		assertNull(journal.activeSession(Journal.SESSION_GAP_MILLIS + MINUTE), "outside the mines it isnt");
	}

	@Test
	void stashCountsMiningInAShaft() {
		hit(0);
		stash.onChat(1_000, STASH);
		enterShaft(2_000);
		inMines = false;
		hit(10_000);
		stash.onChat(60_000, STASH.replace("7,726,738", "7,726,838"));
		assertEquals(List.of(100L), stashed.stream().map(StashEntry::grew).toList());
	}

	@Test
	void stashSkipsHitsElsewhere() {
		hit(0);
		stash.onChat(1_000, STASH);
		inMines = false;
		hit(10_000);
		stash.onChat(60_000, STASH.replace("7,726,738", "7,726,838"));
		assertTrue(stashed.isEmpty());
	}

	@Test
	void outsideMiningSkipsBoth() {
		hit(0);
		enterShaft(1_000);
		hit(5_000);
		sacks(20_000);
		assertTrue(mining.isEmpty());
	}

	@Test
	void outsideMiningForgetsMiningFromBeforeTheShaft() {
		hit(0);
		enterShaft(1_000);
		leaveShaft(5_000);
		sacks(10_000);
		assertTrue(mining.isEmpty(), "mined 10 s ago, but before the shaft");
		hit(11_000);
		sacks(12_000);
		assertEquals(1, mining.size());
	}

	@Test
	void priceCheckOnlyInAShaftWhateverTheTabListSays() {
		enterShaft(0);
		inMines = false;
		hit(5_000);
		sacks(10_000);
		inMines = true;
		hit(15_000);
		sacks(20_000);
		leaveShaft(25_000);
		hit(30_000);
		sacks(35_000);
		assertEquals(List.of(true, true, false), checks);
	}

	@Test
	void clockScopes() {
		// {in shaft, in mines} -> counts for SHAFT, MINES_OUTSIDE_SHAFT, MINES_OR_SHAFT
		boolean[][] places = {{false, false}, {false, true}, {true, false}, {true, true}};
		boolean[][] counts = {{false, false, false}, {false, true, true}, {true, false, true}, {true, false, true}};
		for (int p = 0; p < places.length; p++) {
			for (MiningClock.Scope scope : MiningClock.Scope.values()) {
				boolean mines = places[p][1];
				MiningClock clock = new MiningClock(scope, () -> mines);
				if (places[p][0]) clock.onEvent(new ShaftEntered(null), 0);
				boolean expected = counts[p][scope.ordinal()];
				assertEquals(expected, clock.onBlockHit(10), scope + " " + p);
				assertEquals(expected, clock.onEvent(new PickaxeAbility("Pickobulus"), 20), scope + " " + p);
				assertEquals(expected ? 20 : -1, clock.lastAt());
				assertFalse(clock.onEvent(new ShaftLeft(null), 30), "leaving isnt mining");
				assertFalse(clock.inShaft());
				clock.reset();
				assertFalse(clock.minedSince(0));
			}
		}
	}

	private void enterShaft(long at) {
		now = at;
		tracker.onWorldChange(at);
		tracker.onScoreboard(at, TUNG_1);
	}

	private void leaveShaft(long at) {
		now = at;
		tracker.onWorldChange(at);
	}

	private void hit(long at) {
		now = at;
		journal.onBlockHit(at);
		outside.onBlockHit(at);
		stash.onBlockHit(at);
		priceCheck.onBlockHit(at);
	}

	private void sacks(long at) {
		event(new SacksChanged(30, List.of(new ItemCount("Rough Jade Gemstone", 80)), List.of(), 0, 0), at);
	}

	private void event(ShaftEvent event) {
		event(event, now);
	}

	private void event(ShaftEvent event, long at) {
		now = at;
		journal.onEvent(event, at);
		outside.onEvent(event, at);
		stash.onEvent(event, at);
		boolean check = priceCheck.onEvent(event, at);
		if (event instanceof SacksChanged) checks.add(check);
		if (event instanceof ShaftEntered || event instanceof ShaftLeft) assertFalse(check);
	}
}
