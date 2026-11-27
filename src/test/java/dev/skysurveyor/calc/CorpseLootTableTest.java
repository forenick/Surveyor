package dev.skysurveyor.calc;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

// corpse_loot.txt against research/16
class CorpseLootTableTest {
	private static final CorpseLootTable.Table TABLE = CorpseLootTable.defaults().of("Umber");

	@Test
	void umberAndTungstenShareTheWikiTable() {
		assertSame(TABLE, CorpseLootTable.defaults().of("Tungsten"));
		assertNull(CorpseLootTable.defaults().of("Lapis"));
		// what the pages own chances use (research/16 s2)
		assertEquals(6_640, TABLE.totalWeight());
		assertEquals(38, TABLE.rows().size());
		assertEquals(5.5, TABLE.averageRolls());
	}

	@Test
	void worthIsRollsTimesTheAverageRoll() {
		// 1 coin each: weight x amount = 37,090, 5.586 a roll
		CorpseLootTable.Value value = CorpseLootTable.value(TABLE, row -> 1.0, 0.302);
		assertEquals(37_090.0 / 6_640, value.perRoll(), 1e-9);
		assertEquals(5.802, value.rolls(), 1e-9);
		assertEquals(37_090.0 / 6_640 * 5.802, value.worth(), 1e-9);
	}

	@Test
	void unpricedRowsCountAsNothingAndAreNamed() {
		CorpseLootTable.Value value = CorpseLootTable.value(TABLE, row -> row.id().equals("CAGED_WISP") ? null : 0.0, 0);
		assertEquals(0, value.worth());
		assertEquals(List.of("Caged Wisp"), value.unpriced());
	}
}
