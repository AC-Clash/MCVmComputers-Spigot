package com.acclash.vmcomputers.parts;

import com.acclash.vmcomputers.VMComputers;
import com.acclash.vmcomputers.rfb.RfbClient;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * The eChair: the thing a player sits in to use a computer, and the keyboard they steer it with.
 *
 * <p>An eChair is an entity rather than a block because a player has to be able to <em>ride</em>
 * it, and only an entity can carry a passenger. It is invisible, unkillable and silent, so what
 * the player sees is the spruce stair underneath and what they get is a seat.
 *
 * <p>Its second job is input. While a player is riding one, the client sends its movement state to
 * the server on every change, and that is a real keyboard: five keys the guest can be given
 * without a client mod, a resource pack, or anything the player has to install. See
 * {@link #read(Input)}.
 *
 * <h2>What it is made of</h2>
 *
 * A chicken, and that is an implementation detail rather than a fact about the chair. Everything
 * outside {@link #spawn} talks about eChairs; the only reason the species appears at all is that
 * something has to be passed to {@code World.spawn}, and a chicken is the smallest vanilla mob
 * that seats a player at desk height. Its one leak is documented where it bites --
 * {@code PreventionListener} has to refuse the eggs, because the egg timer is not part of the AI
 * that is switched off here.
 */
public final class EChair {

    /** Marks an entity as a seat. Also read by the listeners that protect and drive it. */
    public static final String TAG = "isEChair";

    private EChair() {
    }

    /**
     * How the chair's five keys reach the guest.
     *
     * <p>Two mappings because one set of keys cannot serve both jobs. A game wants the keys a game
     * expects; a menu, a bootloader or a file manager wants the arrows and enter. Same chair, same
     * five inputs, switched by the player.
     */
    public enum KeyMode {
        /** WASD, space and shift, as a game expects them. */
        GAME("WASD, space, shift", "for playing"),
        /** Arrows, enter and tab, for menus, bootloaders and anything you navigate. */
        MENU("arrows, enter, tab", "for browsing");

        private final String keys;
        private final String purpose;

        KeyMode(String keys, String purpose) {
            this.keys = keys;
            this.purpose = purpose;
        }

        public String keys() {
            return keys;
        }

        public String purpose() {
            return purpose;
        }

        public KeyMode other() {
            return this == GAME ? MENU : GAME;
        }
    }

    /**
     * The six inputs a seated player can produce.
     *
     * <p>Sneak is deliberately not one of them: holding shift is how a player gets out of the
     * chair, and taking that over would seat them permanently.
     *
     * <p>Sprint is the sixth, and it is a held modifier at both ends -- which is why it maps to
     * one. In a game that is left shift, because that is what "run" is bound to almost everywhere.
     * In a menu it is tab, which is the key that actually moves you between fields in a BIOS or an
     * installer, and the one thing the arrows and enter cannot do on their own. Held tab will
     * auto-repeat in the guest, but sprint is a tap in practice.
     */
    public enum Key {
        FORWARD('w', RfbClient.Keysym.UP),
        LEFT('a', RfbClient.Keysym.LEFT),
        BACK('s', RfbClient.Keysym.DOWN),
        RIGHT('d', RfbClient.Keysym.RIGHT),
        JUMP(' ', RfbClient.Keysym.RETURN),
        SPRINT(RfbClient.Keysym.SHIFT_LEFT, RfbClient.Keysym.TAB);

        private final int gameKeysym;
        private final int menuKeysym;

        Key(int game, int menu) {
            this.gameKeysym = game;
            this.menuKeysym = menu;
        }

        /** The X11 keysym this input sends in a given mode. */
        public int keysym(KeyMode mode) {
            return mode == KeyMode.GAME ? gameKeysym : menuKeysym;
        }
    }

    /** Every key, once, in a fixed order so held-state arrays line up between calls. */
    public static final Key[] KEYS = Key.values();

    /**
     * Reads a client's movement state into this chair's keys.
     *
     * <p>Indexed to match {@link #KEYS}, so a caller can diff two of these to find what was just
     * pressed and what was just let go.
     */
    public static boolean[] read(Input input) {
        boolean[] down = new boolean[KEYS.length];
        if (input == null) {
            return down;
        }
        down[Key.FORWARD.ordinal()] = input.isForward();
        down[Key.LEFT.ordinal()] = input.isLeft();
        down[Key.BACK.ordinal()] = input.isBackward();
        down[Key.RIGHT.ordinal()] = input.isRight();
        down[Key.JUMP.ordinal()] = input.isJump();
        down[Key.SPRINT.ordinal()] = input.isSprint();
        return down;
    }

    /** True if this entity is a seat. */
    public static boolean is(Entity entity) {
        return entity != null
                && entity.getPersistentDataContainer().has(tagKey(), PersistentDataType.STRING);
    }

    /** The computer an eChair belongs to, or -1 if it is not stamped with one. */
    public static int computerIdOf(Entity chair) {
        Integer id = chair.getPersistentDataContainer()
                .get(idKey(), PersistentDataType.INTEGER);
        return id == null ? -1 : id.intValue();
    }

    /**
     * The eChair a player is sitting in, or null if they are not in one.
     *
     * <p>Cheap enough to call on every input packet: a vehicle is a field on the player and the
     * tag lookup only happens once something is being ridden.
     */
    public static Entity seatOf(Player player) {
        Entity vehicle = player.getVehicle();
        return is(vehicle) ? vehicle : null;
    }

    /**
     * Puts an eChair in the world.
     *
     * @param seat       where the player sits; the stair's own block, centred
     * @param computerId owning computer, so protection and input can find their way back
     * @param yaw        the direction the sitter faces, which is the direction of the screen
     */
    public static void spawn(World world, Location seat, int computerId, float yaw) {
        world.spawn(seat, org.bukkit.entity.Chicken.class, chair -> {
            chair.getPersistentDataContainer().set(tagKey(), PersistentDataType.STRING, "true");
            chair.getPersistentDataContainer()
                    .set(idKey(), PersistentDataType.INTEGER, Integer.valueOf(computerId));
            chair.setAI(false);
            chair.setInvisible(true);
            chair.setInvulnerable(true);
            chair.setSilent(true);
            chair.setRemoveWhenFarAway(false);
            chair.setRotation(yaw, 0f);
        });
    }

    /**
     * Which mapping this player is using.
     *
     * <p>Kept on the player rather than the chair, and in their persistent data rather than in
     * memory, so it is a preference that follows them between machines and survives a restart.
     * Someone who plays games at their computer should not have to set this every time they sit
     * down.
     */
    public static KeyMode modeOf(Player player) {
        String stored = player.getPersistentDataContainer()
                .get(modeKey(), PersistentDataType.STRING);
        if (stored != null) {
            try {
                return KeyMode.valueOf(stored);
            } catch (IllegalArgumentException ignored) {
                // A mode from a version that had different ones; fall through to the default.
            }
        }
        return KeyMode.GAME;
    }

    public static void setMode(Player player, KeyMode mode) {
        player.getPersistentDataContainer().set(modeKey(), PersistentDataType.STRING, mode.name());
    }

    /**
     * What a player's six inputs actually send in a mode, defaults included.
     *
     * <p>Both modes are rebindable rather than there being a third "custom" one. A player who
     * wants Q and E instead of A and D wants that <em>while playing</em>, and would still want the
     * arrows for menus -- so the two profiles stay meaningful and the toggle keeps its job.
     *
     * <p>Stored as all six keysyms rather than as a set of differences, because the alternative
     * needs to know which defaults a stored binding was diffed against, and those change whenever
     * this file does.
     */
    public static int[] bindingsOf(Player player, KeyMode mode) {
        int[] keysyms = new int[KEYS.length];
        for (Key key : KEYS) {
            keysyms[key.ordinal()] = key.keysym(mode);
        }

        String stored = player.getPersistentDataContainer()
                .get(bindKey(mode), PersistentDataType.STRING);
        if (stored == null || stored.isEmpty()) {
            return keysyms;
        }
        String[] parts = stored.split(",");
        for (int i = 0; i < parts.length && i < keysyms.length; i++) {
            try {
                keysyms[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException ignored) {
                // Leave the default in place. One unreadable entry should cost that key, not the
                // whole profile.
            }
        }
        return keysyms;
    }

    /** Points one input at a different key, leaving the rest of the profile alone. */
    public static void bind(Player player, KeyMode mode, Key key, int keysym) {
        int[] keysyms = bindingsOf(player, mode);
        keysyms[key.ordinal()] = keysym;
        StringBuilder packed = new StringBuilder();
        for (int i = 0; i < keysyms.length; i++) {
            packed.append(i == 0 ? "" : ",").append(keysyms[i]);
        }
        player.getPersistentDataContainer()
                .set(bindKey(mode), PersistentDataType.STRING, packed.toString());
    }

    /** Puts a profile back to the keys it started with. */
    public static void resetBindings(Player player, KeyMode mode) {
        player.getPersistentDataContainer().remove(bindKey(mode));
    }

    /** True if this profile has been changed from its defaults. */
    public static boolean isCustomised(Player player, KeyMode mode) {
        return player.getPersistentDataContainer()
                .has(bindKey(mode), PersistentDataType.STRING);
    }

    /** The input a player means by a name like {@code forward}, or null. */
    public static Key keyByName(String name) {
        for (Key key : KEYS) {
            if (key.name().equalsIgnoreCase(name)) {
                return key;
            }
        }
        return null;
    }

    /** Convenience for entities that are known to be living, which every eChair is. */
    public static boolean isSeatedIn(Player player, LivingEntity entity) {
        return entity.getPassengers().contains(player);
    }

    private static NamespacedKey tagKey() {
        return new NamespacedKey(VMComputers.getPlugin(), TAG);
    }

    private static NamespacedKey idKey() {
        return new NamespacedKey(VMComputers.getPlugin(), "computerId");
    }

    private static NamespacedKey modeKey() {
        return new NamespacedKey(VMComputers.getPlugin(), "eChairKeyMode");
    }

    private static NamespacedKey bindKey(KeyMode mode) {
        return new NamespacedKey(VMComputers.getPlugin(),
                "echairbinds" + mode.name().toLowerCase(java.util.Locale.ROOT));
    }
}
