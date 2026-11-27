package dev.skysurveyor.journal;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.detection.Corpse;
import dev.skysurveyor.detection.CorpseLoot;
import dev.skysurveyor.detection.ItemCount;
import dev.skysurveyor.detection.Shaft.ExitReason;
import dev.skysurveyor.money.ShaftValue;
import dev.skysurveyor.money.ShaftValue.Line;
import dev.skysurveyor.money.ShaftValue.Part;
import dev.skysurveyor.settings.MoneySettings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

// made up, every summary line
class SummaryTextTest {
	private static final String RULE = "§8§m                                                  ";

	private static CorpseLoot lapis(long powder) {
		return new CorpseLoot("Lapis", false, List.of(new ItemCount("Yellow Goblin Egg", 1)), powder);
	}

	@Test
	void fullShaft() {
		JournalEntry entry = EntryBuilder.entry().shaftId("JASP_1").durationMillis(252_000).exit("WARP").searchMillis(90_000L)
				.corpses(List.of(new Corpse("Lapis", true), new Corpse("Lapis", true), new Corpse("Umber", false)))
				.loots(List.of(lapis(5_536), lapis(5_000))).exitPowder(1_250).gems(new GemYield("Jasper", 0, 160, 2, 0, 0))
				.pristineProcs(12).campfiresAt(List.of(30_000L, 90_000L)).build();
		ShaftValue value = new ShaftValue(List.of(new Line(Part.MINED, "Fine Jasper Gemstone", 3.6, 1_200_000.0),
				new Line(Part.CORPSES, "Yellow Goblin Egg", 2, 300_000.0), new Line(Part.KEYS, "Umber Key", 1, -121_000.0),
				new Line(Part.CRYSTALS, "Jasper Crystal", 1, null)), List.of());
		assertEquals(List.of(RULE,
				" §d§lJasper 1  §7inside §f4m 12s §7(warped out)",
				" §7Spawned in §f1m 30s",
				" §7Corpses §9Lapis 2/2§7, §fUmber 0/1",
				// 160 flawed + 2 fine
				" §7Gems §9Fine Jasper Gemstone §8x4.0 §8(12 pristine procs)",
				" §7Powder §f+11,786 §8(corpses 10,536, exit 1,250)",
				" §7Campfires §f2",
				" §7Coins §6+1.4M §8(gems 1.2M, corpses 300.0k, keys -121.0k) §c1 item without a price",
				" §7Session §f5 shafts§7, avg spawn §f1m 30s",
				RULE), SummaryText.lines(entry, new Journal.Session(0, 5, 4, 360_000), value, MoneySettings.DEFAULTS));
	}

	@Test
	void bareShaft() {
		JournalEntry entry = EntryBuilder.entry().shaftId(null).exit("DEATH").build();
		assertEquals(List.of(RULE, " §b§lMineshaft  §7inside §f1m 0s §c(froze)", " §7Spawned in §8not timed", " §7Coins §8no prices yet", RULE),
				SummaryText.lines(entry, null, null, MoneySettings.DEFAULTS));
	}

	@Test
	void coinsLine() {
		assertNull(SummaryText.coins(new ShaftValue(List.of(), List.of()), false), "nothing worth coins");
		assertEquals(" §7Coins §6+1.2M", SummaryText.coins(new ShaftValue(List.of(new Line(Part.CORPSES, "Goblin Egg", 1, 1_240_000.0)),
				List.of()), false), "1 part = no breakdown");
		assertEquals(" §7Coins §c-121.0k", SummaryText.coins(new ShaftValue(List.of(new Line(Part.KEYS, "Umber Key", 1, -121_000.0)),
				List.of()), false));
		assertEquals(" §7Coins §60 §81 item with no NPC price",
				SummaryText.coins(new ShaftValue(List.of(new Line(Part.CRYSTALS, "Jasper Crystal", 1, null)), List.of()), true));
	}

	@Test
	void sessionLine() {
		assertEquals("§f1 shaft", SummaryText.session(new Journal.Session(0, 1, 0, 0)));
		assertEquals("§f2 shafts§7, avg spawn §f45s", SummaryText.session(new Journal.Session(0, 2, 2, 90_000)));
	}

	@Test
	void exitNames() {
		assertEquals("Left by the exit", SummaryText.exitName(ExitReason.BYE));
		assertEquals("Unknown", SummaryText.exitName(ExitReason.UNKNOWN));
	}
}
