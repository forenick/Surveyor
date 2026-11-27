package dev.skysurveyor.mining;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import dev.skysurveyor.detection.RecordingReplay;
import dev.skysurveyor.detection.Shaft;
import dev.skysurveyor.detection.ShaftEvent;
import dev.skysurveyor.detection.ShaftEvent.Pristine;
import dev.skysurveyor.detection.ShaftEvent.ShaftLeft;
import dev.skysurveyor.detection.ShaftTracker;
import dev.skysurveyor.detection.TextPatterns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

// efficiency-design.md s3: research/11 E (spread + whole time inside)
// blocks from sacks, no breaks in these recs. numbers from efficiency.py
class RecordedEfficiencyTest {
	// research/11 s2 (wiki 3 to 5, fits rec 1)
	private static final double BASE_DROPS = 4;
	// game min (research/11 s8), shorter = crosshair passing
	private static final int MIN_TICKS = 4;

	private record Mined(String id, long seconds, long boostSeconds, int ticks, int boostTicks, double blocks, double efficiency) {
	}

	@Test
	void recording5Jasper() throws IOException {
		List<Mined> shafts = mined("2026-11-15_16-10-29.jsonl");
		assertEquals(2, shafts.size());
		check(shafts.get(0), "JASP_C", 763, 159, 13, 4, 1228, 0.711);
		// 10th boost still ran at the exit, research/11 left it out and got 0.702
		check(shafts.get(1), "JASP_C", 741, 188, 13, 4, 1231, 0.688);
	}

	@Test
	void recording6Jade() throws IOException {
		List<Mined> shafts = mined("2026-11-17_19-16-17.jsonl");
		assertEquals(1, shafts.size());
		check(shafts.get(0), "JADE_1", 735, 181, 8, 4, 1634, 0.714);
	}

	private static void check(Mined shaft, String id, long seconds, long boostSeconds, int ticks, int boostTicks, double blocks, double efficiency) {
		assertEquals(id, shaft.id());
		assertEquals(seconds, shaft.seconds());
		assertEquals(boostSeconds, shaft.boostSeconds());
		assertEquals(ticks, shaft.ticks());
		assertEquals(boostTicks, shaft.boostTicks());
		assertEquals(blocks, shaft.blocks(), 0.5);
		assertEquals(efficiency, shaft.efficiency(), 0.0015);
	}

	// gem shafts of 2 min or longer
	private static List<Mined> mined(String name) throws IOException {
		Path file = RecordingReplay.RECORDINGS.resolve(name);
		assumeTrue(Files.exists(file), "no recording " + file);
		List<Mined> out = new ArrayList<>();
		MiningMeter meter = new MiningMeter();
		List<Long> procs = new ArrayList<>();
		List<Integer> gaps = new ArrayList<>();
		List<Integer> boostGaps = new ArrayList<>();
		long[] now = {0};
		long[] lastHit = {-1};
		boolean[] lastHitBoosted = {false};
		ShaftTracker tracker = new ShaftTracker(TextPatterns.defaults(), (ShaftEvent event) -> {
			meter.onEvent(event, now[0]);
			if (event instanceof Pristine pristine) procs.add(pristine.gems().count());
			if (event instanceof ShaftLeft left) {
				Mined mined = mined(left.shaft(), meter, procs, gaps, boostGaps);
				if (mined != null) out.add(mined);
			}
		});
		for (JsonObject record : RecordingReplay.read(file)) {
			now[0] = RecordingReplay.time(record);
			RecordingReplay.feed(record, tracker, t -> {
				if (lastHit[0] >= 0) (lastHitBoosted[0] ? boostGaps : gaps).add((int) Math.round((t - lastHit[0]) / 50.0));
				lastHit[0] = t;
				lastHitBoosted[0] = meter.boosting();
			});
			if (record.get("type").getAsString().equals("world_change")) {
				meter.onWorldChange();
				procs.clear();
				gaps.clear();
				boostGaps.clear();
				lastHit[0] = -1;
			}
		}
		return out;
	}

	private static Mined mined(Shaft shaft, MiningMeter meter, List<Long> procs, List<Integer> gaps, List<Integer> boostGaps) {
		if (shaft.id() == null || shaft.durationMillis() < 120_000 || procs.isEmpty()) return null;
		String gem = gemOf(shaft);
		if (gem == null) return null;
		long items = shaft.sacksAdded().getOrDefault("Rough " + gem + " Gemstone", 0L) + shaft.sacksAdded().getOrDefault("Flawed " + gem + " Gemstone", 0L);
		double blocks = items / procSize(procs) / BASE_DROPS;
		// whole time inside, like research/11
		long boostMillis = meter.boostMillis(shaft.enteredAt(), shaft.enteredAt() + shaft.durationMillis());
		double ticks = MiningMeter.mode(gaps.stream().filter(gap -> gap >= MIN_TICKS).toList());
		double boostTicks = MiningMeter.mode(boostGaps.stream().filter(gap -> gap >= MIN_TICKS).toList());
		double nonstop = Efficiency.nonstop(shaft.durationMillis(), boostMillis, ticks, boostTicks);
		return new Mined(shaft.id().id(), Math.round(shaft.durationMillis() / 1000.0), Math.round(boostMillis / 1000.0), (int) ticks,
				(int) boostTicks, blocks, blocks / nonstop);
	}

	// the gem most of the pristine msgs were for
	private static String gemOf(Shaft shaft) {
		return shaft.pristineItems().entrySet().stream().max(java.util.Map.Entry.comparingByValue())
				.map(e -> e.getKey().replace("Flawed ", "").replace(" Gemstone", "")).orElse(null);
	}

	// proc size: avg of msgs under 1.5x the lower halfs median, trust me
	private static double procSize(List<Long> procs) {
		List<Long> sorted = procs.stream().sorted().toList();
		List<Long> lower = sorted.subList(0, Math.max(1, sorted.size() / 2));
		double median = lower.size() % 2 == 1 ? lower.get(lower.size() / 2)
				: (lower.get(lower.size() / 2 - 1) + lower.get(lower.size() / 2)) / 2.0;
		return procs.stream().filter(proc -> proc < 1.5 * median).mapToLong(Long::longValue).average().orElseThrow();
	}
}
