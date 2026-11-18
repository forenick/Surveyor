package dev.skysurveyor.screen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.journal.JournalEntry;
import dev.skysurveyor.screen.TableColumns.Column;

import static dev.skysurveyor.journal.EntryBuilder.entry;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

// made up widths, every shaft column 10 wide so each one costs 10 + gap
class TableColumnsTest {
	private static final int GAP = 6;
	private static final List<Column> EVEN = TableColumns.SHAFTS.stream().map(c -> c.withWidth(10)).toList();

	private static List<String> keys(List<Column> columns) {
		return columns.stream().map(Column::key).toList();
	}

	private static int needed(List<Column> columns) {
		return columns.stream().mapToInt(Column::width).sum() + GAP * (columns.size() - 1);
	}

	@Test
	void allFit() {
		assertEquals(keys(EVEN), keys(TableColumns.fit(EVEN, needed(EVEN), GAP)));
	}

	@Test
	void dropsInNicksOrder() {
		List<String> dropped = new ArrayList<>();
		List<String> expected = List.of("campfire", "number", "time", "inside", "powder", "spawn");
		for (int step = 1; step <= expected.size(); step++) {
			List<String> shown = keys(TableColumns.fit(EVEN, needed(EVEN) - step * (10 + GAP), GAP));
			dropped.clear();
			for (String key : keys(EVEN)) if (!shown.contains(key)) dropped.add(key);
			// each step drops exactly the next one
			assertEquals(Set.copyOf(expected.subList(0, step)), Set.copyOf(dropped));
		}
	}

	@Test
	void shaftCorpsesMinedCoinsNeverDrop() {
		assertEquals(List.of("shaft", "corpses", "mined", "coins"), keys(TableColumns.fit(EVEN, 0, GAP)));
	}

	@Test
	void corpsesGetsNoSpareRoom() {
		List<Column> columns = List.of(new Column("shaft", "Shaft", 40, false, 0), new Column("corpses", "Corpses", 90, false, 0).asTight(),
				new Column("coins", "Coins", 20, true, 0));
		// 30 spare over the 60 wide stretchy ones: 2/3 to shaft, 1/3 to coins
		List<Column> fitted = TableColumns.fit(columns, 40 + 90 + 20 + 2 * GAP + 30, GAP);
		assertEquals(List.of(20, 0, 10), fitted.stream().map(Column::extra).toList());
	}

	@Test
	void spareCappedAtDoubleWidth() {
		List<Column> columns = List.of(new Column("shaft", "Shaft", 40, false, 0), new Column("corpses", "Corpses", 90, false, 0).asTight());
		List<Column> fitted = TableColumns.fit(columns, 1000, GAP);
		assertEquals(List.of(40, 0), fitted.stream().map(Column::extra).toList());
	}

	@Test
	void measuredNeverBelowFixed() {
		// vanilla font fits: width stays
		assertEquals(44, TableColumns.atLeast(44, 30));
		assertEquals(44, TableColumns.atLeast(44, 43));
		// wider font or "Nov 15 2025": grows, +1 slack
		assertEquals(45, TableColumns.atLeast(44, 44));
		assertEquals(61, TableColumns.atLeast(44, 60));
		// 0 = all measured
		assertEquals(31, TableColumns.atLeast(0, 30));
	}

	@Test
	void measureKeepsTheRest() {
		Column corpses = new Column("corpses", "Corpses", 40, false, 3).asTight();
		Column grown = TableColumns.measure(corpses, 50);
		assertEquals(51, grown.width());
		assertEquals(List.of("corpses", "Corpses", false, 3, true), List.of(grown.key(), grown.title(), grown.right(), grown.dropOrder(), grown.tight()));
		assertEquals(40, TableColumns.measure(corpses, 20).width());
	}

	@Test
	void sortOnlyWhileItsColumnShows() {
		List<Column> small = TableColumns.fit(EVEN, 0, GAP);
		assertEquals("coins", TableColumns.shownSort("coins", small));
		assertNull(TableColumns.shownSort("inside", small));
		assertEquals("inside", TableColumns.shownSort("inside", EVEN));
		assertNull(TableColumns.shownSort(null, EVEN));
	}

	@Test
	void overviewDropsLapisStartLengthRateFirst() {
		// widths made up
		List<Column> even = TableColumns.SESSIONS.stream().map(c -> c.withWidth(10)).toList();
		List<String> dropped = new ArrayList<>(keys(even));
		dropped.removeAll(keys(TableColumns.fit(even, needed(even) - 4 * (10 + GAP), GAP)));
		assertEquals(Set.of("lapis", "start", "length", "rate"), Set.copyOf(dropped));
		assertEquals(List.of("date", "shafts", "spawn", "powder", "coins"), keys(TableColumns.fit(even, 0, GAP)));
	}

	// made up shafts: spawn 30s, none, 10s, 30s. coins 5, null, 9, 5
	private static final List<JournalEntry> SHAFTS = List.of(
			entry().shaftId("TOPAZ_1").searchMillis(30_000L).build(), entry().shaftId("AMBER_1").build(),
			entry().shaftId("JADE_1").searchMillis(10_000L).build(), entry().shaftId("AMBER_2").searchMillis(30_000L).build());
	private static final List<Double> COINS = Arrays.asList(5.0, null, 9.0, 5.0);

	private static int[] order(String key, boolean flipped) {
		return TableColumns.shaftOrder(SHAFTS, COINS, key, flipped, 1);
	}

	@Test
	void noSortIsPlayedOrder() {
		assertArrayEquals(new int[] {0, 1, 2, 3}, order(null, false));
	}

	@Test
	void spawnFastestFirstThenFlipped() {
		// ties in played order, no spawn last both ways
		assertArrayEquals(new int[] {2, 0, 3, 1}, order("spawn", false));
		assertArrayEquals(new int[] {0, 3, 2, 1}, order("spawn", true));
	}

	@Test
	void coinsBiggestFirst() {
		assertArrayEquals(new int[] {2, 0, 3, 1}, order("coins", false));
		assertArrayEquals(new int[] {0, 3, 2, 1}, order("coins", true));
	}

	@Test
	void shaftNamesAToZ() {
		assertArrayEquals(new int[] {1, 3, 2, 0}, order("shaft", false));
	}

	@Test
	void campfiresPlacedOrUsed() {
		assertEquals(0, TableColumns.campfires(entry().build()));
		assertEquals(2, TableColumns.campfires(entry().campfiresAt(List.of(1L, 2L)).campfireColds(List.of(-50)).build()));
		assertEquals(3, TableColumns.campfires(entry().campfiresAt(List.of(1L)).campfireColds(Arrays.asList(-50, null, -70)).build()));
	}
}
