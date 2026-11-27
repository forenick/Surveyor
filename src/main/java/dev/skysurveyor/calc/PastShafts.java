package dev.skysurveyor.calc;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import dev.skysurveyor.journal.CorpseCounts;
import dev.skysurveyor.journal.JournalEntry;
import dev.skysurveyor.money.ShaftValue;
import dev.skysurveyor.money.ShaftValue.Part;
import dev.skysurveyor.util.Stats;

// the guide vs what ur mined shafts made, same prices (nick 11-26)
public final class PastShafts {
	private PastShafts() {
	}

	// looted = every corpse type, each gives pristine. predicted + made = gems + other drops, no corpse loot
	public record Line(JournalEntry entry, int looted, double predicted, double made) {
		// +0.05 = made 5% more than the guide said
		public double off() {
			return predicted <= 0 ? 0 : made / predicted - 1;
		}
	}

	// medianOff null = no lines
	public record Check(List<Line> lines, Double medianOff) {
		public static final Check NONE = new Check(List.of(), null);
	}

	// shown + in the median. display choice
	public static final int NEWEST = 12;

	// every shaft, any order. lines = the newest NEWEST mined ones, newest first. state null = fiesta unknown, every shaft
	// the numbers come from the newest of these, so the median sits near 0. the spread is the news
	static Check check(List<JournalEntry> entries, Function<JournalEntry, ShaftValue> values, ShaftModel.Guide guide, Fiesta.State state,
			GameData data) {
		if (guide == null) return Check.NONE;
		List<JournalEntry> newestFirst = new ArrayList<>(entries);
		newestFirst.sort((a, b) -> Long.compare(b.enteredAt(), a.enteredAt()));
		List<Line> lines = new ArrayList<>();
		for (JournalEntry entry : newestFirst) {
			if (lines.size() >= NEWEST) break;
			// saved b4 fiesta was tracked = in, part flask fits no state (like Learner.perState)
			Fiesta.State mine = Learner.stateOf(entry);
			boolean otherState = state != null && (mine != null ? mine != state : entry.fiesta() != null);
			if (!Learner.mined(entry, data) || otherState) continue;
			// the row has no campfire, its minutes would look like a win
			if (entry.campfiresAt() != null && !entry.campfiresAt().isEmpty()) continue;
			ShaftModel.Row row = guide.row(entry.shaftType(), entry.variant());
			ShaftValue value = values.apply(entry);
			if (row == null || row.perFine() == null || value == null) continue;
			int looted = CorpseCounts.looted(CorpseCounts.of(entry));
			double made = value.total(Part.MINED) + value.total(Part.OTHER) + value.total(Part.GLOSSY) + value.total(Part.SCRAP)
					+ value.total(Part.FOSSIL);
			lines.add(new Line(entry, looted, row.lapis().get(Math.min(looted, ShaftModel.MAX_LAPIS)).coins(), made));
		}
		return new Check(List.copyOf(lines), lines.isEmpty() ? null : Stats.median(lines.stream().map(Line::off).toList()));
	}
}
