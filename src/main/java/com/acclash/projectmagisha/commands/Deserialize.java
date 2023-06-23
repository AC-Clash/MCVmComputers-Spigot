package com.acclash.projectmagisha.commands;

import com.acclash.projectmagisha.files.Classified;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

public class Deserialize implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender instanceof Player player) {
            try {
                String encodedObject = Classified.get().getString("test");

                byte[] serializedObject = Base64.getDecoder().decode(encodedObject);

                ByteArrayInputStream in = new ByteArrayInputStream(serializedObject);
                BukkitObjectInputStream is = new BukkitObjectInputStream(in);

                ItemStack item = (ItemStack) is.readObject();

                String fItemName;
                if (item.getItemMeta().hasDisplayName()) {
                    fItemName = item.getItemMeta().getDisplayName();
                } else if (item.getItemMeta().hasLocalizedName()) {
                    String original = item.getItemMeta().getLocalizedName();
                    String[] words = original.split(" ");

                    StringBuilder sb = new StringBuilder();

                    for(String word : words) {
                        String x = String.valueOf(word.charAt(0)).toUpperCase();
                        sb.append(word.replace(String.valueOf(word.charAt(0)), x)).append(" ");
                    }
                    fItemName = sb.toString();
                } else {
                    String original = item.getType().toString().toLowerCase().replace("_", " ");
                    String[] words = original.split(" ");

                    StringBuilder sb = new StringBuilder();

                    for(String word : words) {
                        String x = String.valueOf(word.charAt(0)).toUpperCase();
                        sb.append(word.replace(String.valueOf(word.charAt(0)), x)).append(" ");
                    }
                    fItemName = sb.toString();
                }

                player.getInventory().setItemInMainHand(item);

                player.sendMessage(ChatColor.GREEN + "Successfully deserialized " + fItemName);

            } catch (IOException | ClassNotFoundException e) {
                player.sendMessage(ChatColor.RED + "Unable to decode the item. Please try again later.");
            }
        }
        return true;
    }
}
