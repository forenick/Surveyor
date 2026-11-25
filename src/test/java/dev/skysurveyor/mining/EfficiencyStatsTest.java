package dev.skysurveyor.mining;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.journal.JournalEntry;
import dev.skysurveyor.journal.PersonalBests;
import dev.skysurveyor.journal.PersonalBests.Def;

import static dev.skysurveyor.journal.EntryBuilder.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class EfficiencyStatsTest {
	// 13 tick blocks for `seconds` of mining at `percent`
	private static JournalEntry shaft(String id, int percent, long seconds) {
		int blocks = (int) Math.round(seconds * 20 / 13.0 * percent / 100);
		return entry().shaftId(id).mining(new MiningStats(seconds * 1000, 30_000, blocks, 0, "magenta_stained_glass", 13.0, null, 0, 0)).build();
	}

	private static final List<JournalEntry> ENTRIES = List.of(shaft("JASP_1", 70, 700), shaft("JADE_1", 62, 700), shaft("JASP_C", 66, 700),
			shaft("JASP_C", 68, 700), entry().shaftId("TOPA_1").build(), shaft("RUBY_1", 90, 20), shaft("JADE_1", 40, 200));

	@Test
	void nothingMined() {
		assertSame(EfficiencyStats.Summary.NONE, EfficiencyStats.of(List.of(entry().shaftId("TOPA_1").build())));
	}

	@Test
	void medianOverShaftsWithANumber() {
		EfficiencyStats.Summary summary = EfficiencyStats.of(ENTRIES);
		// 40, 62, 66, 68, 70: skipped topaz + the 30 block ruby left out
		assertEquals(5, summary.all().shafts());
		assertEquals(0.66, summary.all().median(), 0.005);
		assertEquals(13.0, summary.all().ticks());
	}

	@Test
	void perTypeBestFirstWithVariants() {
		List<EfficiencyStats.Type> types = EfficiencyStats.of(ENTRIES).types();
		assertEquals(List.of("JASP", "JADE"), types.stream().map(EfficiencyStats.Type::type).toList());
		EfficiencyStats.Type jasper = types.getFirst();
		assertEquals(3, jasper.all().shafts());
		assertEquals(0.68, jasper.all().median(), 0.005);
		assertEquals(List.of("1", "C"), List.copyOf(jasper.variants().keySet()));
		assertEquals(0.67, jasper.variants().get("C").median(), 0.005, "even count = middle two");
		assertEquals(0.51, types.get(1).all().median(), 0.005);
	}

	@Test
	void personalBestNeedsFiveMinutesOfMining() {
		Def def = new Def("shaft_efficiency", null, null, null);
		PersonalBests.Result best = PersonalBests.best(def, ENTRIES, List.of(), entry -> null);
		assertEquals(0.70, best.value(), 0.005);
		assertEquals("JASP_1", best.entry().shaftId());
		// the 200 s jade at 40% and the 20 s ruby dont count
		assertNull(Efficiency.record(ENTRIES.get(6).mining()));
		assertNull(PersonalBests.best(def, List.of(ENTRIES.get(5), ENTRIES.get(6)), List.of(), entry -> null).value());
		assertEquals("70%", dev.skysurveyor.hud.HudData.pbText(def, best, 1));
	}
}
