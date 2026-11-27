package dev.skysurveyor.calc;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.data.Patches;
import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.journal.StatsRange;
import dev.skysurveyor.money.ItemIds;
import dev.skysurveyor.money.PriceRules;
import dev.skysurveyor.settings.MoneySettings;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

// prints only, nicks journal moves w/ every shaft. the apps default: newest 5 mined shafts
class LearnerNickTest {
	@Test
	void printNicksNumbers() throws IOException {
		NickFiles files = NickFiles.load();
		assumeTrue(files != null, "no journal at " + NickFiles.FOLDER);
		for (StatsRange range : List.of(StatsRange.LAST_5_SESSIONS, StatsRange.SINCE_PATCH, StatsRange.ALL_TIME)) {
			YourNumbers numbers = Learner.learn(new Learner.Input(range.sessions(files.sessions(), System.currentTimeMillis()),
					e -> files.value(e, MoneySettings.DEFAULTS), e -> files.outside(e, MoneySettings.DEFAULTS), Map.of(),
					Patches.defaults().newest().startMillis(), TextPatterns.defaults(), Fiesta.State.NONE, files.prices().get("GLOSSY_GEMSTONE") == null
							? null : files.prices().get("GLOSSY_GEMSTONE").buyOrder(), files.entries(), 5), GameData.defaults());
			System.out.println("== " + range.displayName() + ", spawn gem " + numbers.spawnGem());
			PriceRules rules = new PriceRules(files.prices()::get, MoneySettings.DEFAULTS, ItemIds.bundled(Map.of()), TextPatterns.defaults());
			CorpseLootTable.Value wiki = CorpseLootTable.value(CorpseLootTable.defaults().of("Umber"), row -> {
				Double each = rules.each(row.name());
				return each != null ? each : rules.sell(row.id());
			}, numbers.get(NumberId.EXTRA_ROLL));
			System.out.printf("  wiki key corpse loot %.0f (%.0f a roll, %.2f rolls), no price: %s%n", wiki.worth(), wiki.perRoll(), wiki.rolls(),
					wiki.unpriced());
			for (NumberId id : NumberId.values()) {
				YourNumbers.Num num = numbers.num(id);
				System.out.printf("  %-30s %10s  %-10s %s%n", id.displayName(), id.format(num.value()), num.source(),
						num.basis() == 0 ? "" : num.basis() + " " + num.basisText());
				if (numbers.fiesta().of(id) == null) continue;
				numbers.fiesta().of(id).forEach((state, n) -> System.out.printf("      %-26s %10s  %-10s %s%s%n", state.displayName(),
						id.format(n.num().value()), n.num().source(), n.from() == null ? "" : "from " + n.from().displayName(), n.pool() ? "pool" : ""));
			}
			numbers.layouts().forEach((id, layout) -> System.out.printf("  %-8s %7s  %-10s %s weight %4.0f%% (%d of %d)  own %s from %d%n", id,
					NumberId.EFFICIENCY.format(layout.efficiency().value()), layout.efficiency().source(), GameData.defaults().layout(id).group(), layout.weight() * 100,
					layout.seen(), layout.gemShafts(), layout.efficiency().tracked() == null ? "-" : NumberId.EFFICIENCY.format(layout.efficiency().tracked()),
					layout.efficiency().basis()));
		}
	}
}
