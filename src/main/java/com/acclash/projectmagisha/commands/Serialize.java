package com.acclash.projectmagisha.commands;

import com.acclash.projectmagisha.files.Classified;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Objects;

public class Serialize implements CommandExecutor {


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender instanceof Player player) {

            ItemStack item = player.getInventory().getItemInMainHand();

            String fItemName;
            if (Objects.requireNonNull(item.getItemMeta()).hasDisplayName()) {
                fItemName = item.getItemMeta().getDisplayName();
            } else if (item.getItemMeta().hasLocalizedName()) {
                String original = item.getType().toString().toLowerCase().replace("_", " ");
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

            String encodedObject;

            try {
                ByteArrayOutputStream io = new ByteArrayOutputStream();
                BukkitObjectOutputStream os = new BukkitObjectOutputStream(io);
                os.writeObject(item);
                os.flush();

                byte[] serializedObject = io.toByteArray();

                encodedObject = Base64.getEncoder().encodeToString(serializedObject);

                player.getInventory().setItemInMainHand(null);

                Classified.get().set("test", encodedObject);
                Classified.save();


                player.sendMessage(ChatColor.GREEN + "Successfully serialized " + fItemName);

            } catch (IOException e) {
                player.sendMessage(ChatColor.RED + "Unable to encode the item. Please try again later.");
            }

        }

        return true;
    }
}
