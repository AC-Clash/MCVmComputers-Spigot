package com.acclash.vmcomputers.display;

/**
 * Maps a player's line of sight onto a pixel on the monitor.
 *
 * <p>This is the whole of "approach 2" pointer control: cast a ray from the player's eyes along
 * their look direction, intersect it with the plane the maps live on, and convert the hit point to
 * a pixel. Nothing is ever cancelled or corrected, so there is no rubber-banding to fight -- the
 * player looks wherever they like and the pointer simply follows.
 *
 * <p>Distance falls out for free: standing closer increases the screen's angular size, so a degree
 * of head movement crosses fewer pixels and the pointer becomes finer. Walking up to read small
 * text also makes it easier to click on.
 *
 * <p>Deliberately contains no Bukkit types so the arithmetic can be tested without a server.
 * Coordinates follow Minecraft's conventions: +Y is up, and the screen's own axes run right and up
 * from its bottom-left corner as seen by a viewer facing it.
 */
public final class ScreenGeometry {

    /** Where a look ray landed. */
    public static final class Hit {
        /** Pixel within the full map grid, origin top-left. */
        public final int gridX;
        public final int gridY;
        /** Pixel within the guest framebuffer, origin top-left; only meaningful if inside. */
        public final int imageX;
        public final int imageY;
        /** False when the ray landed in the letterbox border rather than on the guest image. */
        public final boolean onImage;
        /** Distance in blocks from the eye to the screen. */
        public final double distance;

        Hit(int gridX, int gridY, int imageX, int imageY, boolean onImage, double distance) {
            this.gridX = gridX;
            this.gridY = gridY;
            this.imageX = imageX;
            this.imageY = imageY;
            this.onImage = onImage;
            this.distance = distance;
        }

        @Override
        public String toString() {
            return "Hit{grid=" + gridX + "," + gridY + " image=" + imageX + "," + imageY
                    + " onImage=" + onImage + " distance=" + String.format("%.2f", distance) + "}";
        }
    }

    private static final double EPSILON = 1e-9;

    private final MonitorSize size;
    private final double originX;
    private final double originY;
    private final double originZ;
    // Unit vectors along the screen surface, and the screen's width/height in blocks.
    private final double rightX;
    private final double rightY;
    private final double rightZ;
    private final double upX;
    private final double upY;
    private final double upZ;
    private final double widthBlocks;
    private final double heightBlocks;
    // Plane normal, pointing out towards the viewer.
    private final double normalX;
    private final double normalY;
    private final double normalZ;

    private int guestWidth;
    private int guestHeight;

    /**
     * @param origin      world position of the screen's bottom-left corner, as the viewer sees it
     * @param right       unit vector along the screen's width, to the viewer's right
     * @param up          unit vector along the screen's height
     * @param widthBlocks screen width in blocks (one map per block)
     */
    public ScreenGeometry(MonitorSize size, double[] origin, double[] right, double[] up,
                          double widthBlocks, double heightBlocks) {
        this.size = size;
        this.originX = origin[0];
        this.originY = origin[1];
        this.originZ = origin[2];
        this.rightX = right[0];
        this.rightY = right[1];
        this.rightZ = right[2];
        this.upX = up[0];
        this.upY = up[1];
        this.upZ = up[2];
        this.widthBlocks = widthBlocks;
        this.heightBlocks = heightBlocks;

        // normal = right x up, which points back towards a viewer facing the screen.
        this.normalX = rightY * upZ - rightZ * upY;
        this.normalY = rightZ * upX - rightX * upZ;
        this.normalZ = rightX * upY - rightY * upX;

        this.guestWidth = size.guestWidth();
        this.guestHeight = size.guestHeight();
    }

    /**
     * Updates the guest framebuffer size, which changes where the letterbox borders fall. Call this
     * whenever the guest switches video mode -- BIOS text mode and a desktop are different shapes.
     */
    public void setGuestResolution(int width, int height) {
        this.guestWidth = width;
        this.guestHeight = height;
    }

    public MonitorSize size() {
        return size;
    }

    public int guestWidth() {
        return guestWidth;
    }

    public int guestHeight() {
        return guestHeight;
    }

    /**
     * Intersects a look ray with the screen.
     *
     * @param eye       the player's eye position
     * @param direction look direction; need not be normalised
     * @return where the ray landed, or {@code null} if it missed the screen entirely
     */
    public Hit trace(double[] eye, double[] direction) {
        return trace(eye[0], eye[1], eye[2], direction[0], direction[1], direction[2]);
    }

