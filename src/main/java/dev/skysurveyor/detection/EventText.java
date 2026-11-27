package dev.skysurveyor.detection;

import dev.skysurveyor.detection.ShaftEvent.ShaftEntered;
import dev.skysurveyor.detection.ShaftEvent.ShaftLeft;

// no times, live + replay differ by a ms
public final class EventText {
	private EventText() {
	}

	public static String of(ShaftEvent event) {
		return switch (event) {
			case ShaftEntered entered -> "ShaftEntered[" + id(entered.shaft()) + "]";
			case ShaftLeft left -> "ShaftLeft[" + summary(left.shaft()) + "]";
			default -> event.toString();
		};
	}

	public static String summary(Shaft shaft) {
		return id(shaft) + " " + shaft.exitReason() + " +" + shaft.exitPowder() + " powder"
				+ ", corpses " + shaft.corpses() + ", loots " + shaft.loots().size()
				+ ", campfires " + shaft.campfiresPlaced().size() + ", cold " + shaft.cold()
				+ ", pristine " + shaft.pristineItems() + ", sacks " + shaft.sacksAdded();
	}

	private static String id(Shaft shaft) {
		return shaft.id() == null ? "?" : shaft.id().id();
	}
}
