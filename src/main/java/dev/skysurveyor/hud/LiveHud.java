package dev.skysurveyor.hud;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import com.google.gson.JsonParseException;
import net.minecraft.SharedConstants;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.skysurveyor.Surveyor;
import dev.skysurveyor.data.ShaftNames;
import dev.skysurveyor.detection.LiveTracker;
import dev.skysurveyor.detection.Shaft;
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
import dev.skysurveyor.journal.MiningEntry;
import dev.skysurveyor.journal.PersonalBests;
import dev.skysurveyor.journal.ToastText;
import dev.skysurveyor.money.LiveMoney;
import dev.skysurveyor.money.MoneyStats;
import dev.skysurveyor.settings.LiveSettings;
import dev.skysurveyor.settings.SummarySettings;
import dev.skysurveyor.util.DataFolder;
import dev.skysurveyor.util.SafeWrite;

public final class LiveHud {
	private static final Logger LOGGER = LoggerFactory.getLogger("Surveyor");
	private static final int REFRESH_TICKS = 4;
	// shafts today never get near this, keeps the scan short
	private static final int TODAY_LOOKBACK = 2000;
	// display choice
	private static final long TOAST_FADE = 300;
	private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
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
	private long toastAt;
	private HudData data = HudData.live(SessionTotals.NONE, Totals.NONE, null, null, null, 0, ShaftNames.defaults());
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
	private final RunningTotal<JournalEntry> allTimeCoins;
	// mining outside shafts
	private Double outsideSession;
	private int outsideCount = -1;
	private MiningEntry outsideFirst;
	private final RunningTotal<MiningEntry> outsideAllTime;
	// type code -> shafts since, from saved shafts. redone when one is saved
	private Map<String, Long> savedDry = Map.of();
	private int dryEntries = -1;
	private JournalEntry dryFirst;
	// pbs over the whole journal, done on the io thread. key -> result, client thread only
	private Map<String, PersonalBests.Result> pbResults = Map.of();
	private List<PersonalBests.Def> pbList;
	private int pbEntries = -1;
	private int pbChanges = -1;
	private JournalEntry pbFirst;
	private MiningEntry pbFirstMining;
	private int pbMining = -1;
	private int pbBreaks = -1;
	private boolean pbPending;
	// io thread only. kept until prices or settings change, new shafts valued on top
	private final Map<JournalEntry, Double> pbNets = new java.util.IdentityHashMap<>();
	private int pbNetsChanges = -1;
	// io thread only, each mining entry valued once per price change, for pbs + all-time outside coins
	// null values too, so no HashMap.computeIfAbsent
	private final Map<MiningEntry, Double> ioOutside = new java.util.IdentityHashMap<>();
	private int ioOutsideChanges = -1;
	private long todayCount;
	private LocalDate todayDate;
	private int todayEntries = -1;
	private List<Prepared> prepared;
	private int preparedWidth;
	private int preparedHeight;
	// single thread = read before first save, saves stay in order
	private final ExecutorService io = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "Surveyor HUD");
		thread.setDaemon(true);
		return thread;
	});
	private boolean loaded;
	// io thread only. null = no backup, ".bad" unreadable, ".old" older format
	private String backupSuffix;
	private boolean inMines;
	private boolean inShaft;
	private Path file;
	private int tick;
	private final TickRate tickRate = new TickRate();
	private final PingTimer pingTimer = new PingTimer();

	private record Prepared(Box box, List<String> lines, HudRenderer.Bounds bounds) {
	}

	public LiveHud(LiveTracker tracker, LiveJournal journal, LiveMoney money, LiveSettings settings) {
		this.tracker = tracker;
		this.journal = journal;
		this.money = money;
		this.settings = settings;
		allTimeCoins = new RunningTotal<>("all-time coins", journal::entryCount, journal::firstEntry, journal::entries,
				list -> MoneyStats.coins(list, money::value), list -> MoneyStats.coins(list, money::compute));
		outsideAllTime = new RunningTotal<>("all-time outside coins", journal::miningCount, journal::firstMining, journal::mining,
				list -> MoneyStats.outside(list, money::value), list -> MoneyStats.outside(list, this::ioOutside));
	}

	public void start(Minecraft client) {
		file = DataFolder.in(client.gameDirectory.toPath()).resolve("hud.json");
		CompletableFuture.supplyAsync(this::read, io).thenAccept(read -> client.execute(() -> loaded(read)));
	}

	private void loaded(HudLayout read) {
		loaded = true;
		if (read == null) return;
		// editor changes made before the load win
		if (!unchanged()) {
			LOGGER.warn("Loaded {} after the HUD was changed in the editor; keeping the changes", file);
			return;
		}
		layout = read;
		prepared = null;
	}

	private boolean unchanged() {
		return layout.toJson().equals(DEFAULT_JSON);
	}

	private HudLayout read() {
		if (!Files.exists(file)) return null;
		String json;
		try {
			json = Files.readString(file, StandardCharsets.UTF_8);
		} catch (IOException e) {
			// maybe just locked, keep it, first save backs it up
			backupSuffix = ".bad";
			LOGGER.warn("Could not read {}, using the default HUD", file, e);
			return null;
		}
		try {
			HudLayout layout = HudLayout.fromJson(json);
			// older format or repaired hand edit, keep the original
			if (!layout.toJson().equals(json)) backupSuffix = ".old";
			return layout;
		} catch (JsonParseException e) {
			// else the next save overwrites a hand edit
			try {
				Path backup = Files.move(file, backupPath(".bad"));
				LOGGER.warn("Could not read {}, moved it to {} and using the default HUD", file, backup, e);
			} catch (IOException moveFailed) {
				backupSuffix = ".bad";
				LOGGER.warn("Could not read {} or move it away, using the default HUD", file, e);
			}
			return null;
		}
	}

	private Path backupPath(String suffix) {
		return file.resolveSibling("hud.json." + LocalDateTime.now().format(BACKUP_TIME) + suffix);
	}

	// client thread, after changing the layout
	public void save() {
		prepared = null;
		// still loading, saving defaults would overwrite the file
		if (file == null || (!loaded && unchanged())) return;
		String json = layout.toJson();
		Runnable write = () -> {
			try {
				if (backupSuffix != null && Files.exists(file)) {
					Files.copy(file, backupPath(backupSuffix));
					backupSuffix = null;
				}
				SafeWrite.write(file, json);
			} catch (IOException e) {
				LOGGER.error("Could not save the HUD to {}", file, e);
			}
		};
		try {
			io.execute(write);
		} catch (RejectedExecutionException e) {
			// game closing w/ editor open, io thread already gone
			write.run();
		}
	}

	public void close() {
		io.shutdown();
		try {
			if (!io.awaitTermination(5, TimeUnit.SECONDS)) LOGGER.warn("HUD save did not finish in time");
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
		allTimeCoins.update(client, changes);
		int miningCount = journal.miningCount();
		if (newSession || revalue || miningCount != outsideCount || journal.firstMining() != outsideFirst) {
			outsideSession = MoneyStats.outside(journal.sessionMining(session), money::value);
			outsideCount = miningCount;
			outsideFirst = journal.firstMining();
		}
		outsideAllTime.update(client, changes);
		Totals thisShaft = shaft != null ? Totals.of(shaft, patterns) : lastShaft;
		Double shaftCoins = shaft != null ? MoneyStats.net(money.live(shaft)) : lastShaftCoins;
		int metalTier = money.settings().metalTierUsed().tier();
		List<BreakEntry> breaks = journal.sessionBreaks(session, now);
		long breakMillis = session == null ? 0 : Breaks.counted(breaks, session.id(), now);
		data = HudData.live(totals.withBreaks(breaks), allTime, thisShaft, journal.spawnTimerMillis(now), shaft, now, ShaftNames.defaults(),
				metalTier)
				.lastShaft(lastEntry, ShaftNames.defaults())
				.money(new HudData.Money(shaftCoins, sessionCoins, allTimeCoins.total, allTimeCoins.upTo, outsideSession,
						outsideAllTime.total,
						session == null ? null : session.id(), breakMillis))
				.extra(extra(session, shaft, now))
				.game(game(client, now));
		prepared = null;
	}

	private HudData.Extra extra(Journal.Session session, Shaft shaft, long now) {
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
		return new HudData.Extra(dry, sessionMillis, since, todayCount, pbTexts(inType));
	}

	private Map<String, String> pbTexts(String inType) {
		List<PersonalBests.Def> list = settings.get().personalBests().list();
		HudValue.personalBests(list);
		JournalEntry first = journal.firstEntry();
		MiningEntry firstMining = journal.firstMining();
		int changes = money.changes();
		// outside coins + breaks count in session pbs too
		if (!pbPending && (!list.equals(pbList) || journal.entryCount() != pbEntries || changes != pbChanges || first != pbFirst
				|| firstMining != pbFirstMining || journal.miningCount() != pbMining || journal.breakCount() != pbBreaks)) {
			List<JournalEntry> all = journal.entries();
			pbList = list;
			pbEntries = all.size();
			pbChanges = changes;
			pbFirst = first;
			pbFirstMining = firstMining;
			pbMining = journal.miningCount();
			pbBreaks = journal.breakCount();
			computePbs(list, all, journal.mining(0), journal.breaks(), changes);
		}
		int tier = money.settings().metalTierUsed().tier();
		Map<String, String> texts = new HashMap<>();
		for (PersonalBests.Def def : list) {
			PersonalBests.Result result = pbResults.get(PersonalBests.key(def));
			if (result == null) continue;
			String text = HudData.pbText(def, PersonalBests.live(def, result, inType), tier);
			if (text != null) texts.put(PersonalBests.key(def), text);
		}
		return texts;
	}

	// each shaft valued once, uncached compute is safe on the io thread
	private void computePbs(List<PersonalBests.Def> list, List<JournalEntry> all, List<MiningEntry> mining, List<BreakEntry> breaks,
			int changes) {
		Minecraft client = Minecraft.getInstance();
		try {
			pbPending = true;
			CompletableFuture.supplyAsync(() -> {
				if (changes != pbNetsChanges || pbNets.size() > all.size()) {
					pbNets.clear();
					pbNetsChanges = changes;
				}
				for (JournalEntry entry : all) pbNets.computeIfAbsent(entry, e -> MoneyStats.net(money.compute(e)));
				List<JournalStats.SessionSummary> sessions = JournalStats.sessions(all, mining, breaks);
				PersonalBests.Coins coins = PersonalBests.Coins.of(pbNets::get, this::ioOutside);
				Map<String, PersonalBests.Result> results = new HashMap<>();
				for (PersonalBests.Def def : list) results.put(PersonalBests.key(def), PersonalBests.best(def, all, sessions, coins));
				return results;
			}, io).whenComplete((results, error) -> client.execute(() -> {
				pbPending = false;
				if (error != null) LOGGER.warn("Could not work out personal bests", error);
				else pbResults = results;
			}));
		} catch (RejectedExecutionException e) {
			// closing
			pbPending = false;
		}
	}

	private Double ioOutside(MiningEntry entry) {
		int changes = money.changes();
		if (changes != ioOutsideChanges) {
			ioOutside.clear();
			ioOutsideChanges = changes;
		}
		if (!ioOutside.containsKey(entry)) ioOutside.put(entry, money.compute(entry));
		return ioOutside.get(entry);
	}

	// list [0, upTo) at prices at, from first on. new ones added here, all again on the io thread (thousands = a hitch)
	private final class RunningTotal<E> {
		private final String what;
		private final IntSupplier count;
		private final Supplier<E> first;
		private final IntFunction<List<E>> from;
		// client thread, cached values
		private final Function<List<E>, Double> cached;
		// io thread, uncached
		private final Function<List<E>, Double> compute;
		private Double total;
		private int upTo;
		private int at = -1;
		private E firstSeen;
		private boolean pending;

		RunningTotal(String what, IntSupplier count, Supplier<E> first, IntFunction<List<E>> from, Function<List<E>, Double> cached,
				Function<List<E>, Double> compute) {
			this.what = what;
			this.count = count;
			this.first = first;
			this.from = from;
			this.cached = cached;
			this.compute = compute;
		}

		void update(Minecraft client, int changes) {
			if (at == changes && firstSeen == first.get()) {
				int now = count.getAsInt();
				if (upTo < now) {
					total = MoneyStats.plus(total, cached.apply(from.apply(upTo)));
					upTo = now;
				}
				return;
			}
			if (pending) return;
			List<E> all = from.apply(0);
			try {
				pending = true;
				CompletableFuture.supplyAsync(() -> compute.apply(all), io).whenComplete((coins, error) -> client.execute(() -> {
					pending = false;
					if (error != null) LOGGER.warn("Could not add up {}", what, error);
					else total = coins;
					// on error too, else it retries every refresh
					upTo = all.size();
					at = changes;
					firstSeen = all.isEmpty() ? null : all.getFirst();
				}));
			} catch (RejectedExecutionException e) {
				// closing
				pending = false;
			}
		}
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
		toastAt = Util.getMillis();
	}

	public void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		// editor draws the boxes itself
		if (client.gui.hud.isHidden() || client.gui.screen() instanceof EditHudScreen) return;
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		if (prepared == null || width != preparedWidth || height != preparedHeight) {
			prepared = prepare(client, width, height);
			preparedWidth = width;
			preparedHeight = height;
		}
		for (Prepared box : prepared) HudRenderer.draw(graphics, client.font, box.box(), box.lines(), box.bounds());
		drawToast(graphics, client, width, height);
	}

	private void drawToast(GuiGraphicsExtractor graphics, Minecraft client, int width, int height) {
		if (toast == null) return;
		SummarySettings summary = settings.get().summary();
		long shown = Math.round(summary.seconds() * 1000 * client.gui.toastManager().getNotificationDisplayTimeMultiplier());
		long age = Util.getMillis() - toastAt;
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
			List<String> lines = HudBoxText.lines(box, data, false);
			if (lines.isEmpty()) continue;
			result.add(new Prepared(box, lines, HudRenderer.bounds(client.font, box, lines, width, height)));
		}
		return result;
	}
}
