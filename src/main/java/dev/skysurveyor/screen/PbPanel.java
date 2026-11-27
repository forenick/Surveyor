package dev.skysurveyor.screen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import dev.skysurveyor.Surveyor;
import dev.skysurveyor.data.ShaftNames;
import dev.skysurveyor.hud.HudData;
import dev.skysurveyor.journal.JournalEntry;
import dev.skysurveyor.journal.JournalStats;
import dev.skysurveyor.journal.JournalStats.SessionSummary;
import dev.skysurveyor.journal.PersonalBests;
import dev.skysurveyor.journal.PersonalBests.Def;
import dev.skysurveyor.journal.PersonalBests.Per;
import dev.skysurveyor.journal.PersonalBests.Stat;
import dev.skysurveyor.money.MoneyStats;

import static dev.skysurveyor.screen.ScreenParts.applyTooltip;
import static dev.skysurveyor.screen.ScreenParts.label;

final class PbPanel {
	// original null = new, found by value
	private static final class Draft {
		final Def original;
		Stat stat;
		String shaft;
		String best;
		String name;

		Draft(Def original, Def def) {
			this.original = original;
			this.stat = def.type();
			this.shaft = def.shaftOrDefault();
			this.best = def.best();
			this.name = def.name();
		}

		Def def() {
			String typed = name == null || name.isBlank() ? null : name.strip();
			return new Def(stat.id(), shaft, best, typed);
		}
	}

	private static final Identifier OPEN_ARROW = Identifier.fromNamespaceAndPath("skysurveyor", "textures/gui/open_arrow.png");
	private static final PersonalBests.Coins PB_COINS = PersonalBests.Coins.of(entry -> MoneyStats.net(Surveyor.MONEY.value(entry)),
			Surveyor.MONEY::value);
	// display choice, text 8 + 2 each side
	private static final int SMALL_BUTTON = 12;
	// min, measured past it
	private static final int BUILDER_KEY = 52;
	// display choices
	private static final int CARD_MAX = 250;
	private static final int DROPDOWN_MIN = 90;
	private static final int DROPDOWN_ITEM = 11;
	private static final int DROPDOWN_HEADING = 13;

	private final JournalScreen screen;
	private boolean editing;
	// reset asks twice, one build long
	private boolean resetArmed;
	private Draft draft;
	// "value" or "shaft", null = none open
	private String dropdown;
	private UIComponent dropdownAnchor;
	private TextBoxComponent nameField;
	private TextBoxComponent homeField;
	private LabelComponent previewLabel;
	private String previewName;
	// only builder rebuild changes it
	private PersonalBests.Result previewResult;
	private List<JournalEntry> entries = List.of();
	private List<SessionSummary> sessions = List.of();
	// same range + prices = same results
	private final Map<Def, PersonalBests.Result> cache = new HashMap<>();
	private List<Object> cacheKey = List.of();
	private final List<FlowLayout> rows = new ArrayList<>();
	private final List<FlowLayout> grips = new ArrayList<>();
	// drag indexes point here
	private List<Def> shown = List.of();
	private int drag = -1;
	private int gap = -1;

	PbPanel(JournalScreen screen) {
		this.screen = screen;
	}

	private Font font() {
		return screen.getFont();
	}

	private void rebuild() {
		screen.rebuild(true);
	}

	boolean editing() {
		return editing;
	}

	// not under ur hands
	boolean busy() {
		return draft != null || dropdown != null || drag >= 0;
	}

	// false = nothing open
	boolean stepBack() {
		if (draft != null && dropdown != null) {
			dropdown = null;
		} else if (draft != null) {
			draft = null;
			dropdown = null;
		} else if (editing) {
			editing = false;
			drag = -1;
		} else {
			return false;
		}
		return true;
	}

	void closeBuilder() {
		draft = null;
		dropdown = null;
	}

	// before every screen build
	void beforeBuild() {
		rows.clear();
		grips.clear();
		if (draft != null) keepTypedName();
		nameField = null;
		previewLabel = null;
	}

	void forget() {
		cache.clear();
		cacheKey = List.of();
		previewResult = null;
	}

