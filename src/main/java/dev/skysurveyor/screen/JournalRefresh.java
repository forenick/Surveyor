package dev.skysurveyor.screen;

import java.util.List;

import dev.skysurveyor.journal.JournalEntry;
import dev.skysurveyor.journal.JournalStats.SessionSummary;

// journal changed under the open screen: what stays put, what follows. plain java
public final class JournalRefresh {
	// sessionIndex into the sessions list, selected = shaft index in that session, -1 = none
	public record View(int sessionIndex, int selected, boolean viewing, boolean current, boolean started) {
	}

	private JournalRefresh() {
	}

	// session tab = only the one still going. activeId null = none going
	public static boolean current(List<SessionSummary> sessions, Long activeId) {
		return activeId != null && !sessions.isEmpty() && sessions.getFirst().id() == activeId;
	}

	public static View opened(List<SessionSummary> sessions, Long activeId) {
		boolean current = current(sessions, activeId);
		return new View(0, last(sessions, 0), false, current, activeId != null && !current);
	}

	// sessionTab = session tab picked and no older session opened
	public static View after(List<SessionSummary> before, List<SessionSummary> after, View view, Long activeId, boolean sessionTab) {
		boolean current = current(after, activeId);
		boolean started = activeId != null && !current;
		SessionSummary shown = view.sessionIndex() >= 0 && view.sessionIndex() < before.size() ? before.get(view.sessionIndex()) : null;
		int index = shown == null ? -1 : indexOf(after, shown.id());
		if (sessionTab && !view.viewing()) {
			if (current) {
				int selected = view.current() && index == 0 ? selection(shown, after.getFirst(), view.selected()) : last(after, 0);
				return new View(0, selected, false, true, started);
			}
			// ended while its table was open: stays open, tab stays
			if (view.current() && index >= 0) return new View(index, selection(shown, after.get(index), view.selected()), true, false, started);
			return new View(0, last(after, 0), false, false, started);
		}
		if (index < 0) {
			// cant happen, sessions only grow. back to the list
			return new View(0, last(after, 0), false, current, started);
		}
		return new View(index, selection(shown, after.get(index), view.selected()), view.viewing(), current, started);
	}

	// newest picked + session grew = follow it, else the same shaft
	private static int selection(SessionSummary before, SessionSummary after, int selected) {
		int newest = after.shafts() - 1;
		if (selected < 0 || selected >= before.shafts()) return newest;
		if (selected == before.shafts() - 1 && after.shafts() > before.shafts()) return newest;
		JournalEntry entry = before.entries().get(selected);
		// same objects, the snapshot copies the lists not the entries
		List<JournalEntry> entries = after.entries();
		for (int i = 0; i < entries.size(); i++) {
			if (entries.get(i) == entry) return i;
		}
		int found = entries.indexOf(entry);
		return found >= 0 ? found : Math.min(selected, newest);
	}

	private static int indexOf(List<SessionSummary> sessions, long id) {
		for (int i = 0; i < sessions.size(); i++) {
			if (sessions.get(i).id() == id) return i;
		}
		return -1;
	}

	private static int last(List<SessionSummary> sessions, int index) {
		return index < sessions.size() ? sessions.get(index).shafts() - 1 : -1;
	}
}
