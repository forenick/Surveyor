package dev.skysurveyor.calc;

import java.util.List;

import dev.skysurveyor.data.CorpseType;
import dev.skysurveyor.detection.Corpse;
import dev.skysurveyor.detection.ShaftId;
import dev.skysurveyor.money.Coins;

// calculator-design.md slice 4
public record Verdict(State state, double gain) {
	public enum State {
		MINE("§a"),
		CLOSE("§e"),
		SKIP("§c");

		private final String colour;

		State(String colour) {
			this.colour = colour;
		}

		// red < 0, yellow 0 to band, green from band (nick 11-26: a loss is never close)
		public static State of(double gain, double band) {
			return gain >= band ? MINE : gain >= 0 ? CLOSE : SKIP;
		}

		public String colour() {
			return colour;
		}
	}

	// the row of the shaft u are in, its layouts own efficiency. null = no gem shaft or pace not tracked
	private static ShaftModel.Row row(ShaftModel.Guide guide, YourNumbers numbers, ShaftId id, List<Corpse> corpses) {
		if (guide == null || numbers == null || !numbers.ownPace() || id == null || id.type() == null || corpses.isEmpty()) return null;
		return guide.row(id.type(), id.variant());
	}

	// null = nothing to say or no price. lapis = every lapis corpse, looted first
	public static Verdict of(ShaftModel.Guide guide, YourNumbers numbers, ShaftId id, List<Corpse> corpses) {
		ShaftModel.Row row = row(guide, numbers, id, corpses);
		if (row == null || row.perFine() == null) return null;
		double gain = row.lapis().get(lapis(corpses)).gain();
		return new Verdict(State.of(gain, guide.band()), gain);
	}

	// coins vs leaving, null = nothing to say. placed = used already, next adds less
	public static Double campfire(ShaftModel.Guide guide, YourNumbers numbers, ShaftId id, List<Corpse> corpses, int placed) {
		ShaftModel.Row row = row(guide, numbers, id, corpses);
		if (row == null || row.perFine() == null || guide.campfireCost() == null) return null;
		ShaftModel.Shaft shaft = row.lapis().get(lapis(corpses));
		// only the part b4 the route is done counts
		double after = Math.max(0, shaft.route() - mining(guide, numbers));
		double extra = Math.min(ColdModel.campfireSeconds(guide.freeze(), placed + 1), after) - Math.min(ColdModel.campfireSeconds(guide.freeze(), placed), after);
		return extra * shaft.extraSecond() - guide.campfireCost();
	}

	// unlooted key corpse looted now. left = share of mining time ahead, over 1 w/ a campfire placed
	public static Double key(ShaftModel.Guide guide, YourNumbers numbers, ShaftId id, List<Corpse> corpses, String corpseType, double left) {
		ShaftModel.Row row = row(guide, numbers, id, corpses);
		if (row == null) return null;
		int lapis = lapis(corpses);
		Double atStart = guide.keyGain(row, lapis, corpseType);
		if (atStart == null) return null;
		double pristine = row.lapis().get(lapis).pristine();
		return atStart - pristine * (1 - Math.max(0, left));
	}

	// setup left out
	public static double left(ShaftModel.Guide guide, YourNumbers numbers, long insideMillis) {
		return left(guide, numbers, insideMillis, 0);
	}

	// no route
	public static double left(ShaftModel.Guide guide, YourNumbers numbers, long insideMillis, int placed) {
		return left(guide, numbers, insideMillis, placed, Double.POSITIVE_INFINITY);
	}

	// the shaft u are in, mining stops when its route is done
	public static double left(ShaftModel.Guide guide, YourNumbers numbers, ShaftId id, long insideMillis, int placed) {
		ShaftModel.Row row = guide == null || id == null || id.type() == null ? null : guide.row(id.type(), id.variant());
		return left(guide, numbers, insideMillis, placed, row == null ? Double.POSITIVE_INFINITY : row.lapis().getFirst().route());
	}

	// placed campfires' minutes are mining too. vs no-campfire shaft, so can pass 1
	private static double left(ShaftModel.Guide guide, YourNumbers numbers, long insideMillis, int placed, double route) {
		double setup = setup(guide, numbers);
		double freezeMining = mining(guide, numbers);
		// pristine is worked out over this
		double mining = Math.min(freezeMining, route);
		if (mining <= 0) return 0;
		double done = Math.max(0, insideMillis / 1000.0 - setup);
		double until = Math.min(freezeMining + ColdModel.campfireSeconds(guide.freeze(), placed), route);
		return Math.max(0, (until - done) / mining);
	}

	private static double setup(ShaftModel.Guide guide, YourNumbers numbers) {
		return Math.min(numbers.get(NumberId.SETUP), guide.freeze());
	}

	// till u freeze, no campfire
	private static double mining(ShaftModel.Guide guide, YourNumbers numbers) {
		return guide.freeze() - setup(guide, numbers);
	}

	private static int lapis(List<Corpse> corpses) {
		return (int) Math.min(ShaftModel.MAX_LAPIS, corpses.stream().filter(c -> c.type().equals(CorpseType.LAPIS.label())).count());
	}

	private static final String OVER = " §7over leaving";

	public String text() {
		return switch (state) {
			// titles have no hover, so say what it is
			case MINE -> "§aMine: " + Coins.signed(gain) + OVER;
			// same word as the legend (nick 11-27)
			case CLOSE -> "§eYour call: " + Coins.signed(gain) + OVER;
			case SKIP -> "§cSkip: " + Coins.signed(gain) + OVER;
		};
	}
}
