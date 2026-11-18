package dev.skysurveyor.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.UIComponent;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.network.chat.Component;
import org.joml.Vector2i;

// bits the journal, settings + hud editor share
public final class ScreenParts {
	// journal + settings, the editors is opaque
	public static final Surface PANEL = Surface.flat(0xE8141414).and(Surface.outline(0x30FFFFFF));
	// tooltip frame sticks out ~3px
	public static final int TIP_MARGIN = 5;
	// owo's tooltips dont wrap or stay on screen. right of the mouse if it fits, else left
	public static final ClientTooltipPositioner FIT = (screenW, screenH, x, y, w, h) -> new Vector2i(
			clamp(x + 12 + w <= screenW - TIP_MARGIN ? x + 12 : x - 12 - w, w, screenW), clamp(y - 12, h, screenH));
	// 2x3 dots, drag handle of hud lines + pb rows
	public static final Surface GRIP = (graphics, c) -> {
		int x = c.x() + (c.width() - 3) / 2;
		int y = c.y() + (c.height() - 5) / 2;
		for (int dx = 0; dx <= 2; dx += 2) {
			for (int dy = 0; dy <= 4; dy += 2) graphics.fill(x + dx, y + dy, x + dx + 1, y + dy + 1, 0xFFA0A0A0);
		}
	};

	private ScreenParts() {
	}

	public static int clamp(int pos, int size, int screen) {
		return Math.max(TIP_MARGIN, Math.min(pos, screen - TIP_MARGIN - size));
	}

	public static LabelComponent label(String text) {
		return new SafeLabel(Component.literal(text));
	}

	public static LabelComponent wrapped(String text) {
		LabelComponent label = label(text);
		label.horizontalSizing(Sizing.fill(100));
		return label;
	}

	// cut to width w/ "…", never mid § code. binary search, caxton shapes every width call
	public static String clip(Font font, String text, int max) {
		if (font.width(text) <= max) return text;
		int low = 0;
		int high = text.length();
		while (low < high) {
			int mid = (low + high + 1) >>> 1;
			if (font.width(text.substring(0, codeSafe(text, mid)) + "…") <= max) low = mid;
			else high = mid - 1;
		}
		return text.substring(0, codeSafe(text, low)) + "…";
	}

	private static int codeSafe(String text, int cut) {
		return cut > 0 && text.charAt(cut - 1) == '§' ? cut - 1 : cut;
	}

	public static String strip(String text) {
		return text.replaceAll("§.", "");
	}

	// own tooltips: smallest hovered part wins, null = none
	public static <T> T pick(Map<UIComponent, T> tips, int mouseX, int mouseY, Predicate<UIComponent> shown) {
		T picked = null;
		long smallest = Long.MAX_VALUE;
		for (Map.Entry<UIComponent, T> tip : tips.entrySet()) {
			UIComponent c = tip.getKey();
			long area = (long) c.width() * c.height();
			if (area < smallest && shown.test(c) && c.isInBoundingBox(mouseX, mouseY)) {
				smallest = area;
				picked = tip.getValue();
			}
		}
		return picked;
	}

	// caxton: text set at 0 width can scroll 1st letter out, rehome next frame
	public static void rehome(TextBoxComponent field) {
		field.moveCursorToEnd(false);
		field.moveCursorToStart(false);
	}

	// same as owo's tooltip(Collection<Component>)
	static List<ClientTooltipComponent> tips(List<Component> lines) {
		List<ClientTooltipComponent> tips = new ArrayList<>();
		for (Component line : lines) tips.add(ClientTooltipComponent.create(line.getVisualOrderText()));
		return tips;
	}

	// row + its own children, so the whole row shows it
	static FlowLayout withTips(FlowLayout row, List<ClientTooltipComponent> tooltip) {
		row.tooltip(tooltip);
		row.children().forEach(child -> child.tooltip(tooltip));
		return row;
	}

	// every part under it
	static void applyTooltip(UIComponent component, List<Component> tooltip) {
		component.tooltip(tooltip);
		if (component instanceof ParentUIComponent parent) {
			for (UIComponent child : parent.children()) applyTooltip(child, tooltip);
		}
	}
}
