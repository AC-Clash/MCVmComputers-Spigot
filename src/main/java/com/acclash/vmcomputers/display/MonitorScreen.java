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

    private final Computer computer;
    private final MonitorSize size;
    private final List<PanelRenderer> panels;
    private final List<Integer> mapIds;

    private final byte[] framebuffer;
    private int guestWidth;
    private int guestHeight;
    private volatile ScreenGeometry geometry;

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

    /** Guest resolution currently being displayed; changes when the guest switches video mode. */
    public void setGuestResolution(int width, int height) {
        this.guestWidth = Math.min(width, size.pixelWidth());
        this.guestHeight = Math.min(height, size.pixelHeight());
        ScreenGeometry existing = geometry;
        if (existing != null) {
            // Letterbox borders move when the guest changes mode, so the pointer mapping must too.
            existing.setGuestResolution(this.guestWidth, this.guestHeight);
        }
    }

    /** Paints the whole screen one colour. Black is the powered-off state. */
    public void fill(byte colour) {
        java.util.Arrays.fill(framebuffer, colour);
        pushAll();
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

        java.util.Arrays.fill(framebuffer, border);
        int copyWidth = Math.min(imageWidth, screenWidth - offsetX);
        int copyHeight = Math.min(imageHeight, size.pixelHeight() - offsetY);
        for (int row = 0; row < copyHeight; row++) {
            System.arraycopy(image, row * imageWidth,
                    framebuffer, (offsetY + row) * screenWidth + offsetX, copyWidth);
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
