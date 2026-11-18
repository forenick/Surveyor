package dev.skysurveyor.hud;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.skysurveyor.OldSaves;
import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.journal.JournalEntry;
import dev.skysurveyor.journal.JournalFile;
import dev.skysurveyor.journal.JournalStats;
import dev.skysurveyor.journal.JsonLinesFile;
import dev.skysurveyor.journal.MetalYield;
import dev.skysurveyor.journal.MiningEntry;
import dev.skysurveyor.journal.PersonalBests;
import dev.skysurveyor.money.DropList;
import dev.skysurveyor.money.ItemIds;
import dev.skysurveyor.money.MiningValue;
import dev.skysurveyor.money.MoneyStats;
import dev.skysurveyor.money.ShaftValue;
import dev.skysurveyor.settings.MoneySettings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// all-time sums over a real journal, saved prices only. expected = how LiveHud summed before HudSums
class HudSumsTest {
	private static final TextPatterns PATTERNS = TextPatterns.defaults();
	private static final ItemIds IDS = ItemIds.bundled(Map.of());
	private static List<JournalEntry> entries;
	private static List<MiningEntry> mining;

	@BeforeAll
	static void read() throws Exception {
		Path dir = OldSaves.ROOT.resolve("0.24.0-alpha.14");
		entries = new JournalFile(dir.resolve("journal.jsonl"), Runnable::run).load().get();
		mining = new JsonLinesFile<>(dir.resolve("mining.jsonl"), MiningEntry.class, e -> e.items() != null, Runnable::run).load().get();
		assertTrue(entries.size() > 50 && !mining.isEmpty());
	}

	private static final class FakeJournal {
		final List<JournalEntry> shafts = new ArrayList<>();
		final List<MiningEntry> outside = new ArrayList<>();

		HudSums.Journal sums() {
			return new HudSums.Journal(shafts::size, () -> shafts.isEmpty() ? null : shafts.getFirst(),
					from -> List.copyOf(shafts.subList(from, shafts.size())), outside::size,
					() -> outside.isEmpty() ? null : outside.getFirst(), from -> List.copyOf(outside.subList(from, outside.size())),
					() -> 0, List::of);
		}
	}

	// counts uncached values, the slow part
	private static final class FakeMoney {
		MoneySettings settings = MoneySettings.DEFAULTS;
		int computed;
		int computedLines;

		HudSums.Money sums() {
			return new HudSums.Money(e -> valueOf(e, settings), m -> linesOf(m, settings), e -> {
				computed++;
				return valueOf(e, settings);
			}, m -> {
				computedLines++;
				return linesOf(m, settings);
			});
		}
	}

	private static ShaftValue valueOf(JournalEntry entry, MoneySettings settings) {
		return ShaftValue.of(entry, ShaftValue.layered(entry.prices(), id -> null), settings, IDS, PATTERNS);
	}

	private static List<ShaftValue.Line> linesOf(MiningEntry entry, MoneySettings settings) {
		return MiningValue.lines(entry, ShaftValue.layered(entry.prices(), id -> null), settings, IDS, PATTERNS);
	}

	private static Double outsideTotal(MiningEntry entry, MoneySettings settings) {
		List<ShaftValue.Line> lines = linesOf(entry, settings);
		return lines == null ? null : MiningValue.total(lines);
	}

	private static FakeJournal full() {
		FakeJournal journal = new FakeJournal();
		journal.shafts.addAll(entries);
		journal.outside.addAll(mining);
		return journal;
	}

	@Test
	void sameSumsAsBefore() {
		for (MoneySettings settings : List.of(MoneySettings.DEFAULTS, MoneySettings.DEFAULTS.withIronman(true))) {
			FakeMoney money = new FakeMoney();
			money.settings = settings;
			HudSums sums = new HudSums(full().sums(), money.sums(), Runnable::run, Runnable::run);
			sums.update(1);
			HudSums.AllTime all = sums.allTime();

			Function<JournalEntry, ShaftValue> value = e -> valueOf(e, settings);
			assertNotNull(all.coins());
			assertEquals(MoneyStats.coins(entries, value), all.coins());
			assertEquals(MoneyStats.outside(mining, m -> outsideTotal(m, settings)), all.outside());
			assertEquals(entries.size(), all.shafts());

			DropList.Lines shafts = new DropList.Lines();
			for (JournalEntry entry : entries) shafts.shaft(value.apply(entry));
			DropList.Lines outside = new DropList.Lines();
			for (MiningEntry entry : mining) outside.outside(linesOf(entry, settings));
			DropList before = shafts.summed().plus(outside.summed()).list(settings.isIronman(), PATTERNS);
			assertEquals(before, all.shaftLines().plus(all.outsideLines()).list(settings.isIronman(), PATTERNS));
		}
	}

