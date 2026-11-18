package dev.skysurveyor.detection;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.skysurveyor.detection.ShaftEvent.ShaftEntered;
import dev.skysurveyor.detection.ShaftEvent.ShaftLeft;
import dev.skysurveyor.util.GameText;
import dev.skysurveyor.util.Guard;
import dev.skysurveyor.util.TextUtil;

// same data and rate as the recorder, so replays act like live
public final class LiveTracker {
	private static final Logger LOGGER = LoggerFactory.getLogger("Surveyor Detection");

	private final ShaftTracker tracker = new ShaftTracker(TextPatterns.defaults(), this::onEvent);
	private final List<Consumer<ShaftEvent>> listeners = new ArrayList<>();
	private final List<Runnable> worldChanges = new ArrayList<>();
	private final List<Consumer<String>> chatListeners = new ArrayList<>();
	private final List<Consumer<List<String>>> tabListeners = new ArrayList<>();
	private ClientLevel lastLevel;
	private int tick;

	public void addListener(Consumer<ShaftEvent> listener) {
		listeners.add(listener);
	}

	// raw chat (legacy), after the tracker had it
	public void addChatListener(Consumer<String> listener) {
		chatListeners.add(listener);
	}

	// same lines the tracker got, client thread
	public void addTabListener(Consumer<List<String>> listener) {
		tabListeners.add(listener);
	}

	// after the tracker, so a shaft left comes first
	public void onWorldChange(Runnable listener) {
		worldChanges.add(listener);
	}

	public boolean inMiningArea() {
		return tracker.inMiningArea();
	}

	// client thread only
	public Shaft currentShaft() {
		return tracker.current();
	}

	// each step + listener guarded on its own, a throw here would reach vanilla
	public void onGameMessage(Component message, boolean overlay) {
		if (overlay) return;
		String legacy = Guard.get("chat", () -> TextUtil.toLegacy(message), () -> null);
		if (legacy == null) return;
		Guard.run("chat", () -> tracker.onChat(System.currentTimeMillis(), legacy, TextUtil.hoverTexts(message)));
		Guard.each("chat listener", chatListeners, legacy);
	}

	// game closing inside a shaft, no world change comes
	public void onQuit() {
		worldChange(System.currentTimeMillis());
	}

	public void onTick(Minecraft client) {
		tick++;
		long now = System.currentTimeMillis();
		if (client.level != lastLevel) {
			lastLevel = client.level;
			worldChange(now);
		}
		if (client.level == null || tick % 10 != 0) return;
		ClientLevel level = client.level;
		Guard.run("scoreboard", () -> tracker.onScoreboard(now, GameText.scoreboardLines(level.getScoreboard())));
		List<String> tab = Guard.get("tab list", () -> GameText.tabListLines(client), () -> null);
		if (tab == null) return;
		Guard.run("tab list", () -> tracker.onTabList(now, tab));
		Guard.each("tab list listener", tabListeners, tab);
	}

	private void worldChange(long now) {
		Guard.run("world change", () -> tracker.onWorldChange(now));
		Guard.each("world change listener", worldChanges);
	}

	private void onEvent(ShaftEvent event) {
		Guard.run("event log", () -> {
			switch (event) {
				case ShaftEntered entered -> LOGGER.info("Entered shaft {}", entered.shaft().id().id());
				case ShaftLeft left -> LOGGER.info("Left shaft {} after {}s", EventText.summary(left.shaft()), left.shaft().durationMillis() / 1000);
				default -> LOGGER.debug("{}", event);
			}
		});
		Guard.each("event listener", listeners, event);
	}

}
