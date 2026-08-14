package com.acclash.vmcomputers.display;

/**
 * The in-game monitor sizes a player can choose when building a computer.
 *
 * <p>Size is a real performance decision, not decoration. Every map in the grid is 128x128 bytes
 * that must be pushed to each viewer, so cost scales with the map count: a full frame on
 * {@link #LARGE} is five times the bytes of one on {@link #SMALL}, and the achievable frame rate
 * scales inversely. Offering smaller monitors gives players a genuine trade rather than a cosmetic
 * one.
 *
 * <p>Every size asks the guest for 640x480, since that is the one mode all firmware and operating
 * systems support. Only {@link #XLARGE} can show it untouched; the desk sizes scale it down, and
 * downscaling costs more legibility than the ~250 colour palette does. That is the real reason to
 * build a projector.
 */
public enum MonitorSize {

    /** 2x2 maps. Cheap and fast, but only really legible for large-font or graphical guests. */
    SMALL(2, 2, 640, 480, true, Form.DESK, 2.0),

    /** 3x3 maps. 640x480 scales to 384x288, so text is soft but readable up close. */
    MEDIUM(3, 3, 640, 480, true, Form.DESK, 2.5),

    /** 4x3 maps. 640x480 scales cleanly to 512x384, an exact 0.8 -- the best of the desks. */
    LARGE(4, 3, 640, 480, true, Form.DESK, 3.0),

    /**
     * 6x4 maps: 768x512. The only size that holds both common guest modes at 1:1 -- 640x480
     * graphical and 720x400 VGA text -- with nothing but letterboxing. Text mode matters more than
     * it sounds, because that is what BIOS, bootloaders, installers and DOS all use.
     *
     * <p>Six blocks wide is a wall, not a desk monitor, so this is built as a projector screen with
     * the seat set well back rather than as something you sit at.
     */
    XLARGE(6, 4, 640, 480, false, Form.PROJECTOR, 5.0);

    /** How a monitor of this size is physically built in the world. */
    public enum Form {
        /** Sits on a desk, with a chair, tower, keyboard and mouse around it. */
        DESK,
        /** Wall-mounted screen with seating set back from it. No desk. */
        PROJECTOR
    }

    private static final int MAP_PIXELS = 128;

    /**
     * Horizontal sweep, in degrees, that a viewer at the default distance has to turn their head to
     * cross the whole screen. Kept near this value across sizes so every monitor is comfortable.
     */
    private static final double TARGET_SWEEP_DEGREES = 60.0;

    private final int columns;
    private final int rows;
    private final int guestWidth;
    private final int guestHeight;
    private final boolean requiresScaling;
    private final Form form;
    private final double viewingDistance;

    MonitorSize(int columns, int rows, int guestWidth, int guestHeight, boolean requiresScaling,
                Form form, double viewingDistance) {
        this.columns = columns;
        this.rows = rows;
        this.guestWidth = guestWidth;
        this.guestHeight = guestHeight;
        this.requiresScaling = requiresScaling;
        this.form = form;
        this.viewingDistance = viewingDistance;
    }

    public Form form() {
        return form;
    }

    /**
     * Default distance in blocks between the viewer and the screen.
     *
     * <p>This is only where the seat goes. Players can walk closer, and doing so is genuinely
     * useful: approaching the screen increases its angular size, so each degree of head movement
     * covers fewer pixels and the pointer gets finer. Precision for proximity, for free.
     */
    public double viewingDistance() {
        return viewingDistance;
    }

    /** Horizontal head sweep needed to cross the screen from the default seat, in degrees. */
    public double sweepDegrees(double distanceBlocks) {
        return Math.toDegrees(2.0 * Math.atan((columns / 2.0) / Math.max(0.1, distanceBlocks)));
    }

    /**
     * Pixels the pointer travels per degree of head movement.
     *
     * <p><b>Lower is finer.</b> The screen has a fixed pixel count, so moving closer spreads those
     * pixels across a wider sweep and each degree of head movement covers fewer of them. That is
     * why walking up to the screen makes the pointer easier to place, not harder.
     */
    public double pixelsPerDegree(double distanceBlocks) {
        return pixelWidth() / sweepDegrees(distanceBlocks);
    }

    /** The sweep every size is tuned towards. */
    public static double targetSweepDegrees() {
        return TARGET_SWEEP_DEGREES;
    }

    public int columns() {
        return columns;
    }

    public int rows() {
        return rows;
    }

    /** Number of maps, and therefore the dominant term in bandwidth cost. */
    public int mapCount() {
        return columns * rows;
    }

    public int pixelWidth() {
        return columns * MAP_PIXELS;
    }

    public int pixelHeight() {
        return rows * MAP_PIXELS;
    }

    /**
     * Resolution to request from the guest via EDID.
     *
     * <p>Always 640x480, whatever the monitor size. It is the one mode every firmware and operating
     * system supports, and asking for anything unusual simply fails: ARM UEFI honours the request
     * literally, so a request for 320x240 left the guest with no usable mode at all. Smaller
     * monitors scale the result down instead.
     */
    public int guestWidth() {
        return guestWidth;
    }

    public int guestHeight() {
        return guestHeight;
    }

    /** True when the guest image cannot be shown 1:1 and must be downscaled. */
    public boolean requiresScaling() {
        return requiresScaling;
    }

    /** Bytes in a full-screen update, before protocol compression. */
    public int fullFrameBytes() {
        return mapCount() * MAP_PIXELS * MAP_PIXELS;
    }

    /** Horizontal offset that centres a guest image of {@code width} within the grid. */
    public int letterboxX(int width) {
        return Math.max(0, (pixelWidth() - width) / 2);
    }

    /** Vertical offset that centres a guest image of {@code height} within the grid. */
    public int letterboxY(int height) {
        return Math.max(0, (pixelHeight() - height) / 2);
    }

    /** True when an image of this size fits without scaling. */
    public boolean fits(int width, int height) {
        return width <= pixelWidth() && height <= pixelHeight();
    }

    public String describe() {
        return name() + " (" + columns + "x" + rows + " maps, " + pixelWidth() + "x" + pixelHeight()
                + ", " + mapCount() + " maps/frame)";
    }
}
