package com.acclash.vmcomputers.display;

import com.acclash.vmcomputers.computer.Computer;
import org.bukkit.Bukkit;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The whole screen of one computer: a grid of {@link PanelRenderer}s behind a single framebuffer.
 *
 * <p>Owns the split between guest pixels and map tiles, including the letterbox border, so callers
 * push a framebuffer and never think about map boundaries.
 *
 * <p>Map renderers exist only at runtime -- restarting the server restores the default world-map
 * renderer on every map. Map ids are also the one part of a computer that cannot be derived from
 * its anchor, facing and size, since the server assigns them. So they are persisted, and this class
 * reattaches renderers to them on startup.
 */
public final class MonitorScreen {

    private static final int PANEL = 128;
    private static final int CURSOR_WIDTH = 10;

    private final Computer computer;
    private final MonitorSize size;
    private final List<PanelRenderer> panels;
    private final List<Integer> mapIds;

    /**
     * Host-drawn pointer, in guest pixels.
     *
     * <p>QEMU does not composite the guest's cursor into the framebuffer it sends over VNC -- it
     * offers the shape separately through the Cursor pseudo-encoding, and a guest using a hardware
     * cursor plane therefore appears to have no pointer at all. Drawing our own avoids that
     * entirely, and a chunky high-contrast arrow survives quantization to 244 colours far better
     * than a real 12x19 system cursor would.
     */
    private static final String[] CURSOR = {
            "X.........",
            "XX........",
            "XPX.......",
            "XPPX......",
            "XPPPX.....",
            "XPPPPX....",
            "XPPPPPX...",
            "XPPPPPPX..",
            "XPPPPPPPX.",
            "XPPPPPPPPX",
            "XPPPPPXXXX",
            "XPPXPPX...",
            "XPX.XPPX..",
            "XX...XPPX.",
            "X.....XPX.",
            ".......XX."
    };

    private final byte[] framebuffer;
    private int guestWidth;
    private int guestHeight;
    private volatile ScreenGeometry geometry;

    // Last presented image, kept so the cursor can be redrawn without waiting for a new frame.
    private byte[] lastImage = new byte[0];
    private int lastImageWidth;
    private int lastImageHeight;
    private byte lastBorder;
    private int cursorX = -1;
    private int cursorY = -1;
    private byte cursorFill;
    private byte cursorOutline;

    private MonitorScreen(Computer computer, List<PanelRenderer> panels, List<Integer> mapIds) {
        this.computer = computer;
        this.size = computer.monitorSize();
        this.panels = Collections.unmodifiableList(panels);
        this.mapIds = Collections.unmodifiableList(mapIds);
        this.framebuffer = new byte[size.pixelWidth() * size.pixelHeight()];
        this.guestWidth = size.guestWidth();
        this.guestHeight = size.guestHeight();
    }

    /**
     * Installs a fresh renderer on each of a computer's maps.
     *
     * @param mapIds map ids in the same row-major order as the layout's screen panels
     * @return the screen, or null if any map id no longer resolves
     */
    public static MonitorScreen attach(Computer computer, List<Integer> mapIds) {
        if (mapIds.size() != computer.monitorSize().mapCount()) {
            return null;
        }
        List<PanelRenderer> panels = new ArrayList<PanelRenderer>(mapIds.size());
        for (Integer id : mapIds) {
            MapView view = Bukkit.getMap(id.intValue());
            if (view == null) {
                return null;
            }
            // Drop whatever is there, including the default world-map renderer restored on restart.
            for (MapRenderer existing : new ArrayList<MapRenderer>(view.getRenderers())) {
                view.removeRenderer(existing);
            }
            PanelRenderer panel = new PanelRenderer();
            view.addRenderer(panel);
            panels.add(panel);
        }
        return new MonitorScreen(computer, panels, new ArrayList<Integer>(mapIds));
    }

    public Computer computer() {
        return computer;
    }

    public MonitorSize size() {
        return size;
    }

    public List<Integer> mapIds() {
        return mapIds;
    }

    /**
     * The guest's own framebuffer size, which may be larger than what is displayed.
     *
     * <p>Kept separately from the displayed size so pointer coordinates can be converted back:
     * the player aims at displayed pixels, but the guest only understands its own.
     */
    public void setGuestResolution(int width, int height) {
        this.guestWidth = width;
        this.guestHeight = height;
    }

    /** Size actually drawn on the screen, after any downscale. Drives the letterbox and the ray. */
    public void setDisplayedSize(int width, int height) {
        ScreenGeometry existing = geometry;
        if (existing != null) {
            // Letterbox borders move when the displayed size changes, so the ray mapping must too.
            existing.setGuestResolution(width, height);
        }
    }

