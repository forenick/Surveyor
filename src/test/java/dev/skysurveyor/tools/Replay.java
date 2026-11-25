package dev.skysurveyor.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import dev.skysurveyor.detection.EventText;
import dev.skysurveyor.detection.RecordingReplay;
import dev.skysurveyor.detection.RecordingReplay.LiveCheck;
import dev.skysurveyor.detection.RecordingReplay.Timed;
import dev.skysurveyor.detection.ShaftEvent;
import dev.skysurveyor.detection.ShaftEvent.ColdChanged;
import dev.skysurveyor.detection.ShaftEvent.Pristine;
import dev.skysurveyor.detection.ShaftEvent.ShaftEntered;
import dev.skysurveyor.detection.ShaftTracker;
import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.effects.Consumable;
import dev.skysurveyor.effects.EffectText;
import dev.skysurveyor.effects.EffectTimers;
import dev.skysurveyor.journal.Journal;
import dev.skysurveyor.journal.JournalEntry;
import dev.skysurveyor.journal.MiningEntry;
import dev.skysurveyor.journal.OutsideMining;
import dev.skysurveyor.journal.SummaryText;
import dev.skysurveyor.recorder.RecordingFiles;
import dev.skysurveyor.settings.MoneySettings;
import dev.skysurveyor.util.Format;

// dev tool, not in the jar. ./gradlew replay --args="<recording> [--raw] [--cold] [--marks]"
public final class Replay {
	private static final String INDENT = "          ";
	// live + replay entry of the same shaft, enteredAt off by a ms or so
	private static final long SAME_SHAFT_MILLIS = 2000;

	private record LiveEntry(String shaft, long entered, Long search) {
	}

	private final boolean raw;
	private final boolean cold;
	private final List<JournalEntry> entries = new ArrayList<>();
	private final List<ShaftEvent> recordEvents = new ArrayList<>();
	private final ShaftTracker tracker = new ShaftTracker(TextPatterns.defaults(), recordEvents::add);
	private final Journal journal = new Journal(TextPatterns.defaults(), tracker::inMiningArea, entries::add);
	private final List<MiningEntry> mining = new ArrayList<>();
	private final OutsideMining outside = new OutsideMining(TextPatterns.defaults(), tracker::inMiningArea, journal::activeSession,
			mining::add);
	private long now;
	private long shaftEnteredAt = -1;
	// runs of pristine procs, one line each
	private long pristineFrom = -1;
	private long pristineTo;
	private int pristineProcs;
	private final Map<String, Long> pristineItems = new LinkedHashMap<>();
	private final EffectText effectText = new EffectText(TextPatterns.defaults());
	private final EffectTimers effects = new EffectTimers(effectText);
	private boolean onSkyblock;
	private Map<Consumable, Long> effectsBefore = Map.of();
	private long effectsAt;

	private Replay(boolean raw, boolean cold) {
		this.raw = raw;
		this.cold = cold;
	}

	public static void main(String[] args) throws IOException {
		boolean raw = false;
		boolean cold = false;
		boolean marks = false;
		String name = null;
		for (String arg : args) {
			switch (arg) {
				case "--raw" -> raw = true;
				case "--cold" -> cold = true;
				case "--marks" -> marks = true;
				default -> {
					if (arg.startsWith("--")) {
						System.err.println("Unknown option " + arg
								+ ". Options: --raw (unmatched chat, titles), --cold (cold changes), --marks (marks + the lines around them)");
						System.exit(1);
					}
					name = arg;
				}
			}
		}
		Path file = find(name);
		if (file == null) {
			System.err.println("No recording found" + (name == null ? " in " + RecordingReplay.RECORDINGS.toAbsolutePath().normalize() : ": " + name));
			System.exit(1);
		}
		if (marks) Marks.print(file, RecordingReplay.read(file));
		else new Replay(raw, cold).run(file);
	}

	// newest in ../Recordings, a name in there, or any path
	private static Path find(String name) throws IOException {
		if (name == null) {
			if (!Files.isDirectory(RecordingReplay.RECORDINGS)) return null;
			try (Stream<Path> files = Files.list(RecordingReplay.RECORDINGS)) {
				return files.filter(RecordingFiles::isRecording)
						.max(Comparator.comparing(path -> path.getFileName().toString())).orElse(null);
			}
		}
		for (Path path : List.of(Path.of(name), RecordingReplay.RECORDINGS.resolve(name), RecordingReplay.RECORDINGS.resolve(name + ".jsonl"),
				RecordingReplay.RECORDINGS.resolve(name + ".jsonl.gz"))) {
			if (Files.isRegularFile(path)) return path;
		}
		return null;
	}

