package dev.skysurveyor.calc;

import java.util.List;
import java.util.regex.Matcher;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import dev.skysurveyor.detection.ItemCount;
import dev.skysurveyor.detection.TextPatterns;

// calculator-design.md "Next slice". any thread
// election names the perk, a drop proves it, tab list only knows whos in
public final class Fiesta {
	// gems/block, other drops + coins/spawn kept per state
	public enum State {
		// perk decides, a minister cole can have another one
		NONE("No Fiesta", ""),
		COLE("Fiesta", "_cole"),
		FLASK("Fiesta + flask", "_flask");

		private final String displayName;
		private final String keySuffix;

		State(String displayName, String keySuffix) {
			this.displayName = displayName;
			this.keySuffix = keySuffix;
		}

		public String displayName() {
			return displayName;
		}
	}

	public enum Source {
		ELECTION,
		DROP,
		TAB_LIST
	}

	// on null = unknown
	public record Reading(Boolean on, Source source, long at) {
		static final Reading UNKNOWN = new Reading(null, null, 0);
	}

	// display choices (a term is 124 h)
	static final long ELECTION_MAX_AGE = 2 * 3_600_000L;
	static final long OTHER_MAX_AGE = 6 * 3_600_000L;
	// flask share: over = flask, under = cole, else neither. display choices
	static final double FLASK_MOST = 0.9;
	static final double FLASK_LITTLE = 0.1;
	// a lag spike isnt flask time
	private static final long MAX_STEP = 1_000;

	private final TextPatterns patterns;
	private Boolean election;
	private long electionAt;
	private long dropAt = -1;
	private Boolean tab;
	private long tabAt;
	private long flaskMillis;
	private long lastTick = -1;

	public Fiesta(TextPatterns patterns) {
		this.patterns = patterns;
	}

	// api.hypixel.net/v2/resources/skyblock/election, no mayor = says nothing
	public synchronized void onElection(long now, JsonObject json) {
		Boolean perk = perk(json, patterns);
		if (perk == null) return;
		election = perk;
		electionAt = now;
	}

	// null = not election data. mayor.perks[].name + mayor.minister.perk.name (read 11-25)
	static Boolean perk(JsonObject json, TextPatterns patterns) {
		JsonElement mayor = json.get("mayor");
		if (mayor == null || !mayor.isJsonObject()) return null;
		JsonObject m = mayor.getAsJsonObject();
		JsonElement perks = m.get("perks");
		if (perks != null && perks.isJsonArray()) {
			for (JsonElement perk : perks.getAsJsonArray()) {
				if (isFiesta(perk, patterns)) return true;
			}
		}
		JsonElement minister = m.get("minister");
		return minister != null && minister.isJsonObject() && isFiesta(minister.getAsJsonObject().get("perk"), patterns);
	}

	private static boolean isFiesta(JsonElement perk, TextPatterns patterns) {
		if (perk == null || !perk.isJsonObject()) return false;
		JsonElement name = perk.getAsJsonObject().get("name");
		return name != null && name.isJsonPrimitive() && patterns.matches("fiesta.perk", TextPatterns.strip(name.getAsString()));
	}

	// glossy + refined minerals only drop w/ fiesta (CW Glossy_Gemstone, Refined_Mineral). never says off
	public synchronized void onSacks(long now, List<ItemCount> added) {
		for (ItemCount item : added) {
			if (patterns.matches("fiesta.drop", item.item())) dropAt = now;
		}
	}

	// election widget, not every island. no cole = off, mayor cole = on, perk not named
	public synchronized void onTabList(long now, List<String> lines) {
		boolean seen = false;
		boolean mayor = false;
		boolean minister = false;
		for (String line : lines) {
			String text = TextPatterns.clean(line);
			Matcher m = patterns.match("tab.mayor", text);
			boolean isMayor = m != null;
			if (m == null) m = patterns.match("tab.minister", text);
			if (m == null) continue;
			seen = true;
			if (!patterns.matches("fiesta.candidate", m.group("name"))) continue;
			if (isMayor) mayor = true;
			else minister = true;
		}
		if (!seen) return;
		// minister cole can have another perk, says nothing
		tab = mayor ? Boolean.TRUE : minister ? null : Boolean.FALSE;
		tabAt = now;
	}

	public synchronized Reading reading(long now) {
		if (election != null && now - electionAt <= ELECTION_MAX_AGE) return new Reading(election, Source.ELECTION, electionAt);
		boolean drop = dropAt >= 0 && now - dropAt <= OTHER_MAX_AGE;
		boolean tabList = tab != null && now - tabAt <= OTHER_MAX_AGE;
		// newest wins, a drop also beats an older cole-less tab list
		if (drop && (!tabList || dropAt >= tabAt || tab)) return new Reading(true, Source.DROP, dropAt);
		if (tabList) return new Reading(tab, Source.TAB_LIST, tabAt);
		return Reading.UNKNOWN;
	}

	// null = unknown. flask does nothing w/o fiesta (CW Fiesta_Flask)
	public State state(long now, boolean flask) {
		Boolean on = reading(now).on();
		if (on == null) return null;
		return !on ? State.NONE : flask ? State.FLASK : State.COLE;
	}

	// every tick. flask = timer running
	public synchronized void onTick(long now, boolean inShaft, boolean flask) {
		if (inShaft && flask && lastTick >= 0) flaskMillis += Math.max(0, Math.min(MAX_STEP, now - lastTick));
		lastTick = now;
	}

	public synchronized void onShaftEntered() {
		flaskMillis = 0;
	}

	// since shaft entry
	public synchronized long flaskMillis() {
		return flaskMillis;
	}

	// null = unknown or flask ran for part of it
	public static State of(Boolean fiesta, Long flaskMillis, long durationMillis) {
		if (fiesta == null) return null;
		if (!fiesta) return State.NONE;
		double share = flaskMillis == null || durationMillis <= 0 ? 0 : (double) flaskMillis / durationMillis;
		return share >= FLASK_MOST ? State.FLASK : share <= FLASK_LITTLE ? State.COLE : null;
	}

	public static boolean perState(NumberId id) {
		return id == NumberId.GEMS_PER_BLOCK || id == NumberId.OTHER || id == NumberId.COINS_PER_SPAWN;
	}

	// null = not ours
	public static NumberId idOf(String key) {
		NumberId id = NumberId.byKey(key);
		if (id != null) return id;
		for (State state : State.values()) {
			if (state.keySuffix.isEmpty() || !key.endsWith(state.keySuffix)) continue;
			NumberId base = NumberId.byKey(key.substring(0, key.length() - state.keySuffix.length()));
			if (base != null && perState(base)) return base;
		}
		return null;
	}

	// config.json key, plain key = no cole, also while unknown
	public static String typedKey(NumberId id, State state) {
		return state == null || !perState(id) ? id.key() : id.key() + state.keySuffix;
	}
}
