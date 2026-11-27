package dev.skysurveyor.mining;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import dev.skysurveyor.detection.RecordingReplay;
import dev.skysurveyor.detection.ShaftEvent;
import dev.skysurveyor.detection.ShaftEvent.ShaftLeft;
import dev.skysurveyor.detection.ShaftTracker;
import dev.skysurveyor.detection.TextPatterns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

// real block_break lines (rec 8, alpha.16), numbers from efficiency_gate.py
class RecordedBreaksTest {
	private record Left(String id, MiningStats stats) {
	}

	@Test
	void recording8PeridotCrystal() throws IOException {
		List<Left> shafts = replay("2026-11-21_19-12-23.jsonl.gz");
		Left peri = shafts.stream().filter(s -> s.id().equals("PERI_C")).findFirst().orElseThrow();
		MiningStats stats = peri.stats();
		assertEquals("green_stained_glass", stats.block(), "not the hard stone");
		// 554 blocks + 500 panes, 180 peridot neighbours (gate script)
		assertEquals(1054, stats.blocks());
		assertEquals(180, stats.spread());
		assertEquals(11.0, stats.blockTicks());
		assertEquals(4.0, stats.boostTicks());
		// gate script: 60.3%, its boost ends at the expired line too
		assertEquals(0.60, Efficiency.of(stats), 0.01);
		// 373 in boost (calc-prototype count, same lines, 11-25)
		assertEquals(373, stats.boostBlocks());
	}

	private static List<Left> replay(String name) throws IOException {
		Path file = RecordingReplay.RECORDINGS.resolve(name);
		assumeTrue(Files.exists(file), "no recording " + file);
		List<Left> out = new ArrayList<>();
		MiningMeter meter = new MiningMeter();
		long[] now = {0};
		ShaftTracker tracker = new ShaftTracker(TextPatterns.defaults(), (ShaftEvent event) -> {
			meter.onEvent(event, now[0]);
			if (event instanceof ShaftLeft left && left.shaft().id() != null) {
				out.add(new Left(left.shaft().id().id(), meter.stats(left.shaft().enteredAt(), left.shaft().enteredAt() + left.shaft().durationMillis())));
			}
		});
		for (JsonObject record : RecordingReplay.read(file)) {
			now[0] = RecordingReplay.time(record);
			String type = record.get("type").getAsString();
			if (type.equals("block_break")) {
				List<String> gone = new ArrayList<>();
				if (record.has("gone")) for (JsonElement g : record.getAsJsonArray("gone")) gone.add(g.getAsString());
				String block = record.get("block").getAsString();
				meter.onBreak(now[0], block, record.get("ticks").getAsInt(), record.get("clean").getAsBoolean(), MiningMeter.spread(block, gone));
				continue;
			}
			RecordingReplay.feed(record, tracker, meter::onHit);
			if (type.equals("world_change")) meter.onWorldChange();
		}
		return out;
	}
}
