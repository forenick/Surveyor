package dev.skysurveyor.journal;

// one line of breaks.jsonl: a gap w/o mining over Breaks.BREAK_MILLIS in a session. times epoch ms
// from = last mining or shaft exit, to = next mining or shaft entry. whole gap, the counted part is worked out on read
// new fields at the end, bump VERSION if a meaning changes
public record BreakEntry(int version, long sessionId, long from, long to) {
	public static final int VERSION = 1;
}
