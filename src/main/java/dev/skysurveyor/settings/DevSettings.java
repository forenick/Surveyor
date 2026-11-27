package dev.skysurveyor.settings;

import com.google.gson.annotations.SerializedName;

// "dev" in config.json, names never rename (docs/dev-tools-design.md)
public record DevSettings(AutoRecord autoRecord, KeepRecordings keepRecordings, Boolean recordScreens, Boolean anonymize,
		Boolean debugOverlay) {
	// nick 11-21: auto record off. 30 days + anonymize = claudes picks
	public static final DevSettings DEFAULTS = new DevSettings(AutoRecord.OFF, KeepRecordings.DAYS_30, true, true, false);

	public enum AutoRecord {
		@SerializedName("off")
		OFF,
		@SerializedName("mining_areas")
		MINING_AREAS
	}

	public enum KeepRecordings {
		@SerializedName("7_days")
		DAYS_7(7),
		@SerializedName("30_days")
		DAYS_30(30),
		@SerializedName("90_days")
		DAYS_90(90),
		@SerializedName("forever")
		FOREVER(-1);

		private final int days;

		KeepRecordings(int days) {
			this.days = days;
		}

		// -1 = forever
		public int days() {
			return days;
		}
	}

	// missing = default
	public DevSettings repaired() {
		return new DevSettings(autoRecord == null ? DEFAULTS.autoRecord : autoRecord,
				keepRecordings == null ? DEFAULTS.keepRecordings : keepRecordings,
				recordScreens == null ? DEFAULTS.recordScreens : recordScreens, anonymize == null ? DEFAULTS.anonymize : anonymize,
				debugOverlay == null ? DEFAULTS.debugOverlay : debugOverlay);
	}

	public boolean isRecordScreens() {
		return recordScreens;
	}

	public boolean isAnonymize() {
		return anonymize;
	}

	public boolean isDebugOverlay() {
		return debugOverlay;
	}

	public DevSettings withAutoRecord(AutoRecord value) {
		return new DevSettings(value, keepRecordings, recordScreens, anonymize, debugOverlay);
	}

	public DevSettings withKeepRecordings(KeepRecordings value) {
		return new DevSettings(autoRecord, value, recordScreens, anonymize, debugOverlay);
	}

	public DevSettings withRecordScreens(boolean value) {
		return new DevSettings(autoRecord, keepRecordings, value, anonymize, debugOverlay);
	}

	public DevSettings withAnonymize(boolean value) {
		return new DevSettings(autoRecord, keepRecordings, recordScreens, value, debugOverlay);
	}

	public DevSettings withDebugOverlay(boolean value) {
		return new DevSettings(autoRecord, keepRecordings, recordScreens, anonymize, value);
	}
}
