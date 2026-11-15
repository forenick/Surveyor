package dev.skysurveyor.detection;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.detection.ShaftEvent.CorpseLooted;
import dev.skysurveyor.detection.ShaftEvent.KeySaved;
import dev.skysurveyor.journal.JournalEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// resourceful text from research/02 (other mods), never recorded, so no colour codes
class KeySavedTest {
	private static final String LUCKY = "LUCKY! Your Resourceful perk saved your key from being consumed!";

	private static CorpseLooted loot(String type) {
		return new CorpseLooted(new CorpseLoot(type, false, List.of(new ItemCount("Bejeweled Handle", 1)), 1_000));
	}

	@Test
	void messageParses() {
		assertEquals(new KeySaved(), new ChatParser(TextPatterns.defaults()).parse(LUCKY, List.of()));
	}

	@Test
	void savedRightAfterTheLootBlock() {
		Shaft shaft = new Shaft(0);
		shaft.apply(loot("Lapis"), 1_000);
		shaft.apply(loot("Umber"), 5_000);
		shaft.apply(new KeySaved(), 5_200);
		assertEquals(List.of(1), shaft.keysSaved());
	}

	@Test
	void savedBeforeTheLootBlockGoesToTheNextKeyCorpse() {
		Shaft shaft = new Shaft(0);
		shaft.apply(loot("Umber"), 1_000);
		shaft.apply(new KeySaved(), 30_000);
		shaft.apply(loot("Lapis"), 30_100);
		shaft.apply(loot("Tungsten"), 30_200);
		assertEquals(List.of(2), shaft.keysSaved(), "not the umber from long ago, not the lapis");
	}

	@Test
	void onlyOncePerCorpse() {
		Shaft shaft = new Shaft(0);
		shaft.apply(loot("Umber"), 1_000);
		shaft.apply(new KeySaved(), 1_100);
		shaft.apply(new KeySaved(), 1_200);
		assertEquals(List.of(0), shaft.keysSaved());
		shaft.apply(loot("Tungsten"), 3_000);
		assertEquals(List.of(0, 1), shaft.keysSaved(), "2nd msg waited for the next key corpse");
	}

	@Test
	void otherCorpseInTheWindowDoesntTakeIt() {
		Shaft shaft = new Shaft(0);
		shaft.apply(loot("Umber"), 1_000);
		shaft.apply(loot("Lapis"), 2_000);
		shaft.apply(new KeySaved(), 2_100);
		assertEquals(List.of(0), shaft.keysSaved(), "the umber, not the lapis looted after it");
	}

	@Test
	void waitingMsgRunsOut() {
		Shaft shaft = new Shaft(0);
		shaft.apply(new KeySaved(), 1_000);
		shaft.apply(loot("Tungsten"), 600_000);
		assertEquals(List.of(), shaft.keysSaved(), "10 min later isnt the same click");
	}

	@Test
	void savedOnTheJournalEntry() {
		Shaft shaft = new Shaft(0);
		shaft.apply(loot("Umber"), 1_000);
		assertNull(JournalEntry.of(shaft, 0, null, TextPatterns.defaults()).keysSaved(), "none = not written");
		shaft.apply(new KeySaved(), 1_100);
		JournalEntry entry = JournalEntry.of(shaft, 0, null, TextPatterns.defaults());
		assertEquals(List.of(0), entry.keysSaved());
		assertTrue(entry.keySaved(0));
	}
}
