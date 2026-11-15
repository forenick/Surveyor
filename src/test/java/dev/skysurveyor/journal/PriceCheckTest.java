package dev.skysurveyor.journal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import dev.skysurveyor.detection.ItemCount;
import dev.skysurveyor.detection.RecordingReplay;
import dev.skysurveyor.detection.ShaftEvent;
import dev.skysurveyor.detection.ShaftEvent.PickaxeAbility;
import dev.skysurveyor.detection.ShaftEvent.SacksChanged;
import dev.skysurveyor.detection.ShaftEvent.ShaftEntered;
import dev.skysurveyor.detection.ShaftEvent.ShaftLeft;
import dev.skysurveyor.detection.ShaftTracker;
import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.money.LiveMoney;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PriceCheckTest {
	private final PriceCheck check = new PriceCheck();

	@Test
	void sacksWhileMiningInAShaft() {
		check.onEvent(new ShaftEntered(null), 0);
		check.onBlockHit(10_000);
		assertTrue(check.onEvent(sacks(30), 30_000));
	}

	@Test
	void pickaxeAbilityCountsAsMining() {
		check.onEvent(new ShaftEntered(null), 0);
		check.onEvent(new PickaxeAbility("Pickobulus"), 5_000);
		assertTrue(check.onEvent(sacks(30), 30_000));
	}

	@Test
	void notWithoutMiningInTheWindow() {
		check.onEvent(new ShaftEntered(null), 0);
		check.onBlockHit(10_000);
		assertFalse(check.onEvent(sacks(30), 60_000));
	}

	@Test
	void notOutsideAShaft() {
		check.onBlockHit(10_000);
		assertFalse(check.onEvent(sacks(30), 20_000));
		check.onEvent(new ShaftEntered(null), 30_000);
		check.onBlockHit(40_000);
		check.onEvent(new ShaftLeft(null), 50_000);
		assertFalse(check.onEvent(sacks(30), 55_000));
	}

	@Test
	void miningBeforeTheShaftDoesntCount() {
		check.onBlockHit(10_000);
		check.onEvent(new ShaftEntered(null), 11_000);
		assertFalse(check.onEvent(sacks(30), 20_000));
	}

	@Test
	void notForRemovalsOnly() {
		check.onEvent(new ShaftEntered(null), 0);
		check.onBlockHit(10_000);
		assertFalse(check.onEvent(new SacksChanged(30, List.of(), List.of(new ItemCount("Glacite", 64)), 0, 0), 20_000));
	}

	// recording 5: short shafts have no sacks msg, the 2 long jasper ones get one every ~30s
	@Test
	void recordingFive() throws IOException {
		Path recording = RecordingReplay.RECORDINGS.resolve("2026-11-15_16-10-29.jsonl");
		assumeTrue(Files.exists(recording), "Recording not available: " + recording);
		List<ShaftEvent> events = new ArrayList<>();
		ShaftTracker tracker = new ShaftTracker(TextPatterns.defaults(), events::add);
		PriceCheck replayed = new PriceCheck();
		int shafts = 0;
		int shaftsAsking = 0;
		int longShafts = 0;
		boolean inShaft = false;
		long enteredAt = 0;
		long lastAsk = 0;
		long longestWait = 0;
		for (JsonObject record : RecordingReplay.read(recording)) {
			long now = RecordingReplay.time(record);
			events.clear();
			RecordingReplay.feed(record, tracker, replayed::onBlockHit);
			for (ShaftEvent event : events) {
				if (event instanceof ShaftEntered) {
					inShaft = true;
					shafts++;
					enteredAt = now;
					lastAsk = now;
					longestWait = 0;
				}
				if (replayed.onEvent(event, now)) {
					assertTrue(inShaft);
					if (lastAsk == enteredAt) shaftsAsking++;
					longestWait = Math.max(longestWait, now - lastAsk);
					lastAsk = now;
				}
				if (event instanceof ShaftLeft) {
					inShaft = false;
					if (now - enteredAt > LiveMoney.BAZAAR_MAX_AGE) {
						longShafts++;
						assertTrue(longestWait < 60_000, "waited " + longestWait);
					}
				}
			}
		}
		assertEquals(25, shafts);
		assertEquals(4, shaftsAsking);
		assertEquals(2, longShafts);
	}

	private static SacksChanged sacks(int window) {
		return new SacksChanged(window, List.of(new ItemCount("Rough Jade Gemstone", 80)), List.of(), 0, 0);
	}
}
