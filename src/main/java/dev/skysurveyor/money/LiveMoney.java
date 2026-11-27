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
import java.util.function.Consumer;
import java.util.function.Function;

import com.google.gson.JsonObject;

import dev.skysurveyor.data.ItemNames;
import dev.skysurveyor.detection.Shaft;
import dev.skysurveyor.detection.ShaftEvent;
import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.journal.JournalEntry;
import dev.skysurveyor.journal.MiningEntry;
import dev.skysurveyor.settings.LiveSettings;
import dev.skysurveyor.settings.MoneySettings;
import dev.skysurveyor.settings.MoneySettings.OldShafts;
import dev.skysurveyor.settings.MoneySettings.SellPrice;
import dev.skysurveyor.util.Guard;

// refresh on shaft entry + journal open, no timer, no traffic when idle
public final class LiveMoney {
	// display choice, older = failed or running, not that days prices
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
	// io thread
	private volatile Consumer<JsonObject> election = json -> {
	};
	// read by 3 threads, careful
	private volatile PriceFeed feed;
	// client thread only, saved entries only. cleared when changes() moves
	private final Map<JournalEntry, ShaftValue> values = new IdentityHashMap<>();
	private final Map<MiningEntry, List<ShaftValue.Line>> miningValues = new IdentityHashMap<>();
	private final Map<JournalEntry, ShaftValue> untaxedValues = new IdentityHashMap<>();
	private final Map<MiningEntry, List<ShaftValue.Line>> untaxedMining = new IdentityHashMap<>();
	private int valuesAt = -1;
	// client thread only
	private Shaft liveShaft;
	private int liveVersion;
	private int liveAt = -1;
	private ShaftValue liveValue;

	public LiveMoney(LiveSettings config) {
		this.config = config;
		// hand edits seen by refresh()
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
			ItemNames.use(itemNames());
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
				Map<String, String> tiers = feed.tiers();
				if (feed.stale(now)) feed.fetchStale(now);
				if (feed.tiers() != tiers) ItemNames.use(itemNames());
				if (!settings().equals(settings) || feed.book() != book || feed.tiers() != tiers) changes.incrementAndGet();
				JsonObject mayor = feed.fetchElection(now);
				if (mayor != null) Guard.run("election", () -> election.accept(mayor));
			} finally {
				refreshing.set(false);
			}
		});
	}

	// fetched w/ prices, called on the io thread
	public void onElection(Consumer<JsonObject> listener) {
		election = listener;
	}

	public void onEvent(ShaftEvent event) {
		if (event instanceof ShaftEvent.ShaftEntered) refresh();
	}

	// goes up on price or setting change, screens redraw on it
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

	// null = none yet
	public PriceRules rules() {
		PriceBook book = book();
		return book.isEmpty() ? null : new PriceRules(book::get, settings(), ids(), TextPatterns.defaults());
	}

	public ItemIds ids() {
		return ItemIds.bundled(feed == null ? Map.of() : feed.names());
	}

	private ItemNames itemNames() {
		ItemIds ids = ids();
		Map<String, String> tiers = feed.tiers();
		return new ItemNames(name -> {
			String id = ids.id(name);
			return id == null ? null : tiers.get(id);
		});
	}

	public JournalEntry withPrices(JournalEntry entry) {
		ShaftValue.Prices prices = forSnapshot(book(), entry.endedAt());
		if (prices == null) return entry.withPrices(Map.of());
		return entry.withPrices(ShaftValue.snapshot(entry, prices, settings(), ids(), TextPatterns.defaults()));
	}

	public MiningEntry withPrices(MiningEntry entry) {
		ShaftValue.Prices prices = forSnapshot(book(), entry.at());
		if (prices == null) return entry.withPrices(Map.of());
		return entry.withPrices(MiningValue.snapshot(entry, prices, settings(), ids(), TextPatterns.defaults()));
	}

	// no bazaar = npc/bin only, would undercut gems for good
	static boolean freshForSnapshot(PriceBook book, long end) {
		return book.hasBazaar() && end - book.bazaarAt() <= SNAPSHOT_MAX_AGE;
	}

	// null = nothing saved. old bins = bazaar items only, rest keep todays price
	// not just bins dropped: get() would save an npc only price for an ah item
	static ShaftValue.Prices forSnapshot(PriceBook book, long end) {
		if (!freshForSnapshot(book, end)) return null;
		if (end - book.binsAt() <= SNAPSHOT_MAX_AGE) return book::get;
		return id -> {
			ItemPrice price = book.get(id);
			return price == null || !price.onBazaar() ? null : new ItemPrice(price.buyOrder(), price.sellOffer(), null, price.npcSell());
		};
	}

	// null = no prices yet. saved entries only, cached
	public ShaftValue value(JournalEntry entry) {
		return cached(values, entry, e -> compute(e, settings()));
	}

	// cached
	public ShaftValue untaxed(JournalEntry entry) {
		MoneySettings untaxed = untaxedSettings();
		return untaxed == null ? value(entry) : cached(untaxedValues, entry, e -> compute(e, untaxed));
	}

	public List<ShaftValue.Line> untaxedLines(MiningEntry entry) {
		MoneySettings untaxed = untaxedSettings();
		return untaxed == null ? lines(entry) : cached(untaxedMining, entry, e -> computeLines(e, untaxed));
	}

	// false = no tax line
	public boolean taxed() {
		return untaxedSettings() != null;
	}

	// null = no tax
	private MoneySettings untaxedSettings() {
		MoneySettings settings = settings();
		// w/o tax bazaar can beat npc, no honest tax line
		return settings.isIronman() || settings.bazaarTax() == 0 || settings.sellPrice() == SellPrice.BEST ? null
				: settings.withBazaarTax(0);
	}

	// containsKey bc null is cached too
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

	// null = no prices yet. saved entries only, cached
	public Double value(MiningEntry entry) {
		return total(lines(entry));
	}

	// null = no prices yet. saved entries only, cached
	public List<ShaftValue.Line> lines(MiningEntry entry) {
		return cached(miningValues, entry, e -> computeLines(e, settings()));
	}

	// uncached, any thread
	public Double compute(MiningEntry entry) {
		return total(computeLines(entry, settings()));
	}

	// uncached, any thread
	public List<ShaftValue.Line> computeLines(MiningEntry entry) {
		return computeLines(entry, settings());
	}

	private static Double total(List<ShaftValue.Line> lines) {
		return lines == null ? null : MiningValue.total(lines);
	}

	private List<ShaftValue.Line> computeLines(MiningEntry entry, MoneySettings settings) {
		ShaftValue.Prices prices = prices(entry.prices(), settings);
		return prices == null ? null : MiningValue.lines(entry, prices, settings, ids(), TextPatterns.defaults());
	}

	// todays prices
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

	// null = no prices yet
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

	// not shutdownNow, queued config saves must run
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

	// config saves queue here too, behind a fetch
	public void run(Runnable task) {
		try {
			io.execute(task);
		} catch (RejectedExecutionException e) {
			refreshing.set(false);
		}
	}
}
