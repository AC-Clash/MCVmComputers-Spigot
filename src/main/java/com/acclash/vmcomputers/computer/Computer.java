package com.acclash.vmcomputers.computer;

import com.acclash.vmcomputers.display.MonitorSize;
import com.acclash.vmcomputers.emu.VmSpec;

import java.util.UUID;
import com.acclash.vmcomputers.parts.ComponentSlot;
import com.acclash.vmcomputers.parts.ComponentType;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    private volatile String diskImage;
    private volatile VmSpec.Architecture architecture = VmSpec.Architecture.X86_64;
    /**
     * Who built it, or null for a machine from before computers had owners.
     *
     * <p>Set after construction rather than passed in, for the same reason the ISO and the
     * architecture are: a computer is created from a layout long before anyone knows what will be
     * stored against it, and threading a nullable through every constructor call to say "nobody
     * yet" is worse than a setter.
     */
    private volatile UUID owner;

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

    // ---- installed components -------------------------------------------

    /**
     * What is fitted in each bay. Held in memory and mirrored to {@code computer_components};
     * concurrent because the case menu writes on the server thread while boot checks read from
     * the async task that starts QEMU.
     */
    private final Map<ComponentSlot, ComponentType> installed =
            new ConcurrentHashMap<ComponentSlot, ComponentType>();

    public ComponentType installedIn(ComponentSlot slot) {
        return installed.get(slot);
    }

    /** Fits a component, or empties the bay when {@code type} is null. */
    public void install(ComponentSlot slot, ComponentType type) {
        if (type == null) {
            installed.remove(slot);
        } else {
            installed.put(slot, type);
        }
    }

    public Map<ComponentSlot, ComponentType> installedComponents() {
        return Collections.unmodifiableMap(new EnumMap<ComponentSlot, ComponentType>(installed));
    }

    /**
     * Bays that must be filled before the machine will start, and are not.
     *
     * <p>Empty means it is ready to boot.
     */
    public List<ComponentSlot> missingComponents() {
        List<ComponentSlot> missing = new ArrayList<ComponentSlot>();
        for (ComponentSlot slot : ComponentSlot.values()) {
            if (slot.required() && !installed.containsKey(slot)) {
                missing.add(slot);
            }
        }
        return missing;
    }

    public boolean isAssembled() {
        return missingComponents().isEmpty();
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

    /**
     * Guest CPU architecture. Must match the installed operating system and the ISO -- an x86 image
     * will not boot on an ARM machine.
     */
    public VmSpec.Architecture architecture() {
        return architecture;
    }

    public void setArchitecture(VmSpec.Architecture architecture) {
        this.architecture = architecture;
    }

    /** File name of the ISO in this computer's drive, relative to the isos folder, or null. */
    public String isoName() {
        return isoName;
    }

    public void setIsoName(String isoName) {
        this.isoName = isoName;
    }

    /**
     * File name of an admin-supplied disk image this computer boots from, or null for its own.
     *
     * <p>Set, this replaces the machine's automatic disk entirely rather than adding a second one.
     * The alternative -- both attached -- means the guest sees two drives and boots whichever the
     * firmware prefers, which is not a thing anyone asked for.
     */
    public String diskImage() {
        return diskImage;
    }

    public void setDiskImage(String diskImage) {
        this.diskImage = diskImage;
    }

    /** Who may take this apart. Null on machines built before ownership was recorded. */
    public UUID owner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    /**
     * True if this player may reconfigure or dismantle the machine.
     *
     * <p>An unowned computer is fair game -- those predate ownership and refusing everyone would
     * strand them permanently. Using someone's computer is a separate question and deliberately
     * not this method's: you can sit at a machine you cannot take apart.
     */
    public boolean mayModify(UUID player) {
        return owner == null || owner.equals(player);
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
