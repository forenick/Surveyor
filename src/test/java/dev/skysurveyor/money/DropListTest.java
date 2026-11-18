package dev.skysurveyor.money;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.money.DropList.Kind;
import dev.skysurveyor.money.DropList.Row;
import dev.skysurveyor.money.ShaftValue.Line;
import dev.skysurveyor.money.ShaftValue.Part;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// made up coins in the small tests, saved prices in the old saves
class DropListTest {
	private static final TextPatterns PATTERNS = TextPatterns.defaults();

	private static DropList of(List<Line> shafts, List<Line> outside) {
		return DropList.of(shafts, outside, false, PATTERNS);
	}

	private static Row row(DropList list, String name) {
		return list.items().stream().filter(r -> r.name().equals(name)).findFirst().orElseThrow(() -> new AssertionError(name));
	}

	@Test
	void gemsMergePerGemInFine() {
		DropList list = of(List.of(new Line(Part.MINED, "Flawless Jasper Gemstone", 2, 100.0),
				new Line(Part.CORPSES, "Rough Jasper Gemstone", 6_400, 10.0), new Line(Part.OTHER, "Flawed Jasper Gemstone", 80, 5.0),
				new Line(Part.GLOSSY, "Glossy Gemstone", 2, 300.0), new Line(Part.CORPSES, "Glossy Gemstone", 1, 150.0),
				new Line(Part.CRYSTALS, "Jasper Crystal", 1, 40.0), new Line(Part.CORPSES, "Weird Thing", 3, null),
				new Line(Part.MINED_METAL, "Enchanted Umber", 3.5, 50.0)),
				List.of(new Line(Part.MINED, "Fine Jasper Gemstone", 3, 7.0), new Line(Part.GLOSSY, "Glossy Gemstone", 1, 160.0),
						new Line(Part.MINED_METAL, "Enchanted Umber", 1.5, 20.0)));
		Row jasper = row(list, "Fine Jasper Gemstone");
		assertEquals(Kind.GEM, jasper.kind());
		assertEquals(160 + 1 + 1 + 3, jasper.count(), 1e-9, "flawless back to fine, rough + flawed through GemYield");
		assertEquals(122, jasper.coins(), 1e-9);
		assertEquals('9', jasper.color(), "counted in fine, fines rarity");
		Row glossy = row(list, "Glossy Gemstone");
		assertEquals(4, glossy.count(), 1e-9, "shafts, corpses + outside, one row");
		assertEquals(610, glossy.coins(), 1e-9);
		assertEquals(Kind.ITEM, row(list, "Jasper Crystal").kind(), "a crystal isnt a gem");
		Row umber = row(list, "Enchanted Umber");
		assertEquals(Kind.METAL, umber.kind());
		assertEquals(5, umber.count(), 1e-9);
		assertEquals('f', umber.color(), "no item list in tests");
		assertEquals(1, list.unpriced(), "counted, not a row");
		assertEquals(List.of("Glossy Gemstone", "Fine Jasper Gemstone", "Enchanted Umber", "Jasper Crystal"), list.items().stream().map(Row::name).toList(),
				"biggest first");
		assertEquals(842, list.total(), 1e-9);
	}

	@Test
	void spentRows() {
		DropList list = of(List.of(new Line(Part.MINED, "Fine Jade Gemstone", 10, 1_000.0), new Line(Part.CAMPFIRES, "Portable Campfire", 1, -30.0),
				new Line(Part.KEYS, "Umber Key", 2, -200.0), new Line(Part.KEYS, "Skeleton Key", 1, -50.0)), List.of());
		assertEquals(List.of(new Row(Kind.SPENT, "Umber Key", 2, -200, 'f'), new Row(Kind.SPENT, "Skeleton Key", 1, -50, 'f'),
				new Row(Kind.SPENT, "Portable Campfire", 1, -30, 'f')), list.spent(), "keys per key, biggest cost first, campfires last");
		assertEquals(720, list.total(), 1e-9);
		DropList free = of(List.of(new Line(Part.KEYS, "Umber Key", 1, 0.0), new Line(Part.CAMPFIRES, "Portable Campfire", 1, 0.0)), List.of());
		assertTrue(free.spent().isEmpty(), "dont count + ironman cost 0, no row");
	}

	@Test
	void restFoldsIntoMore() {
		List<Line> lines = new ArrayList<>();
		for (int i = 1; i <= 7; i++) lines.add(new Line(Part.OTHER, "Item " + i, 1, 100.0 * i));
		DropList seven = of(lines, List.of());
		List<Row> shown = seven.shown(5);
		assertEquals(6, shown.size());
		assertEquals(new Row(Kind.MORE, "2 more", 0, 300, '7'), shown.getLast());
		assertEquals(seven.total(), shown.stream().mapToDouble(Row::coins).sum(), 1e-9, "more holds the rest");
		DropList six = of(lines.subList(0, 6), List.of());
		assertEquals(6, six.shown(5).size(), "1 left over shows itself");
		assertTrue(six.shown(5).stream().noneMatch(r -> r.kind() == Kind.MORE));
		assertEquals(7, seven.shown(0).size(), "all");
	}

