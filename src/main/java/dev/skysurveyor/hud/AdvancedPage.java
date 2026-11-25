package dev.skysurveyor.hud;

import java.util.List;
import java.util.function.Function;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import dev.skysurveyor.hud.HudLayout.Box;
import dev.skysurveyor.hud.HudLayout.Line;

import static dev.skysurveyor.hud.AdvancedText.RULES_HELP;
import static dev.skysurveyor.hud.AdvancedText.RULES_LIMIT;
import static dev.skysurveyor.hud.AdvancedText.TEXT_HINT;
import static dev.skysurveyor.hud.AdvancedText.TEXT_LIMIT;
import static dev.skysurveyor.hud.EditorParts.CHOICE;
import static dev.skysurveyor.hud.EditorParts.RESET_ICON;
import static dev.skysurveyor.hud.EditorParts.button;
import static dev.skysurveyor.hud.EditorParts.heading;
import static dev.skysurveyor.hud.EditorParts.iconTextButton;
import static dev.skysurveyor.hud.EditorParts.pixels;
import static dev.skysurveyor.hud.EditorParts.spacer;
import static dev.skysurveyor.screen.ScreenParts.label;

// line page in Advanced mode: text + rules fields, their hints, the value list. one per built page
final class AdvancedPage {
	private final EditHudScreen screen;
	private final Box box;
	private final Line line;
	private FreeTextArea textArea;
	private FreeTextArea rulesArea;
	private LabelComponent textHint;
	private String textHintText;
	private FlowLayout ruleHints;
	private List<String> ruleHintLines = List.of();
	private ValueList list;
	private String hintSource;
	private boolean hintNoRoom;
	// rule hints + placeholder redo only when text, rules or data change
	private String hintRules;
	private HudData hintData;
	private String placeholderSource;
	private HudData placeholderData;
	private String placeholderText;
	private String noRoomFor;

	AdvancedPage(EditHudScreen screen, Box box, Line line) {
		this.screen = screen;
		this.box = box;
		this.line = line;
	}

	ValueList list() {
		return list;
	}

	void build(FlowLayout panel) {
		int lineHeight = screen.getFont().lineHeight;
		FlowLayout textHead = heading("Text");
		textHead.child(spacer());
		boolean listOpen = screen.listOpen();
		ButtonComponent values = button(listOpen ? "§eValues" : "Values", b -> {
			screen.listOpen(!listOpen);
			screen.rebuildPanel();
		});
		values.verticalSizing(Sizing.fixed(CHOICE));
		screen.tip(values, listOpen ? "Hide the value list" : "Show the value list beside this panel: values, maths and colours to click");
		textHead.child(values);
		ButtonComponent reset = iconTextButton(screen.getFont(), pixels(RESET_ICON), "Reset", null, b -> {
			line.resetAdvanced(box);
			line.advanced = true;
			screen.rebuildPanel();
		});
		reset.verticalSizing(Sizing.fixed(CHOICE));
		reset.margins(Insets.left(2));
		screen.tip(reset, "Back to the Easy look plus the ready-made rules. Ctrl+Z undoes it.");
		textHead.child(reset);
		panel.child(textHead);

		String start = line.advancedText(box);
		textArea = new FreeTextArea(Sizing.fill(100), Sizing.fixed(3 * lineHeight + 8), false);
		// never below whats there, setValue would cut a hand edit + it gets saved
		textArea.setCharacterLimit(Math.max(TEXT_LIMIT, start.length()));
		textArea.text(start);
		textArea.margins(Insets.top(2));
		String[] last = {start};
		screen.field(textArea::getValue, typed -> {
			if (typed.indexOf('\n') >= 0 || typed.indexOf('\r') >= 0) {
				typed = AdvancedText.oneLine(typed);
				textArea.text(typed);
			}
			if (typed.equals(last[0])) return;
			last[0] = typed;
			line.settle(box);
			line.text = typed;
		});
		panel.child(textArea);
		textHint = label(TEXT_HINT);
		textHint.maxWidth(screen.inner());
		textHintText = TEXT_HINT;
		panel.child(textHint);
		panel.child(screen.outsideRow(line, true));
		if (line.hudValue().graph(line.range()) != HudValue.Graph.NONE) panel.child(screen.showRow(line));

		FlowLayout rulesHead = heading("Colour rules");
		screen.tip(rulesHead, RULES_HELP);
		panel.child(rulesHead);
		String rules = String.join("\n", line.advancedRules());
		rulesArea = new FreeTextArea(Sizing.fill(100), Sizing.fixed(4 * lineHeight + 8), true);
		rulesArea.setCharacterLimit(Math.max(RULES_LIMIT, rules.length()));
		rulesArea.text(rules);
		rulesArea.margins(Insets.top(2));
		screen.tip(rulesArea, RULES_HELP);
		String[] lastRules = {rules};
		screen.field(rulesArea::getValue, typed -> {
			typed = typed.replace("\r", "");
			if (typed.equals(lastRules[0])) return;
			lastRules[0] = typed;
			line.settle(box);
			line.colorRules = AdvancedText.ruleLines(typed);
		});
		panel.child(rulesArea);
		ButtonComponent addRule = button("+ Rule", b -> addRule());
		screen.tip(addRule, "Type a rule for the first number in the text, with its number now");
		addRule.margins(Insets.top(2));
		panel.child(addRule);
		ruleHints = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		ruleHints.margins(Insets.top(2));
		panel.child(ruleHints);
		if (listOpen) {
			list = new ValueList(screen, this);
			list.build();
		}
	}

