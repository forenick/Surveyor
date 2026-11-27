package dev.skysurveyor.journal;

// stash.jsonl line: material stash growth between 2 stash msgs. epoch ms
// new fields at the end, bump VERSION if a meaning changes
public record StashEntry(int version, long sessionId, long at, long grew) {
	public static final int VERSION = 1;
}
