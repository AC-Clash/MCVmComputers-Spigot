package com.acclash.vmcomputers.parts;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * What parts are bought with.
 *
 * <p>Auros. The mod charges iron ingots and this used to as well, but iron is the most useful metal
 * in the game and spending it on a graphics card competes with everything else a player wants it
 * for. A blank map is a better fit: it costs paper and a compass, so it is not free, but nothing
 * else in a normal game wants one.
 *
 * <p>{@link Material#MAP} specifically, never {@code FILLED_MAP} -- filled maps are what the
 * screens are made of, and a currency the plugin also builds monitors out of would be a bad
 * accident waiting to happen.
 *
 * <p>These are ordinary maps rather than a tagged item of our own. A custom currency would have no
 * source: nothing in the world would drop one, so the shop could never be used. Anything a player
 * can obtain the normal way spends here.
 */
public final class Currency {

    /** The item an Auro is. */
    public static final Material ITEM = Material.MAP;

    private Currency() {
    }

    /** "12 Auros", or "1 Auro". */
    public static String format(int amount) {
        return amount + (amount == 1 ? " Auro" : " Auros");
    }

    /** How many a player is carrying. */
    public static int heldBy(Player player) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == ITEM) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /**
     * Removes exactly {@code amount} from a player's inventory.
     *
     * <p>Only call once {@link #heldBy} has confirmed they have it; this takes what it can find and
     * does not report a shortfall.
     */
    public static void take(Player player, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != ITEM) {
                continue;
            }
            int take = Math.min(remaining, stack.getAmount());
            remaining -= take;
            if (take >= stack.getAmount()) {
                player.getInventory().setItem(i, null);
            } else {
                stack.setAmount(stack.getAmount() - take);
            }
        }
    }
}