	private HudExpr.Values values() {
		return HudBoxText.values(screen.data(), true);
	}

	private void addRule() {
		List<String> tags = HudBoxText.numberTags(textArea.getValue());
		if (tags.isEmpty()) return;
		String now = AdvancedText.typedNumber(tags.getFirst(), values());
		String lead = AdvancedText.ruleLead(rulesArea.getValue(), tags.getFirst());
		line.settle(box);
		line.colorRules = AdvancedText.ruleLines(lead + now + " &c");
		screen.typingRules(true);
		screen.rebuildPanel();
		// rebuilt, so the new page's field
		AdvancedPage page = screen.advancedPage();
		if (page == null) return;
		page.rulesArea.select(lead.length(), lead.length() + now.length());
		screen.focus(page.rulesArea);
		screen.swallowDrag();
	}

	private FreeTextArea typingArea() {
		return screen.typingRules() && rulesArea != null ? rulesArea : textArea;
	}

	void typeValue(String tag) {
		typeEdit(area -> HudTyping.type(area.getValue(), area.cursor(), tag));
	}

	void typeOption(HudValue value, String option) {
		typeEdit(area -> HudTyping.option(area.getValue(), area.cursor(), value, option));
	}

	void typeRaw(String text) {
		typeEdit(area -> HudTyping.insert(area.getValue(), area.cursor(), text));
	}

	private void typeEdit(Function<FreeTextArea, HudTyping.Edit> edit) {
		screen.closeMenu();
		FreeTextArea area = typingArea();
		if (area == null) return;
		HudTyping.Edit typed = edit.apply(area);
		if (typed.text().length() > area.limit()) noRoomFor = area.getValue();
		else area.set(typed.text(), typed.cursor());
		screen.focus(area);
		screen.swallowDrag();
	}

	// every frame, the fields are polled
	void refresh(UIComponent focused) {
		if (focused == textArea) screen.typingRules(false);
		else if (focused == rulesArea) screen.typingRules(true);
		boolean typingRules = screen.typingRules();
		String source = textArea.getValue();
		if (list != null) list.refresh(typingRules, source);
		if (noRoomFor != null && !noRoomFor.equals(source) && !noRoomFor.equals(rulesArea.getValue())) noRoomFor = null;
		boolean noRoom = noRoomFor != null;
		// same object = same text, the poll only swaps it on change
		boolean textChanged = source != hintSource;
		if (textChanged || noRoom != hintNoRoom) {
			hintSource = source;
			hintNoRoom = noRoom;
			String hint = AdvancedText.hint(HudBoxText.problems(source), noRoom);
			if (!hint.equals(textHintText)) {
				textHintText = hint;
				textHint.text(Component.literal(hint));
			}
		}
		String rulesText = rulesArea.getValue();
		HudData data = screen.data();
		if (!textChanged && rulesText == hintRules && data == hintData) return;
		hintRules = rulesText;
		hintData = data;
		List<String> hints = HudRules.hints(AdvancedText.ruleLines(rulesText), source, HudBoxText.values(data, true));
		if (!hints.equals(ruleHintLines)) {
			ruleHintLines = hints;
			ruleHints.clearChildren();
			for (String hint : hints) {
				LabelComponent label = label(hint);
				label.maxWidth(screen.inner());
				ruleHints.child(label);
			}
		}
	}

	// grey example in the empty rules field. top + bottom = the panels visible part
	void drawPlaceholder(GuiGraphicsExtractor graphics, int top, int bottom) {
		if (!rulesArea.getValue().isEmpty()) return;
		int y = rulesArea.y() + 4;
		if (y < top || y + screen.getFont().lineHeight > bottom) return;
		String source = textArea.getValue();
		HudData data = screen.data();
		if (source != placeholderSource || data != placeholderData) {
			placeholderSource = source;
			placeholderData = data;
			placeholderText = screen.clip("§8Example: " + AdvancedText.example(source, HudBoxText.values(data, true)), rulesArea.width() - 8);
		}
		graphics.text(screen.getFont(), placeholderText, rulesArea.x() + 4, y, 0xFFFFFFFF, false);
	}
}