    /**
     * Primitive form of {@link #trace(double[], double[])}.
     *
     * <p>Worth having on its own because this runs on every movement packet of every player near a
     * live screen, and packing the arguments into two throwaway arrays to unpack them again is pure
     * garbage at that rate.
     */
    public Hit trace(double eyeX, double eyeY, double eyeZ,
                     double dirX, double dirY, double dirZ) {
        double dirDotNormal = dirX * normalX + dirY * normalY + dirZ * normalZ;
        if (Math.abs(dirDotNormal) < EPSILON) {
            // Looking exactly along the screen surface.
            return null;
        }

        double toOriginX = originX - eyeX;
        double toOriginY = originY - eyeY;
        double toOriginZ = originZ - eyeZ;
        double t = (toOriginX * normalX + toOriginY * normalY + toOriginZ * normalZ) / dirDotNormal;
        if (t <= 0) {
            // The screen is behind the player.
            return null;
        }

        double hitX = eyeX + dirX * t;
        double hitY = eyeY + dirY * t;
        double hitZ = eyeZ + dirZ * t;

        double localX = hitX - originX;
        double localY = hitY - originY;
        double localZ = hitZ - originZ;

        double alongRight = localX * rightX + localY * rightY + localZ * rightZ;
        double alongUp = localX * upX + localY * upY + localZ * upZ;

        double u = alongRight / widthBlocks;
        double v = alongUp / heightBlocks;
        if (u < 0 || u >= 1 || v < 0 || v >= 1) {
            return null;
        }

        // Pixel rows count downwards from the top, so v is flipped.
        int gridX = clamp((int) (u * size.pixelWidth()), 0, size.pixelWidth() - 1);
        int gridY = clamp((int) ((1.0 - v) * size.pixelHeight()), 0, size.pixelHeight() - 1);

        int offsetX = size.letterboxX(guestWidth);
        int offsetY = size.letterboxY(guestHeight);
        int imageX = gridX - offsetX;
        int imageY = gridY - offsetY;
        boolean onImage = imageX >= 0 && imageX < guestWidth && imageY >= 0 && imageY < guestHeight;

        double length = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        return new Hit(gridX, gridY, imageX, imageY, onImage, t * length);
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    /**
     * How far into the panel block the picture sits, measured along the viewer's line of sight.
     *
     * <p>A screen panel is an item frame hanging in an <em>air</em> block, attached to the wall
     * behind it, so its picture is at the far face of that block rather than the near one. Vanilla
     * puts a frame 0.46875 blocks from the block centre on the side away from the way it faces,
     * which is where this number comes from.
     *
     * <p>Getting this wrong is not a small offset, it is parallax. Intersecting the look ray with a
     * plane a block nearer than the picture makes the pointer fall short of wherever the player is
     * aiming, by an amount that grows with how far off to the side the target is and vanishes when
     * they stand square in front of it. From a LARGE desk that cost about a third of the sweep: the
     * pointer covered roughly 72% of the screen while the head crossed all of it.
     */
    private static final double FRAME_PLANE = 0.96875;

    /**
     * Builds the geometry for a wall of item frames from the direction the viewer looks at them.
     *
     * @param block    block coordinates of the bottom-left screen panel, as the viewer sees it
     * @param forwardX unit vector component pointing from the viewer towards the screen
     */
    public static ScreenGeometry wallMounted(MonitorSize size, int[] block,
                                             int forwardX, int forwardZ) {
        // The viewer's right hand points along (forward x worldUp).
        int rightX = -forwardZ;
        int rightZ = forwardX;

        // Two separate corrections, on two different axes, which is why they can be summed
        // blindly: right and forward are perpendicular, so each touches an axis the other does
        // not. Along an axis running negative, the corner the screen starts from is one block
        // further along -- hence the +1 terms. And along the line of sight, the picture is at the
        // frame rather than at the block's near face.
        double originX = block[0] + (rightX < 0 ? 1 : 0) + (forwardX < 0 ? 1 : 0)
                + forwardX * FRAME_PLANE;
        double originZ = block[2] + (rightZ < 0 ? 1 : 0) + (forwardZ < 0 ? 1 : 0)
                + forwardZ * FRAME_PLANE;

        return new ScreenGeometry(size,
                new double[]{originX, block[1], originZ},
                new double[]{rightX, 0, rightZ},
                new double[]{0, 1, 0},
                size.columns(), size.rows());
    }
}
