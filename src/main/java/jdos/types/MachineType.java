package jdos.types;

public enum MachineType {

	MCH_HERC(0),
	MCH_CGA(1),
	 MCH_TANDY(2),
	MCH_PCJR(3),
	 MCH_EGA(4),
	MCH_VGA(5);

	private final int value;

	MachineType(int value) {
		this.value = value;
	}

	public int getValue() {
		return value;
	}
}
