package me.acclashcorporation.project_magisha.commands;

import me.acclashcorporation.project_magisha.Project_Magisha;
import me.acclashcorporation.project_magisha.files.Classified;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;

public class Remove implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender instanceof Player) {

            Player player = (Player) sender;

            if (args.length == 0) {
                Location loc = player.getLocation();
                if (player.isInsideVehicle()) {
                    if (player.getVehicle().getPersistentDataContainer().has(new NamespacedKey(Project_Magisha.getPlugin(), "isEChair"), PersistentDataType.STRING)) {
                        String blockY = String.valueOf(loc.getBlockY() + 1);
                        String path = "C_" + loc.getWorld().getName() + "_" + loc.getBlockX() + "" + blockY + "" + loc.getBlockZ();
                        if (Classified.get().contains(path)) {
                            player.stopSound("pmagisha.hdd-loop");
                            Location computerLoc = Classified.get().getLocation(path);
                            Location monitorLoc = Project_Magisha.calculateMonitorLoc(computerLoc, computerLoc.getYaw());
                            Location towerLoc = Project_Magisha.calculateTowerLoc(computerLoc, computerLoc.getYaw());
                            Location keyboardLoc = Project_Magisha.calculateKeyboardLoc(computerLoc, computerLoc.getYaw());
                            Location mouseLoc = Project_Magisha.calculateButtonLoc(computerLoc, computerLoc.getYaw());
                            Optional<Entity> monitor = loc.getWorld().getNearbyEntities(monitorLoc, 1, 1, 1).stream().filter(entity -> entity.getPersistentDataContainer().has(new NamespacedKey(Project_Magisha.getPlugin(), "isMonitor"), PersistentDataType.STRING)).findFirst();
                            String chairPath = "B_" + loc.getWorld().getName() + "_" + (loc.getBlockX() + "" + blockY + "" + loc.getBlockZ());
                            String towerPath = "B_" + loc.getWorld().getName() + "_" + (towerLoc.getBlockX() + "" + towerLoc.getBlockY() + "" + towerLoc.getBlockZ());
                            String keyboardPath = "B_" + loc.getWorld().getName() + "_" + (keyboardLoc.getBlockX() + "" + keyboardLoc.getBlockY() + "" + keyboardLoc.getBlockZ());
                            String mousePath = "B_" + loc.getWorld().getName() + "_" + (mouseLoc.getBlockX() + "" + mouseLoc.getBlockY() + "" + mouseLoc.getBlockZ());
                            if (Classified.get().contains(chairPath)) {
                                computerLoc.getBlock().setType(Material.AIR);
                                Classified.get().set(chairPath, null);
                            }
                            if (Classified.get().contains(towerPath)) {
                                towerLoc.getBlock().setType(Material.AIR);
                                Classified.get().set(towerPath, null);
                            }
                            if (Classified.get().contains(keyboardPath)) {
                                keyboardLoc.getBlock().setType(Material.AIR);
                                Classified.get().set(keyboardPath, null);
                            }
                            if (Classified.get().contains(mousePath)) {
                                mouseLoc.getBlock().setType(Material.AIR);
                                Classified.get().set(mousePath, null);
                            }
                            if (monitor.isPresent()) {
                                monitor.get().remove();
                            }
                            player.getVehicle().remove();
                            Classified.get().set(path, null);
                            Classified.save();
                            player.sendMessage(ChatColor.GREEN + "Successfully removed " + path);
                        } else {
                            player.sendMessage(ChatColor.YELLOW + "There is no computer tied to this location!");
                        }
                    } else {
                        player.sendMessage(ChatColor.YELLOW + "You can't ride an entity while doing this.");
                    }
                } else {
                    String path = "C_" + loc.getWorld().getName() + "_" + loc.getBlockX() + "" + loc.getBlockY() + "" + loc.getBlockZ();
                    if (Classified.get().contains(path)) {
                        player.stopSound("pmagisha.hdd-loop");
                        Location computerLoc = Classified.get().getLocation(path);
                        Location monitorLoc = Project_Magisha.calculateMonitorLoc(computerLoc, computerLoc.getYaw());
                        Location towerLoc = Project_Magisha.calculateTowerLoc(computerLoc, computerLoc.getYaw());
                        Location keyboardLoc = Project_Magisha.calculateKeyboardLoc(computerLoc, computerLoc.getYaw());
                        Location mouseLoc = Project_Magisha.calculateButtonLoc(computerLoc, computerLoc.getYaw());
                        Optional<Entity> monitor = loc.getWorld().getNearbyEntities(monitorLoc, 1, 1, 1).stream().filter(entity -> entity.getPersistentDataContainer().has(new NamespacedKey(Project_Magisha.getPlugin(), "isMonitor"), PersistentDataType.STRING)).findFirst();
                        Optional<Entity> eChair = loc.getWorld().getNearbyEntities(monitorLoc, 1, 1, 1).stream().filter(entity -> entity.getPersistentDataContainer().has(new NamespacedKey(Project_Magisha.getPlugin(), "isEChair"), PersistentDataType.STRING)).findFirst();
                        String chairPath = "B_" + loc.getWorld().getName() + "_" + (loc.getBlockX() + "" + loc.getBlockY() + "" + loc.getBlockZ());
                        String towerPath = "B_" + loc.getWorld().getName() + "_" + (towerLoc.getBlockX() + "" + towerLoc.getBlockY() + "" + towerLoc.getBlockZ());
                        String keyboardPath = "B_" + loc.getWorld().getName() + "_" + (keyboardLoc.getBlockX() + "" + keyboardLoc.getBlockY() + "" + keyboardLoc.getBlockZ());
                        String mousePath = "B_" + loc.getWorld().getName() + "_" + (mouseLoc.getBlockX() + "" + mouseLoc.getBlockY() + "" + mouseLoc.getBlockZ());
                        if (Classified.get().contains(chairPath)) {
                            computerLoc.getBlock().setType(Material.AIR);
                            Classified.get().set(chairPath, null);
                        }
                        if (Classified.get().contains(towerPath)) {
                            towerLoc.getBlock().setType(Material.AIR);
                            Classified.get().set(towerPath, null);
                        }
                        if (Classified.get().contains(keyboardPath)) {
                            keyboardLoc.getBlock().setType(Material.AIR);
                            Classified.get().set(keyboardPath, null);
                        }
                        if (Classified.get().contains(mousePath)) {
                            mouseLoc.getBlock().setType(Material.AIR);
                            Classified.get().set(mousePath, null);
                        }
                        if (monitor.isPresent()) {
                            monitor.get().remove();
                        }
                        if (eChair.isPresent()) {
                            eChair.get().remove();
                        }
                        Classified.get().set(path, null);
                        Classified.save();
                        player.sendMessage(ChatColor.GREEN + "Successfully removed " + path);
                    }
                }
            } else {
                player.sendMessage(ChatColor.RED + "Too many arguments.");
                player.sendMessage(ChatColor.YELLOW + "To remove a computer and it's peripherals, hop in the chair and enter: /removec");
            }
        }
        return true;
    }
}
