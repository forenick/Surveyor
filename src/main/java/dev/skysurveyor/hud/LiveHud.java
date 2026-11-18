package dev.skysurveyor.hud;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import net.minecraft.SharedConstants;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.skysurveyor.Surveyor;
import dev.skysurveyor.data.ItemNames;
import dev.skysurveyor.data.ShaftNames;
import dev.skysurveyor.detection.LiveTracker;
import dev.skysurveyor.detection.Shaft;
import dev.skysurveyor.detection.TabListParser;
import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.hud.HudData.SessionTotals;
import dev.skysurveyor.hud.HudData.Totals;
import dev.skysurveyor.hud.HudLayout.Box;
import dev.skysurveyor.journal.BreakEntry;
import dev.skysurveyor.journal.Breaks;
import dev.skysurveyor.journal.Journal;
import dev.skysurveyor.journal.JournalEntry;
import dev.skysurveyor.journal.JournalStats;
import dev.skysurveyor.journal.LiveJournal;
import dev.skysurveyor.journal.MetalYield;
import dev.skysurveyor.journal.MiningEntry;
import dev.skysurveyor.journal.PersonalBests;
import dev.skysurveyor.journal.ToastText;
import dev.skysurveyor.money.DropList;
import dev.skysurveyor.money.LiveMoney;
import dev.skysurveyor.money.MoneyStats;
import dev.skysurveyor.money.ShaftValue;
import dev.skysurveyor.screen.ModScreen;
import dev.skysurveyor.settings.LiveSettings;
import dev.skysurveyor.settings.SummarySettings;
import dev.skysurveyor.util.DataFolder;

