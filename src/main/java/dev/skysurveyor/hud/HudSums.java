package dev.skysurveyor.hud;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.skysurveyor.journal.BreakEntry;
import dev.skysurveyor.journal.JournalEntry;
import dev.skysurveyor.journal.JournalStats;
import dev.skysurveyor.journal.MiningEntry;
import dev.skysurveyor.journal.PersonalBests;
import dev.skysurveyor.money.DropList;
import dev.skysurveyor.money.MiningValue;
import dev.skysurveyor.money.MoneyStats;
import dev.skysurveyor.money.ShaftValue;

// whole journal sums: all-time coins + drops, pbs. summed on io, new entries added on the client thread
public final class HudSums {
	private static final Logger LOGGER = LoggerFactory.getLogger("Surveyor");

	public record Journal(IntSupplier count, Supplier<JournalEntry> first, IntFunction<List<JournalEntry>> from,
			IntSupplier miningCount, Supplier<MiningEntry> firstMining, IntFunction<List<MiningEntry>> miningFrom,
			IntSupplier breakCount, Supplier<List<BreakEntry>> breaks) {
	}

	// value + lines on the client thread, cached. compute + computeLines uncached, any thread
	public record Money(Function<JournalEntry, ShaftValue> value, Function<MiningEntry, List<ShaftValue.Line>> lines,
			Function<JournalEntry, ShaftValue> compute, Function<MiningEntry, List<ShaftValue.Line>> computeLines) {
	}

	// coins null = no prices. shafts = how many saved shafts coins has. lines null = not summed yet
	public record AllTime(Double coins, int shafts, Double outside, int mining, DropList.Lines shaftLines,
			DropList.Lines outsideLines) {
		public static final AllTime NONE = new AllTime(null, 0, null, 0, null, null);

		AllTime plus(AllTime added) {
			return new AllTime(MoneyStats.plus(coins, added.coins), shafts + added.shafts, MoneyStats.plus(outside, added.outside),
					mining + added.mining, plusLines(shaftLines, added.shaftLines), plusLines(outsideLines, added.outsideLines));
		}

		// null = not worked out yet
		private static DropList.Lines plusLines(DropList.Lines a, DropList.Lines b) {
			if (a == null) return b;
			return b == null ? a : a.plus(b).summed();
		}
	}

	private final Journal journal;
	private final Money money;
	private final Executor io;
	private final Executor client;
	// client thread. at prices `at`, from these first entries on
	private AllTime allTime = AllTime.NONE;
	private int at = -1;
	private JournalEntry firstShaft;
	private MiningEntry firstMining;
	private boolean pending;
	// pbs, client thread. key -> result
	private Map<String, PersonalBests.Result> pbResults = Map.of();
	private List<PersonalBests.Def> pbList;
	private int pbEntries = -1;
	private int pbChanges = -1;
	private JournalEntry pbFirst;
	private MiningEntry pbFirstMining;
	private int pbMining = -1;
	private int pbBreaks = -1;
	private boolean pbPending;
	// io thread only. each saved entry valued once per price or settings change, shared by sums + pbs
	// null values too (no prices), so containsKey not computeIfAbsent
	private final Map<JournalEntry, Double> nets = new IdentityHashMap<>();
	private final Map<MiningEntry, Double> outsideNets = new IdentityHashMap<>();
	private int valuedAt = -1;

	// io = 1 thread, client = runs on the thread that calls the rest
	public HudSums(Journal journal, Money money, Executor io, Executor client) {
		this.journal = journal;
		this.money = money;
		this.io = io;
		this.client = client;
	}

	public AllTime allTime() {
		return allTime;
	}

	// client thread, every refresh. changes = LiveMoney.changes() read once
	public void update(int changes) {
		if (at == changes && firstShaft == journal.first().get() && firstMining == journal.firstMining().get()) {
			addNew();
			return;
		}
		if (pending) return;
		// all again on io, thousands = a hitch here
		List<JournalEntry> shafts = journal.from().apply(0);
		List<MiningEntry> mining = journal.miningFrom().apply(0);
		pending = true;
		if (!submit("all-time coins", () -> sum(shafts, mining, changes), (result, error) -> {
			pending = false;
			// marked done on error too, else it retries every refresh
			allTime = error == null ? result
					: new AllTime(allTime.coins, shafts.size(), allTime.outside, mining.size(), allTime.shaftLines, allTime.outsideLines);
			at = changes;
			firstShaft = shafts.isEmpty() ? null : shafts.getFirst();
			firstMining = mining.isEmpty() ? null : mining.getFirst();
		})) {
			// closing
			pending = false;
		}
	}

	// cached client values, same as the session line
	private void addNew() {
		int shafts = journal.count().getAsInt();
		int mining = journal.miningCount().getAsInt();
		if (allTime.shafts >= shafts && allTime.mining >= mining) return;
		List<JournalEntry> newShafts = allTime.shafts < shafts ? journal.from().apply(allTime.shafts) : List.of();
		List<MiningEntry> newMining = allTime.mining < mining ? journal.miningFrom().apply(allTime.mining) : List.of();
		allTime = allTime.plus(sum(newShafts, newMining, money.value(), money.lines(), null, null));
	}