	// caxton scrolls 1st letter out at 0 width
	void rehome() {
		if (homeField == null) return;
		ScreenParts.rehome(homeField);
		homeField = null;
	}

	private List<Def> list() {
		return Surveyor.SETTINGS.get().personalBests().list();
	}

	private void save(List<Def> list) {
		Surveyor.SETTINGS.change(s -> s.withPersonalBests(s.personalBests().withList(list)));
	}

	private PersonalBests.Result result(Def def) {
		return cache.computeIfAbsent(def, d -> PersonalBests.best(d, entries, sessions, PB_COINS));
	}

	void addTo(FlowLayout totals, List<JournalEntry> entries, List<SessionSummary> sessions) {
		this.entries = entries;
		this.sessions = sessions;
		// both ends, new + older loaded both count
		List<Object> key = Arrays.asList(entries.size(), entries.isEmpty() ? null : entries.getFirst(),
				entries.isEmpty() ? null : entries.getLast(), sessions.size(), Surveyor.MONEY.changes());
		if (!key.equals(cacheKey)) {
			cache.clear();
			cacheKey = key;
		}
		FlowLayout header = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		header.margins(Insets.vertical(4));
		header.gap(3);
		header.child(JournalScreen.divider());
		FlowLayout title = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		title.verticalAlignment(VerticalAlignment.CENTER);
		LabelComponent name = label("§7Personal bests");
		name.horizontalSizing(Sizing.expand());
		title.child(name);
		title.child(smallButton(editing ? "§eDone" : "§fEdit", editing ? "Stop editing" : "Add, remove or reorder personal bests", () -> {
			editing = !editing;
			rebuild();
		}));
		header.child(title);
		totals.child(header);

		List<Def> list = list();
		shown = list;
		ShaftNames names = ShaftNames.defaults();
		for (Def def : list) {
			PersonalBests.Result result = result(def);
			String defName = PersonalBests.name(def, names);
			if (!editing && def.type() == Stat.SHAFT_METAL && result.value() == null && def.shaft() == null) continue;
			if (editing) {
				totals.child(editRow(def, defName, text(def, result, defName)));
				continue;
			}
			Runnable open = result.entry() != null ? () -> screen.openShaft(result.entry())
					: result.session() != null ? () -> screen.openSession(result.session()) : null;
			String target = result.entry() != null ? names.coloredName(result.entry().shaftId()) : result.session() != null ? sessionName(result.session()) : null;
			String longest = shortStreak(def, result, defName) ? "§7Longest §f" + Math.round(result.value()) : null;
			totals.child(record(defName, text(def, result, defName), open, target, longest));
		}
		if (editing) {
			FlowLayout buttons = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
			buttons.gap(6);
			buttons.margins(Insets.top(3));
			buttons.child(smallButton("§a+ Add PB", "Make your own personal best", () -> {
				draft = new Draft(null, new Def(Stat.SHAFT_COINS.id(), null, null, null));
				rebuild();
			}));
			boolean armed = resetArmed;
			resetArmed = false;
			if (!list.equals(PersonalBests.DEFAULTS)) {
				// no undo here, so twice like the other resets
				buttons.child(smallButton(armed ? "§cReally reset?" : "§cReset",
						armed ? "Click again to bring back the default list. This can't be undone" : "Back to the default list", () -> {
							if (armed) save(PersonalBests.DEFAULTS);
							else resetArmed = true;
							rebuild();
						}));
			}
			totals.child(buttons);
		}
	}

