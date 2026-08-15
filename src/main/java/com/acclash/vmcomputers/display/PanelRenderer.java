package com.acclash.vmcomputers.display;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

/**
 * Draws one 128x128 panel of a monitor.
 *
 * <p>Holds its own palette-index buffer and copies it onto the canvas. {@code CraftMapCanvas.setPixel}
 * compares against the existing value and marks nothing dirty when they match, and the server sends
 * only the bounding box of what genuinely changed -- so correctness never depended on writing less
 * than everything. Speed does: this tracks the rectangle it has not handed the canvas yet, and
 * repaints only that. A blinking cursor is then a few dozen {@code setPixel} calls rather than
 * 16,384, and that difference lands on the main thread, once per changed panel per viewer.
 *
 * <p>The rectangle starts as the whole panel, which is not an optimisation but a requirement: a
 * fresh canvas is entirely "unset", and any pixel never written would merge as transparent.
 *
 * <p>Non-contextual on purpose: every viewer sees the same screen, so there is no reason to render
 * per player. Whichever viewer is served first does the painting, and the canvas keeps its buffer,
 * so the rest still merge a complete picture.
 */
public final class PanelRenderer extends MapRenderer {

    /** Map colour 0 is transparent, not black -- an unwritten map shows the wall behind it. */
    public static final byte TRANSPARENT = 0;

    private static final int SIZE = 128;

    private final byte[] buffer = new byte[SIZE * SIZE];
    private volatile long generation = 1;

    // The part of the canvas that has not been given the current picture yet. Empty when paintMaxX
    // is negative; starts as the whole panel, since a fresh canvas holds nothing.
    private int paintMinX;
    private int paintMinY;
    private int paintMaxX = SIZE - 1;
    private int paintMaxY = SIZE - 1;

    public PanelRenderer() {
        super(false);
    }

    /** Fills the whole panel with one colour. */
    public void fill(byte colour) {
        synchronized (buffer) {
            java.util.Arrays.fill(buffer, colour);
            growPaint(0, 0, SIZE - 1, SIZE - 1);
            generation++;
        }
    }

    /** Widens the region the canvas still owes. Caller holds the buffer lock. */
    private void growPaint(int minX, int minY, int maxX, int maxY) {
        if (minX < paintMinX) {
            paintMinX = minX;
        }
        if (minY < paintMinY) {
            paintMinY = minY;
        }
        if (maxX > paintMaxX) {
            paintMaxX = maxX;
        }
        if (maxY > paintMaxY) {
            paintMaxY = maxY;
        }
    }

    /**
     * Counts changes to the picture, so a sender can tell what a given viewer is still missing.
     *
     * <p>A flag would not do. Viewers are served under a per-player budget, so one tick can send a
     * panel to one player and not another, and a flag cleared after the first would strand the
     * second on a stale picture until something happened to change it again. Comparing a number
     * each viewer was last given has no such gap.
     *
     * <p>Distinct from the paint rectangle, which asks only what the canvas still needs and is
     * emptied by the render itself.
     */
    public long generation() {
        return generation;
    }

    /**
     * Copies a rectangle of palette indices into this panel.
     *
     * @param source      palette indices for the whole framebuffer
     * @param sourceWidth width of {@code source} in pixels
     * @param sourceX     left edge of the region belonging to this panel
     * @param sourceY     top edge of the region belonging to this panel
     * @param destX       where that region starts within this panel
     * @param width       region width
     */
    public void blit(byte[] source, int sourceWidth, int sourceX, int sourceY,
                     int destX, int destY, int width, int height) {
        boolean altered = false;
        synchronized (buffer) {
            for (int row = 0; row < height; row++) {
                int from = (sourceY + row) * sourceWidth + sourceX;
                int to = (destY + row) * SIZE + destX;
                // Compare before copying. Arrays.mismatch is a vectorised intrinsic, so a row that
                // has not changed costs less than the copy this skips -- and the answer is worth
                // far more than the copy, because it is what stops an unchanged panel being sent.
                int first = java.util.Arrays.mismatch(source, from, from + width, buffer, to, to + width);
                if (first < 0) {
                    continue;
                }
                // Walk in from the far end too, so the repaint covers the characters that changed
                // rather than the whole scanline they sit on.
                int last = width - 1;
                while (last > first && source[from + last] == buffer[to + last]) {
                    last--;
                }
                System.arraycopy(source, from + first, buffer, to + first, last - first + 1);
                growPaint(destX + first, destY + row, destX + last, destY + row);
                altered = true;
            }
            if (altered) {
                generation++;
            }
        }
    }

    public void setPixel(int x, int y, byte colour) {
        if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) {
            return;
        }
        synchronized (buffer) {
            if (buffer[y * SIZE + x] == colour) {
                return;
            }
            buffer[y * SIZE + x] = colour;
            growPaint(x, y, x, y);
            generation++;
        }
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        synchronized (buffer) {
            if (paintMaxX < 0) {
                return;
            }
            for (int y = paintMinY; y <= paintMaxY; y++) {
                int row = y * SIZE;
                for (int x = paintMinX; x <= paintMaxX; x++) {
                    canvas.setPixel(x, y, buffer[row + x]);
                }
            }
            paintMinX = SIZE;
            paintMinY = SIZE;
            paintMaxX = -1;
            paintMaxY = -1;
        }
    }
}
