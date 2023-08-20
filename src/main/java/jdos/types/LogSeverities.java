package jdos.types;

public enum LogSeverities {
   LOG_NORMAL(0),
	LOG_WARN(1),
	LOG_ERROR(2);

	private final int value;

	LogSeverities(int value) {
		this.value = value;
	}

	public int getValue() {
		return value;
	}
}
