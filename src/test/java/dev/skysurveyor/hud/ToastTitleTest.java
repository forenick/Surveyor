package dev.skysurveyor.hud;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.journal.ToastText;
import dev.skysurveyor.settings.SummarySettings.Style;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToastTitleTest {
	// same as replaceFirst("^(§[0-9a-f])", "$1§l").replaceFirst("  ", "§r  ")
	@Test
	void boldTitleLikeBefore() {
		for (String title : List.of(ToastText.example().title(), "§eJade Shaft  §73m 12s", "§eJade", "§aJade 1  here", "§lNo colour", "plain", "",
				"§e", "two  gaps  here")) {
			ToastText text = new ToastText(title, List.of());
			String before = title.replaceFirst("^(§[0-9a-f])", "$1§l").replaceFirst("  ", "§r  ");
			assertEquals(before, ToastRenderer.title(text, Style.DARK), title);
			assertEquals(title, ToastRenderer.title(text, Style.VANILLA));
		}
	}
}
