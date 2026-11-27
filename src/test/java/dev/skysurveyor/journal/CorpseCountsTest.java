package dev.skysurveyor.journal;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import dev.skysurveyor.detection.Corpse;
import dev.skysurveyor.detection.ShaftTracker;
import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.journal.JournalStats.CorpseCount;
import dev.skysurveyor.journal.PersonalBests.Stat;
import dev.skysurveyor.settings.MoneySettings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorpseCountsTest {
	// nicks journal.jsonl, rec 6 at 9:51. lapis looted 9:53.3, tab still not 9:53.5, left 9:56.3, exit bonus counted it
	static final String AQUA_2 = "{\"version\":1,\"sessionId\":1794939351699,\"shaftId\":\"AQUA_2\",\"enteredAt\":1794939967690,"
			+ "\"durationMillis\":6190,\"exit\":\"BYE\",\"exitPowder\":1250,\"searchMillis\":149557,\"corpses\":[{\"type\":\"Lapis\","
			+ "\"looted\":false},{\"type\":\"Umber\",\"looted\":false},{\"type\":\"Tungsten\",\"looted\":false},{\"type\":\"Tungsten\","
			+ "\"looted\":false}],\"extraCorpses\":1,\"loots\":[{\"type\":\"Lapis\",\"bonusDrop\":false,\"items\":[{\"item\":"
			+ "\"Yellow Goblin Egg\",\"count\":1},{\"item\":\"Flawed Onyx Gemstone\",\"count\":20},{\"item\":\"Flawed Peridot Gemstone\","
			+ "\"count\":20}],\"powder\":5536}],\"mayhemBuffs\":[\"Suspicious Scrap chance\"],\"campfiresAt\":[],\"coldAtExit\":0,"
			+ "\"scrapFound\":0,\"pristineProcs\":0,\"pristineItems\":{},\"sacksAdded\":{},\"sacksRemoved\":{},\"crystals\":[],"
			+ "\"otherDrops\":{},\"prices\":{\"FLAWLESS_PERIDOT_GEM\":[2791603,3029039.1],\"FINE_ONYX_GEM\":[34538.1,38146.1,null,25600],"
			+ "\"FINE_PERIDOT_GEM\":[34485.5,38042.2,null,25600],\"FLAWLESS_ONYX_GEM\":[2798146.2,3029999.9],"
			+ "\"GOBLIN_EGG_YELLOW\":[9118.1,13404.2,null,10000]},\"campfireColds\":[]}";

	static JournalEntry aqua2() {
		return new Gson().fromJson(AQUA_2, JournalEntry.class);
	}

	private static List<JournalEntry> realJournal() throws Exception {
		Path path = Path.of(CorpseCountsTest.class.getResource("/journal-2026-11-11.jsonl").toURI());
		return new JournalFile(path, Runnable::run).load().join();
	}

	@Test
	void lootedBeforeTheTabListFlipped() {
		JournalEntry aqua = aqua2();
		assertTrue(aqua.corpses().stream().noneMatch(Corpse::looted), "saved as the tab list showed it");
		Map<String, CorpseCount> counts = CorpseCounts.of(aqua);
		assertEquals(List.of("Lapis", "Umber", "Tungsten"), List.copyOf(counts.keySet()));
		assertEquals(new CorpseCount(1, 1), counts.get("Lapis"));
		assertEquals(new CorpseCount(0, 1), counts.get("Umber"));
		assertEquals(new CorpseCount(0, 2), counts.get("Tungsten"));
		assertEquals(1, CorpseCounts.looted(counts));
		assertEquals(4, CorpseCounts.total(counts));

		String text = String.join("\n", SummaryText.lines(aqua, null, null, MoneySettings.DEFAULTS));
		assertTrue(text.contains(" §7Corpses §9Lapis 1/1§7, §fUmber 0/1§7, §fTungsten 0/2"), text);
		assertEquals(new ToastText.Row("Corpses", "§9Lapis 1/1§7, §fUmber 0/1§7, §fTungsten 0/2"),
				ToastText.of(aqua, null, null, false).rows().getFirst());
		assertEquals(1.0, PersonalBests.shaftValue(Stat.SHAFT_LAPIS, aqua, null));
		assertEquals(1.0, PersonalBests.shaftValue(Stat.SHAFT_CORPSES, aqua, null));
	}

	// same shaft live, rec 6: loot 9:53.3, tab still not looted 9:53.5
	@Test
	void liveShaftCountsTheLootBeforeTheTabListFlips() {
		ShaftTracker tracker = new ShaftTracker(TextPatterns.defaults(), e -> {
		});
		tracker.onWorldChange(0);
		tracker.onScoreboard(0, List.of("§f§lS§6§lK§e§lYBLOCK", "§8§711/17/26 §8m18§8§u§8§86G AQUA_2"));
		String bar = "§a§l" + "▬".repeat(64);
		for (String line : List.of(bar, "§r  §9§lLAPIS §b§lCORPSE LOOT! ", "", "§r  §a§lREWARDS", "§r    §9Yellow Goblin Egg",
				"§r    §a Flawed Onyx Gemstone §8x20", "§r    §a Flawed Peridot Gemstone §8x20", "§r    §bGlacite Powder §8x5,536", bar)) {
			tracker.onChat(1_744, line, List.of());
		}
		String notLooted = "§c§lNOT LOOTED";
		tracker.onTabList(2_001, widget(notLooted));
		Map<String, CorpseCount> expected = Map.of("Lapis", new CorpseCount(1, 1), "Umber", new CorpseCount(0, 1), "Tungsten",
				new CorpseCount(0, 2));
		assertEquals(expected, CorpseCounts.of(tracker.current()));
		tracker.onTabList(3_000, widget("§a§lLOOTED"));
		assertEquals(expected, CorpseCounts.of(tracker.current()), "flipped tab list doesnt count it twice");
	}

	private static List<String> widget(String lapis) {
		return List.of("§b§lFrozen Corpses:", "§r §9Lapis§f: " + lapis, "§r §6Umber§f: §c§lNOT LOOTED", "§r §7Tungsten§f: §c§lNOT LOOTED",
				"§r §7Tungsten§f: §c§lNOT LOOTED", "");
	}

	// real 0.2.0 line, loots taken off like an unparsed loot msg
	@Test
	void tabListAheadOfChatStillCounts() throws Exception {
		JournalEntry titanium = EntryBuilder.from(realJournal().get(3)).loots(List.of()).build();
		assertEquals(new CorpseCount(2, 2), CorpseCounts.of(titanium).get("Lapis"));
		assertEquals(2, CorpseCounts.looted(titanium, "Lapis"));
	}

	// widget never showed, f.e. a very short shaft
	@Test
	void typeOnlyInChatGetsARow() {
		JournalEntry aqua = EntryBuilder.from(aqua2()).corpses(List.of()).build();
		assertEquals(Map.of("Lapis", new CorpseCount(1, 1)), CorpseCounts.of(aqua));
		String text = String.join("\n", SummaryText.lines(aqua, null, null, MoneySettings.DEFAULTS));
		assertTrue(text.contains(" §7Corpses §9Lapis 1/1\n"), text);
		assertEquals(new ToastText.Row("Corpses", "§9Lapis 1/1"), ToastText.of(aqua, null, null, false).rows().getFirst());

		JournalEntry none = EntryBuilder.from(aqua).loots(List.of()).build();
		assertTrue(CorpseCounts.of(none).isEmpty());
		assertFalse(String.join("\n", SummaryText.lines(none, null, null, MoneySettings.DEFAULTS)).contains("Corpses"));
	}

	@Test
	void oneTypeMatchesTheFullCount() throws Exception {
		for (JournalEntry entry : realJournal()) {
			CorpseCounts.of(entry).forEach((type, count) -> assertEquals(count.looted(), CorpseCounts.looted(entry, type), entry.shaftId()));
			assertEquals(0, CorpseCounts.looted(entry, "Nope"));
		}
	}

	@Test
	void normalEntriesUnchanged() throws Exception {
		int loots = 0;
		for (JournalEntry entry : realJournal()) {
			Map<String, int[]> tab = new LinkedHashMap<>();
			for (Corpse corpse : entry.corpses()) {
				int[] count = tab.computeIfAbsent(corpse.type(), t -> new int[2]);
				if (corpse.looted()) count[0]++;
				count[1]++;
			}
			Map<String, CorpseCount> expected = new LinkedHashMap<>();
			tab.forEach((type, count) -> expected.put(type, new CorpseCount(count[0], count[1])));
			assertEquals(expected, CorpseCounts.of(entry), entry.shaftId());
			loots += entry.loots().size();
		}
		assertTrue(loots > 0);
	}

	@Test
	void allTimeTotals() throws Exception {
		List<JournalEntry> entries = new ArrayList<>(realJournal());
		JournalStats.AllTime before = JournalStats.allTime(entries);
		JournalEntry aqua = aqua2();
		JournalEntry chatOnly = EntryBuilder.from(aqua).sessionId(aqua.sessionId() + 1).enteredAt(aqua.enteredAt() + 600_000)
				.corpses(List.of()).build();
		entries.add(aqua);
		entries.add(chatOnly);
		JournalStats.AllTime after = JournalStats.allTime(entries);
		assertEquals(before.looted().get("Lapis") + 2, after.looted().get("Lapis"));
		assertEquals(before.corpsesSeen().get("Lapis") + 2, after.corpsesSeen().get("Lapis"));
		assertEquals(before.corpsesSeen().get("Tungsten") + 2, after.corpsesSeen().get("Tungsten"));
		assertEquals(before.shaftsWithCorpse().get("Lapis") + 2, after.shaftsWithCorpse().get("Lapis"));
		assertEquals(before.shaftsWithCorpse().get("Umber") + 1, after.shaftsWithCorpse().get("Umber"));
		assertEquals(after.drops().get("Lapis").corpses(), after.looted().get("Lapis"), "loots w/ items = looted, no more '123 of 119'");
		assertEquals(1, new JournalStats.SessionSummary(aqua.sessionId(), List.of(aqua)).looted("Lapis"));
	}
}