    /**
     * Converts a displayed pixel to the guest pixel underneath it.
     *
     * <p>The identity when nothing is scaled, which is the case whenever the guest image already
     * fits the grid.
     */
    public int toGuestX(int displayedX) {
        return lastImageWidth <= 0 ? displayedX : displayedX * guestWidth / lastImageWidth;
    }

    public int toGuestY(int displayedY) {
        return lastImageHeight <= 0 ? displayedY : displayedY * guestHeight / lastImageHeight;
    }

    /** Paints the whole screen one colour. Black is the powered-off state. */
    public void fill(byte colour) {
        synchronized (framebuffer) {
            java.util.Arrays.fill(framebuffer, colour);
            lastImageWidth = 0;
            cursorX = -1;
        }
        pushAll();
    }

    /** Colours for the host-drawn pointer. */
    public void setCursorColours(byte fill, byte outline) {
        this.cursorFill = fill;
        this.cursorOutline = outline;
    }

    /**
     * Moves the host-drawn pointer and repaints immediately.
     *
     * <p>Only the two small rectangles the pointer left and arrived at are touched. Repainting the
     * whole screen would mark every panel dirty, and since each dirty panel is fully re-rendered on
     * the next tick, a 24-panel projector would spend some 393,000 setPixel calls per tick just to
     * move a 10x16 arrow -- twenty times a second, for as long as the player kept looking around.
     *
     * <p>Repainting here rather than waiting for the guest's next frame matters too, because an
     * idle guest sends nothing at all and the pointer still has to track the player's head.
     */
    public void setCursor(int imageX, int imageY) {
        int previousX;
        int previousY;
        int offsetX;
        int offsetY;
        synchronized (framebuffer) {
            if (cursorX == imageX && cursorY == imageY) {
                return;
            }
            previousX = cursorX;
            previousY = cursorY;
            cursorX = imageX;
            cursorY = imageY;
            if (lastImageWidth == 0) {
                return;
            }
            if (previousX >= 0) {
                restoreArea(previousX, previousY);
            }
            if (imageX >= 0) {
                drawCursor(imageX, imageY);
            }
            offsetX = size.letterboxX(lastImageWidth);
            offsetY = size.letterboxY(lastImageHeight);
        }

        if (previousX >= 0) {
            pushRegion(offsetX + previousX, offsetY + previousY, CURSOR_WIDTH, CURSOR.length);
        }
        if (imageX >= 0) {
            pushRegion(offsetX + imageX, offsetY + imageY, CURSOR_WIDTH, CURSOR.length);
        }
    }

    public void hideCursor() {
        setCursor(-1, -1);
    }

    /** Puts back whatever the guest had under the pointer's old position. */
    private void restoreArea(int imageX, int imageY) {
        int offsetX = size.letterboxX(lastImageWidth);
        int offsetY = size.letterboxY(lastImageHeight);
        int screenWidth = size.pixelWidth();
        for (int row = 0; row < CURSOR.length; row++) {
            int sourceY = imageY + row;
            int y = offsetY + sourceY;
            if (y < 0 || y >= size.pixelHeight()) {
                continue;
            }
            for (int col = 0; col < CURSOR_WIDTH; col++) {
                int sourceX = imageX + col;
                int x = offsetX + sourceX;
                if (x < 0 || x >= screenWidth) {
                    continue;
                }
                boolean insideImage = sourceX >= 0 && sourceX < lastImageWidth
                        && sourceY >= 0 && sourceY < lastImageHeight;
                framebuffer[y * screenWidth + x] = insideImage
                        ? lastImage[sourceY * lastImageWidth + sourceX] : lastBorder;
            }
        }
    }

    private void drawCursor(int imageX, int imageY) {
        int offsetX = size.letterboxX(lastImageWidth);
        int offsetY = size.letterboxY(lastImageHeight);
        int screenWidth = size.pixelWidth();
        for (int row = 0; row < CURSOR.length; row++) {
            String line = CURSOR[row];
            int y = offsetY + imageY + row;
            if (y < 0 || y >= size.pixelHeight()) {
                continue;
            }
            for (int col = 0; col < line.length(); col++) {
                char pixel = line.charAt(col);
                if (pixel == '.') {
                    continue;
                }
                int x = offsetX + imageX + col;
                if (x < 0 || x >= screenWidth) {
                    continue;
                }
                framebuffer[y * screenWidth + x] = pixel == 'X' ? cursorOutline : cursorFill;
            }
        }
    }

