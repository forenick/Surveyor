package dev.skysurveyor.journal;

import java.util.List;

// after a restart: the newest shafts session goes on, from its last saved activity
// a restart mid session is a gap too. each file calls this once it loaded, in any order
public final class Resume {
	private Resume() {
	}

	public static void shafts(Journal journal, Breaks breaks, List<JournalEntry> entries, List<MiningEntry> mining) {
		journal.resume(entries);
		activity(journal, breaks, entries, mining);
	}

	public static void activity(Journal journal, Breaks breaks, List<JournalEntry> entries, List<MiningEntry> mining) {
		Journal.Session session = journal.session();
		if (session == null) return;
		long last = lastActivity(session.id(), entries, mining);
		if (last < 0) return;
		journal.resumeActivity(session.id(), last);
		breaks.resume(session.id(), last);
	}

	// -1 = none. sessions are saved in order, so only the last line of each file
	static long lastActivity(long sessionId, List<JournalEntry> entries, List<MiningEntry> mining) {
		long last = -1;
		if (!entries.isEmpty() && entries.getLast().sessionId() == sessionId) last = entries.getLast().endedAt();
		if (!mining.isEmpty() && mining.getLast().sessionId() == sessionId) last = Math.max(last, mining.getLast().at());
		return last;
	}
}
