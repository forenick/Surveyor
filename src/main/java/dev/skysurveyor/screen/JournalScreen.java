package dev.skysurveyor.screen;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleUnaryOperator;
import java.util.function.IntConsumer;
import java.util.function.UnaryOperator;

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
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import dev.skysurveyor.Surveyor;
import dev.skysurveyor.data.CorpseType;
import dev.skysurveyor.data.ShaftNames;
import dev.skysurveyor.detection.CorpseLoot;
import dev.skysurveyor.detection.ItemCount;
import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.hud.EditHudScreen;
import dev.skysurveyor.hud.HudData;
import dev.skysurveyor.journal.BreakEntry;
import dev.skysurveyor.journal.Breaks;
import dev.skysurveyor.journal.Journal;
import dev.skysurveyor.journal.JournalEntry;
import dev.skysurveyor.journal.JournalStats;
import dev.skysurveyor.journal.JournalStats.AllTime;
import dev.skysurveyor.journal.JournalStats.CorpseCount;
import dev.skysurveyor.journal.JournalStats.SessionSummary;
import dev.skysurveyor.journal.LootSummary;
import dev.skysurveyor.journal.MiningEntry;
import dev.skysurveyor.journal.OutsideMining;
import dev.skysurveyor.journal.PersonalBests;
import dev.skysurveyor.journal.PersonalBests.Def;
import dev.skysurveyor.journal.PersonalBests.Per;
import dev.skysurveyor.journal.PersonalBests.Stat;
import dev.skysurveyor.journal.StatsRange;
import dev.skysurveyor.journal.SummaryText;
import dev.skysurveyor.money.ChartTips;
import dev.skysurveyor.money.CoinBreakdown;
import dev.skysurveyor.money.Coins;
import dev.skysurveyor.money.MoneyStats;
import dev.skysurveyor.money.PriceRules;
import dev.skysurveyor.money.ShaftValue;
import dev.skysurveyor.money.ShaftValue.Line;
import dev.skysurveyor.money.ShaftValue.Part;
import dev.skysurveyor.settings.JournalSettings;
import dev.skysurveyor.settings.JournalSettings.ChartType;
import dev.skysurveyor.settings.MoneySettings;
import dev.skysurveyor.settings.Setting;
import dev.skysurveyor.settings.SettingsList;
import dev.skysurveyor.util.Format;

// mc font only so pack fonts work
public final class JournalScreen extends BaseOwoScreen<FlowLayout> {
	private enum Tab { OVERVIEW, SESSION, STATS }

	// the builders picks, saved on Done. original null = new, found again by value (list can change under it)
	private static final class PbDraft {
		final Def original;
		Stat stat;
		String shaft;
		String best;
		String name;

