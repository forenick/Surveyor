package dev.skysurveyor.hud;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.data.FossilSpots;
import dev.skysurveyor.data.ShaftNames;
import dev.skysurveyor.detection.Corpse;
import dev.skysurveyor.detection.RecordingReplay;
import dev.skysurveyor.detection.ShaftTracker;
import dev.skysurveyor.detection.TextPatterns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

// topaz entry from recording 2026-11-15: id at 0.5 s, mayhem 1.5 s, corpses 2.0 s
class EntryTitleTest {
	private static final List<String> SCOREBOARD = List.of("§8§711/15/26 §8m88§8§u§8§8CT TOPA_1");
	private static final String MAYHEM = "§b§lMAYHEM! §7You received a §6 Mining Fortune §7buff from your §aMineshaft Mayhem §7perk!";
	private static final List<String> TAB = List.of("§b§lFrozen Corpses:", "§r §9Lapis§f: §c§lNOT LOOTED", "§r §6Umber§f: §c§lNOT LOOTED",
			"§r §7Tungsten§f: §c§lNOT LOOTED", "");
	private static final List<String> TAB_LOOTED = List.of("§b§lFrozen Corpses:", "§r §9Lapis§f: §a§lLOOTED",
			"§r §6Umber§f: §c§lNOT LOOTED", "§r §7Tungsten§f: §c§lNOT LOOTED", "");

	private final EntryTitle title = new EntryTitle(ShaftNames.defaults(), FossilSpots.defaults()::has);
	private long now;
	private final ShaftTracker tracker = new ShaftTracker(TextPatterns.defaults(), event -> title.onEvent(event, now));

	private void at(long millis) {
		now = millis;
	}

	@Test
	void fillsInAsTheGameShowsIt() {
		tracker.onWorldChange(0);
		at(500);
		tracker.onScoreboard(now, SCOREBOARD);
		EntryTitle.Text first = title.text(now, 5);
		assertEquals("§eTopaz Mineshaft", first.title());
		// topaz has a fossil
		assertEquals(List.of("§dFossil"), first.lines());
		at(1_509);
		tracker.onChat(now, MAYHEM, List.of());
		at(2_004);
		tracker.onTabList(now, TAB);
		assertEquals(List.of("§f3 corpses: §9Lapis§7, §6Umber§7, §7Tungsten", "§dFossil", "§7Mayhem: §fMining Fortune"),
				title.text(now, 5).lines());
	}

	@Test
	void staysAfterTheLastChangeThenIsDone() {
		tracker.onWorldChange(0);
		tracker.onScoreboard(0, SCOREBOARD);
		assertEquals(0, title.text(0, 5).alpha(), 1e-6);
		assertEquals(1, title.text(2_000, 5).alpha(), 1e-6);
		at(4_000);
		tracker.onTabList(now, TAB);
		// corpses at 4 s, so up till 9 s
		assertNotNull(title.text(4_000, 5));
		assertNotNull(title.text(8_900, 5));
		assertEquals(0.2f, title.text(8_900, 5).alpha(), 1e-6);
		// a loot doesnt bring it back or keep it up
		at(8_950);
		tracker.onTabList(now, TAB_LOOTED);
		assertNull(title.text(9_000, 5));
		assertNull(title.text(9_100, 5));
	}

	@Test
	void outsideAShaftNothing() {
		tracker.onWorldChange(0);
		tracker.onChat(0, MAYHEM, List.of());
		assertNull(title.text(100, 5));
		tracker.onScoreboard(500, SCOREBOARD);
		// mayhem came before the id
		assertEquals(List.of("§dFossil", "§7Mayhem: §fMining Fortune"), title.text(500, 5).lines());
		tracker.onWorldChange(1_000);
		assertNull(title.text(1_000, 5));
	}

	@Test
	void variantsAndCounts() {
		tracker.onWorldChange(0);
		tracker.onScoreboard(0, List.of("§711/21/26 §8m113BD PERI_C"));
		assertEquals("§2Peridot Mineshaft §7(crystal)", title.text(0, 5).title());
		assertEquals(List.of(), title.text(0, 5).lines(), "no fossil in crystal shafts");
		assertEquals("§f4 corpses: §93 Lapis§7, §7Tungsten", EntryTitle.corpses(List.of(new Corpse("Lapis", false),
				new Corpse("Lapis", false), new Corpse("Tungsten", false), new Corpse("Lapis", true))));
		assertEquals("§f1 corpse: §bVanguard", EntryTitle.corpses(List.of(new Corpse("Vanguard", false))));
	}

	// every shaft entered in recording 2026-11-15 gets a title w/ its corpses + buff
	@Test
	void recording8EveryShaft() throws IOException {
		Path recording = RecordingReplay.RECORDINGS.resolve("2026-11-15_16-10-29.jsonl");
		assumeTrue(Files.exists(recording), "Recording not available: " + recording);
		List<EntryTitle.Text> seen = new ArrayList<>();
		long[] clock = {0};
		EntryTitle live = new EntryTitle(ShaftNames.defaults(), FossilSpots.defaults()::has);
		ShaftTracker replayed = new ShaftTracker(TextPatterns.defaults(), event -> live.onEvent(event, clock[0]));
		EntryTitle.Text[] last = {null};
		RecordingReplay.replay(recording, replayed, t -> {
			clock[0] = t;
			EntryTitle.Text text = live.text(t, 5);
			if (text == null && last[0] != null) seen.add(last[0]);
			last[0] = text;
		}, t -> {
		});
		assertEquals(25, seen.size());
		assertEquals(List.of("§f3 corpses: §9Lapis§7, §6Umber§7, §7Tungsten", "§dFossil", "§7Mayhem: §fMining Fortune"),
				seen.get(2).lines());
		assertEquals("§eTopaz Mineshaft", seen.get(2).title());
		assertEquals(List.of("§f1 corpse: §bVanguard", "§7Mayhem: §fMining Fortune"), seen.get(3).lines());
	}
}
