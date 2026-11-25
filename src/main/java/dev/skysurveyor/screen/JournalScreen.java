package dev.skysurveyor.screen;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.BoxComponent;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import io.wispforest.owo.ui.util.FocusHandler;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import dev.skysurveyor.Surveyor;
import dev.skysurveyor.data.CorpseType;
import dev.skysurveyor.data.ItemNames;
import dev.skysurveyor.data.ShaftNames;
import dev.skysurveyor.detection.CorpseLoot;
import dev.skysurveyor.detection.ItemCount;
import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.hud.EditHudScreen;
import dev.skysurveyor.journal.BreakEntry;
import dev.skysurveyor.journal.Breaks;
import dev.skysurveyor.journal.CorpseCounts;
import dev.skysurveyor.journal.GemYield;
import dev.skysurveyor.journal.Journal;
import dev.skysurveyor.journal.JournalEntry;
import dev.skysurveyor.journal.JournalStats;
import dev.skysurveyor.journal.JournalStats.AllTime;
import dev.skysurveyor.journal.JournalStats.CorpseCount;
import dev.skysurveyor.journal.JournalStats.SessionSummary;
import dev.skysurveyor.journal.LiveJournal;
import dev.skysurveyor.journal.LootSummary;
import dev.skysurveyor.journal.MiningEntry;
import dev.skysurveyor.journal.OutsideMining;
import dev.skysurveyor.journal.StatsRange;
import dev.skysurveyor.journal.SummaryText;
import dev.skysurveyor.mining.Efficiency;
import dev.skysurveyor.mining.EfficiencyStats;
import dev.skysurveyor.mining.MiningText;
import dev.skysurveyor.money.ChartTips;
import dev.skysurveyor.money.CoinBreakdown;
import dev.skysurveyor.money.Coins;
import dev.skysurveyor.money.MoneyStats;
import dev.skysurveyor.money.PriceRules;
import dev.skysurveyor.money.ShaftValue;
import dev.skysurveyor.money.ShaftValue.Line;
import dev.skysurveyor.money.ShaftValue.Part;
import dev.skysurveyor.screen.TableColumns.Column;
import dev.skysurveyor.settings.JournalSettings;
import dev.skysurveyor.settings.JournalSettings.ChartType;
import dev.skysurveyor.settings.MoneySettings;
import dev.skysurveyor.settings.Setting;
import dev.skysurveyor.settings.SettingsList;
import dev.skysurveyor.util.Format;

import static dev.skysurveyor.screen.JournalTable.barCell;
import static dev.skysurveyor.screen.JournalTable.cell;
import static dev.skysurveyor.screen.JournalTable.tableRow;
import static dev.skysurveyor.screen.ScreenParts.PANEL;
import static dev.skysurveyor.screen.ScreenParts.applyTooltip;
import static dev.skysurveyor.screen.ScreenParts.label;
import static dev.skysurveyor.screen.ScreenParts.withTips;
import static dev.skysurveyor.screen.ScreenParts.wrapped;

// mc font only so pack fonts work
public final class JournalScreen extends BaseOwoScreen<FlowLayout> implements ModScreen {
	private enum Tab { OVERVIEW, SESSION, STATS }

	// stats tab w/o prices, per range. stats null = no shafts in it
	private record StatsData(List<SessionSummary> sessions, List<JournalEntry> entries, List<MiningEntry> mining, AllTime stats,
			JournalStats.Campfires campfires, Long recentSpawn, Long allTimeSpawn) {
	}

	// the prices part, coins per session in oldestFirst order
	private record MoneyData(StatsData of, int changes, MoneyStats money, double tax, List<Double> sessionCoins) {
	}

	private record ShiftTip(FlowLayout row, List<ClientTooltipComponent> normal, List<ClientTooltipComponent> shifted) {
	}

	// opaque, it covers the table
	static final Surface OVERLAY = Surface.flat(0xFF141414).and(Surface.outline(0x50FFFFFF));
	private static final Surface ROW_EVEN = Surface.flat(0x0DFFFFFF);
	private static final Surface ROW_DAY = Surface.flat(0x1CFFFFFF);
	private static final Surface ROW_HOVER = Surface.flat(HoverFlow.ROW);
	private static final Surface ROW_SELECTED = Surface.flat(0x26FFFFFF).and(Surface.outline(0x50FFFFFF));
	private static final Surface SLOT = Surface.flat(0xFF8B8B8B).and(Surface.outline(0xFF373737));
	private static final int BAR_GREEN = 0xCC55FF55;
	private static final int BAR_GOLD = 0xCCFFAA00;
	private static final int BAR_RED = 0xCCFF5555;
	private static final int ROW_HEIGHT = JournalTable.ROW_HEIGHT;
	// display choices from here, not game numbers
	static final int PADDING = 8;
	private static final int GAP = 5;
	private static final int COLUMN_PADDING = 6;
	private static final int SECTION_GAP = 6;
	private static final int KEY_GAP = 6;
	// table padding + row padding + scrollbar
	private static final int TABLE_CHROME = 10 + 4 + 4;
	// in %, middle gets the rest
	private static final int STATS_LEFT = 32;
	private static final int STATS_RIGHT = 27;
	private static final int BAR_PADDING = 4;
	private static final int BAR_GAP = 4;
	private static final int ARROW_WIDTH = 16;
	// least widths, measured past them
	private static final int BAR_NAME = 62;
	private static final int BAR_VALUE = 26;
	private static final int BAR_ROW_GAP = 4;
	// display choice: a narrower bar says nothing
	private static final int BAR_MIN = 24;
	private static final int INDENT = 6;
	private static final int DOT = 3;
	private static final int DOT_GAP = 2;
	private static final int DOTS_MARGIN = 3;
	private static final int ARROW_TEX = 3;
	private static final int ARROW_PAD = 3;
	private static final int ARROWS_WIDTH = 2 * (ARROW_TEX + 2 * ARROW_PAD);
	// display choice
	private static final int DAY_GAP = 8;
	// display choice
	private static final int LINE_GAP = 8;
	private static final int FOOTER_GAP = 16;
	// display choice: narrower and the session list folds to a strip
	private static final int SIDE_MIN_WIDTH = 560;
	private static final int STRIP_WIDTH = 12;
	private static final int SIDE_PADDING = 3;
	private static final Identifier OVERVIEW_ICON = Identifier.fromNamespaceAndPath("skysurveyor", "textures/gui/overview_icon.png");
	private static final Identifier SESSION_ICON = Identifier.fromNamespaceAndPath("skysurveyor", "textures/gui/session_icon.png");
	private static final Identifier STATS_ICON = Identifier.fromNamespaceAndPath("skysurveyor", "textures/gui/stats_icon.png");
	static final Identifier SETTINGS_ICON = Identifier.fromNamespaceAndPath("skysurveyor", "textures/gui/settings_icon.png");
	private static final Identifier MOVE_ICON = Identifier.fromNamespaceAndPath("skysurveyor", "textures/gui/move_icon.png");
	// label grey, the dark ones got lost on the panel
	private static final Identifier PREV_ARROW_LIGHT = Identifier.fromNamespaceAndPath("skysurveyor", "textures/gui/prev_arrow_light.png");
	private static final Identifier OPEN_ARROW_LIGHT = Identifier.fromNamespaceAndPath("skysurveyor", "textures/gui/open_arrow_light.png");
	private static final int RECENT_SESSIONS = 5;
	private static final ZoneId ZONE = ZoneId.systemDefault();
	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
	private static final DateTimeFormatter DAY_YEAR = DateTimeFormatter.ofPattern("MMM d yyyy", Locale.ENGLISH);

	private List<JournalEntry> entries;
	private List<MiningEntry> mining;
	private List<BreakEntry> breaks;
	private List<SessionSummary> sessions;
	// what the snapshot was taken at, checked every tick
	private int seenEntries;
	private JournalEntry seenFirstEntry;
	private int seenMining;
	private MiningEntry seenFirstMining;
	private int seenBreaks;
	private BreakEntry seenFirstBreak;
	private Long seenActive;
	private final ItemIcons icons = new ItemIcons();
	private final ShaftNames shaftNames = ShaftNames.defaults();
	private final PbPanel pbs = new PbPanel(this);
	private final JournalTable tables = new JournalTable(font, () -> rebuild(true));
	private Tab tab;
	// an older session opened from overview or stats, tab stays where it came from
	private boolean viewing;
	// session tab = only the one still going, started = going but no shaft yet
	private boolean current;
	private boolean started;
	private boolean byDay = byDaySetting();
	private int sessionIndex;
	private int selected;
	// next build scrolls the table to the selected shaft, not on kept-view rebuilds
	private boolean revealSelected = true;
	private KeptScroll tableScroll;
	private FlowLayout root;
	private int builtWidth;
	private int builtHeight;
	private FlowLayout detail;
	private int detailInner;
	private final List<FlowLayout> shaftRows = new ArrayList<>();
	private final List<KeptScroll> scrolls = new ArrayList<>();
	private final List<ShiftTip> shiftTips = new ArrayList<>();
	// kept while the game runs, not saved
	private static StatsRange statsRange = StatsRange.ALL_TIME;
	// arrows + pb edits rebuild the tab, a new snapshot drops these
	private final Map<StatsRange, StatsData> statsCache = new EnumMap<>(StatsRange.class);
	private final Map<StatsRange, MoneyData> moneyCache = new EnumMap<>(StatsRange.class);
	// display row -> entry index
	private int[] shaftOrder = new int[0];
	private int statsShafts;
	private boolean shiftShown;
	private int moneyChanges = Surveyor.MONEY.changes();
	private boolean stripOpen;
	private KeptScroll sideScroll;
	// list clicks keep the list where it was
	private Double keptSideOffset;

	public JournalScreen() {
		super(Component.literal("Shaft Journal"));
		snapshot();
		apply(JournalRefresh.opened(sessions, seenActive));
		tab = current ? Tab.SESSION : Tab.OVERVIEW;
	}

	// mining = outside shafts, breaks = gaps not counted per hour
	private void snapshot() {
		LiveJournal journal = Surveyor.JOURNAL;
		seenEntries = journal.entryCount();
		seenFirstEntry = journal.firstEntry();
		seenMining = journal.miningCount();
		seenFirstMining = journal.firstMining();
		seenBreaks = journal.breakCount();
		seenFirstBreak = journal.firstBreak();
		seenActive = activeId();
		entries = journal.entries();
		mining = journal.mining(0);
		breaks = journal.breaks();
		sessions = JournalStats.sessions(entries, mining, breaks);
	}

	private static Long activeId() {
		Journal.Session active = Surveyor.JOURNAL.activeSession(System.currentTimeMillis());
		return active == null ? null : active.id();
	}

	// ints + refs only, runs every tick
	private boolean journalChanged() {
		LiveJournal journal = Surveyor.JOURNAL;
		return journal.entryCount() != seenEntries || journal.firstEntry() != seenFirstEntry || journal.miningCount() != seenMining
				|| journal.firstMining() != seenFirstMining || journal.breakCount() != seenBreaks || journal.firstBreak() != seenFirstBreak
				|| !Objects.equals(activeId(), seenActive);
	}

	// not under the players hands, caught up once it ends
	private boolean busy() {
		if (pbs.busy()) return true;
		FocusHandler focus = root == null ? null : root.focusHandler();
		UIComponent focused = focus == null ? null : focus.focused();
		return focused instanceof TextBoxComponent && focused.hasParent();
	}

