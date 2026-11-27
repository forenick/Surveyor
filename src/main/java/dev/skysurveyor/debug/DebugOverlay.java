package dev.skysurveyor.debug;

import java.util.List;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import dev.skysurveyor.Surveyor;
import dev.skysurveyor.detection.Shaft;
import dev.skysurveyor.journal.Journal;
import dev.skysurveyor.money.PriceBook;
import dev.skysurveyor.screen.ScreenParts;
import dev.skysurveyor.util.Guard;

// top left, not in hud.json. only draws
public final class DebugOverlay {
	private static final int REFRESH_TICKS = 4;
	private static final int PADDING = 3;
	// display choice, long texts get cut
	private static final int MAX_WIDTH = 260;
	private static final int BACKGROUND = 0xA0000000;

	private final EventLog log;
	private List<String> lines = List.of();
	private int tick;

	public DebugOverlay(EventLog log) {
		this.log = log;
	}

	public void onTick(Minecraft client) {
		if (!Surveyor.SETTINGS.get().dev().isDebugOverlay()) {
			lines = List.of();
			return;
		}
		if (++tick % REFRESH_TICKS != 0 && !lines.isEmpty()) return;
		long now = System.currentTimeMillis();
		Shaft shaft = Surveyor.TRACKER.currentShaft();
		String shaftText = shaft == null ? null : shaft.id() == null ? "unknown type" : shaft.id().id();
		Journal.Session session = Surveyor.JOURNAL.activeSession(now);
		EventLog.Line last = log.last(EventLog.Kind.EVENT);
		PriceBook book = Surveyor.MONEY.book();
		String version = Surveyor.modVersion();
		lines = DebugLines.of(new DebugLines.Values(version == null ? "dev" : version, Surveyor.TRACKER.inMiningArea(), shaftText,
				session == null ? null : session.id(), session == null ? null : session.shafts(),
				session == null ? null : now - Surveyor.JOURNAL.lastActivity(), Surveyor.JOURNAL.spawnTimerMillis(now),
				last == null ? null : last.text(), last == null ? null : now - last.at(), log.unmatched(), Guard.errorCount(),
				book.bazaarAt() <= 0 ? null : now - book.bazaarAt(), book.binsAt() <= 0 ? null : now - book.binsAt(), Surveyor.HUD.ping(now),
				Surveyor.HUD.tps(now), LiveDebug.recording(Surveyor.RECORDER)));
	}

	public void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		if (lines.isEmpty() || client.gui.hud.isHidden()) return;
		Font font = client.font;
		int max = Math.min(MAX_WIDTH, graphics.guiWidth() - 2 * PADDING - 4);
		int width = 0;
		for (String line : lines) width = Math.max(width, Math.min(max, font.width(line)));
		int height = lines.size() * (font.lineHeight + 1) - 1;
		graphics.fill(2, 2, 2 + width + 2 * PADDING, 2 + height + 2 * PADDING, BACKGROUND);
		int y = 2 + PADDING;
		for (String line : lines) {
			graphics.text(font, ScreenParts.clip(font, line, max), 2 + PADDING, y, 0xFFFFFFFF, true);
			y += font.lineHeight + 1;
		}
	}
}
