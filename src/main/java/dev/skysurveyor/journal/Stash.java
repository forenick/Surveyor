package dev.skysurveyor.journal;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.regex.Matcher;

import dev.skysurveyor.detection.ShaftEvent;
import dev.skysurveyor.detection.TextPatterns;

// full sack = items to stash, no sacks msg (research/09)
// msg ~once a min while it holds items, no names so count only
// 1st msg of a session = baseline, pickups never negative
// not a ShaftEvent: recordings w/ live lines never had it
public final class Stash {
	private final TextPatterns patterns;
	private final MiningClock clock;
	private final LongFunction<Journal.Session> session;
	private final Consumer<StashEntry> listener;
	private long lastCount = -1;
	// -1 = none
	private long countSession = -1;
	private long lastMessageAt = -1;

	public Stash(TextPatterns patterns, BooleanSupplier inMiningArea, LongFunction<Journal.Session> session,
			Consumer<StashEntry> listener) {
		this.patterns = patterns;
		this.clock = new MiningClock(MiningClock.Scope.MINES_OR_SHAFT, inMiningArea);
		this.session = session;
		this.listener = listener;
	}

	public static long grew(List<StashEntry> entries, long sessionId) {
		return entries.stream().filter(e -> e.sessionId() == sessionId).mapToLong(StashEntry::grew).sum();
	}

	public void onBlockHit(long now) {
		clock.onBlockHit(now);
	}

	public void onEvent(ShaftEvent event, long now) {
		clock.onEvent(event, now);
	}

	// chat only, not action bar
	public void onChat(long now, String legacy) {
		Matcher m = patterns.match("stash.count", TextPatterns.clean(legacy));
		if (m == null) return;
		long count = TextPatterns.number(m.group("count"));
		Journal.Session current = session.apply(now);
		long grew = count - lastCount;
		boolean mined = clock.minedSince(lastMessageAt);
		if (current != null && current.id() == countSession && grew > 0 && mined) {
			listener.accept(new StashEntry(StashEntry.VERSION, current.id(), now, grew));
		}
		lastCount = count;
		countSession = current == null ? -1 : current.id();
		lastMessageAt = now;
	}
}
