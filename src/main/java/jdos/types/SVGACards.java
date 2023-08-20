package jdos.types;

public enum SVGACards {
    SVGA_None(0),
	SVGA_S3Trio(1),
	SVGA_TsengET4K(2),
	SVGA_TsengET3K(3),
	SVGA_ParadisePVGA1A(4),
    SVGA_QEMU(5);

	private final int value;

	SVGACards(int value) {
		this.value = value;
	}

	public int getValue() {
		return value;
	}
}
