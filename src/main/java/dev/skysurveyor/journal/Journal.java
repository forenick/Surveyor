package dev.skysurveyor.journal;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import dev.skysurveyor.detection.ShaftEvent;
import dev.skysurveyor.detection.ShaftEvent.PortalSpawned;
import dev.skysurveyor.detection.ShaftEvent.ShaftEntered;
import dev.skysurveyor.detection.ShaftEvent.ShaftLeft;
import dev.skysurveyor.detection.TextPatterns;

// only the finder gets "WOW!", other peoples portals arent timed
public final class Journal {
	// default, not a game number
	public static final long SESSION_GAP_MILLIS = 15 * 60_000L;

	// nicks pick, just > 32s pickobulus cd (research/08)
	// also how long a found portal can sit unentered
	public static final long PAUSE_MILLIS = 40_000L;

	public record Session(long id, int shafts, int timedSpawns, long searchMillis) {
		public Long averageSearchMillis() {
			return timedSpawns == 0 ? null : searchMillis / timedSpawns;
		}

		Session add(JournalEntry entry) {
			Long search = entry.searchMillis();
			return new Session(id, shafts + 1, timedSpawns + (search == null ? 0 : 1),
					searchMillis + (search == null ? 0 : search));
		}
	}

	private final TextPatterns patterns;
	private final MiningClock clock;
	private final Consumer<JournalEntry> listener;
	private Session session;
	private long lastActivity;
	private boolean searching;
	private long searchMillis;
	private long lastMiningAt;
	// gap ends at a world change, a warp isnt searching (recording 8 PERI_C)
	private boolean warped;
	private long portalFoundAt = -1;
	private Long timedSearch;

	public Journal(TextPatterns patterns, BooleanSupplier inMiningArea, Consumer<JournalEntry> listener) {
		this.patterns = patterns;
		this.clock = new MiningClock(MiningClock.Scope.MINES_OR_SHAFT, inMiningArea);
		this.listener = listener;
	}

	public Session session() {
		return session;
	}

	// last block hit, ability or shaft activity of the session
	public long lastActivity() {
		return lastActivity;
	}

	// session only really ends at the next activity
	public Session activeSession(long now) {
		return session == null || now - lastActivity > SESSION_GAP_MILLIS ? null : session;
	}

	public void resume(List<JournalEntry> entries) {
		if (entries.isEmpty() || session != null) return;
		JournalEntry last = entries.getLast();
		Session resumed = new Session(last.sessionId(), 0, 0, 0);
		for (JournalEntry entry : entries) {
			if (entry.sessionId() == last.sessionId()) resumed = resumed.add(entry);
		}
		session = resumed;
		lastActivity = last.endedAt();
	}

	// saved activity after the last shaft, f.e. mining outside shafts
	public void resumeActivity(long sessionId, long at) {
		if (session != null && session.id() == sessionId && at > lastActivity) lastActivity = at;
	}

	public boolean searchRunning() {
		return searching;
	}

	// null once session ran out, next activity drops that timer anyway
	public Long spawnTimerMillis(long now) {
		if (activeSession(now) == null) return null;
		if (searching) return searchMillis + Math.max(0, gap(now));
		return timedSearch;
	}

	public void onBlockHit(long now) {
		if (clock.onBlockHit(now)) mining(now);
	}

	public void onEvent(ShaftEvent event, long now) {
		if (clock.onEvent(event, now)) mining(now);
		switch (event) {
			case PortalSpawned spawned when spawned.foundByYou() -> {
				touch(now);
				timedSearch = searching ? searchMillis + gap(now) : null;
				searching = false;
				portalFoundAt = now;
			}
			case ShaftEntered entered -> {
				touch(now);
				searching = false;
				portalFoundAt = -1;
			}
			case ShaftLeft left -> {
				touch(now);
				JournalEntry entry = JournalEntry.of(left.shaft(), session.id(), timedSearch, patterns);
				timedSearch = null;
				session = session.add(entry);
				listener.accept(entry);
			}
			default -> {
			}
		}
	}

	public void onWorldChange(long now) {
		if (!searching || warped) return;
		searchMillis += gap(now);
		warped = true;
	}

	public void newSession(long now) {
		session = new Session(now, 0, 0, 0);
		lastActivity = now;
		searching = false;
		portalFoundAt = -1;
		timedSearch = null;
	}

	// hits on the garden, hub etc dont start or keep a session, clock skips them
	private void mining(long now) {
		touch(now);
		// spawn timer asks the tab list only
		if (!clock.inMines()) return;
		// mining between finding and entering belongs to neither search
		if (portalFoundAt >= 0 && now - portalFoundAt <= PAUSE_MILLIS) return;
		if (searching) {
			searchMillis += gap(now);
		} else {
			searching = true;
			searchMillis = 0;
			// found portal not entered in time isnt part of the next shaft
			timedSearch = null;
		}
		lastMiningAt = now;
		warped = false;
	}

	private long gap(long now) {
		return warped ? 0 : Math.min(now - lastMiningAt, PAUSE_MILLIS);
	}

	private void touch(long now) {
		if (session == null || now - lastActivity > SESSION_GAP_MILLIS) {
			newSession(now);
		} else {
			lastActivity = now;
		}
	}
}
