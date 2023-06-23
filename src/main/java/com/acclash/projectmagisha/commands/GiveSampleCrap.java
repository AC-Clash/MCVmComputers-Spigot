package com.acclash.projectmagisha.commands;

import com.acclash.projectmagisha.files.Classified;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Objects;

public class GiveSampleCrap implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender instanceof Player player) {

            if (args.length == 1) {
                ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
                MapMeta mapmeta = (MapMeta) mapItem.getItemMeta();

                Objects.requireNonNull(mapmeta).setDisplayName(ChatColor.AQUA + "Test Map");

                MapView mapView = Bukkit.getServer().getMap(Integer.parseInt(args[0]));

                mapmeta.setMapView(mapView);
                mapItem.setItemMeta(mapmeta);

                player.getInventory().addItem(mapItem);

                player.sendMessage(ChatColor.GREEN + "Successfully given the test map with the ID of " + mapmeta.getMapView().getId());

                String encodedObject;
                try {
                    ByteArrayOutputStream io = new ByteArrayOutputStream();
                    BukkitObjectOutputStream os = new BukkitObjectOutputStream(io);
                    os.writeObject(mapView);
                    os.flush();

                    byte[] serializedObject = io.toByteArray();

                    encodedObject = Base64.getEncoder().encodeToString(serializedObject);

                    player.getInventory().setItemInMainHand(null);

                    Classified.get().set("testMap", encodedObject);
                    Classified.save();

                    player.sendMessage(ChatColor.GREEN + "Successfully serialized " + mapmeta.getDisplayName());

                } catch (IOException e) {
                    player.sendMessage(ChatColor.RED + "Unable to encode the item. Please try again later.");
                    e.printStackTrace();
                }
            }
        }
        return true;
    }
}
