package dev.skysurveyor.hud;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.data.ShaftNames;
import dev.skysurveyor.hud.HudData.SessionTotals;
import dev.skysurveyor.hud.HudData.Totals;
import dev.skysurveyor.hud.HudLayout.Box;
import dev.skysurveyor.hud.HudLayout.Line;
import dev.skysurveyor.hud.HudValue.Range;
import dev.skysurveyor.money.DropList;
import dev.skysurveyor.money.DropList.Kind;
import dev.skysurveyor.money.DropList.Row;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// made up coins
class HudDropsTest {
	private static HudData empty() {
		return HudFixtures.live(SessionTotals.NONE, Totals.NONE, null, null, null, 0, ShaftNames.defaults());
	}

	private static DropList list(int items, int unpriced) {
		List<Row> rows = new ArrayList<>();
		for (int i = 0; i < items; i++) rows.add(new Row(Kind.ITEM, "Item " + i, 2, 800_000 - i * 100_000, 'f'));
		return new DropList(rows, List.of(new Row(Kind.SPENT, "Tungsten Key", 1, -300_000, '5')), unpriced, false, false);
	}

	private static Box box(Line... lines) {
		Box box = new Box();
		box.id = "profit";
		box.title = "Profit";
		box.showTitle = true;
		box.lines.addAll(List.of(lines));
		return box;
	}

	private static Line drops(Range range) {
		Line line = new Line(HudValue.DROPS);
		line.setRange(range);
		return line;
	}

	@Test
	void rowsUnderTheLines() {
		Line drops = drops(Range.SESSION);
		HudData data = empty().drops(Range.SESSION, list(7, 2));
		List<HudRow> rows = HudBoxText.rows(box(drops), data, false);
		assertEquals("§e§lProfit", rows.getFirst().text());
		assertFalse(rows.getFirst().columns());
		List<HudRow> list = rows.subList(1, rows.size());
		assertEquals(7, list.size(), "5 + more + keys");
		assertTrue(list.getFirst().rule(), "rule above, the title is above it");
		assertTrue(list.stream().skip(1).noneMatch(HudRow::rule));
		assertEquals(HudRow.columns("§fItem 0 §8x2", "§6+800.0k", true), list.get(0), "name then amount, like hypixel");
		assertEquals(HudRow.columns("§72 more", "§6+500.0k", false), list.get(5));
		assertEquals(HudRow.columns("§fTungsten Key", "§c-300.0k", false), list.get(6), "spent last, x1 left out");

		drops.rows = 0;
		drops.hideAmounts = true;
		drops.hideSpent = true;
		list = HudBoxText.rows(box(drops), data, false).subList(1, 9);
		assertEquals(HudRow.columns("§fItem 0", "§6+800.0k", true), list.getFirst());
		assertEquals(HudRow.columns("§72 items without a price", "", false), list.getLast(), "all rows: no price row");
		assertEquals(9, HudBoxText.rows(box(drops), data, false).size(), "no keys row");
	}

	private static DropList withSources() {
		List<Row> jade = List.of(new Row(Kind.GEM, "Fine Jade Gemstone", 364, 10_400_000, '9'), new Row(Kind.ITEM, "Glossy Gemstone", 5, 6_900_000, 'f'),
				new Row(Kind.ITEM, "Refined Mineral", 35, 3_400_000, 'f'));
		List<DropList.Source> sources = List.of(
				new DropList.Source(new Row(Kind.SOURCE, "Mined gems", 0, 42_700_000, 'f'), List.of(new Row(Kind.GEM, "Fine Jasper Gemstone", 931, 42_700_000, '9'))),
				new DropList.Source(new Row(Kind.SOURCE, "Outside shafts", 0, 20_700_000, 'f'), jade));
		return new DropList(List.of(new Row(Kind.GEM, "Fine Jasper Gemstone", 931, 42_700_000, '9')), List.of(new Row(Kind.SPENT, "Tungsten Key", 3, -10_400_000, '5')),
				0, false, false, sources);
	}

	@Test
	void sourcesAndBoth() {
		Line drops = drops(Range.SESSION);
		drops.dropsView(HudLayout.DropsView.SOURCES);
		assertEquals("sources", drops.view);
		HudData data = empty().drops(Range.SESSION, withSources());
		List<HudRow> rows = HudBoxText.rows(box(drops), data, false);
		assertEquals(List.of(HudRow.columns("§fMined gems", "§6+42.7M", true), HudRow.columns("§fOutside shafts", "§6+20.7M", false),
				HudRow.columns("§fTungsten Key §8x3", "§c-10.4M", false)), rows.subList(1, rows.size()), "no amounts on sources");

		drops.dropsView(HudLayout.DropsView.BOTH);
		rows = HudBoxText.rows(box(drops), data, false);
		assertEquals(List.of(HudRow.columns("§fMined gems", "§6+42.7M", true), HudRow.columns("§fOutside shafts", "§6+20.7M", false),
				new HudRow("§9Fine Jade Gemstone §8x364", null, "§7+10.4M", false, HudRow.SUB_INDENT), HudRow.sub("§fGlossy Gemstone §8x5", "§7+6.9M"),
				HudRow.columns("§fTungsten Key §8x3", "§c-10.4M", false)), rows.subList(1, rows.size()), "2 biggest, a single item gets none");
		drops.rows = 1;
		assertEquals(6, HudBoxText.rows(box(drops), data, false).size(), "title + all, sub rows dont count, 1 left over shows itself");
	}