	// same text as the hud line
	private String text(Def def, PersonalBests.Result result, String key) {
		Stat stat = def.type();
		if (stat == null || result.value() == null) return null;
		int tier = Surveyor.MONEY.settings().metalTierUsed().tier();
		String text = HudData.pbText(def, result, tier);
		// scale 4: tier word would wrap the label to 3 lines
		int room = screen.statsLeftRoom() - 20;
		if (stat == Stat.SHAFT_METAL && font().width(key + " " + text) > room) text = result.entry().metal().amount(tier);
		// scale 4 too, longest goes in the hover
		if (shortStreak(def, result, key)) text = String.valueOf(result.current());
		String color = switch (stat) {
			case SHAFT_COINS, SESSION_COINS, SESSION_COIN_RATE -> Math.round(result.value()) < 0 ? "§c" : "§6";
			case SHAFT_POWDER, SESSION_POWDER, SHAFT_COLD -> "§b";
			case SHAFT_SPAWN, SESSION_SPAWN -> "§a";
			case SHAFT_LAPIS -> "§9";
			default -> "§f";
		};
		return color + text.replace(" avg", " §7avg").replace(" (longest", " §7(longest");
	}

	private boolean shortStreak(Def def, PersonalBests.Result result, String key) {
		if (def.type() == null || def.type().per() != Per.STREAK || result.value() == null || result.current() == null) return false;
		String text = HudData.pbText(def, result, Surveyor.MONEY.settings().metalTierUsed().tier());
		return font().width(key + " " + text) > screen.statsLeftRoom() - 20;
	}

	private FlowLayout editRow(Def def, String key, String value) {
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		row.gap(3);
		row.verticalAlignment(VerticalAlignment.CENTER);
		FlowLayout grip = UIContainers.horizontalFlow(Sizing.fixed(7), Sizing.fixed(9));
		grip.surface(ScreenParts.GRIP);
		grip.cursorStyle(CursorStyle.MOVE);
		grip.tooltip(Component.literal("§fDrag to move"));
		row.child(grip);
		grips.add(grip);
		FlowLayout name = new HoverFlow(Sizing.expand(), Sizing.content(), HoverFlow.ROW);
		name.gap(4);
		LabelComponent keyLabel = label("§7" + key);
		keyLabel.horizontalSizing(Sizing.expand());
		name.child(keyLabel);
		name.child(label(value == null ? "§8-" : value));
		applyTooltip(name, List.of(Component.literal("§f" + key), Component.literal("§8Click to change it")));
		name.mouseDown().subscribe((event, doubleClick) -> {
			draft = new Draft(def, def);
			rebuild();
			return true;
		});
		row.child(name);
		row.child(textButton("§c×", "Remove", () -> {
			List<Def> list = new ArrayList<>(list());
			if (list.remove(def)) save(list);
			rebuild();
		}));
		rows.add(row);
		return row;
	}

	private ButtonComponent smallButton(String text, String tip, Runnable action) {
		ButtonComponent button = PackButton.button(Component.literal(text), b -> action.run());
		button.verticalSizing(Sizing.fixed(SMALL_BUTTON));
		button.horizontalSizing(Sizing.fixed(font().width(text) + 8));
		applyTooltip(button, List.of(Component.literal("§f" + tip)));
		return button;
	}

	private static FlowLayout textButton(String text, String tip, Runnable action) {
		FlowLayout button = new HoverFlow(Sizing.content(), Sizing.content(), HoverFlow.SMALL);
		button.padding(Insets.of(1, 1, 2, 2));
		button.child(label(text));
		applyTooltip(button, List.of(Component.literal("§f" + tip)));
		button.mouseDown().subscribe((event, doubleClick) -> {
			action.run();
			return true;
		});
		return button;
	}

