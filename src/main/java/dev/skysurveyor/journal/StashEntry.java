package dev.skysurveyor.journal;

// one line of stash.jsonl: material stash growth between 2 stash msgs while mining. times epoch ms
// new fields at the end, bump VERSION if a meaning changes
public record StashEntry(int version, long sessionId, long at, long grew) {
	public static final int VERSION = 1;
}
