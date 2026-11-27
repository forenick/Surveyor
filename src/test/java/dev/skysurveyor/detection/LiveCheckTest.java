package dev.skysurveyor.detection;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import dev.skysurveyor.detection.RecordingReplay.LiveCheck;
import dev.skysurveyor.recorder.RecordingFiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LiveCheckTest {
	// rec 4
	private static final String PICKOBULUS = "{\"t\":%d,\"type\":\"chat\",\"legacy\":\"§aYou used your §6Pickobulus §aPickaxe Ability!\"}";

	@Test
	void startsAtTheFirstWorldChange() {
		LiveCheck check = RecordingReplay.liveCheck(records(
				"{\"t\":0,\"type\":\"start\",\"live\":true}",
				PICKOBULUS.formatted(10),
				"{\"t\":10,\"type\":\"live\",\"event\":\"PickaxeAbility[ability=Pickobulus]\"}",
				"{\"t\":100,\"type\":\"world_change\"}",
				"{\"t\":101,\"type\":\"live\",\"event\":\"ShaftLeft[TOPA_1 BYE +0 powder]\"}",
				PICKOBULUS.formatted(500),
				"{\"t\":500,\"type\":\"live\",\"event\":\"PickaxeAbility[ability=Pickobulus]\"}"));
		assertTrue(check.hasLive());
		assertEquals(1, check.live().size());
		assertEquals(-1, check.firstDifference());
	}

	@Test
	void findsTheFirstDifference() {
		LiveCheck check = RecordingReplay.liveCheck(records(
				"{\"t\":0,\"type\":\"start\",\"live\":true}",
				"{\"t\":100,\"type\":\"world_change\"}",
				PICKOBULUS.formatted(500),
				"{\"t\":500,\"type\":\"live\",\"event\":\"PickaxeAbility[ability=Mining Speed Boost]\"}",
				PICKOBULUS.formatted(900)));
		assertEquals(0, check.firstDifference());
	}

	@Test
	void missingLiveEventIsADifference() {
		LiveCheck check = RecordingReplay.liveCheck(records(
				"{\"t\":0,\"type\":\"start\",\"live\":true}",
				"{\"t\":100,\"type\":\"world_change\"}",
				PICKOBULUS.formatted(500)));
		assertEquals(0, check.firstDifference());
	}

	// /sv debug reload after the 1st ability, 2nd line needs the override
	@Test
	void aReloadSwapsThePatternsFromThere() {
		String reworded = "{\"t\":%d,\"type\":\"chat\",\"legacy\":\"§aYou activated §6Pickobulus§a!\"}";
		LiveCheck check = RecordingReplay.liveCheck(records(
				"{\"t\":0,\"type\":\"start\",\"live\":true}",
				"{\"t\":100,\"type\":\"world_change\"}",
				reworded.formatted(300),
				"{\"t\":400,\"type\":\"patterns\",\"patternOverrides\":[\"ability.used ^You activated (?<ability>.+)!$\"]}",
				reworded.formatted(500),
				"{\"t\":500,\"type\":\"live\",\"event\":\"PickaxeAbility[ability=Pickobulus]\"}"));
		assertEquals(-1, check.firstDifference());
		assertEquals(1, check.replayed().size());
	}

	// rec 6 at 0:55.9, alpha.13, before RareDrop
	private static final String RARE_DROP = "{\"t\":%d,\"type\":\"chat\","
			+ "\"legacy\":\"§6§lRARE DROP! §9 Fine Citrine Gemstone §b(§b+103 Magic Find§b)\"}";
	private static final String RARE_DROP_LIVE = "{\"t\":%d,\"type\":\"live\","
			+ "\"event\":\"RareDrop[item=ItemCount[item=Fine Citrine Gemstone, count=1]]\"}";

	@Test
	void olderRecordingsSkipNewerEvents() {
		LiveCheck check = RecordingReplay.liveCheck(records(
				"{\"t\":0,\"type\":\"start\",\"live\":true}",
				"{\"t\":100,\"type\":\"world_change\"}",
				RARE_DROP.formatted(500),
				PICKOBULUS.formatted(600),
				"{\"t\":600,\"type\":\"live\",\"event\":\"PickaxeAbility[ability=Pickobulus]\"}"));
		assertEquals(-1, check.firstDifference());
		assertEquals(1, check.replayed().size());
	}

	@Test
	void newerRecordingsCheckThem() {
		String start = "{\"t\":0,\"type\":\"start\",\"live\":true,\"version\":\"0.25.0-alpha.16\"}";
		LiveCheck missing = RecordingReplay.liveCheck(records(start, "{\"t\":100,\"type\":\"world_change\"}", RARE_DROP.formatted(500)));
		assertEquals(0, missing.firstDifference());
		LiveCheck logged = RecordingReplay.liveCheck(records(start, "{\"t\":100,\"type\":\"world_change\"}", RARE_DROP.formatted(500),
				RARE_DROP_LIVE.formatted(500)));
		assertEquals(-1, logged.firstDifference());
	}

	@Test
	void versionOrder() {
		assertTrue(RecordingReplay.compareVersions("0.25.0-alpha.16", "0.25.0-alpha.9") > 0);
		assertTrue(RecordingReplay.compareVersions("0.25.0", "0.25.0-alpha.16") > 0);
		assertTrue(RecordingReplay.compareVersions("0.25.0-beta.1", "0.25.0-alpha.16") > 0);
		assertTrue(RecordingReplay.compareVersions("0.24.0", "0.25.0-alpha.16") < 0);
		assertEquals(0, RecordingReplay.compareVersions("0.25.0-alpha.16", "0.25.0-alpha.16"));
	}

	@Test
	void oldRecordingsHaveNoLiveLines() {
		assertFalse(RecordingReplay.liveCheck(records("{\"t\":0,\"type\":\"start\"}", "{\"t\":12,\"type\":\"world_change\"}")).hasLive());
	}

	// every rec w/ live lines must replay the same
	@Test
	void recordingsMatchLive() throws IOException {
		assumeTrue(Files.isDirectory(RecordingReplay.RECORDINGS), "No recordings");
		List<Path> files;
		try (Stream<Path> list = Files.list(RecordingReplay.RECORDINGS)) {
			files = list.filter(RecordingFiles::isRecording).sorted().toList();
		}
		for (Path file : files) {
			LiveCheck check = RecordingReplay.liveCheck(RecordingReplay.read(file));
			if (!check.hasLive()) continue;
			int diff = check.firstDifference();
			assertEquals(-1, diff, file.getFileName() + " differs at live event " + (diff + 1)
					+ ", run ./gradlew replay --args=\"" + file.getFileName() + "\"");
		}
	}

	private static List<JsonObject> records(String... lines) {
		return Stream.of(lines).map(line -> JsonParser.parseString(line).getAsJsonObject()).toList();
	}
}
