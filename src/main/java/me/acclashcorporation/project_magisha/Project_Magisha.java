package me.acclashcorporation.project_magisha;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.FieldAccessException;
import me.acclashcorporation.project_magisha.commands.*;
import me.acclashcorporation.project_magisha.files.Classified;
import me.acclashcorporation.project_magisha.listeners.ComputerManager;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public final class Project_Magisha extends JavaPlugin implements Listener {
    private static Project_Magisha plugin;

    private ProtocolManager protocolManager;

    @Override
    public void onEnable() {
        Classified.setup();
        Classified.get().options().copyDefaults();
        Classified.save();

        protocolManager = ProtocolLibrary.getProtocolManager();
        plugin = this;
        getCommand("createc").setExecutor(new Create());
        getCommand("removec").setExecutor(new Remove());
        getCommand("testmap").setExecutor(new SampleCrap());
        getCommand("magisha").setExecutor(new Magisha());
        getCommand("armstest").setExecutor(new ArmSwingTest());
        getCommand("darpatrain").setExecutor(new DARPATrain());
        getServer().getPluginManager().registerEvents(new ComputerManager(), this);
        getServer().getPluginManager().registerEvents(this, this);

        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(this, PacketType.Play.Client.STEER_VEHICLE) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                Entity vehicle = event.getPlayer().getVehicle();
                if (vehicle == null) return;
                float swSpeed, fwSpeed;
                boolean jumping;
                boolean po;
                try {
                    swSpeed = event.getPacket().getFloat().read(0);
                    fwSpeed = event.getPacket().getFloat().read(1);
                    jumping = event.getPacket().getBooleans().read(0);

                } catch (FieldAccessException e) {
                    e.printStackTrace();
                    return;
                }
                if (vehicle.getPersistentDataContainer().has(new NamespacedKey(Project_Magisha.getPlugin(), "isDARPATrain"), PersistentDataType.STRING)) {
                    Location pLoc = event.getPlayer().getLocation().clone();
                    vehicle.setRotation(pLoc.getYaw(), 0); // PITCH COULD BE -- pLoc.getPitch()
                    Vector forwardDir = pLoc.getDirection();
                    Vector sideways = forwardDir.clone().crossProduct(new Vector(0, -1, 0));
                    Vector total = forwardDir.multiply(fwSpeed / 4).add(sideways.multiply(swSpeed / 4));
                    total.setY(0);
                    vehicle.setVelocity(vehicle.getVelocity().add(total));
                }
                //event.getPlayer().sendMessage("swSpeed: " + swSpeed);
                //event.getPlayer().sendMessage("fwSpeed: " + fwSpeed);
                //event.getPlayer().sendMessage("jumping: " + jumping);

                if (jumping) {
                    if (vehicle.getType() == EntityType.CHICKEN) {
                        ComputerManager.sendSpaceInput(event.getPlayer());
                    } else if (vehicle.getPersistentDataContainer().has(new NamespacedKey(Project_Magisha.getPlugin(), "isDARPATrain"), PersistentDataType.STRING)) {
                        vehicle.setVelocity(vehicle.getVelocity().setY(1));
                    }
                }
            }
        });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        player.setResourcePack("https://www.dropbox.com/s/gvovwkn4mw11muu/Project%20Magisha.zip?dl=1", null, true);
    }

    public static BlockFace getMonitorBlockFace(float yaw) {
        if (yaw >= 45.1 && yaw <= 135) {
            return BlockFace.EAST;
        } else if (yaw <= 45.0 && yaw >= -44.9) {
            return BlockFace.NORTH;
        } else if (yaw <= -45.0 && yaw >= -134.9) {
            return BlockFace.WEST;
        } else {
            return BlockFace.SOUTH;
        }
    }

    public static int getRoundedYaw(float yaw) {
        if (yaw >= 45.1 && yaw <= 135) {
            return 90;
        } else if (yaw <= 45.0 && yaw >= -44.9) {
            return 0;
        } else if (yaw <= -45.0 && yaw >= -134.9) {
            return -90;
        } else {
            return 180;
        }
    }

    public static Location calculateEChairLoc(Location location, float playerYaw) {
        if (Project_Magisha.getMonitorBlockFace(playerYaw) == BlockFace.SOUTH) {
            return new Location(location.getWorld(), location.getBlockX() + 0.5, location.getBlockY(), location.getBlockZ() + 0.33);
        } else if (Project_Magisha.getMonitorBlockFace(playerYaw) == BlockFace.EAST) {
            return new Location(location.getWorld(), location.getBlockX() + 0.33, location.getBlockY(), location.getBlockZ() + 0.5);
        } else if (Project_Magisha.getMonitorBlockFace(playerYaw) == BlockFace.NORTH) {
            return new Location(location.getWorld(), location.getBlockX() + 0.5, location.getBlockY(), location.getBlockZ() + 0.66);
        } else {
            return new Location(location.getWorld(), location.getBlockX() + 0.66, location.getBlockY(), location.getBlockZ() + 0.5);
        }
    }

    public static Location calculateMonitorLoc(Location location, float playerYaw) {
        if (Project_Magisha.getMonitorBlockFace(playerYaw) == BlockFace.SOUTH) {
            return new Location(location.getWorld(), location.getBlockX(), location.getBlockY() + 1, location.getBlockZ() - 1);
        } else if (Project_Magisha.getMonitorBlockFace(playerYaw) == BlockFace.EAST) {
            return new Location(location.getWorld(), location.getBlockX() - 1, location.getBlockY() + 1, location.getBlockZ());
        } else if (Project_Magisha.getMonitorBlockFace(playerYaw) == BlockFace.NORTH) {
            return new Location(location.getWorld(), location.getBlockX(), location.getBlockY() + 1, location.getBlockZ() + 1);
        } else {
            return new Location(location.getWorld(), location.getBlockX() + 1, location.getBlockY() + 1, location.getBlockZ());
        }
    }

    public static Location calculateButtonLoc(Location location, float playerYaw) {
        if (Project_Magisha.getMonitorBlockFace(playerYaw) == BlockFace.NORTH) {
            return new Location(location.getWorld(), location.getBlockX() - 1, location.getBlockY() + 1, location.getBlockZ() + 1);
        } else if (Project_Magisha.getMonitorBlockFace(playerYaw) == BlockFace.WEST) {
            return new Location(location.getWorld(), location.getBlockX() + 1, location.getBlockY() + 1, location.getBlockZ() + 1);
        } else if (Project_Magisha.getMonitorBlockFace(playerYaw) == BlockFace.SOUTH) {
            return new Location(location.getWorld(), location.getBlockX() + 1, location.getBlockY() + 1, location.getBlockZ() - 1);
        } else {
            return new Location(location.getWorld(), location.getBlockX() - 1, location.getBlockY() + 1, location.getBlockZ() - 1);
        }
    }

    public static Location calculateTowerLoc(Location location, float playerYaw) {
        if (Project_Magisha.getMonitorBlockFace(playerYaw) == BlockFace.NORTH) {
            return new Location(location.getWorld(), location.getBlockX() + 1, location.getBlockY() + 1, location.getBlockZ() + 1);
        } else if (Project_Magisha.getMonitorBlockFace(playerYaw) == BlockFace.WEST) {
            return new Location(location.getWorld(), location.getBlockX() + 1, location.getBlockY() + 1, location.getBlockZ() - 1);
        } else if (Project_Magisha.getMonitorBlockFace(playerYaw) == BlockFace.SOUTH) {
            return new Location(location.getWorld(), location.getBlockX() - 1, location.getBlockY() + 1, location.getBlockZ() - 1);
        } else {
            return new Location(location.getWorld(), location.getBlockX() - 1, location.getBlockY() + 1, location.getBlockZ() + 1);
        }
    }

    public static Location calculateKeyboardLoc(Location location, float playerYaw) {
        if (Project_Magisha.getMonitorBlockFace(playerYaw) == BlockFace.NORTH) {
            return new Location(location.getWorld(), location.getBlockX(), location.getBlockY() + 1, location.getBlockZ() + 1);
        } else if (Project_Magisha.getMonitorBlockFace(playerYaw) == BlockFace.WEST) {
            return new Location(location.getWorld(), location.getBlockX() + 1, location.getBlockY() + 1, location.getBlockZ());
        } else if (Project_Magisha.getMonitorBlockFace(playerYaw) == BlockFace.SOUTH) {
            return new Location(location.getWorld(), location.getBlockX(), location.getBlockY() + 1, location.getBlockZ() - 1);
        } else {
            return new Location(location.getWorld(), location.getBlockX() - 1, location.getBlockY() + 1, location.getBlockZ());
        }
    }

    public static Location areaCheck(Location location, float playerYaw) {
        if (Project_Magisha.getMonitorBlockFace(playerYaw) == BlockFace.SOUTH) {
            return new Location(location.getWorld(), location.getBlockX(), location.getBlockY() + 1, location.getBlockZ() - 2);
        } else if (Project_Magisha.getMonitorBlockFace(playerYaw) == BlockFace.EAST) {
            return new Location(location.getWorld(), location.getBlockX() - 2, location.getBlockY() + 1, location.getBlockZ());
        } else if (Project_Magisha.getMonitorBlockFace(playerYaw) == BlockFace.NORTH) {
            return new Location(location.getWorld(), location.getBlockX(), location.getBlockY() + 1, location.getBlockZ() + 2);
        } else {
            return new Location(location.getWorld(), location.getBlockX() + 2, location.getBlockY() + 1, location.getBlockZ());
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public static Project_Magisha getPlugin() {
        return plugin;
    }
}
