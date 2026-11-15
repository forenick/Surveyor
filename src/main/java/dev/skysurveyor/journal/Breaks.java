package dev.skysurveyor.journal;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongFunction;

import dev.skysurveyor.detection.ShaftEvent;
import dev.skysurveyor.detection.ShaftEvent.PickaxeAbility;
import dev.skysurveyor.detection.ShaftEvent.ShaftEntered;
import dev.skysurveyor.detection.ShaftEvent.ShaftLeft;

// gaps w/o mining in a session, so per hour rates skip breaks (money-tracker-design 3.8 B)
// mining = block hit or pickaxe ability in the dwarven mines or a shaft. time in a shaft always counts
// 1st BREAK_MILLIS of a gap still counts, so a live rate stops falling after that instead of jumping
public final class Breaks {
	// nicks pick (2026-11-15), display choice, not a game number
	public static final long BREAK_MILLIS = 2 * 60_000L;

	private final BooleanSupplier inMiningArea;
	private final LongFunction<Journal.Session> session;
	private final Consumer<BreakEntry> listener;
	private boolean inShaft;
	private long lastActiveAt = -1;
	// session of lastActiveAt, -1 = none
	private long activeSession = -1;

	public Breaks(BooleanSupplier inMiningArea, LongFunction<Journal.Session> session, Consumer<BreakEntry> listener) {
		this.inMiningArea = inMiningArea;
		this.session = session;
		this.listener = listener;
	}

	// break time not counted, of breaks that ended between from and until
	public static long counted(List<BreakEntry> breaks, long from, long until) {
		long total = 0;
		for (BreakEntry entry : breaks) {
			if (entry.to() > until) continue;
			total += Math.max(0, entry.to() - Math.max(entry.from(), from) - BREAK_MILLIS);
		}
		return total;
	}

	public void onBlockHit(long now) {
		mining(now);
	}

	// after the journal saw it, so a new session is already there
	public void onEvent(ShaftEvent event, long now) {
		switch (event) {
			case PickaxeAbility ability -> mining(now);
			case ShaftEntered entered -> {
				active(now);
				inShaft = true;
			}
			case ShaftLeft left -> {
				inShaft = false;
				mark(now);
			}
			default -> {
			}
		}
	}

	// after a restart, from the sessions last saved activity
	public void resume(long sessionId, long at) {
		if (lastActiveAt >= 0 && (sessionId != activeSession || at <= lastActiveAt)) return;
		activeSession = sessionId;
		lastActiveAt = at;
	}

	// the break going on now, null = none
	public BreakEntry current(long now) {
		if (inShaft || lastActiveAt < 0 || now - lastActiveAt <= BREAK_MILLIS) return null;
		Journal.Session current = session.apply(now);
		if (current == null || current.id() != activeSession) return null;
		return new BreakEntry(BreakEntry.VERSION, activeSession, lastActiveAt, now);
	}

	private void mining(long now) {
		if (inShaft) mark(now);
		else if (inMiningArea.getAsBoolean()) active(now);
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
