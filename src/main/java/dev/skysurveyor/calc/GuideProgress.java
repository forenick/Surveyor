package dev.skysurveyor.calc;

import java.util.ArrayList;
import java.util.List;

import dev.skysurveyor.calc.YourNumbers.Source;
import dev.skysurveyor.journal.JournalEntry;
import dev.skysurveyor.journal.JournalStats.SessionSummary;
import dev.skysurveyor.journal.StatsRange;
import dev.skysurveyor.mining.Efficiency;

// line on top of the guide while starting values run it (nick 11-27). his text to edit
// spawns in the range, mined = shafts behind the mining numbers, shafts = every shaft w/ corpse odds
// typed numbers count as in
public record GuideProgress(int spawns, boolean spawnsIn, int mined, boolean minedIn, int shafts, boolean shaftsIn) {
	public static final GuideProgress DONE = new GuideProgress(Learner.MIN_SPAWNS, true, 1, true, Learner.MIN_SHAFTS, true);
	public static final String TITLE = "Starting values";

	public static GuideProgress of(Learner.Input in, YourNumbers numbers, GameData data) {
		List<JournalEntry> range = StatsRange.flat(in.sessions(), SessionSummary::entries);
		List<JournalEntry> allTime = in.allTime() == null ? range : in.allTime();
		// same pick as Learner.learn
		int mined = (int) (in.minedShafts() > 0 ? allTime : range).stream().filter(entry -> Learner.mined(entry, data)).count();
		int shafts = (int) allTime.stream().filter(Learner::corpseShaft).count();
		boolean odds = shafts >= Learner.MIN_SHAFTS
				|| numbers.num(NumberId.EXTRA_CORPSE).source() != Source.START && numbers.num(NumberId.LAPIS_SHARE).source() != Source.START;
		return new GuideProgress(Learner.spawns(in.sessions()), numbers.num(NumberId.SPAWN).source() != Source.START, mined, numbers.ownPace(),
				shafts, odds);
	}

	public boolean shows() {
		return !spawnsIn || !minedIn || !shaftsIn;
	}

	// only whats missing
	public String line() {
		List<String> missing = new ArrayList<>();
		if (!spawnsIn) missing.add(spawnCount());
		if (!minedIn) missing.add(minedCount());
		if (!shaftsIn) missing.add(shaftCount());
		return "§7Starting values until yours are in: §f" + String.join("§7, §f", missing);
	}

	public List<String> hover() {
		long minutes = Efficiency.RECORD_MILLIS / 60_000;
		return List.of("§f" + TITLE,
				"§7Part of the list runs on general numbers",
				"§7until enough of your own is tracked",
				"§7Spawn numbers: " + state(spawnsIn, spawns >= Learner.MIN_SPAWNS, spawnCount()),
				"§7Mining numbers: " + state(minedIn, mined > 0, minedCount()),
				"§7Corpse odds: " + state(shaftsIn, shafts >= Learner.MIN_SHAFTS, shaftCount()),
				"§8Spawns count in the range at the top",
				"§8A mined shaft is " + minutes + " minutes on its own gem",
				"§8Until mining is in, the entry title shows no verdict",
				"§8Typing your own number counts too");
	}

	private static String state(boolean in, boolean tracked, String count) {
		if (!in) return "§f" + count;
		return tracked ? "§aTracked" : "§eSet by you";
	}

	private String spawnCount() {
		return Math.min(spawns, Learner.MIN_SPAWNS) + " of " + Learner.MIN_SPAWNS + " spawns";
	}

	// ticks need one since the patch, so a not-in shows 0
	private String minedCount() {
		return (minedIn ? 1 : 0) + " of 1 mined shaft";
	}

	private String shaftCount() {
		return Math.min(shafts, Learner.MIN_SHAFTS) + " of " + Learner.MIN_SHAFTS + " shafts";
	}
}