public final class LiveHud {
	private static final Logger LOGGER = LoggerFactory.getLogger("Surveyor");
	private static final int REFRESH_TICKS = 4;
	private static final Identifier HYPIXEL_GEM = Identifier.parse("hypixel_skyblock:item/collections/gemstone/jasper/fine_jasper_gem");
	// shafts today never get near this, keeps the scan short
	private static final int TODAY_LOOKBACK = 2000;
	// display choice
	private static final long TOAST_FADE = 300;
	private static final String DEFAULT_JSON = HudLayout.firstStart().toJson();
	// locale's own 12h/24h
	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT);
	private static final String MOD_VERSION = Surveyor.modVersion();

	private final LiveTracker tracker;
	private final LiveJournal journal;
	private final LiveMoney money;
	private final LiveSettings settings;
	private HudLayout layout = HudLayout.firstStart();
	// movable shaft toast, client thread only
	private ToastText toast;
	private ToastClock toastClock;
	private HudData data = HudData.live(SessionTotals.NONE, Totals.NONE, null, null, null, 0, ShaftNames.defaults(),
			MetalYield.ENCHANTED);
	// new object per saved shaft, compared by identity
	private Journal.Session totalsSession;
	private SessionTotals totals = SessionTotals.NONE;
	private int totalsEntries;
	private JournalEntry totalsFirst;
	private Totals allTime = Totals.NONE;
	private Totals lastShaft;
	private JournalEntry lastEntry;
	private int moneyChanges = -1;
	private List<JournalEntry> sessionEntries = List.of();
	private Double sessionCoins;
	private Double lastShaftCoins;
	// mining outside shafts
	private Double outsideSession;
	private int outsideCount = -1;
	private MiningEntry outsideFirst;
	// the shaft ur in, redone when it changes
	private Shaft totalsShaft;
	private int totalsVersion;
	private Totals shaftTotals;
	// type code -> shafts since, from saved shafts. redone when one is saved
	private Map<String, Long> savedDry = Map.of();
	private int dryEntries = -1;
	private JournalEntry dryFirst;
	// avg spawn chart, redone when a shaft is saved
	private List<HudData.Spawn> recentSpawns = List.of();
	private int spawnEntries = -1;
	private JournalEntry spawnFirst;
	// all-time coins, drops + pbs, summed on sumsIo
	private final HudSums sums;
	// a drawn box read a pb at the last prepare, pbs only redone then
	private boolean pbsDrawn;
	private long todayCount;
	private LocalDate todayDate;
	private int todayEntries = -1;
	private List<Prepared> prepared;
	private int preparedWidth;
	private int preparedHeight;
	// single thread = read before first save, saves stay in order. own thread so a save never waits behind sums
	private final ExecutorService fileIo = thread("Surveyor HUD file");
	private final ExecutorService sumsIo = thread("Surveyor HUD");
	private boolean inMines;
	private boolean inShaft;
	private HudFile file;
	private int tick;
	private final TickRate tickRate = new TickRate();
	private final TabListParser tabList = new TabListParser(TextPatterns.defaults());
	// client thread. null = no widget, or a new world till the next tab list
	private TabListParser.Pity pity;
	private final PingTimer pingTimer = new PingTimer();
	// drops line, redone when an input changes + a line wants it. client thread
	private final Cached sessionDrops = new Cached();
	private final Cached shaftDrops = new Cached();
	private final Cached allTimeDrops = new Cached();

	private record Prepared(Box box, HudRenderer.Measured measured, HudRenderer.Bounds bounds) {
	}

	public LiveHud(LiveTracker tracker, LiveJournal journal, LiveMoney money, LiveSettings settings) {
		this.tracker = tracker;
		this.journal = journal;
		this.money = money;
		this.settings = settings;
		sums = new HudSums(new HudSums.Journal(journal::entryCount, journal::firstEntry, journal::entries, journal::miningCount,
				journal::firstMining, journal::mining, journal::breakCount, journal::breaks),
				new HudSums.Money(money::value, money::lines, money::compute, money::computeLines), sumsIo,
				task -> Minecraft.getInstance().execute(task));
	}

	private static ExecutorService thread(String name) {
		return Executors.newSingleThreadExecutor(task -> {
			Thread thread = new Thread(task, name);
			thread.setDaemon(true);
			return thread;
		});
	}

	public void onTabList(List<String> lines) {
		pity = tabList.pity(lines);
	}

	// old island's number never lingers after a warp
	public void onWorldChange() {
		pity = null;
	}

	public void start(Minecraft client) {
		file = new HudFile(DataFolder.in(client.gameDirectory.toPath()).resolve("hud.json"), fileIo);
		file.load().thenAccept(read -> client.execute(() -> loaded(read)));
	}

	private void loaded(HudLayout read) {
		file.loaded();
		if (read == null) return;
		// editor changes made before the load win
		if (!unchanged()) {
			LOGGER.warn("Loaded {} after the HUD was changed in the editor; keeping the changes", file.path());
			return;
		}
		layout = read;
		prepared = null;
	}

	private boolean unchanged() {
		return layout.toJson().equals(DEFAULT_JSON);
	}

	// client thread, after changing the layout
	public void save() {
		prepared = null;
		if (file != null) file.save(layout.toJson(), this::unchanged);
	}

	// sums are thrown away, saves still run
	public void close() {
		sumsIo.shutdownNow();
		fileIo.shutdown();
		try {
			if (!fileIo.awaitTermination(5, TimeUnit.SECONDS)) LOGGER.warn("HUD save did not finish in time");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	public HudLayout layout() {
		return layout;
	}

	// client thread only, call save() after
	public void setLayout(HudLayout layout) {
		this.layout = layout;
		prepared = null;
	}

	public HudData data() {
		return data;
	}

	public boolean visible(Box box) {
		return box.visible(inMines, inShaft);
	}

	// mixins, client thread
	public void onServerPing(int id) {
		tickRate.onPing(id, System.currentTimeMillis());
	}

	public void onServerChange() {
		tickRate.reset(System.currentTimeMillis());
		pingTimer.reset();
	}

	public void onBlockSent(int sequence) {
		pingTimer.onSent(sequence, System.currentTimeMillis());
	}

	public void onBlockAck(int sequence) {
		pingTimer.onAck(sequence, System.currentTimeMillis());
	}

	public void onTick(Minecraft client) {
		if (++tick % REFRESH_TICKS != 0) return;
		// gem icon glyphs come w/ hypixels pack, its models say if its loaded. models null until the loading overlay is gone
		if (client.gui.overlay() == null) {
			ItemNames.iconsLoaded(!(client.getModelManager().getItemModel(HYPIXEL_GEM) instanceof MissingItemModel));
		}
		long now = System.currentTimeMillis();
		Shaft shaft = tracker.currentShaft();
		inShaft = shaft != null;
		inMines = tracker.inMiningArea();
		Journal.Session session = journal.activeSession(now);
		TextPatterns patterns = TextPatterns.defaults();
		// read once, the io thread bumps it. new prices or settings = coins again, totals stay
		int changes = money.changes();
		boolean revalue = changes != moneyChanges;
		moneyChanges = changes;
		boolean newSession = session != totalsSession;
		if (newSession) {
			totalsSession = session;
			sessionEntries = journal.sessionEntries(session);
			totals = SessionTotals.of(sessionEntries, patterns);
		}
		if (newSession || revalue) sessionCoins = MoneyStats.coins(sessionEntries, money::value);
		JournalEntry last = lastEntry;
		if (journal.entryCount() != totalsEntries) {
			// only new ones, all again if the file loaded in front of them
			JournalEntry first = journal.firstEntry();
			boolean again = first != totalsFirst;
			List<JournalEntry> added = journal.entries(again ? 0 : totalsEntries);
			allTime = (again ? Totals.NONE : allTime).plus(Totals.of(added, patterns));
			totalsFirst = first;
			totalsEntries = journal.entryCount();
			if (!added.isEmpty()) {
				lastEntry = added.getLast();
				lastShaft = Totals.of(List.of(lastEntry), patterns);
			}
		}
		if (lastEntry != last || revalue) lastShaftCoins = lastEntry == null ? null : MoneyStats.net(money.value(lastEntry));
		sums.update(changes);
		int miningCount = journal.miningCount();
		if (newSession || revalue || miningCount != outsideCount || journal.firstMining() != outsideFirst) {
			outsideSession = MoneyStats.outside(journal.sessionMining(session), money::value);
			outsideCount = miningCount;
			outsideFirst = journal.firstMining();
		}
		Totals thisShaft = shaft != null ? shaftTotals(shaft, patterns) : lastShaft;
		ShaftValue live = shaft != null ? money.live(shaft) : null;
		Double shaftCoins = shaft != null ? MoneyStats.net(live) : lastShaftCoins;
		int metalTier = money.settings().metalTierUsed().tier();
		List<BreakEntry> breaks = journal.sessionBreaks(session, now);
		long breakMillis = session == null ? 0 : Breaks.counted(breaks, session.id(), now);
		HudSums.AllTime coins = sums.allTime();
		// editor shows every pb in its value list
		boolean wantPbs = pbsDrawn || client.gui.screen() instanceof EditHudScreen;
		data = HudData.live(totals.withBreaks(breaks), allTime, thisShaft, journal.spawnTimerMillis(now), shaft, now, ShaftNames.defaults(),
				metalTier)
				.lastShaft(lastEntry, ShaftNames.defaults())
				.money(new HudData.Money(shaftCoins, sessionCoins, coins.coins(), coins.shafts(), outsideSession, coins.outside(),
						session == null ? null : session.id(), breakMillis, shaft != null ? shaftCoins : null))
				.extra(extra(session, shaft, now, changes, wantPbs, metalTier))
				.pity(pity)
				.recentSpawns(recentSpawns())
				.game(game(client, now));
		drops(client, session, shaft, live, changes, miningCount);
		prepared = null;
	}

	// cold changes dont bump the version, totals dont use it
	private Totals shaftTotals(Shaft shaft, TextPatterns patterns) {
		if (shaft != totalsShaft || shaft.version() != totalsVersion || shaftTotals == null) {
			shaftTotals = Totals.of(shaft, patterns);
			totalsShaft = shaft;
			totalsVersion = shaft.version();
		}
		return shaftTotals;
	}

	private void drops(Minecraft client, Journal.Session session, Shaft shaft, ShaftValue live, int changes, int miningCount) {
		boolean wantSession = false;
		boolean wantShaft = false;
		boolean wantAllTime = false;
		// editor shows every box + line
		boolean editing = client.gui.screen() instanceof EditHudScreen;
		for (Box box : layout.boxes) {
			if (!editing && !visible(box)) continue;
			for (HudLayout.Line line : box.lines) {
				HudValue value = line.hudValue();
				if (value == null || !value.multiRow() || !editing && !line.shown) continue;
				switch (line.range()) {
					case SHAFT -> wantShaft = true;
					case ALL_TIME -> wantAllTime = true;
					default -> wantSession = true;
				}
			}
		}
		if (wantSession) {
			data.drops(HudValue.Range.SESSION, sessionDrops.get(() -> {
				DropList.Lines in = new DropList.Lines();
				for (JournalEntry entry : sessionEntries) in.shaft(money.value(entry));
				if (shaft != null) in.shaft(live);
				for (MiningEntry entry : journal.sessionMining(session)) in.outside(money.lines(entry));
				return list(in);
			}, sessionEntries, session, shaft, live, changes, miningCount, journal.firstMining()));
		}
		if (wantShaft) {
			data.drops(HudValue.Range.SHAFT, shaftDrops.get(() -> {
				DropList.Lines in = new DropList.Lines();
				if (shaft != null) in.shaft(live);
				else if (lastEntry != null) in.shaft(money.value(lastEntry));
				return list(in);
			}, shaft, live, lastEntry, changes));
		}
		if (wantAllTime) {
			DropList.Lines shafts = sums.allTime().shaftLines();
			DropList.Lines outside = sums.allTime().outsideLines();
			// shaft ur in counts like in all-time coins
			if (shafts != null && outside != null) {
				data.drops(HudValue.Range.ALL_TIME, allTimeDrops.get(() -> {
					DropList.Lines in = shafts.plus(outside);
					return list(shaft == null ? in : in.plus(new DropList.Lines().shaft(live)));
				}, shafts, outside, shaft, live, changes));
			}
		}
	}

	private DropList list(DropList.Lines in) {
		return in.list(money.settings().isIronman(), TextPatterns.defaults());
	}

	// inputs compared by identity, numbers by value
	private static final class Cached {
		private Object[] inputs;
		private DropList list;

		DropList get(Supplier<DropList> compute, Object... now) {
			if (inputs == null || !same(inputs, now)) {
				list = compute.get();
				inputs = now;
			}
			return list;
		}

		private static boolean same(Object[] a, Object[] b) {
			for (int i = 0; i < a.length; i++) {
				if (a[i] != b[i] && !(a[i] instanceof Number && a[i].equals(b[i]))) return false;
			}
			return true;
		}
	}

	private List<HudData.Spawn> recentSpawns() {
		JournalEntry first = journal.firstEntry();
		if (journal.entryCount() != spawnEntries || first != spawnFirst) {
			recentSpawns = HudData.lastSpawns(journal.entries());
			spawnEntries = journal.entryCount();
			spawnFirst = first;
		}
		return recentSpawns;
	}

	private HudData.Extra extra(Journal.Session session, Shaft shaft, long now, int changes, boolean wantPbs, int metalTier) {
		ShaftNames names = ShaftNames.defaults();
		JournalEntry first = journal.firstEntry();
		if (journal.entryCount() != dryEntries || first != dryFirst) {
			savedDry = JournalStats.drystreaks(journal.entries(), names.codes());
			dryEntries = journal.entryCount();
			dryFirst = first;
		}
		String inType = shaft == null || shaft.id() == null ? null : shaft.id().type();
		Map<String, Long> dry = new HashMap<>();
		savedDry.forEach((type, count) -> dry.put(HudValue.targetTag(names.name(type)), PersonalBests.liveDry(count, type, shaft != null, inType)));
		Long sessionMillis = session == null ? null : Math.max(0, now - session.id());
		Long since = shaft != null || lastEntry == null ? null : Math.max(0, now - lastEntry.endedAt());
		LocalDate today = LocalDate.now();
		if (!today.equals(todayDate) || todayEntries != journal.entryCount()) {
			todayCount = JournalStats.shaftsOn(journal.entries(Math.max(0, journal.entryCount() - TODAY_LOOKBACK)), today, ZoneId.systemDefault());
			todayDate = today;
			todayEntries = journal.entryCount();
		}
		List<PersonalBests.Def> pbs = settings.get().personalBests().list();
		HudValue.personalBests(pbs);
		sums.updatePbs(pbs, changes, wantPbs);
		return new HudData.Extra(dry, sessionMillis, since, todayCount, sums.pbTexts(pbs, inType, metalTier));
	}

	private HudData.Game game(Minecraft client, long now) {
		// java 20+ puts a narrow nbsp before AM/PM, not every font has it
		String clock = LocalTime.now().format(CLOCK).replace('\u202f', ' ').replace('\u00a0', ' ');
		return new HudData.Game(client.getFps(), pingTimer.ping(now), tickRate.tps(now), clock, MOD_VERSION,
				SharedConstants.getCurrentVersion().name());
	}

	// client thread. top right = vanillas toast slot, movable = drawn w/ the hud
	public void showToast(ToastText text) {
		Minecraft client = Minecraft.getInstance();
		SummarySettings summary = settings.get().summary();
		if (summary.position() == SummarySettings.Position.TOP_RIGHT) {
			client.gui.toastManager().addToast(new ShaftToast(client.font, text, summary.style(), summary.seconds()));
			return;
		}
		toast = text;
		toastClock = new ToastClock();
		toastClock.tick(Util.getMillis(), false);
	}

	// toast hides + pauses while one is open
	public static boolean modScreenOpen() {
		return Minecraft.getInstance().gui.screen() instanceof ModScreen;
	}

	public void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		boolean modScreen = modScreenOpen();
		// before the early return, so mod screen time doesnt count
		if (toast != null) toastClock.tick(Util.getMillis(), modScreen);
		// editor draws the boxes itself
		if (client.gui.hud.isHidden() || client.gui.screen() instanceof EditHudScreen) return;
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		if (prepared == null || width != preparedWidth || height != preparedHeight) {
			prepared = prepare(client, width, height);
			preparedWidth = width;
			preparedHeight = height;
		}
		for (Prepared box : prepared) HudRenderer.draw(graphics, client.font, box.box(), box.measured(), box.bounds());
		if (!modScreen) drawToast(graphics, client, width, height);
	}

	private void drawToast(GuiGraphicsExtractor graphics, Minecraft client, int width, int height) {
		if (toast == null) return;
		SummarySettings summary = settings.get().summary();
		long shown = Math.round(summary.seconds() * 1000 * client.gui.toastManager().getNotificationDisplayTimeMultiplier());
		long age = toastClock.age();
		if (!summary.movableToast() || age >= shown + TOAST_FADE) {
			toast = null;
			return;
		}
		float alpha = age < TOAST_FADE ? (float) age / TOAST_FADE : age > shown ? 1 - (float) (age - shown) / TOAST_FADE : 1;
		Box box = layout.toastOrDefault();
		ToastRenderer.Size size = ToastRenderer.size(client.font, toast, summary.style());
		HudRenderer.Bounds bounds = ToastRenderer.bounds(size, box, width, height);
		// text under ~4% alpha isnt drawn anyway
		ToastRenderer.draw(graphics, client.font, toast, summary.style(), size, box, bounds, Math.max(0.05f, alpha));
	}

	private List<Prepared> prepare(Minecraft client, int width, int height) {
		List<Prepared> result = new ArrayList<>();
		for (Box box : layout.boxes) {
			if (!visible(box)) continue;
			List<HudRow> rows = HudBoxText.rows(box, data, false);
			if (rows.isEmpty()) continue;
			HudRenderer.Measured measured = HudRenderer.measure(client.font, box, rows);
			result.add(new Prepared(box, measured, HudRenderer.bounds(measured, box, width, height)));
		}
		pbsDrawn = data.pbRead();
		return result;
	}
}
