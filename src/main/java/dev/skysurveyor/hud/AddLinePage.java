package dev.skysurveyor.hud;

import java.util.List;
import java.util.Locale;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.gui.Font;

import dev.skysurveyor.hud.EditorParts.Chip;
import dev.skysurveyor.hud.EditorParts.HoverRow;
import dev.skysurveyor.hud.HudLayout.Box;
import dev.skysurveyor.hud.HudLayout.Line;

import static dev.skysurveyor.hud.EditorParts.RANGE_TEXT;
import static dev.skysurveyor.hud.EditorParts.button;
import static dev.skysurveyor.hud.EditorParts.section;
import static dev.skysurveyor.hud.EditorParts.spacer;
import static dev.skysurveyor.screen.ScreenParts.label;

final class AddLinePage {
	// display choice
	private static final int PERIOD_INDENT = 8;
	private static final int PERIOD_GAP = 3;

	private final EditHudScreen screen;

	AddLinePage(EditHudScreen screen) {
		this.screen = screen;
	}

	private Font font() {
		return screen.getFont();
	}

	void build(FlowLayout panel, Box box) {
		Font font = font();
		int inner = screen.inner();
		FlowLayout header = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		header.gap(4);
		header.verticalAlignment(VerticalAlignment.CENTER);
		ButtonComponent back = button("‹ Back", b -> screen.backToLines());
		screen.tip(back, "Back without adding (Esc)");
		header.child(back);
		header.child(label(screen.clip("§7Pick a value or a period", inner - font.width("‹ Back") - 16)));
		panel.child(screen.crumbs(box, "Add a line"));
		panel.child(header);
		boolean full = ValueListing.periodsFit(box, inner - PERIOD_INDENT, PERIOD_GAP, font.width(" ✔"),
				word -> font.width(word) + 2 * Chip.PAD);
		for (HudValue.Group group : HudValue.Group.values()) {
			panel.child(section(group.displayName()));
			for (HudValue value : ValueListing.addLine(group)) {
				List<HudValue.Period> periods = value.periods();
				HoverRow row = new HoverRow();
				boolean have = periods.isEmpty() && ValueListing.has(box, value);
				String now = nowText(value, value.defaultRange());
				int nowWidth = Math.min(font.width(now), inner / 2);
				LabelComponent name = label(screen.clip("§f" + value.displayName(), inner - nowWidth - (have ? 12 : 4)));
				row.child(name);
				row.child(spacer());
				row.child(label(screen.clip(now, nowWidth)));
				if (have) {
					LabelComponent tick = label("§a✔");
					screen.tip(tick, "Already in this box. You can add it again.");
					row.child(tick);
				}
				Line added = new Line(value);
				screen.tip(row, addTip(box, value, value.period(value.defaultRange()), added));
				row.mouseDown().subscribe((click, doubled) -> screen.addLine(box, new Line(value)));
				panel.child(row);
				if (periods.isEmpty()) continue;
				FlowLayout options = periodRow();
				for (HudValue.Period period : periods) {
					boolean in = ValueListing.has(box, period);
					String word = full ? period.name().toLowerCase(Locale.ROOT) : period.shortName();
					Chip chip = new Chip(word + (in ? " §a✔" : ""), RANGE_TEXT, () -> screen.addLine(box, Line.of(period))).outlined();
					screen.tip(chip, addTip(box, value, period, Line.of(period)));
					options.child(chip);
				}
				panel.child(options);
			}
		}
	}

	private static FlowLayout periodRow() {
		FlowLayout row = UIContainers.ltrTextFlow(Sizing.fill(100), Sizing.content());
		row.margins(Insets.of(1, 2, PERIOD_INDENT, 0));
		row.gap(PERIOD_GAP);
		return row;
	}

	private String nowText(HudValue value, HudValue.Range range) {
		String color = value.defaultColor(value.defaultTarget());
		color = color == null ? "f" : color;
		return "§" + color + HudBoxText.paint(HudBoxText.text(value, range, null, 0, screen.data(), true), color, "");
	}

	private String addTip(Box box, HudValue value, HudValue.Period period, Line line) {
		HudValue shown = period == null ? value : period.value();
		HudValue.Range range = period == null ? value.defaultRange() : period.range();
		StringBuilder tip = new StringBuilder("§f").append(shown.lineName(range, null)).append("\n§7").append(value.description());
		if (period != null) tip.append("\n§7").append(period.description()).append('.');
		switch (value.target()) {
			case SHAFT_TYPE -> tip.append("\n§8Pick the shaft type on its line");
			case CORPSE_TYPE -> tip.append("\n§8Pick the corpse type on its line");
			case PERSONAL_BEST -> tip.append("\n§8Pick which personal best on its line. Make your own on the Stats tab");
			case NONE -> {
			}
		}
		tip.append("\n§7Now ").append(nowText(shown, range));
		return tip.append("\n§eAdds ").append(HudBoxText.line(box, line, screen.data(), true)).toString();
	}
}
