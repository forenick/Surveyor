package dev.skysurveyor.detection;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.detection.ShaftEvent.FossilEssence;
import dev.skysurveyor.journal.FossilYield;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

// action bar, rec 11-21 (jade fossil, 2:17 to 2:26)
class FossilEssenceTest {
	private static final String MIDDLE = "§r§64,167/3,792     §d+4 Fossil Essence     §b811/919 §3400     §4120/132     ";
	private static final String END = "§r§64,167/3,792     §250,498/100k Drill Fuel     §d+8 Fossil Essence";
	private static final String NEXT = "§r§64,167/3,792     §250,496/100k Drill Fuel     §d+4 Fossil Essence";
	private static final String NONE = "§r§63,822/3,447     §3+515.4 Combat (89%)     §b777/914 §3400     §4120/120     ";

	private final List<ShaftEvent> events = new ArrayList<>();
	private final ShaftTracker tracker = new ShaftTracker(TextPatterns.defaults(), events::add);

	private Shaft enter() {
		tracker.onWorldChange(0);
		tracker.onScoreboard(0, List.of("§711/21/26 §8m113BD JADE_1"));
		return tracker.current();
	}

	@Test
	void readsTheAmountWhereverItSitsInTheBar() {
		enter();
		tracker.onActionBar(1, MIDDLE);
		tracker.onActionBar(2, END);
		tracker.onActionBar(3, NONE);
		assertEquals(List.of(new FossilEssence(4), new FossilEssence(8)), events.stream().filter(e -> e instanceof FossilEssence).toList());
	}

	@Test
	void sameBarTwiceIsOneEvent() {
		enter();
		tracker.onActionBar(1, END);
		tracker.onActionBar(2, END);
		assertEquals(1, events.stream().filter(e -> e instanceof FossilEssence).count());
	}

	@Test
	void blocksTimesTheAverageBarAfterABreak() {
		Shaft shaft = enter();
		// bar before any block: not a sample
		tracker.onActionBar(1, MIDDLE);
		tracker.onBlockBreak("quartz_block", List.of("quartz_slab", "stone"));
		tracker.onActionBar(2, END);
		// lingering bar, no new block
		tracker.onActionBar(3, NONE);
		tracker.onActionBar(4, END);
		tracker.onBlockBreak("quartz_stairs", List.of());
		tracker.onBlockBreak("stone", List.of());
		tracker.onActionBar(5, NEXT);
		assertEquals(3, shaft.fossilBlocks());
		assertEquals(2, shaft.essenceSamples());
		assertEquals(12, shaft.essenceShown());
		// 3 x (8 + 4) / 2
		assertEquals(18, FossilYield.of(shaft).essence());
	}

	// bar comes a tick before the break, then same-text resends
	@Test
	void resendSamplesABlockWhoseBarCameFirst() {
		Shaft shaft = enter();
		tracker.onActionBar(1, END);
		tracker.onBlockBreak("quartz_block", List.of());
		int version = shaft.version();
		tracker.onActionBar(2, END);
		tracker.onActionBar(3, END);
		assertEquals(1, shaft.essenceSamples());
		assertEquals(8, shaft.essenceShown());
		assertEquals(version + 1, shaft.version(), "only the sample bumps it");
		assertEquals(1, events.stream().filter(e -> e instanceof FossilEssence).count(), "resends arent events");
	}

	@Test
	void noFossilNoYield() {
		Shaft shaft = enter();
		tracker.onBlockBreak("lime_stained_glass", List.of("stone"));
		tracker.onActionBar(1, NONE);
		assertNull(FossilYield.of(shaft));
	}

	@Test
	void essenceRoundsAndNeedsABar() {
		assertEquals(0, new FossilYield(5, 0, 0).essence());
		// 43 x 112 / 17 = 283.3
		assertEquals(283, new FossilYield(43, 17, 112).essence());
	}
}
