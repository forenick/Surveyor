package dev.skysurveyor.effects;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

// time left per consumable, counts down only while on skyblock (effects pause offline, nick 2026-11-22). client thread
public final class EffectTimers {
	// footer lags the eat by ~2.5s, its old time would undo a reset (recording 2026-11-08). display choice
	static final long FOOTER_IGNORE_MILLIS = 5_000;
	// a count drop clears timers this close to 0. ours runs fast, so only our overshoot (half a footer step, save gap). display choice
	static final long ENDING_MILLIS = 30_000;
	// splash msg comes after the potion left ur hand. display choice
	static final long BUFF_ITEM_MILLIS = 10_000;
	// smaller fixes ride along w/ the next timed save
	static final long SAVE_CHANGE_MILLIS = 2_000;
	// suspend or a hang shouldnt eat the timers
	static final long MAX_STEP_MILLIS = 10_000;

	private final EffectText text;
	private final Map<Consumable, Long> left = new EnumMap<>(Consumable.class);
	private final Map<Consumable, Long> startedAt = new EnumMap<>(Consumable.class);
	private final Map<Consumable, Seen> seen = new EnumMap<>(Consumable.class);
	private Held held;
	private Integer footerCount;
	private Long lastTick;
	private int version;

	private record Held(int slot, Consumable consumable, int count, EffectTime duration) {
	}

	// at = last time it was in hand
	private record Seen(EffectTime duration, long at) {
	}

	public EffectTimers(EffectText text) {
		this.text = text;
	}

	// bumps on every change but the countdown, for saving
	public int version() {
		return version;
	}

	public Long left(Consumable consumable) {
		return left.get(consumable);
	}

	public boolean isEmpty() {
		return left.isEmpty();
	}

	public Map<Consumable, Long> all() {
		return new EnumMap<>(left);
	}

	public void tick(long now, boolean running) {
		if (running && lastTick != null && !left.isEmpty()) {
			long step = Math.clamp(now - lastTick, 0, MAX_STEP_MILLIS);
			left.replaceAll((c, ms) -> ms - step);
			if (left.values().removeIf(ms -> ms <= 0)) version++;
		}
		lastTick = now;
	}

	// selected slot every tick. name null = empty. used = use key or a right-click just now
	public void onHeld(long now, int slot, String legacyName, int count, Supplier<List<String>> lore, boolean used) {
		Consumable consumable = text.item(legacyName);
		if (held != null && held.consumable() != null && held.slot() == slot && used && held.duration() != null) {
			boolean gone = consumable == held.consumable() && count == held.count() - 1 || legacyName == null && held.count() == 1;
			// eating again resets to the full length (nick, 2026-11-22)
			if (gone) start(held.consumable(), held.duration().millis(), now);
		}
		if (held != null && held.consumable() != null && held.duration() != null) {
			seen.put(held.consumable(), new Seen(held.duration(), now));
		}
		if (held != null && held.slot() == slot && held.consumable() == consumable && held.count() == count && consumable != null) return;
		EffectTime duration = consumable == null ? null : text.duration(lore.get());
		if (duration != null) seen.put(consumable, new Seen(duration, now));
		held = new Held(slot, consumable, count, duration);
	}

	// splash backup, f.e. the count drop came too late for the use window
	public void onChat(long now, String legacy) {
		Consumable consumable = text.buff(legacy);
		if (consumable == null) return;
		Long started = startedAt.get(consumable);
		if (started != null && now - started < FOOTER_IGNORE_MILLIS) return;
		Seen s = seen.get(consumable);
		if (s != null && now - s.at() <= BUFF_ITEM_MILLIS) start(consumable, s.duration().millis(), now);
	}

	public void onFooter(long now, EffectText.Footer footer) {
		if (footer == null) return;
		footer.shown().forEach((consumable, time) -> {
			Long started = startedAt.get(consumable);
			if (started != null && now - started < FOOTER_IGNORE_MILLIS) return;
			correct(consumable, time);
		});
		Integer count = footer.count();
		if (count != null && footerCount != null && count < footerCount) ended(footerCount - count, footer.shown().keySet());
		if (count != null) footerCount = count;
	}

	// /effects, opened by the player
	public void onScreen(Map<Consumable, EffectTime> shown) {
		if (shown == null) return;
		// polled while open, so clamp or it never counts down
		shown.forEach(this::correct);
	}

	// "45m" = 45:00 to 45:59, a count inside that stays
	private void correct(Consumable consumable, EffectTime time) {
		Long ms = left.get(consumable);
		long low = time.millis();
		long high = time.millis() + time.step() - 1;
		long fixed = ms == null ? low + time.step() / 2 : Math.clamp(ms, low, high);
		if (ms == null || fixed != ms) set(consumable, fixed);
	}

	// fewer effects in the footer: the ones about to end are gone
	private void ended(int howMany, Set<Consumable> stillShown) {
		List<Consumable> ending = new ArrayList<>();
		left.forEach((c, ms) -> {
			if (ms < ENDING_MILLIS && !stillShown.contains(c)) ending.add(c);
		});
		ending.sort(Comparator.comparing(left::get));
		for (int i = 0; i < Math.min(howMany, ending.size()); i++) left.remove(ending.get(i));
		if (!ending.isEmpty()) version++;
	}

	private void start(Consumable consumable, long millis, long now) {
		startedAt.put(consumable, now);
		set(consumable, millis);
	}

	private void set(Consumable consumable, long millis) {
		if (millis <= 0) {
			if (left.remove(consumable) != null) version++;
			return;
		}
		Long was = left.put(consumable, millis);
		if (was == null || Math.abs(was - millis) > SAVE_CHANGE_MILLIS) version++;
	}

	// effects.json
	public Map<String, Long> saved() {
		Map<String, Long> out = new LinkedHashMap<>();
		left.forEach((c, ms) -> out.put(c.id(), ms));
		return out;
	}

	// live ones win, a load can come after an eat
	public void restore(Map<String, Long> saved) {
		saved.forEach((id, ms) -> {
			Consumable c = Consumable.byId(id);
			if (c != null && ms != null && ms > 0 && !left.containsKey(c)) left.put(c, ms);
		});
	}
}
