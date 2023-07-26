package com.acclash.magishacomputerdivision.utils;

import com.acclash.magishacomputerdivision.MagishaComputerDivision;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;

public class ComputerFunctions {

    //static Sound hddLoop;

    static BukkitTask task;

    public static void sendSpaceInput(Player player, Entity eChair) {
        if (!player.getVehicle().getPersistentDataContainer().has(new NamespacedKey(MagishaComputerDivision.getPlugin(), "isEChair"), PersistentDataType.STRING)) return;
        Bukkit.getScheduler().runTask(MagishaComputerDivision.getPlugin(), () -> {
            Location blockLoc = player.getLocation().getBlock().getLocation().add(0, 1, 0);
            String blockS = Serialization.serialize(blockLoc);
            try {
                String sql = "SELECT * FROM computers WHERE block_loc = '" + blockS + "'";
                ResultSet resultSet = MagishaComputerDivision.getPlugin().getDB().executeQuery(sql);
                if (resultSet.next()) {
                    //BlockFace blockFace = BlockFace.valueOf(resultSet.getString("block_face"));
                    Location monitorLoc = Serialization.deserialize(resultSet.getString("monitor_loc"));
                    Optional<Entity> monitor = blockLoc.getWorld().getNearbyEntities(monitorLoc, 1, 1, 1).stream().filter(entity -> entity.getPersistentDataContainer().has(new NamespacedKey(MagishaComputerDivision.getPlugin(), "isMonitor"), PersistentDataType.STRING)).findFirst();
                    if (monitor.isPresent()) {
                        ItemFrame fmonitor = (ItemFrame) monitor.get();
                        ItemStack screen = fmonitor.getItem();
                        if (screen.getType() == Material.FILLED_MAP) {
                            MapMeta screenMeta = (MapMeta) screen.getItemMeta();
                            if (screenMeta.getMapView().getId() == 5) {
                                startup(fmonitor, eChair);
                            } else if (screenMeta.getMapView().getId() == 2) {
                                shutdown(player, fmonitor, eChair);
                            }
                        } else {
                            player.sendMessage(ChatColor.YELLOW + "You weren't supposed to be able to break the screen! Either put a map in it or re-create the computer.");
                        }
                    } else {
                        player.sendMessage(ChatColor.YELLOW + "You weren't supposed to be able to break the monitor! You'll have to re-create the computer now!");
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static void startup(ItemFrame monitor, Entity echair) {
        ItemStack screen = monitor.getItem();
        MapMeta screenMeta = (MapMeta) screen.getItemMeta();
        MapView desktop = Bukkit.getServer().getMap(4);
        screenMeta.setMapView(desktop);
        screen.setItemMeta(screenMeta);
        monitor.setItem(screen);
        echair.getWorld().playSound(echair.getLocation(), "pmagisha.hdd-startup", 1, 1.0f);
        echair.getWorld().playSound(echair.getLocation(), "pmagisha.mssound", 1, 1.0f);
        BukkitScheduler scheduler = MagishaComputerDivision.getPlugin().getServer().getScheduler();
        scheduler.scheduleSyncDelayedTask(MagishaComputerDivision.getPlugin(), () -> desktop(monitor, echair), 253L);
    }

    public static void desktop(ItemFrame monitor, Entity eChair) {
        ItemStack screen = monitor.getItem();
        MapMeta screenMeta = (MapMeta) screen.getItemMeta();
        MapView desktop = Bukkit.getServer().getMap(2);
        screenMeta.setMapView(desktop);
        screen.setItemMeta(screenMeta);
        monitor.setItem(screen);
        //hddLoop = Sound.valueOf("pmagisha.hdd-loop");
        task = new BukkitRunnable() {
            @Override
            public void run() {
                if (screenMeta.getMapView() != null) {
                    if (screenMeta.getMapView().getId() == 2) {
                        eChair.getWorld().playSound(eChair.getLocation(), /*hddLoop*/"pmagisha.hdd-loop", 1, 1.0f);
                    } else {
                        cancel();
                    }
                }
            }
        }.runTaskTimer(MagishaComputerDivision.getPlugin(), 0, 125);
    }

    public static void shutdown(Player player, ItemFrame monitor, Entity eChair) {
        task.cancel();
        player.stopSound(/*hddLoop*/"pmagisha.hdd-loop");
        ItemStack screen = monitor.getItem();
        MapMeta screenMeta = (MapMeta) screen.getItemMeta();
        MapView desktop = Bukkit.getServer().getMap(4);
        screenMeta.setMapView(desktop);
        screen.setItemMeta(screenMeta);
        monitor.setItem(screen);
        eChair.getWorld().playSound(eChair.getLocation(), "pmagisha.hdd-shutdown", 1, 1.0f);
        BukkitScheduler scheduler = MagishaComputerDivision.getPlugin().getServer().getScheduler();
        scheduler.scheduleSyncDelayedTask(MagishaComputerDivision.getPlugin(), () -> {
            MapView desktop1 = Bukkit.getServer().getMap(5);
            screenMeta.setMapView(desktop1);
            screen.setItemMeta(screenMeta);
            monitor.setItem(screen);
        }, 204L);
    }
}