	@Test
	void eachEntryValuedOncePerPriceChange() {
		FakeMoney money = new FakeMoney();
		HudSums sums = new HudSums(full().sums(), money.sums(), Runnable::run, Runnable::run);
		sums.update(1);
		sums.updatePbs(PersonalBests.DEFAULTS, 1, true);
		assertEquals(entries.size(), money.computed, "coins, drops + pbs share one value per shaft");
		assertEquals(mining.size(), money.computedLines);

		sums.update(1);
		sums.updatePbs(PersonalBests.DEFAULTS, 1, true);
		assertEquals(entries.size(), money.computed, "nothing changed");

		sums.update(2);
		sums.updatePbs(PersonalBests.DEFAULTS, 2, true);
		assertEquals(2 * entries.size(), money.computed, "new prices");
		assertEquals(2 * mining.size(), money.computedLines);
	}

	@Test
	void newEntriesAddUpToAFullSum() {
		FakeJournal journal = new FakeJournal();
		int half = entries.size() / 2;
		journal.shafts.addAll(entries.subList(0, half));
		journal.outside.addAll(mining.subList(0, mining.size() / 2));
		FakeMoney money = new FakeMoney();
		HudSums sums = new HudSums(journal.sums(), money.sums(), Runnable::run, Runnable::run);
		sums.update(1);
		journal.shafts.addAll(entries.subList(half, entries.size()));
		journal.outside.addAll(mining.subList(mining.size() / 2, mining.size()));
		int computed = money.computed;
		sums.update(1);
		assertEquals(computed, money.computed, "new ones from the client cache");

		HudSums whole = new HudSums(full().sums(), new FakeMoney().sums(), Runnable::run, Runnable::run);
		whole.update(1);
		assertEquals(whole.allTime().shafts(), sums.allTime().shafts());
		assertEquals(whole.allTime().mining(), sums.allTime().mining());
		assertEquals(whole.allTime().coins(), sums.allTime().coins(), 1e-3);
		assertEquals(whole.allTime().outside(), sums.allTime().outside(), 1e-3);
		DropList all = whole.allTime().shaftLines().plus(whole.allTime().outsideLines()).list(false, PATTERNS);
		DropList added = sums.allTime().shaftLines().plus(sums.allTime().outsideLines()).list(false, PATTERNS);
		assertEquals(all.total(), added.total(), 1e-3);
		assertEquals(all.items().size(), added.items().size());
	}

	@Test
	void journalLoadedInFrontSumsAgain() {
		FakeJournal journal = new FakeJournal();
		journal.shafts.addAll(entries.subList(entries.size() - 3, entries.size()));
		HudSums sums = new HudSums(journal.sums(), new FakeMoney().sums(), Runnable::run, Runnable::run);
		sums.update(1);
		assertEquals(3, sums.allTime().shafts());
		journal.shafts.clear();
		journal.shafts.addAll(entries);
		journal.outside.addAll(mining);
		sums.update(1);
		assertEquals(MoneyStats.coins(entries, e -> valueOf(e, MoneySettings.DEFAULTS)), sums.allTime().coins());
	}

