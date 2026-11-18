package dev.skysurveyor.screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.mojang.blaze3d.platform.NativeImage;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import dev.skysurveyor.Surveyor;
import dev.skysurveyor.money.LiveMoney;
import dev.skysurveyor.money.PriceBook;
import dev.skysurveyor.settings.Setting;
import dev.skysurveyor.settings.Setting.Choice;
import dev.skysurveyor.settings.Setting.Option;
import dev.skysurveyor.settings.Settings;
import dev.skysurveyor.settings.SettingsList;
import dev.skysurveyor.settings.SettingsList.Category;
import dev.skysurveyor.settings.SettingsList.Section;

import static dev.skysurveyor.screen.ScreenParts.label;
import static dev.skysurveyor.screen.ScreenParts.wrapped;

// mockup: docs/mockups/settings-v2.html. every change saves right away
public final class SettingsScreen extends BaseOwoScreen<FlowLayout> implements ModScreen {
	private static final Surface CARD = Surface.flat(0x0CFFFFFF);
	private static final Surface CARD_HOVER = Surface.flat(0x16FFFFFF);
	private static final Surface ROW_HOVER = Surface.flat(0x18FFFFFF);
	private static final Surface ROW_SELECTED = Surface.flat(0x26FFFFFF);
	private static final int PADDING = 8;
	private static final int GAP = 5;
	private static final int BAR_PADDING = 4;
	private static final int BAR_GAP = 4;
	private static final int SIDE_PADDING = 3;
	private static final int MAIN_PADDING = 7;
	private static final int SCROLLBAR = 3;
	private static final int CARD_PADDING = 4;
	private static final int CARD_GAP = 6;
	private static final int BUTTON = 16;
	private static final int OPTION = 11;
	private static final int FIELD = 44;
	// below this the control goes under the text
	private static final int MIN_TEXT = 140;
	// below this each card sizes its own control, display choice
	private static final int NARROW = 560;
	private static final int TIP_WIDTH = 200;
	// typed number applies after this many quiet ticks or on click away
	private static final int TYPE_DELAY = 10;
	private static final Identifier SEARCH_ICON = icon("search_icon");
	private static final Identifier RESET_ICON = icon("reset_icon");
	private static final Identifier RESET_ALL_ICON = icon("reset_red_icon");
	private static final Identifier RADIO_ON = icon("radio_on_icon");
	private static final Identifier RADIO_OFF = icon("radio_off_icon");
	private static final Identifier CHECK_ICON = icon("check_icon");
	private static final Identifier CROSS_ICON = icon("cross_icon");
	private static final Map<Identifier, Integer> ICON_WIDTHS = new HashMap<>();

	private final Screen parent;
	private Settings settings = Surveyor.SETTINGS.get();
	private String query = "";
	private String category = SettingsList.MONEY;
	private FlowLayout root;
	private FlowLayout side;
	private FlowLayout main;
	private KeptScroll mainScroll;
	private TextBoxComponent search;
	private FlowLayout categoryHead;
	private ButtonComponent resetCategory;
	private boolean resetArmed;
	private LabelComponent bazaarAge;
	private LabelComponent binsAge;
	private String bazaarText;
	private String binsText;
	private LabelComponent saveNote;
	private String saveText;
	// shown ones, in page order
	private final List<Card> cards = new ArrayList<>();
	// built once per size, search only shows, hides + reorders them
	private final Map<Setting, Card> built = new IdentityHashMap<>();
	// widest control of all settings, per size
	private int widestControl = -1;
	private final List<TextBoxComponent> homeFields = new ArrayList<>();
	private final Map<String, UIComponent> sectionHeads = new HashMap<>();
	private final Map<UIComponent, List<FormattedCharSequence>> tips = new IdentityHashMap<>();
	private int builtWidth;
	private int builtHeight;
	private int ticks;

	public SettingsScreen(Screen parent) {
		super(Component.literal("Surveyor Settings"));
		this.parent = parent;
		Surveyor.MONEY.refresh();
	}

