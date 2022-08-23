package me.acclashcorporation.project_magisha.listeners;

import me.acclashcorporation.project_magisha.Project_Magisha;
import me.acclashcorporation.project_magisha.files.Classified;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.spigotmc.event.entity.EntityDismountEvent;
import org.spigotmc.event.entity.EntityMountEvent;

import java.util.Optional;

public class ComputerManager implements Listener {

    //static Sound hddLoop;
    static BukkitTask test;

    //private final HashMap<UUID, Long> cooldown;

    //public ComputerListener() {
    //    this.cooldown = new HashMap<>();
    //}

    // Mounts the player on the chair
    @EventHandler
    public void onRightClick(PlayerInteractEntityEvent e) {
        if (e.getRightClicked() instanceof LivingEntity entity) {
            if (entity.getPersistentDataContainer().has(new NamespacedKey(Project_Magisha.getPlugin(), "isEChair"), PersistentDataType.STRING)) {
                entity.addPassenger(e.getPlayer());
            }
        }
    }

    // If the player right-clicks on the block in general
    @EventHandler
    public void onRightClick(PlayerInteractEvent e) {
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (e.getHand().equals(EquipmentSlot.HAND)) {
                if (e.getMaterial() == Material.SANDSTONE_WALL || e.getMaterial() == Material.SPRUCE_STAIRS) {
                    Location loc = e.getClickedBlock().getLocation();
                    String path = "B_" + loc.getWorld().getName() + "_" + loc.getBlockX() + "" + loc.getBlockY() + "" + loc.getBlockZ();
                    if (Classified.get().getString(path) != null) {
                        if (Classified.get().getString(path).equals("isChair")) {
                            Entity eChair = loc.getWorld().getNearbyEntities(loc, 1, 1, 1).stream().filter(entity -> entity.getPersistentDataContainer().has(new NamespacedKey(Project_Magisha.getPlugin(), "isEChair"), PersistentDataType.STRING)).findFirst().get();
                            eChair.addPassenger(e.getPlayer());
                        } else if (Classified.get().getString(path).equals("isTower")) {
                            e.getPlayer().sendMessage("You right clicked the tower.");
                        }
                    }
                }
            }
        }
    }



    @EventHandler
    public void onChairEnter(EntityMountEvent e) {
        Player player = (Player) e.getEntity();
        if (e.getMount() instanceof LivingEntity entity) {
            if (entity.getPersistentDataContainer().has(new NamespacedKey(Project_Magisha.getPlugin(), "isEChair"), PersistentDataType.STRING)) {
                // Something here?
            } else if (e.getMount().getType() == EntityType.CREEPER) {

            }
        }
    }

    @EventHandler
    public void onChairExit(EntityDismountEvent e) {
        if (e.getDismounted() instanceof LivingEntity entity) {
            if (entity.getPersistentDataContainer().has(new NamespacedKey(Project_Magisha.getPlugin(), "isEChair"), PersistentDataType.STRING)) {
                // Something here?
            } else if (entity.getType() == EntityType.CREEPER) {
                e.setCancelled(true);
            }
        }
    }

    // Cancels the computer from being broken normally
    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        Location loc = e.getBlock().getLocation();
        String path = "B_" + loc.getWorld().getName() + "_" + loc.getBlockX() + "" + loc.getBlockY() + "" + loc.getBlockZ();
        if (Classified.get().contains(path)) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(ChatColor.YELLOW + "To remove the computer or it's peripherals properly, hop in the chair and enter: /removec");
        }
    }

    @EventHandler
    public void onItemFrameBreak(HangingBreakEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(new NamespacedKey(Project_Magisha.getPlugin(), "isMonitor"), PersistentDataType.STRING)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDamageEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(new NamespacedKey(Project_Magisha.getPlugin(), "isEChair"), PersistentDataType.STRING)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(new NamespacedKey(Project_Magisha.getPlugin(), "isEChair"), PersistentDataType.STRING) || e.getEntity().getPersistentDataContainer().has(new NamespacedKey(Project_Magisha.getPlugin(), "isMonitor"), PersistentDataType.STRING)) {
            e.setCancelled(true);
            e.getDamager().sendMessage(ChatColor.YELLOW + "To remove the computer or it's peripherals properly, hop in the chair and enter: /removec");
        }
    }

    // If the player manages to break a peripheral by breaking the block below it
    /*
    @EventHandler
    public void onBlockDrop(BlockDropItemEvent e) {
        Location loc = e.getItems().get(0).getLocation().clone();
        String path = "B_" + loc.getWorld().getName() + "_" + loc.getBlockX() + "" + loc.getBlockY() + "" + loc.getBlockZ();
        if (Classified.get().contains(path)) {
            Classified.get().set(path, null);
            Classified.save();
        }
    }
     */

    public static void sendSpaceInput(Player player) {
        if (player.getVehicle().getPersistentDataContainer().has(new NamespacedKey(Project_Magisha.getPlugin(), "isEChair"), PersistentDataType.STRING)) {
            Bukkit.getScheduler().runTask(Project_Magisha.getPlugin(), new Runnable() {
                @Override
                public void run() {
                    Location loc = player.getLocation();
                    String blockY = String.valueOf(loc.getBlockY() + 1);
                    String path = "C_" + loc.getWorld().getName() + "_" + loc.getBlockX() + "" + blockY + "" + loc.getBlockZ();
                    Location computerLoc = Classified.get().getLocation(path);
                    Location monitorLoc = Project_Magisha.calculateMonitorLoc(computerLoc, computerLoc.getYaw());
                    Optional<Entity> monitor = loc.getWorld().getNearbyEntities(monitorLoc, 1, 1, 1).stream().filter(entity -> entity.getPersistentDataContainer().has(new NamespacedKey(Project_Magisha.getPlugin(), "isMonitor"), PersistentDataType.STRING)).findFirst();
                    Optional<Entity> eChair = loc.getWorld().getNearbyEntities(computerLoc, 1, 1, 1).stream().filter(entity -> entity.getPersistentDataContainer().has(new NamespacedKey(Project_Magisha.getPlugin(), "isEChair"), PersistentDataType.STRING)).findFirst();
                    if (monitor.isPresent()) {
                        ItemFrame fmonitor = (ItemFrame) monitor.get();
                        ItemStack screen = fmonitor.getItem();
                        if (screen.getType() == Material.FILLED_MAP) {
                            MapMeta screenMeta = (MapMeta) screen.getItemMeta();
                            if (screenMeta.getMapView().getId() == 5) {
                                ComputerManager.startup(player, fmonitor, eChair.get());
                            } else if (screenMeta.getMapView().getId() == 2) {
                                ComputerManager.shutdown(player, fmonitor, eChair.get());
                            }
                        } else {
                            player.sendMessage(ChatColor.YELLOW + "You weren't supposed to be able to break the screen! Either put a map in it or re-create the computer.");
                        }
                    } else {
                        player.sendMessage(ChatColor.YELLOW + "You weren't supposed to be able to break the monitor! You'll have to re-create the computer now!");
                    }
                }
            });
        }
    }

    public static void startup(Player player, ItemFrame monitor, Entity echair) {
        ItemStack screen = monitor.getItem();
        MapMeta screenMeta = (MapMeta) screen.getItemMeta();
        MapView desktop = Bukkit.getServer().getMap(4);
        screenMeta.setMapView(desktop);
        screen.setItemMeta(screenMeta);
        monitor.setItem(screen);
        echair.getWorld().playSound(echair.getLocation(), "pmagisha.hdd-startup", 1, 1.0f);
        echair.getWorld().playSound(echair.getLocation(), "pmagisha.mssound", 1, 1.0f);
        BukkitScheduler scheduler = Project_Magisha.getPlugin().getServer().getScheduler();
        scheduler.scheduleSyncDelayedTask(Project_Magisha.getPlugin(), new Runnable() {
            @Override
            public void run() {
                ComputerManager.desktop(player, monitor, echair);
            }
        }, 253L);
    }

    public static void desktop(Player player, ItemFrame monitor, Entity eChair) {
        ItemStack screen = monitor.getItem();
        MapMeta screenMeta = (MapMeta) screen.getItemMeta();
        MapView desktop = Bukkit.getServer().getMap(2);
        screenMeta.setMapView(desktop);
        screen.setItemMeta(screenMeta);
        monitor.setItem(screen);
        //hddLoop = Sound.valueOf("pmagisha.hdd-loop");
        test = new BukkitRunnable() {
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
        }.runTaskTimer(Project_Magisha.getPlugin(), 0, 125);
    }

    public static void shutdown(Player player, ItemFrame monitor, Entity eChair) {
        test.cancel();
        player.stopSound(/*hddLoop*/"pmagisha.hdd-loop");
        ItemStack screen = monitor.getItem();
        MapMeta screenMeta = (MapMeta) screen.getItemMeta();
        MapView desktop = Bukkit.getServer().getMap(4);
        screenMeta.setMapView(desktop);
        screen.setItemMeta(screenMeta);
        monitor.setItem(screen);
        eChair.getWorld().playSound(eChair.getLocation(), "pmagisha.hdd-shutdown", 1, 1.0f);
        BukkitScheduler scheduler = Project_Magisha.getPlugin().getServer().getScheduler();
        scheduler.scheduleSyncDelayedTask(Project_Magisha.getPlugin(), new Runnable() {
            @Override
            public void run() {
                MapView desktop = Bukkit.getServer().getMap(5);
                screenMeta.setMapView(desktop);
                screen.setItemMeta(screenMeta);
                monitor.setItem(screen);
            }
        }, 204L);
    }
}
