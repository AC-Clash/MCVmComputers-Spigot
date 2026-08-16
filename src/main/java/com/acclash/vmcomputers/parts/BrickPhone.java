package com.acclash.vmcomputers.parts;

import com.acclash.vmcomputers.VMComputers;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
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
 * <p>It is a brick. Nothing in vanilla is actually a brick phone, so the choice is between items
 * that gesture at a handheld device and the one item that is literally the thing the joke is named
 * after -- and a brick reads as a brick phone the moment it has the name on it, which no gadget
 * lookalike managed.
 */
public final class BrickPhone {

    private static final String KEY = "brickPhone";

    private BrickPhone() {
    }

    public static ItemStack create() {
        ItemStack stack = new ItemStack(Material.BRICK);
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

    /**
     * Registers the crafting recipe, replacing any left from a previous load.
     *
     * <p>{@code removeRecipe} first because {@code addRecipe} refuses a key that already exists,
     * and a plugin reload runs this a second time in the same server.
     *
     * <p>The shape is the phone: a copper aerial on top, two clay bricks making up the body it is
     * named after, a compass and a repeater for the guts, and glass down the side for the little
     * screen.
     *
     * <pre>
     *     .  rod  .
     *   brick  compass  pane
     *   brick  repeater pane
     * </pre>
     *
     * <p>A repeater rather than plain redstone, because a repeater is what gets a phone its signal
     * in the first place, and the extra cost over dust is a few stone and sticks next to a compass
     * that already wants four iron.
     */
    public static void registerRecipe() {
        NamespacedKey key = new NamespacedKey(VMComputers.getPlugin(), "brick_phone");
        Bukkit.removeRecipe(key);

        ShapedRecipe recipe = new ShapedRecipe(key, create());
        recipe.shape(
                " L ",
                "BCG",
                "BRG");
        recipe.setIngredient('L', Material.LIGHTNING_ROD);
        // Plain bricks only. The phone is itself a brick now, so a bare material ingredient
        // would happily accept one -- and the recipe book's auto-fill would reach into the
        // player's inventory and feed them their own phone to build a phone.
        recipe.setIngredient('B', new RecipeChoice.ExactChoice(new ItemStack(Material.BRICK)));
        recipe.setIngredient('C', Material.COMPASS);
        recipe.setIngredient('G', Material.GLASS_PANE);
        recipe.setIngredient('R', Material.REPEATER);
        Bukkit.addRecipe(recipe);
    }

    public static boolean is(ItemStack stack) {
        if (stack == null || stack.getType() != Material.BRICK) {
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
