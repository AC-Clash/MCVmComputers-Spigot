package com.acclash.vmcomputers.computer;

/**
 * Packs block coordinates into a single long for use as a map key.
 *
 * <p>Lets the click index be a flat {@code HashMap<Long, ...>} rather than allocating a position
 * object on every lookup, which matters because that lookup runs on every player interaction.
 */
public final class BlockKey {

    /** Bits reserved for x and z; covers the +/-30,000,000 world border. */
    private static final int HORIZONTAL_BITS = 26;
    /** Bits reserved for y; covers -64..320 with room for future height changes. */
    private static final int VERTICAL_BITS = 12;

    private static final long HORIZONTAL_MASK = (1L << HORIZONTAL_BITS) - 1;
    private static final long VERTICAL_MASK = (1L << VERTICAL_BITS) - 1;
    private static final int Z_SHIFT = VERTICAL_BITS;
    private static final int X_SHIFT = VERTICAL_BITS + HORIZONTAL_BITS;

    private BlockKey() {
    }

    /**
     * Packs a block position.
     *
     * <p>Masking rather than shifting signed values is what makes negative coordinates work: two's
     * complement means {@code -200} and {@code 67108664} share the low 26 bits, and since the
     * unpacked value is never needed, that aliasing is harmless as long as it stays inside the
     * world border.
     */
    public static long pack(int x, int y, int z) {
        return ((x & HORIZONTAL_MASK) << X_SHIFT)
                | ((z & HORIZONTAL_MASK) << Z_SHIFT)
                | (y & VERTICAL_MASK);
    }
}
