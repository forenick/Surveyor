package dev.skysurveyor.money;

import java.util.Locale;
import java.util.regex.Matcher;

import dev.skysurveyor.data.CorpseType;
import dev.skysurveyor.data.Ladder;
import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.journal.GemYield;
import dev.skysurveyor.journal.MetalYield;
import dev.skysurveyor.settings.MoneySettings;
import dev.skysurveyor.settings.MoneySettings.Crystals;
import dev.skysurveyor.settings.MoneySettings.GemTier;
import dev.skysurveyor.settings.MoneySettings.OwnMetal;
import dev.skysurveyor.settings.MoneySettings.SellPrice;

// rules in docs/money-design.md
public record PriceRules(ShaftValue.Prices prices, MoneySettings settings, ItemIds ids, TextPatterns patterns) {
	// portable campfire recipe (research/01)
	private static final int CAMPFIRE_MATCH_STICKS = 16;
	// tungsten/umber key = 192 enchanted + bejeweled handle (NEU TUNGSTEN_KEY, UMBER_KEY)
	private static final int KEY_ENCHANTED = 192;
	// perfect = 5 flawless + crystal (research/01)
	private static final int PERFECT_FLAWLESS = 5;

	// by chat name
	public Double each(String item) {
		Matcher m = patterns.match("gem.item", item);
		if (m != null) {
			Double perFine = gemPerFine(m.group("gem"));
			return perFine == null ? null : perFine / GemYield.countPerFine(m.group("quality"));
		}
		if ((m = patterns.match("crystal.item", item)) != null) return crystal(m.group("gem"));
		return sell(ids.id(item));
	}

	public Double gemPerFine(String gem) {
		// flawless has no npc price
		if (settings.isIronman() || settings.gemTier() == GemTier.FINE) return sell(gemId("FINE", gem));
		Double flawless = sell(gemId("FLAWLESS", gem));
		return flawless == null ? null : flawless / GemYield.RECIPE;
	}

	// mined metal only, corpse metal keeps its own price
	public Double metalPerRaw(Ladder ladder) {
		int tier = settings.metalTierUsed().tier();
		Double price = sell(ids.id(ladder.name(tier, null)));
		return price == null ? null : price / ladder.base(tier);
	}

	public Double crystal(String gem) {
		// no npc price, listed w/o coins
		if (settings.isIronman()) return null;
		if (settings.crystals() == Crystals.ZERO) return 0.0;
		Double perfect = sell(gemId("PERFECT", gem));
		Double flawless = sell(gemId("FLAWLESS", gem));
		if (perfect == null || flawless == null) return null;
		return Math.max(0, perfect - PERFECT_FLAWLESS * flawless);
	}

	// bazaar w/ tax, else bin, else npc (soulbound drops, empty books). ironman = npc only
	public Double sell(String id) {
		if (id == null) return null;
		ItemPrice price = prices.get(id);
		if (price == null) return null;
		if (settings.isIronman()) return price.npcSell();
		Double side = settings.sellPrice() == SellPrice.SELL_OFFER ? price.sellOffer() : price.buyOrder();
		if (side != null) {
			double bazaar = side * settings.taxFactor();
			Double npc = price.npcSell();
			return settings.sellPrice() == SellPrice.BEST && npc != null ? Math.max(bazaar, npc) : bazaar;
		}
		if (!price.onBazaar() && price.lowestBin() != null) return price.lowestBin();
		return price.npcSell();
	}

	// insta-buy. ironman crafts keys + campfires, nicks pick: no cost
	public Double buy(String id) {
		if (settings.isIronman()) return 0.0;
		if (id == null) return null;
		ItemPrice price = prices.get(id);
		if (price == null) return null;
		return price.onBazaar() ? price.sellOffer() : price.lowestBin();
	}

	// craft = enchanted u could sell + bought handle
	public Double key(String key) {
		String id = ids.id(key);
		String metal = "TUNGSTEN_KEY".equals(id) ? "ENCHANTED_TUNGSTEN" : "UMBER_KEY".equals(id) ? "ENCHANTED_UMBER" : null;
		// skeleton key isnt ur metal, always bought
		if (metal == null) return buy(id);
		if (settings.ownMetal() == OwnMetal.DONT_COUNT) return 0.0;
		if (!crafts()) return buy(id);
		Double enchanted = sell(metal);
		Double handle = buy("BEJEWELED_HANDLE");
		return enchanted == null || handle == null ? null : KEY_ENCHANTED * enchanted + handle;
	}

	public Double campfireCost() {
		if (settings.ownMetal() == OwnMetal.DONT_COUNT) return 0.0;
		Double umber = crafts() ? refinedUmberFromOwn() : buy("REFINED_UMBER");
		Double sticks = buy("MATCH_STICKS");
		return umber == null || sticks == null ? null : umber + CAMPFIRE_MATCH_STICKS * sticks;
	}

	// costs 0 w/ these settings, shown as used
	public static boolean free(MoneySettings settings, ShaftValue.Part part, String item) {
		if (part != ShaftValue.Part.KEYS && part != ShaftValue.Part.CAMPFIRES) return false;
		if (settings.isIronman()) return true;
		return settings.ownMetal() == OwnMetal.DONT_COUNT && (part == ShaftValue.Part.CAMPFIRES
				|| item.equals(CorpseType.key(CorpseType.TUNGSTEN.label())) || item.equals(CorpseType.key(CorpseType.UMBER.label())));
	}

	// ironman: no cost
	private boolean crafts() {
		return settings.ownMetal() == OwnMetal.CRAFT && !settings.isIronman();
	}

	private Double refinedUmberFromOwn() {
		Ladder umber = Ladder.metal("Umber");
		Double enchanted = sell("ENCHANTED_UMBER");
		return enchanted == null ? null : enchanted * umber.base(MetalYield.REFINED) / umber.base(MetalYield.ENCHANTED);
	}

	private static String gemId(String tier, String gem) {
		return tier + "_" + gem.toUpperCase(Locale.ROOT) + "_GEM";
	}
}
