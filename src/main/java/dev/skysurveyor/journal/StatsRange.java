package dev.skysurveyor.journal;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.google.gson.annotations.SerializedName;

import dev.skysurveyor.data.Patches;
import dev.skysurveyor.data.Patches.Patch;
import dev.skysurveyor.journal.JournalStats.SessionSummary;

// whole sessions only, so records + bars still open them
// saved names in config.json (journal.statsRange, calculator.range), never rename
public enum StatsRange {
	@SerializedName("all_time")
	ALL_TIME("All-time"),
	@SerializedName("last_7_days")
	LAST_7_DAYS("Last 7 days"),
	@SerializedName("last_5_sessions")
	LAST_5_SESSIONS("Last 5 sessions"),
	// newest patch in patches.txt. "patch" bc 0.27.3 read like a mod version
	@SerializedName("since_patch")
	SINCE_PATCH("Since patch");

	// display choices, not game numbers
	private static final long WEEK_MILLIS = 7 * 24 * 3_600_000L;
	private static final int RECENT_SESSIONS = 5;

	private final String displayName;

	StatsRange(String displayName) {
		this.displayName = displayName;
	}

	public String displayName() {
		return displayName(Patches.defaults());
	}

	String displayName(Patches patches) {
		Patch patch = this == SINCE_PATCH ? patches.newest() : null;
		return patch == null ? displayName : displayName + " " + patch.name();
	}

	// hover text
	public String description() {
		Patch patch = this == SINCE_PATCH ? Patches.defaults().newest() : null;
		return switch (this) {
			case ALL_TIME -> "Every session you have";
			case LAST_7_DAYS -> "Sessions started in the last 7 days";
			case LAST_5_SESSIONS -> "Your " + RECENT_SESSIONS + " newest sessions";
			case SINCE_PATCH -> patch == null ? "Every session you have" : "Sessions since SkyBlock " + patch.name();
		};
	}

	// null = not one of ours
	public static StatsRange ofSaved(String saved) {
		for (StatsRange range : values()) {
			if (range.name().equalsIgnoreCase(saved)) return range;
		}
		return null;
	}

	public List<SessionSummary> sessions(List<SessionSummary> newestFirst, long now) {
		return sessions(newestFirst, now, Patches.defaults());
	}

	List<SessionSummary> sessions(List<SessionSummary> newestFirst, long now, Patches patches) {
		return switch (this) {
			case ALL_TIME -> newestFirst;
			case LAST_7_DAYS -> newestFirst.stream().filter(s -> s.start() >= now - WEEK_MILLIS).toList();
			case LAST_5_SESSIONS -> newestFirst.stream().limit(RECENT_SESSIONS).toList();
			case SINCE_PATCH -> {
				Patch patch = patches.newest();
				yield patch == null ? newestFirst : newestFirst.stream().filter(s -> s.start() >= patch.startMillis()).toList();
			}
		};
	}

	// oldest first
	public static <T> List<T> flat(List<SessionSummary> newestFirst, Function<SessionSummary, List<T>> part) {
		List<T> all = new ArrayList<>();
		for (SessionSummary session : newestFirst.reversed()) all.addAll(part.apply(session));
		return all;
	}
}
