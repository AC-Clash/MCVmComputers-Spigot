package com.acclash.vmcomputers.parts;

import com.acclash.vmcomputers.VMComputers;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

/**
 * The phone you order parts on.
 *
 * <p>Stands in for the mod's ordering tablet, which waits to acquire a satellite and then shows a
 * little shop OS. That whole interface was drawn by the client mod; with a vanilla client there is
 * nothing to draw it with, so the tablet's job here is only to open a chest menu -- which means the
 * prop is free to be whatever is funniest.
 *
 * <p>The icon is an approximation and known to be one. Nothing in vanilla looks like a brick phone,
 * for the same reason nothing looks like a graphics card: the only item whose texture a server can
 * choose is a player head. A recovery compass is at least a dark handheld device rather than a tool
 * or a food, and it cannot be accidentally placed as a block, which rules out most of the
 * better-shaped candidates.
 */
public final class BrickPhone {

    private static final String KEY = "brickPhone";

    private BrickPhone() {
    }

    public static ItemStack create() {
        ItemStack stack = new ItemStack(Material.RECOVERY_COMPASS);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RESET + "" + ChatColor.GOLD + "Brick Phone");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Heavy. Beige. One number in it.",
                    "",
                    ChatColor.YELLOW + "Right-click to call about parts"));
            meta.getPersistentDataContainer().set(key(), PersistentDataType.STRING, "true");
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static boolean is(ItemStack stack) {
        if (stack == null || stack.getType() != Material.RECOVERY_COMPASS) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null
                && meta.getPersistentDataContainer().has(key(), PersistentDataType.STRING);
    }

    private static NamespacedKey key() {
        return new NamespacedKey(VMComputers.getPlugin(), KEY);
    }
}
