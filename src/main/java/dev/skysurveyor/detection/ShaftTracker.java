package dev.skysurveyor.detection;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;

import dev.skysurveyor.detection.ShaftEvent.ColdChanged;
import dev.skysurveyor.detection.ShaftEvent.CorpsesChanged;
import dev.skysurveyor.detection.ShaftEvent.FossilEssence;
import dev.skysurveyor.detection.ShaftEvent.ShaftEntered;
import dev.skysurveyor.detection.ShaftEvent.ShaftLeft;

// candidate per world change, so msgs before its confirmed still count
public final class ShaftTracker {
	// fossil blocks, wiki Fossil_Essence: quartz block, stairs, slabs
	public static final Set<String> FOSSIL_BLOCKS = Set.of("quartz_block", "quartz_stairs", "quartz_slab");

	private final ChatParser chat;
	private final ScoreboardParser scoreboard;
	private final TabListParser tabList;
	private final Consumer<ShaftEvent> listener;

	private Shaft shaft;
	private Integer cold;
	private List<Corpse> corpses = List.of();
	private boolean inMiningArea;
	// bar comes ~2x a sec, same text = nothing new
	private String lastBar;
	private final TextPatterns patterns;

	public ShaftTracker(TextPatterns patterns, Consumer<ShaftEvent> listener) {
		this.chat = new ChatParser(patterns);
		this.scoreboard = new ScoreboardParser(patterns);
		this.tabList = new TabListParser(patterns);
		this.listener = listener;
		this.patterns = patterns;
	}

	// false right after a world change
	public boolean inMiningArea() {
		return inMiningArea;
	}

	public Shaft current() {
		return shaft != null && shaft.confirmed ? shaft : null;
	}

	public void onWorldChange(long now) {
		if (shaft != null && shaft.confirmed) {
			shaft.leave(now);
			listener.accept(new ShaftLeft(shaft));
		}
		shaft = new Shaft(now);
		chat.reset();
		cold = null;
		corpses = List.of();
		inMiningArea = false;
		lastBar = null;
	}

	public void onChat(long now, String legacy, List<String> hovers) {
		ShaftEvent event = chat.parse(legacy, hovers);
		if (event == null) return;
		if (shaft != null) shaft.apply(event, now);
		listener.accept(event);
	}

	public void onActionBar(long now, String legacy) {
		boolean same = legacy.equals(lastBar);
		// a resend still samples a pending block, its 1st bar comes the tick before BlockWatch sees the break
		if (same && (shaft == null || !shaft.essencePending())) return;
		lastBar = legacy;
		Matcher m = patterns.match("fossil.essence", TextPatterns.clean(legacy));
		if (m == null) return;
		int amount = Integer.parseInt(m.group("amount"));
		if (shaft != null) shaft.sampleEssence(amount);
		if (!same) listener.accept(new FossilEssence(amount));
	}

	// mining/BlockWatch, block ids. gone = spread that went w/ it
	public void onBlockBreak(String block, List<String> gone) {
		int fossil = FOSSIL_BLOCKS.contains(block) ? 1 : 0;
		for (String other : gone) {
			if (FOSSIL_BLOCKS.contains(other)) fossil++;
		}
		if (fossil > 0 && shaft != null) shaft.addFossilBlocks(fossil);
	}

	public void onScoreboard(long now, List<String> lines) {
		ShaftId id = scoreboard.shaftId(lines);
		if (id != null) {
			Shaft candidate = candidate(now);
			candidate.confirmed = true;
			if (candidate.id() == null) {
				candidate.setId(id);
				listener.accept(new ShaftEntered(candidate));
			}
		}
		// cold line can vanish for a sec, keep the last value
		Integer newCold = scoreboard.cold(lines);
		if (newCold != null && !newCold.equals(cold)) {
			cold = newCold;
			if (shaft != null) shaft.setCold(newCold);
			listener.accept(new ColdChanged(newCold));
		}
	}

	public void onTabList(long now, List<String> lines) {
		if (tabList.inMineshaft(lines)) candidate(now).confirmed = true;
		// empty while loading, says nothing
		if (!lines.isEmpty()) inMiningArea = tabList.inMiningArea(lines);
		// widget missing first few secs, empty = not shown
		List<Corpse> newCorpses = tabList.corpses(lines);
		if (!newCorpses.isEmpty() && !newCorpses.equals(corpses)) {
			corpses = newCorpses;
			if (shaft != null) shaft.setCorpses(newCorpses);
			listener.accept(new CorpsesChanged(newCorpses));
		}
	}

	private Shaft candidate(long now) {
		if (shaft == null) shaft = new Shaft(now);
		return shaft;
	}
}
