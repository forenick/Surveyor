package dev.skysurveyor.journal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.hud.HudData;
import dev.skysurveyor.mining.MiningStats;
import dev.skysurveyor.money.ItemPrice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JournalFileTest {
	// lines 12 and 4 of old-saves/0.22.0-alpha.10/journal.jsonl
	private static final String JADE = "{\"version\":1,\"sessionId\":1794174082851,\"shaftId\":\"JADE_1\",\"enteredAt\":1794174160153,"
			+ "\"durationMillis\":7296,\"exit\":\"BYE\",\"exitPowder\":1000,\"searchMillis\":36069,\"corpses\":[{\"type\":\"Umber\","
			+ "\"looted\":false},{\"type\":\"Tungsten\",\"looted\":false},{\"type\":\"Tungsten\",\"looted\":false},{\"type\":\"Tungsten\","
			+ "\"looted\":false}],\"extraCorpses\":1,\"loots\":[],\"mayhemBuffs\":[\"Cold Resistance\"],\"campfiresAt\":[],\"coldAtExit\":0,"
			+ "\"scrapFound\":0,\"pristineProcs\":0,\"pristineItems\":{},\"sacksAdded\":{},\"sacksRemoved\":{},\"crystals\":[],"
			+ "\"otherDrops\":{},\"prices\":{},\"campfireColds\":[]}";
	private static final String TOPAZ = "{\"version\":1,\"sessionId\":1794174082851,\"shaftId\":\"TOPA_1\",\"enteredAt\":1794174760566,"
			+ "\"durationMillis\":6753,\"exit\":\"BYE\",\"exitPowder\":1000,\"searchMillis\":21840,\"corpses\":[{\"type\":\"Lapis\","
			+ "\"looted\":false},{\"type\":\"Umber\",\"looted\":false},{\"type\":\"Tungsten\",\"looted\":false}],\"extraCorpses\":1,"
			+ "\"loots\":[],\"mayhemBuffs\":[\"Suspicious Scrap chance\"],\"campfiresAt\":[],\"coldAtExit\":0,\"scrapFound\":0,"
			+ "\"pristineProcs\":0,\"pristineItems\":{},\"sacksAdded\":{},\"sacksRemoved\":{},\"crystals\":[],\"otherDrops\":{},"
			+ "\"prices\":{},\"campfireColds\":[]}";
	// line 1 of old-saves/0.2.x/journal.jsonl, oldest format
	private static final String PERIDOT_0_2 = "{\"version\":1,\"sessionId\":1794413789542,\"shaftId\":\"PERI_1\",\"enteredAt\":1794413802805,"
			+ "\"durationMillis\":6709,\"exit\":\"BYE\",\"exitPowder\":1000,\"searchMillis\":8889,\"corpses\":[{\"type\":\"Umber\","
			+ "\"looted\":false},{\"type\":\"Umber\",\"looted\":false},{\"type\":\"Umber\",\"looted\":false}],\"extraCorpses\":0,"
			+ "\"loots\":[],\"mayhemBuffs\":[\"Mining Fortune\"],\"campfiresAt\":[],\"coldAtExit\":0,\"scrapFound\":0,\"pristineProcs\":0,"
			+ "\"pristineItems\":{},\"sacksAdded\":{},\"sacksRemoved\":{}}";

	@Test
	void linesMissingFieldsAreSkippedAndKept(@TempDir Path dir) throws IOException {
		Path path = dir.resolve("journal.jsonl");
		// topaz line cut by hand after exitPowder
		String cut = TOPAZ.substring(0, TOPAZ.indexOf(",\"searchMillis\"")) + "}";
		String noExit = JADE.replace("\"exit\":\"BYE\",", "");
		String text = String.join("\n", JADE, "{}", cut, "{\"corpses\":null,\"loots\":[]}", noExit, PERIDOT_0_2, TOPAZ) + "\n";
		Files.writeString(path, text, StandardCharsets.UTF_8);

		List<JournalEntry> entries = new JournalFile(path, Runnable::run).read();
		assertEquals(List.of("JADE_1", "PERI_1", "TOPA_1"), entries.stream().map(JournalEntry::shaftId).toList());
		// what crashed before: hud refresh + journal open
		JournalStats.allTime(entries);
		HudData.Totals.of(entries, TextPatterns.defaults());
		assertEquals(text, Files.readString(path, StandardCharsets.UTF_8), "read never changes the file");
	}

	// threw past the per line catch, whole load failed = empty journal
	@Test
	void badValuesOnlyLoseTheirOwnLine(@TempDir Path dir) throws IOException {
		Path path = dir.resolve("journal.jsonl");
		String textPrice = JADE.replace("\"prices\":{}", "\"prices\":{\"JADE\":[\"abc\",12.5],\"ROUGH_JADE_GEM\":{\"buyOrder\":\"x\",\"sellOffer\":\"3\"}}");
		String wrongType = TOPAZ.replace("\"durationMillis\":6753", "\"durationMillis\":\"long\"");
		String wrongShape = TOPAZ.replace("\"sacksAdded\":{}", "\"sacksAdded\":[1]");
		String torn = TOPAZ.substring(0, 300);
		String text = String.join("\n", JADE, textPrice, wrongType, wrongShape, PERIDOT_0_2, torn);
		Files.writeString(path, text, StandardCharsets.UTF_8);

		JournalFile file = new JournalFile(path, Runnable::run);
		List<JournalEntry> entries = file.load().join();
		assertEquals(List.of("JADE_1", "JADE_1", "PERI_1"), entries.stream().map(JournalEntry::shaftId).toList());
		assertEquals(new ItemPrice(null, 12.5, null, null), entries.get(1).prices().get("JADE"), "text that isnt a number = no price");
		assertEquals(new ItemPrice(null, 3.0, null, null), entries.get(1).prices().get("ROUGH_JADE_GEM"), "a number as text still reads");
		assertEquals(text, Files.readString(path, StandardCharsets.UTF_8), "load never changes the file");

		file.append(EntryBuilder.from(entries.getFirst().withPrices(null)).shaftId("AMBE_1").enteredAt(1794175000000L).build());
		String now = Files.readString(path, StandardCharsets.UTF_8);
		assertTrue(now.startsWith(text + "\n{"), "append only adds, bad lines stay");
		assertEquals(List.of("JADE_1", "JADE_1", "PERI_1", "AMBE_1"), file.load().join().stream().map(JournalEntry::shaftId).toList());
	}

	// 0.27.0-alpha.3: fiesta, flaskMillis, spawnBlock behind fossil, boostBlocks last in mining
	@Test
	void newFieldsGoLastAndComeBack(@TempDir Path dir) throws IOException {
		Path path = dir.resolve("journal.jsonl");
		Files.writeString(path, JADE + "\n", StandardCharsets.UTF_8);
		JournalFile file = new JournalFile(path, Runnable::run);
		JournalEntry old = file.load().join().getFirst();
		assertEquals(null, old.fiesta());
		assertEquals(null, old.flaskMillis());
		assertEquals(null, old.spawnBlock());
		MiningStats mining = new MiningStats(700_000, 60_000, 1_000, 100, "green_stained_glass", 11.0, 4.0, 140_000, 0, 11.0, 380);
		file.append(EntryBuilder.from(old.withPrices(null)).shaftId("PERI_1").mining(mining).fiesta(true, 420_000L)
				.spawnBlock("green_stained_glass").build());
		String line = Files.readAllLines(path, StandardCharsets.UTF_8).get(1);
		assertTrue(line.endsWith(",\"boostBlocks\":380},\"fiesta\":true,\"flaskMillis\":420000,\"spawnBlock\":\"green_stained_glass\"}"), line);
		JournalEntry back = file.load().join().get(1);
		assertEquals(true, back.fiesta());
		assertEquals(420_000L, back.flaskMillis());
		assertEquals("green_stained_glass", back.spawnBlock());
		assertEquals(380, back.mining().boostBlocks());
	}

	record Probe(String name) {
		Probe {
			if (name.equals("boom")) throw new IllegalStateException("boom");
		}
	}

	@Test
	void aLineThatThrowsAnythingOnlyLosesItself(@TempDir Path dir) throws IOException {
		RuntimeException thrown = assertThrows(RuntimeException.class, () -> new Gson().fromJson("{\"name\":\"boom\"}", Probe.class));
		assertFalse(thrown instanceof JsonParseException, "else this test checks nothing new: " + thrown);
		Path path = dir.resolve("probe.jsonl");
		Files.writeString(path, "{\"name\":\"a\"}\n{\"name\":\"boom\"}\n{}\n{\"name\":\"b\"}\n", StandardCharsets.UTF_8);
		JsonLinesFile<Probe> file = new JsonLinesFile<>(path, Probe.class, probe -> true, Runnable::run);
		assertEquals(List.of("a", "b"), file.load().join().stream().map(Probe::name).toList());
		assertFalse(file.failed(), "skipped lines arent a failed read");
	}

	// first run hint waits on this, never for a player w/ shafts
	@Test
	void unreadableFileCountsAsFailed(@TempDir Path dir) throws IOException {
		JournalFile missing = new JournalFile(dir.resolve("none.jsonl"), Runnable::run);
		assertTrue(missing.load().join().isEmpty());
		assertFalse(missing.failed(), "no file yet = new player");

		Path folder = Files.createDirectory(dir.resolve("journal.jsonl"));
		JournalFile broken = new JournalFile(folder, Runnable::run);
		assertTrue(broken.load().join().isEmpty());
		assertTrue(broken.failed());
	}

	@Test
	void badUtf8OnlyLosesItsOwnLine(@TempDir Path dir) throws IOException {
		Path path = dir.resolve("journal.jsonl");
		// write died inside a ❄ (e2 9d 84)
		byte[] torn = {'{', '"', 'x', '"', ':', '"', (byte) 0xE2, (byte) 0x9D};
		byte[] jade = (JADE + "\n").getBytes(StandardCharsets.UTF_8);
		byte[] topaz = ("\n" + TOPAZ + "\n").getBytes(StandardCharsets.UTF_8);
		byte[] all = new byte[jade.length + torn.length + topaz.length];
		System.arraycopy(jade, 0, all, 0, jade.length);
		System.arraycopy(torn, 0, all, jade.length, torn.length);
		System.arraycopy(topaz, 0, all, jade.length + torn.length, topaz.length);
		Files.write(path, all);

		assertEquals(List.of("JADE_1", "TOPA_1"), new JournalFile(path, Runnable::run).read().stream().map(JournalEntry::shaftId).toList());
	}

	@Test
	void appendAfterCutOffLineStartsNewLine(@TempDir Path dir) throws IOException {
		Path path = dir.resolve("journal.jsonl");
		// write died halfway through the topaz line
		String torn = JADE + "\n" + TOPAZ.substring(0, 300);
		Files.writeString(path, torn, StandardCharsets.UTF_8);

		JournalFile file = new JournalFile(path, Runnable::run);
		JournalEntry next = file.read().get(0).withPrices(null);
		file.append(EntryBuilder.from(next).shaftId("AMBE_1").enteredAt(1794175000000L).build());

		assertEquals(List.of("JADE_1", "AMBE_1"), file.read().stream().map(JournalEntry::shaftId).toList());
		String now = Files.readString(path, StandardCharsets.UTF_8);
		assertTrue(now.startsWith(torn + "\n{"), "cut off line stays, next shaft on its own line");
		assertTrue(now.endsWith("}\n"));
	}

	@Test
	void appendAddsNoBlankLines(@TempDir Path dir) throws IOException {
		Path path = dir.resolve("skysurveyor").resolve("journal.jsonl");
		JournalFile file = new JournalFile(path, Runnable::run);
		file.append(EntryBuilder.entry().shaftId("JADE_1").build());
		file.append(EntryBuilder.entry().shaftId("TOPA_1").build());

		List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
		assertEquals(2, lines.size());
		assertTrue(lines.stream().noneMatch(String::isBlank));
		assertEquals(2, file.read().size());
	}
}
