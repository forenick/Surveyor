package dev.skysurveyor.calc;

import java.util.ArrayList;
import java.util.List;

import dev.skysurveyor.detection.Shaft.ColdStep;
import dev.skysurveyor.detection.Shaft.ExitReason;
import dev.skysurveyor.journal.JournalEntry;
import dev.skysurveyor.util.Stats;

// fit to the real cold pace, so buffs + potions count w/o reading stats
public final class FreezeClock {
	// display choice, follows a buff ending in ~2 min
	static final int WINDOW = 20;
	// w/o a journal prior, display choice
	static final int MIN_STEPS = 5;
	// journal pace weighs as this many, display choice
	static final double PRIOR_WEIGHT = 5;
	// faster = mob hit, not cold (rec 5: +3 in 4 s)
	static final double TOO_FAST = 0.6;
	static final double TOO_SLOW = 1.6;
	// else mod started mid shaft, 150 s steps unknown
	static final long START_MILLIS = 40_000;
	// display choice
	static final int PRIOR_FREEZES = 10;

	private FreezeClock() {
	}

	// ms since entry, null = cant tell yet. prior = journal exposure per cold, null = none
	public static Long freezeAt(List<ColdStep> steps, Double prior) {
		if (steps.isEmpty()) return prior == null ? null : at(0, 0, prior);
		ColdStep first = steps.getFirst();
		if (first.millis() > START_MILLIS || level(first) > 1) return null;
		Double perCold = perCold(steps, prior);
		if (perCold == null) return null;
		ColdStep last = steps.getLast();
		return at(last.millis(), level(last), perCold);
	}

	private static long at(long millis, int level, double perCold) {
		if (level >= ColdModel.FREEZE_COLD) return millis;
		double exposure = ColdModel.exposure(millis / 1000.0) + (ColdModel.FREEZE_COLD - level) * perCold;
		return Math.round(ColdModel.seconds(exposure) * 1000);
	}

	// null = too few changes
	static Double perCold(List<ColdStep> steps, Double prior) {
		List<Double> gaps = new ArrayList<>();
		// 1st value = when the scoreboard showed up
		for (int i = Math.max(2, steps.size() - WINDOW); i < steps.size(); i++) {
			ColdStep from = steps.get(i - 1);
			ColdStep to = steps.get(i);
			// campfires + mob hits jump, only +1s count
			if (level(to) != level(from) + 1) continue;
			gaps.add(ColdModel.exposure(to.millis() / 1000.0) - ColdModel.exposure(from.millis() / 1000.0));
		}
		double sum = 0;
		int count = 0;
		if (!gaps.isEmpty()) {
			double median = Stats.median(gaps);
			for (double gap : gaps) {
				if (gap < TOO_FAST * median || gap > TOO_SLOW * median) continue;
				sum += gap;
				count++;
			}
		}
		if (prior != null) return (sum + PRIOR_WEIGHT * prior) / (count + PRIOR_WEIGHT);
		return count < MIN_STEPS ? null : sum / count;
	}

	// null = none
	public static Double prior(List<JournalEntry> entries) {
		Prior prior = prior(entries, PRIOR_FREEZES);
		return prior == null ? null : prior.perCold();
	}

	public record Prior(double perCold, int freezes) {
	}

	// campfire under 50 cold wastes some, those shafts say nothing
	public static Prior prior(List<JournalEntry> entries, int max) {
		List<Double> found = new ArrayList<>();
		for (int i = entries.size() - 1; i >= 0 && found.size() < max; i--) {
			JournalEntry entry = entries.get(i);
			if (entry.exitReason() != ExitReason.DEATH) continue;
			Integer campfires = fullCampfires(entry);
			if (campfires == null) continue;
			double cold = ColdModel.FREEZE_COLD + ColdModel.CAMPFIRE_COLD * campfires;
			found.add(ColdModel.exposure(entry.durationMillis() / 1000.0) / cold);
		}
		return found.isEmpty() ? null : new Prior(Stats.median(found), found.size());
	}

	// null = used too early or w/o a cold line
	private static Integer fullCampfires(JournalEntry entry) {
		List<Integer> colds = entry.campfireColds();
		// before 0.22.0 only placed is known
		if (colds == null) return entry.campfiresAt().isEmpty() ? 0 : null;
		for (Integer cold : colds) {
			if (cold == null || Math.abs(cold) < ColdModel.CAMPFIRE_COLD) return null;
		}
		return colds.size();
	}

	private static int level(ColdStep step) {
		return Math.abs(step.cold());
	}

}
