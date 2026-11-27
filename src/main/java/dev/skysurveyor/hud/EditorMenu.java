package dev.skysurveyor.hud;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.UIComponent;
import net.minecraft.util.FormattedCharSequence;

import static dev.skysurveyor.hud.EditorParts.COLOR_NAMES;
import static dev.skysurveyor.hud.EditorParts.MENU_SWATCH;
import static dev.skysurveyor.hud.EditorParts.OUTLINE_SELECTED;
import static dev.skysurveyor.hud.EditorParts.menuEntry;

final class EditorMenu {
	private static final Surface SURFACE = Surface.flat(0xF0101010).and(Surface.outline(0xFFA0A0A0));

	private final EditHudScreen screen;
	private final Map<UIComponent, List<FormattedCharSequence>> tips = new IdentityHashMap<>();
	private FlowLayout menu;
	private UIComponent anchor;

	EditorMenu(EditHudScreen screen) {
		this.screen = screen;
	}

	boolean isOpen() {
		return menu != null;
	}

	boolean contains(double x, double y) {
		return menu != null && menu.isInBoundingBox(x, y);
	}

	Map<UIComponent, List<FormattedCharSequence>> tips() {
		return tips;
	}

	void child(UIComponent child) {
		menu.child(child);
	}

	void tip(UIComponent c, String text) {
		tips.put(c, EditorParts.tipLines(screen.getFont(), text));
	}

	void open(UIComponent anchor, Consumer<EditorMenu> fill) {
		close();
		FlowLayout root = screen.root();
		menu = UIContainers.verticalFlow(Sizing.content(), Sizing.content());
		menu.surface(SURFACE);
		menu.padding(Insets.of(2));
		// else gaps pass clicks to the panel below
		menu.mouseDown().subscribe((click, doubled) -> true);
		menu.mouseScroll().subscribe((x, y, amount) -> true);
		this.anchor = anchor;
		fill.accept(this);
		menu.positioning(Positioning.absolute(anchor.x() - root.x() - EditHudScreen.PADDING,
				anchor.y() + anchor.height() + 1 - root.y() - EditHudScreen.PADDING));
		// owo lays out right away, no frame offscreen
		root.child(menu);
		fit();
	}

	// sizes only known after layout
	void fit() {
		if (menu == null || menu.width() == 0) return;
		int pad = EditHudScreen.PADDING;
		FlowLayout root = screen.root();
		int x = Math.max(pad, Math.min(menu.x(), screen.width - pad - menu.width()));
		int y = menu.y();
		if (y + menu.height() > screen.height - pad) y = Math.max(pad, anchor.y() - 1 - menu.height());
		if (x != menu.x() || y != menu.y()) menu.positioning(Positioning.absolute(x - root.x() - pad, y - root.y() - pad));
	}

	void close() {
		if (menu == null) return;
		tips.clear();
		screen.root().removeChild(menu);
		menu = null;
		anchor = null;
	}

	// boxColor null = no Box choice
	void colors(UIComponent anchor, String current, String boxColor, String valueDefault, boolean shaft, Consumer<String> pick) {
		open(anchor, m -> {
			if (boxColor != null) {
				child(menuEntry((current == null ? "§e" : "§f") + "Box colour §7(" + COLOR_NAMES[HudLayout.colorIndex(boxColor)] + ")",
						() -> pick.accept(null)));
			}
			if (valueDefault != null) {
				child(menuEntry((valueDefault.equals(current) ? "§e" : "§f") + "Default colour §7("
						+ COLOR_NAMES[HudLayout.colorIndex(valueDefault)] + ")", () -> pick.accept(valueDefault)));
			}
			if (shaft) {
				child(menuEntry((HudLayout.SHAFT_COLOR.equals(current) ? "§e" : "§f") + "Shaft colour",
						() -> pick.accept(HudLayout.SHAFT_COLOR)));
			}
			swatchGrid(current, pick);
		});
	}

	// dark row + light row, code order
	private void swatchGrid(String selected, Consumer<String> pick) {
		for (int half = 0; half < 2; half++) {
			FlowLayout swatches = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
			swatches.gap(2);
			swatches.margins(Insets.of(2, 1, 3, 3));
			for (int i = half * 8; i < half * 8 + 8; i++) {
				String code = String.valueOf(HudLayout.COLOR_CODES.charAt(i));
				FlowLayout swatch = UIContainers.horizontalFlow(Sizing.fixed(MENU_SWATCH), Sizing.fixed(MENU_SWATCH));
				swatch.surface(Surface.flat(0xFF000000 | HudLayout.COLOR_RGB[i])
						.and(Surface.outline(code.equals(selected) ? OUTLINE_SELECTED : 0xFF000000)));
				tip(swatch, COLOR_NAMES[i]);
				swatch.cursorStyle(CursorStyle.HAND);
				swatch.mouseDown().subscribe((click, doubled) -> {
					pick.accept(code);
					return true;
				});
				swatches.child(swatch);
			}
			child(swatches);
		}
	}
}
