package dev.skysurveyor;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import dev.skysurveyor.screen.SettingsScreen;

// only mod menus "modmenu" entrypoint may load this, nothing else touches it
public final class ModMenuEntry implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return SettingsScreen::new;
	}
}
