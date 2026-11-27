package dev.skysurveyor.calc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import dev.skysurveyor.data.Patches;
import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.journal.BreakEntry;
import dev.skysurveyor.journal.CorpseCounts;
import dev.skysurveyor.journal.GemYield;
import dev.skysurveyor.journal.JournalEntry;
import dev.skysurveyor.journal.JournalStats;
import dev.skysurveyor.journal.JournalStats.SessionSummary;
import dev.skysurveyor.journal.JsonLinesFile;
import dev.skysurveyor.journal.MiningEntry;
import dev.skysurveyor.journal.StatsRange;
import dev.skysurveyor.mining.Efficiency;
import dev.skysurveyor.money.ItemIds;
import dev.skysurveyor.money.MiningValue;
import dev.skysurveyor.money.PriceRules;
import dev.skysurveyor.money.ShaftValue;
import dev.skysurveyor.money.ShaftValue.Part;
import dev.skysurveyor.settings.CalculatorSettings.KeyLoot;
import dev.skysurveyor.settings.MoneySettings;
import dev.skysurveyor.util.Stats;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

// prints only: the guide replayed over nicks journal, each mined shaft against what the guide
// said right before it (numbers from older shafts only). todays prices on both sides
class GuideBacktestNickTest {
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());
	// nicks money settings, old shafts at todays prices so list + made compare
	private static final MoneySettings MONEY = new MoneySettings(MoneySettings.SellPrice.BEST, MoneySettings.GemTier.FLAWLESS, 1.125,
			MoneySettings.OldShafts.TODAY, MoneySettings.Crystals.PERFECT_MINUS_FLAWLESS, false, MoneySettings.MetalTier.ENCHANTED,
			MoneySettings.OwnMetal.INSTA_BUY);
	private static final GameData DATA = GameData.defaults();

	// the apps default
	private int minedShafts = 5;
	private NickFiles files;
	private List<BreakEntry> breaks;
	private PriceRules rules;
	private final Map<JournalEntry, ShaftValue> values = new IdentityHashMap<>();

	private boolean load() throws IOException {
		files = NickFiles.load();
		if (files == null) return false;
		breaks = new JsonLinesFile<>(NickFiles.FOLDER.resolve("breaks.jsonl"), BreakEntry.class, e -> e.to() > e.from(), Runnable::run).load().join();
		rules = new PriceRules(files.prices()::get, MONEY, ItemIds.bundled(Map.of()), TextPatterns.defaults());
		return true;
	}

	private ShaftValue value(JournalEntry entry) {
		return values.computeIfAbsent(entry, e -> ShaftValue.of(e, files.prices()::get, MONEY, ItemIds.bundled(Map.of()), TextPatterns.defaults()));
	}

	private Double outside(MiningEntry entry) {
		return MiningValue.coins(entry, files.prices()::get, MONEY, ItemIds.bundled(Map.of()), TextPatterns.defaults());
	}

	private Map<String, Double> typed() throws IOException {
		Map<String, Double> typed = new LinkedHashMap<>();
		JsonObject json = JsonParser.parseString(Files.readString(NickFiles.FOLDER.resolve("config.json"), StandardCharsets.UTF_8)).getAsJsonObject();
		if (!json.has("calculator")) return typed;
		for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("calculator").getAsJsonObject("typed").entrySet()) {
			typed.put(e.getKey(), e.getValue().getAsDouble());
		}
		return typed;
	}

	private interface Window {
		List<SessionSummary> of(List<SessionSummary> newestFirst, long now);
	}

	private static final Window RANGE = StatsRange.LAST_5_SESSIONS::sessions;
	private static final Window ALL = StatsRange.ALL_TIME::sessions;

	// the guide as it stood at a moment: only shafts entered before it
	private YourNumbers numbers(long at, Window window, Map<String, Double> typed, List<JournalEntry> entries, Function<MiningEntry, Double> outside) {
		return numbers(at, window, typed, entries, outside, minedShafts);
	}

	// mined = newest mined shafts behind the mining numbers, 0 = the range (the rule till alpha.24)
	private YourNumbers numbers(long at, Window window, Map<String, Double> typed, List<JournalEntry> entries, Function<MiningEntry, Double> outside,
			int mined) {
		List<JournalEntry> before = entries.stream().filter(e -> e.enteredAt() < at).toList();
		List<MiningEntry> mining = files.mining().stream().filter(e -> e.at() < at).toList();
		List<SessionSummary> sessions = JournalStats.sessions(before, mining, breaks);
		YourNumbers numbers = Learner.learn(new Learner.Input(window.of(sessions, at), this::value, outside, typed,
				Patches.defaults().newest().startMillis(), TextPatterns.defaults(), Fiesta.State.NONE, rules.sell("GLOSSY_GEMSTONE"), before, mined), DATA);
		return LiveGuide.withKeyLoot(numbers, rules, KeyLoot.WIKI);
	}

	private static double gemsPerBlock(JournalEntry entry) {
		double p = Learner.flawedShare(entry.gems());
		int blocks = entry.mining().blocks() + entry.mining().spread();
		return entry.gems().fineEquivalent() * 6400 / (blocks * (1 + 79 * p));
	}

	private double made(JournalEntry entry) {
		ShaftValue value = value(entry);
		return value.total(Part.MINED) + value.total(Part.OTHER) + value.total(Part.GLOSSY) + value.total(Part.SCRAP) + value.total(Part.FOSSIL);
	}

	private static String pct(double ratio) {
		return String.format("%+5.1f%%", (ratio - 1) * 100);
	}

	@Test
	void eachShaftAgainstTheGuideBeforeIt() throws IOException {
		assumeTrue(load(), "no journal at " + NickFiles.FOLDER);
		Map<String, Double> typed = typed();
		System.out.println("== walk forward, numbers from older shafts only. typed = nicks config.json: " + typed);
		List<JournalEntry> mined = files.entries().stream().filter(e -> Learner.mined(e, DATA)).toList();
		List<double[]> errors = new ArrayList<>();
		for (JournalEntry entry : mined) {
			int looted = Math.min(CorpseCounts.looted(CorpseCounts.of(entry)), ShaftModel.MAX_LAPIS);
			double fine = entry.gems().fineEquivalent();
			double made = made(entry);
			StringBuilder line = new StringBuilder(String.format("%s %-7s %dL %-5s cold %4s  fine %5.1f made %5.2fM |", TIME.format(Instant.ofEpochMilli(entry.enteredAt())),
					entry.shaftId(), looted, entry.exit(), entry.coldAtExit(), fine, made / 1e6));
			double[] row = new double[6];
			int i = 0;
			// the app, the app w/ his typed numbers, the rule till alpha.24
			for (int rule : new int[] {minedShafts, 0}) {
				for (Map<String, Double> set : List.of(Map.<String, Double>of(), typed)) {
					if (rule == 0 && !set.isEmpty()) continue;
					YourNumbers n = numbers(entry.enteredAt(), RANGE, set, files.entries(), this::outside, rule);
					ShaftModel.Guide guide = LiveGuide.run(n, rules);
					ShaftModel.Shaft cell = guide.row(entry.shaftType(), entry.variant()).lapis().get(looted);
					row[i++] = cell.fine() / fine;
					row[i++] = cell.coins() / made;
					line.append(String.format(" fine %5.1f %s coins %5.2fM %s |", cell.fine(), pct(cell.fine() / fine), cell.coins() / 1e6, pct(cell.coins() / made)));
				}
			}
			errors.add(row);
			System.out.println(line);
			YourNumbers n = numbers(entry.enteredAt(), RANGE, Map.of(), files.entries(), this::outside);
			ShaftModel.Guide guide = LiveGuide.run(n, rules);
			ShaftModel.Shaft cell = guide.row(entry.shaftType(), entry.variant()).lapis().get(looted);
			YourNumbers.LayoutNum layout = n.layouts().get(entry.shaftId());
			GameData.Gem gem = DATA.gem(entry.shaftType());
			System.out.printf("      used / real: efficiency %.1f%% %s / %.1f%%, setup %.0f / %.0f s, gems per block %.1f %s(%d) / %.1f, flawed %.1f%% / %.1f%%, "
					+ "inside %.0f / %.0f s, ticks %.1f / %.1f, route %.0f %s / own %d, spread %.1f%% / %.1f%%, other %.2fM / %.2fM%n",
					layout.efficiency().value() * 100, layout.efficiency().source(), Efficiency.shown(entry.mining()) * 100, n.get(NumberId.SETUP),
					entry.mining().setupMillis() / 1000.0, n.get(NumberId.GEMS_PER_BLOCK), n.num(NumberId.GEMS_PER_BLOCK).source(),
					n.num(NumberId.GEMS_PER_BLOCK).basis(), gemsPerBlock(entry), (n.get(NumberId.FLAWED) + looted / 100.0) * 100,
					Learner.flawedShare(entry.gems()) * 100, cell.seconds(), entry.durationMillis() / 1000.0, n.get(YourNumbers.ticksOf(gem.strength())),
					entry.mining().shownTicks(), layout.route().value(), layout.route().source(), entry.mining().blocks(), n.get(NumberId.SPREAD) * 100,
					100.0 * entry.mining().spread() / entry.mining().blocks(), cell.other() / 1e6, (made - value(entry).total(Part.MINED)) / 1e6);
		}
		String[] names = {"newest 5 mined shafts, fine", "newest 5 mined shafts, coins", "newest 5 + typed, fine", "newest 5 + typed, coins",
				"all from the range (till alpha.24), fine", "all from the range (till alpha.24), coins"};
		// 1st shaft has no tracked numbers behind it
		for (int from : new int[] {0, 1}) {
			System.out.println(from == 0 ? "-- all " + errors.size() + " shafts" : "-- w/o the 1st (starting values)");
			for (int c = 0; c < 6; c++) {
				List<Double> offs = new ArrayList<>();
				List<Double> abs = new ArrayList<>();
				for (double[] row : errors.subList(from, errors.size())) {
					offs.add(row[c] - 1);
					abs.add(Math.abs(row[c] - 1));
				}
				System.out.printf("   %-32s middle %+5.1f%%, mean abs %4.1f%%, worst %4.1f%%%n", names[c], Stats.median(offs) * 100,
						abs.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 100, abs.stream().mapToDouble(Double::doubleValue).max().orElse(0) * 100);
			}
		}
	}

	@Test
	void layoutsTrackedPerRule() throws IOException {
		assumeTrue(load(), "no journal at " + NickFiles.FOLDER);
		System.out.println("== layouts w/ a tracked efficiency after each session: last 5 sessions against the last 5 shafts on that layout");
		List<JournalEntry> mined = files.entries().stream().filter(e -> Learner.mined(e, DATA)).toList();
		List<SessionSummary> all = JournalStats.sessions(files.entries(), files.mining(), breaks).reversed();
		int emptyRange = 0;
		int counted = 0;
		for (int s = 0; s < all.size(); s++) {
			SessionSummary session = all.get(s);
			long end = session.entries().getLast().endedAt();
			if (mined.isEmpty() || end < mined.getFirst().enteredAt()) continue;
			List<SessionSummary> newest = all.subList(Math.max(0, s - 4), s + 1);
			TreeSet<String> range = new TreeSet<>();
			int minedInRange = 0;
			for (SessionSummary in : newest) {
				for (JournalEntry entry : in.entries()) {
					if (!Learner.mined(entry, DATA)) continue;
					minedInRange++;
					range.add(DATA.layout(entry.shaftId()).group());
				}
			}
			TreeSet<String> ever = new TreeSet<>();
			for (JournalEntry entry : mined) {
				if (entry.enteredAt() <= end) ever.add(DATA.layout(entry.shaftId()).group());
			}
			counted++;
			if (range.isEmpty()) emptyRange++;
			System.out.printf("%s  %2d shafts, %d mined | last 5 sessions: %d mined shafts, %d layouts %s | last 5 on the layout: %d layouts %s%n",
					TIME.format(Instant.ofEpochMilli(session.start())), session.entries().size(),
					session.entries().stream().filter(e -> Learner.mined(e, DATA)).count(), minedInRange, range.size(), range, ever.size(), ever);
		}
		System.out.println("-- sessions w/ nothing tracked at all under last 5 sessions: " + emptyRange + " of " + counted);

		System.out.println("== efficiency the guide would use for each mined shaft: range rule, last 5 on the layout, real");
		double rangeAbs = 0;
		double layoutAbs = 0;
		double levelAbs = 0;
		int n = 0;
		for (JournalEntry entry : mined) {
			// the rule till alpha.24
			YourNumbers numbers = numbers(entry.enteredAt(), RANGE, Map.of(), files.entries(), this::outside, 0);
			YourNumbers.Num used = numbers.layouts().get(entry.shaftId()).efficiency();
			String group = DATA.layout(entry.shaftId()).group();
			List<Double> own = new ArrayList<>();
			for (JournalEntry other : mined) {
				if (other.enteredAt() < entry.enteredAt() && DATA.layout(other.shaftId()).group().equals(group)) own.add(Efficiency.shown(other.mining()));
			}
			List<Double> last = own.subList(Math.max(0, own.size() - 5), own.size());
			double real = Efficiency.shown(entry.mining());
			// no shaft on it yet = the level, same under both rules
			double byLayout = last.isEmpty() ? used.value() : Stats.median(last);
			System.out.printf("%s %-7s real %.1f%% | range rule %.1f%% %-10s (%d) | last 5 on the layout %.1f%% (%d)%n", TIME.format(Instant.ofEpochMilli(entry.enteredAt())),
					entry.shaftId(), real * 100, used.value() * 100, used.source(), used.basis(), byLayout * 100, last.size());
			if (numbers.num(NumberId.EFFICIENCY).source() == YourNumbers.Source.START) continue;
			n++;
			rangeAbs += Math.abs(used.value() - real);
			layoutAbs += Math.abs(byLayout - real);
			levelAbs += Math.abs(numbers.get(NumberId.EFFICIENCY) - real);
		}
		System.out.printf("-- mean miss over %d shafts: range rule %.2f pts, last 5 on the layout %.2f pts, one efficiency for all %.2f pts%n", n, rangeAbs / n * 100,
				layoutAbs / n * 100, levelAbs / n * 100);
	}

	// fiesta = off on every shaft: nicks older ones have none saved + would be a pool the 1st new shaft replaces
	private static JournalEntry scaled(JournalEntry e, double f) {
		GemYield g = e.gems();
		GemYield gems = g == null ? null : new GemYield(g.gem(), Math.round(g.rough() * f), Math.round(g.flawed() * f), Math.round(g.fine() * f),
				Math.round(g.tailRough() * f), Math.round(g.tailFlawed() * f));
		return new JournalEntry(e.version(), e.sessionId(), e.shaftId(), e.enteredAt(), e.durationMillis(), e.exit(), e.exitPowder(), e.searchMillis(),
				e.corpses(), e.extraCorpses(), e.loots(), e.mayhemBuffs(), e.campfiresAt(), e.coldAtExit(), e.scrapFound(), e.pristineProcs(), e.pristineItems(),
				e.sacksAdded(), e.sacksRemoved(), gems, e.crystals(), e.otherDrops(), e.prices(), e.campfireColds(), e.metal(), e.keysSaved(), e.mining(),
				e.fossil(), Boolean.FALSE, 0L, e.spawnBlock());
	}

	// pretend his fortune jumped: every gem count x f from one session on, in shafts + tunnels
	@Test
	void gearJump() throws IOException {
		assumeTrue(load(), "no journal at " + NickFiles.FOLDER);
		double f = 1.15;
		List<JournalEntry> mined = files.entries().stream().filter(e -> Learner.mined(e, DATA)).toList();
		// 1st shaft of the 11-26 23:10 session
		long jump = files.entries().stream().filter(e -> e.sessionId() == mined.get(mined.size() - 5).sessionId()).findFirst().orElseThrow().enteredAt();
		System.out.println("== gear jump: gems x" + f + " from " + TIME.format(Instant.ofEpochMilli(jump)) + " on (shafts + tunnels)");
		List<JournalEntry> lagging = files.entries().stream().map(e -> scaled(e, e.enteredAt() >= jump ? f : 1)).toList();
		List<JournalEntry> truth = files.entries().stream().map(e -> scaled(e, f)).toList();
		Function<MiningEntry, Double> lagOutside = e -> {
			Double v = outside(e);
			return v == null ? null : e.at() >= jump ? v * f : v;
		};
		Function<MiningEntry, Double> trueOutside = e -> {
			Double v = outside(e);
			return v == null ? null : v * f;
		};
		List<Long> times = new ArrayList<>();
		times.add(jump);
		for (JournalEntry entry : files.entries()) {
			if (entry.enteredAt() >= jump) times.add(entry.endedAt() + 1);
		}
		List<Double> series = new ArrayList<>();
		for (JournalEntry entry : lagging) {
			if (Learner.mined(entry, DATA) && entry.enteredAt() < jump) series.add(gemsPerBlock(entry));
		}
		String lastLine = "";
		int step = 0;
		for (long at : times) {
			JournalEntry just = step == 0 ? null : lagging.stream().filter(e -> e.endedAt() + 1 == at).findFirst().orElse(null);
			step++;
			if (just != null && Learner.mined(just, DATA)) series.add(gemsPerBlock(just));
			YourNumbers shown = numbers(at, RANGE, Map.of(), lagging, lagOutside);
			YourNumbers real = numbers(at, RANGE, Map.of(), truth, trueOutside);
			ShaftModel.Guide guide = LiveGuide.run(shown, rules);
			ShaftModel.Guide right = LiveGuide.run(real, rules);
			long newer = lagging.stream().filter(e -> e.enteredAt() >= jump && e.enteredAt() < at && Learner.mined(e, DATA)).count();
			StringBuilder cells = new StringBuilder();
			for (String code : List.of("JASP", "PERI", "AMBE", "SAPP")) {
				ShaftModel.Row a = guide.row(code);
				ShaftModel.Row b = right.row(code);
				cells.append(String.format(" %s 1L %+.2fM (%+.2fM) from %s (%s)", code, a.lapis().get(1).gain() / 1e6, b.lapis().get(1).gain() / 1e6, a.mineFrom(), b.mineFrom()));
			}
			String line = String.format("gems per block %.1f %s(%d) right %.1f, till alpha.24 %.1f | coins per spawn %.2fM right %.2fM | leaving %.1fM right %.1fM / h |%s",
					shown.get(NumberId.GEMS_PER_BLOCK), shown.num(NumberId.GEMS_PER_BLOCK).source(), shown.num(NumberId.GEMS_PER_BLOCK).basis(),
					real.get(NumberId.GEMS_PER_BLOCK), numbers(at, RANGE, Map.of(), lagging, lagOutside, 0).get(NumberId.GEMS_PER_BLOCK), shown.get(NumberId.COINS_PER_SPAWN) / 1e6, real.get(NumberId.COINS_PER_SPAWN) / 1e6,
					guide.perHour(guide.greenOnly()) / 1e6, right.perHour(right.greenOnly()) / 1e6, cells);
			if (line.equals(lastLine)) continue;
			lastLine = line;
			System.out.printf("%s after %-7s %s, %d new mined | %s%n", TIME.format(Instant.ofEpochMilli(at)), just == null ? "-" : just.shaftId(),
					just != null && Learner.mined(just, DATA) ? "mined" : "skip ", newer, line);
		}
		System.out.println("== per mined shaft: gems per block, flawed base, ticks, efficiency, spread, mayhem");
		for (JournalEntry entry : mined) {
			System.out.printf("%s %-7s gems per block %.1f, flawed base %.1f%%, ticks %.1f, efficiency %.1f%%, spread %.1f%%, %s%n", TIME.format(Instant.ofEpochMilli(entry.enteredAt())),
					entry.shaftId(), gemsPerBlock(entry), (Learner.flawedShare(entry.gems()) - CorpseCounts.looted(CorpseCounts.of(entry)) / 100.0) * 100,
					entry.mining().shownTicks(), Efficiency.shown(entry.mining()) * 100, 100.0 * entry.mining().spread() / entry.mining().blocks(), entry.mayhemBuffs());
		}
	}
	// what moves the list from one shaft to the next, nothing typed
	@Test
	void playNumbersShaftByShaft() throws IOException {
		assumeTrue(load(), "no journal at " + NickFiles.FOLDER);
		List<JournalEntry> mined = files.entries().stream().filter(e -> Learner.mined(e, DATA)).toList();
		long from = mined.get(mined.size() - 5).sessionId();
		System.out.println("== numbers after each shaft, last 5 sessions, nothing typed");
		List<Long> times = new ArrayList<>();
		for (JournalEntry entry : files.entries()) {
			if (entry.sessionId() >= from) times.add(entry.endedAt() + 1);
		}
		for (long at : times) {
			JournalEntry just = files.entries().stream().filter(e -> e.endedAt() + 1 == at).findFirst().orElseThrow();
			for (Window window : List.of(RANGE, ALL)) {
				YourNumbers n = numbers(at, window, Map.of(), files.entries(), this::outside);
				ShaftModel.Guide guide = LiveGuide.run(n, rules);
				StringBuilder line = new StringBuilder();
				for (NumberId id : List.of(NumberId.SPAWN, NumberId.NOT_MINING, NumberId.BACK_MINED, NumberId.COINS_PER_SPAWN, NumberId.SETUP, NumberId.EFFICIENCY,
						NumberId.GEMS_PER_BLOCK, NumberId.OTHER, NumberId.FREEZE)) {
					YourNumbers.Num num = n.num(id);
					line.append(String.format(" %s %s%s(%d)", id.key(), id.format(num.value()), num.source() == YourNumbers.Source.TRACKED ? "" : " " + num.source(), num.basis()));
				}
				ShaftModel.Row jasper = guide.row("JASP");
				ShaftModel.Row peridot = guide.row("PERI");
				System.out.printf("%s %-7s %s %s | leaving %.1fM, best %.1fM / h, mined %.0f%% | JASP 1L %+.2fM from %s, PERI 1L %+.2fM from %s |%s%n",
						TIME.format(Instant.ofEpochMilli(at)), just.shaftId(), Learner.mined(just, DATA) ? "mined" : "skip ", window == RANGE ? "5 sess" : "all   ",
						guide.perHour(guide.greenOnly()) / 1e6, guide.perHour(guide.leaving()) / 1e6, guide.minedShare() * 100, jasper.lapis().get(1).gain() / 1e6,
						jasper.mineFrom(), peridot.lapis().get(1).gain() / 1e6, peridot.mineFrom(), line);
			}
		}
		System.out.println("== now: each layout, last 5 sessions against all time");
		long now = System.currentTimeMillis();
		YourNumbers range = numbers(now, RANGE, Map.of(), files.entries(), this::outside);
		YourNumbers all = numbers(now, ALL, Map.of(), files.entries(), this::outside);
		ShaftModel.Guide guide = LiveGuide.run(range, rules);
		ShaftModel.Guide wide = LiveGuide.run(all, rules);
		for (String id : range.layouts().keySet()) {
			YourNumbers.LayoutNum a = range.layouts().get(id);
			YourNumbers.LayoutNum b = all.layouts().get(id);
			if (b.efficiency().basis() == 0 && b.route().basis() == 0) continue;
			System.out.printf("%-7s efficiency %.1f%% %s(%d) | all time %.1f%% %s(%d) || route %.0f %s(%d) | all time %.0f %s(%d) || 1L coins %.2fM | all time %.2fM%n", id,
					a.efficiency().value() * 100, a.efficiency().source(), a.efficiency().basis(), b.efficiency().value() * 100, b.efficiency().source(),
					b.efficiency().basis(), a.route().value(), a.route().source(), a.route().basis(), b.route().value(), b.route().source(), b.route().basis(),
					guide.layouts().get(id).lapis().get(1).coins() / 1e6, wide.layouts().get(id).lapis().get(1).coins() / 1e6);
		}
	}
}
