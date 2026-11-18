package dev.skysurveyor.hud;

import java.util.List;

// sizes = display choices from docs/mockups/hud-graphs-v1.html
public sealed interface HudGraph {
	int ABOVE = 1;
	int BELOW = 2;

	int height();

	// text area at least this wide
	int minWidth();

	// fill 0..1, argb
	record Bar(double fill, int argb) implements HudGraph {
		public static final int HEIGHT = 3;
		public static final int MIN_WIDTH = 60;
		public static final int TRACK = 0x28FFFFFF;

		public Bar {
			fill = Math.clamp(fill, 0, 1);
		}

		@Override
		public int height() {
			return HEIGHT;
		}

		@Override
		public int minWidth() {
			return MIN_WIDTH;
		}

		// whole pixels of a w wide bar
		public int filled(int width) {
			return (int) Math.round(fill * width);
		}
	}

	// columns oldest left. heights + line 0..1 of the chart, line null = none
	record Chart(List<Column> columns, Double line) implements HudGraph {
		public static final int HEIGHT = 16;
		public static final int COLUMN = 4;
		public static final int GAP = 1;
		public static final int LINE = 0xC0FFFFFF;
		public static final int DASH = 2;

		public Chart {
			columns = List.copyOf(columns);
		}

		@Override
		public int height() {
			return HEIGHT;
		}

		@Override
		public int minWidth() {
			return width();
		}

		public int width() {
			return columns.isEmpty() ? 0 : columns.size() * (COLUMN + GAP) - GAP;
		}

		// px, a shaft never vanishes
		public int columnHeight(int i) {
			return Math.max(1, (int) Math.round(Math.clamp(columns.get(i).height(), 0, 1) * HEIGHT));
		}

		// row from the top, null = no line
		public Integer lineY() {
			if (line == null) return null;
			return HEIGHT - Math.max(1, (int) Math.round(Math.clamp(line, 0, 1) * HEIGHT));
		}
	}

	record Column(double height, int argb) {
	}
}
