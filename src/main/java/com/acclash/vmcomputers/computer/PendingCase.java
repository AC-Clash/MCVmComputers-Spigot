package com.acclash.vmcomputers.computer;

import com.acclash.vmcomputers.display.MonitorSize;
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
 * A PC case standing in the world that is not a computer yet.
 *
 * <p>Deliberately its own type rather than a {@link Computer} with the screen switched off. A
 * computer is defined by {@code (anchor, facing, monitorSize)} and everything -- the registry
 * index, the click lookup, the screen geometry, removal -- derives from that triple. A case has no
 * monitor size at all, so it has no layout, no footprint and no anchor; giving {@code Computer} a
 * state where its own defining fields are meaningless would put a null check in every one of those
 * paths for the sake of one screen.
 *
 * <p>What it has instead is a position and a bag of fitted parts. When the required bays are full
 * it turns into a real computer, and the anchor is worked out then -- from the case's position and
 * the monitor that was chosen, so the tower ends up exactly where the player put the case.
 */
public final class PendingCase {

    /**
     * Owner id for a placed case's display entities.
     *
     * <p>Negative so it can never collide with a computer id, which comes from a database sequence
     * and starts at one. {@code -1} is the model preview and {@code -2} is a delivery, so cases
     * take {@code -3}.
     */
    public static final int DISPLAY_OWNER = -3;

    private final int id;
    private final String worldName;
    private final int x;
    private final int y;
    private final int z;
    private final BlockFace facing;
    private final ComponentType caseType;

    private final Map<ComponentSlot, ComponentType> installed =
            new ConcurrentHashMap<ComponentSlot, ComponentType>();

    public PendingCase(int id, String worldName, int x, int y, int z, BlockFace facing) {
        this(id, worldName, x, y, z, facing, ComponentType.PC_CASE);
    }

    public PendingCase(int id, String worldName, int x, int y, int z, BlockFace facing,
                       ComponentType caseType) {
        if (!Computer.isCardinal(facing)) {
            throw new IllegalArgumentException("facing must be cardinal, got " + facing);
        }
        this.id = id;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.facing = facing;
        // Rows written before cases had a type read back as the plain one, which is what they were.
        this.caseType = caseType != null ? caseType : ComponentType.PC_CASE;
    }

    /** Which case was put down. Decides the model drawn and the profile the machine starts with. */
    public ComponentType caseType() {
        return caseType;
    }

    public int id() {
        return id;
    }

    public String worldName() {
        return worldName;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public BlockFace facing() {
        return facing;
    }

    public Location location(World world) {
        return new Location(world, x, y, z);
    }

    public ComponentType installedIn(ComponentSlot slot) {
        return installed.get(slot);
    }

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

    public List<ComponentSlot> missingComponents() {
        List<ComponentSlot> missing = new ArrayList<ComponentSlot>();
        for (ComponentSlot slot : ComponentSlot.values()) {
            if (slot.required() && !installed.containsKey(slot)) {
                missing.add(slot);
            }
        }
        return missing;
    }

    public boolean canAssemble() {
        return missingComponents().isEmpty();
    }

    /** The monitor size the fitted monitor will build, or null while no monitor is in. */
    public MonitorSize plannedSize() {
        ComponentType monitor = installed.get(ComponentSlot.MONITOR);
        return monitor == null ? null : monitor.monitorSize();
    }

    /**
     * Where the computer's anchor lands if this case is assembled at {@code size}.
     *
     * <p>The player put the case where they want the case, so the anchor is worked backwards from
     * it: whichever block the layout puts here -- the tower on a desk, the control block on a
     * projector -- has to end up on this spot, so the anchor is this position minus that offset.
     */
    public int[] anchorFor(MonitorSize size) {
        ComputerLayout layout = ComputerLayout.of(size);
        ComputerLayout.Offset offset =
                layout.tower() != null ? layout.tower() : layout.control();
        if (offset == null) {
            return new int[]{x, y, z};
        }
        int[] delta = ComputerLayout.rotate(offset, facing.getModX(), facing.getModZ());
        return new int[]{x - delta[0], y - delta[1], z - delta[2]};
    }

    /** The computer this case would become, with the id it will be given on insert. */
    public Computer toComputer(int newId, MonitorSize size, String type) {
        int[] anchor = anchorFor(size);
        Computer computer = new Computer(newId, worldName, anchor[0], anchor[1], anchor[2],
                facing, size, type, Computer.State.OFF);
        for (Map.Entry<ComponentSlot, ComponentType> entry : installed.entrySet()) {
            computer.install(entry.getKey(), entry.getValue());
        }
        return computer;
    }

    @Override
    public String toString() {
        return "PendingCase#" + id + "{" + worldName + " " + x + "," + y + "," + z
                + " facing " + facing + ", " + installed.size() + " parts}";
    }
}
