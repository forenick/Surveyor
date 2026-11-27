package dev.skysurveyor.screen;

import java.util.ArrayList;
import java.util.List;

// how the range chips sit when one row is too narrow (wide font, scale 4)
public final class RangeFit {
	// word = "Range" in front. rows = chips per row. extraBelow = the extra part gets its own row
	public record Fit(boolean word, List<Integer> rows, boolean extraBelow) {
	}

	private RangeFit() {
	}

	// word + extra come w/ their own gaps, 0 = none. chips never drop, they wrap
	public static Fit fit(int room, int word, List<Integer> chips, int gap, int extra) {
		int all = -gap;
		for (int chip : chips) all += chip + gap;
		List<Integer> one = List.of(chips.size());
		if (word + all + extra <= room) return new Fit(word > 0, one, false);
		if (all + extra <= room) return new Fit(false, one, false);
		if (all <= room) return new Fit(word > 0 && word + all <= room, one, extra > 0);
		List<Integer> rows = new ArrayList<>();
		int used = 0;
		int count = 0;
		for (int chip : chips) {
			int with = count == 0 ? chip : used + gap + chip;
			// a chip wider than the room still gets its row
			if (count > 0 && with > room) {
				rows.add(count);
				used = chip;
				count = 1;
			} else {
				used = with;
				count++;
			}
		}
		rows.add(count);
		return new Fit(false, rows, extra > 0 && used + extra > room);
	}
}
