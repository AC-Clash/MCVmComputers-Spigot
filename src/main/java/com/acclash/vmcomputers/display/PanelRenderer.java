package com.acclash.vmcomputers.display;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

/**
 * Draws one 128x128 panel of a monitor.
 *
 * <p>Holds its own palette-index buffer and copies it onto the canvas. Writing every pixel each
 * pass looks wasteful but is not: {@code CraftMapCanvas.setPixel} compares against the existing
 * value and returns without marking anything dirty when they match, and the server then transmits
 * only the bounding box of what genuinely changed. So an unchanged panel costs a memory scan and no
 * network traffic at all, and this class does not need damage tracking of its own.
 *
 * <p>Non-contextual on purpose: every viewer sees the same screen, so there is no reason to render
 * per player.
 */
public final class PanelRenderer extends MapRenderer {

    /** Map colour 0 is transparent, not black -- an unwritten map shows the wall behind it. */
    public static final byte TRANSPARENT = 0;

    private static final int SIZE = 128;

    private final byte[] buffer = new byte[SIZE * SIZE];
    private volatile boolean dirty = true;
    private volatile long generation = 1;

    public PanelRenderer() {
        super(false);
    }

    /** Fills the whole panel with one colour. */
    public void fill(byte colour) {
        synchronized (buffer) {
            java.util.Arrays.fill(buffer, colour);
            generation++;
        }
        dirty = true;
    }

    /**
     * Counts changes to the picture, so a sender can tell what a given viewer is still missing.
     *
     * <p>A flag would not do. Viewers are served under a per-player budget, so one tick can send a
     * panel to one player and not another, and a flag cleared after the first would strand the
     * second on a stale picture until something happened to change it again. Comparing a number
     * each viewer was last given has no such gap.
     *
     * <p>Distinct from {@link #isDirty()}, which only asks whether the canvas needs repainting and
     * is cleared by the render itself.
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
                if (java.util.Arrays.mismatch(source, from, from + width, buffer, to, to + width) < 0) {
                    continue;
                }
                System.arraycopy(source, from, buffer, to, width);
                altered = true;
            }
            if (altered) {
                generation++;
            }
        }
        if (altered) {
            dirty = true;
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
            generation++;
        }
        dirty = true;
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        // render() is invoked for every map, every tick, per viewer. A 24-panel projector would
        // otherwise cost ~393k setPixel calls per tick even while the guest sits idle. The canvas
        // keeps its buffer between renders, so skipping an unchanged panel leaves the correct
        // image on screen.
        if (!dirty) {
            return;
        }

        synchronized (buffer) {
            int i = 0;
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    canvas.setPixel(x, y, buffer[i++]);
                }
            }
        }
        dirty = false;
    }

    public boolean isDirty() {
        return dirty;
    }
}