	// new snapshot, same tab + session + shaft, nothing indexes the old lists
	private void takeChanges() {
		List<SessionSummary> before = sessions;
		JournalRefresh.View view = new JournalRefresh.View(sessionIndex, selected, viewing, current, started);
		boolean sessionTab = !viewing && tab == Tab.SESSION;
		JournalEntry shown = selectedEntry();
		snapshot();
		apply(JournalRefresh.after(before, sessions, view, seenActive, sessionTab));
		// f.e. following the newest shaft
		if (!Objects.equals(selectedEntry(), shown)) revealSelected = true;
		pbs.forget();
		statsCache.clear();
		moneyCache.clear();
		moneyChanges = Surveyor.MONEY.changes();
	}

	private void apply(JournalRefresh.View view) {
		sessionIndex = view.sessionIndex();
		selected = view.selected();
		viewing = view.viewing();
		current = view.current();
		started = view.started();
	}

	@Override
	protected OwoUIAdapter<FlowLayout> createAdapter() {
		return OwoUIAdapter.create(this, UIContainers::verticalFlow);
	}

	// prices + settings load on the io thread after the journal opens, the journal file too
	@Override
	public void tick() {
		super.tick();
		// caxton cancels onValueChange, poll
		pbs.updatePreview();
		if (byDayChanged()) {
			rebuild();
			return;
		}
		if (journalChanged() && !busy()) {
			takeChanges();
			rebuild(true);
			return;
		}
		int changes = Surveyor.MONEY.changes();
		if (changes == moneyChanges) return;
		moneyChanges = changes;
		rebuild(true);
	}

	// esc goes back one step before it closes the journal, also while typing
	@Override
	public boolean keyPressed(KeyEvent key) {
		if (key.isEscape() && stepBack()) return true;
		if (arrowKey(key)) return true;
		return super.keyPressed(key);
	}

	// left/right = the top bars < >, up/down = the shaft above or below. only our own screen
	private boolean arrowKey(KeyEvent key) {
		boolean sideways = key.isLeft() || key.isRight();
		if (!sideways && !key.isUp() && !key.isDown()) return false;
		// detail = a session view is built, not overview or stats
		if (detail == null || busy() || stripOpen && width < SIDE_MIN_WIDTH) return false;
		if (sideways) {
			if (!viewing) return false;
			int target = key.isLeft() ? sessionIndex + 1 : sessionIndex - 1;
			if (target >= 0 && target < sessions.size()) showSession(target);
			return true;
		}
		int position = -1;
		for (int i = 0; i < shaftOrder.length; i++) {
			if (shaftOrder[i] == selected) position = i;
		}
		int next = position < 0 ? (key.isDown() ? 0 : shaftOrder.length - 1) : position + (key.isDown() ? 1 : -1);
		if (next < 0 || next >= shaftOrder.length || next == position) return true;
		select(shaftOrder[next]);
		if (tableScroll != null) tableScroll.reveal(next * ROW_HEIGHT, (next + 1) * ROW_HEIGHT, ROW_HEIGHT);
		return true;
	}

	private boolean stepBack() {
		// pb dropdown, builder, edit mode first
		if (!viewing && tab == Tab.STATS && pbs.stepBack()) {
			rebuild(true);
			return true;
		}
		if (stripOpen && width < SIDE_MIN_WIDTH && (viewing || tab == Tab.SESSION && current)) {
			stripOpen = false;
		} else if (viewing) {
			back();
			return true;
		} else {
			return false;
		}
		rebuild(true);
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
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
		// back from settings or edit hud, before the 1st frame
		if (root == null) return;
		boolean journal = journalChanged() && !busy();
		if (journal) takeChanges();
		if (byDayChanged()) rebuild();
		// resize keeps the scrolls the new layout still has
		else if (journal || width != builtWidth || height != builtHeight) rebuild(true);
	}

	// by day lives on the settings page now
	private boolean byDayChanged() {
		if (byDaySetting() == byDay) return false;
		byDay = byDaySetting();
		moneyChanges = Surveyor.MONEY.changes();
		return true;
	}

	private static boolean byDaySetting() {
		return journalSettings().byDay();
	}

	private static JournalSettings journalSettings() {
		return Surveyor.SETTINGS.get().journal();
	}

	private static void changeJournal(UnaryOperator<JournalSettings> change) {
		Surveyor.SETTINGS.change(s -> s.withJournal(change.apply(s.journal())));
	}

	private void rebuild() {
		rebuild(false);
	}

	// keepScroll = same view w/ new prices or size, scrolls stay put
	void rebuild(boolean keepScroll) {
		List<KeptScroll> before = keepScroll ? List.copyOf(scrolls) : List.of();
		List<Double> offsets = before.stream().map(KeptScroll::offset).toList();
		scrolls.clear();
		buildScreen();
		if (before.isEmpty()) return;
		List<String> tags = scrolls.stream().map(KeptScroll::tag).toList();
		if (before.stream().map(KeptScroll::tag).toList().equals(tags)) {
			for (int i = 0; i < offsets.size(); i++) scrolls.get(i).offset(offsets.get(i));
			return;
		}
		// side list came or went (resize past SIDE_MIN_WIDTH): the tagged ones by tag
		for (int i = 0; i < before.size(); i++) {
			String tag = before.get(i).tag();
			if (tag == null || tags.indexOf(tag) != tags.lastIndexOf(tag)) continue;
			int now = tags.indexOf(tag);
			if (now >= 0) scrolls.get(now).offset(offsets.get(i));
		}
	}

	KeptScroll scroll(Sizing horizontalSizing, Sizing verticalSizing, FlowLayout child) {
		KeptScroll scroll = new KeptScroll(horizontalSizing, verticalSizing, child);
		scrolls.add(scroll);
		return scroll;
	}

	private void buildScreen() {
		builtWidth = width;
		builtHeight = height;
		sideScroll = null;
		pbs.beforeBuild();
		root.clearChildren();
		shaftRows.clear();
		shiftTips.clear();
		detail = null;
		tableScroll = null;
		root.child(topBar());
		FlowLayout content = UIContainers.verticalFlow(Sizing.fill(100), Sizing.expand());
		content.gap(GAP);
		if (entries.isEmpty() && !viewing && tab == Tab.SESSION && started) {
			noSession(content);
		} else if (entries.isEmpty()) {
			content.child(panelWith(label("§7No shafts yet. Each shaft shows up here after you leave it."),
					Sizing.fill(100), Sizing.content()));
		} else {
			if (viewing) buildSessionTab(content);
			else switch (tab) {
				case OVERVIEW -> buildOverviewTab(content);
				case SESSION -> {
					if (current) buildSessionTab(content);
					else noSession(content);
				}
				case STATS -> buildStatsTab(content);
			}
		}
		root.child(content);
		FlowLayout footer = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		footer.gap(FOOTER_GAP);
		footerLines().forEach(line -> footer.child(label(line)));
		root.child(footer);
		if (!viewing && tab == Tab.STATS) pbs.buildBuilder(root);
		revealSelected = false;
	}

	// nothing to explain w/o shafts
	private List<String> footerLines() {
		if (entries.isEmpty()) return List.of();
		List<String> lines = new ArrayList<>();
		boolean table = viewing || tab == Tab.SESSION && current || tab == Tab.OVERVIEW;
		if (viewing) {
			lines.add("§8Click a shaft for details");
			lines.add("§8< > other sessions");
		} else switch (tab) {
			case SESSION -> {
				if (current) lines.add("§8Click a shaft for details");
			}
			case OVERVIEW -> lines.add("§8Click a session to open it");
			case STATS -> {
				if (statsShafts == 0) return List.of();
				lines.add("§8Counted from your own " + statsShafts + " shafts");
				lines.add("§8Hover a row for details");
				lines.add(pbs.editing() ? "§8Drag the grips to reorder" : "§8Click a PB to open it");
			}
		}
		// one line only, left out when it doesnt fit
		String hint = "§8Hover a column for what it counts";
		int used = lines.stream().mapToInt(line -> font.width(line) + FOOTER_GAP).sum() + font.width(hint);
		if (table && used <= width - 2 * PADDING) lines.add(hint);
		return lines;
	}

	private FlowLayout topBar() {
		FlowLayout bar = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		bar.surface(PANEL);
		bar.padding(Insets.of(BAR_PADDING));
		bar.gap(BAR_GAP);
		bar.verticalAlignment(VerticalAlignment.CENTER);
		// fixed widths so navRoom() can add them up
		LabelComponent title = label("§fShaft §bJournal");
		title.horizontalSizing(Sizing.fixed(font.width("Shaft Journal")));
		title.margins(Insets.of(0, 0, 2, 8));
		List<ButtonComponent> tabs = List.of(tabButton("Overview", Tab.OVERVIEW), tabButton("Session", Tab.SESSION),
				tabButton("Stats", Tab.STATS));
		IconButton editHud = new IconButton(Component.literal("Edit HUD"), MOVE_ICON, 8, 8,
				b -> minecraft.gui.setScreen(new EditHudScreen(this, Surveyor.HUD)));
		IconButton settings = new IconButton(Component.literal("§fSettings"), SETTINGS_ICON, 8, 8,
				b -> minecraft.gui.setScreen(new SettingsScreen(this)));
		settings.margins(Insets.left(6));
		List<UIComponent> items = new ArrayList<>(tabs);
		items.add(settings);
		items.add(editHud);
		boolean showTitle = true;
		if (viewing) {
			int date = font.width(day(sessions.get(sessionIndex).start()));
			// 480 wide: title steps aside so the date still fits
			showTitle = navRoom(withTitle(items, title)) >= date;
			if (showTitle) items = withTitle(items, title);
		} else {
			items = withTitle(items, title);
		}
		if (showTitle) bar.child(title);
		tabs.forEach(bar::child);

		FlowLayout nav = UIContainers.horizontalFlow(Sizing.expand(), Sizing.content());
		nav.horizontalAlignment(HorizontalAlignment.RIGHT);
		nav.verticalAlignment(VerticalAlignment.CENTER);
		nav.gap(BAR_GAP);
		if (viewing) {
			SessionSummary shown = sessions.get(sessionIndex);
			nav.child(button("<", sessionIndex < sessions.size() - 1, () -> showSession(sessionIndex + 1)));
			nav.child(label("§f" + sessionRange(shown, navRoom(items))));
			nav.child(button(">", sessionIndex > 0, () -> showSession(sessionIndex - 1)));
		} else if (tab == Tab.SESSION && current) {
			nav.child(label("§f" + sessionRange(sessions.getFirst(), navRoom(items))));
		}
		bar.child(nav);
		bar.child(settings);
		bar.child(editHud);
		return bar;
	}

	private static List<UIComponent> withTitle(List<UIComponent> items, UIComponent title) {
		List<UIComponent> all = new ArrayList<>(items);
		all.addFirst(title);
		return all;
	}

	// back to the list it came from, session tab goes to overview
	private void back() {
		viewing = false;
		stripOpen = false;
		if (tab == Tab.SESSION) tab = Tab.OVERVIEW;
		rebuild();
	}

	private String backText() {
		return tab == Tab.STATS ? "< Stats" : "< All sessions";
	}

	private ButtonComponent tabButton(String name, Tab target) {
		Component text = Component.literal((tab == target ? "§e" : "§f") + name);
		Identifier icon = switch (target) {
			case OVERVIEW -> OVERVIEW_ICON;
			case SESSION -> SESSION_ICON;
			case STATS -> STATS_ICON;
		};
		return new IconButton(text, icon, 9, 8, b -> {
			viewing = false;
			stripOpen = false;
			pbs.closeBuilder();
			tab = target;
			if (target == Tab.SESSION) {
				sessionIndex = 0;
				selectLastShaft();
			}
			rebuild();
		});
	}