	void buildBuilder(FlowLayout root) {
		if (draft == null) return;
		int width = screen.width;
		int height = screen.height;
		ShaftNames names = ShaftNames.defaults();
		Def def = draft.def();
		boolean streak = draft.stat.per() == Per.STREAK;
		FlowLayout dim = UIContainers.horizontalFlow(Sizing.fixed(width), Sizing.fixed(height));
		dim.positioning(Positioning.absolute(-JournalScreen.PADDING, -JournalScreen.PADDING));
		dim.surface(Surface.flat(0x99000000));
		dim.mouseDown().subscribe((event, doubleClick) -> {
			if (dropdown != null) dropdown = null;
			else draft = null;
			rebuild();
			return true;
		});
		root.child(dim);

		int cardWidth = Math.min(CARD_MAX, width - 2 * JournalScreen.PADDING);
		FlowLayout card = UIContainers.verticalFlow(Sizing.fixed(cardWidth), Sizing.content());
		card.surface(JournalScreen.OVERLAY);
		card.padding(Insets.of(6));
		card.gap(3);
		card.positioning(Positioning.relative(50, 35));
		card.mouseDown().subscribe((event, doubleClick) -> {
			if (dropdown == null) return true;
			dropdown = null;
			rebuild();
			return true;
		});
		card.child(label("§f" + (draft.original == null ? "New personal best" : "Edit personal best")));

		ButtonComponent value = choice(draft.stat.label() + " §7▾", false, b -> openDropdown("value", b));
		value.horizontalSizing(Sizing.expand());
		card.child(builderRow("Value", value));
		if (!streak) {
			Stat shaftTwin = draft.stat.in(Per.SHAFT);
			Stat sessionTwin = draft.stat.in(Per.SESSION);
			ButtonComponent perShaft = choice("Shaft", draft.stat.per() == Per.SHAFT, b -> setStat(shaftTwin));
			perShaft.active(shaftTwin != null);
			ButtonComponent perSession = choice("Session", draft.stat.per() == Per.SESSION, b -> setStat(sessionTwin));
			perSession.active(sessionTwin != null);
			card.child(builderRow("Per", perShaft, perSession));
			boolean lowest = def.lowest();
			card.child(builderRow("Best is", choice("Highest", !lowest, b -> setBest(false)), choice("Lowest", lowest, b -> setBest(true))));
		}
		String shaftText = draft.shaft == null ? "Any" : names.name(draft.shaft);
		ButtonComponent shaft = choice(shaftText + " §7▾", false, b -> openDropdown("shaft", b));
		shaft.horizontalSizing(Sizing.expand());
		card.child(builderRow("Shaft", shaft));

		String typed = nameField == null ? (draft.name == null ? "" : draft.name) : nameField.getValue();
		nameField = UIComponents.textBox(Sizing.expand());
		nameField.verticalSizing(Sizing.fixed(14));
		nameField.setMaxLength(40);
		nameField.text(typed);
		nameField.setHint(Component.literal(PersonalBests.name(new Def(def.stat(), def.shaft(), def.best(), null), names))
				.setStyle(EditBox.SEARCH_HINT_STYLE));
		homeField = nameField;
		card.child(builderRow("Name", nameField));

		FlowLayout preview = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		preview.surface(Surface.flat(0x80000000));
		preview.padding(Insets.of(3, 3, 5, 5));
		previewLabel = label("");
		previewLabel.horizontalSizing(Sizing.fill(100));
		previewName = null;
		previewResult = null;
		preview.child(previewLabel);
		card.child(preview);
		updatePreview();
		LabelComponent hint = label("§8" + builderHint(def));
		hint.horizontalSizing(Sizing.fill(100));
		card.child(hint);

		FlowLayout buttons = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		buttons.horizontalAlignment(HorizontalAlignment.RIGHT);
		buttons.gap(4);
		buttons.margins(Insets.top(3));
		buttons.child(choice("Cancel", false, b -> {
			draft = null;
			dropdown = null;
			rebuild();
		}));
		buttons.child(choice("Done", false, b -> savePb()));
		card.child(buttons);
		root.child(card);
		if (dropdown != null && dropdownAnchor != null) root.child(dropdownList());
	}

	private FlowLayout builderRow(String key, UIComponent... children) {
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		row.gap(3);
		row.verticalAlignment(VerticalAlignment.CENTER);
		LabelComponent label = label("§7" + key);
		// so rows line up
		int widest = List.of("Value", "Per", "Best is", "Shaft", "Name").stream().mapToInt(font()::width).max().orElse(0);
		label.horizontalSizing(Sizing.fixed(TableColumns.atLeast(BUILDER_KEY, widest)));
		row.child(label);
		for (UIComponent child : children) row.child(child);
		return row;
	}

	private ButtonComponent choice(String text, boolean on, Consumer<ButtonComponent> action) {
		ButtonComponent button = PackButton.button(Component.literal((on ? "§e" : "§f") + text), action);
		button.verticalSizing(Sizing.fixed(14));
		button.horizontalSizing(Sizing.fixed(font().width(text) + 12));
		return button;
	}