	// io thread
	private AllTime sum(List<JournalEntry> shafts, List<MiningEntry> mining, int changes) {
		nets.clear();
		outsideNets.clear();
		valuedAt = changes;
		return sum(shafts, mining, money.compute(), money.computeLines(), nets, outsideNets);
	}

	// keeps each value in nets + outside when given
	private static AllTime sum(List<JournalEntry> shafts, List<MiningEntry> mining, Function<JournalEntry, ShaftValue> value,
			Function<MiningEntry, List<ShaftValue.Line>> lines, Map<JournalEntry, Double> nets,
			Map<MiningEntry, Double> outside) {
		Double coins = null;
		DropList.Lines shaftLines = new DropList.Lines();
		for (JournalEntry entry : shafts) {
			ShaftValue v = value.apply(entry);
			Double net = MoneyStats.net(v);
			if (nets != null) nets.put(entry, net);
			coins = MoneyStats.plus(coins, net);
			shaftLines.shaft(v);
		}
		Double outsideCoins = null;
		DropList.Lines outsideLines = new DropList.Lines();
		for (MiningEntry entry : mining) {
			List<ShaftValue.Line> l = lines.apply(entry);
			Double total = total(l);
			if (outside != null) outside.put(entry, total);
			outsideCoins = MoneyStats.plus(outsideCoins, total);
			outsideLines.outside(l);
		}
		return new AllTime(coins, shafts.size(), outsideCoins, mining.size(), shaftLines.summed(), outsideLines.summed());
	}

	private static Double total(List<ShaftValue.Line> lines) {
		return lines == null ? null : MiningValue.total(lines);
	}

	// client thread. wanted = a drawn line or the editor reads a pb, else nothing is redone (sacks msgs come every ~30 s)
	public void updatePbs(List<PersonalBests.Def> list, int changes, boolean wanted) {
		if (!wanted || pbPending) return;
		JournalEntry first = journal.first().get();
		MiningEntry firstMiningNow = journal.firstMining().get();
		// outside coins + breaks count in session pbs too
		if (list.equals(pbList) && journal.count().getAsInt() == pbEntries && changes == pbChanges && first == pbFirst
				&& firstMiningNow == pbFirstMining && journal.miningCount().getAsInt() == pbMining
				&& journal.breakCount().getAsInt() == pbBreaks) {
			return;
		}
		List<JournalEntry> all = journal.from().apply(0);
		List<MiningEntry> mining = journal.miningFrom().apply(0);
		List<BreakEntry> breaks = journal.breaks().get();
		pbList = list;
		pbEntries = all.size();
		pbChanges = changes;
		pbFirst = first;
		pbFirstMining = firstMiningNow;
		pbMining = journal.miningCount().getAsInt();
		pbBreaks = journal.breakCount().getAsInt();
		pbPending = true;
		if (!submit("personal bests", () -> pbs(list, all, mining, breaks, changes), (results, error) -> {
			pbPending = false;
			if (error == null) pbResults = results;
		})) {
			// closing, try again next time
			pbPending = false;
			pbList = null;
		}
	}

	// io thread
	private Map<String, PersonalBests.Result> pbs(List<PersonalBests.Def> list, List<JournalEntry> all, List<MiningEntry> mining,
			List<BreakEntry> breaks, int changes) {
		// a smaller journal = reloaded, old keys go
		if (changes != valuedAt || nets.size() > all.size() || outsideNets.size() > mining.size()) {
			nets.clear();
			outsideNets.clear();
			valuedAt = changes;
		}
		List<JournalStats.SessionSummary> sessions = JournalStats.sessions(all, mining, breaks);
		PersonalBests.Coins coins = PersonalBests.Coins.of(this::net, this::outsideNet);
		Map<String, PersonalBests.Result> results = new HashMap<>();
		for (PersonalBests.Def def : list) results.put(PersonalBests.key(def), PersonalBests.best(def, all, sessions, coins));
		return results;
	}

	// io thread, null = no prices
	private Double net(JournalEntry entry) {
		if (!nets.containsKey(entry)) nets.put(entry, MoneyStats.net(money.compute().apply(entry)));
		return nets.get(entry);
	}

	private Double outsideNet(MiningEntry entry) {
		if (!outsideNets.containsKey(entry)) outsideNets.put(entry, total(money.computeLines().apply(entry)));
		return outsideNets.get(entry);
	}

	// client thread. key -> shown text, the shaft ur in counts for drystreaks
	public Map<String, String> pbTexts(List<PersonalBests.Def> list, String inShaftType, int metalTier) {
		Map<String, String> texts = new HashMap<>();
		for (PersonalBests.Def def : list) {
			PersonalBests.Result result = pbResults.get(PersonalBests.key(def));
			if (result == null) continue;
			String text = HudData.pbText(def, PersonalBests.live(def, result, inShaftType), metalTier);
			if (text != null) texts.put(PersonalBests.key(def), text);
		}
		return texts;
	}

	// false = not queued (closing)
	private <T> boolean submit(String what, Supplier<T> work, BiConsumer<T, Throwable> done) {
		try {
			CompletableFuture.supplyAsync(work, io).whenComplete((result, error) -> client.execute(() -> {
				if (error != null) LOGGER.warn("Could not work out {}", what, error);
				done.accept(result, error);
			}));
			return true;
		} catch (RejectedExecutionException e) {
			return false;
		}
	}
}
