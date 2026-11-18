package dev.skysurveyor.journal;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.data.ShaftNames;
import dev.skysurveyor.detection.Corpse;
import dev.skysurveyor.journal.PersonalBests.Def;
import dev.skysurveyor.journal.PersonalBests.Result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class PersonalBestsTest {
	// made up shafts, not game data
	private static final PersonalBests.Coins COINS = entry -> entry.exitPowder() * 1000.0;

	private static JournalEntry shaft(long session, int minute, String id, long powder, Long spawn, List<Corpse> corpses) {
		return EntryBuilder.entry().sessionId(session).shaftId(id).enteredAt(session + minute * 60_000L).durationMillis(60_000)
				.exitPowder(powder).searchMillis(spawn).corpses(corpses).crystals(null).otherDrops(null).build();
	}

	private static Result best(String stat, String shaft, String best, List<JournalEntry> entries) {
		return PersonalBests.best(new Def(stat, shaft, best, null), entries, JournalStats.sessions(entries), COINS);
	}

	@Test
	void shaftBestsFilterAndFlip() {
		JournalEntry jade = shaft(0, 1, "JADE_1", 500, 90_000L, List.of());
		JournalEntry jasper = shaft(0, 3, "JASP_C", 300, 20_000L, List.of(new Corpse("Lapis", true), new Corpse("Lapis", true)));
		JournalEntry ruby = shaft(0, 5, "RUBY_2", 200, null, List.of(new Corpse("Lapis", false)));
		List<JournalEntry> entries = List.of(jade, jasper, ruby);
		assertSame(jade, best("shaft_powder", null, null, entries).entry());
		assertSame(jasper, best("shaft_powder", "JASP", null, entries).entry(), "crystal counts as jasper");
		assertSame(ruby, best("shaft_powder", null, "lowest", entries).entry());
		assertSame(jasper, best("shaft_spawn", null, null, entries).entry(), "spawn is lowest by default");
		assertEquals(2.0, best("shaft_lapis", null, null, entries).value(), "not looted ones dont count");
		assertEquals(1.0, best("shaft_lapis", null, null, List.of(jade, CorpseCountsTest.aqua2())).value(), "chat loot before the tab list flips");
		assertNull(best("shaft_powder", "OPAL", null, entries).value(), "no opal shafts");
		assertEquals(500_000.0, best("shaft_coins", null, null, entries).value());
	}

	@Test
	void sessionBestsNeedEnoughShaftsForRates() {
		List<JournalEntry> entries = new ArrayList<>();
		// 4 quick shafts, too few for a rate record
		for (int i = 0; i < 4; i++) entries.add(shaft(0, i, "JADE_1", 100, 30_000L, List.of()));
		long later = 86_400_000L;
		for (int i = 0; i < 6; i++) entries.add(shaft(later, i * 10, i % 2 == 0 ? "JASP_1" : "JADE_1", 100, 60_000L, List.of()));
		assertEquals(later, best("session_rate", null, null, entries).session().id());
		assertEquals(later, best("session_shafts", null, null, entries).session().id());
		assertEquals(3.0, best("session_shafts", "JASP", null, entries).value(), "only jasper shafts count");
		assertEquals(0, best("session_spawn", null, null, entries).session().id(), "fastest avg spawn");
	}

	@Test
	void drystreakCurrentAndLongest() {
		List<JournalEntry> entries = new ArrayList<>();
		String[] ids = {"JADE_1", "JADE_1", "JASP_1", "RUBY_1", "RUBY_1", "RUBY_1", "JASP_C", "OPAL_1"};
		for (int i = 0; i < ids.length; i++) entries.add(shaft(0, i, ids[i], 100, null, List.of()));
		Result dry = best("drystreak", "JASP", null, entries);
		assertEquals(3.0, dry.value());
		assertEquals(1L, dry.current());
		assertSame(entries.get(6), dry.entry(), "the jasper that ended it");
		Result never = best("drystreak", "FAIR", null, entries);
		assertEquals(8.0, never.value());
		assertNull(never.entry(), "still going");
	}

	@Test
	void keysAndTheLiveDrystreak() {
		assertEquals("shaft_coins", PersonalBests.key(new Def("shaft_coins", null, null, "renamed")), "names dont change it");
		assertEquals("drystreak_jasp", PersonalBests.key(new Def("drystreak", null, null, null)));
		assertEquals("shaft_powder_opal_lowest", PersonalBests.key(new Def("shaft_powder", "OPAL", "lowest", null)));

		Def dry = new Def("drystreak", "JASP", null, null);
		Result saved = new Result(3.0, null, null, 3L);
		assertEquals(4L, PersonalBests.live(dry, saved, "JADE").current(), "the shaft ur in counts");
		assertEquals(4.0, PersonalBests.live(dry, saved, "JADE").value(), "and can be the longest");
		assertEquals(0L, PersonalBests.live(dry, saved, "JASP").current(), "in a jasper = 0");
		assertSame(saved, PersonalBests.live(dry, saved, null), "outside = as saved");
	}

	@Test
	void namesFollowThePicks() {
		ShaftNames names = ShaftNames.defaults();
		assertEquals("Coins / shaft", PersonalBests.name(new Def("shaft_coins", null, null, null), names));
		assertEquals("Coins / Jasper shaft", PersonalBests.name(new Def("shaft_coins", "JASP", null, null), names));
		assertEquals("Jasper shafts / h", PersonalBests.name(new Def("session_rate", "JASP", null, null), names));
		assertEquals("Shafts / session", PersonalBests.name(new Def("session_shafts", null, null, null), names));
		assertEquals("Vanguard dry", PersonalBests.name(new Def("drystreak", "FAIR", null, null), names));
		assertEquals("Jasper dry", PersonalBests.name(new Def("drystreak", null, null, null), names));
		assertEquals("My PB", PersonalBests.name(new Def("shaft_coins", null, null, "My PB"), names));
	}
}