    /** Blits just the panels a rectangle overlaps, and only the overlapping part of each. */
    private void pushRegion(int x, int y, int width, int height) {
        int screenWidth = size.pixelWidth();
        int left = Math.max(0, x);
        int top = Math.max(0, y);
        int right = Math.min(screenWidth, x + width);
        int bottom = Math.min(size.pixelHeight(), y + height);
        if (right <= left || bottom <= top) {
            return;
        }

        for (int row = top / PANEL; row <= (bottom - 1) / PANEL; row++) {
            for (int col = left / PANEL; col <= (right - 1) / PANEL; col++) {
                int panelX = col * PANEL;
                int panelY = row * PANEL;
                int sliceLeft = Math.max(left, panelX);
                int sliceTop = Math.max(top, panelY);
                int sliceRight = Math.min(right, panelX + PANEL);
                int sliceBottom = Math.min(bottom, panelY + PANEL);
                synchronized (framebuffer) {
                    panels.get(row * size.columns() + col).blit(framebuffer, screenWidth,
                            sliceLeft, sliceTop, sliceLeft - panelX, sliceTop - panelY,
                            sliceRight - sliceLeft, sliceBottom - sliceTop);
                }
            }
        }
    }

    /**
     * Copies a quantized guest image onto the screen, centred with a letterbox border.
     *
     * @param image  palette indices, {@code imageWidth * imageHeight}
     * @param border colour for the area around the image
     */
    public void present(byte[] image, int imageWidth, int imageHeight, byte border) {
        synchronized (framebuffer) {
            int needed = imageWidth * imageHeight;
            if (lastImage.length < needed) {
                lastImage = new byte[needed];
            }
            System.arraycopy(image, 0, lastImage, 0, needed);
            lastImageWidth = imageWidth;
            lastImageHeight = imageHeight;
            lastBorder = border;
            composite();
        }
        pushAll();
    }

    /** Rebuilds the framebuffer from the last guest image plus the pointer on top. */
    private void composite() {
        int offsetX = size.letterboxX(lastImageWidth);
        int offsetY = size.letterboxY(lastImageHeight);
        int screenWidth = size.pixelWidth();

        java.util.Arrays.fill(framebuffer, lastBorder);
        int copyWidth = Math.min(lastImageWidth, screenWidth - offsetX);
        int copyHeight = Math.min(lastImageHeight, size.pixelHeight() - offsetY);
        for (int row = 0; row < copyHeight; row++) {
            System.arraycopy(lastImage, row * lastImageWidth,
                    framebuffer, (offsetY + row) * screenWidth + offsetX, copyWidth);
        }

        if (cursorX < 0 || cursorY < 0) {
            return;
        }
        for (int row = 0; row < CURSOR.length; row++) {
            String line = CURSOR[row];
            int y = offsetY + cursorY + row;
            if (y < 0 || y >= size.pixelHeight()) {
                continue;
            }
            for (int col = 0; col < line.length(); col++) {
                char pixel = line.charAt(col);
                if (pixel == '.') {
                    continue;
                }
                int x = offsetX + cursorX + col;
                if (x < 0 || x >= screenWidth) {
                    continue;
                }
                framebuffer[y * screenWidth + x] = pixel == 'X' ? cursorOutline : cursorFill;
            }
        }
    }

    /**
     * Hands each panel its slice of the framebuffer.
     *
     * <p>No damage tracking here on purpose: {@code CraftMapCanvas.setPixel} already compares
     * against the previous value and only marks genuinely changed pixels dirty, and the server
     * transmits just the bounding box of those. Unchanged panels therefore cost a memory copy and
     * no network traffic.
     */
    private void pushAll() {
        int screenWidth = size.pixelWidth();
        int index = 0;
        for (int row = 0; row < size.rows(); row++) {
            for (int col = 0; col < size.columns(); col++) {
                panels.get(index++).blit(framebuffer, screenWidth,
                        col * PANEL, row * PANEL, 0, 0, PANEL, PANEL);
            }
        }
    }

    /**
     * The screen's plane in world space, for aiming the pointer.
     *
     * <p>The surface is the block <em>face</em> pointing at the viewer, not the block's origin
     * corner, so which corner to start from depends on the facing. Along an axis whose direction is
     * negative the relevant edge is one block further along, hence the +1 terms. The screen's right
     * and forward axes are always perpendicular, so each of the two terms touches a different axis.
     */
    public ScreenGeometry geometry() {
        ScreenGeometry cached = geometry;
        if (cached == null) {
            int[] block = computer.blockAt(computer.layout().screenBottomLeft());
            int forwardX = computer.facing().getModX();
            int forwardZ = computer.facing().getModZ();
            int rightX = -forwardZ;
            int rightZ = forwardX;

            double originX = block[0] + (rightX < 0 ? 1 : 0) + (forwardX < 0 ? 1 : 0);
            double originZ = block[2] + (rightZ < 0 ? 1 : 0) + (forwardZ < 0 ? 1 : 0);

            // wallMounted takes the direction from the screen towards the viewer.
            cached = ScreenGeometry.wallMounted(size,
                    new double[]{originX, block[1], originZ}, -forwardX, -forwardZ);
            cached.setGuestResolution(guestWidth, guestHeight);
            geometry = cached;
        }
        return cached;
    }

    public int guestWidth() {
        return guestWidth;
    }

    public int guestHeight() {
        return guestHeight;
    }
}
