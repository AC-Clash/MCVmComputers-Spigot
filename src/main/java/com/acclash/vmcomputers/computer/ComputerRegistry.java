package com.acclash.vmcomputers.computer;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory index of every computer, so "is this block part of a computer?" is a hash lookup.
 *
 * <p>The previous design ran a SQL query against every column on every right-click anywhere in the
 * world, on the main thread. Since a computer's occupied blocks are fully derived from its layout,
 * the whole index can be built once at startup and kept in memory instead. Clicks then cost a map
 * lookup and no I/O at all.
 */
public final class ComputerRegistry {

    private final Map<Integer, Computer> byId = new ConcurrentHashMap<Integer, Computer>();
    /** World name -> packed block position -> computer. */
    private final Map<String, Map<Long, Computer>> byBlock =
            new ConcurrentHashMap<String, Map<Long, Computer>>();

    /**
     * Packs block coordinates into a long.
     *
     * <p>26 bits each for x and z covers the +/-30M world border, and 12 bits of y covers the
     * -64..320 build range with room to spare.
     */
    public static long packBlock(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38)
                | ((long) (z & 0x3FFFFFF) << 12)
                | (y & 0xFFF);
    }

    /** Replaces the whole index. Called once after loading from storage. */
    public synchronized void replaceAll(Collection<Computer> computers) {
        byId.clear();
        byBlock.clear();
        for (Computer computer : computers) {
            add(computer);
        }
    }

    public synchronized void add(Computer computer) {
        byId.put(Integer.valueOf(computer.id()), computer);
        Map<Long, Computer> world = byBlock.computeIfAbsent(
                computer.worldName(), name -> new HashMap<Long, Computer>());
        for (int[] block : computer.occupiedBlocks()) {
            world.put(Long.valueOf(packBlock(block[0], block[1], block[2])), computer);
        }
    }

    public synchronized Computer remove(int id) {
        Computer computer = byId.remove(Integer.valueOf(id));
        if (computer == null) {
            return null;
        }
        Map<Long, Computer> world = byBlock.get(computer.worldName());
        if (world != null) {
            for (int[] block : computer.occupiedBlocks()) {
                world.remove(Long.valueOf(packBlock(block[0], block[1], block[2])));
            }
            if (world.isEmpty()) {
                byBlock.remove(computer.worldName());
            }
        }
        return computer;
    }

    public Computer byId(int id) {
        return byId.get(Integer.valueOf(id));
    }

    /** The computer occupying a block, or null. This is the hot path for click handling. */
    public Computer at(String worldName, int x, int y, int z) {
        Map<Long, Computer> world = byBlock.get(worldName);
        return world == null ? null : world.get(Long.valueOf(packBlock(x, y, z)));
    }

    /**
     * True if any of a computer's blocks would collide with an existing one. Checked before
     * building so two machines cannot be interleaved.
     */
    public Computer findOverlap(Computer candidate) {
        Map<Long, Computer> world = byBlock.get(candidate.worldName());
        if (world == null) {
            return null;
        }
        for (int[] block : candidate.occupiedBlocks()) {
            Computer existing = world.get(Long.valueOf(packBlock(block[0], block[1], block[2])));
            if (existing != null) {
                return existing;
            }
        }
        return null;
    }

    public Collection<Computer> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    public int size() {
        return byId.size();
    }
}