	private void run(Path file) throws IOException {
		List<JsonObject> records = RecordingReplay.read(file);
		long length = records.isEmpty() ? 0 : RecordingReplay.time(records.getLast());
		System.out.println(file.getFileName() + ", " + Format.duration(length));
		List<LiveEntry> liveEntries = new ArrayList<>();
		for (JsonObject record : records) {
			now = RecordingReplay.time(record);
			String type = RecordingReplay.type(record);
			if (type.equals("live_entry")) {
				liveEntries.add(new LiveEntry(record.get("shaft").isJsonNull() ? null : record.get("shaft").getAsString(),
						record.get("entered").getAsLong(), record.has("search") ? record.get("search").getAsLong() : null));
				continue;
			}
			boolean wasSearching = journal.searchRunning();
			int entryCount = entries.size();
			recordEvents.clear();
			int miningCount = mining.size();
			RecordingReplay.feed(record, tracker, t -> {
				journal.onBlockHit(t);
				outside.onBlockHit(t);
			});
			for (ShaftEvent event : recordEvents) {
				journal.onEvent(event, now);
				outside.onEvent(event, now);
				print(event);
			}
			if (type.equals("world_change")) {
				journal.onWorldChange(now);
				outside.onWorldChange(now);
			}
			for (MiningEntry entry : mining.subList(miningCount, mining.size())) printMining(entry);
			if (!wasSearching && journal.searchRunning()) line("spawn timer started");
			if (wasSearching && !journal.searchRunning()) {
				Long spawn = journal.spawnTimerMillis(now);
				line(spawn == null ? "spawn timer stopped, not timed" : "spawn timer stopped at " + Format.duration(spawn));
			}
			for (JournalEntry entry : entries.subList(entryCount, entries.size())) printEntry(entry);
			printOther(record, type);
			feedEffects(record, type);
		}
		flushPristine();
		printMiningTotals();
		System.out.println();
		printLiveCheck(RecordingReplay.liveCheck(records), liveEntries);
	}

	private void print(ShaftEvent event) {
		if (event instanceof ShaftEntered) shaftEnteredAt = now;
		if (event instanceof Pristine pristine) {
			if (pristineFrom < 0) pristineFrom = now;
			pristineTo = now;
			pristineProcs++;
			pristineItems.merge(pristine.gems().item(), (long) pristine.gems().count(), Long::sum);
			return;
		}
		if (event instanceof ColdChanged changed) {
			if (cold) line("cold " + changed.cold() + (shaftEnteredAt < 0 ? "" : ", " + Format.duration(now - shaftEnteredAt) + " inside"));
			return;
		}
		line(EventText.of(event));
	}

	private void flushPristine() {
		if (pristineFrom < 0) return;
		StringBuilder text = new StringBuilder("Pristine x" + pristineProcs + " until " + time(pristineTo) + ":");
		pristineItems.forEach((item, count) -> text.append(" +").append(Format.number(count)).append(' ').append(item));
		System.out.println(String.format(Locale.ROOT, "%9s  %s", time(pristineFrom), text));
		pristineFrom = -1;
		pristineProcs = 0;
		pristineItems.clear();
	}

	private void printEntry(JournalEntry entry) {
		flushPristine();
		shaftEnteredAt = -1;
		for (String text : SummaryText.lines(entry, journal.session(), null, MoneySettings.DEFAULTS)) {
			String clean = TextPatterns.clean(text).strip();
			// no prices in a replay
			if (!clean.isEmpty() && !clean.startsWith("Coins")) System.out.println(INDENT + "| " + clean);
		}
	}

	private void printMining(MiningEntry entry) {
		flushPristine();
		long items = entry.items().values().stream().mapToLong(Long::longValue).sum();
		line((entry.estimated() ? "outside shafts, estimated tail: " : "outside shafts: ") + Format.number(items) + " items");
	}

