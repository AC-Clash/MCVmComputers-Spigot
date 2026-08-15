package com.acclash.vmcomputers.display;

/**
 * Fits a guest framebuffer inside a monitor without cropping it.
 *
 * <p>Scaling happens in RGB, before quantization. Averaging palette indices would be meaningless --
 * index 40 is not "between" 39 and 41 -- so downscaling has to occur while the pixels are still
 * colours.
 *
 * <p>A box filter is used rather than nearest-neighbour because the content is mostly text. Dropping
 * pixels makes glyph stems vanish entirely at non-integer ratios, whereas averaging turns them grey
 * and keeps the shape readable.
 *
 * <p>Free of Bukkit so the arithmetic can be tested directly.
 */
public final class ImageScaler {

    private ImageScaler() {
    }

    /**
     * Largest size that fits {@code srcW x srcH} inside {@code maxW x maxH} keeping aspect ratio.
     *
     * @return {@code {width, height}}, never larger than the source (images are never scaled up)
     */
    public static int[] fitDimensions(int srcW, int srcH, int maxW, int maxH) {
        if (srcW <= 0 || srcH <= 0) {
            return new int[]{0, 0};
        }
        if (srcW <= maxW && srcH <= maxH) {
            return new int[]{srcW, srcH};
        }
        double scale = Math.min((double) maxW / srcW, (double) maxH / srcH);
        int width = Math.max(1, (int) Math.floor(srcW * scale));
        int height = Math.max(1, (int) Math.floor(srcH * scale));
        return new int[]{width, height};
    }

    /**
     * Box-filter downscale of a packed-ARGB image.
     *
     * @param out reused destination buffer; must hold at least {@code dstW * dstH} pixels
     */
    public static void scale(int[] src, int srcW, int srcH, int[] out, int dstW, int dstH) {
        if (dstW == srcW && dstH == srcH) {
            System.arraycopy(src, 0, out, 0, srcW * srcH);
            return;
        }

        for (int dy = 0; dy < dstH; dy++) {
            // Source rows covered by this destination row.
            int y0 = (int) ((long) dy * srcH / dstH);
            int y1 = (int) (((long) dy + 1) * srcH / dstH);
            if (y1 <= y0) {
                y1 = y0 + 1;
            }
            if (y1 > srcH) {
                y1 = srcH;
            }

            int rowBase = dy * dstW;
            for (int dx = 0; dx < dstW; dx++) {
                int x0 = (int) ((long) dx * srcW / dstW);
                int x1 = (int) (((long) dx + 1) * srcW / dstW);
                if (x1 <= x0) {
                    x1 = x0 + 1;
                }
                if (x1 > srcW) {
                    x1 = srcW;
                }

                int red = 0;
                int green = 0;
                int blue = 0;
                int count = 0;
                for (int sy = y0; sy < y1; sy++) {
                    int offset = sy * srcW;
                    for (int sx = x0; sx < x1; sx++) {
                        int pixel = src[offset + sx];
                        red += (pixel >> 16) & 0xFF;
                        green += (pixel >> 8) & 0xFF;
                        blue += pixel & 0xFF;
                        count++;
                    }
                }
                if (count == 0) {
                    count = 1;
                }
                out[rowBase + dx] = 0xFF000000
                        | ((red / count) << 16)
                        | ((green / count) << 8)
                        | (blue / count);
            }
        }
    }
}
