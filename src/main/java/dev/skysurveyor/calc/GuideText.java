package dev.skysurveyor.calc;

public final class GuideText {
	private GuideText() {
	}

	// plain words, "1l" read as "11" (review 11-27)
	public static String lapis(int count) {
		return count + " Lapis";
	}

	// "Always" or "3 Lapis", the column says from
	public static String from(int count) {
		return count == 0 ? "Always" : lapis(count);
	}

	// 1st green count, like the headline + the share (nick 11-26, same as his sheet)
	public static String mineFrom(ShaftModel.Row row) {
		return row.mineFrom() == null ? "§cSkip" : "§a" + from(row.mineFrom());
	}

	// 1st green count too (nick 11-26)
	public static String campfire(ShaftModel.Row row, double band) {
		Integer from = row.campfireFrom(band);
		// grey, an answer not bad news
		return from == null ? "§7Never" : "§a" + from(from);
	}

	public static String colour(double gain, double band) {
		return Verdict.State.of(gain, band).colour();
	}
}
