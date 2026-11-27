package dev.skysurveyor.journal;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongFunction;

import dev.skysurveyor.detection.ShaftEvent;
import dev.skysurveyor.detection.ShaftEvent.ShaftEntered;
import dev.skysurveyor.detection.ShaftEvent.ShaftLeft;

// per hour rates skip breaks (money-tracker-design 3.8 B)
// mining = block hit or ability. shaft time always counts
// 1st BREAK_MILLIS still counts, so live rate doesnt jump
public final class Breaks {
	// nicks pick (11-15), display choice, not a game number
	public static final long BREAK_MILLIS = 2 * 60_000L;

	private final MiningClock clock;
	private final LongFunction<Journal.Session> session;
	private final Consumer<BreakEntry> listener;
	private long lastActiveAt = -1;
	// -1 = none
	private long activeSession = -1;

	public Breaks(BooleanSupplier inMiningArea, LongFunction<Journal.Session> session, Consumer<BreakEntry> listener) {
		this.clock = new MiningClock(MiningClock.Scope.MINES_OR_SHAFT, inMiningArea);
		this.session = session;
		this.listener = listener;
	}

	// of breaks ended between from and until
	public static long counted(List<BreakEntry> breaks, long from, long until) {
		long total = 0;
		for (BreakEntry entry : breaks) {
			if (entry.to() > until) continue;
			total += Math.max(0, entry.to() - Math.max(entry.from(), from) - BREAK_MILLIS);
		}
		return total;
	}

	public void onBlockHit(long now) {
		if (clock.onBlockHit(now)) mining(now);
	}

	// after the journal, so new session is there
	public void onEvent(ShaftEvent event, long now) {
		// before the clock flips, call order matters
		if (event instanceof ShaftEntered) active(now);
		if (clock.onEvent(event, now)) mining(now);
		if (event instanceof ShaftLeft) mark(now);
	}

	public void resume(long sessionId, long at) {
		if (lastActiveAt >= 0 && (sessionId != activeSession || at <= lastActiveAt)) return;
		activeSession = sessionId;
		lastActiveAt = at;
	}

	// null = none
	public BreakEntry current(long now) {
		if (clock.inShaft() || lastActiveAt < 0 || now - lastActiveAt <= BREAK_MILLIS) return null;
		Journal.Session current = session.apply(now);
		if (current == null || current.id() != activeSession) return null;
		return new BreakEntry(BreakEntry.VERSION, activeSession, lastActiveAt, now);
	}

	private void mining(long now) {
		if (clock.inShaft()) mark(now);
		else active(now);
	}

	private void active(long now) {
		BreakEntry ended = current(now);
		if (ended != null) listener.accept(ended);
		mark(now);
	}

	private void mark(long now) {
		Journal.Session current = session.apply(now);
		if (current == null) return;
		activeSession = current.id();
		lastActiveAt = now;
	}
}
