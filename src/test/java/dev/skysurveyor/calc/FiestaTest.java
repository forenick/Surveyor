package dev.skysurveyor.calc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import dev.skysurveyor.calc.Fiesta.Reading;
import dev.skysurveyor.calc.Fiesta.Source;
import dev.skysurveyor.calc.Fiesta.State;
import dev.skysurveyor.detection.ItemCount;
import dev.skysurveyor.detection.TextPatterns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FiestaTest {
	private static final long HOUR = 3_600_000L;
	// rec 5 (11-15), private island
	private static final List<String> COLE_TAB = List.of("", "§e§lElection: §cOver!", "§r §fMayor: §aMarina", "§r §fMinister: §aCole",
			"§r §fYear: §d526", "");
	// rec 5 line w/ cole as mayor, made up
	private static final List<String> MAYOR_COLE_TAB = List.of("", "§e§lElection: §cOver!", "§r §fMayor: §aCole", "§r §fMinister: §aMarina",
			"§r §fYear: §d526", "");
	// rec 2026-11-21_19-12-23, hub
	private static final List<String> NO_COLE_TAB = List.of("", "§e§lElection: §cOver!", "§r §fMayor: §aAatrox", "§r §fMinister: §aDiaz",
			"§r §fYear: §d527", "");
	// mines, no election widget (every rec)
	private static final List<String> MINES_TAB = List.of("§b§lArea: §7Dwarven Mines", "§r Server: §8mini107F", "§e§lPet:");
	// rec 2 sacks hover at 417 s: "+1 Glossy Gemstone (Mining Sack, Events Sack)"
	private static final List<ItemCount> GLOSSY = List.of(new ItemCount("Glacite", 4400), new ItemCount("Glossy Gemstone", 1));

	private final Fiesta fiesta = new Fiesta(TextPatterns.defaults());

	// hypixel api 11-26: mayor diaz, minister diana w/ huntress' intuition
	private static JsonObject election() throws IOException {
		return JsonParser.parseString(Files.readString(Path.of("src/test/resources/election/2026-11-26.json"), StandardCharsets.UTF_8))
				.getAsJsonObject();
	}

	@Test
	void nothingReadIsUnknown() {
		assertNull(fiesta.reading(0).on());
		assertNull(fiesta.state(0, true));
	}

	@Test
	void electionWithoutThePerkIsOff() throws IOException {
		fiesta.onElection(1_000, election());
		assertEquals(new Reading(false, Source.ELECTION, 1_000), fiesta.reading(2_000));
		assertEquals(State.NONE, fiesta.state(2_000, true), "a flask does nothing w/o the fiesta");
	}

	// no cole answer saved yet (research/11 s14), real one w/ the perk swapped
	@Test
	void aMinisterWithThePerkIsOn() throws IOException {
		JsonObject json = election();
		json.getAsJsonObject("mayor").getAsJsonObject("minister").getAsJsonObject("perk").addProperty("name", "Mining Fiesta");
		fiesta.onElection(1_000, json);
		assertEquals(State.COLE, fiesta.state(2_000, false));
		assertEquals(State.FLASK, fiesta.state(2_000, true));
	}

	@Test
	void aMayorWithThePerkIsOn() throws IOException {
		JsonObject json = election();
		json.getAsJsonObject("mayor").getAsJsonArray("perks").get(1).getAsJsonObject().addProperty("name", "Mining Fiesta");
		assertTrue(Fiesta.perk(json, TextPatterns.defaults()));
	}

	// last elections candidates are in there too, not in office
	@Test
	void candidatesDontCount() throws IOException {
		JsonObject json = election();
		json.getAsJsonObject("mayor").getAsJsonObject("election").getAsJsonArray("candidates").get(0).getAsJsonObject()
				.getAsJsonArray("perks").get(0).getAsJsonObject().addProperty("name", "Mining Fiesta");
		assertFalse(Fiesta.perk(json, TextPatterns.defaults()));
	}

	@Test
	void anErrorBodyChangesNothing() throws IOException {
		fiesta.onElection(1_000, election());
		fiesta.onElection(2_000, JsonParser.parseString("{\"success\":false,\"cause\":\"Key throttle\"}").getAsJsonObject());
		assertEquals(1_000, fiesta.reading(3_000).at());
	}

	@Test
	void anOldElectionAnswerNoLongerCounts() throws IOException {
		fiesta.onElection(0, election());
		assertFalse(fiesta.reading(Fiesta.ELECTION_MAX_AGE).on());
		assertNull(fiesta.reading(Fiesta.ELECTION_MAX_AGE + 1).on());
	}

	@Test
	void tabListKnowsWhoIsInOffice() {
		fiesta.onTabList(1_000, MAYOR_COLE_TAB);
		assertEquals(new Reading(true, Source.TAB_LIST, 1_000), fiesta.reading(2_000));
		fiesta.onTabList(3_000, NO_COLE_TAB);
		assertEquals(new Reading(false, Source.TAB_LIST, 3_000), fiesta.reading(4_000));
	}

	@Test
	void ministerColeSaysNothing() {
		fiesta.onTabList(1_000, COLE_TAB);
		assertNull(fiesta.reading(2_000).on());
		fiesta.onTabList(3_000, NO_COLE_TAB);
		fiesta.onTabList(4_000, COLE_TAB);
		assertNull(fiesta.reading(5_000).on(), "an older off isnt kept either");
	}

	@Test
	void aTabListWithoutTheWidgetChangesNothing() {
		fiesta.onTabList(1_000, MAYOR_COLE_TAB);
		fiesta.onTabList(2_000, MINES_TAB);
		assertEquals(new Reading(true, Source.TAB_LIST, 1_000), fiesta.reading(3_000));
		assertNull(fiesta.reading(1_000 + Fiesta.OTHER_MAX_AGE + 1).on());
	}

	@Test
	void aGlossyGemstoneProvesIt() {
		fiesta.onSacks(1_000, List.of(new ItemCount("Rough Jade Gemstone", 4551)));
		assertNull(fiesta.reading(2_000).on());
		fiesta.onSacks(3_000, GLOSSY);
		assertEquals(new Reading(true, Source.DROP, 3_000), fiesta.reading(4_000));
		fiesta.onSacks(5_000, List.of(new ItemCount("Refined Mineral", 1)));
		assertEquals(5_000, fiesta.reading(6_000).at());
	}

	@Test
	void electionBeatsTheRestWhileFresh() throws IOException {
		fiesta.onTabList(1_000, COLE_TAB);
		fiesta.onSacks(1_500, GLOSSY);
		fiesta.onElection(2_000, election());
		assertEquals(Source.ELECTION, fiesta.reading(3_000).source());
		// refresh failing 2 h+: back to the drop
		assertEquals(Source.DROP, fiesta.reading(2_000 + Fiesta.ELECTION_MAX_AGE + 1).source());
	}

	@Test
	void aDropBeatsAnOlderTabListAndTheOtherWayRound() {
		fiesta.onTabList(1_000, NO_COLE_TAB);
		fiesta.onSacks(2_000, GLOSSY);
		assertTrue(fiesta.reading(3_000).on());
		fiesta.onTabList(4_000, NO_COLE_TAB);
		assertFalse(fiesta.reading(5_000).on(), "a new term");
		fiesta.onTabList(6_000, COLE_TAB);
		assertEquals(Source.DROP, fiesta.reading(7_000).source(), "the drop names the perk, the tab list doesnt");
	}

	@Test
	void flaskTimeCountsInsideAShaftOnly() {
		fiesta.onTick(0, false, true);
		fiesta.onTick(50, false, true);
		assertEquals(0, fiesta.flaskMillis());
		fiesta.onShaftEntered();
		fiesta.onTick(100, true, true);
		fiesta.onTick(150, true, true);
		fiesta.onTick(200, true, false);
		assertEquals(100, fiesta.flaskMillis());
		// client freeze isnt flask time
		fiesta.onTick(30_200, true, true);
		assertEquals(1_100, fiesta.flaskMillis());
		fiesta.onShaftEntered();
		assertEquals(0, fiesta.flaskMillis());
	}

	@Test
	void savedShaftStates() {
		long shaft = 12 * 60_000L;
		assertNull(Fiesta.of(null, null, shaft), "before 0.27.0-alpha.3");
		assertEquals(State.NONE, Fiesta.of(false, shaft, shaft));
		assertEquals(State.COLE, Fiesta.of(true, 0L, shaft));
		assertEquals(State.COLE, Fiesta.of(true, shaft / 10, shaft));
		assertEquals(State.FLASK, Fiesta.of(true, shaft, shaft));
		assertEquals(State.FLASK, Fiesta.of(true, shaft * 9 / 10, shaft));
		assertNull(Fiesta.of(true, shaft / 2, shaft), "the flask ran out halfway");
	}

	@Test
	void typedKeys() {
		assertEquals("gems_per_block", Fiesta.typedKey(NumberId.GEMS_PER_BLOCK, State.NONE));
		assertEquals("gems_per_block", Fiesta.typedKey(NumberId.GEMS_PER_BLOCK, null));
		assertEquals("gems_per_block_cole", Fiesta.typedKey(NumberId.GEMS_PER_BLOCK, State.COLE));
		assertEquals("other_flask", Fiesta.typedKey(NumberId.OTHER, State.FLASK));
		assertEquals("efficiency", Fiesta.typedKey(NumberId.EFFICIENCY, State.FLASK));
	}

	@Test
	void tileText() {
		assertEquals("§eUnknown", FiestaText.tile(Reading.UNKNOWN, null));
		assertEquals("§7Off", FiestaText.tile(new Reading(false, Source.ELECTION, 0), 40 * 60_000L));
		assertEquals("§aOn", FiestaText.tile(new Reading(true, Source.DROP, 0), null));
		assertEquals("§aOn + flask §742m", FiestaText.tile(new Reading(true, Source.ELECTION, 0), 42 * 60_000L + 59_000));
		assertEquals("off (election, 4m ago)", FiestaText.debug(new Reading(false, Source.ELECTION, 0), null, 4 * 60_000L));
		assertEquals("§8From Hypixel's election data, 2h ago", FiestaText.tip(new Reading(false, Source.ELECTION, 0), null, 2 * HOUR).getLast());
	}
}
