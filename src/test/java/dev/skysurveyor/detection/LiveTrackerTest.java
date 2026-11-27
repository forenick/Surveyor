package dev.skysurveyor.detection;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

// a throwing listener cant stop the next or reach vanilla
class LiveTrackerTest {
	private static final String WOW = "§5§lWOW! §aYou found a §bGlacite Mineshaft portal!";

	private final LiveTracker tracker = new LiveTracker();

	@Test
	void aThrowingEventListenerDoesntStopTheNextOne() {
		List<ShaftEvent> got = new ArrayList<>();
		tracker.addListener(event -> {
			throw new IllegalStateException("bug");
		});
		tracker.addListener(got::add);
		tracker.onGameMessage(Component.literal(WOW), false);
		assertEquals(1, got.size());
		assertInstanceOf(ShaftEvent.PortalSpawned.class, got.getFirst());
	}

	@Test
	void aThrowingChatListenerDoesntStopTheNextOne() {
		List<String> got = new ArrayList<>();
		tracker.addChatListener(line -> {
			throw new IllegalStateException("bug");
		});
		tracker.addChatListener(got::add);
		tracker.onGameMessage(Component.literal("hi"), false);
		assertEquals(List.of("§rhi"), got);
	}

	@Test
	void aThrowingWorldChangeListenerDoesntStopTheNextOne() {
		List<String> got = new ArrayList<>();
		tracker.onWorldChange(() -> {
			throw new IllegalStateException("bug");
		});
		tracker.onWorldChange(() -> got.add("changed"));
		tracker.onQuit();
		assertEquals(List.of("changed"), got);
	}
}
