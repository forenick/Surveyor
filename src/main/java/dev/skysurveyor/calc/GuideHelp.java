package dev.skysurveyor.calc;

import java.util.List;

import dev.skysurveyor.calc.YourNumbers.Source;
import dev.skysurveyor.mining.Efficiency;

// the guide tab explained, behind How it works (nick 11-27). his text to edit
public final class GuideHelp {
	public static final String BUTTON = "How it works";
	public static final String TITLE = "How the Calculator works";

	private GuideHelp() {
	}

	// icon null = plain line, else the source's icon + name go in front
	public record Line(Source icon, String text) {
	}

	public record Block(String title, List<Line> lines) {
	}

	// minedShafts = newest mined shafts behind the mining numbers, 0 = the range
	public static List<Block> blocks(int minedShafts) {
		// counts from the learner so the text cant drift
		long minutes = Efficiency.RECORD_MILLIS / 60_000;
		return List.of(
				block("The list",
						"A row is a shaft type. The columns under Lapis corpses are how many it has, 0 to " + ShaftModel.MAX_LAPIS + ".",
						"A cell is what mining that shaft makes over leaving it and spawning the next one. The time it takes is counted.",
						"§aGreen§7: mine it. §eYellow§7: a small gain, your call. §cRed§7: skip it. The line above the list says the same.",
						"Mine from is the first green count. Campfire and the key columns read the same way.",
						"Hover a cell for how it adds up, a gem for its ticks, price and coins per hour inside."),
				block("Leaving is worth",
						"What you make per hour when you leave every shaft that isn't green and spawn the next one.",
						"Every cell is measured against it. Red doesn't mean a shaft pays badly, only that spawning pays you more."),
				new Block("Your numbers", List.of(
						new Line(null, "The list is worked out from your numbers. Show in the Your numbers bar opens them, the bar counts them by where they come from."),
						new Line(Source.TRACKED, "from your own shafts"),
						new Line(Source.SET, "typed by you, used until you undo it"),
						new Line(Source.START, "a general number, used until yours is tracked"),
						new Line(Source.CALCULATED, "follows from your other numbers"),
						new Line(null, minedShafts > 0
								? "Spawn numbers use the range at the top, mining numbers your newest " + minedShafts + " mined shafts."
								: "Spawn and mining numbers use the range at the top."))),
				block("How long it takes",
						"Spawn numbers are yours after " + Learner.MIN_SPAWNS + " spawns.",
						"Mining numbers after the first shaft you mine for " + minutes + " minutes.",
						"Corpse odds after " + Learner.MIN_SHAFTS + " shafts.",
						"Until your mining is tracked the list runs on starting values and the entry title shows no verdict.",
						"A line on top of the list says what is still missing.",
						"Every shaft after that makes them steadier."),
				block("When a number looks wrong",
						"Open Your numbers and hover it for what it is and how many shafts it comes from.",
						"Click it and type your own. Right-click it to undo.",
						"Past shafts compares what your mined shafts made with what the list said.",
						// a median flips once the new ones are the bigger half
						minedShafts > 0 ? "New gear shows after about " + (minedShafts / 2 + 1) + " mined shafts."
								: "New gear shows once your newer mined shafts outnumber the older ones in the range."),
				block("In a shaft",
						"The entry title says Mine, Your call or Skip for the shaft you entered, with its own Lapis count.",
						"The HUD values Shaft gain, Campfire gain, Key corpses and Freeze in show it live. Add them in Edit HUD."));
	}

	private static Block block(String title, String... lines) {
		return new Block(title, List.of(lines).stream().map(line -> new Line(null, line)).toList());
	}
}
