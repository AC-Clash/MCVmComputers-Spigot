package com.acclash.vmcomputers.listeners;

import com.acclash.vmcomputers.utils.CustomItems;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;

public class RecipeListener implements Listener {

    @EventHandler
    public void onItemCraft(PrepareItemCraftEvent e) {
        List<ItemStack> recipe = Arrays.stream(e.getInventory().getMatrix()).toList();
        StringBuilder recipeStringBuilder = new StringBuilder();
        for (int i = 0; i < 9; i++) {
            if (i == 8) {
                if (recipe.get(i) != null) {
                    recipeStringBuilder.append(recipe.get(i).getType());
                } else {
                    recipeStringBuilder.append("AIR");
                }
            } else {
                if (recipe.get(i) != null) {
                    recipeStringBuilder.append(recipe.get(i).getType()).append(", ");
                } else {
                    recipeStringBuilder.append("AIR, ");
                }
            }
        }
        String recipeString = recipeStringBuilder.toString();
        String customRecipeString = "BRICK, IRON_INGOT, BRICK, REDSTONE, GLASS_PANE, COMPARATOR, BRICK, PAPER, BRICK";
        if (recipeString.equals(customRecipeString)) {
            e.getInventory().setResult(CustomItems.getPhone());
        }
    }
}
