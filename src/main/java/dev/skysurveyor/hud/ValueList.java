package dev.skysurveyor.hud;

import java.util.ArrayList;
import java.util.List;

import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import dev.skysurveyor.hud.EditorParts.Chip;
import dev.skysurveyor.hud.EditorParts.HoverRow;

import static dev.skysurveyor.hud.EditorParts.CHOICE;
import static dev.skysurveyor.hud.EditorParts.COLOR_NAMES;
import static dev.skysurveyor.hud.EditorParts.MENU_SWATCH;
import static dev.skysurveyor.hud.EditorParts.PANEL;
import static dev.skysurveyor.hud.EditorParts.RANGE_TEXT;
import static dev.skysurveyor.hud.EditorParts.section;
import static dev.skysurveyor.hud.EditorParts.spacer;
import static dev.skysurveyor.screen.ScreenParts.label;
import static dev.skysurveyor.screen.ScreenParts.strip;

final class ValueList {
	// display choice
	private static final int FORMAT_TEXT = 0x6A6A6A;
	private static final Identifier SEARCH_ICON = EditorParts.icon("search_icon");
	private static final int FORMAT_OFF = 0x333333;

	private final EditHudScreen screen;
	private final AdvancedPage page;
	private FlowLayout panel;
	private FlowLayout content;
	private FlowLayout mineGroup;
	private String mineSource;
	private final List<Chip> formatChips = new ArrayList<>();
	private boolean formatsOff;

	ValueList(EditHudScreen screen, AdvancedPage page) {
		this.screen = screen;
		this.page = page;
	}

	FlowLayout panel() {
		return panel;
	}

	boolean contains(double x, double y) {
		return panel != null && panel.isInBoundingBox(x, y);
	}