		PbDraft(Def original, Def def) {
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

	private record ShiftTip(FlowLayout row, List<ClientTooltipComponent> normal, List<ClientTooltipComponent> shifted) {
	}

	// higher dropOrder hides first, 0 = never. extra = spare room handed out by fit()
	private record Column(String key, String title, int width, boolean right, int dropOrder, int extra) {
		Column(String key, String title, int width, boolean right, int dropOrder) {
			this(key, title, width, right, dropOrder, 0);
		}

		int total() {
			return width + extra;
		}
	}

	private static final Surface PANEL = Surface.flat(0xE8141414).and(Surface.outline(0x30FFFFFF));
	// opaque, it covers the table
	private static final Surface OVERLAY = Surface.flat(0xFF141414).and(Surface.outline(0x50FFFFFF));
	private static final Surface ROW_EVEN = Surface.flat(0x0DFFFFFF);
	private static final Surface ROW_DAY = Surface.flat(0x1CFFFFFF);
	private static final Surface ROW_HOVER = Surface.flat(HoverFlow.ROW);
	private static final Surface ROW_SELECTED = Surface.flat(0x26FFFFFF).and(Surface.outline(0x50FFFFFF));
	private static final Surface SLOT = Surface.flat(0xFF8B8B8B).and(Surface.outline(0xFF373737));
	private static final int BAR_GREEN = 0xCC55FF55;
	private static final int BAR_GOLD = 0xCCFFAA00;
	private static final int BAR_RED = 0xCCFF5555;
	private static final Color GREEN = Color.ofArgb(BAR_GREEN);
	private static final int ROW_HEIGHT = 12;
	private static final int SPAWN_BAR = 26;
	private static final int PADDING = 8;
	private static final int GAP = 5;
	private static final int COLUMN_PADDING = 6;
	private static final int COLUMN_GAP = 6;
	// table padding + row padding + scrollbar
	private static final int TABLE_CHROME = 10 + 4 + 4;
	// in %, middle gets the rest
	private static final int STATS_LEFT = 32;
	private static final int STATS_RIGHT = 27;
	private static final int AXIS_GAP = 3;
	private static final int BAR_PADDING = 4;
	private static final int BAR_GAP = 4;
	private static final int ARROW_WIDTH = 16;
	private static final int INDENT = 6;
	// display choice
	private static final int DAY_GAP = 8;
	// display choice
	private static final int LINE_GAP = 8;
	// display choice: narrower and the session list folds to a strip
	private static final int SIDE_MIN_WIDTH = 560;
	private static final int STRIP_WIDTH = 12;
	private static final int SIDE_PADDING = 3;
	private static final Identifier OVERVIEW_ICON = Identifier.fromNamespaceAndPath("skysurveyor", "textures/gui/overview_icon.png");
	private static final Identifier SESSION_ICON = Identifier.fromNamespaceAndPath("skysurveyor", "textures/gui/session_icon.png");
	private static final Identifier STATS_ICON = Identifier.fromNamespaceAndPath("skysurveyor", "textures/gui/stats_icon.png");
	static final Identifier SETTINGS_ICON = Identifier.fromNamespaceAndPath("skysurveyor", "textures/gui/settings_icon.png");
	private static final Identifier MOVE_ICON = Identifier.fromNamespaceAndPath("skysurveyor", "textures/gui/move_icon.png");
	private static final Identifier OPEN_ARROW = Identifier.fromNamespaceAndPath("skysurveyor", "textures/gui/open_arrow.png");
	private static final Identifier PREV_ARROW = Identifier.fromNamespaceAndPath("skysurveyor", "textures/gui/prev_arrow.png");
	private static final int RECENT_SESSIONS = 5;
	// seconds
	private static final double[] SPAWN_STEPS = {5, 10, 15, 30, 60, 120, 300, 600, 900, 1800, 3600};
	// 1, 2, 5 x 10^n coins
	private static final double[] COIN_STEPS = coinSteps();
	private static final ZoneId ZONE = ZoneId.systemDefault();
	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
	private static final DateTimeFormatter DAY_YEAR = DateTimeFormatter.ofPattern("MMM d yyyy", Locale.ENGLISH);

	// width 0 = measured per table, see measured()
	private static final List<Column> SHAFT_COLUMNS = List.of(
			new Column("number", "#", 14, false, 5), new Column("shaft", "Shaft", 44, false, 0),
			new Column("time", "Time", 32, false, 3), new Column("spawn", "Spawn", 66, false, 0),
			new Column("inside", "Inside", 0, true, 2), new Column("corpses", "Corpses", 0, false, 0),
			new Column("powder", "Powder", 44, true, 1), new Column("mined", "Mined", 40, true, 4),
			new Column("coins", "Coins", 0, true, 0), new Column("campfire", "Campfire", 0, false, 6));
	// by day: start time goes under the days date, no start column
	private static final List<Column> SESSION_COLUMNS = List.of(
			new Column("date", "Date", 56, false, 0), new Column("start", "Start", 32, false, 5),
			new Column("length", "Length", 44, true, 4), new Column("shafts", "Shafts", 36, true, 0),
			new Column("spawn", "Avg spawn", 78, false, 0), new Column("rate", "Per hour", 46, true, 3),
			new Column("powder", "Powder", 58, true, 0), new Column("lapis", "Lapis", 36, true, 6),
			new Column("coins", "Coins", 0, true, 0), new Column("coinRate", "Coins / h", 0, true, 2));

	private final List<JournalEntry> entries;
	private final List<MiningEntry> mining;
	private final List<BreakEntry> breaks;
	private final List<SessionSummary> sessions;
	private final ItemIcons icons = new ItemIcons();
	private final ShaftNames shaftNames = ShaftNames.defaults();
	private Tab tab;
	// an older session opened from overview or stats, tab stays where it came from
	private boolean viewing;
	private final boolean current;
	private final boolean started;
	private boolean byDay = byDaySetting();
	private int sessionIndex;
	private int selected;
	private FlowLayout root;
	private int builtWidth;
	private int builtHeight;
	private FlowLayout detail;
	private final List<FlowLayout> shaftRows = new ArrayList<>();
	private final List<KeptScroll> scrolls = new ArrayList<>();
	private final List<ShiftTip> shiftTips = new ArrayList<>();
	// kept while the game runs, not saved
	private static StatsRange statsRange = StatsRange.ALL_TIME;
	// session table order, null = played order. kept while the game runs
	private static String shaftSort;
	private static boolean shaftSortFlipped;
	// display row -> entry index
	private int[] shaftOrder = new int[0];
	private int statsShafts;
	private boolean shiftShown;
	private int moneyChanges = Surveyor.MONEY.changes();
	private boolean stripOpen;
	// personal bests: edit mode, builder, drag
	private boolean editingPbs;
	private PbDraft draft;
	// "value" or "shaft", null = none open
	private String dropdown;
	private UIComponent dropdownAnchor;
	private TextBoxComponent nameField;
	private TextBoxComponent homeField;
	private LabelComponent previewLabel;
	private String previewName;
	// name doesnt change it, only the builder rebuild does
	private PersonalBests.Result previewResult;
	private List<JournalEntry> pbEntries = List.of();
	private List<SessionSummary> pbSessions = List.of();
	// builder + edit clicks rebuild the tab, same range + prices = same results
	private final Map<Def, PersonalBests.Result> pbCache = new HashMap<>();
	private List<Object> pbCacheKey = List.of();
	private final List<FlowLayout> pbRows = new ArrayList<>();
	private final List<FlowLayout> pbGrips = new ArrayList<>();
	// the list the rows were built from, drag indexes point into it
	private List<Def> pbShown = List.of();
	private int pbDrag = -1;
	private int pbGap = -1;
	private KeptScroll sideScroll;
	// list clicks keep the list where it was
	private Double keptSideOffset;

	// mining = outside shafts, breaks = gaps not counted per hour
	public JournalScreen(List<JournalEntry> entries, List<MiningEntry> mining, List<BreakEntry> breaks) {
		super(Component.literal("Shaft Journal"));
		this.entries = List.copyOf(entries);
		this.mining = List.copyOf(mining);
		this.breaks = List.copyOf(breaks);
		this.sessions = JournalStats.sessions(this.entries, this.mining, this.breaks);
		selectLastShaft();
		// session tab = only the one still going
		Journal.Session active = Surveyor.JOURNAL.activeSession(System.currentTimeMillis());
		current = active != null && !sessions.isEmpty() && sessions.getFirst().id() == active.id();
		started = active != null && !current;
		tab = current ? Tab.SESSION : Tab.OVERVIEW;
	}

	@Override
	protected OwoUIAdapter<FlowLayout> createAdapter() {
		return OwoUIAdapter.create(this, UIContainers::verticalFlow);
	}

	// prices + settings load on the io thread after the journal opens
	@Override
	public void tick() {
		super.tick();
		// caxton cancels onValueChange, poll
		updatePreview();
		if (byDayChanged()) {
			rebuild();
			return;
		}
		int changes = Surveyor.MONEY.changes();
		if (changes == moneyChanges) return;
		moneyChanges = changes;
		rebuild(true);
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
		// back from settings, before the 1st frame
		if (root != null && (byDayChanged() || width != builtWidth || height != builtHeight)) rebuild();
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

	// keepScroll = same view w/ new prices, scrolls stay put
	private void rebuild(boolean keepScroll) {
		List<Double> offsets = keepScroll ? scrolls.stream().map(KeptScroll::offset).toList() : List.of();
		scrolls.clear();
		buildScreen();
		if (offsets.size() == scrolls.size()) {
			for (int i = 0; i < offsets.size(); i++) scrolls.get(i).offset(offsets.get(i));
		}
	}

	private KeptScroll scroll(Sizing horizontalSizing, Sizing verticalSizing, FlowLayout child) {
		KeptScroll scroll = new KeptScroll(horizontalSizing, verticalSizing, child);
		scrolls.add(scroll);
		return scroll;
	}

	private void buildScreen() {
		builtWidth = width;
		builtHeight = height;
		sideScroll = null;
		pbRows.clear();
		pbGrips.clear();
		if (draft != null) keepTypedName();
		nameField = null;
		previewLabel = null;
		root.clearChildren();
		shaftRows.clear();
		shiftTips.clear();
		detail = null;
		root.child(topBar());
		FlowLayout content = UIContainers.verticalFlow(Sizing.fill(100), Sizing.expand());
		content.gap(GAP);
		if (entries.isEmpty()) {
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
		footer.gap(16);
		if (viewing) {
			footer.child(label("§8Click a shaft for details"));
			footer.child(label("§8< > other sessions"));
		} else switch (tab) {
			case SESSION -> {
				if (current) footer.child(label("§8Click a shaft for details"));
			}
			case OVERVIEW -> footer.child(label("§8Click a session to open it"));
			case STATS -> {
				footer.child(label("§8Counted from your own " + statsShafts + " shafts"));
				footer.child(label("§8Hover a row for details"));
				footer.child(label(editingPbs ? "§8Drag the grips to reorder" : "§8Click a PB to open it"));
			}
		}
		root.child(footer);
		if (draft != null && !viewing && tab == Tab.STATS) buildPbBuilder();
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
			draft = null;
			dropdown = null;
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

	private void openShaft(JournalEntry entry) {
		for (int i = 0; i < sessions.size(); i++) {
			int index = sessions.get(i).entries().indexOf(entry);
			if (index >= 0) {
				sessionIndex = i;
				selected = index;
				viewing = true;
				rebuild();
				return;
			}
		}
	}

	private void openSession(SessionSummary session) {
		int index = sessions.indexOf(session);
		if (index >= 0) showSession(index);
	}

	private void selectLastShaft() {
		selected = sessions.isEmpty() ? -1 : sessions.get(sessionIndex).shafts() - 1;
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
		FlowLayout rateTile = tile(tileWidth, "Per hour", rate == null ? "§8-" : "§f" + Format.decimal(rate));
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
		List<Column> measured = measured(SHAFT_COLUMNS, "inside",
				session.entries().stream().map(e -> Format.duration(e.durationMillis())).toList());
		measured = measured(measured, "corpses", session.entries().stream().map(JournalScreen::corpses).toList());
		List<String> coinTexts = new ArrayList<>(shaftCoins.stream().map(JournalScreen::coinText).toList());
		if (!session.mining().isEmpty()) coinTexts.add(coinText(sessionMoney.outside()));
		measured = measured(measured, "coins", coinTexts);
		if (session.entries().stream().allMatch(e -> campfires(e) == 0)) {
			measured = measured.stream().filter(c -> !c.key().equals("campfire")).toList();
		} else {
			measured = measured(measured, "campfire", session.entries().stream().map(JournalScreen::campfireCell).toList());
		}
		List<Column> columns = fit(measured, width - 2 * PADDING - 2 * GAP - sideWidth - detailWidth - TABLE_CHROME);
		FlowLayout rows = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		shaftOrder = shaftOrder(session.entries(), shaftCoins);
		for (int position = 0; position < shaftOrder.length; position++) {
			int i = shaftOrder[position];
			JournalEntry entry = session.entries().get(i);
			FlowLayout row = row(position, i);
			for (Column column : columns) {
				row.child(switch (column.key()) {
					case "number" -> cell(column, "§8" + (i + 1));
					case "shaft" -> cell(column, "§b" + (entry.shaftId() == null ? "?" : entry.shaftId()));
					case "time" -> cell(column, "§7" + clock(entry.enteredAt()));
					case "spawn" -> barCell(column, entry.searchMillis(), slowest);
					case "inside" -> cell(column, "§f" + Format.duration(entry.durationMillis()));
					case "corpses" -> cell(column, corpses(entry));
					case "powder" -> cell(column, "§f" + Format.number(JournalStats.powder(entry)));
					case "coins" -> cell(column, coinText(shaftCoins.get(i)));
					case "campfire" -> cell(column, campfireCell(entry));
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
		main.child(table(columns, rows, this::sortShafts));

		detail = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		detail.gap(2);
		KeptScroll detailScroll = scroll(Sizing.fixed(detailWidth), Sizing.fill(100), detail);
		detailScroll.surface(PANEL);
		detailScroll.padding(Insets.of(COLUMN_PADDING));
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
		int span = -COLUMN_GAP;
		for (Column column : columns.subList(0, first)) span += column.total() + COLUMN_GAP;
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
			gems.text(Component.literal("§f" + Format.decimal(fine.values().stream().mapToDouble(Double::doubleValue).sum())));
			List<Component> tooltip = new ArrayList<>();
			tooltip.add(Component.literal("§fGems mined outside shafts"));
			fine.forEach((gem, count) -> tooltip.add(Component.literal(minedColor(gem) + Format.decimal(count) + " Fine " + gem)));
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
		scroll.scrollbarThiccness(3);
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

	// measured bc pack fonts (caxton, bold) are wider. +1 slack
	private List<Column> measured(List<Column> columns, String key, List<String> texts) {
		return columns.stream().map(c -> {
			if (!c.key().equals(key)) return c;
			int widest = font.width(c.title());
			for (String text : texts) widest = Math.max(widest, font.width(text));
			return new Column(c.key(), c.title(), widest + 1, c.right(), c.dropOrder());
		}).toList();
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
		String powder = "§b" + Format.number(JournalStats.powder(entry));
		if (entry.corpsePowder() > 0 && entry.exitPowder() > 0) {
			powder += " §8(" + Format.number(entry.corpsePowder()) + " corpses + " + Format.number(entry.exitPowder()) + " exit)";
		}
		detail.child(keyValue("Powder", powder));
		if (campfires(entry) > 0) {
			String colds = colds(entry);
			detail.child(keyValue("Campfires", "§f" + campfires(entry) + (colds == null ? "" : " §7at " + colds)));
		}
		if (entry.coldAtExit() != null) detail.child(keyValue("Cold at exit", "§b" + entry.coldAtExit() + "❄"));

		detail.child(section(lootTitle(entry.loots())));
		LootSummary loot = LootSummary.of(entry.loots(), TextPatterns.defaults());
		if (loot.gems() == null && loot.items().isEmpty()) {
			detail.child(wrapped("§8No corpses looted"));
		} else {
			FlowLayout grid = UIContainers.ltrTextFlow(Sizing.fill(100), Sizing.content());
			grid.gap(2);
			if (loot.gems() != null) {
				List<Component> tooltip = new ArrayList<>();
				tooltip.add(Component.literal("§fGemstones §7" + fine(loot.gems().fine()) + " Fine"));
				for (ItemCount part : loot.gems().parts()) tooltip.add(Component.literal("§7" + Format.number(part.count()) + " " + part.item()));
				// parts sorted, first = most common
				grid.child(slot(loot.gems().parts().getFirst().item(), compact(loot.gems().fine()), tooltip));
			}
			for (ItemCount item : loot.items()) {
				grid.child(slot(item.item(), Format.number(item.count()),
						List.of(Component.literal("§f" + item.item()), Component.literal("§7× " + Format.number(item.count())))));
			}
			detail.child(grid);
		}

		if (entry.gems() != null) {
			detail.child(section("Gems mined"));
			detail.child(wrapped(minedColor(entry.gems().gem()) + minedGems(entry)));
			if (entry.pristineProcs() > 0) detail.child(wrapped("§8" + entry.pristineProcs() + " pristine procs"));
		} else if (entry.metal() == null) {
			// metal shafts skip the empty gems section
			detail.child(section("Gems mined"));
			detail.child(wrapped("§8None"));
		}
		if (entry.metal() != null) {
			detail.child(section("Metal mined"));
			detail.child(wrapped(minedColor(entry.metal().metal()) + entry.metal().text(Surveyor.MONEY.settings().metalTierUsed().tier())));
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
				tooltip.add(Component.literal("§7" + CoinBreakdown.count(line.part(), line.item(), line.count()) + " " + line.item() + "  "
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
		withTooltip(heading, how);
		entry.savedKeys().forEach((key, count) -> detail.child(wrapped("§7" + count + " " + key + (count == 1 ? "" : "s") + " saved by Resourceful")));
		int unpriced = value.unpriced().size();
		if (unpriced > 0) detail.child(wrapped(CoinBreakdown.unpriced(unpriced, ironman)));
		boolean saved = Surveyor.MONEY.usesSavedPrices(entry);
		long end = entry.endedAt();
		detail.child(wrapped("§8" + (saved ? "Prices from " + day(end) + " " + clock(end) : "Today's prices")));
	}

	// cold at use, count for shafts from before 0.22.0 or when some werent used
	private static String campfireCell(JournalEntry entry) {
		int count = campfires(entry);
		if (count == 0) return "§8-";
		String colds = colds(entry);
		if (colds == null) return "§f" + count;
		return count > entry.campfireColds().size() ? "§f" + count + " §7at " + colds : colds;
	}

	// placed or used, whichever saw more
	private static int campfires(JournalEntry entry) {
		return Math.max(entry.campfiresAt().size(), entry.campfireColds() == null ? 0 : entry.campfireColds().size());
	}

	// null = no cold saved
	private static String colds(JournalEntry entry) {
		if (entry.campfireColds() == null) return null;
		List<String> colds = entry.campfireColds().stream().map(c -> c == null ? "§8?" : "§b" + c + "❄").toList();
		return colds.isEmpty() ? null : String.join("§7, ", colds);
	}

	private static FlowLayout tipRow(String key, String value, List<Component> tooltip) {
		return withTooltip(keyValue(key, value), tooltip);
	}

	private static String lootTitle(List<CorpseLoot> loots) {
		if (loots.isEmpty()) return "Corpse loot";
		Map<String, Integer> byType = new LinkedHashMap<>();
		for (CorpseLoot loot : loots) byType.merge(loot.type(), 1, Integer::sum);
		List<String> parts = new ArrayList<>();
		byType.forEach((type, count) -> parts.add(count + " " + type));
		return "Corpse loot (" + String.join(", ", parts) + ")";
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
		List<Column> measured = measured(SESSION_COLUMNS.stream().filter(c -> !byDay || !c.key().equals("start")).toList(),
				"coins", coinTexts);
		measured = measured(measured, "coinRate", rateTexts);
		List<Column> columns = fit(measured, width - 2 * PADDING - TABLE_CHROME);
		FlowLayout rows = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		for (Day day : days) {
			if (byDay) {
				FlowLayout dayRow = dayRow(columns, day);
				if (!rows.children().isEmpty()) dayRow.margins(Insets.top(DAY_GAP));
				rows.child(dayRow);
			}
			for (int i : day.sessions()) {
				SessionSummary session = sessions.get(i);
				Double rate = session.shaftsPerHour();
				FlowLayout row = row(i, i);
				for (Column column : columns) {
					row.child(switch (column.key()) {
						case "date" -> byDay ? indented(column, "§7" + clock(session.start())) : cell(column, "§f" + day(session.start()));
						case "start" -> cell(column, "§7" + clock(session.start()));
						case "length" -> breakTooltip(cell(column, "§f" + Format.duration(session.lengthMillis())), session.breakMillis());
						case "shafts" -> cell(column, "§f" + session.shafts());
						case "spawn" -> barCell(column, session.averageSearchMillis(), slowest);
						case "rate" -> cell(column, rate == null ? "§8-" : "§f" + Format.decimal(rate));
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
		content.child(table(columns, rows, null));
	}

	private FlowLayout dayRow(List<Column> columns, Day day) {
		FlowLayout row = tableRow();
		row.surface(ROW_DAY);
		int count = day.sessions().size();
		for (Column column : columns) {
			row.child(switch (column.key()) {
				case "date" -> cell(column, "§f" + day(day.start()));
				case "length" -> breakTooltip(cell(column, "§f" + Format.duration(day.millis())), day.millis() - day.rateMillis());
				case "shafts" -> cell(column, "§f" + day.shafts());
				case "spawn" -> cell(column, "§7" + count + (count == 1 ? " session" : " sessions"));
				case "powder" -> cell(column, "§b" + Format.number(day.powder()));
				case "coins" -> cell(column, coinText(day.coins()));
				case "coinRate" -> cell(column, dayRate(day));
				default -> cell(column, "");
			});
		}
		return row;
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

	private void buildStatsTab(FlowLayout content) {
		boolean all = statsRange == StatsRange.ALL_TIME;
		List<SessionSummary> rangeSessions = statsRange.sessions(sessions, System.currentTimeMillis());
		List<JournalEntry> rangeEntries = all ? entries : StatsRange.flat(rangeSessions, SessionSummary::entries);
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
		List<MiningEntry> rangeMining = StatsRange.flat(rangeSessions, SessionSummary::mining);
		AllTime stats = JournalStats.allTime(rangeEntries, rangeMining, StatsRange.flat(rangeSessions, SessionSummary::breaks));
		MoneyStats money = MoneyStats.of(rangeEntries, Surveyor.MONEY::value, rangeMining, Surveyor.MONEY::lines,
				Surveyor.MONEY.settings().isIronman());

		totals.child(coinsTotal(money, stats, beforeOutside(rangeSessions), tax(rangeEntries, rangeMining, money)));
		totals.child(keyValue("Powder", "§b" + Format.number(stats.powder())));
		FlowLayout shafts = keyValue("Shafts", "§f" + stats.shafts());
		shafts.margins(Insets.top(LINE_GAP));
		totals.child(shafts);
		totals.child(keyValue("Sessions", "§f" + stats.sessions()));
		long breakMillis = rangeSessions.stream().mapToLong(SessionSummary::breakMillis).sum();
		totals.child(breakTooltip(keyValue("Time shafting", "§f" + Format.duration(stats.timeMillis())), breakMillis));
		JournalStats.Campfires campfires = JournalStats.campfires(rangeEntries);
		if (campfires.placed() > 0) {
			Double cold = campfires.averageCold();
			totals.child(keyValue("Campfires", "§f" + campfires.placed()
					+ (cold == null ? "" : " §7avg at §b" + Math.round(cold) + "❄")));
		}
		totals.child(section("Corpses looted"));
		for (String type : stats.corpsesSeen().keySet()) {
			totals.child(corpseRow(type, stats.looted().getOrDefault(type, 0), money, stats.drops().get(type)));
		}
		personalBests(totals, rangeEntries, rangeSessions);
		columns.child(scrollColumn(Sizing.fill(STATS_LEFT), totals));

		FlowLayout spawn = column();
		spawn.child(header("Spawn time"));
		if (all) {
			spawn.child(keyValue("All-time avg", stats.averageSearchMillis() == null ? "§8-"
					: "§e" + Format.duration(stats.averageSearchMillis())));
			Long recent = JournalStats.recentAverage(sessions, RECENT_SESSIONS);
			if (recent != null && stats.averageSearchMillis() != null && sessions.size() > RECENT_SESSIONS) {
				spawn.child(keyValue("Last " + RECENT_SESSIONS + " sessions",
						"§f" + Format.duration(recent) + change(recent, stats.averageSearchMillis())));
			}
		} else {
			Long average = stats.averageSearchMillis();
			Long allTime = JournalStats.averageSearch(entries);
			spawn.child(keyValue("Avg", average == null ? "§8-"
					: "§e" + Format.duration(average) + (allTime == null ? "" : change(average, allTime))));
			spawn.child(keyValue("All-time avg", allTime == null ? "§8-" : "§f" + Format.duration(allTime)));
		}
		String legend = all ? "all-time avg" : "avg";
		spawn.child(chartSection("Avg spawn per session", journalSettings().spawnChart(),
				step -> changeJournal(j -> j.withSpawnChart(j.spawnChart().next(step)))));
		spawn.child(spawnChart(stats.oldestFirst(), stats.averageSearchMillis(), legend));
		spawn.child(section("Corpse % per shaft"));
		for (String type : stats.corpsesSeen().keySet()) {
			int with = stats.shaftsWithCorpse().getOrDefault(type, 0);
			FlowLayout row = barRow(CorpseType.color(type) + type, with / (double) stats.shafts(),
					Math.round(100.0 * with / stats.shafts()) + "%", CorpseType.argb(type));
			row.tooltip(List.of(Component.literal("§f" + type + " corpses"),
					Component.literal("§7In " + with + " of " + stats.shafts() + " shafts"),
					Component.literal("§7" + String.format(Locale.ROOT, "%.2f", stats.corpsesSeen().get(type) / (double) stats.shafts())
							+ " per shaft on average")));
			spawn.child(row);
		}
		spawn.child(chartSection("Coins / h per session", journalSettings().coinChart(),
				step -> changeJournal(j -> j.withCoinChart(j.coinChart().next(step)))));
		Double averageRate = money.total() == null ? null : JournalStats.perHour(money.total(), stats.timeMillis());
		spawn.child(coinChart(stats.oldestFirst(), averageRate, money.total() != null, legend, stats.timeMillis()));
		columns.child(scrollColumn(Sizing.expand(), spawn));

		FlowLayout odds = column();
		odds.child(header("Shaft types seen"));
		int typed = stats.typedShafts();
		for (Map.Entry<String, Map<String, Integer>> type : stats.types().entrySet()) {
			String name = shaftNames.name(type.getKey());
			int total = type.getValue().values().stream().mapToInt(Integer::intValue).sum();
			FlowLayout row = barRow("§f" + name, total / (double) typed,
					Math.round(100.0 * total / typed) + "%", 0xFF000000 | shaftNames.rgb(type.getKey()));
			List<Component> tooltip = new ArrayList<>();
			tooltip.add(Component.literal("§f" + name + " shafts §7" + total));
			type.getValue().forEach((variant, count) -> {
				if (!variant.equals(ShaftNames.LITTLEFOOT)) tooltip.add(Component.literal("§7" + capitalized(ShaftNames.variantName(variant)) + " §f" + count));
			});
			row.tooltip(tooltip);
			odds.child(row);
		}
		typeCoins(odds, money);
		columns.child(scrollColumn(Sizing.fill(STATS_RIGHT), odds));
		content.child(columns);
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
		FlowLayout row = keyValue(type, corpse == null ? value : value + " §7(" + CoinBreakdown.coins(corpse.each()) + " §7avg.)");
		if (corpse == null && drops == null) return row;
		return withTips(row, TooltipLines.of(CoinBreakdown.corpse(type, looted, corpse, drops, tooltipHeight(),
				Surveyor.MONEY.settings().isIronman())));
	}

	// best avg first, bar = share of the coins all types made
	private void typeCoins(FlowLayout column, MoneyStats money) {
		List<Map.Entry<String, MoneyStats.TypeCoins>> types = money.types().entrySet().stream()
				.sorted(Comparator.comparingDouble((Map.Entry<String, MoneyStats.TypeCoins> e) -> -e.getValue().each())).toList();
		if (types.isEmpty()) return;
		column.child(section("Avg coins per shaft"));
		double made = types.stream().mapToDouble(e -> Math.max(0, e.getValue().coins())).sum();
		int valueWidth = types.stream().mapToInt(e -> font.width(CoinBreakdown.coins(e.getValue().each()))).max().orElse(0);
		for (Map.Entry<String, MoneyStats.TypeCoins> type : types) {
			String name = shaftNames.name(type.getKey());
			Double share = made > 0 ? Math.max(0, type.getValue().coins()) / made : null;
			FlowLayout row = barRow(name, share == null ? 0 : share, CoinBreakdown.coins(type.getValue().each()),
					0xFF000000 | shaftNames.rgb(type.getKey()), valueWidth);
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
		LabelComponent name = label("§7Range §e" + statsRange.displayName());
		name.horizontalSizing(Sizing.expand());
		row.child(name);
		row.child(cycler(statsRange.ordinal(), StatsRange.values().length, step -> statsRange = statsRange.next(step)));
		List<Component> tooltip = List.of(Component.literal("§fTime range"),
				Component.literal("§7Which sessions the whole Stats tab counts"),
				Component.literal("§8Click the arrows to cycle"));
		applyTooltip(row, tooltip);
		return row;
	}

	private static void applyTooltip(UIComponent component, List<Component> tooltip) {
		component.tooltip(tooltip);
		if (component instanceof ParentUIComponent parent) {
			for (UIComponent child : parent.children()) applyTooltip(child, tooltip);
		}
	}

	// dots show where u are, arrows wrap around
	private FlowLayout cycler(int index, int count, IntConsumer step) {
		FlowLayout cycler = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
		cycler.verticalAlignment(VerticalAlignment.CENTER);
		FlowLayout dots = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
		dots.gap(2);
		dots.margins(Insets.right(3));
		for (int i = 0; i < count; i++) {
			BoxComponent dot = UIComponents.box(Sizing.fixed(3), Sizing.fixed(3));
			dot.fill(true);
			dot.color(Color.ofArgb(i == index ? 0xFFFFFF55 : 0xFF555555));
			dots.child(dot);
		}
		cycler.child(dots);
		cycler.child(cyclerArrow(PREV_ARROW, () -> step.accept(-1)));
		cycler.child(cyclerArrow(OPEN_ARROW, () -> step.accept(1)));
		return cycler;
	}

	// padding = bigger click area
	private UIComponent cyclerArrow(Identifier texture, Runnable action) {
		FlowLayout arrow = new HoverFlow(Sizing.content(), Sizing.content(), HoverFlow.SMALL);
		arrow.padding(Insets.of(2, 2, 3, 3));
		arrow.child(UIComponents.texture(texture, 0, 0, 3, 5, 3, 5).blend(true));
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

	private static FlowLayout withTips(FlowLayout row, List<ClientTooltipComponent> tooltip) {
		row.tooltip(tooltip);
		row.children().forEach(child -> child.tooltip(tooltip));
		return row;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		boolean shift = minecraft.hasShiftDown();
		if (shift != shiftShown) {
			shiftShown = shift;
			for (ShiftTip tip : shiftTips) withTips(tip.row(), shift ? tip.shifted() : tip.normal());
		}
		// caxton: text set at 0 width can scroll 1st letter out, rehome next frame
		if (homeField != null) {
			homeField.moveCursorToEnd(false);
			homeField.moveCursorToStart(false);
			homeField = null;
		}
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		drawPbDrag(graphics);
	}

	private static FlowLayout withTooltip(FlowLayout row, List<Component> tooltip) {
		row.tooltip(tooltip);
		row.children().forEach(child -> child.tooltip(tooltip));
		return row;
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

	private List<Def> pbList() {
		return Surveyor.SETTINGS.get().personalBests().list();
	}

	private void savePbs(List<Def> list) {
		Surveyor.SETTINGS.change(s -> s.withPersonalBests(s.personalBests().withList(list)));
	}

	private static final PersonalBests.Coins PB_COINS = PersonalBests.Coins.of(entry -> MoneyStats.net(Surveyor.MONEY.value(entry)),
			Surveyor.MONEY::value);

	private PersonalBests.Result pbResult(Def def) {
		return pbCache.computeIfAbsent(def, d -> PersonalBests.best(d, pbEntries, pbSessions, PB_COINS));
	}

	private void personalBests(FlowLayout totals, List<JournalEntry> entries, List<SessionSummary> sessions) {
		pbEntries = entries;
		pbSessions = sessions;
		// both ends, so new shafts + loaded older ones count as a change
		List<Object> cacheKey = Arrays.asList(entries.size(), entries.isEmpty() ? null : entries.getFirst(),
				entries.isEmpty() ? null : entries.getLast(), sessions.size(), Surveyor.MONEY.changes());
		if (!cacheKey.equals(pbCacheKey)) {
			pbCache.clear();
			pbCacheKey = cacheKey;
		}
		FlowLayout header = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		header.margins(Insets.vertical(4));
		header.gap(3);
		header.child(divider());
		FlowLayout title = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		title.verticalAlignment(VerticalAlignment.CENTER);
		LabelComponent name = label("§7Personal bests");
		name.horizontalSizing(Sizing.expand());
		title.child(name);
		title.child(textButton(editingPbs ? "§eDone" : "§7Edit", editingPbs ? "Stop editing" : "Add, remove or reorder personal bests", () -> {
			editingPbs = !editingPbs;
			rebuild(true);
		}));
		header.child(title);
		totals.child(header);

		List<Def> list = pbList();
		pbShown = list;
		ShaftNames names = ShaftNames.defaults();
		for (Def def : list) {
			PersonalBests.Result result = pbResult(def);
			String key = PersonalBests.name(def, names);
			// metal record only once u have metal shafts
			if (!editingPbs && def.type() == Stat.SHAFT_METAL && result.value() == null && def.shaft() == null) continue;
			if (editingPbs) {
				totals.child(editRow(def, key, pbText(def, result, key)));
				continue;
			}
			Runnable open = result.entry() != null ? () -> openShaft(result.entry())
					: result.session() != null ? () -> openSession(result.session()) : null;
			String target = result.entry() != null ? result.entry().shaftId() : result.session() != null ? sessionName(result.session()) : null;
			totals.child(record(key, pbText(def, result, key), open, target));
		}
		if (editingPbs) {
			FlowLayout buttons = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
			buttons.gap(6);
			buttons.margins(Insets.top(3));
			buttons.child(textButton("§a+ Add PB", "Make your own personal best", () -> {
				draft = new PbDraft(null, new Def(Stat.SHAFT_COINS.id(), null, null, null));
				rebuild(true);
			}));
			if (!list.equals(PersonalBests.DEFAULTS)) {
				buttons.child(textButton("§7Reset", "Back to the default list", () -> {
					savePbs(PersonalBests.DEFAULTS);
					rebuild(true);
				}));
			}
			totals.child(buttons);
		}
	}

	// null result value = "-". same text as the hud line, plus colours
	private String pbText(Def def, PersonalBests.Result result, String key) {
		Stat stat = def.type();
		if (stat == null || result.value() == null) return null;
		int tier = Surveyor.MONEY.settings().metalTierUsed().tier();
		String text = HudData.pbText(def, result, tier);
		// scale 4: tier word would wrap the label to 3 lines
		int room = (width - 2 * PADDING) * STATS_LEFT / 100 - 2 * COLUMN_PADDING - 20;
		if (stat == Stat.SHAFT_METAL && font.width(key + " " + text) > room) text = result.entry().metal().amount(tier);
		String color = switch (stat) {
			case SHAFT_COINS, SESSION_COINS, SESSION_COIN_RATE -> Math.round(result.value()) < 0 ? "§c" : "§6";
			case SHAFT_POWDER, SESSION_POWDER, SHAFT_COLD -> "§b";
			case SHAFT_SPAWN, SESSION_SPAWN -> "§a";
			case SHAFT_LAPIS -> "§9";
			default -> "§f";
		};
		return color + text.replace(" avg", " §7avg").replace(" (longest", " §7(longest");
	}

	private FlowLayout editRow(Def def, String key, String value) {
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		row.gap(3);
		row.verticalAlignment(VerticalAlignment.CENTER);
		FlowLayout grip = UIContainers.horizontalFlow(Sizing.fixed(7), Sizing.fixed(9));
		grip.surface(EditHudScreen.GRIP);
		grip.cursorStyle(CursorStyle.MOVE);
		grip.tooltip(Component.literal("§7Drag to move"));
		row.child(grip);
		pbGrips.add(grip);
		FlowLayout name = new HoverFlow(Sizing.expand(), Sizing.content(), HoverFlow.ROW);
		name.gap(4);
		LabelComponent keyLabel = label("§7" + key);
		keyLabel.horizontalSizing(Sizing.expand());
		name.child(keyLabel);
		name.child(label(value == null ? "§8-" : value));
		applyTooltip(name, List.of(Component.literal("§7Click to change it")));
		name.mouseDown().subscribe((event, doubleClick) -> {
			draft = new PbDraft(def, def);
			rebuild(true);
			return true;
		});
		row.child(name);
		row.child(textButton("§c×", "Remove", () -> {
			List<Def> list = new ArrayList<>(pbList());
			if (list.remove(def)) savePbs(list);
			rebuild(true);
		}));
		pbRows.add(row);
		return row;
	}

	private FlowLayout textButton(String text, String tip, Runnable action) {
		FlowLayout button = new HoverFlow(Sizing.content(), Sizing.content(), HoverFlow.SMALL);
		button.padding(Insets.of(1, 1, 2, 2));
		button.child(label(text));
		applyTooltip(button, List.of(Component.literal("§7" + tip)));
		button.mouseDown().subscribe((event, doubleClick) -> {
			action.run();
			return true;
		});
		return button;
	}

	private void buildPbBuilder() {
		ShaftNames names = ShaftNames.defaults();
		Def def = draft.def();
		boolean streak = draft.stat.per() == Per.STREAK;
		FlowLayout dim = UIContainers.horizontalFlow(Sizing.fixed(width), Sizing.fixed(height));
		dim.positioning(Positioning.absolute(-PADDING, -PADDING));
		dim.surface(Surface.flat(0x99000000));
		dim.mouseDown().subscribe((event, doubleClick) -> {
			if (dropdown != null) dropdown = null;
			else draft = null;
			rebuild(true);
			return true;
		});
		root.child(dim);

		int cardWidth = Math.min(250, width - 2 * PADDING);
		FlowLayout card = UIContainers.verticalFlow(Sizing.fixed(cardWidth), Sizing.content());
		card.surface(OVERLAY);
		card.padding(Insets.of(6));
		card.gap(3);
		card.positioning(Positioning.relative(50, 35));
		// clicks on the card stay on it
		card.mouseDown().subscribe((event, doubleClick) -> {
			if (dropdown == null) return true;
			dropdown = null;
			rebuild(true);
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
			rebuild(true);
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
		label.horizontalSizing(Sizing.fixed(52));
		row.child(label);
		for (UIComponent child : children) row.child(child);
		return row;
	}

	private ButtonComponent choice(String text, boolean on, java.util.function.Consumer<ButtonComponent> action) {
		ButtonComponent button = PackButton.button(Component.literal((on ? "§e" : "§f") + text), action);
		button.verticalSizing(Sizing.fixed(14));
		button.horizontalSizing(Sizing.fixed(font.width(text) + 12));
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
		rebuild(true);
	}

	private void setBest(boolean lowest) {
		draft.best = lowest == draft.stat.lowestByDefault() ? null : lowest ? Def.LOWEST : Def.HIGHEST;
		keepTypedName();
		rebuild(true);
	}

	private void keepTypedName() {
		if (nameField != null) draft.name = nameField.getValue();
	}

	private void openDropdown(String which, UIComponent anchor) {
		keepTypedName();
		dropdown = which.equals(dropdown) ? null : which;
		dropdownAnchor = anchor;
		rebuild(true);
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
		int listWidth = Math.max(dropdownAnchor.width(), 90);
		int top = dropdownAnchor.y() + dropdownAnchor.height();
		int room = height - PADDING - top;
		// owo cant content-size a vertical scroll, so rows + headings measured
		int needed = items.children().stream().mapToInt(c -> c instanceof HoverFlow ? 11 : 13).sum();
		KeptScroll scroll = scroll(Sizing.fixed(listWidth), Sizing.fixed(Math.max(40, Math.min(needed, room - 4))), items);
		scroll.scrollbarThiccness(3);
		FlowLayout panel = UIContainers.verticalFlow(Sizing.content(), Sizing.content());
		panel.surface(OVERLAY);
		panel.padding(Insets.of(2));
		panel.child(scroll);
		panel.positioning(Positioning.absolute(dropdownAnchor.x() - PADDING, top - PADDING));
		return panel;
	}

	private FlowLayout dropdownItem(String text, boolean on, Runnable action) {
		FlowLayout item = new HoverFlow(Sizing.fill(100), Sizing.fixed(11), HoverFlow.SMALL);
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
		rebuild(true);
	}

	private void updatePreview() {
		if (previewLabel == null || draft == null) return;
		String typed = nameField == null ? draft.name : nameField.getValue();
		if (typed != null && typed.equals(previewName)) return;
		previewName = typed;
		draft.name = typed;
		Def def = draft.def();
		String key = PersonalBests.name(def, ShaftNames.defaults());
		if (previewResult == null) previewResult = pbResult(def);
		String value = pbText(def, previewResult, key);
		previewLabel.text(Component.literal("§7" + key + " " + (value == null ? "§8-" : value)));
	}

	private void savePb() {
		keepTypedName();
		List<Def> list = new ArrayList<>(pbList());
		Def def = draft.def();
		int index = draft.original == null ? -1 : list.indexOf(draft.original);
		if (index < 0) list.add(def);
		else list.set(index, def);
		savePbs(list);
		draft = null;
		dropdown = null;
		rebuild(true);
	}

	// drag a grip in edit mode, like the HUD editors lines
	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		if (editingPbs && draft == null && click.button() == 0) {
			for (int i = 0; i < pbGrips.size(); i++) {
				if (pbGrips.get(i).isInBoundingBox(click.x(), click.y())) {
					pbDrag = i;
					pbGap = i;
					return true;
				}
			}
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent drag, double dx, double dy) {
		if (pbDrag < 0) return super.mouseDragged(drag, dx, dy);
		pbGap = pbRows.size();
		for (int i = 0; i < pbRows.size(); i++) {
			FlowLayout row = pbRows.get(i);
			if (drag.y() < row.y() + row.height() / 2.0) {
				pbGap = i;
				break;
			}
		}
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent release) {
		if (pbDrag < 0) return super.mouseReleased(release);
		int from = pbDrag;
		int gap = pbGap;
		pbDrag = -1;
		if (!pbList().equals(pbShown)) {
			rebuild(true);
		} else if (gap != from && gap != from + 1) {
			List<Def> list = new ArrayList<>(pbList());
			Def moved = list.remove(from);
			list.add(gap > from ? gap - 1 : gap, moved);
			savePbs(list);
			rebuild(true);
		}
		return true;
	}

	private void drawPbDrag(GuiGraphicsExtractor graphics) {
		if (pbDrag < 0 || pbDrag >= pbRows.size()) return;
		FlowLayout from = pbRows.get(pbDrag);
		graphics.fill(from.x(), from.y(), from.x() + from.width(), from.y() + from.height(), 0x30FFFFFF);
		if (pbGap == pbDrag || pbGap == pbDrag + 1) return;
		FlowLayout last = pbRows.getLast();
		int y = pbGap < pbRows.size() ? pbRows.get(pbGap).y() : last.y() + last.height();
		graphics.fill(from.x(), y - 1, from.x() + from.width(), y + 1, 0xFFFFFF55);
	}

	private FlowLayout record(String key, String value, Runnable open, String target) {
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
			applyTooltip(row, List.of(Component.literal("§7Open " + (target == null ? "shaft" : target))));
			row.mouseDown().subscribe((event, doubleClick) -> {
				open.run();
				return true;
			});
		}
		return row;
	}

	private static String sessionName(SessionSummary session) {
		return "session from " + day(session.start()) + " " + clock(session.start());
	}

	private static double[] coinSteps() {
		double[] steps = new double[3 * 13];
		for (int i = 0; i < steps.length; i++) steps[i] = new double[] {1, 2, 5}[i % 3] * Math.pow(10, i / 3);
		return steps;
	}

	private record Bar(SessionSummary session, double value, int argb, List<Component> tooltip) {
	}

	private FlowLayout spawnChart(List<SessionSummary> oldestFirst, Long average, String legend) {
		List<Bar> bars = new ArrayList<>();
		for (SessionSummary session : oldestFirst) {
			if (session.averageSearchMillis() == null) continue;
			bars.add(new Bar(session, session.averageSearchMillis() / 1000.0, BAR_GREEN, List.of(
					Component.literal("§f" + day(session.start()) + " " + clock(session.start())),
					Component.literal("§7Avg spawn " + spawn(session.averageSearchMillis()) + " §7over " + session.timedSpawns() + " shafts"),
					Component.literal("§8Click to open"))));
		}
		return barChart(bars, journalSettings().spawnChart(), 0xFF55FF55, SPAWN_STEPS, seconds -> axisLabel(Math.round(seconds)),
				average == null ? null : average / 1000.0, 0xB0FFFF55, "§eYellow line§7: " + legend, ChartTips.spawn(oldestFirst),
				"§8No timed spawns yet");
	}

	private FlowLayout coinChart(List<SessionSummary> oldestFirst, Double average, boolean priced, String legend, long totalMillis) {
		List<Bar> bars = new ArrayList<>();
		List<ChartTips.SessionCoins> sessionCoins = new ArrayList<>();
		for (SessionSummary session : oldestFirst) {
			Double coins = sessionCoins(session);
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
			bars.add(new Bar(session, rate, Math.round(rate) < 0 ? BAR_RED : BAR_GOLD, tip));
		}
		String empty = priced ? "§8Needs a session of " + JournalStats.MIN_RATE_MILLIS / 60_000 + "+ minutes" : "§8No prices yet";
		return barChart(bars, journalSettings().coinChart(), 0xFFFFAA00, COIN_STEPS, JournalScreen::axisCoins, average, 0xB0FFFFFF,
				"§fWhite line§7: " + legend, ChartTips.coins(sessionCoins, totalMillis), empty);
	}

	private static String axisCoins(double coins) {
		return Coins.whole(coins);
	}

	// up to 4 steps, bars under 0 hang down from the 0 line
	private FlowLayout barChart(List<Bar> all, ChartType type, int lineArgb, double[] steps, DoubleFunction<String> axisText,
			Double average, int averageArgb, String legend, List<String> averageTip, String empty) {
		FlowLayout wrapper = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		if (all.isEmpty()) {
			wrapper.child(label(empty));
			return wrapper;
		}
		int chartHeight = Math.clamp(height / 3, 50, 110);
		double highest = Math.max(0, all.stream().mapToDouble(Bar::value).max().orElse(0));
		double lowest = Math.min(0, all.stream().mapToDouble(Bar::value).min().orElse(0));
		double step = steps[steps.length - 1];
		for (double candidate : steps) {
			if (Math.ceil(highest / candidate) + Math.ceil(-lowest / candidate) <= 4) {
				step = candidate;
				break;
			}
		}
		double top = Math.ceil(highest / step) * step;
		double bottom = -Math.ceil(-lowest / step) * step;
		if (top == bottom) top = step;
		int axisWidth = 0;
		for (double value = bottom; value <= top + step / 2; value += step) axisWidth = Math.max(axisWidth, font.width(axisText.apply(value)));
		axisWidth += 2;

		int plotWidth = Math.max(60, width - 2 * PADDING - 2 * GAP - width * STATS_LEFT / 100 - width * STATS_RIGHT / 100
				- 2 * COLUMN_PADDING - axisWidth - AXIS_GAP);
		// display choice: bars up to 24 wide, gap grows w/ them
		int barWidth = Math.clamp((plotWidth - 2) * 4 / 5 / all.size(), 3, 24);
		int barGap = Math.clamp(barWidth / 4, 2, 6);
		int maxBars = Math.max(1, (plotWidth - 2) / (barWidth + barGap));
		List<Bar> shown = all.size() > maxBars ? all.subList(all.size() - maxBars, all.size()) : all;

		int plotTop = 4;
		int base = chartHeight - 5;
		int plotHeight = base - plotTop;
		double span = top - bottom;
		DoubleUnaryOperator y = value -> base - (int) (plotHeight * (value - bottom) / span);
		FlowLayout chart = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(chartHeight));
		chart.gap(AXIS_GAP);
		StackLayout axis = UIContainers.stack(Sizing.fixed(axisWidth), Sizing.fixed(chartHeight));
		StackLayout plot = UIContainers.stack(Sizing.expand(), Sizing.fixed(chartHeight));
		for (double value = bottom; value <= top + step / 2; value += step) {
			int lineY = (int) y.applyAsDouble(value);
			LabelComponent tick = label("§8" + axisText.apply(value));
			tick.horizontalSizing(Sizing.fixed(axisWidth));
			tick.horizontalTextAlignment(HorizontalAlignment.RIGHT);
			tick.positioning(Positioning.absolute(0, lineY - 4));
			axis.child(tick);
			plot.child(line(lineY, Math.abs(value) < step / 2 ? 0x40FFFFFF : 0x18FFFFFF));
		}
		int zero = (int) y.applyAsDouble(0);
		if (type == ChartType.LINE) {
			int[] xs = new int[shown.size()];
			int[] ys = new int[shown.size()];
			int[] dots = new int[shown.size()];
			for (int i = 0; i < shown.size(); i++) {
				xs[i] = 2 + i * (barWidth + barGap) + barWidth / 2;
				ys[i] = (int) y.applyAsDouble(shown.get(i).value());
				dots[i] = 0xFF000000 | shown.get(i).argb();
			}
			LinePlot line = new LinePlot(xs, ys, dots, lineArgb);
			line.positioning(Positioning.absolute(0, 0));
			plot.child(line);
		}
		for (int i = 0; i < shown.size(); i++) {
			Bar bar = shown.get(i);
			int end = (int) y.applyAsDouble(bar.value());
			int barHeight = Math.max(1, Math.abs(zero - end));
			BoxComponent box;
			if (type == ChartType.LINE) {
				// invisible column, easy to hover
				box = new HoverFlow.Box(Sizing.fixed(barWidth + barGap), Sizing.fixed(base - plotTop + 1), HoverFlow.ROW);
				box.color(Color.ofArgb(0));
				box.positioning(Positioning.absolute(2 + i * (barWidth + barGap) - barGap / 2, plotTop));
			} else {
				box = new HoverFlow.Box(Sizing.fixed(barWidth), Sizing.fixed(barHeight), HoverFlow.SMALL);
				box.color(Color.ofArgb(bar.argb()));
				box.positioning(Positioning.absolute(2 + i * (barWidth + barGap), bar.value() < 0 ? zero + 1 : zero - barHeight));
			}
			box.fill(true);
			box.tooltip(bar.tooltip());
			box.mouseDown().subscribe((event, doubleClick) -> {
				openSession(bar.session());
				return true;
			});
			plot.child(box);
		}
		List<ClientTooltipComponent> averageTips = TooltipLines.of(averageTip);
		if (average != null) {
			int averageY = (int) y.applyAsDouble(Math.clamp(average, bottom, top));
			plot.child(line(averageY, averageArgb));
			if (!averageTips.isEmpty()) {
				// 1 px line is too thin to hover
				BoxComponent band = UIComponents.box(Sizing.fill(100), Sizing.fixed(5));
				band.color(Color.ofArgb(0));
				band.positioning(Positioning.absolute(0, averageY - 2));
				band.tooltip(averageTips);
				plot.child(band);
			}
		}
		chart.child(axis);
		chart.child(plot);
		wrapper.child(chart);

		StackLayout dates = UIContainers.stack(Sizing.fill(100), Sizing.fixed(10));
		LabelComponent first = label("§8" + day(shown.getFirst().session().start()));
		first.positioning(Positioning.absolute(axisWidth + AXIS_GAP + 2, 1));
		dates.child(first);
		if (shown.size() > 1) {
			String lastText = "§8" + day(shown.getLast().session().start());
			int lastX = axisWidth + AXIS_GAP + 2 + (shown.size() - 1) * (barWidth + barGap) + barWidth - font.width(lastText);
			int firstEnd = axisWidth + AXIS_GAP + 2 + font.width("§8" + day(shown.getFirst().session().start()));
			if (lastX > firstEnd + 6) {
				LabelComponent last = label(lastText);
				last.positioning(Positioning.absolute(lastX, 1));
				dates.child(last);
			}
		}
		wrapper.child(dates);
		if (average != null) {
			LabelComponent legendLabel = label(legend);
			if (!averageTips.isEmpty()) legendLabel.tooltip(averageTips);
			wrapper.child(legendLabel);
		}
		return wrapper;
	}

	private static BoxComponent line(int y, int argb) {
		BoxComponent line = UIComponents.box(Sizing.fill(100), Sizing.fixed(1));
		line.fill(true);
		line.color(Color.ofArgb(argb));
		line.positioning(Positioning.absolute(0, y));
		return line;
	}

	private static String axisLabel(long seconds) {
		if (seconds == 0) return "0";
		if (seconds < 60) return seconds + "s";
		return seconds % 60 == 0 ? seconds / 60 + "m" : seconds / 60 + "m " + seconds % 60 + "s";
	}

	private FlowLayout barRow(String name, double fraction, String value, int argb) {
		return barRow(name, fraction, "§f" + value, argb, 26);
	}

	private FlowLayout barRow(String name, double fraction, String value, int argb, int valueWidth) {
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(ROW_HEIGHT));
		row.verticalAlignment(VerticalAlignment.CENTER);
		row.gap(4);
		LabelComponent nameLabel = label("§f" + name);
		nameLabel.horizontalSizing(Sizing.fixed(62));
		row.child(nameLabel);
		FlowLayout track = UIContainers.horizontalFlow(Sizing.expand(), Sizing.fixed(5));
		track.surface(Surface.flat(0x14FFFFFF));
		BoxComponent fill = UIComponents.box(Sizing.fill((int) Math.round(Math.min(1, fraction) * 100)), Sizing.fill(100));
		fill.fill(true);
		fill.color(Color.ofArgb(argb));
		track.child(fill);
		row.child(track);
		LabelComponent valueLabel = label(value);
		valueLabel.horizontalSizing(Sizing.fixed(valueWidth));
		valueLabel.horizontalTextAlignment(HorizontalAlignment.RIGHT);
		row.child(valueLabel);
		return row;
	}

	private static List<Column> fit(List<Column> columns, int available) {
		List<Column> visible = new ArrayList<>(columns);
		while (visible.stream().mapToInt(Column::width).sum() + COLUMN_GAP * (visible.size() - 1) > available) {
			Column drop = visible.stream().filter(c -> c.dropOrder() > 0)
					.max(Comparator.comparingInt(Column::dropOrder)).orElse(null);
			if (drop == null) break;
			visible.remove(drop);
		}
		// wide screens (gui scale 2) spread the spare room, up to 2x each
		int base = visible.stream().mapToInt(Column::width).sum();
		int spare = available - base - COLUMN_GAP * (visible.size() - 1);
		if (spare <= 0) return visible;
		List<Column> spread = new ArrayList<>();
		for (Column c : visible) {
			int extra = Math.min(c.width(), spare * c.width() / base);
			spread.add(new Column(c.key(), c.title(), c.width(), c.right(), c.dropOrder(), extra));
		}
		return spread;
	}

	// sort null = headers not clickable
	private FlowLayout table(List<Column> columns, FlowLayout rows, Consumer<String> sort) {
		FlowLayout table = UIContainers.verticalFlow(Sizing.expand(), Sizing.fill(100));
		table.surface(PANEL);
		table.padding(Insets.of(5));
		FlowLayout header = tableRow();
		for (Column column : columns) header.child(sort == null ? cell(column, "§7" + column.title()) : sortHeader(column, sort));
		table.child(header);
		table.child(divider());
		KeptScroll scroll = scroll(Sizing.fill(100), Sizing.expand(), rows);
		scroll.scrollbarThiccness(3);
		scroll.margins(Insets.top(2));
		table.child(scroll);
		return table;
	}

	// active column: white + arrow, if the arrow fits
	private FlowLayout sortHeader(Column column, Consumer<String> sort) {
		FlowLayout cell = new HoverFlow(Sizing.fixed(column.total()), Sizing.fixed(ROW_HEIGHT), HoverFlow.ROW);
		cell.verticalAlignment(VerticalAlignment.CENTER);
		String text = "§7" + column.title();
		if (column.key().equals(shaftSort)) {
			String arrow = ascending(column.key()) ? "▲" : "▼";
			text = "§f" + column.title();
			for (String gap : List.of(" ", "")) {
				String withArrow = column.right() ? "§f" + arrow + gap + column.title() : "§f" + column.title() + gap + arrow;
				if (font.width(withArrow) <= column.total()) {
					text = withArrow;
					break;
				}
			}
		}
		cell.child(cell(column, text));
		cell.mouseDown().subscribe((event, doubleClick) -> {
			sort.accept(column.key());
			return true;
		});
		return cell;
	}

	// 1st click best/biggest first, 2nd flips, 3rd back to played order
	private void sortShafts(String key) {
		if (!key.equals(shaftSort)) {
			shaftSort = key;
			shaftSortFlipped = false;
		} else if (!shaftSortFlipped) {
			shaftSortFlipped = true;
		} else {
			shaftSort = null;
		}
		rebuild(true);
	}

	// shaft names a to z + fastest spawn first, rest biggest first
	private static boolean ascending(String key) {
		boolean first = key.equals("shaft") || key.equals("spawn");
		return first != shaftSortFlipped;
	}

	// nulls last both ways, ties in played order
	private static int[] shaftOrder(List<JournalEntry> shafts, List<Double> coins) {
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < shafts.size(); i++) order.add(i);
		String key = shaftSort;
		if (key != null) {
			boolean ascending = ascending(key);
			order.sort((a, b) -> {
				Comparable<?> ka = sortKey(key, a, shafts.get(a), coins.get(a));
				Comparable<?> kb = sortKey(key, b, shafts.get(b), coins.get(b));
				if (ka == null || kb == null) return ka == kb ? Integer.compare(a, b) : ka == null ? 1 : -1;
				int compared = compare(ka, kb);
				if (!ascending) compared = -compared;
				return compared != 0 ? compared : Integer.compare(a, b);
			});
		}
		return order.stream().mapToInt(Integer::intValue).toArray();
	}

	@SuppressWarnings("unchecked")
	private static int compare(Comparable<?> a, Comparable<?> b) {
		return ((Comparable<Object>) a).compareTo(b);
	}

	private static Comparable<?> sortKey(String key, int index, JournalEntry entry, Double coins) {
		return switch (key) {
			case "shaft" -> entry.shaftId();
			case "spawn" -> entry.searchMillis();
			case "inside" -> entry.durationMillis();
			case "corpses" -> {
				int looted = 0;
				int total = 0;
				for (CorpseCount count : JournalStats.corpses(entry).values()) {
					looted += count.looted();
					total += count.total();
				}
				yield looted * 1000L + total;
			}
			case "powder" -> JournalStats.powder(entry);
			case "mined" -> entry.gems() != null ? (Comparable<?>) entry.gems().fineEquivalent()
					: entry.metal() != null ? entry.metal().in(Surveyor.MONEY.settings().metalTierUsed().tier()) : null;
			case "coins" -> coins;
			case "campfire" -> campfires(entry);
			default -> index;
		};
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
		int tier = Surveyor.MONEY.settings().metalTierUsed().tier();
		LabelComponent cell;
		String full;
		if (entry.gems() != null) {
			String color = minedColor(entry.gems().gem());
			cell = cell(column, color + Format.decimal(entry.gems().fineEquivalent()));
			full = color + minedGems(entry);
		} else if (entry.metal() != null) {
			String color = minedColor(entry.metal().metal());
			cell = cell(column, color + entry.metal().amount(tier));
			full = color + entry.metal().text(tier);
		} else {
			return cell(column, "§8-");
		}
		cell.tooltip(List.of(Component.literal(full)));
		return cell;
	}

	// the gem or metals hud colour, white if none
	private static String minedColor(String name) {
		Character color = ShaftNames.defaults().hudColorByName(name);
		return "§" + (color == null ? 'f' : color);
	}

	private static String minedGems(JournalEntry entry) {
		return Format.decimal(entry.gems().fineEquivalent()) + " Fine " + entry.gems().gem();
	}

	private static LabelComponent cell(Column column, String text) {
		LabelComponent label = label(text);
		label.horizontalSizing(Sizing.fixed(column.total()));
		if (column.right()) label.horizontalTextAlignment(HorizontalAlignment.RIGHT);
		return label;
	}

	private static FlowLayout barCell(Column column, Long millis, long slowest) {
		FlowLayout cell = UIContainers.horizontalFlow(Sizing.fixed(column.total()), Sizing.content());
		cell.verticalAlignment(VerticalAlignment.CENTER);
		cell.gap(3);
		if (millis == null) {
			cell.child(label("§8-"));
			return cell;
		}
		LabelComponent text = label("§f" + Format.duration(millis));
		text.horizontalSizing(Sizing.fixed(column.width() - SPAWN_BAR - 3));
		cell.child(text);
		int barMax = SPAWN_BAR + column.extra();
		BoxComponent bar = UIComponents.box(Sizing.fixed(Math.max(1, (int) (barMax * millis / Math.max(1, slowest)))), Sizing.fixed(3));
		bar.fill(true);
		bar.color(GREEN);
		cell.child(bar);
		return cell;
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
		scroll.padding(Insets.of(COLUMN_PADDING));
		scroll.scrollbarThiccness(3);
		return scroll;
	}

	private static FlowLayout panelWith(UIComponent child, Sizing width, Sizing height) {
		FlowLayout panel = UIContainers.verticalFlow(width, height);
		panel.surface(PANEL);
		panel.padding(Insets.of(8));
		panel.child(child);
		return panel;
	}

	// gaps over Breaks.BREAK_MILLIS w/o mining, minus their 1st minutes. none = no tooltip
	private static BoxComponent divider() {
		return UIComponents.box(Sizing.fill(100), Sizing.fixed(1)).color(Color.ofArgb(0x1FFFFFFF)).fill(true);
	}

	private static FlowLayout tableRow() {
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(ROW_HEIGHT));
		row.padding(Insets.horizontal(2));
		row.gap(COLUMN_GAP);
		row.verticalAlignment(VerticalAlignment.CENTER);
		return row;
	}

	private static <T extends UIComponent> T breakTooltip(T component, long breakMillis) {
		if (breakMillis <= 0) return component;
		component.tooltip(List.of(Component.literal("§7Breaks left out: §f" + Format.duration(breakMillis)),
				Component.literal("§8After " + Breaks.BREAK_MILLIS / 60_000 + " minutes without mining, time stops counting per hour")));
		return component;
	}

	private static FlowLayout keyValue(String key, String value) {
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		row.gap(6);
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

	private static FlowLayout section(String title) {
		FlowLayout section = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		section.margins(Insets.vertical(4));
		section.gap(3);
		section.child(divider());
		section.child(wrapped("§7" + title));
		return section;
	}

	private static UIComponent spacer(int height) {
		return UIComponents.box(Sizing.fixed(1), Sizing.fixed(height)).color(Color.ofArgb(0));
	}

	private static LabelComponent label(String text) {
		return new SafeLabel(Component.literal(text));
	}

	private static LabelComponent wrapped(String text) {
		LabelComponent label = label(text);
		label.horizontalSizing(Sizing.fill(100));
		return label;
	}

	private static String corpses(JournalEntry entry) {
		List<String> parts = new ArrayList<>();
		for (Map.Entry<String, CorpseCount> e : JournalStats.corpses(entry).entrySet()) {
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

	private static String clock(long millis) {
		return CLOCK.format(Instant.ofEpochMilli(millis).atZone(ZONE));
	}

	private static String time(long millis) {
		return TIME.format(Instant.ofEpochMilli(millis).atZone(ZONE));
	}

	private static LocalDate date(long millis) {
		return Instant.ofEpochMilli(millis).atZone(ZONE).toLocalDate();
	}

	private static String day(long millis) {
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
