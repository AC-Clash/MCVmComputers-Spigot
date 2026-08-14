package com.acclash.vmcomputers.computer;

import com.acclash.vmcomputers.display.MonitorSize;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.List;

/**
 * One computer placed in the world.
 *
 * <p>Only the anchor, facing and monitor size are stored. Every component position is derived from
 * {@link ComputerLayout}, so a 24-panel projector persists in exactly the same four columns as a
 * 4-panel desktop, and adding a new component never needs a schema change.
 */
public final class Computer {

    /** Lifecycle of the machine behind this computer. */
    public enum State {
        OFF,
        BOOTING,
        RUNNING,
        ERROR
    }

    private final int id;
    private final String worldName;
    private final int x;
    private final int y;
    private final int z;
    private final BlockFace facing;
    private final MonitorSize monitorSize;
    private final String type;
    private volatile State state;
    private volatile String isoName;

    private final ComputerLayout layout;

    public Computer(int id, String worldName, int x, int y, int z, BlockFace facing,
                    MonitorSize monitorSize, String type, State state) {
        if (!isCardinal(facing)) {
            throw new IllegalArgumentException("facing must be cardinal, got " + facing);
        }
        this.id = id;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.facing = facing;
        this.monitorSize = monitorSize;
        this.type = type;
        this.state = state;
        this.layout = ComputerLayout.of(monitorSize);
    }

    public static boolean isCardinal(BlockFace face) {
        return face == BlockFace.NORTH || face == BlockFace.EAST
                || face == BlockFace.SOUTH || face == BlockFace.WEST;
    }

    public int id() {
        return id;
    }

    public String worldName() {
        return worldName;
    }

    public int anchorX() {
        return x;
    }

    public int anchorY() {
        return y;
    }

    public int anchorZ() {
        return z;
    }

    /** The direction the player was facing when they built it, i.e. towards the screen. */
    public BlockFace facing() {
        return facing;
    }

    public MonitorSize monitorSize() {
        return monitorSize;
    }

    public String type() {
        return type;
    }

    public State state() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    /** File name of the ISO in this computer's drive, relative to the isos folder, or null. */
    public String isoName() {
        return isoName;
    }

    public void setIsoName(String isoName) {
        this.isoName = isoName;
    }

    public ComputerLayout layout() {
        return layout;
    }

    /** World block coordinates of a layout offset. */
    public int[] blockAt(ComputerLayout.Offset offset) {
        int[] delta = ComputerLayout.rotate(offset, facing.getModX(), facing.getModZ());
        return new int[]{x + delta[0], y + delta[1], z + delta[2]};
    }

    public Location locationOf(World world, ComputerLayout.Offset offset) {
        int[] p = blockAt(offset);
        return new Location(world, p[0], p[1], p[2]);
    }

    public Location anchorLocation(World world) {
        return new Location(world, x, y, z);
    }

    /** Every block this computer occupies, in world coordinates. Used to build the click index. */
    public List<int[]> occupiedBlocks() {
        List<ComputerLayout.Offset> offsets = layout.occupiedBlocks();
        List<int[]> out = new ArrayList<int[]>(offsets.size());
        for (ComputerLayout.Offset offset : offsets) {
            out.add(blockAt(offset));
        }
        return out;
    }

    /** Screen panel positions in world coordinates, row-major from the top-left. */
    public List<int[]> screenPanelBlocks() {
        List<ComputerLayout.Offset> offsets = layout.screenPanels();
        List<int[]> out = new ArrayList<int[]>(offsets.size());
        for (ComputerLayout.Offset offset : offsets) {
            out.add(blockAt(offset));
        }
        return out;
    }

    @Override
    public String toString() {
        return "Computer#" + id + "{" + type + ", " + monitorSize + ", " + worldName
                + " " + x + "," + y + "," + z + " facing " + facing + ", " + state + "}";
    }
}
