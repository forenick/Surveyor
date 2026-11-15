package dev.skysurveyor.money;

import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import dev.skysurveyor.detection.Shaft;
import dev.skysurveyor.detection.ShaftEvent;
import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.journal.JournalEntry;
import dev.skysurveyor.journal.MiningEntry;
import dev.skysurveyor.settings.LiveSettings;
import dev.skysurveyor.settings.MoneySettings;
import dev.skysurveyor.settings.MoneySettings.OldShafts;

// prices refresh on shaft entry + journal open, not on a timer, so no traffic when not shafting
public final class LiveMoney {
	// display choice, older = refresh failed or still running, not that days prices
	static final long SNAPSHOT_MAX_AGE = 60 * 60_000L;
	public static final long BAZAAR_MAX_AGE = PriceFeed.BAZAAR_MAX_AGE;
	public static final long BINS_MAX_AGE = PriceFeed.BINS_MAX_AGE;

	private final ExecutorService io = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "Surveyor prices");
		thread.setDaemon(true);
		return thread;
	});
	private final AtomicBoolean refreshing = new AtomicBoolean();
	private final AtomicInteger changes = new AtomicInteger();
	private final LiveSettings config;
	private PriceFeed feed;
	// client thread only, saved entries only (live ones would pile up). cleared when changes() moves
	private final Map<JournalEntry, ShaftValue> values = new IdentityHashMap<>();
	private final Map<MiningEntry, List<ShaftValue.Line>> miningValues = new IdentityHashMap<>();
	private final Map<JournalEntry, ShaftValue> untaxedValues = new IdentityHashMap<>();
	private final Map<MiningEntry, List<ShaftValue.Line>> untaxedMining = new IdentityHashMap<>();
	private int valuesAt = -1;
	// client thread only, live value until the shaft or prices change
	private Shaft liveShaft;
	private int liveVersion;
	private int liveAt = -1;
	private ShaftValue liveValue;

	public LiveMoney(LiveSettings config) {
		this.config = config;
		// page changes, a hand edit is seen by refresh()
		config.onChange((before, after) -> {
			if (!after.money().equals(before.money())) changes.incrementAndGet();
		});
	}

	// after config.start, its file loads on this io thread
	public void start(Path folder, String version) {
		feed = new PriceFeed(folder, version);
		run(() -> {
			config.reloadIfChanged();
			feed.loadCache();
			changes.incrementAndGet();
		});
	}

	public void refresh() {
		if (feed == null || !refreshing.compareAndSet(false, true)) return;
		run(() -> {
			try {
				MoneySettings settings = settings();
				PriceBook book = feed.book();
				config.reloadIfChanged();
				long now = System.currentTimeMillis();
				if (feed.stale(now)) feed.fetchStale(now);
				if (!settings().equals(settings) || feed.book() != book) changes.incrementAndGet();
			} finally {
				refreshing.set(false);
			}
		});
	}

	public void onEvent(ShaftEvent event) {
		if (event instanceof ShaftEvent.ShaftEntered) refresh();
	}

	// goes up when prices or settings change, open screens redraw on it
	public int changes() {
		return changes.get();
	}

	public boolean refreshing() {
		return refreshing.get();
	}

	public MoneySettings settings() {
		return config.get().money();
	}

	public PriceBook book() {
		return feed == null ? PriceBook.EMPTY : feed.book();
	}

	public ItemIds ids() {
		return ItemIds.bundled(feed == null ? Map.of() : feed.names());
	}

	public JournalEntry withPrices(JournalEntry entry) {
		PriceBook book = book();
		if (!freshForSnapshot(book, entry.endedAt())) return entry.withPrices(Map.of());
		return entry.withPrices(ShaftValue.snapshot(entry, book::get, settings(), ids(), TextPatterns.defaults()));
	}

	public MiningEntry withPrices(MiningEntry entry) {
		PriceBook book = book();
		if (!freshForSnapshot(book, entry.at())) return entry.withPrices(Map.of());
		return entry.withPrices(MiningValue.snapshot(entry, book::get, settings(), ids(), TextPatterns.defaults()));
	}

	// no bazaar = npc/bin only, would undercut gems for good
	static boolean freshForSnapshot(PriceBook book, long end) {
		return book.hasBazaar() && end - book.bazaarAt() <= SNAPSHOT_MAX_AGE;
	}

	// null = no prices at all yet. saved entries only, cached
	public ShaftValue value(JournalEntry entry) {
		return cached(values, entry, e -> compute(e, settings()));
	}

	// same w/o bazaar tax, for the tax line. cached
	public ShaftValue untaxed(JournalEntry entry) {
		MoneySettings untaxed = untaxedSettings();
		return untaxed == null ? value(entry) : cached(untaxedValues, entry, e -> compute(e, untaxed));
	}

	public List<ShaftValue.Line> untaxedLines(MiningEntry entry) {
		MoneySettings untaxed = untaxedSettings();
		return untaxed == null ? lines(entry) : cached(untaxedMining, entry, e -> computeLines(e, untaxed));
	}

	// false = untaxed values are the same, no tax line to work out
	public boolean taxed() {
		return untaxedSettings() != null;
	}

	// null = no tax to take off
	private MoneySettings untaxedSettings() {
		MoneySettings settings = settings();
		return settings.isIronman() || settings.bazaarTax() == 0 ? null : settings.withBazaarTax(0);
	}

	// containsKey bc null = no prices is cached too
	private <K, V> V cached(Map<K, V> map, K key, Function<K, V> compute) {
		clearIfChanged();
		if (map.containsKey(key)) return map.get(key);
		V value = compute.apply(key);
		map.put(key, value);
		return value;
	}

	private void clearIfChanged() {
		int now = changes.get();
		if (now == valuesAt) return;
		values.clear();
		miningValues.clear();
		untaxedValues.clear();
		untaxedMining.clear();
		valuesAt = now;
	}

	// null = no prices at all yet. saved entries only, cached
	public Double value(MiningEntry entry) {
		return total(lines(entry));
	}

	// per item, null = no prices at all yet. saved entries only, cached
	public List<ShaftValue.Line> lines(MiningEntry entry) {
		return cached(miningValues, entry, e -> computeLines(e, settings()));
	}

	// uncached, any thread
	public Double compute(MiningEntry entry) {
		return total(computeLines(entry, settings()));
	}

	private static Double total(List<ShaftValue.Line> lines) {
		return lines == null ? null : MiningValue.total(lines);
	}

	private List<ShaftValue.Line> computeLines(MiningEntry entry, MoneySettings settings) {
		ShaftValue.Prices prices = prices(entry.prices(), settings);
		return prices == null ? null : MiningValue.lines(entry, prices, settings, ids(), TextPatterns.defaults());
	}

	// shaft still going, todays prices
	public ShaftValue live(Shaft shaft) {
		int now = changes.get();
		if (shaft != liveShaft || shaft.version() != liveVersion || now != liveAt) {
			liveValue = compute(JournalEntry.of(shaft, 0, null, TextPatterns.defaults()));
			liveShaft = shaft;
			liveVersion = shaft.version();
			liveAt = now;
		}
		return liveValue;
	}

	// uncached, any thread
	public ShaftValue compute(JournalEntry entry) {
		return compute(entry, settings());
	}

	private ShaftValue compute(JournalEntry entry, MoneySettings settings) {
		ShaftValue.Prices prices = prices(entry.prices(), settings);
		return prices == null ? null : ShaftValue.of(entry, prices, settings, ids(), TextPatterns.defaults());
	}

	// null = no prices at all yet
	private ShaftValue.Prices prices(Map<String, ItemPrice> saved, MoneySettings settings) {
		PriceBook book = book();
		boolean useSaved = usesSavedPrices(saved, settings);
		if (book.isEmpty() && !useSaved) return null;
		return useSaved ? ShaftValue.layered(saved, book::get) : book::get;
	}

	public boolean usesSavedPrices(JournalEntry entry) {
		return usesSavedPrices(entry.prices(), settings());
	}

	private static boolean usesSavedPrices(Map<String, ItemPrice> saved, MoneySettings settings) {
		return settings.oldShafts() == OldShafts.THAT_DAY && saved != null && !saved.isEmpty();
	}

	// shutdown not shutdownNow, queued config saves must still run
	public void close() {
		if (feed != null) feed.close();
		io.shutdown();
		try {
			if (!io.awaitTermination(5, TimeUnit.SECONDS)) io.shutdownNow();
		} catch (InterruptedException e) {
			io.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}

	// config saves queue here too, behind a price fetch
	public void run(Runnable task) {
		try {
			io.execute(task);
		} catch (RejectedExecutionException e) {
			// closing
			refreshing.set(false);
		}
	}
}