	private void noSession(FlowLayout content) {
		FlowLayout panel = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		panel.gap(4);
		if (started) {
			panel.child(wrapped("§7Your session has started."));
			panel.child(wrapped("§8Each shaft shows up here after you leave it."));
			Journal.Session active = Surveyor.JOURNAL.activeSession(System.currentTimeMillis());
			List<MiningEntry> outside = active == null ? List.of() : mining.stream().filter(m -> m.sessionId() == active.id()).toList();
			Double coins = MoneyStats.outside(outside, Surveyor.MONEY::value);
			if (coins != null && Math.round(coins) != 0) panel.child(wrapped("§7Mined outside shafts so far " + CoinBreakdown.coins(coins)));
		} else {
			panel.child(wrapped("§7No session going right now."));
			panel.child(wrapped("§8A session starts when you mine in the Dwarven Mines and ends after "
					+ Journal.SESSION_GAP_MILLIS / 60_000 + " minutes without mining or shafting. Older sessions are in the Overview."));
		}
		content.child(panelWith(panel, Sizing.fill(100), Sizing.content()));
	}

	private ButtonComponent button(String text, boolean active, Runnable action) {
		ButtonComponent button = PackButton.button(Component.literal(text), b -> action.run());
		button.active(active);
		button.horizontalSizing(Sizing.fixed(ARROW_WIDTH));
		return button;
	}

	private void showSession(int index) {
		sessionIndex = index;
		selectLastShaft();
		viewing = true;
		stripOpen = false;
		rebuild();
	}

	void openShaft(JournalEntry entry) {
		for (int i = 0; i < sessions.size(); i++) {
			int index = sessions.get(i).entries().indexOf(entry);
			if (index >= 0) {
				sessionIndex = i;
				selected = index;
				viewing = true;
				revealSelected = true;
				rebuild();
				return;
			}
		}
	}

	void openSession(SessionSummary session) {
		int index = sessions.indexOf(session);
		if (index >= 0) showSession(index);
	}

	private void selectLastShaft() {
		selected = sessions.isEmpty() ? -1 : sessions.get(sessionIndex).shafts() - 1;
		revealSelected = true;
	}

	// selected row + a row of context in view, after the table is built
	private void revealSelected() {
		if (tableScroll == null) return;
		for (int position = 0; position < shaftOrder.length; position++) {
			if (shaftOrder[position] != selected) continue;
			tableScroll.reveal(position * ROW_HEIGHT, (position + 1) * ROW_HEIGHT, ROW_HEIGHT);
			return;
		}
	}

	private JournalEntry selectedEntry() {
		if (sessionIndex < 0 || sessionIndex >= sessions.size()) return null;
		List<JournalEntry> shafts = sessions.get(sessionIndex).entries();
		return selected >= 0 && selected < shafts.size() ? shafts.get(selected) : null;
	}

	private void buildSessionTab(FlowLayout content) {
		SessionSummary session = sessions.get(sessionIndex);
		Double rate = session.shaftsPerHour();
		MoneyStats sessionMoney = MoneyStats.of(session.entries(), Surveyor.MONEY::value, session.mining(), Surveyor.MONEY::lines,
				Surveyor.MONEY.settings().isIronman());
		Double sessionCoins = sessionMoney.total();
		FlowLayout tiles = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		tiles.gap(GAP);
		int tileWidth = (width - 2 * PADDING - 5 * GAP) / 6;
		tiles.child(tile(tileWidth, "Shafts", "§f" + session.shafts()));
		tiles.child(tile(tileWidth, "Avg spawn", spawn(session.averageSearchMillis())));
		FlowLayout rateTile = tile(tileWidth, "Shafts / h", shaftRate(rate));
		breakTooltip(rateTile, session.breakMillis());
		tiles.child(rateTile);
		tiles.child(tile(tileWidth, "Powder", "§b" + Format.number(session.powder())));
		tiles.child(tile(tileWidth, "Lapis looted", "§9" + session.looted(CorpseType.LAPIS.label())));
		FlowLayout coinsTile = tile(tileWidth, "Coins", coinText(sessionCoins));
		if (sessionCoins != null) {
			Double perHour = session.perHour(sessionCoins, session.end());
			double tax = tax(session.entries(), session.mining(), sessionMoney);
			MoneySettings settings = Surveyor.MONEY.settings();
			long breakMillis = session.breakMillis();
			shiftTooltip(coinsTile, CoinBreakdown.session(sessionMoney, perHour, breakMillis, tax, settings, tooltipHeight()),
					CoinBreakdown.sessionShort(sessionMoney, perHour, breakMillis, tax, settings));
		}
		tiles.child(coinsTile);
		content.child(tiles);

		FlowLayout main = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fill(100));
		main.gap(GAP);
		boolean side = width >= SIDE_MIN_WIDTH;
		List<String> sideCoins = side || stripOpen ? sessions.stream().map(s -> coinText(sessionCoins(s))).toList() : null;
		int sideWidth = side ? sideListWidth(sideCoins) : STRIP_WIDTH;
		main.child(side ? sideList(sideWidth, sideCoins) : strip());

		long slowest = session.entries().stream().filter(e -> e.searchMillis() != null)
				.mapToLong(JournalEntry::searchMillis).max().orElse(1);
		int detailWidth = Math.clamp(width * (side ? 25 : 30) / 100, 140, 230);
		List<Double> shaftCoins = session.entries().stream().map(e -> MoneyStats.net(Surveyor.MONEY.value(e))).toList();
		List<Column> measured = tables.measured(TableColumns.SHAFTS, "inside",
				session.entries().stream().map(e -> Format.duration(e.durationMillis())).toList());
		measured = tables.measured(measured, "number", IntStream.rangeClosed(1, session.entries().size()).mapToObj(n -> "§8" + n).toList());
		measured = tables.measured(measured, "shaft", session.entries().stream().map(JournalScreen::shaftCell).toList());
		measured = tables.measured(measured, "spawn", tables.barCellWidth(session.entries().stream().map(e -> spawnCell(e.searchMillis())).toList()));
		measured = tables.measured(measured, "powder", session.entries().stream().map(e -> "§f" + Format.number(JournalStats.powder(e))).toList());
		List<String> minedTexts = new ArrayList<>(session.entries().stream().map(JournalScreen::minedText).toList());
		if (!session.mining().isEmpty()) minedTexts.add(outsideMined(session));
		measured = tables.measured(measured, "mined", minedTexts);
		measured = tables.measured(measured, "time", session.entries().stream().map(e -> "§7" + clock(e.enteredAt())).toList());
		measured = tables.measured(measured, "corpses", session.entries().stream().map(JournalScreen::corpses).toList());
		List<String> coinTexts = new ArrayList<>(shaftCoins.stream().map(JournalScreen::coinText).toList());
		if (!session.mining().isEmpty()) coinTexts.add(coinText(sessionMoney.outside()));
		measured = tables.measured(measured, "coins", coinTexts);
		// most sessions have no mined shaft, an all - column only takes room
		if (session.entries().stream().allMatch(e -> Efficiency.shown(e.mining()) == null)) {
			measured = measured.stream().filter(c -> !c.key().equals("eff")).toList();
		} else {
			measured = tables.measured(measured, "eff", session.entries().stream().map(TableColumns::efficiency).toList());
		}
		if (session.entries().stream().allMatch(e -> TableColumns.campfires(e) == 0)) {
			measured = measured.stream().filter(c -> !c.key().equals("campfire")).toList();
		} else {
			measured = tables.measured(measured, "campfire", session.entries().stream().map(JournalScreen::campfireCell).toList());
		}
		List<Column> columns = JournalTable.fit(measured, width - 2 * PADDING - 2 * GAP - sideWidth - detailWidth - TABLE_CHROME);
		FlowLayout rows = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		shaftOrder = JournalTable.shaftOrder(session.entries(), shaftCoins, columns);
		for (int position = 0; position < shaftOrder.length; position++) {
			int i = shaftOrder[position];
			JournalEntry entry = session.entries().get(i);
			FlowLayout row = row(position, i);
			for (Column column : columns) {
				row.child(switch (column.key()) {
					case "number" -> cell(column, "§8" + (i + 1));
					case "shaft" -> cell(column, shaftCell(entry));
					case "time" -> cell(column, "§7" + clock(entry.enteredAt()));
					case "spawn" -> barCell(column, entry.searchMillis(), slowest);
					case "inside" -> cell(column, "§f" + Format.duration(entry.durationMillis()));
					case "corpses" -> cell(column, corpses(entry));
					case "powder" -> cell(column, "§f" + Format.number(JournalStats.powder(entry)));
					case "coins" -> cell(column, coinText(shaftCoins.get(i)));
					case "campfire" -> cell(column, campfireCell(entry));
					case "eff" -> cell(column, TableColumns.efficiency(entry));
					default -> minedCell(column, entry);
				});
			}
			int index = i;
			row.mouseDown().subscribe((event, doubleClick) -> {
				select(index);
				return true;
			});
			shaftRows.add(row);
			rows.child(row);
		}
		if (!session.mining().isEmpty()) outsideRow(rows, columns, session, sessionMoney);
		long stashed = Surveyor.JOURNAL.stashed(session.id());
		if (stashed > 0) {
			LabelComponent note = wrapped("§7" + Coins.whole(stashed) + " items went to the stash (a sack is full). Not counted.");
			note.margins(Insets.of(4, 0, 2, 0));
			rows.child(note);
		}
		main.child(table(columns, rows, true));
		if (revealSelected) revealSelected();

