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

	// points oldest left, 0.69 = 69%. usual = dashed line, null = none. low..high = bottom..top row
	record Line(List<Double> points, Double usual, int argb, double low, double high) implements HudGraph {
		public static final int HEIGHT = 16;
		// as wide as 12 spawn chart columns
		public static final int WIDTH = 59;
		// display choice: at least 20 points tall, so a steady shaft isnt a wild line
		public static final double MIN_SPAN = 0.2;

		public Line {
			points = List.copyOf(points);
		}

		// scaled to its own points + the usual, like the spawn chart scales to its shafts
		public static Line of(List<Double> points, Double usual, int argb) {
			double low = usual == null ? Double.MAX_VALUE : usual;
			double high = usual == null ? -Double.MAX_VALUE : usual;
			for (double point : points) {
				low = Math.min(low, point);
				high = Math.max(high, point);
			}
			if (low > high) {
				low = 0;
				high = 1;
			}
			double grow = Math.max(0, MIN_SPAN - (high - low)) / 2;
			return new Line(points, usual, argb, low - grow, high + grow);
		}

		@Override
		public int height() {
			return HEIGHT;
		}

		@Override
		public int minWidth() {
			return WIDTH;
		}

		// row from the top for a value
		public int y(double value) {
			double part = high > low ? (value - low) / (high - low) : 0.5;
			return HEIGHT - 1 - (int) Math.round(Math.clamp(part, 0, 1) * (HEIGHT - 1));
		}

		// row at column x of a WIDTH wide line, points spread evenly
		public int yAt(int x) {
			if (points.size() == 1) return y(points.getFirst());
			double at = x * (points.size() - 1) / (double) (WIDTH - 1);
			int i = Math.min((int) at, points.size() - 2);
			double part = at - i;
			return y(points.get(i) * (1 - part) + points.get(i + 1) * part);
		}

		public Integer usualY() {
			return usual == null ? null : y(usual);
		}
	}
}
