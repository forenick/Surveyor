package dev.skysurveyor.hud;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.data.ShaftNames;
import dev.skysurveyor.hud.HudData.SessionTotals;
import dev.skysurveyor.hud.HudData.Totals;
import dev.skysurveyor.hud.HudValue.Range;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdvancedTextTest {
	@Test
	void pastedNewlinesBecomeSpaces() {
		assertEquals("a b c", AdvancedText.oneLine("a\r\nb\nc"));
	}

	@Test
	void ruleLinesDropTrailingBlanks() {
		assertEquals(List.of("{cold} < -80 &c", "", "{tps} > 19 &a"), AdvancedText.ruleLines("{cold} < -80 &c\n\n{tps} > 19 &a\n \n"));
		assertEquals(List.of(), AdvancedText.ruleLines(""));
	}

	@Test
	void ruleLeadStartsANewLine() {
		assertEquals("{coins} > ", AdvancedText.ruleLead("", "coins"));
		assertEquals("{cold} < -80 &c\n{coins} > ", AdvancedText.ruleLead("{cold} < -80 &c", "coins"));
		assertEquals("{cold} < -80 &c\n{coins} > ", AdvancedText.ruleLead("{cold} < -80 &c\n", "coins"));
	}

	@Test
	void hintShowsTheFirstProblem() {
		assertEquals(AdvancedText.TEXT_HINT, AdvancedText.hint(List.of(), false));
		assertEquals("§cUnknown value {nope}. It shows as typed.", AdvancedText.hint(List.of("Unknown value {nope}"), false));
		assertEquals("§cDid you mean {coins}? It shows as typed.", AdvancedText.hint(List.of("Did you mean {coins}?"), false));
		assertEquals("§cfirst (and 2 more). It shows as typed.", AdvancedText.hint(List.of("first", "second", "third"), false));
		assertEquals("§cNo room for that, the text is 256 and the rules 512 characters max.",
				AdvancedText.hint(List.of("first"), true));
	}

	@Test
	void typedNumberLooksTyped() {
		HudData data = HudFixtures.live(SessionTotals.NONE, Totals.NONE, null, 102_000L, null, 0, ShaftNames.defaults()).cold(-63)
				.set(HudValue.TPS, null, 19.97).set(HudValue.COINS, Range.PER_HOUR, 46_800_000.0);
		HudExpr.Values values = HudBoxText.values(data, false);
		assertEquals("-63", AdvancedText.typedNumber("cold", values));
		assertEquals("1:42", AdvancedText.typedNumber("spawn", values));
		assertEquals("46.8M", AdvancedText.typedNumber("coins:per_hour", values));
		assertEquals("0", AdvancedText.typedNumber("coins", values), "missing");
		assertEquals("0", AdvancedText.typedNumber("nope +", values), "parse error");
	}

	@Test
	void exampleUsesTheFirstNumber() {
		HudData data = HudFixtures.live(SessionTotals.NONE, Totals.NONE, null, null, null, 0, ShaftNames.defaults()).cold(-63);
		HudExpr.Values values = HudBoxText.values(data, false);
		assertEquals("{coins} > 2M &a", AdvancedText.example("no numbers here", values));
		assertEquals("{cold} > -63 &c", AdvancedText.example("Cold: &b{cold}", values));
	}
}
