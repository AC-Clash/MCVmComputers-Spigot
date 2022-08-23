package me.acclashcorporation.project_magisha.commands;

import me.acclashcorporation.project_magisha.MagishaMapRenderer;
import me.acclashcorporation.project_magisha.Project_Magisha;
import me.acclashcorporation.project_magisha.files.Classified;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class Create implements TabExecutor {

    MagishaMapRenderer mapRenderer;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender instanceof Player) {

            Player player = (Player) sender;

            if (args.length == 0) {
                player.sendMessage(ChatColor.RED + "You need to enter some arguments.");
                player.sendMessage(ChatColor.YELLOW + "To create a computer, stand where the chair should be, face where the monitor should be, and enter: /createc <type>");
            } else if (args.length == 1) {
                if (args[0].equals("Windows_95")) {
                    Location loc = player.getLocation();
                    if (!player.isInsideVehicle()) {
                        Location blockLoc = new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                        if (!Classified.get().contains("C_" + loc.getWorld().getName() + "_" + (loc.getBlockX() + "" + loc.getBlockY() + "" + loc.getBlockZ()))) {
                            if (Project_Magisha.areaCheck(blockLoc, loc.getYaw()).getBlock().getType() != Material.AIR) {
                                // Create chair
                                Block chair = blockLoc.getBlock();
                                chair.setType(Material.OAK_STAIRS);
                                Classified.get().set("B_" + loc.getWorld().getName() + "_" + (blockLoc.getBlockX() + "" + blockLoc.getBlockY() + "" + blockLoc.getBlockZ()), "isChair");
                                Directional cdir = (Directional) chair.getBlockData();
                                cdir.setFacing(Project_Magisha.getMonitorBlockFace(loc.getYaw()));
                                chair.setBlockData(cdir);
                                // Create eChair
                                Location eChairLoc = Project_Magisha.calculateEChairLoc(blockLoc, loc.getYaw());
                                LivingEntity eChair = (LivingEntity) player.getWorld().spawnEntity(eChairLoc, EntityType.CHICKEN);
                                eChair.getPersistentDataContainer().set(new NamespacedKey(Project_Magisha.getPlugin(), "isEChair"), PersistentDataType.STRING, "true");
                                eChair.setRotation(Project_Magisha.getRoundedYaw(loc.getYaw()), 0);
                                eChair.setAI(false);
                                eChair.setInvisible(true);
                                eChair.setInvulnerable(true);
                                eChair.setSilent(true);
                                // Create monitor and set the screen (map)
                                Location monitorLoc = Project_Magisha.calculateMonitorLoc(blockLoc, loc.getYaw());
                                Location fmonitorLoc = new Location(monitorLoc.getWorld(), monitorLoc.getBlockX(), loc.getBlockY() + 1, monitorLoc.getBlockZ());
                                ItemFrame monitor = (ItemFrame) loc.getWorld().spawnEntity(fmonitorLoc, EntityType.ITEM_FRAME);
                                monitor.getPersistentDataContainer().set(new NamespacedKey(Project_Magisha.getPlugin(), "isMonitor"), PersistentDataType.STRING, "true");
                                monitor.setFacingDirection(Project_Magisha.getMonitorBlockFace(loc.getYaw()), true);
                                ItemStack screen = new ItemStack(Material.FILLED_MAP);
                                MapMeta screenMeta = (MapMeta) screen.getItemMeta();
                                MapView mapView = Bukkit.getServer().getMap(5);
                                screenMeta.setMapView(mapView);
                                screen.setItemMeta(screenMeta);
                                monitor.setItem(screen);
                                // Create tower
                                Location towerLoc = Project_Magisha.calculateTowerLoc(blockLoc, loc.getYaw());
                                Block tower = towerLoc.getBlock();
                                tower.setType(Material.SANDSTONE_WALL);
                                Classified.get().set("B_" + loc.getWorld().getName() + "_" + (towerLoc.getBlockX() + "" + towerLoc.getBlockY() + "" + towerLoc.getBlockZ()), "isTower");
                                // Create keyboard
                                Location keyboardLoc = Project_Magisha.calculateKeyboardLoc(blockLoc, loc.getYaw());
                                Block keyboard = keyboardLoc.getBlock();
                                keyboard.setType(Material.HEAVY_WEIGHTED_PRESSURE_PLATE);
                                Classified.get().set("B_" + loc.getWorld().getName() + "_" + (keyboardLoc.getBlockX() + "" + keyboardLoc.getBlockY() + "" + keyboardLoc.getBlockZ()), "isKeyboard");
                                // Create mouse
                                Location mouseLoc = Project_Magisha.calculateButtonLoc(blockLoc, loc.getYaw());
                                Classified.get().set("B_" + loc.getWorld().getName() + "_" + (mouseLoc.getBlockX() + "" + mouseLoc.getBlockY() + "" + mouseLoc.getBlockZ()), "isMouse");
                                BlockData mouseData = Material.STONE_BUTTON.createBlockData("[face=floor]");
                                mouseLoc.getBlock().setBlockData(mouseData);
                                // Save computer location
                                Location pathLoc = new Location(blockLoc.getWorld(), blockLoc.getBlockX(), blockLoc.getBlockY(), blockLoc.getBlockZ(), loc.getYaw(), 0);
                                String path = "C_" + loc.getWorld().getName() + "_" + (loc.getBlockX() + "" + loc.getBlockY() + "" + loc.getBlockZ());
                                Classified.get().set(path, pathLoc);
                                Classified.save();
                                player.sendMessage(ChatColor.GREEN + "Successfully created " + path);
                            } else {
                                player.sendMessage(ChatColor.RED + "Unable to create computer. There would've been no block behind the monitor.");
                            }
                        } else {
                            player.sendMessage(ChatColor.RED + "Unable to create computer. You must fully remove the old one that was in it's place first by using /removec");
                        }
                    } else {
                        player.sendMessage(ChatColor.YELLOW + "You can't ride an entity while doing this.");
                    }
                } else if (args[0].equals("Windows_XP")) {
                    player.sendMessage(ChatColor.YELLOW + "Unfortunately, the Windows XP computer hasn't been made yet.");
                } else if (args[0].equals("Modern_Gaming_Rig")) {
                    player.sendMessage(ChatColor.YELLOW + "Unfortunately, the modern gaming rig hasn't been made yet.");
                } else {
                    player.sendMessage(ChatColor.RED + "That isn't a valid type of computer. Please check your spelling and try again.");
                }
            } else {
                player.sendMessage(ChatColor.RED + "Too many arguments.");
                player.sendMessage(ChatColor.YELLOW + "To create a computer, stand where the chair should be, face where the monitor should be, and enter: /createc <type>");
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> trainTypes = new ArrayList<>();
            trainTypes.add("Windows_95");
            trainTypes.add("Windows_XP");
            trainTypes.add("Modern_Gaming_Rig");

            return trainTypes;
        }
        return null;
    }
}
