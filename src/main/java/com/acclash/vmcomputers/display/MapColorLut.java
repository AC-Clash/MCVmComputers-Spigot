package com.acclash.vmcomputers.display;

import org.bukkit.map.MapPalette;

import java.awt.Color;

/**
 * RGB -> Minecraft map palette index lookup table.
 *
 * <p>{@link MapPalette#matchColor(Color)} does a linear scan over the whole palette for every
 * single pixel, which is far too slow to run over a framebuffer every frame. This precomputes the
 * answer for a quantized RGB cube once, so the per-pixel cost becomes one array lookup.
 *
 * <p>The distance function is copied from Bukkit's so the output matches what {@code matchColor}
 * would have picked, apart from the error introduced by quantizing the cube to {@code bits} per
 * channel.
 *
 * <p>Dithering, when enabled, is ordered (Bayer) rather than error-diffusing. That is deliberate:
 * error diffusion propagates each pixel's error into its neighbours, so a single changed pixel in
 * the guest would dirty every pixel after it and destroy the damage tracking the whole renderer
 * depends on. An ordered matrix depends only on (x, y), so identical input always produces
 * identical output and unchanged regions stay unchanged.
 */
public final class MapColorLut {

    /** Bits per channel in the lookup cube. 6 -> 262144 entries (256 KiB). */
    public static final int DEFAULT_BITS = 6;

    /** Default dither strength, in 0-255 colour units. */
    public static final int DEFAULT_DITHER_SPREAD = 20;

    /** 8x8 ordered dither matrix, values 0-63. */
    private static final int[] BAYER_8 = {
             0, 32,  8, 40,  2, 34, 10, 42,
            48, 16, 56, 24, 50, 18, 58, 26,
            12, 44,  4, 36, 14, 46,  6, 38,
            60, 28, 52, 20, 62, 30, 54, 22,
             3, 35, 11, 43,  1, 33,  9, 41,
            51, 19, 59, 27, 49, 17, 57, 25,
            15, 47,  7, 39, 13, 45,  5, 37,
            63, 31, 55, 23, 61, 29, 53, 21
    };

    private final int bits;
    private final int shift;
    private final int levels;
    private final byte[] lut;

    /** Palette indices that are actually usable (opaque). */
    private final byte[] paletteIndices;
    private final int[] paletteR;
    private final int[] paletteG;
    private final int[] paletteB;

    private MapColorLut(int bits, byte[] paletteIndices, int[] r, int[] g, int[] b, byte[] lut) {
        this.bits = bits;
        this.shift = 8 - bits;
        this.levels = 1 << bits;
        this.paletteIndices = paletteIndices;
        this.paletteR = r;
        this.paletteG = g;
        this.paletteB = b;
        this.lut = lut;
    }

    /**
     * Builds the table. This is a few hundred milliseconds of pure CPU at 6 bits, so call it once
     * off the main thread during startup rather than lazily.
     */
    public static MapColorLut build(int bits) {
        if (bits < 4 || bits > 8) {
            throw new IllegalArgumentException("bits must be 4..8, got " + bits);
        }

        // Collect the opaque part of the palette. Indices 0-3 are transparent, and the palette's
        // length has changed between versions, so probe rather than assume.
        byte[] idx = new byte[256];
        int[] pr = new int[256];
        int[] pg = new int[256];
        int[] pb = new int[256];
        int n = 0;
        for (int i = 0; i < 256; i++) {
            Color c;
            try {
                c = MapPalette.getColor((byte) i);
            } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
                continue;
            }
            if (c == null || c.getAlpha() < 128) {
                continue;
            }
            idx[n] = (byte) i;
            pr[n] = c.getRed();
            pg[n] = c.getGreen();
            pb[n] = c.getBlue();
            n++;
        }
        if (n == 0) {
            throw new IllegalStateException("map palette appears to be empty");
        }

        byte[] pIdx = new byte[n];
        int[] r = new int[n];
        int[] g = new int[n];
        int[] b = new int[n];
        System.arraycopy(idx, 0, pIdx, 0, n);
        System.arraycopy(pr, 0, r, 0, n);
        System.arraycopy(pg, 0, g, 0, n);
        System.arraycopy(pb, 0, b, 0, n);

