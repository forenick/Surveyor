package dev.skysurveyor.detection;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

// fixtures = real snapshots from recordings
class HudParserTest {
	private static final List<String> SHAFT_SCOREBOARD = List.of(
			"§e§lSKYBLOCK",
			"§8§711/08/26 §8m11§8§u§8§87BB JADE_1",
			"§f  §f§t",
			"§f Late Winter 20t§f§s§fh",
			"§7 §775:50am§7§q",
			"§b §7 §bGlacite M§b§p§b§bineshafts",
			"§f      §f§j",
			"§b§fCold: §b-4❄§b§i",
			"§f        §f§h",
			"§e§ewww.hypixel.ne§e§g§e§et");

	private static final List<String> TUNNELS_SCOREBOARD = List.of(
			"§e§lSKYBLOCK",
			"§f  §f§!",
			"§f Late Winter 19t§f§z§fh",
			"§3 §77:30am §3§3§y",
			"§b §7 §bGlacite T§b§x§b§bunnels",
			"§f      §f§w",
			"§fCold: §b-1❄§b§v",
			"§e§ewww.hypixel.ne§e§g§e§et");

	private static final List<String> SHAFT_TAB = List.of(
			"§r               §3§lInfo",
			"§b§lArea: §7Mineshaft",
			"§r Server: §8mini70DC",
			"§r Scrap: §b0",
			"§r Glacite: §b45,553,105",
			"",
			"§b§lFrozen Corpses:",
			"§r §9Lapis§f: §c§lNOT LOOTED",
			"§r §9Lapis§f: §a§lLOOTED",
			"§r §6Umber§f: §c§lNOT LOOTED",
			"§r §7Tungsten§f: §c§lNOT LOOTED",
			"",
			"");

	private final ScoreboardParser scoreboard = new ScoreboardParser(TextPatterns.defaults());
	private final TabListParser tabList = new TabListParser(TextPatterns.defaults());

	@Test
	void shaftIdIgnoresJunkCodesInServerName() {
		assertEquals(new ShaftId("JADE_1", "JADE", "1"), scoreboard.shaftId(SHAFT_SCOREBOARD));
		assertEquals(new ShaftId("AQUA_C", "AQUA", "C"),
				scoreboard.shaftId(List.of("§8§711/08/26 §8m26DB AQUA_C")));
		assertEquals(new ShaftId("LITT_L", "LITT", "L"),
				scoreboard.shaftId(List.of("§8§711/08/26 §8m8§8§u§8§8DJ LITT_L")));
	}

	@Test
	void noShaftIdOutsideShafts() {
		assertNull(scoreboard.shaftId(List.of("§8§711/08/26 §8m10§8§z§8§89DE")));
		assertNull(scoreboard.shaftId(TUNNELS_SCOREBOARD));
	}

	@Test
	void coldValue() {
		assertEquals(-4, scoreboard.cold(SHAFT_SCOREBOARD));
		assertEquals(-1, scoreboard.cold(TUNNELS_SCOREBOARD));
		assertEquals(0, scoreboard.cold(List.of("§b§fCold: §b0❄§b§i")));
		assertNull(scoreboard.cold(List.of("§fPurse: §6371,724§6§t§6§6,588")));
	}

	@Test
	void mineshaftArea() {
		assertTrue(tabList.inMineshaft(SHAFT_TAB));
		assertFalse(tabList.inMineshaft(List.of("§b§lArea: §7Dwarven Mines")));
	}

	@Test
	void corpseWidget() {
		assertEquals(List.of(
				new Corpse("Lapis", false),
				new Corpse("Lapis", true),
				new Corpse("Umber", false),
				new Corpse("Tungsten", false)), tabList.corpses(SHAFT_TAB));
		assertEquals(List.of(), tabList.corpses(List.of("§b§lArea: §7Mineshaft", "§r Scrap: §b0")));
	}

	// real lines, recordings 1-5
	@Test
	void pityWidget() {
		assertEquals(new TabListParser.Pity(388, 2_000),
				tabList.pity(List.of("§d§lPity:", "§r §bGlacite Mineshafts§f: §e388§7/§a2,000")));
		assertEquals(new TabListParser.Pity(0, 2_000), tabList.pity(List.of("§r §bGlacite Mineshafts§f: §c0§7/§a2,000")));
		assertEquals(new TabListParser.Pity(1_498, 2_000), tabList.pity(List.of("§r §bGlacite Mineshafts§f: §e1,498§7/§a2,000")));
		assertNull(tabList.pity(SHAFT_TAB));
	}

	@Test
	void everyRecordedPityLineParses() throws IOException {
		Path dir = RecordingReplay.RECORDINGS;
		assumeTrue(Files.isDirectory(dir), "Recordings not available: " + dir);
		List<Path> files;
		try (Stream<Path> list = Files.list(dir)) {
			files = list.filter(file -> file.toString().endsWith(".jsonl")).sorted().toList();
		}
		int seen = 0;
		for (Path file : files) {
			for (JsonObject record : RecordingReplay.read(file)) {
				if (!RecordingReplay.type(record).equals("tablist")) continue;
				for (String line : RecordingReplay.strings(record.getAsJsonArray("lines"))) {
					if (!TextPatterns.clean(line).startsWith(" Glacite Mineshafts")) continue;
					assertNotNull(tabList.pity(List.of(line)), file.getFileName() + ": " + line);
					seen++;
				}
			}
		}
		assumeTrue(seen > 0, "no pity lines recorded");
	}
}
