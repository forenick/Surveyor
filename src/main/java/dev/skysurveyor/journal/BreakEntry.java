package dev.skysurveyor.journal;

// breaks.jsonl line: gap w/o mining over BREAK_MILLIS. epoch ms
// from = last mining/exit, to = next mining/entry. whole gap, counted part on read
// new fields at the end, bump VERSION if a meaning changes
public record BreakEntry(int version, long sessionId, long from, long to) {
	public static final int VERSION = 1;
}
