package dev.skysurveyor.data;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.calc.GameData;
import dev.skysurveyor.calc.Layouts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ShaftNamesTest {
	@Test
	void namesKnownCodesAndKeepsUnknownOnes() {
		ShaftNames names = ShaftNames.defaults();
		assertEquals("Jasper", names.name("JASP"));
		assertEquals("Vanguard", names.name("FAIR"));
		assertEquals("ABCD", names.name("ABCD"));
		assertEquals(0xE0303A, names.rgb("RUBY"));
		assertEquals(ShaftNames.DEFAULT_RGB, names.rgb("ABCD"));
	}

	// ids from the scoreboard (research/12 s1)
	@Test
	void shaftNameAlwaysHasTheLayout() {
		ShaftNames names = ShaftNames.defaults();
		assertEquals("Jasper 1", names.shaftName("JASP_1"));
		assertEquals("Peridot 2", names.shaftName("PERI_2"));
		assertEquals("Jasper crystal", names.shaftName("JASP_C"));
		assertEquals("Vanguard 1", names.shaftName("FAIR_1"));
		assertEquals("Littlefoot", names.shaftName("LITT_L"));
		assertEquals("ABCD_1", names.shaftName("ABCD_1"));
		assertEquals("not an id", names.shaftName("not an id"));
		assertNull(names.shaftName(null));
		assertEquals("§dJasper crystal", names.coloredName("JASP_C"));
		assertEquals("§3Aquamarine 2", names.coloredName("AQUA", "2"));
		assertEquals("§fABCD_1", names.coloredName("ABCD_1"));
		assertNull(names.coloredName(null));
	}

	@Test
	void calculatorLayoutsUseTheSameNames() {
		ShaftNames names = ShaftNames.defaults();
		for (GameData.Layout layout : GameData.defaults().layouts()) {
			assertEquals(names.shaftName(layout.id()), Layouts.name(names, layout), layout.id());
		}
		assertEquals("Peridot 2", Layouts.name(names, GameData.defaults().layout("PERI_2")));
	}

	@Test
	void commentWithoutColorAndBadColorsAreHandled() {
		ShaftNames names = new ShaftNames(java.util.List.of(
				"TUNG = Tungsten   # unconfirmed",
				"UMBE = Umber, #8A93AG   # typo",
				"RUBY = Ruby, #E0303A # seen"));
		assertEquals("Tungsten", names.name("TUNG"));
		assertEquals(ShaftNames.DEFAULT_RGB, names.rgb("TUNG"));
		assertEquals("Umber", names.name("UMBE"));
		assertEquals(ShaftNames.DEFAULT_RGB, names.rgb("UMBE"));
		assertEquals(0xE0303A, names.rgb("RUBY"));
	}
}
