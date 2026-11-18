package dev.skysurveyor.hud;

import java.util.List;

// one drawn row of a box. text null = graph only, graph null = text only
// right != null = 2 columns: text left, right at the right edge, alignment ignored (drops line)
// rule = thin line above. indent = px before the left text
// sizes = display choices from docs/mockups/hud-drops-v1.html
public record HudRow(String text, HudGraph graph, String right, boolean rule, int indent) {
	public static final int COLUMN_GAP = 10;
	public static final int RULE = 0x30FFFFFF;
	// both: items under their source
	public static final int SUB_INDENT = 4;
	// 1 + the gap above = 2 px over the rule, 2 under
	static final int RULE_ABOVE = 1;
	static final int RULE_BELOW = 2;
	static final int RULE_HEIGHT = RULE_ABOVE + 1 + RULE_BELOW;

	public HudRow(String text, HudGraph graph) {
		this(text, graph, null, false, 0);
	}

	public static HudRow text(String text) {
		return new HudRow(text, null);
	}

	public static HudRow columns(String left, String right, boolean rule) {
		return new HudRow(left, null, right == null ? "" : right, rule, 0);
	}

	public static HudRow sub(String left, String right) {
		return new HudRow(left, null, right, false, SUB_INDENT);
	}

	HudRow withRule() {
		return new HudRow(text, graph, right, true, indent);
	}

	public boolean columns() {
		return right != null;
	}

	// w/o the gap under it
	int height(int lineHeight) {
		return (rule ? RULE_HEIGHT : 0) + (text == null ? 0 : lineHeight) + (graph == null ? 0 : HudGraph.ABOVE + graph.height());
	}

	int gapBelow(int lineGap) {
		return graph == null ? lineGap : HudGraph.BELOW;
	}

	// all rows, w/o padding. no gap under the last one
	static int height(List<HudRow> rows, int lineHeight, int lineGap) {
		int height = 0;
		for (int i = 0; i < rows.size(); i++) {
			height += rows.get(i).height(lineHeight);
			if (i < rows.size() - 1) height += rows.get(i).gapBelow(lineGap);
		}
		return height;
	}

	static int minWidth(List<HudRow> rows) {
		int width = 0;
		for (HudRow row : rows) {
			if (row.graph != null) width = Math.max(width, row.graph.minWidth());
		}
		return width;
	}
}
