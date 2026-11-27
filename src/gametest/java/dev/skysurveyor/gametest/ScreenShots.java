package dev.skysurveyor.gametest;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.UIComponent;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import org.lwjgl.glfw.GLFW;

import dev.skysurveyor.Surveyor;
import dev.skysurveyor.data.ShaftNames;
import dev.skysurveyor.hud.EditHudScreen;
import dev.skysurveyor.hud.HudLayout;
import dev.skysurveyor.hud.HudRenderer;
import dev.skysurveyor.hud.HudValue;
import dev.skysurveyor.hud.ShaftToast;
import dev.skysurveyor.journal.JournalEntry;
import dev.skysurveyor.journal.JournalStats;
import dev.skysurveyor.journal.JournalStats.SessionSummary;
import dev.skysurveyor.journal.ToastText;
import dev.skysurveyor.recorder.MarkKey;
import dev.skysurveyor.screen.HoverFlow;
import dev.skysurveyor.screen.JournalScreen;
import dev.skysurveyor.screen.SettingsScreen;
import dev.skysurveyor.screen.Switch;
import dev.skysurveyor.settings.SummarySettings;

public class ScreenShots implements FabricClientGameTest {
	// 1080p at scale 2, 3, 4 (nick's rule)
	private static final int[] SCALES = {2, 3, 4};
	// … = starts w/
	private static final String STARTING = "Starting values until…";
	// same as the journal's
	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
	// data dependent shots that found nothing, logged at the end
	private static final List<String> SKIPPED = new ArrayList<>();

