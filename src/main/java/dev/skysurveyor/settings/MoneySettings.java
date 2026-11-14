package dev.skysurveyor.settings;

import com.google.gson.annotations.SerializedName;

// "money" in config.json, names never rename (docs/money-design.md)
public record MoneySettings(SellPrice sellPrice, GemTier gemTier, Double bazaarTax, OldShafts oldShafts, Crystals crystals,
		Boolean ironman, MetalTier metalTier) {
	// nicks picks 2026-11-12, tax is his own rate. metal tier nicks pick 2026-11-14
	public static final MoneySettings DEFAULTS = new MoneySettings(SellPrice.INSTA_SELL, GemTier.FLAWLESS, 1.125,
			OldShafts.THAT_DAY, Crystals.PERFECT_MINUS_FLAWLESS, false, MetalTier.ENCHANTED);

	public enum SellPrice {
		@SerializedName("insta_sell")
		INSTA_SELL,
		@SerializedName("sell_offer")
		SELL_OFFER
	}

	public enum GemTier {
		@SerializedName("fine")
		FINE,
		@SerializedName("flawless")
		FLAWLESS
	}

	public enum OldShafts {
		@SerializedName("that_day")
		THAT_DAY,
		@SerializedName("today")
		TODAY
	}

	public enum Crystals {
		@SerializedName("perfect_minus_flawless")
		PERFECT_MINUS_FLAWLESS,
		@SerializedName("zero")
		ZERO
	}

	// ladder tier = MetalYield.RAW, ENCHANTED, REFINED
	public enum MetalTier {
		@SerializedName("raw")
		RAW,
		@SerializedName("enchanted")
		ENCHANTED,
		@SerializedName("refined")
		REFINED;

		public int tier() {
			return ordinal();
		}
	}

	// hand edited file, anything missing or weird = default
	public MoneySettings repaired() {
		double tax = bazaarTax == null || !(bazaarTax >= 0 && bazaarTax <= 100) ? DEFAULTS.bazaarTax : bazaarTax;
		return new MoneySettings(sellPrice == null ? DEFAULTS.sellPrice : sellPrice, gemTier == null ? DEFAULTS.gemTier : gemTier,
				tax, oldShafts == null ? DEFAULTS.oldShafts : oldShafts, crystals == null ? DEFAULTS.crystals : crystals,
				ironman == null ? DEFAULTS.ironman : ironman, metalTier == null ? DEFAULTS.metalTier : metalTier);
	}

	public MoneySettings withSellPrice(SellPrice value) {
		return new MoneySettings(value, gemTier, bazaarTax, oldShafts, crystals, ironman, metalTier);
	}

	public MoneySettings withBazaarTax(double value) {
		return new MoneySettings(sellPrice, gemTier, value, oldShafts, crystals, ironman, metalTier);
	}

	public MoneySettings withOldShafts(OldShafts value) {
		return new MoneySettings(sellPrice, gemTier, bazaarTax, value, crystals, ironman, metalTier);
	}

	public MoneySettings withGemTier(GemTier tier) {
		return new MoneySettings(sellPrice, tier, bazaarTax, oldShafts, crystals, ironman, metalTier);
	}

	public MoneySettings withCrystals(Crystals value) {
		return new MoneySettings(sellPrice, gemTier, bazaarTax, oldShafts, value, ironman, metalTier);
	}

	public MoneySettings withIronman(boolean value) {
		return new MoneySettings(sellPrice, gemTier, bazaarTax, oldShafts, crystals, value, metalTier);
	}

	public MoneySettings withMetalTier(MetalTier value) {
		return new MoneySettings(sellPrice, gemTier, bazaarTax, oldShafts, crystals, ironman, value);
	}

	// ironman = raw npc price
	public MetalTier metalTierUsed() {
		return isIronman() ? MetalTier.RAW : metalTier;
	}

	// null in configs from before 0.22.0
	public boolean isIronman() {
		return Boolean.TRUE.equals(ironman);
	}

	public double taxFactor() {
		return 1 - bazaarTax / 100;
	}
}
