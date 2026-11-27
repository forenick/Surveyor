package dev.skysurveyor.settings;

import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import dev.skysurveyor.OldSaves;
import dev.skysurveyor.calc.Fiesta;
import dev.skysurveyor.calc.Layouts;
import dev.skysurveyor.calc.NumberId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CalculatorSettingsTest {
	// nicks config.json as 0.27.0-alpha.2 wrote it, before numbers were kept per fiesta state
	@Test
	void numbersTypedBeforeTheFiestaStatesStay() {
		JsonObject config = JsonParser.parseString(OldSaves.read(OldSaves.ROOT.resolve("0.27.0-alpha.2/config.json"))).getAsJsonObject();
		CalculatorSettings settings = CalculatorSettings.read(config.get("calculator"));
		assertEquals(Map.of("spawn", 100.0, "not_mining", 20.0, "setup", 40.0), settings.typed());
		assertEquals(CalculatorSettings.KeyLoot.WIKI, settings.keyLoot());
	}

	// nicks config.json as 0.27.0-alpha.22 wrote it, before minedShafts
	@Test
	void aFileFromBeforeMinedShaftsGetsTheDefault() {
		JsonObject config = JsonParser.parseString(OldSaves.read(OldSaves.ROOT.resolve("0.27.0-alpha.22/config.json"))).getAsJsonObject();
		CalculatorSettings settings = CalculatorSettings.read(config.get("calculator"));
		assertEquals(CalculatorSettings.MinedShafts.NEWEST_5, settings.minedShafts());
		assertEquals(92.0, settings.typed().get("spawn"));
		assertEquals(20.0, settings.typed().get("back_mined"));
		// the old crystal key moved to its bp group
		assertEquals(0.66, settings.typed().get("efficiency_CRYS_9"));
		assertEquals(7, settings.typed().size());
	}

	@Test
	void minedShaftsSavesAndLoads() {
		CalculatorSettings ten = CalculatorSettings.DEFAULTS.withTyped("spawn", 92.0).withMinedShafts(CalculatorSettings.MinedShafts.NEWEST_10);
		JsonObject json = ConfigFile.GSON.toJsonTree(ten).getAsJsonObject();
		assertEquals("newest_10", json.get("minedShafts").getAsString());
		assertEquals(ten, CalculatorSettings.read(json));
		assertEquals(0, CalculatorSettings.MinedShafts.RANGE.count());
		assertEquals("range", ConfigFile.GSON.toJsonTree(ten.withMinedShafts(CalculatorSettings.MinedShafts.RANGE)).getAsJsonObject().get("minedShafts").getAsString());
		// typed numbers survive a reset of the choice + a bad value
		assertEquals(CalculatorSettings.MinedShafts.NEWEST_5,
				CalculatorSettings.read(JsonParser.parseString("{\"typed\":{\"spawn\":92},\"minedShafts\":\"newest_500\"}")).minedShafts());
		assertEquals(Map.of("spawn", 92.0), CalculatorSettings.read(JsonParser.parseString("{\"typed\":{\"spawn\":92},\"minedShafts\":{\"x\":1}}")).typed());
	}

	@Test
	void keysPerFiestaState() {
		CalculatorSettings settings = CalculatorSettings.read(JsonParser.parseString("{\"typed\":{\"gems_per_block\":110,"
				+ "\"gems_per_block_cole\":140,\"other_flask\":-5,\"coins_per_spawn_flask\":2100000,\"ticks_4000\":0,\"some_later_key\":3}}"));
		// same limits as typing: no negative coins, no 0 ticks. a key from a later version stays
		assertEquals(Map.of("gems_per_block", 110.0, "gems_per_block_cole", 140.0, "coins_per_spawn_flask", 2_100_000.0, "some_later_key", 3.0),
				settings.typed());
		// a typed gems per block from before means no cole
		assertEquals("gems_per_block", Fiesta.typedKey(NumberId.GEMS_PER_BLOCK, Fiesta.State.NONE));
		assertEquals(NumberId.OTHER, Fiesta.idOf("other_flask"));
		assertEquals(null, Fiesta.idOf("efficiency_cole"));
	}

	@Test
	void efficiencyPerLayout() {
		CalculatorSettings settings = CalculatorSettings.read(JsonParser.parseString("{\"typed\":{\"efficiency\":0.6,"
				+ "\"efficiency_PERI_2\":0.66,\"efficiency_JASP_C\":1.4,\"efficiency_ONYX_C\":-0.1}}"));
		// a percent like the one efficiency: 140% and under 0 skipped
		assertEquals(Map.of("efficiency", 0.6, "efficiency_PERI_2", 0.66), settings.typed());
		assertEquals("efficiency_PERI_2", Layouts.typedKey(dev.skysurveyor.calc.GameData.defaults().layout("PERI_2")));
		assertEquals("efficiency_CRYS_9", Layouts.typedKey(dev.skysurveyor.calc.GameData.defaults().layout("PERI_C")));
		assertEquals("CRYS_9", Layouts.layoutOf("efficiency_CRYS_9"));
		assertEquals("PERI_2", Layouts.layoutOf("efficiency_PERI_2"));
		assertEquals(null, Layouts.layoutOf("efficiency"));
		assertEquals(null, Layouts.layoutOf("efficiency_cole"));
		assertEquals(null, Layouts.layoutOf("setup_PERI_2"));
	}

	// alpha.14 to 18 typed crystals per gem, since alpha.19 per bp
	@Test
	void oldCrystalKeysMoveToTheirGroup() {
		assertEquals(Map.of("efficiency_CRYS_9", 0.9), CalculatorSettings.read(JsonParser.parseString("{\"typed\":{\"efficiency_PERI_C\":0.9}}")).typed());
		assertEquals(Map.of("efficiency_CRYS_9", 0.7),
				CalculatorSettings.read(JsonParser.parseString("{\"typed\":{\"efficiency_PERI_C\":0.9,\"efficiency_CRYS_9\":0.7}}")).typed());
		assertEquals(Map.of("efficiency_CRYS_9", 0.7),
				CalculatorSettings.read(JsonParser.parseString("{\"typed\":{\"efficiency_CRYS_9\":0.7,\"efficiency_PERI_C\":0.9}}")).typed());
	}

	// review 4.2 + 4.3
	@Test
	void aBadKeyLootKeepsTheTypedNumbersInOrder() {
		CalculatorSettings settings = CalculatorSettings.read(JsonParser.parseString("{\"typed\":{\"spawn\":100,\"setup\":70,"
				+ "\"efficiency\":0.6,\"freeze\":700},\"keyLoot\":{\"x\":1}}"));
		assertEquals(CalculatorSettings.KeyLoot.WIKI, settings.keyLoot());
		assertEquals(java.util.List.of("spawn", "setup", "efficiency", "freeze"), java.util.List.copyOf(settings.typed().keySet()));
	}
}