	private static Identifier icon(String name) {
		return Identifier.fromNamespaceAndPath("skysurveyor", "textures/gui/" + name + ".png");
	}

	@Override
	protected OwoUIAdapter<FlowLayout> createAdapter() {
		return OwoUIAdapter.create(this, UIContainers::verticalFlow);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		flushTyped();
		minecraft.gui.setScreen(parent);
	}

	@Override
	protected void build(FlowLayout root) {
		this.root = root;
		root.surface(Surface.VANILLA_TRANSLUCENT);
		root.padding(Insets.of(PADDING));
		root.gap(GAP);
		rebuild();
	}

	// owo calls build() once, rebuild on resize
	@Override
	protected void init() {
		super.init();
		if (root != null && (width != builtWidth || height != builtHeight)) rebuild();
	}

	@Override
	public void tick() {
		super.tick();
		String typed = search.getValue();
		if (!typed.equals(query)) {
			query = typed;
			fillBody();
		}
		for (Card card : List.copyOf(cards)) card.poll();
		// hand edit picked up on the io thread
		Settings now = Surveyor.SETTINGS.get();
		if (!now.equals(settings)) {
			settings = now;
			refreshCards();
		}
		if (++ticks % 10 == 0) updatePrices();
		updateSaveNote();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		homeFields.forEach(ScreenParts::rehome);
		homeFields.clear();
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		drawTip(graphics, mouseX, mouseY);
	}

