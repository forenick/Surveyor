package dev.skysurveyor.calc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import dev.skysurveyor.detection.RecordingReplay;
import dev.skysurveyor.detection.Shaft;
import dev.skysurveyor.detection.Shaft.ColdStep;
import dev.skysurveyor.detection.ShaftEvent;
import dev.skysurveyor.detection.ShaftEvent.ShaftLeft;
import dev.skysurveyor.detection.ShaftTracker;
import dev.skysurveyor.detection.TextPatterns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

// shafts nick really froze in, timer vs when he died
class RecordedFreezeTest {
	// jasper w/ mayhem speed buff, 2nd jasper left by exit
	@Test
	void recording5() throws IOException {
		List<Shaft> frozen = frozen("2026-11-15_16-10-29.jsonl");
		assertEquals(1, frozen.size());
		check(frozen.getFirst());
	}

	// peridot crystal shaft
	@Test
	void recording8() throws IOException {
		List<Shaft> frozen = frozen("2026-11-21_19-12-23.jsonl.gz");
		assertEquals(1, frozen.size());
		check(frozen.getFirst());
	}

	// worst miss in secs, from 1 min + 5 min in
	private static void check(Shaft shaft) {
		List<ColdStep> steps = shaft.coldSteps();
		double early = 0;
		double late = 0;
		for (int i = 1; i <= steps.size(); i++) {
			ColdStep at = steps.get(i - 1);
			if (at.millis() < 60_000) continue;
			Long freezeAt = FreezeClock.freezeAt(steps.subList(0, i), null);
			assertTrue(freezeAt != null, "no time at " + at);
			double miss = Math.abs(freezeAt - shaft.durationMillis()) / 1000.0;
			early = Math.max(early, miss);
			if (at.millis() >= 300_000) late = Math.max(late, miss);
		}
		System.out.printf("%s froze at %.1f s, worst miss %.1f s from 1 min, %.1f s from 5 min%n", shaft.id().id(),
				shaft.durationMillis() / 1000.0, early, late);
		assertTrue(early < 45, "from 1 min: " + early);
		assertTrue(late < 12, "from 5 min: " + late);
	}

	private static List<Shaft> frozen(String name) throws IOException {
		Path file = RecordingReplay.RECORDINGS.resolve(name);
		assumeTrue(Files.exists(file), "no recording " + file);
		List<Shaft> out = new ArrayList<>();
		ShaftTracker tracker = new ShaftTracker(TextPatterns.defaults(), (ShaftEvent event) -> {
			if (event instanceof ShaftLeft left && left.shaft().exitReason() == Shaft.ExitReason.DEATH) out.add(left.shaft());
		});
		for (JsonObject record : RecordingReplay.read(file)) RecordingReplay.feed(record, tracker, t -> {
		});
		return out;
	}
}