	@Test
	void unknownViewIsItems() {
		Line drops = drops(Range.SESSION);
		drops.view = "pie";
		assertEquals(HudLayout.DropsView.ITEMS, drops.dropsView());
		HudData data = empty().drops(Range.SESSION, withSources());
		assertEquals("§9Fine Jasper Gemstone §8x931", HudBoxText.rows(box(drops), data, false).get(1).text());
		HudLayout layout = new HudLayout();
		layout.boxes.add(box(drops));
		String json = layout.toJson();
		assertTrue(json.contains("\"view\": \"pie\""), "kept");
		assertEquals(json, HudLayout.fromJson(json).toJson());
		drops.dropsView(HudLayout.DropsView.ITEMS);
		assertNull(drops.view, "items not written");
	}

	@Test
	void firstRowNoRule() {
		Box box = box(drops(Range.SESSION));
		box.showTitle = false;
		List<HudRow> rows = HudBoxText.rows(box, empty().drops(Range.SESSION, list(1, 0)), false);
		assertFalse(rows.getFirst().rule());
		assertEquals(2, rows.size());
	}

	@Test
	void emptyAndWaiting() {
		Box box = box(drops(Range.SESSION));
		assertEquals(HudRow.columns("§7No drops yet", "", true), HudBoxText.rows(box, empty(), false).get(1));
		assertEquals(HudRow.columns("§7No drops yet", "", true),
				HudBoxText.rows(box, empty().drops(Range.SESSION, DropList.NONE), false).get(1));
		assertEquals(HudRow.columns("§7No prices yet", "", true),
				HudBoxText.rows(box, empty().drops(Range.SESSION, DropList.NO_PRICES), false).get(1));
		DropList unpriced = new DropList(List.of(), List.of(), 3, true, false);
		assertEquals(HudRow.columns("§73 items with no NPC price", "", true),
				HudBoxText.rows(box, empty().drops(Range.SESSION, unpriced), false).get(1), "nothing else to show");
		assertEquals(8, HudBoxText.rows(box, empty(), true).size(), "editor shows the mockup rows");
	}

	@Test
	void allTimeWaitsForTheIoThread() {
		Box box = box(drops(Range.ALL_TIME));
		HudRow waiting = HudBoxText.rows(box, empty(), false).get(1);
		assertTrue(waiting.text().startsWith("§7."), "dots like other all-time values");
		assertTrue(waiting.rule());
		assertEquals(8, HudBoxText.rows(box, empty(), true).size(), "editor shows the example");
		assertEquals("§9Fine Jasper Gemstone §8x931", HudBoxText.rows(box, empty().drops(Range.ALL_TIME, list(0, 0)).drops(Range.ALL_TIME, withSources()), false)
				.get(1).text());
	}

	@Test
	void thisShaftOutside() {
		Line shaft = drops(Range.SHAFT);
		HudData data = empty().drops(Range.SHAFT, list(2, 0));
		assertEquals(4, HudBoxText.rows(box(shaft), data, false).size(), "the last shaft");
		shaft.dashOutside = true;
		assertEquals(List.of(HudRow.columns("§7-", "", true)), HudBoxText.rows(box(shaft), data, false).subList(1, 2));
	}

	@Test
	void editorText() {
		Box box = box();
		Line drops = drops(Range.SHAFT);
		assertEquals("", drops.prefix);
		assertEquals("§7Drops, 5 rows", HudBoxText.line(box, drops, HudData.EXAMPLE, true));
		drops.rows = 0;
		assertEquals("§7Drops, all rows", HudBoxText.line(box, drops, HudData.EXAMPLE, true));
		drops.setPeriod(HudValue.DROPS.period(Range.SESSION));
		assertEquals("", drops.prefix, "every default label is empty");
		assertEquals(Range.SESSION, drops.range());
		assertEquals("12 items", HudBoxText.text(HudValue.DROPS, Range.SESSION, 0, HudData.EXAMPLE, true));
		assertEquals("-", HudBoxText.text(HudValue.DROPS, Range.SESSION, 0, empty(), false));
	}

	@Test
	void notATag() {
		Box box = box();
		Line coins = new Line(HudValue.COINS);
		coins.advanced = true;
		coins.text = "&7Drops {drops} and {coins}";
		assertEquals("§7§7Drops {drops} and 14.8M", HudBoxText.line(box, coins, HudData.EXAMPLE, true), "stays as typed");
		assertFalse(HudBoxText.problems("{drops}").isEmpty());
		assertNull(HudValue.byTag("drops"));
	}

	@Test
	void fieldsRoundTrip() {
		HudLayout layout = new HudLayout();
		Line drops = drops(Range.SHAFT);
		layout.boxes.add(box(drops));
		String plain = layout.toJson();
		assertFalse(plain.contains("rows") || plain.contains("hide"), "defaults not written");
		drops.rows = 8;
		drops.hideAmounts = true;
		drops.hideBars = true;
		drops.hideSpent = true;
		String json = layout.toJson();
		Line read = HudLayout.fromJson(json).boxes.getFirst().lines.getFirst();
		assertEquals(8, read.rowsOrDefault());
		assertTrue(read.hideAmounts && read.hideBars && read.hideSpent);
		assertEquals(json, HudLayout.fromJson(json).toJson());
		assertTrue(json.indexOf("\"showAs\"") < 0 || json.indexOf("\"showAs\"") < json.indexOf("\"rows\""), "new fields last");
	}

	@Test
	void sessionProfitEndsWithDrops() {
		Box money = HudLayout.defaults().boxes.get(3);
		Line last = money.lines.getLast();
		assertEquals(HudValue.DROPS, last.hudValue());
		assertEquals(Range.SESSION, last.range());
		assertNull(last.rows);
		assertNull(last.valueColor);
	}
}
