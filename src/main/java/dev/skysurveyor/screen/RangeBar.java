package dev.skysurveyor.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import dev.skysurveyor.journal.StatsRange;

import static dev.skysurveyor.screen.ScreenParts.PANEL;
import static dev.skysurveyor.screen.ScreenParts.applyTooltip;
import static dev.skysurveyor.screen.ScreenParts.label;

// the range chips of stats + calculator, one look (review 11-27)
final class RangeBar {
	// display choices, not game numbers
	static final String WORD = "Range";
	static final int GAP = 3;
	static final int CHIP_HEIGHT = 12;
	// left + right of the bar
	static final int PADDING = 6;
	private static final int CHIP_PAD = 4;
	private static final int WORD_MARGIN = 2;
	private static final int ON = 0x24FFFF55;
	private static final int EDGE = 0x80FFFF55;

	private RangeBar() {
	}

	// width = the bar's own. extra = sits right of the chips, null = none
	static FlowLayout build(Font font, int width, StatsRange picked, List<String> wordTip, Consumer<StatsRange> pick, UIComponent extra,
			int extraWidth) {
		StatsRange[] ranges = StatsRange.values();
		List<Integer> widths = new ArrayList<>();
		for (StatsRange range : ranges) widths.add(chipWidth(font, range.displayName()));
		// spacer sits between 2 gaps
		RangeFit.Fit fit = RangeFit.fit(width - 2 * PADDING, font.width(WORD) + WORD_MARGIN + GAP, widths, GAP,
				extra == null ? 0 : extraWidth + 2 * GAP);
		FlowLayout bar = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		bar.surface(PANEL);
		bar.padding(Insets.of(2, 2, PADDING, PADDING));
		bar.gap(2);
		int next = 0;
		FlowLayout row = null;
		for (int count : fit.rows()) {
			row = row();
			if (next == 0 && fit.word()) {
				LabelComponent word = label("§7" + WORD);
				word.margins(Insets.right(WORD_MARGIN));
				tip(word, wordTip);
				row.child(word);
			}
			for (int i = 0; i < count; i++) {
				StatsRange range = ranges[next++];
				boolean on = range == picked;
				row.child(chip(range.displayName(), on,
						List.of("§f" + range.displayName(), "§7" + range.description(), on ? "§8In use" : "§8Click to use it"), () -> {
							if (!on) pick.accept(range);
						}));
			}
			bar.child(row);
		}
		if (extra != null) {
			if (fit.extraBelow()) {
				row = row();
				bar.child(row);
			}
			row.child(UIContainers.horizontalFlow(Sizing.expand(), Sizing.fixed(1)));
			row.child(extra);
		}
		return bar;
	}

	private static FlowLayout row() {
		// + the outline of an extra's box, so both tabs' bars are as tall
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(CHIP_HEIGHT + 2));
		row.gap(GAP);
		row.verticalAlignment(VerticalAlignment.CENTER);
		return row;
	}

	static int chipWidth(Font font, String text) {
		return font.width(text) + 2 * CHIP_PAD;
	}

	// flat, yellow = picked or open
	static FlowLayout chip(String text, boolean on, List<String> tip, Runnable click) {
		FlowLayout chip = new HoverFlow(Sizing.content(), Sizing.fixed(CHIP_HEIGHT), HoverFlow.ROW);
		chip.verticalAlignment(VerticalAlignment.CENTER);
		chip.padding(Insets.horizontal(CHIP_PAD));
		if (on) chip.surface(Surface.flat(ON).and(Surface.outline(EDGE)));
		chip.child(label((on ? "§e" : "§7") + text));
		tip(chip, tip);
		chip.mouseDown().subscribe((event, doubleClick) -> {
			if (event.button() != 0) return false;
			click.run();
			return true;
		});
		return chip;
	}

	private static void tip(UIComponent component, List<String> lines) {
		applyTooltip(component, lines.stream().map(line -> (Component) Component.literal(line)).toList());
	}
}
