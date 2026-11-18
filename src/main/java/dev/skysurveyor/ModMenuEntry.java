package dev.skysurveyor;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import dev.skysurveyor.screen.SettingsScreen;

// only loaded by mod menu's "modmenu" entrypoint, nothing else may touch this class
public final class ModMenuEntry implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return SettingsScreen::new;
	}
}
