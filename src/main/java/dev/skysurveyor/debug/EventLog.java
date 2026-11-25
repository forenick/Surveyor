package dev.skysurveyor.debug;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import dev.skysurveyor.detection.ShaftEvent;
import dev.skysurveyor.detection.ShaftEvent.ColdChanged;
import dev.skysurveyor.detection.ShaftEvent.FossilEssence;

// last events, unmatched chat + errors for /sv debug log. any thread
public final class EventLog {
	// display choice
	public static final int SIZE = 200;

	public enum Kind {
		EVENT, UNMATCHED, ERROR
	}

	public record Line(long at, Kind kind, String text) {
	}

	private final Deque<Line> lines = new ArrayDeque<>();
	private int unmatched;

	// fossil bars come 2 to 3 per block, cold every few secs. theyd push the shaft events + errors out
	public static boolean keeps(ShaftEvent event) {
		return !(event instanceof FossilEssence || event instanceof ColdChanged);
	}

	public synchronized void add(long at, Kind kind, String text) {
		lines.addLast(new Line(at, kind, text));
		if (lines.size() > SIZE) lines.removeFirst();
		if (kind == Kind.UNMATCHED) unmatched++;
	}

	public synchronized List<Line> lines() {
		return new ArrayList<>(lines);
	}

	// null = none yet
	public synchronized Line last(Kind kind) {
		var it = lines.descendingIterator();
		while (it.hasNext()) {
			Line line = it.next();
			if (line.kind() == kind) return line;
		}
		return null;
	}

	// since start, not just whats still kept
	public synchronized int unmatched() {
		return unmatched;
	}
}
