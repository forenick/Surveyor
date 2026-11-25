package dev.skysurveyor.hud;

import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import dev.skysurveyor.hud.HudLayout.DropsView;
import dev.skysurveyor.hud.HudLayout.ShowAs;
import dev.skysurveyor.hud.HudValue.Range;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

// names saved in hud.json or typed in {tags}. a rename drops players lines, so add here, never change
class HudSavedNamesTest {
	private static final Gson GSON = new Gson();

	private static final List<String> IDS = List.of("session_shafts", "shafts_per_hour", "average_spawn", "session_powder",
			"session_corpse_fine", "mined_metal", "spawn_timer", "drystreak", "pity", "since_last_shaft", "corpses_looted", "session_time",
			"shafts_today", "efficiency", "personal_best", "shaft_type", "shaft_time", "shaft_corpses", "shaft_cold", "shaft_campfires", "coins",
			"coins_per_shaft", "outside_coins", "drops", "fps", "ping", "tps", "clock", "mod_version", "minecraft_version",
			"fossil_essence", "pristine_potato", "filet_o_fortune", "cold_resistance", "fiesta_flask");

	private static final List<String> TAGS = List.of("shafts", "shafts_per_hour", "avg_spawn", "shaft_powder", "corpse_fine",
			"mined_metal", "spawn", "dry", "pity", "since_shaft", "looted", "session_time", "shafts_today", "efficiency", "pb", "shaft_type",
			"time_inside", "corpses", "cold", "campfires", "coins", "coins_per_shaft", "outside_coins", "fps", "ping", "tps", "clock",
			"version", "mc_version", "fossil_essence", "pristine_potato", "filet_o_fortune", "cold_resistance", "fiesta_flask");

	@Test
	void valueIds() {
		for (String id : IDS) {
			HudValue value = HudValue.byId(id);
			assertNotNull(value, id);
			assertEquals(id, value.id());
		}
		for (HudValue value : HudValue.values()) assertTrue(IDS.contains(value.id()), "new value, pin it here: " + value.id());
	}

	@Test
	void tagsAndOldIdsInText() {
		for (String tag : TAGS) assertNotNull(HudValue.byTag(tag), tag);
		for (String id : IDS) {
			if (HudValue.byId(id).isTag()) assertSame(HudValue.byId(id), HudValue.byTag(id), "old id still reads: " + id);
		}
	}

	@Test
	void ranges() {
		Map<String, String> saved = Map.of("SHAFT", "shaft", "SESSION", "session", "PER_HOUR", "per_hour", "ALL_TIME", "all_time");
		saved.forEach((json, tag) -> {
			Range range = GSON.fromJson('"' + json + '"', Range.class);
			assertNotNull(range, json);
			assertEquals('"' + json + '"', GSON.toJson(range));
			assertSame(range, Range.byTagName(tag));
		});
	}

	@Test
	void showAs() {
		Map<String, ShowAs> saved = Map.of("both", ShowAs.BOTH, "graph", ShowAs.GRAPH);
		saved.forEach((text, as) -> {
			HudLayout.Line line = new HudLayout.Line(HudValue.PITY);
			line.showAs = text;
			assertEquals(as, line.showAs());
			line.showAs(ShowAs.TEXT);
			line.showAs(as);
			assertEquals(text, line.showAs);
		});
		HudLayout.Line line = new HudLayout.Line(HudValue.PITY);
		line.showAs(ShowAs.TEXT);
		assertEquals(null, line.showAs, "text = not written");
	}

	@Test
	void dropsViews() {
		Map<String, DropsView> saved = Map.of("sources", DropsView.SOURCES, "both", DropsView.BOTH);
		saved.forEach((text, view) -> {
			HudLayout.Line line = new HudLayout.Line(HudValue.DROPS);
			line.view = text;
			assertEquals(view, line.dropsView());
			line.dropsView(DropsView.ITEMS);
			assertEquals(null, line.view, "items = not written");
			line.dropsView(view);
			assertEquals(text, line.view);
		});
	}

	@Test
	void boxEnums() {
		for (String name : List.of("START", "MIDDLE", "END")) assertNotNull(GSON.fromJson('"' + name + '"', HudLayout.Anchor.class), name);
		for (String name : List.of("NONE", "DARK", "FRAME")) assertNotNull(GSON.fromJson('"' + name + '"', HudLayout.Background.class), name);
		for (String name : List.of("LEFT", "CENTER", "RIGHT")) assertNotNull(GSON.fromJson('"' + name + '"', HudLayout.Align.class), name);
	}
}