	@FunctionalInterface
	private interface Finder {
		UIComponent find(UIComponent root);
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		context.getInput().resizeWindow(1920, 1080);
		// item icons need a world
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			parkCursor(context);
			if (Boolean.getBoolean("skysurveyor.fresh")) {
				freshShots(context);
				// new install: auto-record off + unbound, so no mark key
				controlsShot(context, "controls-scale2");
				context.setScreen(() -> null);
				logSkipped();
				return;
			}
			// typed numbers, else no Reset all
			context.runOnClient(mc -> Surveyor.SETTINGS.change(s -> s.withCalculator(s.calculator().withTyped("spawn", 92.0).withTyped("setup", 30.0))));
			String biggest = null;
			for (int scale : SCALES) {
				setScale(context, scale);
				Surveyor.MONEY.refresh();
				context.setScreen(JournalScreen::new);
				context.waitTicks(20);
				context.takeScreenshot("journal-scale" + scale);

				// session w/ the most shafts
				if (biggest == null) biggest = clock(biggestSession(context).start());
				click(context, label(biggest), "session " + biggest);
				context.waitTicks(10);
				context.takeScreenshot("session-scale" + scale);

				clickButton(context, "Stats");
				context.waitTicks(10);
				context.takeScreenshot("stats-scale" + scale);
				// the longest pb names, below the fold at scale 4. no hover, its tip covers the rows
				if (has(context, label("Vanguard drystreak"))) {
					reveal(context, label("Vanguard drystreak"), "last pb row");
					parkCursor(context);
					context.waitTicks(5);
					context.takeScreenshot("stats-pb-list-scale" + scale);
				} else {
					skip("stats-pb-list-scale" + scale);
				}
				// pb list in edit mode + the builder
				click(context, button("Edit"), "Edit");
				context.waitTicks(10);
				context.takeScreenshot("stats-pb-edit-scale" + scale);
				click(context, button("+ Add PB"), "+ Add PB");
				context.waitTicks(10);
				context.takeScreenshot("stats-pb-new-scale" + scale);
				clickButton(context, "Cancel");
				context.waitTicks(5);
				click(context, button("Done"), "Done");
				context.waitTicks(10);

				clickButton(context, "Calculator (WIP)");
				context.waitTicks(10);
				// your numbers folded
				context.takeScreenshot("guide-scale" + scale);
				// name, then 0 to 4 lapis: 2 lapis
				hoverCell(context, "Peridot", 3);
				context.waitTicks(5);
				context.takeScreenshot("guide-cell-scale" + scale);
				hoverLabel(context, "Your call");
				context.waitTicks(5);
				context.takeScreenshot("guide-legend-scale" + scale);
				hoverLabel(context, "All-time");
				context.waitTicks(5);
				context.takeScreenshot("guide-range-scale" + scale);
				hoverLabel(context, "Leaving is worth");
				context.waitTicks(5);
				context.takeScreenshot("guide-leaving-scale" + scale);
				hoverLabel(context, "Peridot");
				context.waitTicks(5);
				context.takeScreenshot("guide-gem-scale" + scale);
				if (scale == 2) {
					hoverLabel(context, "Mining Fiesta");
					context.waitTicks(5);
					context.takeScreenshot("guide-fiesta-scale" + scale);
				}
				// the explanation, closed by default
				clickLabel(context, "? How it works");
				context.waitTicks(10);
				context.takeScreenshot("guide-help-scale" + scale);
				clickLabel(context, "? How it works");
				context.waitTicks(5);
				// numbers open: show, view chips, legend, reset all
				// below the fold on 3 + 4
				if (scale > 2) scrollTo(context, "Your numbers");
				clickLabel(context, "Show");
				context.waitTicks(10);
				context.takeScreenshot("guide-open-scale" + scale);
				scrollTo(context, "Your numbers");
				context.takeScreenshot("guide-numbers-scale" + scale);
				// below the fold on 3 + 4
				if (scale == 2) {
					hoverLabel(context, "Gems per block");
					context.waitTicks(5);
					context.takeScreenshot("guide-number-scale" + scale);
					hoverLabel(context, "Shafts of your spawn gem, Peridot");
					context.waitTicks(5);
					context.takeScreenshot("guide-spawn-gem-scale" + scale);
					hoverLabel(context, "Campfire adds");
					context.waitTicks(5);
					context.takeScreenshot("guide-campfire-adds-scale" + scale);
					parkCursor(context);
				}
				// asks twice
				clickButton(context, "Reset all");
				context.waitTicks(5);
				context.takeScreenshot("guide-reset-scale" + scale);
				// per layout + past shafts open on their own, numbers closed
				clickLabel(context, "Hide");
				context.waitTicks(10);
				clickLabel(context, "Per layout");
				context.waitTicks(10);
				scrollDown(context);
				context.takeScreenshot("guide-layouts-scale" + scale);
				hoverLabel(context, "Peridot 2");
				context.waitTicks(5);
				context.takeScreenshot("guide-layout-tip-scale" + scale);
				parkCursor(context);
				clickLabel(context, "Per layout");
				context.waitTicks(5);
				if (scale == 2) {
					clickLabel(context, "Past shafts");
					context.waitTicks(10);
					scrollDown(context);
					context.takeScreenshot("guide-past-scale" + scale);
					clickLabel(context, "Past shafts");
					context.waitTicks(5);
				}

				context.setScreen(() -> new SettingsScreen(null));
				context.waitTicks(10);
				context.takeScreenshot("settings-scale" + scale);
				for (String[] cat : new String[][]{{"In a shaft", "shaft"}, {"Journal", "journal"}, {"HUD", "hud"}, {"Bug reports", "dev"}}) {
					clickLabel(context, cat[0]);
					context.waitTicks(10);
					context.takeScreenshot("settings-" + cat[1] + "-scale" + scale);
				}
				// scale 2 = help pane, 3 and 4 = the row's hover
				clickLabel(context, "In a shaft");
				context.waitTicks(10);
				hoverLabel(context, "Shaft summary");
				shot(context, "settings-row-hover-scale" + scale);
				if (scale > 2) {
					hover(context, button("Toast"), "Toast option");
					shot(context, "settings-option-tip-scale" + scale);
				}
				parkCursor(context);

				context.setScreen(() -> new EditHudScreen(null, Surveyor.HUD));
				context.waitTicks(10);
				context.takeScreenshot("hud-editor-scale" + scale);
				placeShots(context, "hud-editor", scale);
				clickButton(context, "?");
				context.waitTicks(5);
				context.takeScreenshot("hud-help-scale" + scale);
				// scrolls on small screens
				double[] card = helpCard(context);
				if (card[4] > 0) {
					context.getInput().setCursorPos(card[0], card[1]);
					context.getInput().scroll(-60);
					context.waitTicks(5);
					parkCursor(context);
					context.waitTicks(3);
					context.takeScreenshot("hud-help-end-scale" + scale);
				}
				clickButton(context, "?");
				context.waitTicks(5);
				clickButton(context, "+ Preset");
				context.waitTicks(5);
				context.takeScreenshot("hud-preset-scale" + scale);
				// 1st click outside only closes it
				context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
				context.waitTicks(5);
				clickBox(context, "spawn");
				context.waitTicks(10);
				context.takeScreenshot("hud-edit-box-scale" + scale);
				// format buttons say 1:42, not m:ss
				click(context, sibling(up(label("Lines (…"), 1), 1), "first line");
				context.waitTicks(10);
				context.takeScreenshot("hud-line-page-scale" + scale);
				context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
				context.waitTicks(10);
				// periods are chips
				clickButton(context, "+ Add line");
				context.waitTicks(10);
				context.takeScreenshot("hud-add-line-scale" + scale);
				context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
				context.waitTicks(10);
			}
			setScale(context, 2);
			overviewExtras(context, biggest);
			sessionExtras(context, biggest);
			statsExtras(context);
			settingsExtras(context);
			editorExtras(context);
			overlays(context);
			// seeded config records, so the mark key is listed
			controlsShot(context, "controls-scale2");
			context.setScreen(() -> null);
			logSkipped();
		}
	}

	// vanilla controls at its end, where mod keys sit. the mark key is only there for recorders or once bound
	private static void controlsShot(ClientGameTestContext context, String name) {
		setScale(context, 2);
		context.setScreen(() -> new KeyBindsScreen(null, Minecraft.getInstance().options));
		context.waitTicks(10);
		boolean mark = context.computeOnClient(mc -> {
			try {
				Field field = KeyBindsScreen.class.getDeclaredField("keyBindsList");
				field.setAccessible(true);
				KeyBindsList keys = (KeyBindsList) field.get(mc.gui.screen());
				keys.setScrollAmount(keys.maxScrollAmount());
			} catch (ReflectiveOperationException e) {
				throw new AssertionError(e);
			}
			return Arrays.stream(mc.options.keyMappings).anyMatch(key -> key.getName().equals(MarkKey.NAME));
		});
		context.waitTicks(5);
		context.takeScreenshot(name);
		System.out.println("[shots] mark key in controls: " + mark);
	}

	// -Pfresh, no journal: empty screens + the starting values line on top
	private static void freshShots(ClientGameTestContext context) {
		for (int scale : SCALES) {
			setScale(context, scale);
			Surveyor.MONEY.refresh();
			context.setScreen(JournalScreen::new);
			context.waitTicks(20);
			context.takeScreenshot("journal-scale" + scale);
			clickButton(context, "Stats");
			context.waitTicks(10);
			context.takeScreenshot("stats-scale" + scale);
			clickButton(context, "Calculator (WIP)");
			context.waitTicks(20);
			context.takeScreenshot("guide-fresh-scale" + scale);
			hoverLabel(context, STARTING);
			context.waitTicks(5);
			context.takeScreenshot("guide-fresh-tip-scale" + scale);
			parkCursor(context);
			if (scale == 2) {
				clickLabel(context, "Show");
				context.waitTicks(10);
				context.takeScreenshot("guide-fresh-open-scale" + scale);
				clickLabel(context, "Hide");
				context.waitTicks(5);
			}
		}
		for (int scale : SCALES) {
			setScale(context, scale);
			context.setScreen(() -> new EditHudScreen(null, Surveyor.HUD));
			context.waitTicks(10);
			context.takeScreenshot("hud-editor-scale" + scale);
			placeShots(context, "hud-editor", scale);
		}
		setScale(context, 2);
		// spawn + shaft box share a spot, a click picks the one on top
		context.setScreen(() -> new EditHudScreen(null, Surveyor.HUD));
		context.waitTicks(10);
		clickBox(context, "spawn");
		context.waitTicks(10);
		context.takeScreenshot("hud-click-elsewhere-scale2");
		place(context, false, true);
		clickLabel(context, "HUD");
		context.waitTicks(5);
		clickBox(context, "spawn");
		context.waitTicks(10);
		context.takeScreenshot("hud-click-shaft-scale2");
		// same spot again = the box under it
		clickBox(context, "spawn");
		context.waitTicks(10);
		context.takeScreenshot("hud-click-through-scale2");
		place(context, null, null);
		// default shaft box title + shaft name in add line
		context.setScreen(() -> new EditHudScreen(null, Surveyor.HUD));
		context.waitTicks(10);
		place(context, false, true);
		clickBox(context, "shaft");
		context.waitTicks(10);
		context.takeScreenshot("hud-shaft-box-scale2");
		clickButton(context, "+ Add line");
		context.waitTicks(10);
		reveal(context, label("Shaft name"), "Shaft name");
		hoverLabel(context, "Shaft name");
		context.waitTicks(5);
		context.takeScreenshot("hud-add-line-shaft-name-scale2");
		parkCursor(context);
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(10);
		place(context, null, null);
		context.setScreen(() -> new SettingsScreen(null));
		context.waitTicks(10);
		context.takeScreenshot("settings-scale2");
		clickLabel(context, "In a shaft");
		context.waitTicks(10);
		context.takeScreenshot("settings-shaft-scale2");
		context.setScreen(() -> new EditHudScreen(null, Surveyor.HUD));
		context.waitTicks(10);
		clickButton(context, "+ Preset");
		context.waitTicks(5);
		context.takeScreenshot("hud-preset-scale2");
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTicks(5);
		clickBox(context, null);
		context.waitTicks(10);
		context.takeScreenshot("hud-edit-box-scale2");
	}

	private static void overviewExtras(ClientGameTestContext context, String biggest) {
		context.setScreen(JournalScreen::new);
		context.waitTicks(20);
		// only Lapis has a header tip on the overview
		if (has(context, label("Lapis"))) {
			hover(context, label("Lapis"), "Lapis header");
			shot(context, "overview-header-tip-scale2");
		} else {
			skip("overview-header-tip (no Lapis column)");
		}
		// every column has one since alpha.30
		maybeHover(context, label("Avg spawn"), "overview-spawn-tip-scale2");
		maybeHover(context, label("Coins / h"), "overview-coinrate-tip-scale2");
		hover(context, label(biggest), "session row");
		shot(context, "overview-row-hover-scale2");
		// length cell has the breaks tip
		hover(context, sibling(label(biggest), 1), "session length");
		shot(context, "overview-length-tip-scale2");
		String day = DAY.format(Instant.ofEpochMilli(biggestSession(context).start()).atZone(ZoneId.systemDefault()));
		if (has(context, label(day))) {
			hover(context, label(day), "day row");
			shot(context, "overview-day-hover-scale2");
			hover(context, sibling(label(day), 1), "day length");
			shot(context, "overview-day-length-tip-scale2");
		} else {
			skip("overview-day-hover (no " + day + " day row)");
		}
		hover(context, button("Settings"), "Settings button");
		shot(context, "topbar-hover-scale2");
		// the one tab that says what its for
		hover(context, button("Calculator (WIP)"), "Calculator tab");
		shot(context, "topbar-calculator-tip-scale2");
		parkCursor(context);
	}

	private static void sessionExtras(ClientGameTestContext context, String biggest) {
		context.setScreen(JournalScreen::new);
		context.waitTicks(20);
		click(context, label(biggest), "session " + biggest);
		context.waitTicks(10);
		SessionSummary session = biggestSession(context);
		// the row shows the name, not the id
		String other = session.entries().size() > 2 ? ShaftNames.defaults().shaftName(session.entries().get(2).shaftId()) : null;
		if (other != null && has(context, label(other))) {
			hover(context, label(other), "shaft row");
			shot(context, "session-row-hover-scale2");
		} else {
			skip("session-row-hover (no 3rd shaft id)");
		}
		hover(context, label("Corpses"), "Corpses header");
		shot(context, "session-header-tip-scale2");
		hover(context, label("Mined"), "Mined header");
		shot(context, "session-mined-header-tip-scale2");
		maybeHover(context, label("Shaft"), "session-shaft-header-tip-scale2");
		hover(context, label("Coins"), "Coins tile");
		shot(context, "session-coins-tip-scale2");
		context.getInput().holdShift();
		context.waitTicks(5);
		shot(context, "session-coins-shift-scale2");
		context.getInput().releaseShift();
		hover(context, label("Shafts / h"), "Shafts / h tile");
		shot(context, "session-rate-tip-scale2");
		maybeHover(context, label("Coins …"), "session-detail-coins-tip-scale2");
		maybeHover(context, label("Mined gems"), "session-detail-part-tip-scale2");
		maybeHover(context, label("Efficiency"), "session-detail-efficiency-tip-scale2");
		maybeHover(context, first(ItemComponent.class), "session-loot-tip-scale2");
		maybeHover(context, label("Outside shafts"), "session-outside-tip-scale2");
		// 3rd click = played order again
		click(context, label("Spawn"), "Spawn header");
		context.waitTicks(10);
		context.takeScreenshot("session-sorted-scale2");
		click(context, label("Spawn…"), "Spawn header");
		context.waitTicks(5);
		click(context, label("Spawn…"), "Spawn header");
		context.waitTicks(10);
		scrollIn(context, label(other != null ? other : "Corpses"), -60);
		context.takeScreenshot("session-table-scrolled-scale2");
		// Entered is also a column header
		scrollIn(context, label("Spawned in"), -60);
		context.takeScreenshot("session-detail-scrolled-scale2");
		hover(context, button("< All sessions"), "back button");
		shot(context, "session-back-hover-scale2");
		parkCursor(context);

		// strip instead of the side list at 480 wide
		setScale(context, 4);
		context.setScreen(JournalScreen::new);
		context.waitTicks(20);
		click(context, label(biggest), "session " + biggest);
		context.waitTicks(10);
		if (has(context, label("S"))) {
			click(context, label("S"), "Sessions strip");
			context.waitTicks(10);
			context.takeScreenshot("session-strip-open-scale4");
		} else {
			skip("session-strip-open (no strip)");
		}
		setScale(context, 2);
	}

	private static void statsExtras(ClientGameTestContext context) {
		context.setScreen(JournalScreen::new);
		context.waitTicks(20);
		clickButton(context, "Stats");
		context.waitTicks(10);
		hover(context, label("Coins"), "Coins total");
		shot(context, "stats-coins-tip-scale2");
		context.getInput().holdShift();
		context.waitTicks(5);
		shot(context, "stats-coins-shift-scale2");
		context.getInput().releaseShift();
		hover(context, label("Time shafting"), "Time shafting");
		shot(context, "stats-time-tip-scale2");
		maybeHover(context, label("Campfires"), "stats-campfires-hover-scale2");
		maybeHover(context, label("Efficiency"), "stats-efficiency-tip-scale2");
		hover(context, label("Lapis"), "Lapis corpses");
		shot(context, "stats-corpse-tip-scale2");
		maybeHover(context, nthLabel("Lapis", 1), "stats-corpse-share-tip-scale2");
		maybeHover(context, nthLabel("Jade", 0), "stats-type-tip-scale2");
		maybeHover(context, nthLabel("Jade", 1), "stats-type-coins-tip-scale2");
		maybeHover(context, nthLabel("Jade", 2), "stats-type-efficiency-tip-scale2");
		// a type w/ more than 1 layout
		maybeHover(context, nthLabel("Peridot", 0), "stats-type-layouts-tip-scale2");
		maybeHover(context, nthLabel("Peridot", 1), "stats-type-layouts-coins-tip-scale2");
		maybeHover(context, nthLabel("Peridot", 2), "stats-type-layouts-efficiency-tip-scale2");
		hover(context, label("Gems / shaft"), "Gems PB");
		shot(context, "stats-pb-hover-scale2");

		// range chips: the word's tip, a chip's tip, then one pick and back
		hover(context, label("Range"), "range word");
		shot(context, "stats-range-tip-scale2");
		hover(context, label("Since patch 0.27.3"), "since patch chip");
		shot(context, "stats-range-chip-tip-scale2");
		click(context, label("Last 7 days"), "Last 7 days chip");
		context.waitTicks(10);
		context.takeScreenshot("stats-range-next-scale2");
		click(context, label("All-time"), "All-time chip");
		context.waitTicks(10);

		hover(context, tallestBar(), "chart bar");
		shot(context, "stats-chart-bar-scale2");
		maybeHover(context, label("Yellow line: …"), "stats-chart-legend-scale2");
		hover(context, child(up(label("Avg spawn per session"), 1), 1), "chart cycler");
		shot(context, "stats-chart-cycler-tip-scale2");
		// spawn chart as a line, then back to bars
		click(context, child(child(up(label("Avg spawn per session"), 1), 1), -1), "next chart arrow");
		context.waitTicks(10);
		context.takeScreenshot("stats-chart-line-scale2");
		hover(context, tallestBar(), "line chart point");
		shot(context, "stats-chart-line-hover-scale2");
		click(context, child(child(up(label("Avg spawn per session"), 1), 1), -2), "prev chart arrow");
		context.waitTicks(10);

		// pb opens its shaft
		click(context, label("Gems / shaft"), "Gems PB");
		context.waitTicks(10);
		context.takeScreenshot("stats-pb-open-scale2");
		clickButton(context, "< Stats");
		context.waitTicks(10);

		click(context, button("Edit"), "Edit");
		context.waitTicks(10);
		hover(context, label("Gems / shaft"), "pb edit row");
		shot(context, "stats-pb-edit-hover-scale2");
		hover(context, label("×"), "pb remove");
		shot(context, "stats-pb-remove-tip-scale2");
		// asks twice, the next click lets go of it
		if (has(context, button("Reset"))) {
			click(context, button("Reset"), "pb reset");
			context.waitTicks(10);
			maybeHover(context, button("Really reset?"), "stats-pb-reset-armed-scale2");
		} else {
			skip("stats-pb-reset-armed (default pb list)");
		}
		click(context, label("Gems / shaft"), "pb edit row");
		context.waitTicks(10);
		context.takeScreenshot("stats-pb-builder-edit-scale2");
		click(context, button("Gems / shaft ▾"), "value dropdown");
		context.waitTicks(10);
		context.takeScreenshot("stats-pb-dropdown-value-scale2");
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(5);
		click(context, button("Any ▾"), "shaft dropdown");
		context.waitTicks(10);
		context.takeScreenshot("stats-pb-dropdown-shaft-scale2");
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(5);
		click(context, first(TextBoxComponent.class), "pb name field");
		context.getInput().typeChars("My best gems");
		context.waitTicks(10);
		context.takeScreenshot("stats-pb-builder-named-scale2");
		clickButton(context, "Cancel");
		context.waitTicks(5);
		click(context, button("Done"), "Done");
		context.waitTicks(10);
	}

	private static void settingsExtras(ClientGameTestContext context) {
		context.setScreen(() -> new SettingsScreen(null));
		context.waitTicks(10);
		scrollIn(context, label("Ironman"), -60);
		context.takeScreenshot("settings-money-bottom-scale2");
		hover(context, label("Sell price"), "Sell price row");
		shot(context, "settings-help-choice-scale2");
		hover(context, first(Switch.class), "Ironman switch");
		shot(context, "settings-toggle-tip-scale2");
		// on: selling, gems and metal dim
		click(context, first(Switch.class), "Ironman switch");
		context.waitTicks(10);
		context.takeScreenshot("settings-ironman-on-scale2");
		hover(context, sibling(label("Ironman"), 1), "reset icon");
		shot(context, "settings-reset-tip-scale2");
		parkCursor(context);
		context.waitTicks(5);
		click(context, button("Reset all"), "Reset all");
		context.waitTicks(5);
		context.takeScreenshot("settings-reset-armed-scale2");
		click(context, button("Really reset all?"), "Really reset all");
		context.waitTicks(10);
		context.takeScreenshot("settings-reset-done-scale2");
		hover(context, label("Bug reports"), "Bug reports entry");
		shot(context, "settings-side-hover-scale2");
		parkCursor(context);

		clickLabel(context, "In a shaft");
		context.waitTicks(10);
		for (String[] row : new String[][]{{"Entry title", "title"}, {"Fossil waypoints", "waypoint"}, {"Toast style", "toast"}}) {
			hover(context, label(row[0]), row[0]);
			shot(context, "settings-preview-" + row[1] + "-scale2");
		}
		// changed: yellow edges, count in the side, toast + chat preview
		click(context, button("Both"), "Both");
		click(context, button("Frame"), "Frame");
		context.waitTicks(5);
		hover(context, label("Shaft summary"), "Shaft summary");
		shot(context, "settings-changed-both-scale2");
		hover(context, label("Toast style"), "Toast style");
		shot(context, "settings-changed-frame-scale2");
		// chat only folds the toast settings away
		click(context, button("Chat"), "Chat");
		context.waitTicks(10);
		hover(context, label("Shaft summary"), "Shaft summary");
		shot(context, "settings-folded-toast-scale2");
		click(context, first(Switch.class), "Entry title switch");
		context.waitTicks(10);
		hover(context, label("Entry title"), "Entry title");
		shot(context, "settings-folded-title-scale2");
		parkCursor(context);

		Finder swatch = child(child(up(label("Fossil waypoints"), 3), 2), 0);
		hover(context, swatch, "waypoint swatch");
		shot(context, "settings-swatch-tip-scale2");
		click(context, swatch, "waypoint swatch");
		context.waitTicks(10);
		context.takeScreenshot("settings-color-picker-scale2");
		click(context, swatch, "waypoint swatch");
		context.waitTicks(5);

		// search finds folded ones too
		click(context, first(TextBoxComponent.class), "search");
		context.getInput().typeChars("toast");
		context.waitTicks(10);
		context.takeScreenshot("settings-search-folded-scale2");
		backspace(context, 5);
		context.getInput().typeChars("price");
		context.waitTicks(10);
		context.takeScreenshot("settings-search-scale2");
		backspace(context, 5);
		context.getInput().typeChars("zzz");
		context.waitTicks(10);
		context.takeScreenshot("settings-search-none-scale2");
		backspace(context, 3);
		context.waitTicks(5);
		parkCursor(context);
		context.waitTicks(5);
		click(context, button("Reset all"), "Reset all");
		click(context, button("Really reset all?"), "Really reset all");
		context.waitTicks(10);

		clickLabel(context, "Bug reports");
		context.waitTicks(10);
		click(context, button("Mining areas"), "Mining areas");
		context.waitTicks(10);
		context.takeScreenshot("settings-dev-auto-scale2");
		click(context, button("Off"), "Auto-record off");
		context.waitTicks(10);

		clickLabel(context, "HUD");
		context.waitTicks(10);
		click(context, button("Open the editor"), "Open the editor");
		context.waitTicks(10);
		context.takeScreenshot("settings-hud-editor-scale2");
		click(context, button("Done"), "Edit HUD done");
		context.waitTicks(10);
		context.takeScreenshot("settings-hud-back-scale2");
	}

	private static void editorExtras(ClientGameTestContext context) {
		context.setScreen(() -> new EditHudScreen(null, Surveyor.HUD));
		context.waitTicks(10);
		hover(context, button("+ New box"), "+ New box");
		shot(context, "hud-toolbar-tip-scale2");
		click(context, button("Reset all"), "Reset all");
		context.waitTicks(5);
		context.takeScreenshot("hud-reset-armed-scale2");
		// toolbar click lets go
		clickLabel(context, "Edit HUD");
		context.waitTicks(5);

		clickButton(context, "+ Preset");
		context.waitTicks(5);
		hover(context, label("Spawn"), "Spawn preset");
		shot(context, "hud-preset-tip-scale2");
		click(context, label("Performance"), "Performance preset");
		context.waitTicks(10);
		context.takeScreenshot("hud-preset-added-scale2");
		clickLabel(context, "HUD");
		context.waitTicks(5);

		clickBox(context, "spawn");
		context.waitTicks(10);
		Finder linesHead = up(label("Lines (…"), 1);
		Finder firstLine = sibling(linesHead, 1);
		hover(context, child(firstLine, 2), "line text");
		shot(context, "hud-line-tip-scale2");
		// 2nd line is per session, tag sits after the text
		Finder tag = r -> child(sibling(linesHead, 2), 3).find(r) instanceof LabelComponent label
				&& !"›".equals(ChatFormatting.stripFormatting(label.text().getString())) ? label : null;
		maybeHover(context, tag, "hud-period-tip-scale2");
		hover(context, label("Elsewhere"), "Elsewhere check");
		shot(context, "hud-showin-tip-scale2");
		click(context, sibling(label("Labels"), 1), "labels colour");
		context.waitTicks(5);
		context.takeScreenshot("hud-color-menu-scale2");
		hover(context, child(child(lastRootChild(), -1), 3), "colour swatch");
		shot(context, "hud-color-swatch-tip-scale2");
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(5);
		click(context, first(TextBoxComponent.class), "title field");
		context.getInput().pressKey(GLFW.GLFW_KEY_END);
		context.getInput().typeChars(" test");
		context.waitTicks(10);
		context.takeScreenshot("hud-title-edit-scale2");
		click(context, child(child(up(linesHead, 1), -1), 0), "Delete box");
		context.waitTicks(5);
		context.takeScreenshot("hud-delete-armed-scale2");
		// any other click lets go
		click(context, label("Lines (…"), "Lines heading");
		context.waitTicks(5);

		// line page, easy then advanced
		click(context, firstLine, "first line");
		context.waitTicks(10);
		context.takeScreenshot("hud-line-page-scale2");
		hover(context, sibling(up(label("Mode"), 1), -1), "line preview");
		shot(context, "hud-line-preview-tip-scale2");
		if (has(context, label("Period"))) {
			hover(context, child(sibling(label("Period"), 1), 0), "period choice");
			shot(context, "hud-line-period-choice-tip-scale2");
		} else {
			skip("hud-line-period-choice-tip (no Period row)");
		}
		click(context, sibling(label("Colour"), 1), "line colour");
		context.waitTicks(5);
		context.takeScreenshot("hud-line-color-menu-scale2");
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(5);
		click(context, button("Advanced"), "Advanced");
		context.waitTicks(10);
		context.takeScreenshot("hud-line-advanced-scale2");
		hover(context, label("{…"), "value list row");
		shot(context, "hud-value-tip-scale2");
		click(context, first(TextBoxComponent.class), "value search");
		context.getInput().typeChars("coin");
		context.waitTicks(10);
		context.takeScreenshot("hud-value-search-scale2");
		backspace(context, 4);
		context.waitTicks(5);
		hover(context, label("Colour rules"), "rules heading");
		shot(context, "hud-rules-tip-scale2");
		click(context, button("+ Rule"), "+ Rule");
		context.waitTicks(10);
		context.takeScreenshot("hud-rule-added-scale2");
		click(context, button("Values"), "Values");
		context.waitTicks(10);
		context.takeScreenshot("hud-line-advanced-nolist-scale2");
		click(context, button("Values"), "Values");
		context.waitTicks(5);
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(10);
		context.takeScreenshot("hud-box-after-advanced-scale2");

		// help beside the panel
		clickButton(context, "?");
		context.waitTicks(5);
		context.takeScreenshot("hud-help-box-scale2");
		clickButton(context, "?");
		context.waitTicks(5);

		// new box goes straight to the picker
		clickButton(context, "+ New box");
		context.waitTicks(10);
		context.takeScreenshot("hud-new-box-scale2");
		hover(context, sibling(up(label("Pick a value…"), 1), 2), "first value");
		shot(context, "hud-add-line-tip-scale2");
		scrollIn(context, label("Pick a value…"), -10);
		context.takeScreenshot("hud-add-line-scrolled-scale2");
		scrollIn(context, label("Pick a value…"), -300);
		context.takeScreenshot("hud-add-line-bottom-scale2");
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitTicks(10);
		context.takeScreenshot("hud-new-box-empty-scale2");
		hover(context, sibling(button("+ New box"), -2), "undo");
		shot(context, "hud-undo-tip-scale2");
		hover(context, sibling(button("+ New box"), -1), "redo");
		shot(context, "hud-redo-tip-scale2");

		// drops line: Amount + Spent switches
		String[] drops = context.computeOnClient(mc -> {
			for (HudLayout.Box box : Surveyor.HUD.layout().boxes) {
				for (int i = 0; i < box.lines.size(); i++) {
					if (box.lines.get(i).hudValue() == HudValue.DROPS) return new String[]{box.id, String.valueOf(i)};
				}
			}
			return null;
		});
		if (drops != null) {
			clickLabel(context, "HUD");
			context.waitTicks(5);
			clickBox(context, drops[0]);
			context.waitTicks(10);
			click(context, sibling(up(label("Lines (…"), 1), 1 + Integer.parseInt(drops[1])), "drops line");
			context.waitTicks(10);
			context.takeScreenshot("hud-drops-page-scale2");
			context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			context.waitTicks(10);
		} else {
			skip("hud-drops-page (no drops line)");
		}

		// movable toast shows in the editor
		context.runOnClient(mc -> Surveyor.SETTINGS.change(s -> s.withSummary(s.summary().withPosition(SummarySettings.Position.MOVABLE))));
		clickLabel(context, "HUD");
		context.waitTicks(10);
		context.takeScreenshot("hud-editor-toast-scale2");
		clickBox(context, HudLayout.TOAST_ID);
		context.waitTicks(10);
		context.takeScreenshot("hud-toast-page-scale2");
		context.runOnClient(mc -> Surveyor.SETTINGS.change(s -> s.withSummary(s.summary().withPosition(SummarySettings.Position.TOP_RIGHT))));
		context.waitTicks(5);
	}

	// public api only, no fake game text
	private static void overlays(ClientGameTestContext context) {
		context.setScreen(() -> null);
		context.waitTicks(10);
		for (SummarySettings.Style style : SummarySettings.Style.values()) {
			context.runOnClient(mc -> {
				mc.gui.toastManager().clear();
				JournalEntry last = Surveyor.JOURNAL.entries().getLast();
				ToastText text = ToastText.of(last, null, Surveyor.MONEY.value(last), false);
				mc.gui.toastManager().addToast(new ShaftToast(mc.font, text, style, 20));
			});
			context.waitTicks(25);
			context.takeScreenshot("toast-" + style.name().toLowerCase(Locale.ROOT) + "-scale2");
		}
		context.runOnClient(mc -> mc.gui.toastManager().clear());
		// every box is hidden outside the mines, show them here
		context.runOnClient(mc -> {
			HudLayout layout = Surveyor.HUD.layout();
			layout.boxes.forEach(box -> box.showElsewhere = true);
			Surveyor.HUD.setLayout(layout);
		});
		context.waitTicks(20);
		context.takeScreenshot("world-hud-scale2");
		context.runOnClient(mc -> Surveyor.SETTINGS.change(s -> s.withDev(s.dev().withDebugOverlay(true))));
		context.waitTicks(20);
		context.takeScreenshot("world-debug-overlay-scale2");
		context.runOnClient(mc -> Surveyor.SETTINGS.change(s -> s.withDev(s.dev().withDebugOverlay(false))));
	}

	private static SessionSummary biggestSession(ClientGameTestContext context) {
		return context.computeOnClient(mc -> JournalStats.sessions(Surveyor.JOURNAL.entries(), Surveyor.JOURNAL.mining(0), Surveyor.JOURNAL.breaks())
				.stream().max(Comparator.comparingInt(SessionSummary::shafts)).orElseThrow(() -> new AssertionError("No sessions")));
	}

	private static String clock(long millis) {
		return CLOCK.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
	}

	private static void setScale(ClientGameTestContext context, int scale) {
		context.runOnClient(mc -> {
			mc.options.guiScale().set(scale);
			mc.resizeGui();
		});
	}

	private static void shot(ClientGameTestContext context, String name) {
		context.waitTicks(5);
		context.takeScreenshot(name);
	}

	private static void skip(String what) {
		SKIPPED.add(what);
	}

	private static void logSkipped() {
		System.out.println("[shots] skipped: " + (SKIPPED.isEmpty() ? "none" : String.join("; ", SKIPPED)));
	}

	private static void backspace(ClientGameTestContext context, int times) {
		for (int i = 0; i < times; i++) context.getInput().pressKey(GLFW.GLFW_KEY_BACKSPACE);
	}

	// owo buttons arent screen children
	private static void clickButton(ClientGameTestContext context, String text) {
		double[] at = context.computeOnClient(mc -> {
			ButtonComponent button = find(root(mc), text);
			if (button == null) throw new AssertionError("No button " + text + " on " + mc.gui.screen());
			int scale = mc.getWindow().getGuiScale();
			return new double[]{(button.x() + button.width() / 2.0) * scale, (button.y() + button.height() / 2.0) * scale};
		});
		context.getInput().setCursorPos(at[0], at[1]);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		parkCursor(context);
	}

	// chips are flows w/ a label
	private static void clickLabel(ClientGameTestContext context, String text) {
		hoverLabel(context, text);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		parkCursor(context);
	}

	// down till the label sits in the upper half
	private static void scrollTo(ClientGameTestContext context, String text) {
		hoverLabel(context, "Mine from");
		context.getInput().scroll(60);
		context.waitTicks(5);
		for (int i = 0; i < 80; i++) {
			boolean there = context.computeOnClient(mc -> {
				LabelComponent label = findLabel(root(mc), text);
				if (label == null) throw new AssertionError("No label " + text + " on " + mc.gui.screen());
				return label.y() < mc.getWindow().getGuiScaledHeight() / 2;
			});
			if (there) break;
			context.getInput().scroll(-1);
			context.waitTicks(2);
		}
		context.waitTicks(8);
		parkCursor(context);
		context.waitTicks(5);
	}

	// guide to its bottom
	private static void scrollDown(ClientGameTestContext context) {
		hoverLabel(context, "Mine from");
		context.getInput().scroll(-60);
		context.waitTicks(10);
		parkCursor(context);
		context.waitTicks(5);
	}

	private static void hoverLabel(ClientGameTestContext context, String text) {
		double[] at = context.computeOnClient(mc -> {
			LabelComponent label = findLabel(root(mc), text);
			if (label == null) throw new AssertionError("No label " + text + " on " + mc.gui.screen());
			int scale = mc.getWindow().getGuiScale();
			return new double[]{(label.x() + 4) * scale, (label.y() + label.height() / 2.0) * scale};
		});
		context.getInput().setCursorPos(at[0], at[1]);
	}

	// index = cell in the row, f.e. 2 lapis of a gem
	private static void hoverCell(ClientGameTestContext context, String rowLabel, int index) {
		double[] at = context.computeOnClient(mc -> {
			LabelComponent label = findLabel(root(mc), rowLabel);
			if (label == null) throw new AssertionError("No label " + rowLabel + " on " + mc.gui.screen());
			UIComponent row = label.parent().parent();
			UIComponent cell = ((ParentUIComponent) row).children().get(index);
			int scale = mc.getWindow().getGuiScale();
			return new double[]{(cell.x() + cell.width() / 2.0) * scale, (cell.y() + cell.height() / 2.0) * scale};
		});
		context.getInput().setCursorPos(at[0], at[1]);
	}

	// center, scrolled into view first
	private static void hover(ClientGameTestContext context, Finder finder, String what) {
		reveal(context, finder, what);
		double[] at = context.computeOnClient(mc -> center(mc, need(mc, finder, what)));
		context.getInput().setCursorPos(at[0], at[1]);
	}

	private static void click(ClientGameTestContext context, Finder finder, String what) {
		hover(context, finder, what);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		parkCursor(context);
	}

	// data dependent, skipped not failed
	private static void maybeHover(ClientGameTestContext context, Finder finder, String name) {
		if (!has(context, finder)) {
			skip(name);
			return;
		}
		hover(context, finder, name);
		shot(context, name);
	}

	private static boolean has(ClientGameTestContext context, Finder finder) {
		return context.computeOnClient(mc -> finder.find(root(mc)) != null);
	}

	// wheel over the scroll holding it
	private static void scrollIn(ClientGameTestContext context, Finder finder, double amount) {
		double[] at = context.computeOnClient(mc -> {
			ScrollContainer<?> scroll = scrollOf(need(mc, finder, "scroll anchor"));
			if (scroll == null) throw new AssertionError("No scroll around anchor on " + mc.gui.screen());
			return center(mc, scroll);
		});
		context.getInput().setCursorPos(at[0], at[1]);
		context.getInput().scroll(amount);
		context.waitTicks(10);
		parkCursor(context);
		context.waitTicks(5);
	}

	// one notch at a time till its top shows
	private static void reveal(ClientGameTestContext context, Finder finder, String what) {
		for (int i = 0; i < 150; i++) {
			double[] step = context.computeOnClient(mc -> {
				UIComponent c = need(mc, finder, what);
				ScrollContainer<?> scroll = scrollOf(c);
				if (scroll == null) return null;
				int top = scroll.y();
				int bottom = scroll.y() + scroll.height();
				if (c.y() >= top && c.y() + Math.min(c.height(), 12) <= bottom) return null;
				double[] at = center(mc, scroll);
				return new double[]{at[0], at[1], c.y() < top ? 1 : -1};
			});
			if (step == null) {
				if (i > 0) context.waitTicks(8);
				return;
			}
			context.getInput().setCursorPos(step[0], step[1]);
			context.getInput().scroll(step[2]);
			context.waitTicks(3);
		}
		throw new AssertionError("Couldnt scroll to " + what);
	}

	private static ScrollContainer<?> scrollOf(UIComponent c) {
		for (UIComponent p = c.parent(); p != null; p = p.parent()) {
			if (p instanceof ScrollContainer<?> scroll) return scroll;
		}
		return null;
	}

	private static double[] center(Minecraft mc, UIComponent c) {
		int scale = mc.getWindow().getGuiScale();
		return new double[]{(c.x() + c.width() / 2.0) * scale, (c.y() + c.height() / 2.0) * scale};
	}

	private static UIComponent need(Minecraft mc, Finder finder, String what) {
		UIComponent c = finder.find(root(mc));
		if (c == null) throw new AssertionError("No " + what + " on " + mc.gui.screen());
		return c;
	}

	private static Finder label(String text) {
		return r -> findLabel(r, text);
	}

	private static Finder nthLabel(String text, int n) {
		return r -> {
			List<LabelComponent> all = new ArrayList<>();
			allLabels(r, text, all);
			return all.size() > n ? all.get(n) : null;
		};
	}

	private static Finder button(String text) {
		return r -> find(r, text);
	}

	private static Finder first(Class<? extends UIComponent> type) {
		return r -> firstOf(r, type);
	}

	// negative = from the end
	private static Finder child(Finder of, int index) {
		return r -> {
			if (!(of.find(r) instanceof ParentUIComponent parent)) return null;
			List<? extends UIComponent> kids = parent.children();
			int i = index < 0 ? kids.size() + index : index;
			return i >= 0 && i < kids.size() ? kids.get(i) : null;
		};
	}

	private static Finder sibling(Finder of, int offset) {
		return r -> {
			UIComponent c = of.find(r);
			if (c == null || c.parent() == null) return null;
			List<? extends UIComponent> kids = c.parent().children();
			int i = kids.indexOf(c) + offset;
			return i >= 0 && i < kids.size() ? kids.get(i) : null;
		};
	}

	private static Finder up(Finder of, int levels) {
		return r -> {
			UIComponent c = of.find(r);
			for (int i = 0; i < levels && c != null; i++) c = c.parent();
			return c;
		};
	}

	// menus get added last
	private static Finder lastRootChild() {
		return r -> r instanceof ParentUIComponent parent && !parent.children().isEmpty() ? parent.children().getLast() : null;
	}

	// chart bars + line points
	private static Finder tallestBar() {
		return r -> {
			List<UIComponent> bars = new ArrayList<>();
			allOf(r, HoverFlow.Box.class, bars);
			return bars.stream().max(Comparator.comparingInt(UIComponent::height)).orElse(null);
		};
	}

	// null = where the test world is (elsewhere)
	private static void place(ClientGameTestContext context, Boolean mines, Boolean shaft) {
		context.runOnClient(mc -> Surveyor.HUD.testPlace(mines, shaft));
		context.waitTicks(5);
	}

	// the editor in the mines and in a shaft
	private static void placeShots(ClientGameTestContext context, String name, int scale) {
		place(context, true, false);
		context.takeScreenshot(name + "-mines-scale" + scale);
		place(context, false, true);
		context.takeScreenshot(name + "-shaft-scale" + scale);
		place(context, null, null);
	}

	// boxes are drawn, not owo parts
	@SuppressWarnings("unchecked")
	private static void clickBox(ClientGameTestContext context, String id) {
		double[] at = context.computeOnClient(mc -> {
			try {
				Field field = EditHudScreen.class.getDeclaredField("drawn");
				field.setAccessible(true);
				Map<HudLayout.Box, HudRenderer.Bounds> drawn = (Map<HudLayout.Box, HudRenderer.Bounds>) field.get(mc.gui.screen());
				String wanted = id != null ? id : Surveyor.HUD.layout().boxes.getFirst().id;
				for (Map.Entry<HudLayout.Box, HudRenderer.Bounds> box : drawn.entrySet()) {
					if (!wanted.equals(box.getKey().id)) continue;
					HudRenderer.Bounds b = box.getValue();
					int scale = mc.getWindow().getGuiScale();
					return new double[]{(b.x() + b.width() / 2.0) * scale, (b.y() + b.height() / 2.0) * scale};
				}
				throw new AssertionError("No box " + wanted + " drawn");
			} catch (ReflectiveOperationException e) {
				throw new AssertionError("Not the hud editor: " + mc.gui.screen(), e);
			}
		});
		context.getInput().setCursorPos(at[0], at[1]);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		parkCursor(context);
	}

	// x, y center, then w, h, hidden part. 0 hidden = no scroll
	private static double[] helpCard(ClientGameTestContext context) {
		return context.computeOnClient(mc -> {
			try {
				Field helpField = EditHudScreen.class.getDeclaredField("help");
				helpField.setAccessible(true);
				Object help = helpField.get(mc.gui.screen());
				int x = intField(help, "x");
				int y = intField(help, "y");
				int w = intField(help, "w");
				int h = intField(help, "h");
				int full = intField(help, "height");
				int scale = mc.getWindow().getGuiScale();
				return new double[]{(x + w / 2.0) * scale, (y + h / 2.0) * scale, w, h, Math.max(0, full - h)};
			} catch (ReflectiveOperationException e) {
				throw new AssertionError("No help card on " + mc.gui.screen(), e);
			}
		});
	}

	private static int intField(Object of, String name) throws ReflectiveOperationException {
		Field field = of.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.getInt(of);
	}

	private static LabelComponent findLabel(UIComponent component, String text) {
		if (component instanceof LabelComponent label && matches(label, text)) return label;
		if (component instanceof ParentUIComponent parent) {
			for (UIComponent child : parent.children()) {
				LabelComponent found = findLabel(child, text);
				if (found != null) return found;
			}
		}
		return null;
	}

	private static void allLabels(UIComponent component, String text, List<LabelComponent> into) {
		if (component instanceof LabelComponent label && matches(label, text)) into.add(label);
		if (component instanceof ParentUIComponent parent) {
			for (UIComponent child : parent.children()) allLabels(child, text, into);
		}
	}

	private static boolean matches(LabelComponent label, String text) {
		String shown = ChatFormatting.stripFormatting(label.text().getString());
		return text.equals(shown) || text.endsWith("…") && shown.startsWith(text.substring(0, text.length() - 1));
	}

	private static UIComponent firstOf(UIComponent component, Class<? extends UIComponent> type) {
		if (type.isInstance(component)) return component;
		if (component instanceof ParentUIComponent parent) {
			for (UIComponent child : parent.children()) {
				UIComponent found = firstOf(child, type);
				if (found != null) return found;
			}
		}
		return null;
	}

	private static void allOf(UIComponent component, Class<? extends UIComponent> type, List<UIComponent> into) {
		if (type.isInstance(component)) into.add(component);
		if (component instanceof ParentUIComponent parent) {
			for (UIComponent child : parent.children()) allOf(child, type, into);
		}
	}

	// so nothing shows hovered
	private static void parkCursor(ClientGameTestContext context) {
		context.getInput().setCursorPos(0, 0);
	}

	private static UIComponent root(Minecraft mc) {
		try {
			Field field = BaseOwoScreen.class.getDeclaredField("uiAdapter");
			field.setAccessible(true);
			return ((OwoUIAdapter<?>) field.get(mc.gui.screen())).rootComponent;
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("Not an owo screen: " + mc.gui.screen(), e);
		}
	}

	// drawn buttons have no message, their name is the narration's
	private static String buttonText(ButtonComponent button) {
		String message = ChatFormatting.stripFormatting(button.getMessage().getString());
		if (!message.isEmpty()) return message;
		try {
			Field name = button.getClass().getDeclaredField("name");
			name.setAccessible(true);
			return ChatFormatting.stripFormatting((String) name.get(button));
		} catch (ReflectiveOperationException | ClassCastException e) {
			return message;
		}
	}

	private static ButtonComponent find(UIComponent component, String text) {
		if (component instanceof ButtonComponent button && text.equals(buttonText(button))) return button;
		if (component instanceof ParentUIComponent parent) {
			for (UIComponent child : parent.children()) {
				ButtonComponent found = find(child, text);
				if (found != null) return found;
			}
		}
		return null;
	}
}
