package dev.skysurveyor.recorder;

import java.util.List;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import dev.skysurveyor.detection.TextPatterns;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NameMaskTest {
	@Test
	void ownNameIsYouOthersNumbered() {
		NameMask mask = new NameMask(Set.of());
		mask.update("Steve_1", List.of("Alex", "Steve_1", "Notch"));
		// lines from recording 6, name swapped
		assertEquals("[MVP+] You entered Glacite Mineshafts!", mask.apply("[MVP+] Steve_1 entered Glacite Mineshafts!"));
		assertEquals("§b[MVP+] You's Mineshaft Portal", mask.apply("§b[MVP+] Steve_1's Mineshaft Portal"));
		assertEquals("§7[312] §aPlayer1 §7and Player2", mask.apply("§7[312] §aAlex §7and Notch"));
	}

	@Test
	void sameMaskAllFileLongAndNewNamesGetTheNext() {
		NameMask mask = new NameMask(Set.of());
		mask.update(null, List.of("Alex"));
		mask.update(null, List.of("Notch", "Alex"));
		assertEquals("Player1 Player2", mask.apply("Alex Notch"));
		mask.reset();
		mask.update(null, List.of("Notch"));
		assertEquals("Player1", mask.apply("Notch"));
	}

	@Test
	void partsOfWordsAndGameWordsStay() {
		NameMask mask = new NameMask(Set.of("Jasper"));
		mask.update(null, List.of("Ab", "jasper", "Alex", "!A-a", "Al"));
		assertEquals("Alexander Jasper Gemstone Player1", mask.apply("Alexander Jasper Gemstone Alex"));
		assertEquals("Ab", mask.apply("Ab"), "too short to be a name");
	}

	@Test
	void longerNameWinsOverItsStart() {
		NameMask mask = new NameMask(Set.of());
		mask.update(null, List.of("Alex", "Alex_2"));
		assertEquals("Player2 Player1", mask.apply("Alex_2 Alex"));
	}

	@Test
	void everyStringInAJsonLine() {
		NameMask mask = new NameMask(Set.of());
		mask.update("Alex", List.of());
		JsonObject line = JsonParser.parseString("{\"type\":\"tablist\",\"lines\":[\"[100] Alex\",\"x\"],\"stand\":{\"name\":\"Alex's Portable Campfire\"},"
				+ "\"t\":5}").getAsJsonObject();
		mask.apply(line);
		assertEquals("{\"type\":\"tablist\",\"lines\":[\"[100] You\",\"x\"],\"stand\":{\"name\":\"You's Portable Campfire\"},\"t\":5}",
				line.toString());
	}

	@Test
	void lineStructureStays() {
		NameMask mask = new NameMask(Set.of());
		mask.update(null, List.of("mark", "live"));
		JsonObject line = JsonParser.parseString("{\"type\":\"mark\",\"auto\":\"live\",\"note\":\"mark live\"}").getAsJsonObject();
		mask.apply(line);
		assertEquals("{\"type\":\"mark\",\"auto\":\"live\",\"note\":\"Player1 Player2\"}", line.toString());
	}

	@Test
	void rankedNamesFromOtherLobbiesMaskedInTheWholeLine() {
		NameMask mask = new NameMask(Set.of());
		mask.update("Steve_1", List.of());
		// recording 3 line, name swapped. text 1st, legacy after
		JsonObject line = JsonParser.parseString("{\"type\":\"chat\",\"text\":\"Party > Alex_9 hi\","
				+ "\"legacy\":\"§9The party leader, §b[MVP§c+§b] Alex_9 §ehas disconnected\"}").getAsJsonObject();
		mask.apply(line);
		assertEquals("Party > Player1 hi", line.get("text").getAsString());
		assertEquals("§9The party leader, §b[MVP§c+§b] Player1 §ehas disconnected", line.get("legacy").getAsString());
		assertEquals("[NPC] Kalhuiki", mask.apply("[NPC] Kalhuiki"), "npcs arent ranks");
	}

	// made up rank, a patterns.txt override adds it w/o a release
	@Test
	void anOverrideAddsARank() {
		TextPatterns patterns = TextPatterns.builtInWith(List.of("recorder.ranked \\[NEWRANK] (?<name>[A-Za-z0-9_]{3,16})"));
		NameMask mask = new NameMask(Set.of(), patterns);
		JsonObject line = JsonParser.parseString("{\"type\":\"chat\",\"legacy\":\"§d[NEWRANK] Alex_9 §ejoined the party\"}").getAsJsonObject();
		mask.apply(line);
		assertEquals("§d[NEWRANK] Player1 §ejoined the party", line.get("legacy").getAsString());
	}

	@Test
	void nameRightAfterAColourCode() {
		NameMask mask = new NameMask(Set.of());
		mask.update(null, List.of("Alex"));
		assertEquals("§bPlayer1 §#a1b2c3Player1 bAlex", mask.apply("§bAlex §#a1b2c3Alex bAlex"));
	}

	@Test
	void realVocabularyKeepsGameWords() {
		NameMask mask = NameMask.defaults();
		mask.update(null, List.of("Jasper", "Umber", "Alex"));
		assertEquals("Jasper Umber Player1", mask.apply("Jasper Umber Alex"));
	}
}
