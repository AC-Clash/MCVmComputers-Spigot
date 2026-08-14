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

    /**
     * Debug pointer, in screen pixels. Off unless {@code /vmcomputers debug} turns it on.
     *
     * <p>Not how the pointer is meant to be seen -- drawing it into the framebuffer dirties map
     * panels on every head movement, and the arrow arrives a map packet behind the crosshair it is
     * chasing, so it visibly lags. That is exactly why it is useful for testing: it shows where the
     * plugin thinks the guest's pointer is, which is otherwise invisible when the guest draws its
     * cursor on a hardware plane.
     *
     * <p>A chunky high-contrast arrow also survives quantization to 244 colours far better than a
     * real 12x19 system cursor would.
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
    private static final int CURSOR_WIDTH = 10;
    private static final int CURSOR_HEIGHT = CURSOR.length;

    private final Computer computer;
    private final MonitorSize size;
    private final List<PanelRenderer> panels;
    private final List<Integer> mapIds;

    private final byte[] framebuffer;
    private int guestWidth;
    private int guestHeight;
    private volatile ScreenGeometry geometry;

    // Size actually drawn, which the pointer conversion needs in order to map a displayed pixel
    // back to the guest pixel underneath it.
    private int lastImageWidth;
    private int lastImageHeight;

    /**
     * The pixels the debug cursor is covering, so they can be put back when it moves.
     *
     * <p>Saving the patch is what lets the fast path stay fast. The earlier drawn cursor kept a
     * whole second copy of the guest image and recomposited the screen to move the arrow; a 10x16
     * patch of the framebuffer holds exactly the same information for this purpose and costs
     * nothing when the cursor is off.
     */
    private final byte[] cursorBackup = new byte[CURSOR_WIDTH * CURSOR_HEIGHT];
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
            lastImageHeight = 0;
            // Whatever the cursor was covering is gone, so its backup is meaningless now.
            cursorX = -1;
            cursorY = -1;
        }
        pushAll();
    }

    /** Colours for the debug cursor, from the palette the frames are quantized against. */
    public void setCursorColours(byte fill, byte outline) {
        this.cursorFill = fill;
        this.cursorOutline = outline;
    }

    /**
     * Moves the debug cursor, or hides it when given a negative coordinate.
     *
     * <p>Coordinates are screen pixels -- the framebuffer's own space, which is what the look ray
     * already reports as {@code Hit.gridX/gridY}, so no letterbox arithmetic is needed here.
     *
     * <p>Only the two small rectangles the cursor left and arrived at are repainted. A full repaint
     * would mark every panel dirty, and since a dirty panel is re-rendered whole on the next tick,
     * a 24-panel projector would spend some 393,000 setPixel calls per tick to move a 10x16 arrow.
     */
    public void setCursor(int screenX, int screenY) {
        synchronized (framebuffer) {
            if (cursorX == screenX && cursorY == screenY) {
                return;
            }
            int previousX = cursorX;
            int previousY = cursorY;
            if (previousX >= 0) {
                restoreCursorArea(previousX, previousY);
            }
            cursorX = screenX;
            cursorY = screenY;
            if (screenX >= 0) {
                saveCursorArea(screenX, screenY);
                drawCursor(screenX, screenY);
            }
            if (previousX >= 0) {
                pushCursorArea(previousX, previousY);
            }
            if (screenX >= 0) {
                pushCursorArea(screenX, screenY);
            }
        }
    }

    public void hideCursor() {
        setCursor(-1, -1);
    }

    private void saveCursorArea(int x, int y) {
        int screenWidth = size.pixelWidth();
        int screenHeight = size.pixelHeight();
        for (int row = 0; row < CURSOR_HEIGHT; row++) {
            int py = y + row;
            for (int col = 0; col < CURSOR_WIDTH; col++) {
                int px = x + col;
                boolean inside = px >= 0 && px < screenWidth && py >= 0 && py < screenHeight;
                cursorBackup[row * CURSOR_WIDTH + col] =
                        inside ? framebuffer[py * screenWidth + px] : 0;
            }
        }
    }

    private void restoreCursorArea(int x, int y) {
        int screenWidth = size.pixelWidth();
        int screenHeight = size.pixelHeight();
        for (int row = 0; row < CURSOR_HEIGHT; row++) {
            int py = y + row;
            if (py < 0 || py >= screenHeight) {
                continue;
            }
            for (int col = 0; col < CURSOR_WIDTH; col++) {
                int px = x + col;
                if (px < 0 || px >= screenWidth) {
                    continue;
                }
                framebuffer[py * screenWidth + px] = cursorBackup[row * CURSOR_WIDTH + col];
            }
        }
    }

    private void drawCursor(int x, int y) {
        int screenWidth = size.pixelWidth();
        int screenHeight = size.pixelHeight();
        for (int row = 0; row < CURSOR_HEIGHT; row++) {
            String line = CURSOR[row];
            int py = y + row;
            if (py < 0 || py >= screenHeight) {
                continue;
            }
            for (int col = 0; col < line.length(); col++) {
                char pixel = line.charAt(col);
                if (pixel == '.') {
                    continue;
                }
                int px = x + col;
                if (px < 0 || px >= screenWidth) {
                    continue;
                }
                framebuffer[py * screenWidth + px] = pixel == 'X' ? cursorOutline : cursorFill;
            }
        }
    }

    /** Blits just the panels the cursor rectangle overlaps, and only the overlapping part of each. */
    private void pushCursorArea(int x, int y) {
        int screenWidth = size.pixelWidth();
        int left = Math.max(0, x);
        int top = Math.max(0, y);
        int right = Math.min(screenWidth, x + CURSOR_WIDTH);
        int bottom = Math.min(size.pixelHeight(), y + CURSOR_HEIGHT);
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
                panels.get(row * size.columns() + col).blit(framebuffer, screenWidth,
                        sliceLeft, sliceTop, sliceLeft - panelX, sliceTop - panelY,
                        sliceRight - sliceLeft, sliceBottom - sliceTop);
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
        int offsetX = size.letterboxX(imageWidth);
        int offsetY = size.letterboxY(imageHeight);
        int screenWidth = size.pixelWidth();

        synchronized (framebuffer) {
            lastImageWidth = imageWidth;
            lastImageHeight = imageHeight;

            java.util.Arrays.fill(framebuffer, border);
            int copyWidth = Math.min(imageWidth, screenWidth - offsetX);
            int copyHeight = Math.min(imageHeight, size.pixelHeight() - offsetY);
            for (int row = 0; row < copyHeight; row++) {
                System.arraycopy(image, row * imageWidth,
                        framebuffer, (offsetY + row) * screenWidth + offsetX, copyWidth);
            }

            // The new frame has just overwritten whatever the cursor was covering, so the backup
            // has to be taken again from the fresh pixels before the arrow goes back on top.
            if (cursorX >= 0) {
                saveCursorArea(cursorX, cursorY);
                drawCursor(cursorX, cursorY);
            }
        }
        pushAll();
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
        synchronized (framebuffer) {
            for (int row = 0; row < size.rows(); row++) {
                for (int col = 0; col < size.columns(); col++) {
                    panels.get(index++).blit(framebuffer, screenWidth,
                            col * PANEL, row * PANEL, 0, 0, PANEL, PANEL);
                }
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