        int levels = 1 << bits;
        int shift = 8 - bits;
        byte[] lut = new byte[levels * levels * levels];

        // Centre each cube cell so the representative colour is the middle of the range it covers
        // rather than its lower corner.
        int half = (1 << shift) >> 1;

        for (int ri = 0; ri < levels; ri++) {
            int rr = (ri << shift) + half;
            if (rr > 255) rr = 255;
            for (int gi = 0; gi < levels; gi++) {
                int gg = (gi << shift) + half;
                if (gg > 255) gg = 255;
                int rowBase = (ri * levels + gi) * levels;
                for (int bi = 0; bi < levels; bi++) {
                    int bb = (bi << shift) + half;
                    if (bb > 255) bb = 255;

                    double best = Double.MAX_VALUE;
                    int bestI = 0;
                    for (int p = 0; p < n; p++) {
                        double d = distance(rr, gg, bb, r[p], g[p], b[p]);
                        if (d < best) {
                            best = d;
                            bestI = p;
                        }
                    }
                    lut[rowBase + bi] = pIdx[bestI];
                }
            }
        }

        return new MapColorLut(bits, pIdx, r, g, b, lut);
    }

    /** Bukkit's "redmean" weighted distance, replicated so output matches {@code matchColor}. */
    private static double distance(int r1, int g1, int b1, int r2, int g2, int b2) {
        double rmean = (r1 + r2) / 2.0;
        double r = r1 - r2;
        double g = g1 - g2;
        double b = b1 - b2;
        double weightR = 2 + rmean / 256.0;
        double weightB = 2 + (255 - rmean) / 256.0;
        return weightR * r * r + 4.0 * g * g + weightB * b * b;
    }

    public int bits() {
        return bits;
    }

    /** Number of distinct opaque colours available. */
    public int paletteSize() {
        return paletteIndices.length;
    }

    /** Single-pixel lookup. */
    public byte match(int r, int g, int b) {
        return lut[(((r >> shift) * levels) + (g >> shift)) * levels + (b >> shift)];
    }

    /**
     * Quantizes a packed-ARGB framebuffer into map palette indices, one output byte per pixel.
     *
     * @param argb   source pixels, {@code width * height} of them, row-major
     * @param out    destination indices, same length and layout
     */
    public void quantize(int[] argb, int width, int height, byte[] out) {
        int count = width * height;
        for (int i = 0; i < count; i++) {
            int p = argb[i];
            out[i] = lut[((((p >> 16 & 0xFF) >> shift) * levels) + ((p >> 8 & 0xFF) >> shift)) * levels
                    + ((p & 0xFF) >> shift)];
        }
    }

    /** As {@link #quantize} but with ordered dithering, which is worth it on this palette. */
    public void quantizeDithered(int[] argb, int width, int height, byte[] out, int spread) {
        for (int y = 0; y < height; y++) {
            int row = y * width;
            int bayerRow = (y & 7) << 3;
            for (int x = 0; x < width; x++) {
                int p = argb[row + x];
                // (bayer - 32) / 32 * spread, i.e. roughly -spread .. +spread
                int off = ((BAYER_8[bayerRow + (x & 7)] - 32) * spread) >> 5;

                int r = (p >> 16 & 0xFF) + off;
                int g = (p >> 8 & 0xFF) + off;
                int b = (p & 0xFF) + off;
                if (r < 0) r = 0; else if (r > 255) r = 255;
                if (g < 0) g = 0; else if (g > 255) g = 255;
                if (b < 0) b = 0; else if (b > 255) b = 255;

                out[row + x] = lut[(((r >> shift) * levels) + (g >> shift)) * levels + (b >> shift)];
            }
        }
    }

    /**
     * Reference implementation using Bukkit's per-pixel matcher. Only here so the benchmark can
     * show the difference; never use this on a live framebuffer.
     */
    @SuppressWarnings("deprecation")
    public static void quantizeWithMapPalette(int[] argb, int width, int height, byte[] out) {
        int count = width * height;
        for (int i = 0; i < count; i++) {
            int p = argb[i];
            out[i] = MapPalette.matchColor(new Color(p >> 16 & 0xFF, p >> 8 & 0xFF, p & 0xFF));
        }
    }
}
