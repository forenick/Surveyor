package dev.skysurveyor.calc;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;

import dev.skysurveyor.Surveyor;
import dev.skysurveyor.data.CorpseType;
import dev.skysurveyor.data.Patches;
import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.journal.BreakEntry;
import dev.skysurveyor.journal.JournalEntry;
import dev.skysurveyor.journal.JournalStats;
import dev.skysurveyor.journal.JournalStats.SessionSummary;
import dev.skysurveyor.journal.LiveJournal;
import dev.skysurveyor.journal.MiningEntry;
import dev.skysurveyor.journal.StatsRange;
import dev.skysurveyor.money.LiveMoney;
import dev.skysurveyor.money.PriceRules;
import dev.skysurveyor.money.ShaftValue;
import dev.skysurveyor.settings.CalculatorSettings;
import dev.skysurveyor.settings.CalculatorSettings.KeyLoot;
import dev.skysurveyor.util.Guard;

// redone on journal, price or typed changes. built on its own thread, rest client thread
public final class LiveGuide {
	// display choice
	private static final long AGE_MILLIS = 3_600_000;
	// item id, rec 2026-11-22_14-07-41 (held item)
	private static final String GLOSSY = "GLOSSY_GEMSTONE";

	private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "Surveyor guide");
		thread.setDaemon(true);
		return thread;
	});
	private final Executor client;
	// client thread. what the last build came from, null = not built
	private Inputs built;
	private long builtAt;
	private boolean pending;
	private Built last;
	// goes up when a build lands
	private int version;
	// last build threw
	private boolean failed;
	// worker only. LiveMoney's cache is client thread only
	private final Map<JournalEntry, ShaftValue> values = new IdentityHashMap<>();
	private final Map<MiningEntry, Double> outside = new IdentityHashMap<>();
	private int valuedAt = -1;
	private JournalEntry valuedFirst;
	private MiningEntry valuedFirstMining;

	// client = runs on the client thread
	public LiveGuide(Executor client) {
		this.client = client;
	}

	// config.json since 0.27.0-alpha.39
	public static StatsRange range() {
		return Surveyor.SETTINGS.get().calculator().range();
	}

	public static void range(StatsRange value) {
		Surveyor.SETTINGS.change(s -> s.withCalculator(s.calculator().withRange(value)));
	}

	// last built, starts a rebuild if stale. null = not built yet
	public YourNumbers numbers() {
		refresh();
		return cachedNumbers();
	}

	// last built, starts a rebuild if stale. null = no prices or not built yet
	public ShaftModel.Guide guide() {
		refresh();
		return cachedGuide();
	}

	// no rebuild, for the render thread. hud tick keeps it fresh. null = not built
	public YourNumbers cachedNumbers() {
		return last == null ? null : last.numbers;
	}

	public ShaftModel.Guide cachedGuide() {
		return last == null ? null : last.guide;
	}

	// no rebuild. newest mined shafts vs the guide
	public PastShafts.Check cachedPast() {
		return last == null ? PastShafts.Check.NONE : last.past;
	}

	// no rebuild. gems w/ a mined shaft, any fiesta or price
	public Set<String> cachedMeasured() {
		return last == null ? Set.of() : last.measured;
	}

	// no rebuild. DONE = nothing missing or not built
	public GuideProgress cachedProgress() {
		return last == null ? GuideProgress.DONE : last.progress;
	}

	// screens redraw when it moves
	public int version() {
		return version;
	}

	// the log has why. next input change retries
	public boolean failed() {
		return failed;
	}

	// breaks split sessions too
	private record Inputs(int entries, JournalEntry firstEntry, int mining, MiningEntry firstMining, int breaks, BreakEntry firstBreak,
			int changes, Map<String, Double> typed, KeyLoot keyLoot, StatsRange range, Fiesta.State fiesta, int minedShafts) {
	}

	// all read on the client thread, worker only reads this
	private record Snapshot(Inputs inputs, long millis, List<JournalEntry> entries, List<MiningEntry> mining, List<BreakEntry> breaks,
			PriceRules rules, long patchStart) {
	}

	private record Built(YourNumbers numbers, ShaftModel.Guide guide, PastShafts.Check past, Set<String> measured, GuideProgress progress) {
	}

	// cheap if nothing changed, the build runs on the worker
	public void refresh() {
		// lands first, then the next call checks again
		if (pending) return;
		LiveJournal journal = Surveyor.JOURNAL;
		CalculatorSettings settings = Surveyor.SETTINGS.get().calculator();
		Inputs now = new Inputs(journal.entryCount(), journal.firstEntry(), journal.miningCount(), journal.firstMining(), journal.breakCount(),
				journal.firstBreak(), Surveyor.MONEY.changes(), settings.typed(), settings.keyLoot(), settings.range(), Surveyor.fiestaState(),
				settings.minedShafts().count());
		long millis = System.currentTimeMillis();
		// 7 days drops old sessions w/o a journal change
		boolean aged = settings.range() == StatsRange.LAST_7_DAYS && millis - builtAt >= AGE_MILLIS;
		if (!aged && now.equals(built)) return;
		Patches.Patch patch = Patches.defaults().newest();
		Snapshot snapshot = new Snapshot(now, millis, journal.entries(), journal.mining(0), journal.breaks(), Surveyor.MONEY.rules(),
				patch == null ? 0 : patch.startMillis());
		LiveMoney money = Surveyor.MONEY;
		pending = true;
		try {
			CompletableFuture.supplyAsync(() -> Guard.get("guide build", () -> build(snapshot, money), () -> null), worker)
					.whenComplete((result, error) -> client.execute(() -> Guard.run("guide landed", () -> land(snapshot, result))));
		} catch (RejectedExecutionException e) {
			// closing
			pending = false;
		}
	}

	// client thread. null = it threw, marked done anyway, else it retries every tick
	private void land(Snapshot snapshot, Built result) {
		pending = false;
		built = snapshot.inputs();
		builtAt = snapshot.millis();
		failed = result == null;
		if (result != null) last = result;
		version++;
	}

	// worker thread
	private Built build(Snapshot s, LiveMoney money) {
		Inputs in = s.inputs();
		JournalEntry first = s.entries().isEmpty() ? null : s.entries().getFirst();
		MiningEntry firstMining = s.mining().isEmpty() ? null : s.mining().getFirst();
		// new file or prices = all again
		if (in.changes() != valuedAt || first != valuedFirst || firstMining != valuedFirstMining) {
			values.clear();
			outside.clear();
			valuedAt = in.changes();
			valuedFirst = first;
			valuedFirstMining = firstMining;
		}
		List<SessionSummary> sessions = JournalStats.sessions(s.entries(), s.mining(), s.breaks());
		PriceRules rules = s.rules();
		// containsKey bc null is kept too
		Learner.Input input = new Learner.Input(in.range().sessions(sessions, s.millis()), entry -> {
			if (values.containsKey(entry)) return values.get(entry);
			ShaftValue value = money.compute(entry);
			values.put(entry, value);
			return value;
		}, entry -> {
			if (outside.containsKey(entry)) return outside.get(entry);
			Double value = money.compute(entry);
			outside.put(entry, value);
			return value;
		}, in.typed(), s.patchStart(), TextPatterns.defaults(), in.fiesta(), rules == null ? null : rules.sell(GLOSSY), s.entries(),
				in.minedShafts());
		YourNumbers numbers = Learner.learn(input, GameData.defaults());
		GuideProgress progress = GuideProgress.of(input, numbers, GameData.defaults());
		if (rules != null) numbers = withKeyLoot(numbers, rules, in.keyLoot());
		ShaftModel.Guide guide = run(numbers, rules);
		// every shaft: layouts keep their own numbers whatever the range
		Set<String> measured = s.entries().stream().filter(e -> Learner.mined(e, GameData.defaults())).map(JournalEntry::shaftType)
				.collect(Collectors.toUnmodifiableSet());
		// not all in values yet, the learner only prices the range
		return new Built(numbers, guide, PastShafts.check(s.entries(), entry -> values.computeIfAbsent(entry, money::compute), guide, in.fiesta(),
				GameData.defaults()), measured, progress);
	}

	// null = no prices yet. key loot already picked (withKeyLoot)
	static ShaftModel.Guide run(YourNumbers numbers, PriceRules rules) {
		if (rules == null) return null;
		Map<String, Double> prices = new HashMap<>();
		for (GameData.Gem gem : GameData.defaults().gems()) {
			Double perFine = rules.gemPerFine(gem.name());
			if (perFine != null) prices.put(gem.code(), perFine);
		}
		if (prices.isEmpty()) return null;
		Map<String, Double> keys = new HashMap<>();
		for (CorpseType type : CorpseType.KEY_LOOT) {
			Double key = rules.key(CorpseType.key(type.label()));
			if (key != null) keys.put(type.label(), key);
		}
		return ShaftModel.run(numbers, prices, rules.campfireCost(), keys, GameData.defaults());
	}

	// wiki table, own avg or none. typed always wins
	static YourNumbers withKeyLoot(YourNumbers numbers, PriceRules rules, KeyLoot choice) {
		YourNumbers.Num own = numbers.num(NumberId.KEY_LOOT);
		if (own.source() == YourNumbers.Source.SET) return numbers;
		if (choice == KeyLoot.OWN && own.source() == YourNumbers.Source.TRACKED) return numbers;
		double value = choice == KeyLoot.PRISTINE_ONLY ? 0 : wiki(rules, numbers.get(NumberId.EXTRA_ROLL));
		Map<NumberId, YourNumbers.Num> map = new EnumMap<>(numbers.numbers());
		map.put(NumberId.KEY_LOOT, new YourNumbers.Num(value, YourNumbers.Source.CALCULATED, own.tracked(), own.basis(), own.basisText()));
		return new YourNumbers(map, numbers.spawnGem(), numbers.fiesta(), numbers.layouts());
	}

	// gems + crystals by tier + crystal settings, rest by id
	static double wiki(PriceRules rules, double extraRoll) {
		CorpseLootTable.Table table = CorpseLootTable.defaults().of(CorpseType.UMBER.label());
		return CorpseLootTable.value(table, row -> {
			Double each = rules.each(row.name());
			return each != null ? each : rules.sell(row.id());
		}, extraRoll).worth();
	}

	public void close() {
		worker.shutdownNow();
	}
}
