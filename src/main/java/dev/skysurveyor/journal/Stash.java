package dev.skysurveyor.journal;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.regex.Matcher;

import dev.skysurveyor.detection.ShaftEvent;
import dev.skysurveyor.detection.ShaftEvent.PickaxeAbility;
import dev.skysurveyor.detection.ShaftEvent.ShaftEntered;
import dev.skysurveyor.detection.ShaftEvent.ShaftLeft;
import dev.skysurveyor.detection.TextPatterns;

// full sack = items go to the material stash, no sacks msg for them (research/09)
// stash msg comes ~once a minute while it holds items. no item names, so only a count
// 1st msg of a session = baseline, a pickup (drop) is never negative
// not a ShaftEvent: recordings w/ live lines never had it
public final class Stash {
	private final TextPatterns patterns;
	private final BooleanSupplier inMiningArea;
	private final LongFunction<Journal.Session> session;
	private final Consumer<StashEntry> listener;
	private boolean inShaft;
	private long lastMiningAt = -1;
	private long lastCount = -1;
	// session of lastCount, -1 = none
	private long countSession = -1;
	private long lastMessageAt = -1;

	public Stash(TextPatterns patterns, BooleanSupplier inMiningArea, LongFunction<Journal.Session> session,
			Consumer<StashEntry> listener) {
		this.patterns = patterns;
		this.inMiningArea = inMiningArea;
		this.session = session;
		this.listener = listener;
	}

	public static long grew(List<StashEntry> entries, long sessionId) {
		return entries.stream().filter(e -> e.sessionId() == sessionId).mapToLong(StashEntry::grew).sum();
	}

	public void onBlockHit(long now) {
		mining(now);
	}

	public void onEvent(ShaftEvent event, long now) {
		switch (event) {
			case PickaxeAbility ability -> mining(now);
			case ShaftEntered entered -> inShaft = true;
			case ShaftLeft left -> inShaft = false;
			default -> {
			}
		}
	}

	// chat only, not action bar
	public void onChat(long now, String legacy) {
		Matcher m = patterns.match("stash.count", TextPatterns.clean(legacy));
		if (m == null) return;
		long count = TextPatterns.number(m.group("count"));
		Journal.Session current = session.apply(now);
		long grew = count - lastCount;
		boolean mined = lastMiningAt >= 0 && lastMiningAt >= lastMessageAt;
		if (current != null && current.id() == countSession && grew > 0 && mined) {
			listener.accept(new StashEntry(StashEntry.VERSION, current.id(), now, grew));
		}
		lastCount = count;
		countSession = current == null ? -1 : current.id();
		lastMessageAt = now;
	}

	private void mining(long now) {
		if (inShaft || inMiningArea.getAsBoolean()) lastMiningAt = now;
	}
}
