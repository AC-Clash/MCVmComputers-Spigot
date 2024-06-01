package com.acclash.vmcomputers.gui;

import com.acclash.vmcomputers.commands.computersubcommands.Create;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;

public class ShopGUI implements Listener {

    public static void openMenu(Player player) {

        Inventory gui = Bukkit.createInventory(player, 27, "Steve's Computer Supply");
        ItemStack dell = new ItemStack(Material.SANDSTONE_WALL);
        ItemStack compaq = new ItemStack(Material.STONE_BRICK_WALL);

        ItemMeta dellMeta = dell.getItemMeta();
        dellMeta.setDisplayName(ChatColor.AQUA + "Dell Dimension L500R");
        ArrayList<String> pvpffalore = new ArrayList<>();
        pvpffalore.add(ChatColor.GREEN + "The perfect computer (for the 90s anyway)");
        dellMeta.setLore(pvpffalore);
        dell.setItemMeta(dellMeta);
        gui.setItem(12, dell);

        ItemMeta compaqMeta = compaq.getItemMeta();
        compaqMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "Compaq Presario");
        ArrayList<String> pvpteamslore = new ArrayList<>();
        pvpteamslore.add(ChatColor.GOLD + "A mid computer from 2006");
        compaqMeta.setLore(pvpteamslore);
        compaq.setItemMeta(compaqMeta);
        gui.setItem(14, compaq);

        player.openInventory(gui);
    }

    @EventHandler
    public void clickEvent(InventoryClickEvent e) {
        Player player = (Player) e.getWhoClicked();
        if (e.getCurrentItem() == null) return;
        if (e.getView().getTitle().equalsIgnoreCase("Steve's Computer Supply")) {
            e.setCancelled(true);
            player.performCommand("vmcomputers create " + ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName()));
        }

    }

}