	private static String builderHint(Def def) {
		Stat stat = def.type();
		if (stat.per() == Per.STREAK) return "Shafts since your last one, and your longest run without one";
		if (stat.isRate()) {
			return "Sessions with " + JournalStats.MIN_SHAFTS_FOR_RATE_RECORD + "+ shafts only";
		}
		if (def.shaft() == null) return stat.per() == Per.SHAFT ? "Every shaft counts" : "Every session counts";
		return "Only " + ShaftNames.defaults().name(def.shaft()) + " shafts count, any variant";
	}

	private void setStat(Stat stat) {
		if (stat == null) return;
		draft.stat = stat;
		draft.best = null;
		if (stat == Stat.DRYSTREAK && draft.shaft == null) draft.shaft = PersonalBests.DEFAULT_DRY;
		keepTypedName();
		dropdown = null;
		rebuild();
	}

	private void setBest(boolean lowest) {
		draft.best = lowest == draft.stat.lowestByDefault() ? null : lowest ? Def.LOWEST : Def.HIGHEST;
		keepTypedName();
		rebuild();
	}

	private void keepTypedName() {
		if (nameField != null) draft.name = nameField.getValue();
	}

	private void openDropdown(String which, UIComponent anchor) {
		keepTypedName();
		dropdown = which.equals(dropdown) ? null : which;
		dropdownAnchor = anchor;
		rebuild();
	}

	private FlowLayout dropdownList() {
		FlowLayout items = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		if (dropdown.equals("value")) {
			for (Per per : Per.values()) {
				LabelComponent heading = label("§7" + per.displayName());
				heading.margins(Insets.of(items.children().isEmpty() ? 0 : 3, 1, 1, 0));
				items.child(heading);
				for (Stat stat : PersonalBests.stats(per)) items.child(dropdownItem(stat.label(), stat == draft.stat, () -> setStat(stat)));
			}
		} else {
			ShaftNames names = ShaftNames.defaults();
			if (draft.stat.per() != Per.STREAK) items.child(dropdownItem("Any", draft.shaft == null, () -> setShaft(null)));
			names.codes().stream().sorted(Comparator.comparing(names::name))
					.forEach(code -> items.child(dropdownItem(names.name(code), code.equals(draft.shaft), () -> setShaft(code))));
		}
		int listWidth = Math.max(dropdownAnchor.width(), DROPDOWN_MIN);
		int top = dropdownAnchor.y() + dropdownAnchor.height();
		int room = screen.height - JournalScreen.PADDING - top;
		// owo cant content-size a vertical scroll, idk why
		int needed = items.children().stream().mapToInt(c -> c instanceof HoverFlow ? DROPDOWN_ITEM : DROPDOWN_HEADING).sum();
		KeptScroll scroll = screen.scroll(Sizing.fixed(listWidth), Sizing.fixed(Math.max(40, Math.min(needed, room - 4))), items);
		FlowLayout panel = UIContainers.verticalFlow(Sizing.content(), Sizing.content());
		panel.surface(JournalScreen.OVERLAY);
		panel.padding(Insets.of(2));
		panel.child(scroll);
		panel.positioning(Positioning.absolute(dropdownAnchor.x() - JournalScreen.PADDING, top - JournalScreen.PADDING));
		return panel;
	}

	private FlowLayout dropdownItem(String text, boolean on, Runnable action) {
		FlowLayout item = new HoverFlow(Sizing.fill(100), Sizing.fixed(DROPDOWN_ITEM), HoverFlow.SMALL);
		item.padding(Insets.horizontal(4));
		item.verticalAlignment(VerticalAlignment.CENTER);
		item.child(label((on ? "§e" : "§f") + text));
		item.mouseDown().subscribe((event, doubleClick) -> {
			action.run();
			return true;
		});
		return item;
	}

	private void setShaft(String code) {
		draft.shaft = code;
		keepTypedName();
		dropdown = null;
		rebuild();
	}

