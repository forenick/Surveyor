package dev.skysurveyor.calc;

import dev.skysurveyor.calc.Fiesta.State;

// a number moved between fiesta states (calculator-design.md, research/11 s12-14)
// same maths in calc-prototype/guide_model.py, FiestaScaleTest checks both
public final class FiestaScale {
	private FiestaScale() {
	}

	// mining fortune: cole x1.25, flask x1.5 on top (CW Mining_Fiesta, Fiesta_Flask)
	public static double fortune(State state, GameData data) {
		return switch (state) {
			case NONE -> 1;
			case COLE -> data.value("fiesta_fortune");
			case FLASK -> data.value("flask_fortune");
		};
	}

	// gemstone fortune not multiplied i think (unconfirmed)
	// so 3-5% off for nick, high going up, low going down
	public static double byFortune(double value, State from, State to, GameData data) {
		return value * fortune(to, data) / fortune(from, data);
	}

	// spread blocks too. refined mind + scatha shard unknown here
	public static double glossyPerBlock(GameData.Gem gem, State state, GameData data) {
		if (state == State.NONE) return 0;
		double chance = gem.breakingPower() * data.value("glossy_per_power");
		return state == State.FLASK ? chance * data.value("flask_glossy") : chance;
	}
}