	private void rebuild() {
		builtWidth = width;
		builtHeight = height;
		// card sizes come from the width
		flushTyped();
		built.clear();
		cards.clear();
		widestControl = -1;
		root.clearChildren();
		root.child(topBar());
		FlowLayout body = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.expand());
		body.gap(GAP);
		side = UIContainers.verticalFlow(Sizing.fixed(sideWidth()), Sizing.fill(100));
		side.surface(ScreenParts.PANEL);
		side.padding(Insets.of(SIDE_PADDING));
		side.gap(1);
		main = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		main.gap(2);
		mainScroll = new KeptScroll(Sizing.expand(), Sizing.fill(100), main);
		mainScroll.surface(ScreenParts.PANEL);
		mainScroll.padding(Insets.of(MAIN_PADDING));
		mainScroll.scrollbarThiccness(SCROLLBAR);
		body.child(side);
		body.child(mainScroll);
		root.child(body);
		FlowLayout footer = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		footer.gap(16);
		saveNote = label("");
		saveText = null;
		updateSaveNote();
		footer.child(saveNote);
		footer.child(label("§8Changes apply right away"));
		root.child(footer);
		fillBody();
	}

	private FlowLayout topBar() {
		FlowLayout bar = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		bar.surface(ScreenParts.PANEL);
		bar.padding(Insets.of(BAR_PADDING));
		bar.gap(BAR_GAP);
		bar.verticalAlignment(VerticalAlignment.CENTER);
		FlowLayout title = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
		title.gap(3);
		title.verticalAlignment(VerticalAlignment.CENTER);
		title.margins(Insets.left(2));
		title.child(UIComponents.texture(JournalScreen.SETTINGS_ICON, 0, 0, 8, 8, 8, 8));
		title.child(label("§fSurveyor §7Settings"));
		bar.child(title);
		bar.child(UIContainers.horizontalFlow(Sizing.expand(), Sizing.content()));
		bar.child(UIComponents.texture(SEARCH_ICON, 0, 0, 8, 8, 8, 8));
		String typed = search == null ? "" : search.getValue();
		search = UIComponents.textBox(Sizing.fixed(width <= 480 ? 100 : width <= 640 ? 130 : 160));
		search.verticalSizing(Sizing.fixed(BUTTON));
		search.setMaxLength(64);
		search.text(typed);
		search.setHint(Component.literal("Search settings").setStyle(EditBox.SEARCH_HINT_STYLE));
		homeFields.add(search);
		bar.child(search);
		ButtonComponent back = PackButton.button(Component.literal("Back"), b -> onClose());
		// same as owo's content(6)
		back.horizontalSizing(Sizing.fixed(font.width("Back") + 20));
		back.margins(Insets.left(2));
		bar.child(back);
		return bar;
	}

	private int sideWidth() {
		return width <= 480 ? 78 : width <= 640 ? 96 : 116;
	}

	// main panel minus padding + scrollbar
	private int mainInner() {
		return width - 2 * PADDING - sideWidth() - GAP - 2 * MAIN_PADDING - SCROLLBAR - 2;
	}

	private boolean searching() {
		return !query.isBlank();
	}

	// added in one go, owo relayouts per child
	private void fillBody() {
		flushTyped();
		cards.clear();
		tips.clear();
		sectionHeads.clear();
		categoryHead = null;
		resetCategory = null;
		resetArmed = false;
		bazaarAge = null;
		binsAge = null;
		bazaarText = null;
		binsText = null;
		side.clearChildren();
		main.clearChildren();
		List<UIComponent> rows = new ArrayList<>();
		fillSide(rows);
		side.children(rows);
		List<UIComponent> body = new ArrayList<>();
		if (searching()) fillSearch(body);
		else fillCategory(body, category(category));
		for (Card card : cards) tips.putAll(card.tips);
		main.children(body);
		refreshCards();
		updatePrices();
	}

	private static Category category(String id) {
		return SettingsList.CATEGORIES.stream().filter(c -> c.id().equals(id)).findFirst().orElse(SettingsList.CATEGORIES.getFirst());
	}

	private void fillSide(List<UIComponent> rows) {
		for (Category c : SettingsList.CATEGORIES) {
			int hits = searching() ? c.matching(query).settings().size() : 0;
			boolean on = !searching() && c.id().equals(category);
			FlowLayout row = clickRow(16, on, () -> {
				category = c.id();
				query = "";
				search.setValue("");
				fillBody();
			});
			row.padding(Insets.horizontal(4));
			row.gap(4);
			// fixed 8 wide so names line up, icon drawn at its own width
			FlowLayout iconBox = UIContainers.horizontalFlow(Sizing.fixed(8), Sizing.fixed(8));
			Identifier icon = icon(c.icon());
			int iconWidth = iconWidth(icon);
			iconBox.child(UIComponents.texture(icon, 0, 0, iconWidth, 8, iconWidth, 8));
			row.child(iconBox);
			LabelComponent name = label((on ? "§e" : searching() && hits == 0 ? "§8" : "§f") + c.name());
			name.horizontalSizing(Sizing.expand());
			row.child(name);
			if (hits > 0) row.child(label("§8" + hits));
			rows.add(row);
			// section jumps, no room at 480
			if (!on || c.sections().size() < 2 || width <= 480) continue;
			for (Section section : c.sections()) {
				FlowLayout sub = clickRow(12, false, () -> {
					UIComponent head = sectionHeads.get(section.name());
					if (head != null) mainScroll.scrollTo(head);
				});
				sub.padding(Insets.left(16));
				sub.child(label("§7" + section.name()));
				rows.add(sub);
			}
		}
	}

	private FlowLayout clickRow(int height, boolean selected, Runnable click) {
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(height));
		row.verticalAlignment(VerticalAlignment.CENTER);
		row.surface(selected ? ROW_SELECTED : Surface.BLANK);
		if (selected) return row;
		row.cursorStyle(CursorStyle.HAND);
		row.mouseEnter().subscribe(() -> row.surface(ROW_HOVER));
		row.mouseLeave().subscribe(() -> row.surface(Surface.BLANK));
		row.mouseDown().subscribe((event, doubleClick) -> {
			click.run();
			return true;
		});
		return row;
	}

	private void fillSearch(List<UIComponent> body) {
		boolean any = false;
		for (Category c : SettingsList.CATEGORIES) {
			Category hits = c.matching(query);
			if (hits.sections().isEmpty()) continue;
			LabelComponent head = label("§e" + c.name());
			if (any) head.margins(Insets.top(10));
			body.add(head);
			sections(body, hits);
			any = true;
		}
		if (!any) body.add(wrapped("§8No settings match \"" + query.strip() + "\""));
	}

	private void fillCategory(List<UIComponent> body, Category c) {
		categoryHead = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(BUTTON));
		categoryHead.verticalAlignment(VerticalAlignment.CENTER);
		LabelComponent name = label("§e" + c.name());
		name.horizontalSizing(Sizing.expand());
		categoryHead.child(name);
		body.add(categoryHead);
		body.add(wrapped("§7" + c.description()));
		sections(body, c);
		if (c.id().equals(SettingsList.MONEY)) prices(body);
		armReset(false);
	}

	// asks twice like edit hud. new button each time, owo doesnt re-measure a changed label
	private void armReset(boolean armed) {
		resetArmed = armed;
		boolean shown = resetCategory != null && resetCategory.hasParent();
		if (resetCategory != null) {
			tips.remove(resetCategory);
			if (shown) {
				dropFocusIn(resetCategory);
				categoryHead.removeChild(resetCategory);
			}
		}
		Category c = category(category);
		resetCategory = new IconButton(Component.literal(armed ? "§cReally reset all?" : "§cReset all"), RESET_ALL_ICON, 8, 8, b -> {
			if (!resetArmed) {
				armReset(true);
				return;
			}
			armReset(false);
			apply(c::reset);
		});
		resetCategory.verticalSizing(Sizing.fixed(BUTTON));
		tip(resetCategory, armed ? "§fClick again to reset every " + c.name() + " setting to its default. This can't be undone."
				: "§fReset every " + c.name() + " setting to its default");
		if (shown) categoryHead.child(resetCategory);
	}

	private void dropFocusIn(UIComponent removed) {
		for (UIComponent f = root.focusHandler().focused(); f != null; f = f.parent()) {
			if (f == removed) {
				root.focusHandler().focus(null, null);
				return;
			}
		}
	}

	// any click off the armed button disarms it, like edit hud
	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		if (resetArmed && !resetCategory.isInBoundingBox(click.x(), click.y())) armReset(false);
		return super.mouseClicked(click, doubled);
	}

	private void sections(List<UIComponent> body, Category c) {
		for (Section section : c.sections()) {
			body.add(sectionHead(section.name()));
			for (Setting setting : section.settings()) {
				Card card = built.computeIfAbsent(setting, Card::new);
				body.add(card.box);
				cards.add(card);
			}
		}
	}

	private FlowLayout sectionHead(String name) {
		FlowLayout head = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		head.gap(4);
		head.verticalAlignment(VerticalAlignment.CENTER);
		head.margins(Insets.of(10, 1, 0, 0));
		head.child(label("§f" + name));
		head.child(UIComponents.box(Sizing.expand(), Sizing.fixed(1)).color(Color.ofArgb(0x1CFFFFFF)).fill(true));
		sectionHeads.put(name, head);
		return head;
	}

	private void prices(List<UIComponent> body) {
		body.add(sectionHead("Prices"));
		FlowLayout box = card();
		FlowLayout ages = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
		ages.gap(12);
		bazaarAge = label("");
		binsAge = label("");
		ages.child(bazaarAge);
		ages.child(binsAge);
		box.child(ages);
		LabelComponent note = label("§7Fetched when you enter a shaft or open the journal.");
		note.horizontalSizing(Sizing.fixed(mainInner() - 2 * CARD_PADDING));
		box.child(note);
		body.add(box);
	}

	private void updatePrices() {
		if (bazaarAge == null) return;
		PriceBook book = Surveyor.MONEY.book();
		long now = System.currentTimeMillis();
		String bazaar = "§7Bazaar " + age(book.bazaarAt(), LiveMoney.BAZAAR_MAX_AGE, now);
		String bins = "§7Lowest BIN " + age(book.binsAt(), LiveMoney.BINS_MAX_AGE, now);
		// .text() relayouts the page even when nothing changed
		if (!bazaar.equals(bazaarText)) bazaarAge.text(Component.literal(bazaarText = bazaar));
		if (!bins.equals(binsText)) binsAge.text(Component.literal(binsText = bins));
	}

	private void updateSaveNote() {
		String text = Surveyor.SETTINGS.saveError() == null ? "§8Saved in config.json" : "§cCould not save config.json, see the log";
		if (!text.equals(saveText)) saveNote.text(Component.literal(saveText = text));
	}

	// green = still fresh, gray = waits for the next shaft or journal open
	private static String age(long at, long maxAge, long now) {
		boolean fresh = at > 0 && now - at <= maxAge;
		if (!fresh && Surveyor.MONEY.refreshing()) return "§7fetching";
		if (at <= 0) return "§8never";
		long minutes = Math.max(0, now - at) / 60_000;
		String ago = minutes < 1 ? "just now" : minutes < 60 ? minutes + "m ago" : minutes < 24 * 60 ? minutes / 60 + "h ago"
				: minutes / (24 * 60) + "d ago";
		return (fresh ? "§a" : "§7") + ago;
	}

	private static FlowLayout card() {
		FlowLayout box = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		box.surface(CARD);
		box.padding(Insets.of(CARD_PADDING));
		box.gap(2);
		return box;
	}

	// built on memory now, not a stale copy. true = cards refreshed
	private boolean apply(Function<Settings, Settings> change) {
		// a click lands before the tick, keep a number typed right before it
		flushTyped();
		Settings next = Surveyor.SETTINGS.change(change);
		if (next.equals(settings)) return false;
		settings = next;
		refreshCards();
		return true;
	}

	private void flushTyped() {
		for (Card card : List.copyOf(cards)) card.flush();
	}

	private void refreshCards() {
		for (Card card : cards) card.refresh();
		if (resetCategory == null) return;
		// only there when something isnt default, like the per setting ones
		boolean show = !category(category).isDefault(settings);
		if (show && !resetCategory.hasParent()) categoryHead.child(resetCategory);
		else if (!show && resetCategory.hasParent()) {
			categoryHead.removeChild(resetCategory);
			if (resetArmed) armReset(false);
		}
	}

	private void tip(UIComponent c, String text) {
		tip(tips, c, text);
	}

	private void tip(Map<UIComponent, List<FormattedCharSequence>> target, UIComponent c, String text) {
		target.put(c, font.split(Component.literal(text), TIP_WIDTH));
	}

	// removed parts keep their old bounds, so hasParent
	private void drawTip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (!mainScroll.isInBoundingBox(mouseX, mouseY)) return;
		List<FormattedCharSequence> lines = ScreenParts.pick(tips, mouseX, mouseY, UIComponent::hasParent);
		if (lines != null) graphics.setTooltipForNextFrame(font, lines, ScreenParts.FIT, mouseX, mouseY, true);
	}

	// widest of all settings, so controls line up. narrow screens: own width, more room for text
	private int controlWidth(Setting of) {
		if (width < NARROW) return controlWidthOf(of);
		if (widestControl < 0) {
			int widest = toggleWidth();
			for (Category c : SettingsList.CATEGORIES) {
				for (Setting setting : c.settings()) widest = Math.max(widest, controlWidthOf(setting));
			}
			widestControl = widest;
		}
		return widestControl;
	}

	private int controlWidthOf(Setting setting) {
		return switch (setting) {
			case Choice<?> choice -> {
				int text = 0;
				for (Option<?> option : choice.options()) text = Math.max(text, font.width(option.label()));
				yield 2 + 8 + 4 + text + 2;
			}
			case Setting.Number number -> FIELD + 3 + font.width(number.unit());
			case Setting.Toggle toggle -> toggleWidth();
		};
	}

	// read once, f.e. journal_icon is 7 wide
	private int iconWidth(Identifier icon) {
		return ICON_WIDTHS.computeIfAbsent(icon, id -> {
			try (var in = minecraft.getResourceManager().open(id); NativeImage image = NativeImage.read(in)) {
				return Math.min(8, image.getWidth());
			} catch (Exception e) {
				return 8;
			}
		});
	}

	private int toggleWidth() {
		return Math.max(font.width("On"), font.width("Off")) + 8 + 3 + 12;
	}

	private final class Card {
		private final Setting setting;
		private final FlowLayout box;
		// only while shown, fillBody hands them over
		private final Map<UIComponent, List<FormattedCharSequence>> tips = new IdentityHashMap<>();
		private final FlowLayout nameRow;
		private final LabelComponent description;
		private final FlowLayout extra;
		private String descriptionText;
		private final IconButton reset;
		private final List<FlowLayout> options = new ArrayList<>();
		private int picked = -1;
		private IconButton toggle;
		private TextBoxComponent field;
		private String lastText;
		private boolean pending;
		private int typedAt;
		private boolean wasFocused;

		Card(Setting setting) {
			this.setting = setting;
			int content = mainInner() - 2 * CARD_PADDING;
			int controlWidth = controlWidth(setting);
			boolean beside = content - controlWidth - CARD_GAP >= MIN_TEXT;
			int textWidth = beside ? content - controlWidth - CARD_GAP : content;

			box = beside ? UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content())
					: UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
			box.surface(CARD);
			box.padding(Insets.of(CARD_PADDING));
			box.gap(beside ? CARD_GAP : 3);
			box.verticalAlignment(VerticalAlignment.CENTER);
			box.mouseEnter().subscribe(() -> box.surface(CARD_HOVER));
			box.mouseLeave().subscribe(() -> box.surface(CARD));

			FlowLayout text = UIContainers.verticalFlow(Sizing.fixed(textWidth), Sizing.content());
			text.gap(1);
			nameRow = UIContainers.horizontalFlow(Sizing.content(), Sizing.fixed(12));
			nameRow.gap(3);
			nameRow.verticalAlignment(VerticalAlignment.CENTER);
			text.child(nameRow);
			description = label(descriptionText = "§7" + setting.description());
			description.horizontalSizing(Sizing.fixed(textWidth));
			text.child(description);
			extra = UIContainers.verticalFlow(Sizing.fixed(textWidth), Sizing.content());
			text.child(extra);
			box.child(text);
			box.child(control(controlWidth));

			reset = new IconButton(Component.empty(), RESET_ICON, 8, 8, b -> apply(setting::reset));
			reset.horizontalSizing(Sizing.fixed(12));
			reset.verticalSizing(Sizing.fixed(12));
			tip(tips, reset, "§fReset to " + setting.defaultText());
		}

		private FlowLayout control(int available) {
			FlowLayout control = UIContainers.horizontalFlow(Sizing.fixed(available), Sizing.content());
			control.gap(2);
			control.verticalAlignment(VerticalAlignment.CENTER);
			switch (setting) {
				case Choice<?> choice -> choices(control, choice, available);
				case Setting.Number number -> {
					field = UIComponents.textBox(Sizing.fixed(FIELD));
					field.verticalSizing(Sizing.fixed(BUTTON));
					field.setMaxLength(24);
					field.text(Setting.Number.format(number.get().applyAsDouble(settings)));
					// box cuts past max length, compare w/ what it holds
					lastText = field.getValue();
					homeFields.add(field);
					tip(tips, field, "§8Default: " + number.defaultText());
					control.child(field);
					LabelComponent unit = label("§f" + number.unit());
					unit.margins(Insets.left(1));
					control.child(unit);
				}
				case Setting.Toggle t -> {
					toggle = new IconButton(Component.literal("§fOff"), CROSS_ICON, 8, 8, b -> apply(s -> t.set().apply(s, !t.get().test(s))));
					toggle.horizontalSizing(Sizing.fixed(toggleWidth()));
					toggle.verticalSizing(Sizing.fixed(BUTTON));
					tip(tips, toggle, "§8Default: " + t.defaultText());
					control.child(toggle);
				}
			}
			return control;
		}

		private <T> void choices(FlowLayout control, Choice<T> choice, int available) {
			FlowLayout list = UIContainers.verticalFlow(Sizing.fixed(available), Sizing.content());
			for (Option<T> option : choice.options()) {
				int index = options.size();
				FlowLayout row = UIContainers.horizontalFlow(Sizing.fixed(available), Sizing.fixed(OPTION));
				row.verticalAlignment(VerticalAlignment.CENTER);
				row.padding(Insets.horizontal(2));
				row.gap(4);
				row.cursorStyle(CursorStyle.HAND);
				row.mouseEnter().subscribe(() -> {
					if (index != picked) row.surface(ROW_HOVER);
				});
				row.mouseLeave().subscribe(() -> row.surface(Surface.BLANK));
				row.mouseDown().subscribe((event, doubleClick) -> {
					if (index == picked) return false;
					row.surface(Surface.BLANK);
					apply(s -> choice.set().apply(s, option.value()));
					return true;
				});
				tip(tips, row, "§e" + option.label() + "\n§7" + option.tip() + (choice.isDefault(option) ? "\n§8Default" : ""));
				options.add(row);
				list.child(row);
			}
			control.child(list);
		}

		// polled bc caxton cancels onValueChange
		void poll() {
			if (!(setting instanceof Setting.Number)) return;
			boolean focused = field.isFocused();
			if (!field.getValue().equals(lastText)) {
				lastText = field.getValue();
				pending = true;
				typedAt = ticks;
				refresh();
			}
			if (pending && (!focused || ticks - typedAt >= TYPE_DELAY)) flush();
			// clicked away from bad text, put the saved value back
			else if (wasFocused && !focused) refresh();
			wasFocused = focused;
		}

		void flush() {
			if (!pending || !(setting instanceof Setting.Number number)) return;
			pending = false;
			Double value = number.parse(field.getValue());
			if (value == null || !apply(s -> number.set().apply(s, value))) refresh();
		}

		void refresh() {
			String unused = setting.unused(settings);
			nameRow.clearChildren();
			nameRow.child(label((unused == null ? "§f" : "§8") + setting.name()));
			if (!setting.isDefault(settings)) nameRow.child(reset);
			String text = (unused == null ? "§7" : "§8") + setting.description();
			// .text() relayouts, only on a real change
			if (!text.equals(descriptionText)) description.text(Component.literal(descriptionText = text));
			extra.clearChildren();
			if (unused != null) {
				LabelComponent why = label("§7" + unused);
				why.horizontalSizing(Sizing.fill(100));
				extra.child(why);
			}
			switch (setting) {
				case Choice<?> choice -> refreshChoice(choice);
				case Setting.Number number -> {
					double value = number.get().applyAsDouble(settings);
					Double typed = number.parse(field.getValue());
					// reset or hand edit, never while typing
					if (!field.isFocused() && (typed == null || typed != value)) {
						field.setValue(Setting.Number.format(value));
						lastText = field.getValue();
						pending = false;
						typed = value;
					}
					if (typed == null) {
						LabelComponent error = label("§cNot a number from " + Setting.Number.format(number.min()) + " to "
								+ Setting.Number.format(number.max()) + ", still using " + Setting.Number.format(value) + number.unit());
						error.horizontalSizing(Sizing.fill(100));
						extra.child(error);
					}
				}
				case Setting.Toggle t -> {
					boolean on = t.get().test(settings);
					toggle.icon(on ? CHECK_ICON : CROSS_ICON);
					toggle.setMessage(Component.literal(on ? "§fOn" : "§fOff"));
				}
			}
		}

		private <T> void refreshChoice(Choice<T> choice) {
			T value = choice.get().apply(settings);
			picked = -1;
			for (int i = 0; i < options.size(); i++) {
				Option<T> option = choice.options().get(i);
				boolean selected = option.value().equals(value);
				if (selected) picked = i;
				FlowLayout row = options.get(i);
				row.clearChildren();
				row.cursorStyle(selected ? CursorStyle.POINTER : CursorStyle.HAND);
				row.child(UIComponents.texture(selected ? RADIO_ON : RADIO_OFF, 0, 0, 8, 8, 8, 8));
				row.child(label((selected ? "§e" : "§f") + option.label()));
			}
		}
	}
}