	@Test
	void sourcesPerPartOutsideAsOne() {
		DropList list = of(List.of(new Line(Part.MINED, "Flawless Jasper Gemstone", 1, 500.0), new Line(Part.CORPSES, "Rough Jasper Gemstone", 6_400, 10.0),
				new Line(Part.CORPSES, "Flawed Jasper Gemstone", 80, 5.0), new Line(Part.CORPSES, "Glossy Gemstone", 1, 150.0),
				new Line(Part.GLOSSY, "Glossy Gemstone", 2, 300.0), new Line(Part.OTHER, "Weird Thing", 3, null),
				new Line(Part.KEYS, "Umber Key", 1, -100.0)),
				List.of(new Line(Part.MINED, "Fine Jade Gemstone", 3, 60.0), new Line(Part.GLOSSY, "Glossy Gemstone", 1, 160.0)));
		assertEquals(List.of("Mined gems", "Glossy Gemstones", "Outside shafts", "Corpse loot"),
				list.sources().stream().map(s -> s.row().name()).toList(), "biggest first, no keys, unpriced only = no row");
		DropList.Source corpses = list.sources().getLast();
		assertEquals(165, corpses.row().coins(), 1e-9);
		assertEquals(List.of("Glossy Gemstone", "Fine Jasper Gemstone"), corpses.items().stream().map(Row::name).toList(), "gems merged inside it");
		assertEquals(2, corpses.items().getLast().count(), 1e-9, "1 + 1 fine");
		DropList.Source outside = list.sources().get(2);
		assertEquals(220, outside.row().coins(), 1e-9);
		assertEquals(Kind.SOURCE, outside.row().kind());
		double sum = list.sources().stream().mapToDouble(s -> s.row().coins()).sum() + list.spent().getFirst().coins();
		assertEquals(list.total(), sum, 1e-9, "sources add up like items");
		List<DropList.Source> two = list.shownSources(2);
		assertEquals(new Row(Kind.MORE, "2 more", 0, 385, '7'), two.getLast().row());
		assertEquals(4, list.shownSources(3).size(), "1 left over shows itself");
		assertEquals("", DropList.amount(outside.row()), "no amount on a source");
	}

	@Test
	void linesSumAndWait() {
		assertEquals(DropList.NONE, new DropList.Lines().list(false, PATTERNS), "nothing yet");
		assertEquals(DropList.NO_PRICES, new DropList.Lines().shaft(null).outside(null).list(false, PATTERNS));
		ShaftValue a = new ShaftValue(List.of(new Line(Part.MINED, "Flawless Jade Gemstone", 1, 100.0), new Line(Part.CORPSES, "Thing", 2, null),
				new Line(Part.KEYS, "Umber Key", 1, -40.0)), List.of());
		ShaftValue b = new ShaftValue(List.of(new Line(Part.MINED, "Flawless Jade Gemstone", 2, 200.0), new Line(Part.CORPSES, "Thing", 1, 5.0)),
				List.of());
		DropList.Lines in = new DropList.Lines().shaft(a).shaft(null).outside(List.of(new Line(Part.MINED, "Fine Jade Gemstone", 8, 10.0)));
		DropList.Lines all = in.plus(new DropList.Lines().shaft(b));
		DropList plain = all.list(false, PATTERNS);
		DropList summed = all.summed().list(false, PATTERNS);
		assertEquals(plain, summed, "summing per part + item changes nothing");
		assertEquals(275, summed.total(), 1e-9);
		assertEquals(3 * 80 + 8, row(summed, "Fine Jade Gemstone").count(), 1e-9);
		assertEquals(List.of("Mined gems", "Outside shafts", "Corpse loot"), summed.sources().stream().map(s -> s.row().name()).toList());
		assertEquals(3, row(summed, "Thing").count(), 1e-9, "a price in 1 shaft prices the item");
		assertEquals(70, in.list(false, PATTERNS).total(), 1e-9, "plus leaves both as they were");
	}

	@Test
	void amounts() {
		assertEquals("3.0", DropList.amount(new Row(Kind.GEM, "Jasper", 3.04, 1, 'd')));
		assertEquals("931", DropList.amount(new Row(Kind.GEM, "Jasper", 931.2, 1, 'd')));
		assertEquals("1,234", DropList.amount(new Row(Kind.ITEM, "Glacite", 1_234, 1, 'f')));
		assertEquals("3", DropList.amount(new Row(Kind.ITEM, "Glossy Gemstone", 3, 1, 'f')));
		assertEquals("5", DropList.amount(new Row(Kind.METAL, "Umber", 5, 1, '6')), "raw metal stays whole");
		assertEquals("3.5", DropList.amount(new Row(Kind.METAL, "Enchanted Umber", 3.5, 1, '6')));
		assertEquals("", DropList.amount(new Row(Kind.MORE, "2 more", 0, 1, '7')));
	}
}
