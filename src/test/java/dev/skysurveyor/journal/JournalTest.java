package dev.skysurveyor.journal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.skysurveyor.detection.Corpse;
import dev.skysurveyor.detection.RecordingReplay;
import dev.skysurveyor.detection.ShaftEvent;
import dev.skysurveyor.detection.ShaftTracker;
import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.settings.MoneySettings;
import dev.skysurveyor.util.Format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class JournalTest {
	private static final long MINUTE = 60_000;
	private static final String WOW = "§5§lWOW! §aYou found a §bGlacite Mineshaft portal!";
	private static final String NEARBY = "§b§lMINESHAFT! §7A Mineshaft portal spawned nearby!";

	private final List<JournalEntry> entries = new ArrayList<>();
	private boolean inMines = true;
	private final Journal journal = new Journal(TextPatterns.defaults(), () -> inMines, entries::add);
	private long now;
	private final ShaftTracker tracker = new ShaftTracker(TextPatterns.defaults(), event -> journal.onEvent(event, now));

	@Test
	void timesSpawnFromFirstBlockToWow() {
		at(0).hit(true);
		at(30_000).hit(true);
		at(MINUTE).chat(WOW).chat(NEARBY);
		visitShaft(MINUTE + 3_000, "JADE_1");

		assertEquals(1, entries.size());
		assertEquals(MINUTE, entries.get(0).searchMillis());
		assertEquals("JADE_1", entries.get(0).shaftId());
		assertEquals(new Journal.Session(0, 1, 1, MINUTE), journal.session());

		// timer restarts at first hit after shaft, not on leave
		at(2 * MINUTE).hit(true);
		at(2 * MINUTE + 20_000).hit(true);
		at(2 * MINUTE + 40_000).chat(WOW);
		visitShaft(2 * MINUTE + 43_000, "AMET_1");
		assertEquals(40_000, entries.get(1).searchMillis());
		assertEquals(50_000, journal.session().averageSearchMillis());
	}

	@Test
	void gapsOverFortySecondsOnlyCountFortySeconds() {
		at(0).hit(true);
		at(10_000).hit(true);
		at(110_000).hit(true);
		at(120_000).chat(WOW);
		visitShaft(123_000, "JADE_1");
		assertEquals(60_000, entries.get(0).searchMillis());
	}

	// rec 8 PERI_C: last hit, 2 warps, next hit 12.5 s later
	@Test
	void aWarpEndsTheGap() {
		at(0).hit(true);
		at(10_000).hit(true);
		at(17_000);
		journal.onWorldChange(now);
		assertEquals(17_000, journal.spawnTimerMillis(20_000), "stops at the warp");
		at(21_000);
		journal.onWorldChange(now);
		at(22_500).hit(true);
		at(30_000).chat(WOW);
		visitShaft(33_000, "PERI_C");
		assertEquals(24_500, entries.get(0).searchMillis());
	}

	@Test
	void spawnTimerForTheHud() {
		assertNull(journal.spawnTimerMillis(0));
		at(0).hit(true);
		at(10_000).hit(true);
		assertEquals(15_000, journal.spawnTimerMillis(15_000));
		assertEquals(50_000, journal.spawnTimerMillis(200_000), "a break counts for 40 s at most");
		at(20_000).chat(WOW);
		assertEquals(20_000, journal.spawnTimerMillis(90_000), "frozen at the found time");
		visitShaft(23_000, "JADE_1");
		assertNull(journal.spawnTimerMillis(30_000), "nothing until the next search starts");
	}

	@Test
	void spawnTimerEndsWithTheSession() {
		at(0).hit(true);
		at(60_000).hit(true);
		assertEquals(80_000, journal.spawnTimerMillis(60_000 + Journal.SESSION_GAP_MILLIS), "40 s of mining plus a 40 s break");
		assertNull(journal.spawnTimerMillis(60_001 + Journal.SESSION_GAP_MILLIS),
				"the next activity starts a new session and drops this timer, so it isn't shown");
	}

	@Test
	void aPortalLeftUnenteredIsNotTimedForTheNextShaft() {
		at(0).hit(true);
		at(20_000).chat(WOW);
		at(70_000).hit(true); // >40 s later = new search, portal abandoned
		assertEquals(0, journal.spawnTimerMillis(70_000));
		visitShaft(80_000, "JADE_1"); // someone elses portal, no WOW
		assertNull(entries.get(0).searchMillis());
	}

	@Test
	void sessionRunsOutForDisplayBeforeTheNextActivity() {
		at(0).hit(true);
		assertEquals(0, journal.activeSession(Journal.SESSION_GAP_MILLIS).id());
		assertNull(journal.activeSession(Journal.SESSION_GAP_MILLIS + 1));
		assertNull(new Journal(TextPatterns.defaults(), () -> true, e -> {
		}).activeSession(0), "no session before any activity");
	}

	@Test
	void pickobulusCountsAsMining() {
		at(0).hit(true);
		at(35_000).chat("§aYou used your §6Pickobulus §aPickaxe Ability!");
		at(70_000).chat("§aYou used your §6Pickobulus §aPickaxe Ability!");
		at(71_000).chat(WOW);
		visitShaft(74_000, "JADE_1");
		assertEquals(71_000, entries.get(0).searchMillis());
	}

	@Test
	void miningBetweenWowAndEnteringDoesNotStartATimer() {
		at(0).hit(true);
		at(10_000).chat(WOW);
		at(12_000).hit(true);
		assertTrue(!journal.searchRunning());
		visitShaft(15_000, "JADE_1");
		assertEquals(10_000, entries.get(0).searchMillis());

		// portal not entered within 40 s = skipped
		at(MINUTE).hit(true);
		at(MINUTE + 5_000).chat(WOW);
		at(MINUTE + 50_000).hit(true);
		assertTrue(journal.searchRunning());
	}

	@Test
	void doesNotTimeOtherPlayersPortalsOrHitsOutsideTheMines() {
		at(0).hit(false);
		at(MINUTE).chat(WOW);
		visitShaft(MINUTE + 3_000, "JADE_1");
		assertNull(entries.get(0).searchMillis());

		at(2 * MINUTE).hit(true);
		at(3 * MINUTE).chat(NEARBY);
		visitShaft(3 * MINUTE + 3_000, "OPAL_1");
		assertNull(entries.get(1).searchMillis());
		assertEquals(null, journal.session().averageSearchMillis());
	}

	@Test
	void onlyMinesAndShaftsKeepASession() {
		at(0).hit(true);
		visitShaft(MINUTE, "JADE_1");
		// half an hour on the garden
		for (long t = 2 * MINUTE; t <= 32 * MINUTE; t += MINUTE) at(t).hit(false);
		assertNull(journal.activeSession(32 * MINUTE));

		// 20 min mining inside one shaft
		at(40 * MINUTE).hit(true);
		at(41 * MINUTE).tracker.onWorldChange(now);
		tracker.onScoreboard(now, List.of("§e§lSKYBLOCK", "§711/11/26 §8m19AB JADE_1"));
		for (long t = 42 * MINUTE; t <= 60 * MINUTE; t += MINUTE) at(t).hit(false);
		at(61 * MINUTE).tracker.onWorldChange(now);
		assertEquals(40 * MINUTE, entries.get(1).sessionId(), "same session, not a new one at the exit");
	}

	@Test
	void longBreakStartsNewSessionAndDropsTheTimer() {
		at(0).hit(true);
		at(16 * MINUTE).hit(true);
		at(17 * MINUTE).chat(WOW);
		visitShaft(17 * MINUTE + 3_000, "JADE_1");

		assertEquals(16 * MINUTE, entries.get(0).sessionId());
		assertEquals(40_000, entries.get(0).searchMillis());
	}

	@Test
	void resumesRecentSessionAfterRestart() {
		at(0).hit(true);
		at(MINUTE).chat(WOW);
		visitShaft(MINUTE + 3_000, "JADE_1");

		Journal restarted = new Journal(TextPatterns.defaults(), () -> true, entry -> {
		});
		restarted.resume(entries);
		assertEquals(journal.session(), restarted.session());
		restarted.onBlockHit(MINUTE + 5 * MINUTE);
		assertEquals(0, restarted.session().id());
		restarted.onBlockHit(MINUTE + 30 * MINUTE);
		assertEquals(MINUTE + 30 * MINUTE, restarted.session().id());
	}

	@Test
	void miningAfterTheLastShaftKeepsTheSessionAfterRestart() {
		at(0).hit(true);
		at(MINUTE).chat(WOW);
		visitShaft(MINUTE + 3_000, "JADE_1");

		Journal restarted = new Journal(TextPatterns.defaults(), () -> true, entry -> {
		});
		restarted.resume(entries);
		// mined outside till 20 min, then restarted
		restarted.resumeActivity(0, 20 * MINUTE);
		assertEquals(0, restarted.activeSession(30 * MINUTE).id());
		restarted.resumeActivity(0, 10 * MINUTE);
		assertEquals(0, restarted.activeSession(30 * MINUTE).id(), "older activity doesnt move it back");
		restarted.resumeActivity(5, 50 * MINUTE);
		assertNull(restarted.activeSession(40 * MINUTE), "other session ignored");
	}

	@Test
	void fileRoundTrip(@TempDir Path dir) throws IOException {
		Path recording = RecordingReplay.RECORDINGS.resolve("2026-11-11_14-13-53.jsonl");
		assumeTrue(Files.exists(recording), "Recording not available: " + recording);
		replay(recording);
		assertEquals(2, entries.size());

		JournalFile file = new JournalFile(dir.resolve("skysurveyor").resolve("journal.jsonl"), Runnable::run);
		entries.forEach(file::append);
		assertEquals(entries, file.read());
	}

	// rec 2, by hand in research/06: 379.1 Fine reported, ~385 total
	@Test
	void jadeShaftGems() throws IOException {
		Path recording = RecordingReplay.RECORDINGS.resolve("2026-11-08_22-41-09.jsonl");
		assumeTrue(Files.exists(recording), "Recording not available: " + recording);
		replay(recording);
		GemYield jade = entries.get(14).gems();

		assertEquals("Jade", jade.gem());
		assertEquals(135_434, jade.rough());
		assertEquals(28_635, jade.flawed());
		assertEquals(379.1, jade.reportedFine(), 0.05);
		assertTrue(jade.fineEquivalent() > 382 && jade.fineEquivalent() < 386, "total " + jade.fineEquivalent());
	}

	// rec 6 at 16:16.3 + 17:22.4 in JADE_1: 5,650 hard stone in, 5,760 compacted
	@Test
	void compactedHardStoneIsntAnotherDrop() {
		at(0).tracker.onWorldChange(now);
		tracker.onScoreboard(now, List.of("§e§lSKYBLOCK", "§711/17/26 §8m19AB JADE_1"));
		at(30_000).tracker.onChat(now, "§6[Sacks] §a+3,479§e items§e, §c-2,880§e items§e.§8 (Last 23s.)", List.of(
					"§aAdded items:\n§a  +5 §eEnchanted Hard Stone§8 (Enchanted Mining Sack)§a\n§a  +2,475 §eHard Stone§8 (Mining Sack)§a\n"
							+ "§a  +160 §e Rough Jade Gemstone§8 (Gemstones Sack)§a",
					"§cRemoved items:\n§c  -2,880 §eHard Stone§8 (Mining Sack)§c"));
		at(60_000).tracker.onChat(now, "§6[Sacks] §a+16,654§e items§e, §c-2,880§e items§e.§8 (Last 30s.)", List.of(
					"§aAdded items:\n§a  +5 §eEnchanted Hard Stone§8 (Enchanted Mining Sack)§a\n§a  +3,175 §eHard Stone§8 (Mining Sack)§a\n"
							+ "§a  +2,387 §e Flawed Jade Gemstone§8 (Gemstones Sack)§a\n§a  +11,087 §e Rough Jade Gemstone§8 (Gemstones Sack)§a",
					"§cRemoved items:\n§c  -2,880 §eHard Stone§8 (Mining Sack)§c"));
		at(90_000).tracker.onWorldChange(now);
		JournalEntry jade = entries.getFirst();
		assertEquals(Map.of("Enchanted Hard Stone", 10L), jade.otherDrops());
		assertEquals(11_247, jade.gems().rough());
		// raw msgs saved as they came
		assertEquals(5_650L, jade.sacksAdded().get("Hard Stone"));
		assertEquals(5_760L, jade.sacksRemoved().get("Hard Stone"));
	}

	// hand sums: hard stone 47,931 in, 48,384 compacted = none mined. enchanted 88, none from corpses
	@Test
	void recording6JadeShaft() throws IOException {
		Path recording = RecordingReplay.RECORDINGS.resolve("2026-11-17_19-16-17.jsonl");
		assumeTrue(Files.exists(recording), "Recording not available: " + recording);
		replay(recording);
		JournalEntry jade = entries.stream().filter(entry -> "JADE_1".equals(entry.shaftId())).findFirst().orElseThrow();
		assertNull(jade.otherDrops().get("Hard Stone"));
		assertEquals(88L, jade.otherDrops().get("Enchanted Hard Stone"));
		assertEquals(47_931L, jade.sacksAdded().get("Hard Stone"));
		// 7 RARE DROP! lines, each in the next sacks msg. sacks onyx 3, peridot 2, citrine 3, 1 onyx from a corpse
		assertEquals(2L, jade.otherDrops().get("Fine Onyx Gemstone"));
		assertEquals(2L, jade.otherDrops().get("Fine Peridot Gemstone"));
		assertEquals(3L, jade.otherDrops().get("Fine Citrine Gemstone"));
	}

	// left before any sacks msg, drops only in chat: 0:55.9 + 12:48.0 to 12:58.3
	@Test
	void recording6RareDropsInShortShafts() throws IOException {
		Path recording = RecordingReplay.RECORDINGS.resolve("2026-11-17_19-16-17.jsonl");
		assumeTrue(Files.exists(recording), "Recording not available: " + recording);
		replay(recording);
		JournalEntry amethyst = entries.stream().filter(entry -> "AMET_1".equals(entry.shaftId())).findFirst().orElseThrow();
		assertEquals(Map.of("Fine Citrine Gemstone", 1L, "Fine Peridot Gemstone", 1L), amethyst.otherDrops());
		JournalEntry fair = entries.stream().filter(entry -> "FAIR_1".equals(entry.shaftId())).findFirst().orElseThrow();
		assertEquals(Map.of("Fine Aquamarine Gemstone", 2L, "Fine Peridot Gemstone", 2L, "Fine Citrine Gemstone", 1L,
				"Glacite Jewel", 1L, "Fine Onyx Gemstone", 1L), fair.otherDrops());
		assertEquals(Map.of(), fair.sacksAdded());
	}

	// lapis looted 9:53.3, tab still not at 9:53.5, left 9:56.3. exit 1,250 = 1,000 + 250 for it
	@Test
	void recording6LootedLapisBeforeTheTabListFlipped() throws IOException {
		Path recording = RecordingReplay.RECORDINGS.resolve("2026-11-17_19-16-17.jsonl");
		assumeTrue(Files.exists(recording), "Recording not available: " + recording);
		replay(recording);
		JournalEntry aqua = entries.stream().filter(entry -> "AQUA_2".equals(entry.shaftId())).findFirst().orElseThrow();
		assertEquals(1250, aqua.exitPowder());
		assertTrue(aqua.corpses().stream().noneMatch(Corpse::looted), "tab list lagged");
		String text = String.join("\n", SummaryText.lines(aqua, null, null, MoneySettings.DEFAULTS));
		assertTrue(text.contains("§9Lapis 1/1§7, §fUmber 0/1§7, §fTungsten 0/2"), text);
	}

	// jade fossil 2:17 to 2:26: 43 quartz blocks (42 + 1 spread), 17 bars after a break summing 112
	@Test
	void recording7FossilEssence() throws IOException {
		Path recording = RecordingReplay.RECORDINGS.resolve("2026-11-21_19-12-23.jsonl.gz");
		assumeTrue(Files.exists(recording), "Recording not available: " + recording);
		replay(recording);
		JournalEntry jade = entries.stream().filter(entry -> "JADE_1".equals(entry.shaftId())).findFirst().orElseThrow();
		assertEquals(new FossilYield(43, 17, 112), jade.fossil());
		assertEquals(283, jade.fossilEssence());
		assertTrue(entries.stream().filter(entry -> !"JADE_1".equals(entry.shaftId())).allMatch(entry -> entry.fossil() == null));
	}

	@Test
	void summaryOfRecording3() throws IOException {
		Path recording = RecordingReplay.RECORDINGS.resolve("2026-11-11_14-13-53.jsonl");
		assumeTrue(Files.exists(recording), "Recording not available: " + recording);
		replay(recording);
		JournalEntry aqua = entries.get(0);
		assertNull(aqua.gems(), "Nick can't mine Aquamarine yet, so there are no Aquamarine gems");
		assertEquals(5759 + 4791, aqua.corpsePowder());

		String text = String.join("\n", SummaryText.lines(aqua, null, null, MoneySettings.DEFAULTS));
		assertTrue(text.contains("§3§lAquamarine crystal  §7inside §f14s"), text);
		assertTrue(text.contains("§9Lapis 2/2§7, §fTungsten 0/1"), text);
		assertTrue(text.contains("§7Powder §f+12,060 §8(corpses 10,550, exit 1,510)"), text);
		assertTrue(text.contains("§7Coins §8no prices yet"), text);

		// sacks minus 1st corpse's onyx + citrine, 2nd came after the last sacks msg (research/07)
		assertEquals(Map.of("Enchanted Glacite", 2L, "Flawed Sapphire Gemstone", 40L, "Flawed Amethyst Gemstone", 40L,
				"Rough Amber Gemstone", 40L, "Rough Topaz Gemstone", 40L, "Rough Jade Gemstone", 40L), aqua.otherDrops());
	}

	// rec 4 (research/08), by hand
	@Test
	void recording4SpawnTimes() throws IOException {
		Path recording = RecordingReplay.RECORDINGS.resolve("2026-11-11_17-16-21.jsonl");
		assumeTrue(Files.exists(recording), "Recording not available: " + recording);
		ShaftTracker replayTracker = new ShaftTracker(TextPatterns.defaults(), event -> journal.onEvent(event, now));
		inMines = false;
		for (JsonObject record : RecordingReplay.read(recording)) {
			now = record.get("t").getAsLong();
			inMines = replayTracker.inMiningArea();
			RecordingReplay.feed(record, replayTracker, journal::onBlockHit);
			if (record.get("type").getAsString().equals("world_change")) journal.onWorldChange(now);
		}

		assertEquals(List.of("PERI_1", "JADE_1", "AMET_1", "TITA_1", "AQUA_C", "ONYX_1"),
				entries.stream().map(JournalEntry::shaftId).toList());
		List<Long> seconds = entries.stream().map(entry -> Math.round(entry.searchMillis() / 1000.0)).toList();
		assertEquals(List.of(10L, 56L, 38L, 157L, 84L, 164L), seconds);
	}

	@Test
	void durations() {
		assertEquals("45s", Format.duration(45_999));
		assertEquals("12m 27s", Format.duration(747_000));
		assertEquals("1h 03m", Format.duration(3_780_000));
	}

	private void replay(Path recording) throws IOException {
		// recs have no block hits, spawns never timed
		for (ShaftEvent event : RecordingReplay.replay(recording)) journal.onEvent(event, 0);
	}

	@Test
	void turkishLocaleStillFindsTheShaftGem() {
		Locale before = Locale.getDefault();
		// turkish locale: "Citrine" uppercases to "CİTRİNE" lol
		Locale.setDefault(Locale.forLanguageTag("tr-TR"));
		try {
			at(0).tracker.onWorldChange(now);
			tracker.onScoreboard(now, List.of("§e§lSKYBLOCK", "§711/11/26 §8m19AB CITR_1"));
			at(5_000).tracker.onChat(now, "§6[Sacks] §a+80§e items§e.§8 (Last 5s.)",
					List.of("§a  +80 §eFlawed Citrine Gemstone§8 (Gemstones Sack)§a"));
			at(15_000).tracker.onWorldChange(now);
		} finally {
			Locale.setDefault(before);
		}
		assertEquals("Citrine", entries.get(0).gems().gem());
		assertEquals(Map.of(), entries.get(0).otherDrops());
	}

	private void visitShaft(long enteredAt, String id) {
		at(enteredAt).tracker.onWorldChange(now);
		tracker.onScoreboard(now, List.of("§e§lSKYBLOCK", "§711/11/26 §8m19AB " + id));
		at(enteredAt + 15_000).tracker.onWorldChange(now);
	}

	private JournalTest at(long time) {
		now = time;
		return this;
	}

	private JournalTest hit(boolean inMiningArea) {
		inMines = inMiningArea;
		journal.onBlockHit(now);
		return this;
	}

	private JournalTest chat(String legacy) {
		tracker.onChat(now, legacy, List.of());
		return this;
	}
}
