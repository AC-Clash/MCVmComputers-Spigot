package com.acclash.vmcomputers.utils;

import com.acclash.vmcomputers.VMComputers;
import jdos.gui.Main;
import jdos.gui.MainFrame;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.awt.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Optional;

public class ComputerFunctions {

    static HashMap<Integer, Integer> taskMap = new HashMap<>();

    static HashMap<Integer, MainFrame> frameMap = new HashMap<>();

    public static HashMap<Integer, MainFrame> getFrameMap() {
        return frameMap;
    }

    public static void sendSpaceInput(Player player) {
            Location blockLoc = player.getLocation().getBlock().getLocation().add(0, 1, 0);
            String blockS = Serialization.serialize(blockLoc);
            try {
                String sql = "SELECT * FROM computers WHERE block_loc = '" + blockS + "'";
                ResultSet resultSet = VMComputers.getPlugin().getDB().executeQuery(sql);
                if (resultSet.next()) {
                    int id = resultSet.getInt("id");
                    if (resultSet.getString("state").equals("OFF")) {
                        // Turn on PC
                        if (resultSet.getString("type").equals("Dell Dimension l500R")) {
                            String sql2 = "UPDATE computers SET state = 'ON' WHERE id = " + id + ";";
                            VMComputers.getPlugin().getDB().executeUpdate(sql2);
                            BukkitTask task = Bukkit.getScheduler().runTask(VMComputers.getPlugin(), () -> {
                                String[] args = {};
                                MainFrame mainFrame = new MainFrame();
                                Main.main(mainFrame, args);
                                frameMap.put(id, mainFrame);
                            });
                            taskMap.put(id, task.getTaskId());
                        } else {
                            player.sendMessage("Not implemented yet");
                        }
                    } else {
                        // Turn off PC
                        System.out.println("Turn off?");
                        if (resultSet.getString("type").equals("Dell Dimension l500R")) {
                            String sql2 = "UPDATE computers SET state = 'OFF' WHERE id = " + id + ";";
                            VMComputers.getPlugin().getDB().executeUpdate(sql2);
                            Bukkit.getScheduler().cancelTask(taskMap.get(id));
                            taskMap.put(id, null);
                        } else {
                            player.sendMessage("Not implemented yet");
                        }
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
    }

    public static HashMap<Integer, Integer> getTaskMap() {
        return taskMap;
    }

    public static void refreshImage(int id, Image image) {
        Bukkit.getScheduler().callSyncMethod(VMComputers.getPlugin(), () -> {
            try {
                String sql = "SELECT * FROM computers WHERE id = '" + id + "'";
                ResultSet resultSet = VMComputers.getPlugin().getDB().executeQuery(sql);
                Location monitorLoc = Serialization.deserialize(resultSet.getString("monitor_loc"));
                Optional<Entity> monitor = monitorLoc.getWorld().getNearbyEntities(monitorLoc, 1, 1, 1).stream().filter(entity -> entity.getPersistentDataContainer().has(new NamespacedKey(VMComputers.getPlugin(), "isMonitor"), PersistentDataType.STRING)).findFirst();
                if (resultSet.next()) {
                    if (resultSet.getString("state").equals("OFF")) {
                        VMComputers.getPlugin().getLogger().severe("This wasn't supposed to happen. Shutting down now...");
                        if (taskMap.get(id) != null) {
                            Bukkit.getScheduler().cancelTask(taskMap.get(id));
                            taskMap.put(id, null);
                        }
                    } else {
                        if (monitor.isPresent()) {
                            ItemFrame fmonitor = (ItemFrame) monitor.get();
                            ItemStack screen = fmonitor.getItem();
                            MapMeta screenMeta = (MapMeta) screen.getItemMeta();
                            MapView mapView = screenMeta.getMapView();

                            MagishaMapRenderer mapRenderer = new MagishaMapRenderer(image);

                            mapView.getRenderers().clear();
                            mapView.addRenderer(mapRenderer);

                            screenMeta.setMapView(mapView);
                            screen.setItemMeta(screenMeta);
                            fmonitor.setItem(screen);
                        } else {
                            Bukkit.broadcastMessage(ChatColor.YELLOW + "You weren't supposed to be able to break the monitor! You'll have to re-create the computer now!");
                            if (taskMap.get(id) != null) {
                                Bukkit.getScheduler().cancelTask(taskMap.get(id));
                                taskMap.put(id, null);
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }
}
