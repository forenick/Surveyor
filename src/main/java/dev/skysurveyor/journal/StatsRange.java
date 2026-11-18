package dev.skysurveyor.journal;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import dev.skysurveyor.data.Patches;
import dev.skysurveyor.data.Patches.Patch;
import dev.skysurveyor.journal.JournalStats.SessionSummary;

// whole sessions only, so records and bars still open them
public enum StatsRange {
	ALL_TIME("All-time"),
	LAST_7_DAYS("Last 7 days"),
	LAST_5_SESSIONS("Last 5 sessions"),
	// newest patch in patches.txt, name from there
	SINCE_PATCH("Since");

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

	public StatsRange next(int step) {
		StatsRange[] all = values();
		return all[Math.floorMod(ordinal() + step, all.length)];
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

	// oldest first, f.e. flat(sessions, SessionSummary::entries)
	public static <T> List<T> flat(List<SessionSummary> newestFirst, Function<SessionSummary, List<T>> part) {
		List<T> all = new ArrayList<>();
		for (SessionSummary session : newestFirst.reversed()) all.addAll(part.apply(session));
		return all;
	}
}
