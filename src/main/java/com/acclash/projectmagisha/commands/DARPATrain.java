package com.acclash.projectmagisha.commands;

import com.acclash.projectmagisha.ProjectMagisha;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.material.MaterialData;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class DARPATrain implements TabExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender instanceof Player player) {
            if (args.length == 0) {
                player.sendMessage(ChatColor.RED + "You need to enter some arguments.");
                player.sendMessage(ChatColor.YELLOW + "To create a DARPA train: /darpatrain <type>");
            } else if (args.length == 1) {
                if (args[0].equals("Prototype_A")) {
                    Minecart minecart = (Minecart) player.getWorld().spawnEntity(player.getLocation(), EntityType.MINECART);
                    minecart.setMaxSpeed(20);
                    minecart.setDisplayBlock(new MaterialData(Material.DIAMOND_BLOCK));
                    minecart.setInvulnerable(true);
                    minecart.getPersistentDataContainer().set(new NamespacedKey(ProjectMagisha.getPlugin(), "isDARPATrain"), PersistentDataType.STRING, "true");
                    player.sendMessage(ChatColor.GREEN + "Enjoy your new experimental DARPA train.");
                } else if (args[0].equals("Prototype_B")) {
                    Minecart minecart = (Minecart) player.getWorld().spawnEntity(player.getLocation(), EntityType.MINECART);
                    minecart.setMaxSpeed(0.8);
                    minecart.setDisplayBlock(new MaterialData(Material.GOLD_BLOCK));
                    minecart.setInvulnerable(true);
                    minecart.getPersistentDataContainer().set(new NamespacedKey(ProjectMagisha.getPlugin(), "isDARPATrain"), PersistentDataType.STRING, "true");
                    player.sendMessage(ChatColor.GREEN + "Enjoy your new experimental DARPA train.");
                } else {
                    player.sendMessage(ChatColor.RED + "Check your spelling and try again.");
                }
            } else {
                player.sendMessage(ChatColor.RED + "Too many arguments.");
                player.sendMessage(ChatColor.YELLOW + "To create a DARPA train: /darpatrain <type>");
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> trainTypes = new ArrayList<>();
            trainTypes.add("Prototype_A");
            trainTypes.add("Prototype_B");

            return trainTypes;
        }
        return null;
    }
}