	void build() {
		int w = screen.listWidth();
		int inner = w - 8;
		panel = UIContainers.verticalFlow(Sizing.fixed(w), Sizing.fixed(screen.panelHeight()));
		panel.surface(PANEL);
		panel.padding(Insets.of(4));
		panel.gap(2);
		// headings + gaps would pass clicks to boxes below
		panel.mouseDown().subscribe((click, doubled) -> true);
		FlowLayout head = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		head.child(label("§fValues"));
		head.child(spacer());
		head.child(label("§8click to type"));
		panel.child(head);

		FlowLayout keys = UIContainers.ltrTextFlow(Sizing.fill(100), Sizing.content());
		keys.gap(1);
		for (String k : new String[] {">", ">=", "<", "<=", "=", "!="}) keys.child(key(k, " " + k + " ", "Compare, for colour rules"));
		for (String k : new String[] {"+", "-", "*", "/"}) keys.child(key(k, " " + k + " ", "Maths, inside { }"));
		keys.child(key("(", "(", "Maths, inside { }"));
		keys.child(key(")", ")", "Maths, inside { }"));
		panel.child(keys);

		FlowLayout swatches = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		swatches.gap(1);
		int size = Math.min(MENU_SWATCH, (inner - 15) / 16);
		for (int i = 0; i < 16; i++) {
			String code = String.valueOf(HudLayout.COLOR_CODES.charAt(i));
			FlowLayout swatch = UIContainers.horizontalFlow(Sizing.fixed(size), Sizing.fixed(size));
			swatch.surface(Surface.flat(0xFF000000 | HudLayout.COLOR_RGB[i]).and(Surface.outline(0xFF000000)));
			swatch.cursorStyle(CursorStyle.HAND);
			screen.tip(swatch, COLOR_NAMES[i] + " (&" + code + ")");
			swatch.mouseDown().subscribe((click, doubled) -> {
				page.typeRaw("&" + code);
				return true;
			});
			swatches.child(swatch);
		}
		panel.child(swatches);

		FlowLayout styles = UIContainers.ltrTextFlow(Sizing.fill(100), Sizing.content());
		styles.gap(1);
		String[][] codes = {{"&l", "Bold"}, {"&o", "Italic"}, {"&n", "Underline"}, {"&m", "Strikethrough"},
				{"&r", "Box label colour"}, {"&v", "Box value colour"}};
		for (String[] code : codes) styles.child(key(code[0], code[0], code[1] + ", until the next colour"));
		panel.child(styles);

		TextBoxComponent search = screen.textField(screen.listSearch(), 32, typed -> {
			screen.listSearch(typed);
			fill();
		});
		search.verticalSizing(Sizing.fixed(CHOICE));
		search.setHint(Component.literal("Search"));
		screen.tip(search, "Search values by name");
		// magnifier like settings
		FlowLayout searchRow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		searchRow.gap(3);
		searchRow.verticalAlignment(VerticalAlignment.CENTER);
		searchRow.child(UIComponents.texture(SEARCH_ICON, 0, 0, 8, 8, 8, 8));
		searchRow.child(search);
		panel.child(searchRow);

		content = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		content.padding(Insets.right(3));
		ScrollContainer<FlowLayout> scroll = UIContainers.verticalScroll(Sizing.fill(100), Sizing.expand(), content);
		scroll.scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(0x80FFFFFF)));
		scroll.scrollbarThiccness(2);
		panel.child(scroll);
		fill();
		screen.root().child(panel);
	}

	private Chip key(String text, String typed, String tooltip) {
		Chip key = new Chip(text, 0xFFFFFF, () -> page.typeRaw(typed));
		key.surface(Surface.flat(0xFF3A3A3A).and(Surface.outline(0xFF000000)));
		key.padding(Insets.of(1, 1, 3, 3));
		screen.tip(key, tooltip);
		return key;
	}

	// minus padding + scrollbar
	private int inner() {
		return screen.listWidth() - 8 - 3;
	}

	private String now(HudValue value, HudValue.Range range, String target) {
		return strip(HudBoxText.paint(HudBoxText.text(value, range, target, 0, screen.data(), true), "7", ""));
	}

	private void fill() {
		if (content == null) return;
		content.clearChildren();
		formatChips.clear();
		mineGroup = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		mineSource = null;
		content.child(mineGroup);
		int inner = inner();
		for (HudValue.Group group : HudValue.Group.values()) {
			List<HudValue> shown = ValueListing.tags(group, screen.listSearch());
			if (shown.isEmpty()) continue;
			content.child(section(group.displayName()));
			for (HudValue value : shown) {
				HoverRow row = new HoverRow();
				String nowText = screen.clip("§8" + now(value, value.defaultRange(), null), inner / 3);
				row.child(label(screen.clip("§f{" + value.tag() + "}", inner - screen.getFont().width(nowText) - 4)));
				row.child(spacer());
				row.child(label(nowText));
				screen.tip(row, "§f" + value.displayName() + "\n§7" + value.description() + "\n§eClick to type {" + value.tag() + "}");
				row.mouseDown().subscribe((click, doubled) -> {
					page.typeValue(value.tag());
					return true;
				});
				content.child(row);
				if (!value.targets().isEmpty()) {
					FlowLayout options = optionRow();
					for (String target : value.targets()) {
						Chip chip = new Chip(":" + target, RANGE_TEXT, () -> page.typeOption(value, target));
						screen.tip(chip, "{" + value.tag() + ":" + target + "}: " + value.targetName(target) + ". Now "
								+ now(value, value.defaultRange(), target));
						options.child(chip);
					}
					content.child(options);
				}
				if (value.ranges().size() > 1) {
					FlowLayout options = optionRow();
					for (HudValue.Range range : value.ranges()) {
						Chip chip = new Chip(":" + range.tagName(), RANGE_TEXT, () -> page.typeOption(value, range.tagName()));
						screen.tip(chip, "{" + value.tag() + ":" + range.tagName() + "}: " + range.description()
								+ ". Now " + now(value, range, null));
						options.child(chip);
					}
					content.child(options);
				}
				List<String> formats = HudFormat.names(value.kind());
				if (value.hasFormatChoice() && !formats.isEmpty()) {
					FlowLayout options = optionRow();
					for (int i = 0; i < formats.size(); i++) {
						String format = formats.get(i);
						Chip chip = new Chip(":" + format, FORMAT_TEXT, () -> {
							if (!screen.typingRules()) page.typeOption(value, format);
						});
						screen.tip(chip, "{" + value.tag() + ":" + format + "} shows as "
								+ HudFormat.example(value.kind(), i, HudBoxText.suffix(value, value.defaultRange()))
								+ ". Formats don't matter in rules.");
						formatChips.add(chip);
						options.child(chip);
					}
					content.child(options);
				}
			}
		}
		formatsOff = false;
	}

	private static FlowLayout optionRow() {
		FlowLayout row = UIContainers.ltrTextFlow(Sizing.fill(100), Sizing.content());
		row.margins(Insets.of(0, 1, 6, 0));
		row.gap(3);
		return row;
	}

	// formats greyed while typing rules, "in this line" follows the text
	void refresh(boolean typingRules, String text) {
		if (formatsOff != typingRules) {
			formatsOff = typingRules;
			for (Chip chip : formatChips) chip.color(typingRules ? FORMAT_OFF : FORMAT_TEXT);
		}
		if (mineGroup != null && !text.equals(mineSource)) fillMine(text);
	}

	// tags as written, rules from these always match
	private void fillMine(String text) {
		mineSource = text;
		mineGroup.clearChildren();
		List<String> tags = HudBoxText.numberTags(text);
		if (tags.isEmpty()) return;
		mineGroup.child(section("In this line"));
		int inner = inner();
		for (String tag : tags) {
			HoverRow row = new HoverRow();
			row.child(label(screen.clip("§f{" + tag + "}", inner)));
			screen.tip(row, "Click to type it, for example to start a rule for it");
			row.mouseDown().subscribe((click, doubled) -> {
				page.typeValue(tag);
				return true;
			});
			mineGroup.child(row);
		}
	}

	// sizes known after layout
	void fit(UIComponent beside) {
		int width = screen.width;
		boolean panelRight = beside.x() > width / 2;
		int x = panelRight ? beside.x() - 2 - panel.width() : beside.x() + beside.width() + 2;
		int y = beside.y();
		FlowLayout root = screen.root();
		int pad = EditHudScreen.PADDING;
		if (x != panel.x() || y != panel.y()) panel.positioning(Positioning.absolute(x - root.x() - pad, y - root.y() - pad));
		if (panel.height() != beside.height()) panel.verticalSizing(Sizing.fixed(beside.height()));
	}

	void remove() {
		screen.dropFocusIn(panel);
		screen.root().removeChild(panel);
	}
}
