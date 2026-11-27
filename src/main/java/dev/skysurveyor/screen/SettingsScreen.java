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
import io.wispforest.owo.ui.component.ColorPickerComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.HorizontalAlignment;
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
import dev.skysurveyor.debug.LiveDebug;
import dev.skysurveyor.hud.EditHudScreen;
import dev.skysurveyor.money.LiveMoney;
import dev.skysurveyor.money.PriceBook;
import dev.skysurveyor.settings.Setting;
import dev.skysurveyor.settings.Setting.Action;
import dev.skysurveyor.settings.Setting.Choice;
import dev.skysurveyor.settings.Setting.Option;
import dev.skysurveyor.settings.Settings;
import dev.skysurveyor.settings.SettingsList;
import dev.skysurveyor.settings.SettingsList.Category;
import dev.skysurveyor.settings.SettingsList.Preview;
import dev.skysurveyor.settings.SettingsList.Section;
import dev.skysurveyor.util.Format;

import static dev.skysurveyor.screen.ScreenParts.label;
import static dev.skysurveyor.screen.ScreenParts.wrapped;

// mockup docs/mockups/settings-v3.html
public final class SettingsScreen extends BaseOwoScreen<FlowLayout> implements ModScreen {
	private static final int ROW_FILL = 0x0CFFFFFF;
	private static final int ROW_HOVER = 0x18FFFFFF;
	private static final int CHANGED_EDGE = 0xFFFFFF55;
	private static final Surface SIDE_HOVER = Surface.flat(0x18FFFFFF);
	private static final Surface SIDE_SELECTED = Surface.flat(0x26FFFFFF);
	private static final int PADDING = 8;
	private static final int GAP = 4;
	private static final int BAR_PADDING = 4;
	private static final int BAR_GAP = 4;
	private static final int SIDE_PADDING = 3;
	private static final int MAIN_PADDING = 7;
	private static final int SCROLLBAR = 3;
	private static final int HELP_PADDING = 7;
	private static final int BUTTON = 16;
	private static final int FIELD = 44;
	// display choices
	private static final int ROW_TOP = 3;
	private static final int ROW_LEFT = 6;
	private static final int ROW_RIGHT = 5;
	private static final int ROW_GAP = 6;
	private static final int INDENT = 10;
	private static final int EDGE = 2;
	private static final int CHOICE_PAD = 10;
	private static final int SWATCH = 12;
	private static final int PICKER_WIDTH = 150;
	private static final int PICKER_HEIGHT = 60;
	// text narrower = control under it
	private static final int MIN_TEXT = 140;
	// at or below = every control under its text, like the mockup at scale 4
	private static final int STACK_WIDTH = 480;
	// above = help pane on the right
	private static final int PANE_WIDTH = 640;
	private static final int TIP_WIDTH = 200;
	// quiet ticks before a typed number applies
	private static final int TYPE_DELAY = 10;
	private static final Identifier SEARCH_ICON = icon("search_icon");
	private static final Identifier RESET_ICON = icon("reset_icon");
	private static final Identifier RESET_ALL_ICON = icon("reset_red_icon");
	private static final Map<Identifier, Integer> ICON_WIDTHS = new HashMap<>();