	// caxton cancels onValueChange, poll
	void updatePreview() {
		if (previewLabel == null || draft == null) return;
		String typed = nameField == null ? draft.name : nameField.getValue();
		if (typed != null && typed.equals(previewName)) return;
		previewName = typed;
		draft.name = typed;
		Def def = draft.def();
		String key = PersonalBests.name(def, ShaftNames.defaults());
		if (previewResult == null) previewResult = result(def);
		String value = text(def, previewResult, key);
		previewLabel.text(Component.literal("§7" + key + " " + (value == null ? "§8-" : value)));
	}

	private void savePb() {
		keepTypedName();
		List<Def> list = new ArrayList<>(list());
		Def def = draft.def();
		int index = draft.original == null ? -1 : list.indexOf(draft.original);
		if (index < 0) list.add(def);
		else list.set(index, def);
		save(list);
		draft = null;
		dropdown = null;
		rebuild();
	}

	// true = grabbed one
	boolean grab(MouseButtonEvent click) {
		if (!editing || draft != null || click.button() != 0) return false;
		for (int i = 0; i < grips.size(); i++) {
			if (grips.get(i).isInBoundingBox(click.x(), click.y())) {
				drag = i;
				gap = i;
				return true;
			}
		}
		return false;
	}

	boolean dragging() {
		return drag >= 0;
	}

	void dragTo(double mouseY) {
		gap = rows.size();
		for (int i = 0; i < rows.size(); i++) {
			FlowLayout row = rows.get(i);
			if (mouseY < row.y() + row.height() / 2.0) {
				gap = i;
				break;
			}
		}
	}

	void drop() {
		int from = drag;
		int to = gap;
		drag = -1;
		if (!list().equals(shown)) {
			rebuild();
		} else if (to != from && to != from + 1) {
			List<Def> list = new ArrayList<>(list());
			Def moved = list.remove(from);
			list.add(to > from ? to - 1 : to, moved);
			save(list);
			rebuild();
		}
	}

	void drawDrag(GuiGraphicsExtractor graphics) {
		if (drag < 0 || drag >= rows.size()) return;
		FlowLayout from = rows.get(drag);
		graphics.fill(from.x(), from.y(), from.x() + from.width(), from.y() + from.height(), 0x30FFFFFF);
		if (gap == drag || gap == drag + 1) return;
		FlowLayout last = rows.getLast();
		int y = gap < rows.size() ? rows.get(gap).y() : last.y() + last.height();
		graphics.fill(from.x(), y - 1, from.x() + from.width(), y + 1, 0xFFFFFF55);
	}

	// extra null = none
	private static FlowLayout record(String key, String value, Runnable open, String target, String extra) {
		FlowLayout row = open == null ? UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content())
				: new HoverFlow(Sizing.fill(100), Sizing.content(), HoverFlow.ROW);
		row.gap(4);
		row.verticalAlignment(VerticalAlignment.TOP);
		LabelComponent keyLabel = label("§7" + key);
		keyLabel.horizontalSizing(Sizing.expand());
		row.child(keyLabel);
		row.child(label(value == null ? "§8-" : value));
		if (open != null) {
			FlowLayout arrow = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
			arrow.padding(Insets.of(2, 0, 2, 1));
			arrow.margins(Insets.top(-1));
			arrow.child(UIComponents.texture(OPEN_ARROW, 0, 0, 3, 5, 3, 5).blend(true));
			row.child(arrow);
			List<Component> tip = new ArrayList<>(List.of(Component.literal("§f" + key)));
			if (extra != null) tip.add(Component.literal(extra));
			tip.add(Component.literal("§8Click to open " + (target == null ? "the shaft" : target)));
			applyTooltip(row, tip);
			row.mouseDown().subscribe((event, doubleClick) -> {
				open.run();
				return true;
			});
		} else if (extra != null) {
			applyTooltip(row, List.of(Component.literal("§f" + key), Component.literal(extra)));
		}
		return row;
	}

	private static String sessionName(SessionSummary session) {
		return "session from " + JournalScreen.day(session.start()) + " " + JournalScreen.clock(session.start());
	}
}
