package com.acclash.vmcomputers.utils;

import com.acclash.vmcomputers.computer.Computer;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * Who may do what.
 *
 * <p>The line is drawn by what a player can <em>lose</em>, not by what they can touch. Anyone may
 * use a machine — sit at it, switch it on, click and type — because a computer standing in a
 * shared world is a thing to use, and locking that down would make a built machine useless to
 * everyone but its owner. Only the owner may take one apart or change what is fitted, because
 * that is what destroys work: pulling the hard drive out of someone's Debian install is not a
 * prank you can undo.
 *
 * <p>Every refusal says who owns the thing. A silent no reads as a broken plugin, and a player who
 * cannot tell the difference between "not allowed" and "not working" reports the wrong bug.
 */
public final class Permissions {

    public static final String USE = "vmcomputers.use";
    public static final String BUILD = "vmcomputers.build";
    public static final String ADMIN = "vmcomputers.admin";

    private Permissions() {
    }

    /** May interact with computers at all: sitting, power, pointer, keyboard. */
    public static boolean canUse(Player player) {
        return player.hasPermission(USE) || player.hasPermission(ADMIN);
    }

    /** May place cases, assemble machines and buy parts. */
    public static boolean canBuild(Player player) {
        return player.hasPermission(BUILD) || player.hasPermission(ADMIN);
    }

    public static boolean isAdmin(Player player) {
        return player.hasPermission(ADMIN);
    }

    /**
     * May reconfigure or dismantle this particular machine.
     *
     * <p>Admins override, and unowned machines — the ones built before ownership was recorded —
     * stay open to anyone. Refusing everyone on those would strand them with no way back.
     */
    public static boolean canModify(Player player, Computer computer) {
        return isAdmin(player) || computer.mayModify(player.getUniqueId());
    }

    /**
     * Checks {@link #canModify} and explains the refusal.
     *
     * @return true if the player may proceed
     */
    public static boolean requireModify(Player player, Computer computer, String action) {
        if (canModify(player, computer)) {
            return true;
        }
        player.sendMessage(ChatColor.RED + "You cannot " + action + " computer #" + computer.id()
                + ChatColor.RED + " — it belongs to " + ownerName(computer) + ".");
        return false;
    }

    /** Checks {@link #canUse} and explains the refusal. */
    public static boolean requireUse(Player player) {
        if (canUse(player)) {
            return true;
        }
        player.sendMessage(ChatColor.RED + "You do not have permission to use computers here.");
        return false;
    }

    /** Checks {@link #canBuild} and explains the refusal. */
    public static boolean requireBuild(Player player) {
        if (canBuild(player)) {
            return true;
        }
        player.sendMessage(ChatColor.RED + "You do not have permission to build computers here.");
        return false;
    }

    /** A readable owner, falling back to the raw id for someone who has never joined. */
    public static String ownerName(Computer computer) {
        if (computer.owner() == null) {
            return "nobody";
        }
        String name = org.bukkit.Bukkit.getOfflinePlayer(computer.owner()).getName();
        return name != null ? name : computer.owner().toString();
    }
}
