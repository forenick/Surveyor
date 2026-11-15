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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LiveCheckTest {
	// recording 4
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

	@Test
	void oldRecordingsHaveNoLiveLines() {
		assertFalse(RecordingReplay.liveCheck(records("{\"t\":0,\"type\":\"start\"}", "{\"t\":12,\"type\":\"world_change\"}")).hasLive());
	}

	// every recording made w/ live lines must replay the same
	@Test
	void recordingsMatchLive() throws IOException {
		assumeTrue(Files.isDirectory(RecordingReplay.RECORDINGS), "No recordings");
		List<Path> files;
		try (Stream<Path> list = Files.list(RecordingReplay.RECORDINGS)) {
			files = list.filter(path -> path.toString().endsWith(".jsonl")).sorted().toList();
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
