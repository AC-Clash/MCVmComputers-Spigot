package com.acclash.projectmagisha.commands;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class FallingBlockTest implements CommandExecutor, Listener {

    boolean fallingChicken;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender instanceof Player player) {

            ItemStack egg = new ItemStack(Material.VILLAGER_SPAWN_EGG);
            ItemMeta eggMeta = egg.getItemMeta();
            eggMeta.setCustomModelData(117);
            eggMeta.setDisplayName(ChatColor.BLUE + "Falling Block Chicken");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Summon a chicken with a");
            lore.add(ChatColor.GRAY + "falling block on it");
            eggMeta.setLore(lore);
            egg.setItemMeta(eggMeta);
            player.getInventory().addItem(egg);
        }
        return true;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        // Check if the interaction was with a spawn egg
        if (item != null && item.getType() == Material.VILLAGER_SPAWN_EGG) {
            ItemMeta meta = item.getItemMeta();

            // Check if the spawn egg has a custom display name
            if (meta != null && meta.hasCustomModelData()) {
                int modelNum = meta.getCustomModelData();

                // Check if the display name matches the desired name
                if (modelNum == 117) {
                    // The player spawned a chicken with the specified spawn egg name
                    fallingChicken = true;
                    // Additional logic or actions can be performed here

                }
            }
        }
    }

    @EventHandler
    public void onChickenSpawn(CreatureSpawnEvent e) {
        if (!(e.getEntity() instanceof Villager)) return;
        if (!e.getSpawnReason().equals(CreatureSpawnEvent.SpawnReason.SPAWNER_EGG)) return;
        if (fallingChicken) {
            ArmorStand armorStand = (ArmorStand) e.getLocation().getWorld().spawnEntity(e.getLocation().add(0, 0.5, 0), EntityType.ARMOR_STAND);
            armorStand.setVisible(false);
            armorStand.getEquipment().setHelmet(new ItemStack(Material.OAK_PLANKS));
            e.getEntity().addPassenger(armorStand);
            e.getEntity().getEquipment().setHelmet(new ItemStack(Material.SPRUCE_PLANKS));
            fallingChicken = false;
        }
    }

}