	private final Screen parent;
	private Settings settings = Surveyor.SETTINGS.get();
	private String query = "";
	private String category = SettingsList.MONEY;
	private FlowLayout root;
	private FlowLayout side;
	private FlowLayout body;
	private FlowLayout main;
	private KeptScroll mainScroll;
	// null = no pane at this size
	private FlowLayout help;
	private KeptScroll helpScroll;
	private TextBoxComponent search;
	private FlowLayout resetHolder;
	private ButtonComponent resetAll;
	private boolean resetArmed;
	private LabelComponent bazaarAge;
	private LabelComponent binsAge;
	private String bazaarText;
	private String binsText;
	private LabelComponent saveNote;
	private String saveText;
	private final List<Row> rows = new ArrayList<>();
	// built once per size, a category or search only re-adds them
	private final Map<Setting, Row> built = new IdentityHashMap<>();
	// what the page lists now, folding changes it
	private List<Setting> shown = List.of();
	private final List<TextBoxComponent> homeFields = new ArrayList<>();
	private final Map<UIComponent, List<FormattedCharSequence>> tips = new IdentityHashMap<>();
	// side count tips, rebuilt w/ the side
	private final Map<UIComponent, List<FormattedCharSequence>> sideTips = new IdentityHashMap<>();
	private String sideKey;
	// row under the mouse, row the pane shows
	private Row hovered;
	private Setting hot;
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
			hot = null;
			fillBody();
		}
		for (Row row : List.copyOf(rows)) row.poll();
		// hand edit picked up on the io thread
		Settings now = Surveyor.SETTINGS.get();
		if (!now.equals(settings)) {
			settings = now;
			changed();
		}
		if (++ticks % 10 == 0) updatePrices();
		updateSaveNote();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		homeFields.forEach(ScreenParts::rehome);
		homeFields.clear();
		trackHover(mouseX, mouseY);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		drawTip(graphics, mouseX, mouseY);
	}

	private void rebuild() {
		builtWidth = width;
		builtHeight = height;
		flushTyped();
		built.clear();
		rows.clear();
		hot = null;
		hovered = null;
		sideKey = null;
		root.clearChildren();
		root.child(topBar());
		body = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.expand());
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
		help = null;
		helpScroll = null;
		if (pane()) {
			help = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
			help.gap(3);
			helpScroll = new KeptScroll(Sizing.fixed(helpWidth()), Sizing.fill(100), help);
			helpScroll.surface(ScreenParts.PANEL);
			helpScroll.padding(Insets.of(HELP_PADDING - 1, HELP_PADDING, HELP_PADDING, HELP_PADDING));
			body.child(helpScroll);
		}
		root.child(body);
		FlowLayout footer = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		footer.gap(16);
		saveNote = label("");
		saveText = null;
		updateSaveNote();
		footer.child(saveNote);
		if (!pane()) footer.child(label("§7Hover a setting for details"));
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
		title.child(label(width <= STACK_WIDTH ? "§fSettings" : "§fSurveyor §7Settings"));
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
		// done = closes, like edit hud
		ButtonComponent back = PackButton.button(Component.literal("Done"), b -> onClose());
		// same as owo's content(6)
		back.horizontalSizing(Sizing.fixed(font.width("Done") + 20));
		back.margins(Insets.left(2));
		bar.child(back);
		return bar;
	}

	// fits "Bug reports 1" in the vanilla font
	private int sideWidth() {
		return width <= 640 ? 104 : 116;
	}

	private boolean pane() {
		return width > PANE_WIDTH;
	}

	// mockup 250 at 960
	private int helpWidth() {
		return Math.clamp(width * 26 / 100, 200, 340);
	}

	private int helpInner() {
		return helpWidth() - 2 * HELP_PADDING - SCROLLBAR;
	}

	private int mainInner() {
		int used = 2 * PADDING + sideWidth() + GAP + 2 * MAIN_PADDING + SCROLLBAR + 2;
		if (pane()) used += helpWidth() + GAP;
		return width - used;
	}

	private boolean searching() {
		return !query.isBlank();
	}

	// added in one go, owo relayouts per child
	private void fillBody() {
		flushTyped();
		rows.clear();
		resetHolder = null;
		resetAll = null;
		resetArmed = false;
		bazaarAge = null;
		binsAge = null;
		bazaarText = null;
		binsText = null;
		hovered = null;
		main.clearChildren();
		List<UIComponent> list = new ArrayList<>();
		if (searching()) fillSearch(list);
		else fillCategory(list, category(category));
		main.children(list);
		shown = rows.stream().map(r -> r.setting).toList();
		fillSide();
		refreshRows();
		updatePrices();
		fillHelp();
	}

	private static Category category(String id) {
		return SettingsList.CATEGORIES.stream().filter(c -> c.id().equals(id)).findFirst().orElse(SettingsList.CATEGORIES.getFirst());
	}

	// counts or hits change it, else kept
	private void fillSide() {
		StringBuilder key = new StringBuilder(category).append('|').append(query);
		for (Category c : SettingsList.CATEGORIES) {
			key.append('|').append(searching() ? c.matching(query).settings().size() : c.changed(settings));
		}
		if (key.toString().equals(sideKey)) return;
		sideKey = key.toString();
		sideTips.clear();
		side.clearChildren();
		List<UIComponent> entries = new ArrayList<>();
		boolean lined = false;
		for (Category c : SettingsList.CATEGORIES) {
			if (c.apart() && !lined) {
				lined = true;
				entries.add(UIContainers.verticalFlow(Sizing.fill(100), Sizing.expand()));
				FlowLayout line = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(1));
				line.surface(Surface.flat(0x1CFFFFFF));
				line.margins(Insets.of(3, 3, 2, 2));
				entries.add(line);
			}
			int hits = searching() ? c.matching(query).settings().size() : 0;
			int changed = c.changed(settings);
			boolean on = !searching() && c.id().equals(category);
			FlowLayout row = clickRow(16, on, () -> {
				category = c.id();
				query = "";
				search.setValue("");
				hot = null;
				fillBody();
				mainScroll.offset(0);
			});
			row.padding(Insets.horizontal(4));
			row.gap(4);
			// fixed 8 wide so names line up
			FlowLayout iconBox = UIContainers.horizontalFlow(Sizing.fixed(8), Sizing.fixed(8));
			Identifier icon = icon(c.icon());
			int iconWidth = iconWidth(icon);
			iconBox.child(UIComponents.texture(icon, 0, 0, iconWidth, 8, iconWidth, 8));
			row.child(iconBox);
			boolean dim = searching() ? hits == 0 : c.apart() && !on;
			String number = searching() ? hits > 0 ? "§7" + hits : "" : changed > 0 ? "§e" + changed : "";
			// one line, clipped if the count leaves no room
			int room = sideWidth() - 2 * SIDE_PADDING - 8 - 8 - 4 - 4 - (number.isEmpty() ? 0 : 4 + font.width(number));
			row.child(label((on ? "§e" : dim ? "§8" : "§f") + ScreenParts.clip(font, c.name(), room)));
			row.child(UIContainers.horizontalFlow(Sizing.expand(), Sizing.fixed(1)));
			if (!number.isEmpty()) {
				LabelComponent count = label(number);
				if (!searching()) sideTips.put(count, font.split(Component.literal("§f" + changed + " changed from the default"), TIP_WIDTH));
				row.child(count);
			}
			entries.add(row);
		}
		side.children(entries);
	}

	private FlowLayout clickRow(int height, boolean selected, Runnable click) {
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(height));
		row.verticalAlignment(VerticalAlignment.CENTER);
		row.surface(selected ? SIDE_SELECTED : Surface.BLANK);
		if (selected) return row;
		row.cursorStyle(CursorStyle.HAND);
		row.mouseEnter().subscribe(() -> row.surface(SIDE_HOVER));
		row.mouseLeave().subscribe(() -> row.surface(Surface.BLANK));
		row.mouseDown().subscribe((event, doubleClick) -> {
			click.run();
			return true;
		});
		return row;
	}

	private void fillSearch(List<UIComponent> list) {
		boolean any = false;
		for (Category c : SettingsList.CATEGORIES) {
			Category hits = c.matching(query);
			if (hits.sections().isEmpty()) continue;
			LabelComponent head = label("§e" + c.name());
			head.margins(Insets.of(any ? 10 : 2, 1, 0, 0));
			list.add(head);
			// folded ones show too, not indented
			for (Setting setting : hits.settings()) addRow(list, setting, false);
			any = true;
		}
		if (!any) list.add(wrapped("§8No settings match \"" + query.strip() + "\""));
	}

	private void fillCategory(List<UIComponent> list, Category c) {
		if (!pane()) {
			LabelComponent intro = label("§7" + c.description());
			intro.horizontalSizing(Sizing.fixed(mainInner()));
			intro.margins(Insets.bottom(2));
			list.add(intro);
		}
		boolean first = true;
		for (Section section : c.sections()) {
			list.add(sectionHead(section.name(), first));
			first = false;
			for (Setting setting : section.settings()) {
				if (!setting.folded(settings)) addRow(list, setting, setting.isChild());
			}
		}
		if (c.id().equals(SettingsList.MONEY)) prices(list);
		if (!pane()) {
			resetHolder = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
			resetHolder.margins(Insets.top(8));
			list.add(resetHolder);
		}
	}

	private void addRow(List<UIComponent> list, Setting setting, boolean indented) {
		Row row = built.computeIfAbsent(setting, Row::new);
		row.place(indented);
		list.add(row.box);
		rows.add(row);
	}

	private FlowLayout sectionHead(String name, boolean first) {
		FlowLayout head = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		head.gap(4);
		head.verticalAlignment(VerticalAlignment.CENTER);
		head.margins(Insets.of(first ? 2 : 9, 1, 0, 0));
		head.child(label("§f" + name));
		head.child(UIComponents.box(Sizing.expand(), Sizing.fixed(1)).color(Color.ofArgb(0x1CFFFFFF)).fill(true));
		return head;
	}

	private void prices(List<UIComponent> list) {
		list.add(sectionHead("Prices", false));
		FlowLayout box = UIContainers.verticalFlow(Sizing.fixed(mainInner()), Sizing.content());
		box.surface(Surface.flat(ROW_FILL));
		box.padding(Insets.of(ROW_TOP, ROW_TOP, ROW_LEFT, ROW_RIGHT));
		box.gap(1);
		box.margins(Insets.top(2));
		FlowLayout ages = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
		ages.gap(12);
		bazaarAge = label("");
		binsAge = label("");
		ages.child(bazaarAge);
		ages.child(binsAge);
		box.child(ages);
		LabelComponent note = label("§7Fetched when you enter a shaft or open the journal.");
		note.horizontalSizing(Sizing.fixed(mainInner() - ROW_LEFT - ROW_RIGHT));
		box.child(note);
		list.add(box);
	}

	// pane: the hot setting, else the category w/ its reset all
	private void fillHelp() {
		if (help != null) {
			resetHolder = null;
			help.clearChildren();
			List<UIComponent> parts = new ArrayList<>();
			if (hot != null) settingHelp(parts, hot);
			else categoryHelp(parts);
			help.children(parts);
			helpScroll.offset(0);
		}
		fillReset();
	}

	private void categoryHelp(List<UIComponent> parts) {
		Category c = category(category);
		parts.add(helpLine(searching() ? "§fSearch" : "§f" + c.name()));
		parts.add(helpLine(searching() ? "§7Results from every category. Click a category to leave the search." : "§7" + c.description()));
		parts.add(helpLine("§8Hover a setting to see what it does and what each choice means."));
		if (searching()) return;
		int changed = c.changed(settings);
		if (changed == 0) return;
		LabelComponent count = helpLine("§8" + changed + " changed here");
		count.margins(Insets.top(6));
		parts.add(count);
		resetHolder = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		parts.add(resetHolder);
	}

	private void settingHelp(List<UIComponent> parts, Setting setting) {
		parts.add(helpLine("§f" + setting.name()));
		parts.add(helpLine("§7" + setting.description()));
		if (setting instanceof Choice<?> choice) options(parts, choice);
		String unused = setting.unused(settings);
		if (unused != null) parts.add(helpLine("§8" + unused + "."));
		if (!(setting instanceof Action)) {
			LabelComponent def = helpLine("§8Default: " + setting.defaultText());
			def.margins(Insets.top(2));
			parts.add(def);
		}
		Preview preview = SettingsList.preview(setting);
		if (preview != null) parts.addAll(SettingsPreviews.of(preview, settings, font, helpInner()));
	}

	private <T> void options(List<UIComponent> parts, Choice<T> choice) {
		T value = choice.get().apply(settings);
		for (Option<T> option : choice.options()) {
			FlowLayout block = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
			block.margins(Insets.top(2));
			block.child(helpLine((option.value().equals(value) ? "§e" : "§f") + option.label()));
			block.child(helpLine("§7" + option.tip()));
			parts.add(block);
		}
	}

	private LabelComponent helpLine(String text) {
		LabelComponent line = label(text);
		line.horizontalSizing(Sizing.fixed(helpInner()));
		return line;
	}

	// owo doesnt re-measure a changed label, idk why
	private void fillReset() {
		if (resetHolder == null) return;
		resetHolder.clearChildren();
		tips.remove(resetAll);
		resetAll = null;
		Category c = category(category);
		if (searching() || c.isDefault(settings)) {
			resetArmed = false;
			return;
		}
		boolean armed = resetArmed;
		resetAll = new IconButton(Component.literal(armed ? "§cReally reset all?" : "§cReset all"), RESET_ALL_ICON, 8, 8, b -> {
			if (!resetArmed) {
				resetArmed = true;
				fillReset();
				return;
			}
			resetArmed = false;
			apply(c::reset);
		});
		resetAll.verticalSizing(Sizing.fixed(BUTTON));
		tip(resetAll, armed ? "§fClick again to reset every " + c.name() + " setting to its default. This can't be undone."
				: "§fReset every " + c.name() + " setting to its default");
		resetHolder.child(resetAll);
	}

	private void dropFocusIn(UIComponent removed) {
		for (UIComponent f = root.focusHandler().focused(); f != null; f = f.parent()) {
			if (f == removed) {
				root.focusHandler().focus(null, null);
				return;
			}
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		if (resetArmed && (resetAll == null || !resetAll.isInBoundingBox(click.x(), click.y()))) {
			resetArmed = false;
			if (resetAll != null) dropFocusIn(resetAll);
			fillReset();
		}
		return super.mouseClicked(click, doubled);
	}

	// row under the mouse lights up and fills the pane. pane keeps it while the mouse is right of the side
	private void trackHover(int mouseX, int mouseY) {
		Row over = null;
		if (mainScroll.isInBoundingBox(mouseX, mouseY)) {
			for (Row row : rows) {
				if (row.box.hasParent() && row.box.isInBoundingBox(mouseX, mouseY)) {
					over = row;
					break;
				}
			}
		}
		hovered = over;
		if (help == null) return;
		Setting want = hot;
		if (over != null) want = over.setting;
		else if (!(mouseX >= mainScroll.x() && mouseY >= body.y() && mouseY < body.y() + body.height())) want = null;
		if (want != hot) {
			hot = want;
			fillHelp();
		}
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
		// same words as edit hud
		String text = Surveyor.SETTINGS.saveError() == null ? "§7Changes save by themselves"
				: "§cCouldn't save your settings (config.json). The log says why";
		if (!text.equals(saveText)) saveNote.text(Component.literal(saveText = text));
	}

	// gray = waits for next shaft or journal open
	private static String age(long at, long maxAge, long now) {
		boolean fresh = at > 0 && now - at <= maxAge;
		if (!fresh && Surveyor.MONEY.refreshing()) return "§7fetching";
		if (at <= 0) return "§8never";
		return (fresh ? "§a" : "§7") + Format.ago(now - at);
	}

	// true = changed
	private boolean apply(Function<Settings, Settings> change) {
		// click lands before the tick
		flushTyped();
		Settings next = Surveyor.SETTINGS.change(change);
		if (next.equals(settings)) return false;
		settings = next;
		changed();
		return true;
	}

	// a fold opened or closed = new list, else refresh in place
	private void changed() {
		if (!searching() && !category(category).shown(settings).equals(shown)) {
			fillBody();
			return;
		}
		refreshRows();
		fillSide();
		fillHelp();
	}

	private void flushTyped() {
		for (Row row : List.copyOf(rows)) row.flush();
	}

	private void refreshRows() {
		for (Row row : rows) row.refresh();
	}

	private void tip(UIComponent c, String text) {
		tips.put(c, font.split(Component.literal(text), TIP_WIDTH));
	}

	// removed parts keep their old bounds, so hasParent
	private void drawTip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		List<FormattedCharSequence> lines = null;
		if (side.isInBoundingBox(mouseX, mouseY)) lines = ScreenParts.pick(sideTips, mouseX, mouseY, UIComponent::hasParent);
		else if (mainScroll.isInBoundingBox(mouseX, mouseY) || helpScroll != null && helpScroll.isInBoundingBox(mouseX, mouseY)) {
			Map<UIComponent, List<FormattedCharSequence>> all = new IdentityHashMap<>(tips);
			for (Row row : rows) all.putAll(row.tips);
			lines = ScreenParts.pick(all, mouseX, mouseY, UIComponent::hasParent);
		}
		if (lines != null) graphics.setTooltipForNextFrame(font, lines, ScreenParts.FIT, mouseX, mouseY, true);
	}

	// read once, journal_icon is 7 wide
	private int iconWidth(Identifier icon) {
		return ICON_WIDTHS.computeIfAbsent(icon, id -> {
			try (var in = minecraft.getResourceManager().open(id); NativeImage image = NativeImage.read(in)) {
				return Math.min(8, image.getWidth());
			} catch (Exception e) {
				return 8;
			}
		});
	}

	private int controlWidth(Setting setting) {
		return switch (setting) {
			case Choice<?> choice -> {
				int sum = 0;
				for (Option<?> option : choice.options()) sum += font.width(option.label()) + CHOICE_PAD;
				yield sum + choice.options().size() - 1;
			}
			case Setting.Number number -> FIELD + 3 + font.width(number.unit());
			case Setting.Toggle toggle -> Switch.width(font, true) + (toggle.swatch() == null ? 0 : SWATCH + 4);
			case Action action -> font.width(action.button()) + 20;
		};
	}

	private void runAction(Action action, Row row) {
		switch (action.id()) {
			case SettingsList.EDIT_HUD -> {
				flushTyped();
				minecraft.gui.setScreen(new EditHudScreen(this, Surveyor.HUD));
			}
			case SettingsList.COPY_DEBUG -> LiveDebug.copyDebugInfo(minecraft, () -> row.note("§aCopied. Paste it into your GitHub issue."));
			default -> {
			}
		}
	}

	private final class Row {
		private final Setting setting;
		private final FlowLayout box;
		// only while shown, drawTip reads them
		private final Map<UIComponent, List<FormattedCharSequence>> tips = new IdentityHashMap<>();
		private int indent;
		private final FlowLayout text;
		private final FlowLayout nameRow;
		private final LabelComponent line;
		private String lineText;
		private String noteText;
		private final FlowLayout extra;
		private final IconButton reset;
		private boolean changed;
		private final List<ButtonComponent> options = new ArrayList<>();
		private Switch toggle;
		private FlowLayout swatch;
		// null = closed. not in extra, refresh mid drag rebuilds
		private FlowLayout pickerBox;
		private LabelComponent pickerHex;
		private ColorPickerComponent picker;
		// picker moved by us, not a drag
		private boolean syncingPicker;
		// unsaved drag colour
		private Integer pendingColor;
		private int colorAt;
		private TextBoxComponent field;
		private String lastText;
		private boolean pending;
		private int typedAt;
		private boolean wasFocused;

		Row(Setting setting) {
			this.setting = setting;
			// text sized for the indented spot, search just has room to spare
			int content = mainInner() - ROW_LEFT - ROW_RIGHT - (setting.isChild() ? INDENT : 0);
			int controlWidth = controlWidth(setting);
			boolean beside = builtWidth > STACK_WIDTH && content - controlWidth - ROW_GAP >= MIN_TEXT;
			int textWidth = beside ? content - controlWidth - ROW_GAP : content;

			box = beside ? UIContainers.horizontalFlow(Sizing.fixed(mainInner()), Sizing.content())
					: UIContainers.verticalFlow(Sizing.fixed(mainInner()), Sizing.content());
			box.padding(Insets.of(ROW_TOP, ROW_TOP, ROW_LEFT, ROW_RIGHT));
			box.gap(beside ? 0 : 3);
			box.verticalAlignment(VerticalAlignment.CENTER);
			box.surface((graphics, c) -> {
				int left = c.x() + indent;
				graphics.fill(left, c.y(), c.x() + c.width(), c.y() + c.height(), hovered == this || hot == setting ? ROW_HOVER : ROW_FILL);
				if (changed) graphics.fill(left, c.y(), left + EDGE, c.y() + c.height(), CHANGED_EDGE);
			});

			text = UIContainers.verticalFlow(Sizing.fixed(textWidth), Sizing.content());
			text.gap(1);
			nameRow = UIContainers.horizontalFlow(Sizing.content(), Sizing.fixed(12));
			nameRow.gap(3);
			nameRow.verticalAlignment(VerticalAlignment.CENTER);
			text.child(nameRow);
			line = label(lineText = "");
			line.horizontalSizing(Sizing.fixed(textWidth));
			text.child(line);
			extra = UIContainers.verticalFlow(Sizing.fixed(textWidth), Sizing.content());
			text.child(extra);
			if (setting instanceof Setting.Toggle t && t.swatch() != null) {
				pickerBox = UIContainers.verticalFlow(Sizing.fixed(textWidth), Sizing.content());
				pickerBox.gap(2);
				text.child(pickerBox);
			}
			box.child(text);
			// only a row of choices wraps, ltr flow tops the rest
			FlowLayout control = beside ? UIContainers.horizontalFlow(Sizing.fixed(controlWidth), Sizing.content())
					: setting instanceof Choice ? UIContainers.ltrTextFlow(Sizing.fixed(textWidth), Sizing.content())
							: UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
			control.gap(beside || !(setting instanceof Choice) ? 2 : 1);
			control.verticalAlignment(VerticalAlignment.CENTER);
			if (beside) {
				control.horizontalAlignment(HorizontalAlignment.RIGHT);
				control.margins(Insets.left(ROW_GAP));
				// search rows are wider, control stays right
				box.child(UIContainers.horizontalFlow(Sizing.expand(), Sizing.fixed(1)));
			}
			control(control);
			box.child(control);

			reset = new IconButton(Component.empty(), RESET_ICON, 8, 8, b -> apply(setting::reset));
			reset.horizontalSizing(Sizing.fixed(12));
			reset.verticalSizing(Sizing.fixed(12));
			tips.put(reset, split("§fReset to " + setting.defaultText()));
			// no pane = the row's hover says it all
			if (!pane()) tips.put(text, rowTip());
		}

		// indented under its parent or flush in search. padding, a resize didnt take
		void place(boolean indented) {
			indent = indented ? INDENT : 0;
			box.padding(Insets.of(ROW_TOP, ROW_TOP, ROW_LEFT + indent, ROW_RIGHT));
		}

		private List<FormattedCharSequence> split(String text) {
			return font.split(Component.literal(text), TIP_WIDTH);
		}

		private List<FormattedCharSequence> rowTip() {
			StringBuilder tip = new StringBuilder("§f" + setting.name() + "\n§7" + setting.description());
			if (setting instanceof Choice<?> choice) {
				for (Option<?> option : choice.options()) tip.append("\n§f").append(option.label()).append("§7: ").append(option.tip());
			}
			if (!(setting instanceof Action)) tip.append("\n§8Default: ").append(setting.defaultText());
			return split(tip.toString());
		}

		private void control(FlowLayout control) {
			switch (setting) {
				case Choice<?> choice -> choices(control, choice);
				case Setting.Number number -> {
					field = UIComponents.textBox(Sizing.fixed(FIELD));
					field.verticalSizing(Sizing.fixed(BUTTON));
					field.setMaxLength(24);
					field.text(Setting.Number.format(number.get().applyAsDouble(settings)));
					// box cuts past max length, compare w/ what it holds
					lastText = field.getValue();
					homeFields.add(field);
					tips.put(field, split("§f" + setting.name() + "\n§8Default: " + number.defaultText()));
					control.child(field);
					LabelComponent unit = label("§f" + number.unit());
					unit.margins(Insets.left(1));
					control.child(unit);
				}
				case Setting.Toggle t -> {
					if (t.swatch() != null) {
						swatch = UIContainers.horizontalFlow(Sizing.fixed(SWATCH), Sizing.fixed(SWATCH));
						swatch.cursorStyle(CursorStyle.HAND);
						swatch.margins(Insets.right(2));
						swatch.mouseDown().subscribe((event, doubleClick) -> {
							togglePicker(t.swatch());
							return true;
						});
						control.child(swatch);
					}
					toggle = new Switch(t.get().test(settings), true, on -> apply(s -> t.set().apply(s, on)));
					tips.put(toggle, split("§f" + setting.name() + "\n§8Default: " + (t.get().test(Settings.DEFAULTS) ? "On" : "Off")));
					control.child(toggle);
				}
				case Action action -> {
					ButtonComponent button = PackButton.button(Component.literal(action.button()), b -> runAction(action, this));
					button.horizontalSizing(Sizing.fixed(font.width(action.button()) + 20));
					button.verticalSizing(Sizing.fixed(BUTTON));
					control.child(button);
				}
			}
		}

		private <T> void choices(FlowLayout control, Choice<T> choice) {
			for (Option<T> option : choice.options()) {
				ButtonComponent button = PackButton.button(Component.literal("§f" + option.label()), b -> {
					if (!option.value().equals(choice.get().apply(settings))) apply(s -> choice.set().apply(s, option.value()));
				});
				button.horizontalSizing(Sizing.fixed(font.width(option.label()) + CHOICE_PAD));
				button.verticalSizing(Sizing.fixed(BUTTON));
				if (!pane()) tips.put(button, split("§f" + option.label() + "\n§7" + option.tip() + (choice.isDefault(option) ? "\n§8Default" : "")));
				options.add(button);
				control.child(button);
			}
		}

		private void togglePicker(Setting.Swatch of) {
			if (!pickerBox.children().isEmpty()) {
				flushColor();
				pickerBox.clearChildren();
				picker = null;
				return;
			}
			picker = new ColorPickerComponent();
			picker.horizontalSizing(Sizing.fixed(Math.min(PICKER_WIDTH, pickerBox.width() > 0 ? pickerBox.width() : PICKER_WIDTH)));
			picker.verticalSizing(Sizing.fixed(PICKER_HEIGHT));
			picker.showAlpha(false);
			picker.selectedColor(Color.ofRgb(of.get().applyAsInt(settings)));
			picker.onChanged().subscribe(color -> {
				if (syncingPicker) return;
				pendingColor = color.rgb();
				colorAt = ticks;
				paintSwatch(pendingColor);
			});
			pickerHex = label("§7" + of.name() + " §f" + Format.hex(of.get().applyAsInt(settings)));
			pickerBox.child(picker);
			pickerBox.child(pickerHex);
		}

		private void paintSwatch(int rgb) {
			swatch.surface(Surface.flat(0xFF000000 | rgb).and(Surface.outline(0xFF000000)));
			Setting.Swatch of = ((Setting.Toggle) setting).swatch();
			tips.put(swatch, split("§f" + of.name() + " §7" + Format.hex(rgb) + "\n§7Click to pick\n§8Default: "
					+ Format.hex(of.get().applyAsInt(Settings.DEFAULTS))));
			if (pickerHex != null) pickerHex.text(Component.literal("§7" + of.name() + " §f" + Format.hex(rgb)));
		}

		private void flushColor() {
			if (pendingColor == null || !(setting instanceof Setting.Toggle t)) return;
			int rgb = pendingColor;
			pendingColor = null;
			apply(s -> t.swatch().set().apply(s, rgb));
		}

		// copied and the like, till the page is rebuilt
		void note(String text) {
			noteText = text;
			refresh();
		}

		// polled bc caxton cancels onValueChange
		void poll() {
			if (pendingColor != null && ticks - colorAt >= TYPE_DELAY) flushColor();
			if (!(setting instanceof Setting.Number)) return;
			boolean focused = field.isFocused();
			if (!field.getValue().equals(lastText)) {
				lastText = field.getValue();
				pending = true;
				typedAt = ticks;
				refresh();
			}
			if (pending && (!focused || ticks - typedAt >= TYPE_DELAY)) flush();
			// bad text, saved value back
			else if (wasFocused && !focused) refresh();
			wasFocused = focused;
		}

		void flush() {
			flushColor();
			if (!pending || !(setting instanceof Setting.Number number)) return;
			pending = false;
			Double value = number.parse(field.getValue());
			if (value == null || !apply(s -> number.set().apply(s, value))) refresh();
		}

		void refresh() {
			String unused = setting.unused(settings);
			changed = !setting.isDefault(settings);
			nameRow.clearChildren();
			nameRow.child(label((unused == null ? "§f" : "§8") + setting.name()));
			if (changed) nameRow.child(reset);
			String text = noteText != null ? noteText : unused != null ? "§8" + unused : "§7" + setting.line(settings);
			// .text() relayouts, only on a real change
			if (!text.equals(lineText)) line.text(Component.literal(lineText = text));
			extra.clearChildren();
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
					toggle.on(t.get().test(settings));
					if (swatch != null && pendingColor == null) {
						int rgb = t.swatch().get().applyAsInt(settings);
						paintSwatch(rgb);
						// reset or hand edit while its open
						if (picker != null && picker.selectedColor().rgb() != rgb) {
							syncingPicker = true;
							try {
								picker.selectedColor(Color.ofRgb(rgb));
							} finally {
								syncingPicker = false;
							}
						}
					}
				}
				case Action action -> {
				}
			}
		}

		private <T> void refreshChoice(Choice<T> choice) {
			T value = choice.get().apply(settings);
			for (int i = 0; i < options.size(); i++) {
				Option<T> option = choice.options().get(i);
				boolean picked = option.value().equals(value);
				ButtonComponent button = options.get(i);
				String message = (picked ? "§e" : "§f") + option.label();
				if (!message.equals(button.getMessage().getString())) button.setMessage(Component.literal(message));
				button.cursorStyle(picked ? CursorStyle.POINTER : CursorStyle.HAND);
			}
		}
	}
}
