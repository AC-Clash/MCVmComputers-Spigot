package com.acclash.vmcomputers.commands.computersubcommands;

import com.acclash.vmcomputers.VMComputers;
import com.acclash.vmcomputers.commands.ComputerSubCommand;
import com.acclash.vmcomputers.utils.Serialization;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Remove extends ComputerSubCommand {

    @Override
    public String getName() {
        return "remove";
    }

    @Override
    public String getDescription() {
        return "Removes a computer at your current location.";
    }

    @Override
    public String getSyntax() {
        return ChatColor.GOLD + "To remove a computer and it's peripherals, hop in the chair and enter: /computer remove OR use /computer remove <ID>";
    }

    @Override
    public void perform(Player player, String[] args) {
        if (args.length == 1) {
            Location proposedLoc = player.getLocation().getBlock().getLocation();
            if (player.isInsideVehicle()) {
                if (!player.getVehicle().getPersistentDataContainer().has(new NamespacedKey(VMComputers.getPlugin(), "isEChair"), PersistentDataType.STRING)) return;
                proposedLoc.setY(proposedLoc.getBlockY() + 1);
            }
            String proposedS = Serialization.serialize(proposedLoc);
            try {
                String sql = "SELECT * FROM `computers` WHERE `block_loc` = '" + proposedS + "'";
                ResultSet resultSet = VMComputers.getPlugin().getDB().executeQuery(sql);
                if (resultSet.next()) {
                    player.stopSound("pmagisha.hdd-loop");
                    Location blockLoc = Serialization.deserialize(resultSet.getString("block_loc"));
                    blockLoc.getBlock().setType(Material.AIR);
                    Location monitorLoc = Serialization.deserialize(resultSet.getString("monitor_loc"));
                    monitorLoc.getBlock().setType(Material.AIR);
                    Location towerLoc = Serialization.deserialize(resultSet.getString("tower_loc"));
                    towerLoc.getBlock().setType(Material.AIR);
                    Location keyboardLoc = Serialization.deserialize(resultSet.getString("keyboard_loc"));
                    keyboardLoc.getBlock().setType(Material.AIR);
                    Location mouseLoc = Serialization.deserialize(resultSet.getString("button_loc"));
                    mouseLoc.getBlock().setType(Material.AIR);
                    Optional<Entity> monitor = blockLoc.getWorld().getNearbyEntities(monitorLoc, 1, 1, 1).stream().filter(entity -> entity.getPersistentDataContainer().has(new NamespacedKey(VMComputers.getPlugin(), "isMonitor"), PersistentDataType.STRING)).findFirst();
                    monitor.ifPresent(Entity::remove);
                    Optional<Entity> eChair = blockLoc.getWorld().getNearbyEntities(blockLoc, 1, 1, 1).stream().filter(entity -> entity.getPersistentDataContainer().has(new NamespacedKey(VMComputers.getPlugin(), "isEChair"), PersistentDataType.STRING)).findFirst();
                    eChair.ifPresent(Entity::remove);
                    player.sendMessage(ChatColor.GREEN + "Successfully removed a " + resultSet.getString("type") + " with an ID of " + resultSet.getString("id"));

                    // DELETE FROM DATABASE
                    String sql2 = "DELETE FROM `computers` WHERE `block_loc` = '" + proposedS + "'";
                    VMComputers.getPlugin().getDB().executeUpdate(sql2);
                } else {
                    player.sendMessage(ChatColor.YELLOW + "There is no computer tied to this location!");
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else if (args.length == 2) {
            try {
                String sql = "SELECT * FROM `computers` WHERE `id` = '" + args[1] + "'";
                ResultSet resultSet = VMComputers.getPlugin().getDB().executeQuery(sql);
                if (resultSet.next()) {
                    player.stopSound("pmagisha.hdd-loop");
                    Location blockLoc = Serialization.deserialize(resultSet.getString("block_loc"));
                    blockLoc.getBlock().setType(Material.AIR);
                    Location monitorLoc = Serialization.deserialize(resultSet.getString("monitor_loc"));
                    monitorLoc.getBlock().setType(Material.AIR);
                    Location towerLoc = Serialization.deserialize(resultSet.getString("tower_loc"));
                    towerLoc.getBlock().setType(Material.AIR);
                    Location keyboardLoc = Serialization.deserialize(resultSet.getString("keyboard_loc"));
                    keyboardLoc.getBlock().setType(Material.AIR);
                    Location mouseLoc = Serialization.deserialize(resultSet.getString("button_loc"));
                    mouseLoc.getBlock().setType(Material.AIR);
                    Optional<Entity> monitor = blockLoc.getWorld().getNearbyEntities(monitorLoc, 1, 1, 1).stream().filter(entity -> entity.getPersistentDataContainer().has(new NamespacedKey(VMComputers.getPlugin(), "isMonitor"), PersistentDataType.STRING)).findFirst();
                    monitor.ifPresent(Entity::remove);
                    Optional<Entity> eChair = blockLoc.getWorld().getNearbyEntities(blockLoc, 1, 1, 1).stream().filter(entity -> entity.getPersistentDataContainer().has(new NamespacedKey(VMComputers.getPlugin(), "isEChair"), PersistentDataType.STRING)).findFirst();
                    eChair.ifPresent(Entity::remove);
                    player.sendMessage(ChatColor.GREEN + "Successfully removed a " + resultSet.getString("type") + " with an ID of " + resultSet.getString("id"));

                    // DELETE FROM DATABASE
                    String sql2 = "DELETE FROM `computers` WHERE `id` = '" + args[1] + "'";
                    VMComputers.getPlugin().getDB().executeUpdate(sql2);
                } else {
                    player.sendMessage(ChatColor.YELLOW + "There is no computer tied to this location!");
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            List<String> ids = new ArrayList<>();
            ResultSet rs = null;
            try {

                // Execute a SELECT query to get all the values under a certain column
                rs = VMComputers.getPlugin().getDB().executeQuery("SELECT `id` FROM `computers`");

                // Iterate over the result set in a for loop
                while (rs.next()) {
                    int id = rs.getInt("id");
                    // Do something with the value
                    ids.add(String.valueOf(id));
                }

            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                // Close the result set, statement, and connection
                try {
                    if (rs != null) {
                        rs.close();
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            return ids;
        }
        return null;
    }
}
