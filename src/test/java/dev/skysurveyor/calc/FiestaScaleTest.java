package dev.skysurveyor.calc;

import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.calc.Fiesta.State;

import static org.junit.jupiter.api.Assertions.assertEquals;

// vs guide_model.py "== fiesta" lines (11-26)
class FiestaScaleTest {
	private static final GameData DATA = GameData.defaults();

	@Test
	void gemsPerBlockFollowsTheFortuneFactor() {
		assertEquals(137.5, FiestaScale.byFortune(110, State.NONE, State.COLE, DATA), 1e-9);
		assertEquals(165.0, FiestaScale.byFortune(110, State.NONE, State.FLASK, DATA), 1e-9);
		assertEquals(137.5, FiestaScale.byFortune(165, State.FLASK, State.COLE, DATA), 1e-9);
		assertEquals(110, FiestaScale.byFortune(110, State.COLE, State.COLE, DATA), 1e-9);
	}

	@Test
	void glossyPerBlockByBreakingPower() {
		double[] python = {0, 0.0018, 0.0036, 0, 0.0016, 0.0032, 0, 0.0014, 0.0028, 0, 0.0012, 0.0024};
		int i = 0;
		for (String code : new String[] {"JASP", "TOPA", "JADE", "RUBY"}) {
			for (State state : State.values()) {
				assertEquals(python[i++], FiestaScale.glossyPerBlock(DATA.gem(code), state, DATA), 1e-12, code + " " + state);
			}
		}
	}

	// nicks ShaftModelTest numbers w/ cole + a flask
	@Test
	void nickScaledThroughTheModel() {
		double[] other = {302_225.8278, 532_351.6556};
		double[] leaving = {80_559_838.9, 103_856_563.7};
		double[][] jasper = {{1261722.4, 2144180.2, 3026638.0, 3909095.8, 4791553.6}, {2142412.0, 3201361.3, 4260310.7, 5319260.1, 6378209.5}};
		State[] states = {State.COLE, State.FLASK};
		for (int i = 0; i < states.length; i++) {
			Map<NumberId, Double> v = ShaftModelTest.nick();
			v.put(NumberId.GEMS_PER_BLOCK, FiestaScale.byFortune(110, State.NONE, states[i], DATA));
			v.put(NumberId.COINS_PER_SPAWN, FiestaScale.byFortune(1_400_000, State.NONE, states[i], DATA));
			// other drops of a 755 s shaft + glossy on 975 peridot blocks at 1.65M
			double scaled = (72_100 * 755 / 60.0 + 0.65 * 1500 * FiestaScale.glossyPerBlock(DATA.gem("PERI"), states[i], DATA) * 1_650_000) / (755 / 60.0);
			assertEquals(other[i], scaled, 1e-3);
			v.put(NumberId.OTHER, scaled);
			ShaftModel.Guide guide = ShaftModel.run(ShaftModelTest.numbers(v, "PERI"), ShaftModelTest.PRICES, 2_810_000.0, DATA);
			assertEquals(leaving[i], guide.perHour(guide.leaving()), 1, states[i] + " leaving");
			for (int k = 0; k <= 4; k++) assertEquals(jasper[i][k], guide.row("JASP").lapis().get(k).gain(), 0.5, states[i] + " jasper " + k);
		}
	}
}
