package dev.skysurveyor.journal;

import java.util.List;

// after a restart the newest session goes on from its last activity
// restart mid session = gap too. files call this in any order
public final class Resume {
	private Resume() {
	}

	public static void shafts(Journal journal, Breaks breaks, List<JournalEntry> entries, List<MiningEntry> mining, List<BreakEntry> paused) {
		journal.resume(entries);
		activity(journal, breaks, entries, mining, paused);
	}

	public static void activity(Journal journal, Breaks breaks, List<JournalEntry> entries, List<MiningEntry> mining, List<BreakEntry> paused) {
		Journal.Session session = journal.session();
		if (session == null) return;
		long last = lastActivity(session.id(), entries, mining, paused);
		if (last < 0) return;
		journal.resumeActivity(session.id(), last);
		breaks.resume(session.id(), last);
	}

	// -1 = none. saved in order, so last line per file
	// breaks end in mining, so a later break moves it too
	static long lastActivity(long sessionId, List<JournalEntry> entries, List<MiningEntry> mining, List<BreakEntry> paused) {
		long last = -1;
		if (!entries.isEmpty() && entries.getLast().sessionId() == sessionId) last = entries.getLast().endedAt();
		if (!mining.isEmpty() && mining.getLast().sessionId() == sessionId) last = Math.max(last, mining.getLast().at());
		if (last >= 0 && !paused.isEmpty() && paused.getLast().sessionId() == sessionId) last = Math.max(last, paused.getLast().to());
		return last;
	}
}
