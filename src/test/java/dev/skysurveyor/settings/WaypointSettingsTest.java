package dev.skysurveyor.settings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointSettingsTest {
	@Test
	void colorRoundTripsAndBadTextIsDefault() {
		WaypointSettings green = WaypointSettings.DEFAULTS.withColor(0x55FF55);
		assertEquals("#55FF55", green.color());
		assertEquals(0x55FF55, green.colorRgb());
		assertEquals(WaypointSettings.DEFAULTS, new WaypointSettings(null, "purple").repaired());
		assertEquals("#abcdef", new WaypointSettings(false, "#abcdef").repaired().color());
		assertTrue(WaypointSettings.DEFAULTS.isFossil());
	}

	@Test
	void toggleWithASwatchResetsBoth() {
		Setting.Toggle fossil = (Setting.Toggle) SettingsList.CATEGORIES.stream().flatMap(c -> c.settings().stream())
				.filter(s -> s.name().equals("Fossil waypoints")).findFirst().orElseThrow();
		Settings changed = Settings.DEFAULTS.withWaypoints(new WaypointSettings(true, "#00FF00"));
		assertFalse(fossil.isDefault(changed), "colour alone counts");
		assertEquals(Settings.DEFAULTS, fossil.reset(changed));
		assertEquals("On, #FF55FF", fossil.defaultText());
	}
}
