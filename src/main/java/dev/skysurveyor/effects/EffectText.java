package dev.skysurveyor.effects;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import dev.skysurveyor.detection.TextPatterns;

// game text in, legacy (§ codes) everywhere
public final class EffectText {
	private final TextPatterns patterns;

	public EffectText(TextPatterns patterns) {
		this.patterns = patterns;
	}

	// tab footer. count null = no count line, effects = the ones shown w/ a time
	public record Footer(Integer count, Map<Consumable, EffectTime> shown) {
	}

	public record Item(String name, List<String> lore) {
	}

	public Consumable item(String legacyName) {
		return legacyName == null ? null : Consumable.ofItem(patterns, TextPatterns.clean(legacyName).strip());
	}

	// from the items lore, null = none found
	public EffectTime duration(List<String> lore) {
		if (lore == null) return null;
		for (String line : lore) {
			Matcher m = patterns.match("consumable.duration", TextPatterns.clean(line));
			if (m == null) continue;
			EffectTime time = EffectTime.parse(m.group("time"));
			if (time != null) return time;
		}
		return null;
	}

	public Consumable buff(String legacyChat) {
		Matcher m = patterns.match("consumable.buff", TextPatterns.clean(legacyChat));
		return m == null ? null : Consumable.ofEffect(patterns, m.group("effect"));
	}

	public boolean skyblock(String legacyTitle) {
		return legacyTitle != null && patterns.matches("scoreboard.skyblock", TextPatterns.clean(legacyTitle).strip());
	}

	// null = no effects part (other servers, loading)
	public Footer footer(String legacyFooter) {
		if (legacyFooter == null) return null;
		boolean inside = false;
		Integer count = null;
		Map<Consumable, EffectTime> shown = new EnumMap<>(Consumable.class);
		for (String raw : legacyFooter.split("\n")) {
			String line = TextPatterns.clean(raw).strip();
			if (!inside) {
				inside = patterns.matches("effect.footer_start", line);
				continue;
			}
			// blank line ends the effects part, cookie buff comes after
			if (line.isEmpty()) break;
			Matcher c = patterns.match("effect.footer_count", line);
			if (c != null) {
				count = Integer.parseInt(c.group("count"));
				continue;
			}
			Matcher m = patterns.match("effect.line", line);
			if (m == null) continue;
			Consumable consumable = Consumable.ofEffect(patterns, m.group("effect"));
			EffectTime time = EffectTime.parse(m.group("time"));
			if (consumable != null && time != null) shown.put(consumable, time);
		}
		return inside ? new Footer(count, shown) : null;
	}

	public boolean isScreen(String legacyTitle) {
		return legacyTitle != null && patterns.matches("effect.screen", TextPatterns.clean(legacyTitle));
	}

	// null = not the effects screen
	public Map<Consumable, EffectTime> screen(String legacyTitle, List<Item> items) {
		if (!isScreen(legacyTitle)) return null;
		Map<Consumable, EffectTime> found = new EnumMap<>(Consumable.class);
		for (Item item : items) {
			String clean = TextPatterns.clean(item.name()).strip();
			Matcher name = patterns.match("effect.screen_name", clean);
			Consumable consumable = Consumable.ofEffect(patterns, name == null ? clean : name.group("effect"));
			if (consumable == null || item.lore() == null) continue;
			for (String line : item.lore()) {
				Matcher t = patterns.match("effect.screen_time", TextPatterns.clean(line).strip());
				EffectTime time = t == null ? null : EffectTime.parse(t.group("time"));
				if (time != null) {
					found.put(consumable, time);
					break;
				}
			}
		}
		return found;
	}
}