		detail = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		detail.gap(2);
		detailInner = detailWidth - 2 * COLUMN_PADDING;
		KeptScroll detailScroll = scroll(Sizing.fixed(detailWidth), Sizing.fill(100), detail);
		detailScroll.tag("detail");
		detailScroll.surface(PANEL);
		detailScroll.paddedWithBar(COLUMN_PADDING);
		main.child(detailScroll);
		// stack, so the open list takes no room from the table
		StackLayout area = UIContainers.stack(Sizing.fill(100), Sizing.expand());
		area.child(main);
		if (!side && stripOpen) {
			// click beside the open list closes it
			BoxComponent dim = UIComponents.box(Sizing.fill(100), Sizing.fill(100));
			dim.color(Color.ofArgb(0x66000000));
			dim.fill(true);
			dim.mouseDown().subscribe((event, doubleClick) -> {
				stripOpen = false;
				rebuild(true);
				return true;
			});
			area.child(dim);
			FlowLayout list = sideList(sideListWidth(sideCoins), sideCoins);
			list.surface(OVERLAY);
			area.child(list);
		}
		content.child(area);
		fillDetail();
	}

	// under the shafts, not sorted or clickable
	private void outsideRow(FlowLayout rows, List<Column> columns, SessionSummary session, MoneyStats money) {
		rows.child(divider());
		FlowLayout row = tableRow();
		int first = 0;
		while (first < columns.size() && !columns.get(first).key().equals("mined") && !columns.get(first).key().equals("coins")) first++;
		int span = -JournalTable.COLUMN_GAP;
		for (Column column : columns.subList(0, first)) span += column.total() + JournalTable.COLUMN_GAP;
		if (first > 0) {
			LabelComponent name = label("§fOutside shafts");
			name.horizontalSizing(Sizing.fixed(span));
			row.child(name);
		}
		LabelComponent gems = null;
		for (Column column : columns.subList(first, columns.size())) {
			LabelComponent cell = cell(column, switch (column.key()) {
				case "coins" -> coinText(money.outside());
				case "mined" -> "§8-";
				default -> "";
			});
			if (column.key().equals("mined")) gems = cell;
			row.child(cell);
		}
		withTips(row, TooltipLines.of(CoinBreakdown.outside(money, tooltipHeight())));
		Map<String, Double> fine = OutsideMining.fineGems(session.mining(), TextPatterns.defaults());
		if (gems != null && !fine.isEmpty()) {
			gems.text(Component.literal(outsideMined(session)));
			List<Component> tooltip = new ArrayList<>();
			tooltip.add(Component.literal("§fGems mined outside shafts"));
			fine.forEach((gem, count) -> tooltip.add(Component.literal(ItemNames.current().styled(GemYield.fineName(gem), Format.decimal(count)))));
			gems.tooltip(tooltip);
		}
		rows.child(row);
	}

	private int sideListWidth(List<String> coinTexts) {
		int shafts = sideShaftsWidth();
		int coins = coinTexts.stream().mapToInt(font::width).max().orElse(0);
		int days = sessions.stream().mapToInt(s -> font.width(day(s.start()))).max().orElse(0);
		int row = Math.max(days, INDENT + font.width("00:00") + 6 + shafts + 6 + coins) + 4;
		int back = font.width(backText()) + 12;
		// padding + scrollbar
		return Math.max(row + 3, back) + 2 * SIDE_PADDING;
	}

	// overview in short beside an open session
	private int sideShaftsWidth() {
		return sessions.stream().mapToInt(s -> font.width(String.valueOf(s.shafts()))).max().orElse(0);
	}

	private FlowLayout sideList(int listWidth, List<String> coinTexts) {
		FlowLayout panel = UIContainers.verticalFlow(Sizing.fixed(listWidth), Sizing.fill(100));
		panel.surface(PANEL);
		panel.padding(Insets.of(SIDE_PADDING));
		panel.gap(3);
		ButtonComponent back = PackButton.button(Component.literal(backText()), b -> back());
		back.horizontalSizing(Sizing.fill(100));
		panel.child(back);

		boolean inSessionTab = !viewing && tab == Tab.SESSION;
		int shaftsWidth = sideShaftsWidth();
		FlowLayout rows = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		int selectedY = 0;
		LocalDate lastDay = null;
		for (int i = 0; i < sessions.size(); i++) {
			SessionSummary session = sessions.get(i);
			if (!date(session.start()).equals(lastDay)) {
				lastDay = date(session.start());
				LabelComponent dayLabel = label("§7" + day(session.start()));
				dayLabel.margins(Insets.of(rows.children().isEmpty() ? 0 : 3, 1, 2, 0));
				rows.child(dayLabel);
			}
			if (i == sessionIndex) selectedY = rows.children().size();
			FlowLayout row = new HoverFlow(Sizing.fill(100), Sizing.fixed(ROW_HEIGHT), HoverFlow.ROW);
			row.padding(Insets.of(0, 0, 2 + INDENT, 2));
			row.gap(6);
			row.verticalAlignment(VerticalAlignment.CENTER);
			if (i == sessionIndex) row.surface(ROW_SELECTED);
			LabelComponent start = label("§f" + clock(session.start()));
			start.horizontalSizing(Sizing.expand());
			row.child(start);
			LabelComponent shafts = label("§f" + session.shafts());
			shafts.horizontalSizing(Sizing.fixed(shaftsWidth));
			shafts.horizontalTextAlignment(HorizontalAlignment.RIGHT);
			row.child(shafts);
			row.child(label(coinTexts.get(i)));
			int index = i;
			row.mouseDown().subscribe((event, doubleClick) -> {
				if (inSessionTab && index == 0) return true;
				keptSideOffset = sideScroll == null ? null : sideScroll.offset();
				if (inSessionTab) tab = Tab.OVERVIEW;
				showSession(index);
				return true;
			});
			rows.child(row);
		}
		KeptScroll scroll = scroll(Sizing.fill(100), Sizing.expand(), rows);
		scroll.tag("side");
		// a few rows of context above the open one, layout clamps it
		double offset = keptSideOffset != null ? keptSideOffset : Math.max(0, (selectedY - 3) * ROW_HEIGHT);
		keptSideOffset = null;
		scroll.offset(offset);
		sideScroll = scroll;
		panel.child(scroll);
		return panel;
	}

	// session list folded up, < goes back, the rest opens it
	private FlowLayout strip() {
		FlowLayout strip = UIContainers.verticalFlow(Sizing.fixed(STRIP_WIDTH), Sizing.fill(100));
		strip.surface(PANEL);
		FlowLayout backCell = new HoverFlow(Sizing.fill(100), Sizing.fixed(ROW_HEIGHT), HoverFlow.SMALL);
		backCell.horizontalAlignment(HorizontalAlignment.CENTER);
		backCell.verticalAlignment(VerticalAlignment.CENTER);
		backCell.child(label("§f<"));
		applyTooltip(backCell, List.of(Component.literal("§7Back to " + (tab == Tab.STATS ? "Stats" : "all sessions"))));
		backCell.mouseDown().subscribe((event, doubleClick) -> {
			back();
			return true;
		});
		strip.child(backCell);
		strip.child(UIComponents.box(Sizing.fill(100), Sizing.fixed(1)).color(Color.ofArgb(0x30FFFFFF)).fill(true));
		FlowLayout open = new HoverFlow(Sizing.fill(100), Sizing.expand(), HoverFlow.ROW, FlowLayout.Algorithm.VERTICAL);
		open.horizontalAlignment(HorizontalAlignment.CENTER);
		open.padding(Insets.top(4));
		open.child(label("§7>"));
		open.child(spacer(3));
		for (char c : "Sessions".toCharArray()) open.child(label("§8" + c));
		applyTooltip(open, List.of(Component.literal("§7Show sessions")));
		open.mouseDown().subscribe((event, doubleClick) -> {
			stripOpen = true;
			rebuild(true);
			return true;
		});
		strip.child(open);
		return strip;
	}

	private void select(int index) {
		selected = index;
		for (int i = 0; i < shaftRows.size(); i++) shaftRows.get(i).surface(rowSurface(i, shaftOrder[i], false));
		fillDetail();
	}

	private void fillDetail() {
		if (detail == null) return;
		detail.clearChildren();
		List<JournalEntry> shafts = sessions.get(sessionIndex).entries();
		if (selected < 0 || selected >= shafts.size()) return;
		JournalEntry entry = shafts.get(selected);
		ShaftValue value = Surveyor.MONEY.value(entry);

		String id = entry.shaftId() == null ? "Unknown shaft" : entry.shaftId();
		detail.child(wrapped("§b" + id + (ShaftNames.CRYSTAL.equals(entry.variant()) ? " §7(crystal)" : "")));
		detail.child(spacer(4));
		detail.child(keyValue("Entered", "§f" + time(entry.enteredAt())));
		detail.child(keyValue("Exit", "§f" + SummaryText.exitName(entry.exitReason())));
		detail.child(keyValue("Spawned in", entry.searchMillis() == null ? "§8not timed" : spawn(entry.searchMillis())));
		detail.child(keyValue("Inside", "§f" + Format.duration(entry.durationMillis())));
		if (!entry.mayhemBuffs().isEmpty()) detail.child(keyValue("Mayhem", "§e" + String.join(", ", entry.mayhemBuffs())));
		detail.child(keyValue("Powder", "§b" + Format.number(JournalStats.powder(entry))));
		if (entry.corpsePowder() > 0 && entry.exitPowder() > 0) {
			String corpses = "§8" + Format.number(entry.corpsePowder()) + " corpses";
			String exit = "+ " + Format.number(entry.exitPowder()) + " exit";
			// too wide: split at the +, not wherever the wrap lands
			if (font.width(corpses + " " + exit) <= detailInner) {
				detail.child(wrapped(corpses + " " + exit));
			} else {
				detail.child(wrapped(corpses));
				detail.child(wrapped("§8" + exit));
			}
		}
		if (TableColumns.campfires(entry) > 0) {
			String colds = colds(entry);
			detail.child(keyValue("Campfires", "§f" + TableColumns.campfires(entry) + (colds == null ? "" : " §7at " + colds)));
		}
		if (entry.coldAtExit() != null) detail.child(keyValue("Cold at exit", "§b" + entry.coldAtExit() + "❄"));

		detail.child(lootSection(entry.loots()));
		LootSummary loot = LootSummary.of(entry.loots(), TextPatterns.defaults());
		if (loot.gems() == null && loot.items().isEmpty()) {
			detail.child(wrapped("§8No corpses looted"));
		} else {
			FlowLayout grid = UIContainers.ltrTextFlow(Sizing.fill(100), Sizing.content());
			grid.gap(2);
			if (loot.gems() != null) {
				List<Component> tooltip = new ArrayList<>();
				tooltip.add(Component.literal("§fGemstones §7" + fine(loot.gems().fine()) + " Fine"));
				for (ItemCount part : loot.gems().parts()) tooltip.add(Component.literal(ItemNames.current().styled(part.item(), Format.number(part.count()))));
				// parts sorted, first = most common
				grid.child(slot(loot.gems().parts().getFirst().item(), compact(loot.gems().fine()), tooltip));
			}
			for (ItemCount item : loot.items()) {
				grid.child(slot(item.item(), Format.number(item.count()),
						List.of(Component.literal(ItemNames.current().styled(item.item(), Format.number(item.count()))))));
			}
			detail.child(grid);
		}

		if (entry.gems() != null) {
			detail.child(section("Gems mined"));
			detail.child(wrapped(ItemNames.current().styled(GemYield.fineName(entry.gems().gem()), Format.decimal(entry.gems().fineEquivalent()))));
			if (entry.pristineProcs() > 0) detail.child(wrapped("§8" + entry.pristineProcs() + " pristine procs"));
		} else if (entry.metal() == null) {
			// metal shafts skip the empty gems section
			detail.child(section("Gems mined"));
			detail.child(wrapped("§8None"));
		}
		if (entry.metal() != null) {
			detail.child(section("Metal mined"));
			detail.child(wrapped(entry.metal().styledText(Surveyor.MONEY.settings().metalTierUsed().tier(), ItemNames.current())));
		}
		MiningText.Lines mining = MiningText.of(entry.mining());
		if (mining != null) {
			detail.child(section("Mining"));
			String aside = mining.withSetup() == null ? "" : " §8(" + mining.withSetup() + " with setup)";
			// narrow panel: the aside would wrap, so its own row
			boolean fits = font.width("Efficiency") + KEY_GAP + font.width("§f" + mining.value() + aside) <= detailInner;
			List<Component> tooltip = new ArrayList<>();
			for (String line : mining.tooltip()) tooltip.add(Component.literal(line));
			FlowLayout row = keyValue("Efficiency", "§f" + mining.value() + (fits ? aside : ""));
			applyTooltip(row, tooltip);
			detail.child(row);
			if (!fits && mining.withSetup() != null) {
				FlowLayout second = keyValue("With setup", "§8" + mining.withSetup());
				applyTooltip(second, tooltip);
				detail.child(second);
			}
		}
		coinsSection(entry, value);
	}

	private void coinsSection(JournalEntry entry, ShaftValue value) {
		boolean worth = value != null && !value.lines().isEmpty();
		FlowLayout heading = section("Coins" + (worth ? " " + CoinBreakdown.coins(value.net()) : ""));
		detail.child(heading);
		if (value == null) {
			detail.child(wrapped("§8No prices yet. They load when you enter a shaft or open the journal."));
			return;
		}
		if (value.lines().isEmpty()) {
			detail.child(wrapped("§8Nothing worth coins"));
			return;
		}
		MoneySettings settings = Surveyor.MONEY.settings();
		boolean ironman = settings.isIronman();
		for (Part part : Part.values()) {
			// biggest first, no price last
			List<Line> lines = value.lines().stream().filter(l -> l.part() == part)
					.sorted(Comparator.comparing((Line l) -> l.coins() == null).thenComparing(l -> l.coins() == null ? 0 : -Math.abs(l.coins())))
					.toList();
			if (lines.isEmpty()) continue;
			boolean used = lines.stream().allMatch(l -> PriceRules.free(settings, part, l.item()));
			boolean priced = lines.stream().anyMatch(l -> l.coins() != null);
			List<Component> tooltip = new ArrayList<>();
			tooltip.add(Component.literal("§f" + part.displayName()));
			for (Line line : lines) {
				tooltip.add(Component.literal(ItemNames.current().styled(line.item(), CoinBreakdown.count(line.part(), line.item(), line.count())) + "  "
						+ (PriceRules.free(settings, part, line.item()) ? "§8used" : line.coins() == null ? CoinBreakdown.noPrice(ironman) : CoinBreakdown.coins(line.coins()))));
			}
			String total = used ? "§7" + Math.round(lines.stream().mapToDouble(Line::count).sum()) + " used"
					: priced ? CoinBreakdown.coins(value.total(part)) : CoinBreakdown.noPrice(ironman);
			FlowLayout row = tipRow(part.displayName(), total, tooltip);
			// indented = breakdown of the net above
			row.margins(Insets.left(INDENT));
			detail.child(row);
		}
		List<Component> how = ironman
				? List.of(Component.literal("§fHow coins are counted"),
						Component.literal("§7Ironman: NPC sell prices"),
						Component.literal("§7Gems as Fine, metal as Tungsten or Umber"),
						Component.literal("§7Keys and campfires cost nothing"),
						Component.literal("§8Change it in Settings"))
				: List.of(Component.literal("§fHow coins are counted"),
						Component.literal("§7Gems as " + CoinBreakdown.gemTierName(settings)),
						Component.literal("§7Metal as " + SettingsList.label(settings.metalTier())),
						Component.literal("§7Sold by " + CoinBreakdown.sellName(settings)
								+ ", " + Setting.Number.format(settings.bazaarTax()) + "% Bazaar tax"),
						Component.literal("§7Keys and campfires " + switch (settings.ownMetal()) {
							case CRAFT -> "crafted from your metal";
							case DONT_COUNT -> "not counted";
							default -> "at insta-buy";
						}),
						Component.literal("§8Change it in Settings"));
		withTips(heading, ScreenParts.tips(how));
		entry.savedKeys().forEach((key, count) -> detail.child(wrapped("§7" + count + " " + key + (count == 1 ? "" : "s") + " saved by Resourceful")));
		int unpriced = value.unpriced().size();
		if (unpriced > 0) detail.child(wrapped(CoinBreakdown.unpriced(unpriced, ironman)));
		boolean saved = Surveyor.MONEY.usesSavedPrices(entry);
		long end = entry.endedAt();
		detail.child(wrapped("§8" + (saved ? "Prices from " + day(end) + " " + clock(end) : "Today's prices")));
	}

	// cold at use, count for shafts from before 0.22.0 or when some werent used
	private static String campfireCell(JournalEntry entry) {
		int count = TableColumns.campfires(entry);
		if (count == 0) return "§8-";
		String colds = colds(entry);
		if (colds == null) return "§f" + count;
		return count > entry.campfireColds().size() ? "§f" + count + " §7at " + colds : colds;
	}

	// null = no cold saved
	private static String colds(JournalEntry entry) {
		if (entry.campfireColds() == null) return null;
		List<String> colds = entry.campfireColds().stream().map(c -> c == null ? "§8?" : "§b" + c + "❄").toList();
		return colds.isEmpty() ? null : String.join("§7, ", colds);
	}

	private static FlowLayout tipRow(String key, String value, List<Component> tooltip) {
		return withTips(keyValue(key, value), ScreenParts.tips(tooltip));
	}

	// counts on the right, 1st letters when the full words dont fit
	private FlowLayout lootSection(List<CorpseLoot> loots) {
		String title = "Corpse loot";
		if (loots.isEmpty()) return section(title);
		Map<String, Integer> byType = new LinkedHashMap<>();
		for (CorpseLoot loot : loots) byType.merge(loot.type(), 1, Integer::sum);
		List<String> full = new ArrayList<>();
		List<String> letters = new ArrayList<>();
		byType.forEach((type, count) -> {
			full.add(count + " " + type);
			letters.add(count + " " + type.charAt(0));
		});
		String counts = String.join(", ", full);
		if (font.width(title) + SECTION_GAP + font.width(counts) > detailInner) counts = String.join(", ", letters);
		FlowLayout section = section(null);
		FlowLayout heading = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		heading.gap(SECTION_GAP);
		heading.child(label("§7" + title));
		LabelComponent right = label("§8" + counts);
		right.horizontalSizing(Sizing.expand());
		right.horizontalTextAlignment(HorizontalAlignment.RIGHT);
		heading.child(right);
		section.child(heading);
		return section;
	}

	private StackLayout slot(String name, String count, List<Component> tooltip) {
		StackLayout slot = UIContainers.stack(Sizing.fixed(18), Sizing.fixed(18));
		slot.surface(SLOT);
		slot.alignment(HorizontalAlignment.RIGHT, VerticalAlignment.BOTTOM);
		ItemComponent item = UIComponents.item(icons.stack(name));
		item.positioning(Positioning.absolute(1, 1));
		item.tooltip(tooltip);
		slot.child(item);
		LabelComponent label = label("§f" + count);
		label.shadow(true);
		label.tooltip(tooltip);
		slot.child(label);
		slot.tooltip(tooltip);
		return slot;
	}

	// millis = wall clock, rateMillis = w/o breaks
	private record Day(long start, List<Integer> sessions, long millis, long rateMillis, int shafts, long powder, Double coins) {
		Double perHour() {
			return coins == null ? null : JournalStats.perHour(coins, rateMillis);
		}
	}

	// sessions newest first, a session counts on the day it started
	private List<Day> days(List<Double> sessionCoins) {
		List<Day> days = new ArrayList<>();
		for (int i = 0; i < sessions.size(); i++) {
			SessionSummary session = sessions.get(i);
			Day last = days.isEmpty() ? null : days.getLast();
			if (last == null || !date(last.start()).equals(date(session.start()))) {
				days.add(new Day(session.start(), List.of(i), session.lengthMillis(), session.rateMillis(), session.shafts(), session.powder(),
						sessionCoins.get(i)));
				continue;
			}
			List<Integer> indexes = new ArrayList<>(last.sessions());
			indexes.add(i);
			days.set(days.size() - 1, new Day(last.start(), indexes, last.millis() + session.lengthMillis(),
					last.rateMillis() + session.rateMillis(), last.shafts() + session.shafts(),
					last.powder() + session.powder(), MoneyStats.plus(last.coins(), sessionCoins.get(i))));
		}
		return days;
	}

	private void buildOverviewTab(FlowLayout content) {
		long slowest = sessions.stream().map(SessionSummary::averageSearchMillis).filter(a -> a != null)
				.mapToLong(Long::longValue).max().orElse(1);
		List<Double> sessionCoins = sessions.stream().map(JournalScreen::sessionCoins).toList();
		List<Double> coinRates = new ArrayList<>();
		for (int i = 0; i < sessions.size(); i++) {
			Double coins = sessionCoins.get(i);
			coinRates.add(coins == null ? null : sessions.get(i).perHour(coins, sessions.get(i).end()));
		}
		List<Day> days = days(sessionCoins);
		List<String> coinTexts = new ArrayList<>(sessionCoins.stream().map(JournalScreen::coinText).toList());
		List<String> rateTexts = new ArrayList<>(coinRates.stream().map(JournalScreen::rateText).toList());
		// day rows only drawn by day
		if (byDay) {
			for (Day day : days) {
				coinTexts.add(coinText(day.coins()));
				rateTexts.add(dayRate(day));
			}
		}
		List<Column> measured = tables.measured(TableColumns.SESSIONS.stream().filter(c -> !byDay || !c.key().equals("start")).toList(),
				"coins", coinTexts);
		measured = tables.measured(measured, "coinRate", rateTexts);
		measured = tables.measured(measured, "rate", sessions.stream().map(s -> shaftRate(s.shaftsPerHour())).toList());
		// day rows count too, by day the sessions go indented under the date
		List<String> dates = new ArrayList<>(byDay ? days.stream().map(d -> "§f" + day(d.start())).toList()
				: sessions.stream().map(s -> "§f" + day(s.start())).toList());
		int indented = byDay ? INDENT + tables.widest(sessions.stream().map(s -> "§7" + clock(s.start())).toList()) : 0;
		measured = tables.measured(measured, "date", Math.max(tables.widest(dates), indented));
		measured = tables.measured(measured, "start", sessions.stream().map(s -> "§7" + clock(s.start())).toList());
		List<String> lengths = new ArrayList<>(sessions.stream().map(s -> "§f" + Format.duration(s.lengthMillis())).toList());
		List<String> shaftCounts = new ArrayList<>(sessions.stream().map(s -> "§f" + s.shafts()).toList());
		List<String> powders = new ArrayList<>(sessions.stream().map(s -> "§b" + Format.number(s.powder())).toList());
		List<String> sessionCounts = new ArrayList<>();
		if (byDay) {
			for (Day day : days) {
				lengths.add("§f" + Format.duration(day.millis()));
				shaftCounts.add("§f" + day.shafts());
				powders.add("§b" + Format.number(day.powder()));
				sessionCounts.add(daySessions(day));
			}
		}
		measured = tables.measured(measured, "length", lengths);
		measured = tables.measured(measured, "shafts", shaftCounts);
		measured = tables.measured(measured, "spawn",
				Math.max(tables.widest(sessionCounts), tables.barCellWidth(sessions.stream().map(s -> spawnCell(s.averageSearchMillis())).toList())));
		measured = tables.measured(measured, "powder", powders);
		measured = tables.measured(measured, "lapis", sessions.stream().map(s -> "§9" + s.looted(CorpseType.LAPIS.label())).toList());
		List<Column> columns = JournalTable.fit(measured, width - 2 * PADDING - TABLE_CHROME);
		FlowLayout rows = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		for (Day day : days) {
			if (byDay) {
				FlowLayout dayRow = dayRow(columns, day);
				if (!rows.children().isEmpty()) dayRow.margins(Insets.top(DAY_GAP));
				rows.child(dayRow);
			}
			for (int i : day.sessions()) {
				SessionSummary session = sessions.get(i);
				FlowLayout row = row(i, i);
				for (Column column : columns) {
					row.child(switch (column.key()) {
						case "date" -> byDay ? indented(column, "§7" + clock(session.start())) : cell(column, "§f" + day(session.start()));
						case "start" -> cell(column, "§7" + clock(session.start()));
						case "length" -> breakTooltip(cell(column, "§f" + Format.duration(session.lengthMillis())), session.breakMillis());
						case "shafts" -> cell(column, "§f" + session.shafts());
						case "spawn" -> barCell(column, session.averageSearchMillis(), slowest);
						case "rate" -> cell(column, shaftRate(session.shaftsPerHour()));
						case "powder" -> cell(column, "§b" + Format.number(session.powder()));
						case "coins" -> cell(column, coinText(sessionCoins.get(i)));
						case "coinRate" -> cell(column, rateText(coinRates.get(i)));
						default -> cell(column, "§9" + session.looted(CorpseType.LAPIS.label()));
					});
				}
				int index = i;
				row.mouseDown().subscribe((event, doubleClick) -> {
					showSession(index);
					return true;
				});
				rows.child(row);
			}
		}
		content.child(table(columns, rows, false));
	}

	private FlowLayout dayRow(List<Column> columns, Day day) {
		FlowLayout row = tableRow();
		row.surface(ROW_DAY);
		for (Column column : columns) {
			row.child(switch (column.key()) {
				case "date" -> cell(column, "§f" + day(day.start()));
				case "length" -> breakTooltip(cell(column, "§f" + Format.duration(day.millis())), day.millis() - day.rateMillis());
				case "shafts" -> cell(column, "§f" + day.shafts());
				case "spawn" -> cell(column, daySessions(day));
				case "powder" -> cell(column, "§b" + Format.number(day.powder()));
				case "coins" -> cell(column, coinText(day.coins()));
				case "coinRate" -> cell(column, dayRate(day));
				default -> cell(column, "");
			});
		}
		return row;
	}

	private static String daySessions(Day day) {
		int count = day.sessions().size();
		return "§7" + count + (count == 1 ? " session" : " sessions");
	}

	private static LabelComponent indented(Column column, String text) {
		LabelComponent label = label(text);
		label.horizontalSizing(Sizing.fixed(Math.max(0, column.total() - INDENT)));
		label.margins(Insets.left(INDENT));
		return label;
	}

	private static String dayRate(Day day) {
		Double perHour = day.perHour();
		return rateText(perHour) + (perHour == null ? "" : " §8(avg)");
	}

	// same range + snapshot = same numbers. last 7 days can lose a session while open
	private StatsData statsData() {
		List<SessionSummary> rangeSessions = statsRange.sessions(sessions, System.currentTimeMillis());
		StatsData cached = statsCache.get(statsRange);
		if (cached != null && sameItems(cached.sessions(), rangeSessions)) return cached;
		boolean all = statsRange == StatsRange.ALL_TIME;
		List<JournalEntry> rangeEntries = all ? entries : StatsRange.flat(rangeSessions, SessionSummary::entries);
		List<MiningEntry> rangeMining = StatsRange.flat(rangeSessions, SessionSummary::mining);
		StatsData data = rangeEntries.isEmpty() ? new StatsData(rangeSessions, rangeEntries, rangeMining, null, null, null, null)
				: new StatsData(rangeSessions, rangeEntries, rangeMining,
						JournalStats.allTime(rangeEntries, rangeMining, StatsRange.flat(rangeSessions, SessionSummary::breaks)),
						JournalStats.campfires(rangeEntries), JournalStats.recentAverage(sessions, RECENT_SESSIONS),
						JournalStats.averageSearch(entries));
		statsCache.put(statsRange, data);
		return data;
	}

	// prices or money settings moved = new changes count
	private MoneyData moneyData(StatsData data) {
		int changes = Surveyor.MONEY.changes();
		MoneyData cached = moneyCache.get(statsRange);
		if (cached != null && cached.of() == data && cached.changes() == changes) return cached;
		MoneyStats money = MoneyStats.of(data.entries(), Surveyor.MONEY::value, data.mining(), Surveyor.MONEY::lines,
				Surveyor.MONEY.settings().isIronman());
		List<Double> coins = data.stats().oldestFirst().stream().map(JournalScreen::sessionCoins).toList();
		MoneyData fresh = new MoneyData(data, changes, money, tax(data.entries(), data.mining(), money), coins);
		moneyCache.put(statsRange, fresh);
		return fresh;
	}

	private static <T> boolean sameItems(List<T> a, List<T> b) {
		if (a.size() != b.size()) return false;
		for (int i = 0; i < a.size(); i++) {
			if (a.get(i) != b.get(i)) return false;
		}
		return true;
	}

	private void buildStatsTab(FlowLayout content) {
		boolean all = statsRange == StatsRange.ALL_TIME;
		StatsData data = statsData();
		List<SessionSummary> rangeSessions = data.sessions();
		List<JournalEntry> rangeEntries = data.entries();
		statsShafts = rangeEntries.size();
		FlowLayout columns = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fill(100));
		columns.gap(GAP);
		FlowLayout totals = column();
		totals.child(rangeHeader());
		if (rangeEntries.isEmpty()) {
			totals.child(wrapped("§8No shafts in this range"));
			columns.child(scrollColumn(Sizing.fill(STATS_LEFT), totals));
			columns.child(scrollColumn(Sizing.expand(), column()));
			columns.child(scrollColumn(Sizing.fill(STATS_RIGHT), column()));
			content.child(columns);
			return;
		}
		AllTime stats = data.stats();
		MoneyData moneyData = moneyData(data);
		MoneyStats money = moneyData.money();

		totals.child(coinsTotal(money, stats, beforeOutside(rangeSessions), moneyData.tax()));
		totals.child(keyValue("Powder", "§b" + Format.number(stats.powder())));
		FlowLayout shafts = keyValue("Shafts", "§f" + stats.shafts());
		shafts.margins(Insets.top(LINE_GAP));
		totals.child(shafts);
		totals.child(keyValue("Sessions", "§f" + stats.sessions()));
		long breakMillis = rangeSessions.stream().mapToLong(SessionSummary::breakMillis).sum();
		totals.child(breakTooltip(keyValue("Time shafting", "§f" + Format.duration(stats.timeMillis())), breakMillis));
		JournalStats.Campfires campfires = data.campfires();
		if (campfires.placed() > 0) {
			Double cold = campfires.averageCold();
			totals.child(keyValue("Campfires", "§f" + campfires.placed()
					+ (cold == null ? "" : " §7avg at §b" + Math.round(cold) + "❄")));
		}
		EfficiencyStats.Summary efficiency = EfficiencyStats.of(rangeEntries);
		if (efficiency.all() != null) totals.child(efficiencyRow(efficiency.all()));
		totals.child(section("Corpses looted"));
		for (String type : stats.corpsesSeen().keySet()) {
			totals.child(corpseRow(type, stats.looted().getOrDefault(type, 0), money, stats.drops().get(type)));
		}
		pbs.addTo(totals, rangeEntries, rangeSessions);
		columns.child(scrollColumn(Sizing.fill(STATS_LEFT), totals));

		FlowLayout spawn = column();
		spawn.child(header("Spawn time"));
		if (all) {
			spawn.child(keyValue("All-time avg", stats.averageSearchMillis() == null ? "§8-"
					: "§e" + Format.duration(stats.averageSearchMillis())));
			Long recent = data.recentSpawn();
			if (recent != null && stats.averageSearchMillis() != null && sessions.size() > RECENT_SESSIONS) {
				spawn.child(keyValue("Last " + RECENT_SESSIONS + " sessions",
						"§f" + Format.duration(recent) + change(recent, stats.averageSearchMillis())));
			}
		} else {
			Long average = stats.averageSearchMillis();
			Long allTime = data.allTimeSpawn();
			spawn.child(keyValue("Avg", average == null ? "§8-"
					: "§e" + Format.duration(average) + (allTime == null ? "" : change(average, allTime))));
			spawn.child(keyValue("All-time avg", allTime == null ? "§8-" : "§f" + Format.duration(allTime)));
		}
		String legend = all ? "all-time avg" : "avg";
		spawn.child(chartSection("Avg spawn per session", journalSettings().spawnChart(),
				step -> changeJournal(j -> j.withSpawnChart(j.spawnChart().next(step)))));
		spawn.child(spawnChart(stats.oldestFirst(), stats.averageSearchMillis(), legend));
		spawn.child(section("Shafts with a corpse"));
		List<String> corpseTypes = List.copyOf(stats.corpsesSeen().keySet());
		List<String> corpseShares = corpseTypes.stream()
				.map(type -> "§f" + Math.round(100.0 * stats.shaftsWithCorpse().getOrDefault(type, 0) / stats.shafts()) + "%").toList();
		BarSizes corpseSizes = barSizes(corpseTypes, corpseShares, statsMiddleRoom());
		for (int i = 0; i < corpseTypes.size(); i++) {
			String type = corpseTypes.get(i);
			int with = stats.shaftsWithCorpse().getOrDefault(type, 0);
			FlowLayout row = barRow(CorpseType.color(type) + type, with / (double) stats.shafts(), corpseShares.get(i), CorpseType.argb(type),
					corpseSizes);
			row.tooltip(List.of(Component.literal("§f" + type + " corpses"),
					Component.literal("§7In " + with + " of " + stats.shafts() + " shafts"),
					Component.literal("§7" + String.format(Locale.ROOT, "%.2f", stats.corpsesSeen().get(type) / (double) stats.shafts())
							+ " per shaft on average")));
			spawn.child(row);
		}
		spawn.child(chartSection("Coins / h per session", journalSettings().coinChart(),
				step -> changeJournal(j -> j.withCoinChart(j.coinChart().next(step)))));
		Double averageRate = money.total() == null ? null : JournalStats.perHour(money.total(), stats.timeMillis());
		spawn.child(coinChart(stats.oldestFirst(), moneyData.sessionCoins(), averageRate, money.total() != null, legend, stats.timeMillis()));
		columns.child(scrollColumn(Sizing.expand(), spawn));

		FlowLayout odds = column();
		odds.child(header("Shaft types seen"));
		int typed = stats.typedShafts();
		// both right column groups, so their bars line up
		List<String> rightNames = new ArrayList<>();
		stats.types().keySet().forEach(code -> rightNames.add(shaftNames.name(code)));
		money.types().keySet().forEach(code -> rightNames.add(shaftNames.name(code)));
		List<String> typeShares = stats.types().values().stream()
				.map(variants -> "§f" + Math.round(100.0 * variants.values().stream().mapToInt(Integer::intValue).sum() / typed) + "%").toList();
		BarSizes typeSizes = barSizes(rightNames, typeShares, statsRightRoom());
		for (Map.Entry<String, Map<String, Integer>> type : stats.types().entrySet()) {
			String name = shaftNames.name(type.getKey());
			int total = type.getValue().values().stream().mapToInt(Integer::intValue).sum();
			FlowLayout row = barRow("§f" + name, total / (double) typed,
					"§f" + Math.round(100.0 * total / typed) + "%", 0xFF000000 | shaftNames.rgb(type.getKey()), typeSizes);
			List<Component> tooltip = new ArrayList<>();
			tooltip.add(Component.literal("§f" + name + " shafts §7" + total));
			type.getValue().forEach((variant, count) -> {
				if (!variant.equals(ShaftNames.LITTLEFOOT)) tooltip.add(Component.literal("§7" + capitalized(ShaftNames.variantName(variant)) + " §f" + count));
			});
			row.tooltip(tooltip);
			odds.child(row);
		}
		typeCoins(odds, money, typeSizes.name());
		typeEfficiency(odds, efficiency, typeSizes.name());
		columns.child(scrollColumn(Sizing.fill(STATS_RIGHT), odds));
		content.child(columns);
	}

	private FlowLayout efficiencyRow(EfficiencyStats.Group all) {
		FlowLayout row = keyValue("Efficiency", "§f" + Format.percent(all.median()) + " §8" + all.shafts() + (all.shafts() == 1 ? " shaft" : " shafts"));
		List<Component> tooltip = new ArrayList<>();
		tooltip.add(Component.literal("§fEfficiency"));
		tooltip.add(Component.literal("§7Blocks you broke against nonstop mining"));
		tooltip.add(Component.literal("§7Middle of your mined shafts in this range"));
		tooltip.add(Component.literal("§8From your first gem block, no Gemstone Spread"));
		applyTooltip(row, tooltip);
		return row;
	}

	// best first, bar = the efficiency itself
	private void typeEfficiency(FlowLayout column, EfficiencyStats.Summary efficiency, int nameWidth) {
		if (efficiency.types().isEmpty()) return;
		column.child(section("Efficiency per shaft type"));
		int valueWidth = efficiency.types().stream().mapToInt(t -> font.width(Format.percent(t.all().median()))).max().orElse(0);
		BarSizes sizes = new BarSizes(nameWidth, valueWidth, statsRightRoom());
		for (EfficiencyStats.Type type : efficiency.types()) {
			String name = shaftNames.name(type.type());
			EfficiencyStats.Group all = type.all();
			FlowLayout row = barRow(name, Math.min(1, all.median()), "§f" + Format.percent(all.median()),
					0xFF000000 | shaftNames.rgb(type.type()), sizes);
			List<Component> tooltip = new ArrayList<>();
			tooltip.add(Component.literal("§f" + name + " §7" + Format.percent(all.median()) + " over " + all.shafts()
					+ (all.shafts() == 1 ? " shaft" : " shafts")));
			if (all.ticks() != null) tooltip.add(Component.literal("§7" + MiningText.ticks(all.ticks()) + " ticks a block"));
			if (type.variants().size() > 1) {
				type.variants().forEach((variant, group) -> tooltip.add(Component.literal("§7" + capitalized(ShaftNames.variantName(variant))
						+ " §f" + Format.percent(group.median()) + " §8" + group.shafts())));
			}
			row.tooltip(tooltip);
			column.child(row);
		}
	}

	private FlowLayout coinsTotal(MoneyStats money, AllTime stats, boolean beforeOutside, double tax) {
		FlowLayout row = keyValue("Coins", coinText(money.total()));
		if (money.total() == null) return row;
		Double perHour = JournalStats.perHour(money.total(), stats.timeMillis());
		shiftTooltip(row, CoinBreakdown.detailed(money, perHour, stats.timeMillis(), beforeOutside, tax, tooltipHeight()),
				CoinBreakdown.basic(money, perHour, stats.timeMillis()));
		return row;
	}

	// same entries valued w/o bazaar tax
	private static double tax(List<JournalEntry> entries, List<MiningEntry> mining, MoneyStats money) {
		if (!Surveyor.MONEY.taxed()) return 0;
		MoneyStats untaxed = MoneyStats.of(entries, Surveyor.MONEY::untaxed, mining, Surveyor.MONEY::untaxedLines, money.ironman());
		return MoneyStats.tax(money, untaxed);
	}

	private FlowLayout corpseRow(String type, int looted, MoneyStats money, JournalStats.Drops drops) {
		MoneyStats.CorpseCoins corpse = money.corpses().get(type);
		String value = CorpseType.color(type) + looted;
		FlowLayout row = keyValue(type, corpse == null ? value : value + " §7(" + CoinBreakdown.coins(corpse.each()) + " §7avg)");
		if (corpse == null && drops == null) return row;
		return withTips(row, TooltipLines.of(CoinBreakdown.corpse(type, looted, corpse, drops, tooltipHeight(),
				Surveyor.MONEY.settings().isIronman())));
	}

	// best avg first, bar = share of the coins all types made
	private void typeCoins(FlowLayout column, MoneyStats money, int nameWidth) {
		List<Map.Entry<String, MoneyStats.TypeCoins>> types = money.types().entrySet().stream()
				.sorted(Comparator.comparingDouble((Map.Entry<String, MoneyStats.TypeCoins> e) -> -e.getValue().each())).toList();
		if (types.isEmpty()) return;
		column.child(section("Avg coins per shaft"));
		double made = types.stream().mapToDouble(e -> Math.max(0, e.getValue().coins())).sum();
		int valueWidth = types.stream().mapToInt(e -> font.width(CoinBreakdown.coins(e.getValue().each()))).max().orElse(0);
		BarSizes sizes = new BarSizes(nameWidth, valueWidth, statsRightRoom());
		for (Map.Entry<String, MoneyStats.TypeCoins> type : types) {
			String name = shaftNames.name(type.getKey());
			Double share = made > 0 ? Math.max(0, type.getValue().coins()) / made : null;
			FlowLayout row = barRow(name, share == null ? 0 : share, CoinBreakdown.coins(type.getValue().each()),
					0xFF000000 | shaftNames.rgb(type.getKey()), sizes);
			column.child(withTips(row, TooltipLines.of(CoinBreakdown.shaftType(name, type.getValue(), share,
					v -> capitalized(ShaftNames.variantName(v))))));
		}
	}

	private FlowLayout rangeHeader() {
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		row.verticalAlignment(VerticalAlignment.CENTER);
		row.surface(Surface.flat(0x14FFFFFF));
		row.padding(Insets.of(2, 2, 4, 1));
		row.margins(Insets.bottom(4));
		int count = StatsRange.values().length;
		int widest = 0;
		for (StatsRange range : StatsRange.values()) widest = Math.max(widest, font.width(range.displayName()));
		// row padding 4 + 1
		int room = statsLeftRoom() - 5;
		RangeFit.Fit fit = RangeFit.fit(room, font.width("Range "), widest, dotsWidth(count), ARROWS_WIDTH);
		LabelComponent name = label((fit == RangeFit.Fit.FULL ? "§7Range §e" : "§e") + statsRange.displayName());
		name.horizontalSizing(Sizing.expand());
		row.child(name);
		row.child(cycler(statsRange.ordinal(), count, fit != RangeFit.Fit.NO_DOTS, step -> statsRange = statsRange.next(step)));
		String title = fit == RangeFit.Fit.FULL ? "§fTime range" : "§fRange §e" + statsRange.displayName();
		List<Component> tooltip = List.of(Component.literal(title),
				Component.literal("§7Which sessions the whole Stats tab counts"),
				Component.literal("§8Click the arrows to cycle"));
		applyTooltip(row, tooltip);
		return row;
	}

	// dots show where u are, arrows wrap around
	private FlowLayout cycler(int index, int count, IntConsumer step) {
		return cycler(index, count, true, step);
	}

	private FlowLayout cycler(int index, int count, boolean withDots, IntConsumer step) {
		FlowLayout cycler = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
		cycler.verticalAlignment(VerticalAlignment.CENTER);
		if (withDots) {
			FlowLayout dots = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
			dots.gap(DOT_GAP);
			dots.margins(Insets.right(DOTS_MARGIN));
			for (int i = 0; i < count; i++) {
				BoxComponent dot = UIComponents.box(Sizing.fixed(DOT), Sizing.fixed(DOT));
				dot.fill(true);
				dot.color(Color.ofArgb(i == index ? 0xFFFFFF55 : 0xFF555555));
				dots.child(dot);
			}
			cycler.child(dots);
		}
		cycler.child(cyclerArrow(PREV_ARROW_LIGHT, () -> step.accept(-1)));
		cycler.child(cyclerArrow(OPEN_ARROW_LIGHT, () -> step.accept(1)));
		return cycler;
	}

	// padding = bigger click area
	private UIComponent cyclerArrow(Identifier texture, Runnable action) {
		FlowLayout arrow = new HoverFlow(Sizing.content(), Sizing.content(), HoverFlow.SMALL);
		arrow.padding(Insets.of(2, 2, ARROW_PAD, ARROW_PAD));
		arrow.child(UIComponents.texture(texture, 0, 0, ARROW_TEX, 5, ARROW_TEX, 5).blend(true));
		arrow.mouseDown().subscribe((event, doubleClick) -> {
			action.run();
			rebuild(true);
			return true;
		});
		return arrow;
	}

	private FlowLayout chartSection(String title, ChartType type, IntConsumer step) {
		FlowLayout section = section(title);
		LabelComponent label = (LabelComponent) section.children().getLast();
		section.removeChild(label);
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		row.verticalAlignment(VerticalAlignment.CENTER);
		label.horizontalSizing(Sizing.expand());
		row.child(label);
		FlowLayout cycler = cycler(type.ordinal(), ChartType.values().length, step);
		applyTooltip(cycler, List.of(Component.literal("§fChart §e" + type.displayName()),
				Component.literal("§7Switches between bars and a line"), Component.literal("§8Click the arrows to cycle")));
		row.child(cycler);
		section.child(row);
		return section;
	}

	// durations show whole secs, <1s = same
	private static String change(long now, long before) {
		long difference = now - before;
		return Math.abs(difference) < 1000 ? " §7(same)"
				: difference < 0 ? " §7(§a" + Format.duration(-difference) + " faster§7)"
				: " §7(§c" + Format.duration(difference) + " slower§7)";
	}

	// room for the tooltip's own border
	private int tooltipHeight() {
		return height - 10;
	}

	private void shiftTooltip(FlowLayout row, List<String> normal, List<String> shifted) {
		ShiftTip tip = new ShiftTip(row, TooltipLines.of(normal), TooltipLines.of(shifted));
		shiftTips.add(tip);
		withTips(row, shiftShown ? tip.shifted() : tip.normal());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		boolean shift = minecraft.hasShiftDown();
		if (shift != shiftShown) {
			shiftShown = shift;
			for (ShiftTip tip : shiftTips) withTips(tip.row(), shift ? tip.shifted() : tip.normal());
		}
		pbs.rehome();
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		pbs.drawDrag(graphics);
	}

	// shafts + outside
	private static Double sessionCoins(SessionSummary session) {
		return MoneyStats.session(session, Surveyor.MONEY::value, Surveyor.MONEY::value);
	}

	// sessions before outside coins were saved (0.24.0-alpha.11) have none. none saved = nothing to say
	private boolean beforeOutside(List<SessionSummary> range) {
		if (mining.isEmpty()) return false;
		long first = mining.getFirst().sessionId();
		return range.stream().anyMatch(s -> s.id() < first);
	}

	private static String coinText(Double coins) {
		return coinText(coins, null);
	}

	private static String shaftRate(Double rate) {
		return rate == null ? "§8-" : "§f" + Format.decimal(rate);
	}

	// gray so only the totals are gold
	private static String rateText(Double coins) {
		return coinText(coins, "§7");
	}

	// color null = gold, red when negative
	private static String coinText(Double coins, String color) {
		if (coins == null) return "§8-";
		if (Math.round(coins) == 0) return "§80";
		return color == null ? CoinBreakdown.coins(coins) : color + Coins.signed(coins);
	}

	// drag a grip in pb edit mode
	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		if (pbs.grab(click)) return true;
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent drag, double dx, double dy) {
		if (!pbs.dragging()) return super.mouseDragged(drag, dx, dy);
		pbs.dragTo(drag.y());
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent release) {
		if (!pbs.dragging()) return super.mouseReleased(release);
		pbs.drop();
		return true;
	}

	private FlowLayout spawnChart(List<SessionSummary> oldestFirst, Long average, String legend) {
		List<BarChart.Bar> bars = new ArrayList<>();
		for (SessionSummary session : oldestFirst) {
			if (session.averageSearchMillis() == null) continue;
			bars.add(new BarChart.Bar(session, session.averageSearchMillis() / 1000.0, BAR_GREEN, List.of(
					Component.literal("§f" + day(session.start()) + " " + clock(session.start())),
					Component.literal("§7Avg spawn " + spawn(session.averageSearchMillis()) + " §7over " + session.timedSpawns() + " shafts"),
					Component.literal("§8Click to open"))));
		}
		return barChart(bars, new BarChart.Look(journalSettings().spawnChart(), 0xFF55FF55, ChartAxis.SPAWN_STEPS,
				seconds -> ChartAxis.seconds(Math.round(seconds)), average == null ? null : average / 1000.0, 0xB0FFFF55,
				"§eYellow line§7: " + legend, ChartTips.spawn(oldestFirst), "§8No timed spawns yet"));
	}

	// coins = per session, same order
	private FlowLayout coinChart(List<SessionSummary> oldestFirst, List<Double> coinsEach, Double average, boolean priced, String legend,
			long totalMillis) {
		List<BarChart.Bar> bars = new ArrayList<>();
		List<ChartTips.SessionCoins> sessionCoins = new ArrayList<>();
		for (int i = 0; i < oldestFirst.size(); i++) {
			SessionSummary session = oldestFirst.get(i);
			Double coins = coinsEach.get(i);
			Double rate = coins == null ? null : session.perHour(coins, session.end());
			sessionCoins.add(new ChartTips.SessionCoins(coins, rate));
			if (rate == null) continue;
			List<Component> tip = new ArrayList<>();
			tip.add(Component.literal("§f" + day(session.start()) + " " + clock(session.start())));
			tip.add(Component.literal("§7Coins / h " + CoinBreakdown.coins(rate)));
			tip.add(Component.literal("§7Coins " + coinText(coins) + " §7in " + Format.duration(session.rateMillis())));
			if (session.breakMillis() > 0) tip.add(Component.literal("§8Breaks left out: " + Format.duration(session.breakMillis())));
			tip.add(Component.literal("§7Shafts §f" + session.shafts()));
			tip.add(Component.literal("§8Click to open"));
			bars.add(new BarChart.Bar(session, rate, Math.round(rate) < 0 ? BAR_RED : BAR_GOLD, tip));
		}
		String empty = priced ? "§8Needs a session of " + JournalStats.MIN_RATE_MILLIS / 60_000 + "+ minutes" : "§8No prices yet";
		return barChart(bars, new BarChart.Look(journalSettings().coinChart(), 0xFFFFAA00, ChartAxis.COIN_STEPS, JournalScreen::axisCoins,
				average, 0xB0FFFFFF, "§fWhite line§7: " + legend, ChartTips.coins(sessionCoins, totalMillis), empty));
	}

	private static String axisCoins(double coins) {
		return Coins.whole(coins);
	}

	// middle column inside its padding, a session per bar
	private FlowLayout barChart(List<BarChart.Bar> bars, BarChart.Look look) {
		int room = width - 2 * PADDING - 2 * GAP - width * STATS_LEFT / 100 - width * STATS_RIGHT / 100 - 2 * COLUMN_PADDING;
		return BarChart.of(font, bars, look, room, height, this::openSession);
	}

	// name + value widths for one group of bar rows, room = the columns inner width
	private record BarSizes(int name, int value, int room) {
	}

	private BarSizes barSizes(List<String> names, List<String> values, int room) {
		return new BarSizes(TableColumns.atLeast(BAR_NAME, tables.widest(names)), TableColumns.atLeast(BAR_VALUE, tables.widest(values)), room);
	}

	// track left out when too narrow to say anything, name left + value right stay
	private FlowLayout barRow(String name, double fraction, String value, int argb, BarSizes sizes) {
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(ROW_HEIGHT));
		row.verticalAlignment(VerticalAlignment.CENTER);
		row.gap(BAR_ROW_GAP);
		LabelComponent nameLabel = label("§f" + name);
		boolean withTrack = sizes.room() - sizes.name() - sizes.value() - 2 * BAR_ROW_GAP >= BAR_MIN;
		nameLabel.horizontalSizing(withTrack ? Sizing.fixed(sizes.name()) : Sizing.expand());
		row.child(nameLabel);
		if (withTrack) row.child(barTrack(fraction, argb));
		LabelComponent valueLabel = label(value);
		valueLabel.horizontalSizing(Sizing.fixed(sizes.value()));
		valueLabel.horizontalTextAlignment(HorizontalAlignment.RIGHT);
		row.child(valueLabel);
		return row;
	}

	private static FlowLayout barTrack(double fraction, int argb) {
		FlowLayout track = UIContainers.horizontalFlow(Sizing.expand(), Sizing.fixed(5));
		track.surface(Surface.flat(0x14FFFFFF));
		BoxComponent fill = UIComponents.box(Sizing.fill((int) Math.round(Math.min(1, fraction) * 100)), Sizing.fill(100));
		fill.fill(true);
		fill.color(Color.ofArgb(argb));
		track.child(fill);
		return track;
	}

	// sortable = the shaft table, its headers sort it
	private FlowLayout table(List<Column> columns, FlowLayout rows, boolean sortable) {
		KeptScroll scroll = scroll(Sizing.fill(100), Sizing.expand(), rows);
		tableScroll = scroll;
		return tables.panel(columns, scroll, sortable);
	}

	private FlowLayout row(int position, int index) {
		FlowLayout row = tableRow();
		row.surface(rowSurface(position, index, false));
		row.cursorStyle(CursorStyle.HAND);
		row.mouseEnter().subscribe(() -> row.surface(rowSurface(position, index, true)));
		row.mouseLeave().subscribe(() -> row.surface(rowSurface(position, index, false)));
		return row;
	}

	// stripes by position on screen, selection by entry
	private Surface rowSurface(int position, int index, boolean hovered) {
		if ((viewing || tab == Tab.SESSION) && index == selected) return ROW_SELECTED;
		if (hovered) return ROW_HOVER;
		return position % 2 == 1 ? ROW_EVEN : Surface.BLANK;
	}

	// gem shafts in fine, metal shafts in the metal tier setting
	private static LabelComponent minedCell(Column column, JournalEntry entry) {
		LabelComponent cell = cell(column, minedText(entry));
		int tier = Surveyor.MONEY.settings().metalTierUsed().tier();
		if (entry.gems() != null) cell.tooltip(List.of(Component.literal(minedColor(entry.gems().gem()) + minedGems(entry))));
		else if (entry.metal() != null) cell.tooltip(List.of(Component.literal(minedColor(entry.metal().metal()) + entry.metal().text(tier))));
		return cell;
	}

	private static String minedText(JournalEntry entry) {
		if (entry.gems() != null) return minedColor(entry.gems().gem()) + Format.decimal(entry.gems().fineEquivalent());
		if (entry.metal() != null) return minedColor(entry.metal().metal()) + entry.metal().amount(Surveyor.MONEY.settings().metalTierUsed().tier());
		return "§8-";
	}

	// outside rows mined cell
	private static String outsideMined(SessionSummary session) {
		Map<String, Double> fine = OutsideMining.fineGems(session.mining(), TextPatterns.defaults());
		return fine.isEmpty() ? "§8-" : "§f" + Format.decimal(fine.values().stream().mapToDouble(Double::doubleValue).sum());
	}

	private static String shaftCell(JournalEntry entry) {
		return "§b" + (entry.shaftId() == null ? "?" : entry.shaftId());
	}

	static String spawnCell(Long millis) {
		return millis == null ? "§8-" : "§f" + Format.duration(millis);
	}

	// the gem or metals hud colour, white if none
	private static String minedColor(String name) {
		return "§" + ShaftNames.defaults().minedColor(name);
	}

	private static String minedGems(JournalEntry entry) {
		return Format.decimal(entry.gems().fineEquivalent()) + " Fine " + entry.gems().gem();
	}

	private static FlowLayout tile(int width, String key, String value) {
		FlowLayout tile = UIContainers.verticalFlow(Sizing.fixed(width), Sizing.content());
		tile.surface(PANEL);
		tile.padding(Insets.of(5));
		tile.gap(3);
		tile.child(label("§7" + key));
		tile.child(label(bold(value)));
		return tile;
	}

	// color code resets bold, so bold goes after it
	private static String bold(String value) {
		return value.startsWith("§") && value.length() >= 2 ? value.substring(0, 2) + "§l" + value.substring(2) : "§l" + value;
	}

	private static FlowLayout column() {
		FlowLayout column = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		column.gap(2);
		return column;
	}

	private KeptScroll scrollColumn(Sizing width, FlowLayout column) {
		KeptScroll scroll = scroll(width, Sizing.fill(100), column);
		scroll.surface(PANEL);
		scroll.paddedWithBar(COLUMN_PADDING);
		return scroll;
	}

	private static FlowLayout panelWith(UIComponent child, Sizing width, Sizing height) {
		FlowLayout panel = UIContainers.verticalFlow(width, height);
		panel.surface(PANEL);
		panel.padding(Insets.of(8));
		panel.child(child);
		return panel;
	}

	static BoxComponent divider() {
		return UIComponents.box(Sizing.fill(100), Sizing.fixed(1)).color(Color.ofArgb(0x1FFFFFFF)).fill(true);
	}

	// gaps over Breaks.BREAK_MILLIS w/o mining, minus their 1st minutes. none = no tooltip
	private static <T extends UIComponent> T breakTooltip(T component, long breakMillis) {
		if (breakMillis <= 0) return component;
		component.tooltip(List.of(Component.literal("§7Breaks left out: §f" + Format.duration(breakMillis)),
				Component.literal("§8After " + Breaks.BREAK_MILLIS / 60_000 + " minutes without mining, time stops counting per hour")));
		return component;
	}

	private static FlowLayout keyValue(String key, String value) {
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		row.gap(KEY_GAP);
		row.child(label("§7" + key));
		LabelComponent valueLabel = label(value);
		valueLabel.horizontalSizing(Sizing.expand());
		row.child(valueLabel);
		return row;
	}

	private static LabelComponent header(String text) {
		LabelComponent label = label("§7" + text);
		label.margins(Insets.bottom(3));
		return label;
	}

	// title null = caller adds its own heading
	private static FlowLayout section(String title) {
		FlowLayout section = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		section.margins(Insets.vertical(4));
		section.gap(3);
		section.child(divider());
		if (title != null) section.child(wrapped("§7" + title));
		return section;
	}

	private static UIComponent spacer(int height) {
		return UIComponents.box(Sizing.fixed(1), Sizing.fixed(height)).color(Color.ofArgb(0));
	}

	private static String corpses(JournalEntry entry) {
		List<String> parts = new ArrayList<>();
		for (Map.Entry<String, CorpseCount> e : CorpseCounts.of(entry).entrySet()) {
			CorpseCount count = e.getValue();
			String color = count.looted() > 0 ? CorpseType.color(e.getKey()) : "§8";
			parts.add(color + count.looted() + "/" + count.total() + " " + e.getKey().charAt(0));
		}
		return parts.isEmpty() ? "§8-" : String.join(" ", parts);
	}

	private static String capitalized(String text) {
		return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}

	private static String spawn(Long millis) {
		return millis == null ? "§8-" : "§a" + Format.duration(millis);
	}

	static String clock(long millis) {
		return CLOCK.format(Instant.ofEpochMilli(millis).atZone(ZONE));
	}

	private static String time(long millis) {
		return TIME.format(Instant.ofEpochMilli(millis).atZone(ZONE));
	}

	private static LocalDate date(long millis) {
		return Instant.ofEpochMilli(millis).atZone(ZONE).toLocalDate();
	}

	static String day(long millis) {
		LocalDate date = date(millis);
		LocalDate today = LocalDate.now(ZONE);
		if (date.equals(today)) return "Today";
		if (date.equals(today.minusDays(1))) return "Yesterday";
		return (date.getYear() == today.getYear() ? DAY : DAY_YEAR).format(date);
	}

	// shorter at 480 wide, the full range ran under the tabs
	private String sessionRange(SessionSummary session, int room) {
		String start = day(session.start()) + " " + clock(session.start());
		String full = start + " to " + clock(session.end());
		if (font.width(full) <= room) return full;
		return font.width(start) <= room ? start : day(session.start());
	}

	// room left for the date: top bar minus its items, gaps and the < > arrows
	private int navRoom(List<UIComponent> items) {
		// nav is one more bar item
		int used = 2 * PADDING + 2 * BAR_PADDING + BAR_GAP * items.size() + 2 * ARROW_WIDTH + 2 * BAR_GAP;
		for (UIComponent item : items) used += fixedWidth(item);
		return width - used;
	}

	// stats left column inside its padding
	int statsLeftRoom() {
		return (width - 2 * PADDING) * STATS_LEFT / 100 - 2 * COLUMN_PADDING;
	}

	// same rounding as owos fill
	private int statsFill(int percent) {
		return Math.round(percent / 100f * (width - 2 * PADDING));
	}

	private int statsMiddleRoom() {
		return width - 2 * PADDING - statsFill(STATS_LEFT) - statsFill(STATS_RIGHT) - 2 * GAP - 2 * COLUMN_PADDING;
	}

	private int statsRightRoom() {
		return statsFill(STATS_RIGHT) - 2 * COLUMN_PADDING;
	}

	private static int dotsWidth(int count) {
		return count * DOT + (count - 1) * DOT_GAP + DOTS_MARGIN;
	}

	// fixed sizing only
	private static int fixedWidth(UIComponent component) {
		Insets margins = component.margins().get();
		return component.horizontalSizing().get().value + margins.left() + margins.right();
	}

	private static String fine(double value) {
		return value > 0 && value < 0.05 ? "<0.1" : Format.decimal(value);
	}

	private static String compact(double value) {
		if (value >= 9.95 || value == Math.rint(value)) return Format.number(Math.round(value));
		return fine(value);
	}
}
