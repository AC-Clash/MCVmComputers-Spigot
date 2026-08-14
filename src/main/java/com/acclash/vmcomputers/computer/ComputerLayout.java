package com.acclash.vmcomputers.computer;

import com.acclash.vmcomputers.display.MonitorSize;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Where every part of a computer sits, relative to its anchor.
 *
 * <p>The single source of truth for the shape of a computer. Placement, removal and the click
 * index all derive from this, so they cannot disagree about which blocks belong to a machine.
 *
 * <p>Because the whole layout is a function of {@link MonitorSize}, a computer only needs its
 * anchor, facing and size persisted -- never a list of component positions. That is what lets a
 * 6x4 projector with 24 screen panels be stored in the same four columns as a 2x2 desktop.
 *
 * <p>Offsets are in the anchor's own frame: {@code forward} away from the player who built it,
 * {@code right} to their right, {@code up} vertically. Rotating into world coordinates happens at
 * the call site, which keeps this class free of Bukkit and therefore testable.
 */
public final class ComputerLayout {

    /** A position relative to the anchor block. */
    public static final class Offset {
        public final int right;
        public final int up;
        public final int forward;

        public Offset(int right, int up, int forward) {
            this.right = right;
            this.up = up;
            this.forward = forward;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Offset)) {
                return false;
            }
            Offset other = (Offset) o;
            return right == other.right && up == other.up && forward == other.forward;
        }

        @Override
        public int hashCode() {
            return (right * 31 + up) * 31 + forward;
        }

        @Override
        public String toString() {
            return "(r" + right + ",u" + up + ",f" + forward + ")";
        }
    }

    private final MonitorSize size;
    private final List<Offset> screenPanels;
    private final List<Offset> backingBlocks;
    private final List<Offset> deskBlocks;
    private final Offset screenBottomLeft;
    private final Offset chair;
    private final Offset tower;
    private final Offset keyboard;
    private final Offset mouse;
    private final Offset control;
    private final List<Offset> occupied;
    private final int screenDepth;

    private ComputerLayout(MonitorSize size, List<Offset> screenPanels, List<Offset> backingBlocks,
                           List<Offset> deskBlocks, Offset screenBottomLeft, Offset chair,
                           Offset tower, Offset keyboard, Offset mouse, Offset control,
                           int screenDepth) {
        this.size = size;
        this.screenPanels = Collections.unmodifiableList(screenPanels);
        this.backingBlocks = Collections.unmodifiableList(backingBlocks);
        this.deskBlocks = Collections.unmodifiableList(deskBlocks);
        this.screenBottomLeft = screenBottomLeft;
        this.chair = chair;
        this.tower = tower;
        this.keyboard = keyboard;
        this.mouse = mouse;
        this.control = control;
        this.screenDepth = screenDepth;

        List<Offset> all = new ArrayList<Offset>();
        all.addAll(screenPanels);
        all.addAll(backingBlocks);
        all.addAll(deskBlocks);
        for (Offset o : new Offset[]{chair, tower, keyboard, mouse, control}) {
            if (o != null) {
                all.add(o);
            }
        }
        this.occupied = Collections.unmodifiableList(all);
    }

    /** Builds the layout for a size. Cheap enough to call freely; nothing is cached. */
    public static ComputerLayout of(MonitorSize size) {
        return size.form() == MonitorSize.Form.PROJECTOR ? projector(size) : desk(size);
    }

    /**
     * Desk form: the player sits at the anchor, a desk runs forward from them, and the screen
     * stands at the far edge of it.
     *
     * <p>Desk depth comes from the size's viewing distance, so a bigger screen automatically gets
     * a deeper desk and stays within a comfortable head sweep.
     */
    private static ComputerLayout desk(MonitorSize size) {
        int cols = size.columns();
        int rows = size.rows();
        int depth = Math.max(2, (int) Math.round(size.viewingDistance()));
        int startRight = -(cols / 2);

        List<Offset> panels = new ArrayList<Offset>();
        List<Offset> backing = new ArrayList<Offset>();
        // Row-major from the top-left, matching how map tiles are indexed.
        for (int row = 0; row < rows; row++) {
            int up = rows - row;
            for (int col = 0; col < cols; col++) {
                panels.add(new Offset(startRight + col, up, depth));
                backing.add(new Offset(startRight + col, up, depth + 1));
            }
        }

        // Desk surface between the player and the screen. Its top face is one block up, which is
        // where the keyboard and mouse sit.
        List<Offset> desk = new ArrayList<Offset>();
        for (int f = 1; f < depth; f++) {
            for (int col = 0; col < cols; col++) {
                desk.add(new Offset(startRight + col, 0, f));
            }
        }

        // Keyboard and mouse rest on the desk surface, so both must sit within its width. Column 0
        // is always covered, but the column to its right is not on a two-wide desk -- putting the
        // mouse there leaves it floating with nothing beneath it.
        int maxRight = startRight + cols - 1;
        int mouseRight = 1 <= maxRight ? 1 : startRight;

        return new ComputerLayout(size, panels, backing, desk,
                new Offset(startRight, 1, depth),
                new Offset(0, 0, 0),
                new Offset(startRight - 1, 0, 1),
                new Offset(0, 1, 1),
                new Offset(mouseRight, 1, 1),
                null,
                depth);
    }

    /**
     * Projector form: just a wall-mounted screen and a control block beneath it. No seating, so
     * players choose their own distance -- which matters, because walking closer makes the pointer
     * finer.
     */
    private static ComputerLayout projector(MonitorSize size) {
        int cols = size.columns();
        int rows = size.rows();
        int startRight = -(cols / 2);

        List<Offset> panels = new ArrayList<Offset>();
        List<Offset> backing = new ArrayList<Offset>();
        for (int row = 0; row < rows; row++) {
            int up = rows - row;
            for (int col = 0; col < cols; col++) {
                panels.add(new Offset(startRight + col, up, 1));
                backing.add(new Offset(startRight + col, up, 2));
            }
        }

        return new ComputerLayout(size, panels, backing, new ArrayList<Offset>(),
                new Offset(startRight, 1, 1),
                null,
                null,
                null,
                null,
                new Offset(0, 0, 1),
                1);
    }

    public MonitorSize size() {
        return size;
    }

    /** Screen panels in row-major order from the top-left, one per map. */
    public List<Offset> screenPanels() {
        return screenPanels;
    }

    /** Solid blocks placed behind the screen so the item frames have something to attach to. */
    public List<Offset> backingBlocks() {
        return backingBlocks;
    }

    public List<Offset> deskBlocks() {
        return deskBlocks;
    }

    /** Bottom-left corner of the screen surface, the origin for pointer ray casting. */
    public Offset screenBottomLeft() {
        return screenBottomLeft;
    }

    /** Null on a projector. */
    public Offset chair() {
        return chair;
    }

    /** Null on a projector. */
    public Offset tower() {
        return tower;
    }

    /** Null on a projector. */
    public Offset keyboard() {
        return keyboard;
    }

    /** Null on a projector. */
    public Offset mouse() {
        return mouse;
    }

    /** Only present on a projector. */
    public Offset control() {
        return control;
    }

    /** How far forward the screen sits from the anchor, in blocks. */
    public int screenDepth() {
        return screenDepth;
    }

    /** Every block this computer occupies; what the click index is built from. */
    public List<Offset> occupiedBlocks() {
        return occupied;
    }

    /**
     * Rotates a layout offset into a world-space delta.
     *
     * <p>Facing north, the player's right hand points east, and that relationship rotates with
     * them; {@code right = (-forwardZ, forwardX)} captures it for all four cardinal directions.
     *
     * <p>Lives here rather than on {@link Computer} so it stays free of Bukkit and can be tested
     * directly -- getting the handedness backwards would mirror every computer in the world, which
     * is exactly the kind of bug that is invisible until someone builds one facing south.
     *
     * @param forwardX x component of the facing direction, one of -1, 0, 1
     * @param forwardZ z component of the facing direction, one of -1, 0, 1
     * @return {@code {dx, dy, dz}} relative to the anchor
     */
    public static int[] rotate(Offset offset, int forwardX, int forwardZ) {
        int rightX = -forwardZ;
        int rightZ = forwardX;
        return new int[]{
                offset.forward * forwardX + offset.right * rightX,
                offset.up,
                offset.forward * forwardZ + offset.right * rightZ
        };
    }
}