	@Test
	void samePbsAsBefore() {
		FakeMoney money = new FakeMoney();
		HudSums sums = new HudSums(full().sums(), money.sums(), Runnable::run, Runnable::run);
		sums.update(1);
		sums.updatePbs(PersonalBests.DEFAULTS, 1, true);

		Map<JournalEntry, Double> nets = new java.util.IdentityHashMap<>();
		for (JournalEntry entry : entries) nets.put(entry, MoneyStats.net(valueOf(entry, MoneySettings.DEFAULTS)));
		List<JournalStats.SessionSummary> sessions = JournalStats.sessions(entries, mining, List.of());
		PersonalBests.Coins coins = PersonalBests.Coins.of(nets::get, m -> outsideTotal(m, MoneySettings.DEFAULTS));
		for (String inType : new String[] {null, "JASP", "JADE"}) {
			Map<String, String> before = new HashMap<>();
			for (PersonalBests.Def def : PersonalBests.DEFAULTS) {
				PersonalBests.Result result = PersonalBests.best(def, entries, sessions, coins);
				String text = HudData.pbText(def, PersonalBests.live(def, result, inType), MetalYield.ENCHANTED);
				if (text != null) before.put(PersonalBests.key(def), text);
			}
			assertFalse(before.isEmpty());
			assertEquals(before, sums.pbTexts(PersonalBests.DEFAULTS, inType, MetalYield.ENCHANTED), "in " + inType);
		}
	}

	// review 4.1: a sacks msg outside shafts used to redo every pb
	@Test
	void pbsOnlyWhileALineWantsThem() {
		FakeJournal journal = full();
		FakeMoney money = new FakeMoney();
		HudSums sums = new HudSums(journal.sums(), money.sums(), Runnable::run, Runnable::run);
		sums.updatePbs(PersonalBests.DEFAULTS, 1, false);
		assertEquals(0, money.computed);
		assertTrue(sums.pbTexts(PersonalBests.DEFAULTS, null, MetalYield.ENCHANTED).isEmpty());

		// a pb line shows up later
		sums.updatePbs(PersonalBests.DEFAULTS, 1, true);
		assertEquals(entries.size(), money.computed);
		String rate = sums.pbTexts(PersonalBests.DEFAULTS, null, MetalYield.ENCHANTED).get("session_coin_rate");
		assertNotNull(rate);

		// line gone, more mining: nothing redone
		journal.outside.removeLast();
		sums.updatePbs(PersonalBests.DEFAULTS, 2, false);
		assertEquals(entries.size(), money.computed);
		assertEquals(rate, sums.pbTexts(PersonalBests.DEFAULTS, null, MetalYield.ENCHANTED).get("session_coin_rate"), "old results kept");

		// back: redone w/ what changed meanwhile
		sums.updatePbs(PersonalBests.DEFAULTS, 2, true);
		assertEquals(2 * entries.size(), money.computed);
	}

	// io + client queued like in game: nothing summed twice, results only on the client thread
	@Test
	void waitsForTheIoThread() {
		List<Runnable> io = new ArrayList<>();
		List<Runnable> client = new ArrayList<>();
		FakeMoney money = new FakeMoney();
		HudSums sums = new HudSums(full().sums(), money.sums(), io::add, client::add);
		sums.update(1);
		sums.update(1);
		sums.updatePbs(PersonalBests.DEFAULTS, 1, true);
		sums.updatePbs(PersonalBests.DEFAULTS, 1, true);
		assertEquals(2, io.size(), "one sum + one pb job");
		assertNull(sums.allTime().coins());
		io.forEach(Runnable::run);
		assertNull(sums.allTime().coins(), "not handed over yet");
		assertTrue(sums.pbTexts(PersonalBests.DEFAULTS, null, MetalYield.ENCHANTED).isEmpty());
		client.forEach(Runnable::run);
		assertNotNull(sums.allTime().coins());
		assertFalse(sums.pbTexts(PersonalBests.DEFAULTS, null, MetalYield.ENCHANTED).isEmpty());
		assertEquals(entries.size(), money.computed);
	}

	@Test
	void closingDoesntStick() {
		FakeMoney money = new FakeMoney();
		boolean[] open = {false};
		HudSums sums = new HudSums(full().sums(), money.sums(), task -> {
			if (!open[0]) throw new java.util.concurrent.RejectedExecutionException();
			task.run();
		}, Runnable::run);
		sums.update(1);
		sums.updatePbs(PersonalBests.DEFAULTS, 1, true);
		open[0] = true;
		sums.update(1);
		sums.updatePbs(PersonalBests.DEFAULTS, 1, true);
		assertNotNull(sums.allTime().coins());
	}
}