	private void printMiningTotals() {
		if (mining.isEmpty()) return;
		System.out.println();
		System.out.println("Mined outside shafts (" + mining.size() + " entries), estimated tail in brackets");
		Map<String, long[]> totals = new LinkedHashMap<>();
		for (MiningEntry entry : mining) {
			entry.items().forEach((item, count) -> totals.computeIfAbsent(item, i -> new long[2])[entry.estimated() ? 1 : 0] += count);
		}
		totals.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> System.out.println("  "
				+ Format.number(e.getValue()[0]) + (e.getValue()[1] > 0 ? " (+" + Format.number(e.getValue()[1]) + ")" : "") + " "
				+ e.getKey()));
	}

	private void printOther(JsonObject record, String type) {
		switch (type) {
			case "start" -> line("recording started");
			case "stop" -> line("recording stopped");
			case "world_change" -> line("world change");
			case "mark" -> line("MARK " + record.get("note").getAsString());
			case "chat" -> {
				if (raw && recordEvents.isEmpty()) line("chat: " + TextPatterns.clean(record.get("legacy").getAsString()));
			}
			case "title", "subtitle" -> {
				if (raw) line(type + ": " + TextPatterns.clean(record.get("legacy").getAsString()));
			}
			default -> {
			}
		}
	}

	// no slot or use key in recordings: every stack drop counts as used
	private void feedEffects(JsonObject record, String type) {
		boolean running = onSkyblock;
		effects.tick(now, running);
		switch (type) {
			case "scoreboard" -> {
				JsonArray lines = record.getAsJsonArray("lines");
				onSkyblock = !lines.isEmpty() && effectText.skyblock(lines.get(0).getAsString());
			}
			case "held_item" -> {
				JsonElement item = record.get("item");
				JsonObject held = item == null || item.isJsonNull() ? null : item.getAsJsonObject();
				effects.onHeld(now, 0, held == null ? null : held.get("name").getAsString(), held == null ? 0 : held.get("count").getAsInt(),
						() -> held == null ? null : strings(held.getAsJsonArray("lore")), true);
			}
			case "chat" -> effects.onChat(now, record.get("legacy").getAsString());
			case "tab_header_footer" -> effects.onFooter(now, effectText.footer(record.get("footer").getAsString()));
			case "screen" -> {
				List<EffectText.Item> items = new ArrayList<>();
				for (JsonElement slot : record.getAsJsonArray("slots")) {
					JsonObject o = slot.getAsJsonObject();
					if (o.has("name")) items.add(new EffectText.Item(o.get("name").getAsString(), strings(o.getAsJsonArray("lore"))));
				}
				effects.onScreen(effectText.screen(record.get("title").getAsString(), items));
			}
			default -> {
			}
		}
		Map<Consumable, Long> after = effects.all();
		long passed = running ? now - effectsAt : 0;
		for (Consumable c : Consumable.values()) {
			Long was = effectsBefore.get(c);
			Long is = after.get(c);
			if (was != null && is == null) line(c.id() + " ended");
			else if (was == null && is != null) line(c.id() + " started, " + Format.duration(is) + " left");
			// countdown alone moves it by about the time passed
			else if (was != null && Math.abs(was - passed - is) > 1500) line(c.id() + " set to " + Format.duration(is) + " (was " + Format.duration(Math.max(0, was - passed)) + ")");
		}
		effectsBefore = after;
		effectsAt = now;
	}

	private static List<String> strings(JsonArray array) {
		if (array == null) return null;
		List<String> out = new ArrayList<>();
		array.forEach(e -> out.add(e.getAsString()));
		return out;
	}

	private void printLiveCheck(LiveCheck check, List<LiveEntry> liveEntries) {
		if (!check.hasLive()) {
			System.out.println("Live check: no live lines, recorded before 0.24.0");
			return;
		}
		int diff = check.firstDifference();
		if (diff < 0) {
			System.out.println("Live check: all " + check.live().size() + " live events match the replay");
		} else {
			System.out.println("Live check: differs at event " + (diff + 1) + " of " + check.live().size() + " live, "
					+ check.replayed().size() + " replayed");
			System.out.println("  live    " + timed(check.live(), diff));
			System.out.println("  replay  " + timed(check.replayed(), diff));
		}
		if (liveEntries.isEmpty()) return;
		System.out.println("Spawn times, live vs replay");
		for (LiveEntry live : liveEntries) {
			JournalEntry replayed = entries.stream()
					.filter(entry -> Objects.equals(entry.shaftId(), live.shaft())
							&& Math.abs(entry.enteredAt() - live.entered()) <= SAME_SHAFT_MILLIS)
					.findFirst().orElse(null);
			String replaySpawn = replayed == null ? "not in replay" : spawn(replayed.searchMillis());
			System.out.println(String.format(Locale.ROOT, "  %-8s %-10s %s", live.shaft(), spawn(live.search()), replaySpawn));
		}
	}

	private static String timed(List<Timed> events, int index) {
		if (index >= events.size()) return "(none)";
		Timed event = events.get(index);
		return time(event.t()) + "  " + event.text();
	}

	private static String spawn(Long millis) {
		return millis == null ? "-" : Format.duration(millis);
	}

	private void line(String text) {
		flushPristine();
		System.out.println(String.format(Locale.ROOT, "%9s  %s", time(now), text));
	}

	static String time(long millis) {
		long tenths = millis / 100;
		long seconds = tenths / 10;
		if (seconds >= 3600) {
			return String.format(Locale.ROOT, "%d:%02d:%02d.%d", seconds / 3600, seconds % 3600 / 60, seconds % 60, tenths % 10);
		}
		return String.format(Locale.ROOT, "%d:%02d.%d", seconds / 60, seconds % 60, tenths % 10);
	}
}
