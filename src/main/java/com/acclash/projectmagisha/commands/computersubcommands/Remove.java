package com.acclash.projectmagisha.commands.computersubcommands;

import com.acclash.projectmagisha.ProjectMagisha;
import com.acclash.projectmagisha.commands.ComputerSubCommand;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.sql.ResultSet;
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
        return ChatColor.GOLD + "To remove a computer and it's peripherals, hop in the chair and enter: /computer remove";
    }

    @Override
    public void perform(Player player, String[] args) {
        if (args.length >= 1) {
            Location blockLoc = player.getLocation().getBlock().getLocation();
            if (player.isInsideVehicle()) {
                blockLoc.setY(blockLoc.getBlockY() + 1);
                if (!player.getVehicle().getPersistentDataContainer().has(new NamespacedKey(ProjectMagisha.getPlugin(), "isEChair"), PersistentDataType.STRING))
                    return;
            }
            try {
                String sql = "SELECT * FROM `computers` WHERE `block_loc` = '" + blockLoc + "'";
                ResultSet resultSet = ProjectMagisha.getPlugin().getDB().executeQuery(sql);
                if (resultSet.next()) {
                    float direction = resultSet.getFloat("direction");
                    player.stopSound("pmagisha.hdd-loop");
                    blockLoc.getBlock().setType(Material.AIR);
                    Location monitorLoc = ProjectMagisha.calculateMonitorLoc(blockLoc, direction);
                    monitorLoc.getBlock().setType(Material.AIR);
                    Location towerLoc = ProjectMagisha.calculateTowerLoc(blockLoc, direction);
                    towerLoc.getBlock().setType(Material.AIR);
                    Location keyboardLoc = ProjectMagisha.calculateKeyboardLoc(blockLoc, direction);
                    keyboardLoc.getBlock().setType(Material.AIR);
                    Location mouseLoc = ProjectMagisha.calculateButtonLoc(blockLoc, direction);
                    mouseLoc.getBlock().setType(Material.AIR);
                    Optional<Entity> monitor = blockLoc.getWorld().getNearbyEntities(monitorLoc, 1, 1, 1).stream().filter(entity -> entity.getPersistentDataContainer().has(new NamespacedKey(ProjectMagisha.getPlugin(), "isMonitor"), PersistentDataType.STRING)).findFirst();
                    monitor.ifPresent(Entity::remove);
                    Optional<Entity> eChair = blockLoc.getWorld().getNearbyEntities(blockLoc, 1, 1, 1).stream().filter(entity -> entity.getPersistentDataContainer().has(new NamespacedKey(ProjectMagisha.getPlugin(), "isEChair"), PersistentDataType.STRING)).findFirst();
                    eChair.ifPresent(Entity::remove);
                    player.sendMessage(ChatColor.GREEN + "Successfully removed a " + resultSet.getString("type") + " with an ID of " + resultSet.getString("computer_id"));
                    // DELETE FROM DATABASE
                    String sql2 = "DELETE FROM `computers` WHERE `block_loc` = '" + blockLoc + "'";
                    ProjectMagisha.getPlugin().getDB().executeUpdate(sql2);
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
        return null;
    }
}
