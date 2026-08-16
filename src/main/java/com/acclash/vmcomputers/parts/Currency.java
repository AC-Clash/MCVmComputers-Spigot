package com.acclash.vmcomputers.parts;

import com.acclash.vmcomputers.VMComputers;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

/**
 * Auros: what parts are bought with.
 *
 * <h2>Why not iron</h2>
 *
 * <p>The mod charges iron ingots and so did this. Iron is the most useful metal in the game, so
 * spending it on a graphics card competes with everything else a player wants it for, and the
 * prices inherited from the mod were tuned against that.
 *
 * <h2>Why not plain maps</h2>
 *
 * <p>The obvious cheap version -- count vanilla blank maps -- is a trap. A blank map costs eight
 * paper and a compass, and a compass is four iron, so every unit would have been four iron plus
 * change: a full machine came to roughly 344 iron against the mod's 86. Nothing else in a normal
 * game wants a map either, so no player would have any and no farm produces them.
 *
 * <p>So an Auro is its own item with its own recipe. That is what decouples the price scale from
 * iron and lets the mod's numbers keep meaning what they meant. Paper is the input because it is
 * renewable, farmable, and worth almost nothing otherwise.
 *
 * <h2>What it is</h2>
 *
 * <p>A filled map, tinted gold, named and tagged. Filled rather than blank because the tint sits on
 * the map's markings layer and <em>does</em> show in an inventory slot, so an Auro reads as money
 * at a glance where a blank map reads as stationery. It carries no map id and draws nothing: a
 * filled map does not render its picture in a slot anyway, so artwork would only be visible by
 * holding one up.
 *
 * <p>Two Auros are byte-identical and stack to 64, which was worth checking -- a currency that does
 * not stack is unusable, and forty of them for a monitor would be forty inventory slots.
 *
 * <p>The tag is what counts, never the material. Screen panels are filled maps too, and a currency
 * the plugin also builds monitors out of must not be confusable with one.
 */
public final class Currency {

    private static final String KEY = "auro";

    /** Gold, on the markings layer of the map item. */
    private static final Color TINT = Color.fromRGB(0xE0B33A);

    /**
     * Paper per Auro.
     *
     * <p>Three puts a whole default machine at about 258 paper -- a few stacks of sugarcane, so a
     * real errand but not a grind. This is the one number that sets the cost of everything, since
     * the prices themselves are the mod's.
     */
    private static final int PAPER_PER_AURO = 3;

    private Currency() {
    }

    /** A stack of Auros. */
    public static ItemStack create(int amount) {
        ItemStack stack = new ItemStack(Material.FILLED_MAP, amount);
        MapMeta meta = (MapMeta) stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RESET + "" + ChatColor.GOLD + "Auro");
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Legal tender of Aura Charisma.",
                    ChatColor.DARK_GRAY + "Crafted from " + PAPER_PER_AURO + " paper."));
            meta.setColor(TINT);
            meta.getPersistentDataContainer().set(key(), PersistentDataType.STRING, "true");
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** True for an Auro, false for any other map including a screen panel. */
    public static boolean is(ItemStack stack) {
        if (stack == null || stack.getType() != Material.FILLED_MAP) {
            return false;
        }
        return stack.getItemMeta() != null
                && stack.getItemMeta().getPersistentDataContainer()
                .has(key(), PersistentDataType.STRING);
    }

    /** "12 Auros", or "1 Auro". */
    public static String format(int amount) {
        return amount + (amount == 1 ? " Auro" : " Auros");
    }

    /** How many a player is carrying. */
    public static int heldBy(Player player) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (is(stack)) {
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
            if (!is(stack)) {
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

    /**
     * Registers minting, replacing any recipe left from a previous load.
     *
     * <p>Shapeless, so it does not matter where the paper goes, and three paper alone matches no
     * vanilla recipe -- a book wants leather with it.
     */
    public static void registerRecipe() {
        NamespacedKey key = new NamespacedKey(VMComputers.getPlugin(), "auro");
        Bukkit.removeRecipe(key);

        ShapelessRecipe recipe = new ShapelessRecipe(key, create(1));
        recipe.addIngredient(PAPER_PER_AURO, Material.PAPER);
        Bukkit.addRecipe(recipe);
    }

    private static NamespacedKey key() {
        return new NamespacedKey(VMComputers.getPlugin(), KEY);
    }
}
