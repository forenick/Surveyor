package dev.skysurveyor.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.util.Util;

import dev.skysurveyor.journal.ToastText;
import dev.skysurveyor.settings.SummarySettings.Style;
import dev.skysurveyor.util.Guard;

// vanilla toast slot, top right. manager does the slide + stacking
public final class ShaftToast implements Toast {
	// hud styles keep the hud margin from the screen edge
	private static final int HUD_INSET = HudLayout.MARGIN;

	private final ToastText text;
	private final Style style;
	private final long millis;
	private final ToastRenderer.Size size;
	private final int width;
	private final int height;
	private final ToastClock clock = new ToastClock();
	private Visibility wanted = Visibility.SHOW;
	private boolean broken;

	public ShaftToast(Font font, ToastText text, Style style, double seconds) {
		this.text = text;
		this.style = style;
		this.millis = Math.round(seconds * 1000);
		size = ToastRenderer.size(font, text, style);
		int inset = inset();
		this.width = size.width() + 2 * inset;
		this.height = size.height() + 2 * inset;
	}

	@Override
	public Visibility getWantedVisibility() {
		return wanted;
	}

	// own clock, not vanillas time: pauses on mod screens. notification time option stretches it like vanillas own
	// vanilla calls these, guarded. a broken toast just goes away
	@Override
	public void update(ToastManager manager, long time) {
		if (!broken) broken = !Guard.run("toast", () -> {
			long age = clock.tick(Util.getMillis(), LiveHud.modScreenOpen());
			wanted = age < millis * manager.getNotificationDisplayTimeMultiplier() ? Visibility.SHOW : Visibility.HIDE;
		});
		if (broken) wanted = Visibility.HIDE;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long time) {
		if (broken) return;
		broken = !Guard.run("toast render", () -> {
			if (LiveHud.modScreenOpen()) return;
			int inset = inset();
			graphics.pose().pushMatrix();
			try {
				graphics.pose().translate(inset, inset);
				ToastRenderer.draw(graphics, font, text, style, size, 1);
			} finally {
				graphics.pose().popMatrix();
			}
		});
	}

	@Override
	public int width() {
		return width;
	}

	@Override
	public int height() {
		return height;
	}

	private int inset() {
		return style == Style.VANILLA ? 0 : HUD_INSET;
	}
}
