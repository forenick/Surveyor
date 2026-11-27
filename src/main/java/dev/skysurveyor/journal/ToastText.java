package dev.skysurveyor.journal;

import java.util.ArrayList;
import java.util.List;

import dev.skysurveyor.data.ShaftNames;
import dev.skysurveyor.detection.Shaft.ExitReason;
import dev.skysurveyor.money.CoinBreakdown;
import dev.skysurveyor.money.Coins;
import dev.skysurveyor.money.ShaftValue;
import dev.skysurveyor.util.Format;

// nicks short card (Docs/mockups/toast-v1.html)
public record ToastText(String title, List<Row> rows) {
	// hud drops line too
	public static final String NO_PRICES = "no prices yet";

	public record Row(String label, String value) {
	}

	// session incl entry, null = no session row. value null = no prices yet
	public static ToastText of(JournalEntry entry, Journal.Session session, ShaftValue value, boolean ironman) {
		String title = (entry.shaftId() == null ? "§eMineshaft" : ShaftNames.defaults().coloredName(entry.shaftId())) + "  §f"
				+ Format.duration(entry.durationMillis()) + exit(entry.exitReason());
		List<Row> rows = new ArrayList<>();
		String corpses = SummaryText.corpses(entry);
		if (corpses != null) rows.add(new Row("Corpses", corpses));
		if (value == null) {
			rows.add(new Row("Coins", "§8" + NO_PRICES));
		} else if (value.lines().stream().allMatch(line -> line.coins() == null)) {
			// gold 0 reads as worthless
			if (!value.lines().isEmpty()) rows.add(new Row("Coins", CoinBreakdown.noPrice(ironman)));
		} else {
			double net = value.net();
			rows.add(new Row("Coins", (Math.round(net) < 0 ? "§c" : "§6") + Coins.signed(net)));
		}
		if (session != null) rows.add(new Row("Session", SummaryText.session(session)));
		return new ToastText(title, List.copyOf(rows));
	}

	private static String exit(ExitReason exit) {
		return switch (exit) {
			case DEATH -> " §c(froze)";
			case WARP -> " §7(warped out)";
			default -> "";
		};
	}

	// editor + settings preview, made up numbers
	public static ToastText example() {
		return new ToastText("§dJasper 1  §f4m 12s", List.of(
				new Row("Corpses", "§9Lapis 2/2§7, §fUmber 1/1"),
				new Row("Coins", "§6+1.2M"),
				new Row("Session", "§f5 shafts§7, avg spawn §f1m 30s")));
	}
}
