package dev.skysurveyor.screen;

import java.util.List;

import dev.skysurveyor.journal.JournalEntry;
import dev.skysurveyor.journal.JournalStats.SessionSummary;

public final class JournalRefresh {
	// selected -1 = none
	public record View(int sessionIndex, int selected, boolean viewing, boolean current, boolean started) {
	}

	private JournalRefresh() {
	}

	// activeId null = none going
	public static boolean current(List<SessionSummary> sessions, Long activeId) {
		return activeId != null && !sessions.isEmpty() && sessions.getFirst().id() == activeId;
	}

	// the live session opens itself
	public static View opened(List<SessionSummary> sessions, Long activeId) {
		boolean current = current(sessions, activeId);
		return new View(0, last(sessions, 0), current, current, activeId != null && !current);
	}

	// fresh = nothing clicked since open, so a live session that shows up late (file still loading) opens
	public static View after(List<SessionSummary> before, List<SessionSummary> after, View view, Long activeId, boolean fresh) {
		boolean current = current(after, activeId);
		boolean started = activeId != null && !current;
		if (fresh && !view.viewing() && current) return new View(0, last(after, 0), true, true, started);
		SessionSummary shown = view.sessionIndex() >= 0 && view.sessionIndex() < before.size() ? before.get(view.sessionIndex()) : null;
		int index = shown == null ? -1 : indexOf(after, shown.id());
		if (index < 0) {
			// nothing loaded before, or cant happen, sessions only grow. i think
			return new View(0, last(after, 0), false, current, started);
		}
		return new View(index, selection(shown, after.get(index), view.selected()), view.viewing(), current, started);
	}

	// newest picked + grew = follow it
	private static int selection(SessionSummary before, SessionSummary after, int selected) {
		int newest = after.shafts() - 1;
		if (selected < 0 || selected >= before.shafts()) return newest;
		if (selected == before.shafts() - 1 && after.shafts() > before.shafts()) return newest;
		JournalEntry entry = before.entries().get(selected);
		// snapshot copies lists, not entries
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
