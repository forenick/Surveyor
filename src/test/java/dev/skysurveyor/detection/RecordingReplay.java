package dev.skysurveyor.detection;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.LongConsumer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.skysurveyor.detection.ShaftEvent.RareDrop;
import dev.skysurveyor.detection.ShaftEvent.ShaftLeft;

public final class RecordingReplay {
	// not in git (player names), tests skip w/o them
	public static final Path RECORDINGS = Path.of("..", "Recordings");
	// world change + the live ShaftLeft it causes land in the same tick
	private static final long SAME_TICK_MILLIS = 50;
	// event added after live lines -> 1st version that writes it. older recordings cant have it, so replay skips it there
	static final Map<Class<? extends ShaftEvent>, String> NEWER_EVENTS = Map.of(RareDrop.class, "0.25.0-alpha.16");

	private RecordingReplay() {
	}

	public record Timed(long t, String text) {
	}

	// live = what the game saw, replayed = what the code does now. both from the 1st world change on
	public record LiveCheck(boolean hasLive, List<Timed> live, List<Timed> replayed) {
		// -1 = same
		public int firstDifference() {
			for (int i = 0; i < Math.max(live.size(), replayed.size()); i++) {
				if (i >= live.size() || i >= replayed.size() || !live.get(i).text().equals(replayed.get(i).text())) return i;
			}
			return -1;
		}
	}

	public static List<ShaftEvent> replay(Path file) throws IOException {
		List<ShaftEvent> events = new ArrayList<>();
		replay(file, new ShaftTracker(TextPatterns.defaults(), events::add), t -> {
		}, t -> {
		});
		return events;
	}

	// clock set before each record is fed
	public static void replay(Path file, ShaftTracker tracker, LongConsumer clock, LongConsumer onBlockHit) throws IOException {
		for (JsonObject record : read(file)) {
			clock.accept(time(record));
			feed(record, tracker, onBlockHit);
		}
	}

	public static List<JsonObject> read(Path file) throws IOException {
		List<JsonObject> records = new ArrayList<>();
		try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (!line.isBlank()) records.add(JsonParser.parseString(line).getAsJsonObject());
			}
		}
		return records;
	}

	public static void feed(JsonObject record, ShaftTracker tracker, LongConsumer onBlockHit) {
		long t = time(record);
		switch (type(record)) {
			case "world_change" -> tracker.onWorldChange(t);
			case "chat" -> tracker.onChat(t, record.get("legacy").getAsString(), strings(record.getAsJsonArray("hover")));
			case "scoreboard" -> tracker.onScoreboard(t, strings(record.getAsJsonArray("lines")));
			case "tablist" -> tracker.onTabList(t, strings(record.getAsJsonArray("lines")));
			case "block_hit" -> onBlockHit.accept(t);
			default -> {
			}
		}
	}

	// before the 1st world change the live tracker already knew things replay doesnt
	public static LiveCheck liveCheck(List<JsonObject> records) {
		boolean hasLive = records.stream().anyMatch(record -> type(record).equals("start") && record.has("live"));
		String version = records.stream().filter(record -> type(record).equals("start") && record.has("version")
				&& !record.get("version").isJsonNull()).map(record -> record.get("version").getAsString()).findFirst().orElse(null);
		List<Timed> live = new ArrayList<>();
		List<Timed> replayed = new ArrayList<>();
		long[] now = {0};
		boolean[] synced = {false};
		ShaftTracker tracker = new ShaftTracker(TextPatterns.defaults(), event -> {
			if (synced[0] && knows(version, event)) replayed.add(new Timed(now[0], EventText.of(event)));
		});
		long syncedAt = -1;
		for (JsonObject record : records) {
			now[0] = time(record);
			switch (type(record)) {
				case "live" -> {
					if (!synced[0]) continue;
					String text = record.get("event").getAsString();
					// shaft from before the recording, replay never saw it start
					boolean syncLeft = live.isEmpty() && text.startsWith(ShaftLeft.class.getSimpleName())
							&& now[0] - syncedAt <= SAME_TICK_MILLIS;
					if (!syncLeft) live.add(new Timed(now[0], text));
				}
				case "world_change" -> {
					feed(record, tracker, t -> {
					});
					if (!synced[0]) {
						synced[0] = true;
						syncedAt = now[0];
					}
				}
				default -> feed(record, tracker, t -> {
				});
			}
		}
		return new LiveCheck(hasLive, live, replayed);
	}

	// no version = before 0.25.0-alpha.16
	static boolean knows(String version, ShaftEvent event) {
		String since = NEWER_EVENTS.get(event.getClass());
		return since == null || version != null && compareVersions(version, since) >= 0;
	}

	// semver: x.y.z, a release beats its prereleases, prerelease parts numeric where both are
	static int compareVersions(String a, String b) {
		String[] aParts = a.split("-", 2);
		String[] bParts = b.split("-", 2);
		int core = compareDotted(aParts[0], bParts[0]);
		if (core != 0) return core;
		if (aParts.length != bParts.length) return aParts.length == 1 ? 1 : -1;
		return aParts.length == 1 ? 0 : compareDotted(aParts[1], bParts[1]);
	}

	private static int compareDotted(String a, String b) {
		String[] as = a.split("\\.");
		String[] bs = b.split("\\.");
		for (int i = 0; i < Math.min(as.length, bs.length); i++) {
			boolean numbers = as[i].matches("\\d+") && bs[i].matches("\\d+");
			int c = numbers ? Long.compare(Long.parseLong(as[i]), Long.parseLong(bs[i])) : as[i].compareTo(bs[i]);
			if (c != 0) return c;
		}
		return Integer.compare(as.length, bs.length);
	}

	public static long time(JsonObject record) {
		return record.get("t").getAsLong();
	}

	public static String type(JsonObject record) {
		return record.get("type").getAsString();
	}

	public static List<String> strings(JsonArray array) {
		if (array == null) return List.of();
		return array.asList().stream().map(JsonElement::getAsString).toList();
	}
}
