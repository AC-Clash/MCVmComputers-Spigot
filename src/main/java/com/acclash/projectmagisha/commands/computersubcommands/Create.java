package com.acclash.projectmagisha.commands.computersubcommands;

import com.acclash.projectmagisha.ProjectMagisha;
import com.acclash.projectmagisha.commands.ComputerSubCommand;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class Create extends ComputerSubCommand {

    @Override
    public String getName() {
        return "create";
    }

    @Override
    public String getDescription() {
        return "Creates a computer of the specified type.";
    }

    @Override
    public String getSyntax() {
        return ChatColor.GOLD + "To create a computer, stand where the chair should be, face where the monitor should be, and enter: /computer create <type>";
    }

    @Override
    public void perform(Player player, String[] args) {
        if (args.length == 1) {
            player.sendMessage(ChatColor.RED + "You need to enter some arguments.");
            player.sendMessage(getSyntax());
        } else if (args.length == 2) {
            switch (args[1]) {
                case "Dell_Dimension_L500R" -> {
                    Location loc = player.getLocation();
                    if (player.isInsideVehicle()) {
                        player.sendMessage(ChatColor.YELLOW + "You can't ride an entity while doing this.");
                        return;
                    }
                    Location blockLoc = loc.getBlock().getLocation();
                    try {
                        ResultSet resultSet = ProjectMagisha.getPlugin().getDB().executeQuery("SELECT * FROM `computers` WHERE block_loc = '" + blockLoc + "'");
                        if (!resultSet.next()) {
                            if (ProjectMagisha.areaCheck(blockLoc, loc.getYaw()).getBlock().getType() != Material.AIR) {
                                // Create chair
                                Block chair = loc.getBlock();
                                chair.setType(Material.OAK_STAIRS);
                                Directional cdir = (Directional) chair.getBlockData();
                                cdir.setFacing(ProjectMagisha.getMonitorBlockFace(loc.getYaw()));
                                chair.setBlockData(cdir);
                                // Create eChair
                                Location eChairLoc = ProjectMagisha.calculateEChairLoc(blockLoc, loc.getYaw());
                                LivingEntity eChair = (LivingEntity) player.getWorld().spawnEntity(eChairLoc, EntityType.CHICKEN);
                                eChair.getPersistentDataContainer().set(new NamespacedKey(ProjectMagisha.getPlugin(), "isEChair"), PersistentDataType.STRING, "true");
                                eChair.setRotation(ProjectMagisha.getRoundedYaw(loc.getYaw()), 0);
                                eChair.setAI(false);
                                eChair.setInvisible(true);
                                eChair.setInvulnerable(true);
                                eChair.setSilent(true);
                                // Create monitor and set the screen (map)
                                Location monitorLoc = ProjectMagisha.calculateMonitorLoc(blockLoc, loc.getYaw());
                                ItemFrame monitor = (ItemFrame) loc.getWorld().spawnEntity(monitorLoc, EntityType.ITEM_FRAME);
                                monitor.getPersistentDataContainer().set(new NamespacedKey(ProjectMagisha.getPlugin(), "isMonitor"), PersistentDataType.STRING, "true");
                                monitor.setFacingDirection(ProjectMagisha.getMonitorBlockFace(loc.getYaw()), true);
                                ItemStack screen = new ItemStack(Material.FILLED_MAP);
                                MapMeta screenMeta = (MapMeta) screen.getItemMeta();
                                MapView mapView = Bukkit.getServer().getMap(5);
                                screenMeta.setMapView(mapView);
                                screen.setItemMeta(screenMeta);
                                monitor.setItem(screen);
                                // Create tower
                                Location towerLoc = ProjectMagisha.calculateTowerLoc(blockLoc, loc.getYaw());
                                Block tower = towerLoc.getBlock();
                                tower.setType(Material.SANDSTONE_WALL);
                                // Create keyboard
                                Location keyboardLoc = ProjectMagisha.calculateKeyboardLoc(blockLoc, loc.getYaw());
                                Block keyboard = keyboardLoc.getBlock();
                                keyboard.setType(Material.HEAVY_WEIGHTED_PRESSURE_PLATE);
                                // Create mouse
                                Location mouseLoc = ProjectMagisha.calculateButtonLoc(blockLoc, loc.getYaw());
                                BlockData mouseData = Material.STONE_BUTTON.createBlockData("[face=floor]");
                                mouseLoc.getBlock().setBlockData(mouseData);
                                // Save computer
                                String sql2 = "INSERT INTO computers (type, world, block_loc, tower_loc, direction)" +
                                "VALUES ('Dell Dimension l500R', '" + loc.getWorld().getName() + "', '" + blockLoc + "', '" + towerLoc + "', '" + loc.getYaw() + "')";
                                ProjectMagisha.getPlugin().getDB().executeUpdate(sql2);
                                String sql3 = "SELECT `computer_id` FROM `computers` WHERE `block_loc` = '" + blockLoc + "'";
                                ResultSet resultSet1 = ProjectMagisha.getPlugin().getDB().executeQuery(sql3);
                                resultSet1.next();
                                player.sendMessage(ChatColor.GREEN + "Successfully created a Dell Dimension L500R with an ID of " + resultSet1.getInt("computer_id"));
                            } else {
                                player.sendMessage(ChatColor.RED + "Unable to create computer. There would've been no block behind the monitor.");
                            }
                        } else {
                            player.sendMessage(ChatColor.RED + "Unable to create computer. You must fully remove the old one that was in it's place first by using /removec");
                        }
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }

                }
                case "Compaq_Presario" ->
                        player.sendMessage(ChatColor.YELLOW + "Unfortunately, the Compaq Presario hasn't been made yet.");
                case "Modern_Gaming_Rig" ->
                        player.sendMessage(ChatColor.YELLOW + "Unfortunately, the modern gaming rig hasn't been made yet.");
                default ->
                        player.sendMessage(ChatColor.RED + "That isn't a valid type of computer. Please check your spelling and try again.");
            }
        } else {
            player.sendMessage(ChatColor.RED + "Too many arguments.");
            player.sendMessage(getSyntax());
        }

    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            List<String> compTypes = new ArrayList<>();
            compTypes.add("Dell_Dimension_L500R");
            compTypes.add("Compaq_Presario");
            compTypes.add("Modern_Gaming_Rig");

            return compTypes;
        }
        return null;
    }


}
