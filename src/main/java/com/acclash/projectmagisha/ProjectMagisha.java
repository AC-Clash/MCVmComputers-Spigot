package com.acclash.projectmagisha;

import com.acclash.projectmagisha.commands.*;
import com.acclash.projectmagisha.listeners.*;
import com.acclash.projectmagisha.sql.Database;
import com.acclash.projectmagisha.sql.SQLite;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class ProjectMagisha extends JavaPlugin implements Listener {
    private static ProjectMagisha plugin;

    private Database db;

    public Database getDB() {
        return db;
    }

    @Override
    public void onEnable() {
        plugin = this;
        Bukkit.getOnlinePlayers().forEach(InboundHandler::attach);
        getCommand("computer").setExecutor(new ComputerCM());
        getCommand("testmap").setExecutor(new SampleCrap());
        getCommand("givetestmap").setExecutor(new GiveSampleCrap());
        getCommand("magisha").setExecutor(new Magisha());
        getCommand("darpatrain").setExecutor(new DARPATrain());
        getCommand("serialize").setExecutor(new Serialize());
        getCommand("deserialize").setExecutor(new Deserialize());
        getCommand("armstest").setExecutor(new ArmSwingTest());
        getCommand("lightpo").setExecutor(new LightPO());
        getCommand("summonpo").setExecutor(new SummonPO());
        getCommand("fallingblocktest").setExecutor(new FallingBlockTest());
        getCommand("control").setExecutor(new Control());
        getServer().getPluginManager().registerEvents(new ComputerManager(), this);
        getServer().getPluginManager().registerEvents(new ControlListener(), this);
        getServer().getPluginManager().registerEvents(new PortalListener(), this);
        getServer().getPluginManager().registerEvents(new PoListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(), this);
        getServer().getPluginManager().registerEvents(new FallingBlockTest(), this);
        getServer().getPluginManager().registerEvents(this, this);

        this.db = new SQLite(this);
        this.db.load();
    }

    public Database getRDatabase() {
        return this.db;
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
        if (ProjectMagisha.getMonitorBlockFace(playerYaw) == BlockFace.SOUTH) {
            return new Location(location.getWorld(), location.getBlockX() + 0.5, location.getBlockY(), location.getBlockZ() + 0.33);
        } else if (ProjectMagisha.getMonitorBlockFace(playerYaw) == BlockFace.EAST) {
            return new Location(location.getWorld(), location.getBlockX() + 0.33, location.getBlockY(), location.getBlockZ() + 0.5);
        } else if (ProjectMagisha.getMonitorBlockFace(playerYaw) == BlockFace.NORTH) {
            return new Location(location.getWorld(), location.getBlockX() + 0.5, location.getBlockY(), location.getBlockZ() + 0.66);
        } else {
            return new Location(location.getWorld(), location.getBlockX() + 0.66, location.getBlockY(), location.getBlockZ() + 0.5);
        }
    }

    public static Location calculateMonitorLoc(Location location, float playerYaw) {
        if (ProjectMagisha.getMonitorBlockFace(playerYaw) == BlockFace.SOUTH) {
            return new Location(location.getWorld(), location.getBlockX(), location.getBlockY() + 1, location.getBlockZ() - 1);
        } else if (ProjectMagisha.getMonitorBlockFace(playerYaw) == BlockFace.EAST) {
            return new Location(location.getWorld(), location.getBlockX() - 1, location.getBlockY() + 1, location.getBlockZ());
        } else if (ProjectMagisha.getMonitorBlockFace(playerYaw) == BlockFace.NORTH) {
            return new Location(location.getWorld(), location.getBlockX(), location.getBlockY() + 1, location.getBlockZ() + 1);
        } else {
            return new Location(location.getWorld(), location.getBlockX() + 1, location.getBlockY() + 1, location.getBlockZ());
        }
    }

    public static Location calculateButtonLoc(Location location, float playerYaw) {
        if (ProjectMagisha.getMonitorBlockFace(playerYaw) == BlockFace.NORTH) {
            return new Location(location.getWorld(), location.getBlockX() - 1, location.getBlockY() + 1, location.getBlockZ() + 1);
        } else if (ProjectMagisha.getMonitorBlockFace(playerYaw) == BlockFace.WEST) {
            return new Location(location.getWorld(), location.getBlockX() + 1, location.getBlockY() + 1, location.getBlockZ() + 1);
        } else if (ProjectMagisha.getMonitorBlockFace(playerYaw) == BlockFace.SOUTH) {
            return new Location(location.getWorld(), location.getBlockX() + 1, location.getBlockY() + 1, location.getBlockZ() - 1);
        } else {
            return new Location(location.getWorld(), location.getBlockX() - 1, location.getBlockY() + 1, location.getBlockZ() - 1);
        }
    }

    public static Location calculateTowerLoc(Location location, float playerYaw) {
        if (ProjectMagisha.getMonitorBlockFace(playerYaw) == BlockFace.NORTH) {
            return new Location(location.getWorld(), location.getBlockX() + 1, location.getBlockY() + 1, location.getBlockZ() + 1);
        } else if (ProjectMagisha.getMonitorBlockFace(playerYaw) == BlockFace.WEST) {
            return new Location(location.getWorld(), location.getBlockX() + 1, location.getBlockY() + 1, location.getBlockZ() - 1);
        } else if (ProjectMagisha.getMonitorBlockFace(playerYaw) == BlockFace.SOUTH) {
            return new Location(location.getWorld(), location.getBlockX() - 1, location.getBlockY() + 1, location.getBlockZ() - 1);
        } else {
            return new Location(location.getWorld(), location.getBlockX() - 1, location.getBlockY() + 1, location.getBlockZ() + 1);
        }
    }

    public static Location calculateKeyboardLoc(Location location, float playerYaw) {
        if (ProjectMagisha.getMonitorBlockFace(playerYaw) == BlockFace.NORTH) {
            return new Location(location.getWorld(), location.getBlockX(), location.getBlockY() + 1, location.getBlockZ() + 1);
        } else if (ProjectMagisha.getMonitorBlockFace(playerYaw) == BlockFace.WEST) {
            return new Location(location.getWorld(), location.getBlockX() + 1, location.getBlockY() + 1, location.getBlockZ());
        } else if (ProjectMagisha.getMonitorBlockFace(playerYaw) == BlockFace.SOUTH) {
            return new Location(location.getWorld(), location.getBlockX(), location.getBlockY() + 1, location.getBlockZ() - 1);
        } else {
            return new Location(location.getWorld(), location.getBlockX() - 1, location.getBlockY() + 1, location.getBlockZ());
        }
    }

    public static Location areaCheck(Location location, float playerYaw) {
        if (ProjectMagisha.getMonitorBlockFace(playerYaw) == BlockFace.SOUTH) {
            return new Location(location.getWorld(), location.getBlockX(), location.getBlockY() + 1, location.getBlockZ() - 2);
        } else if (ProjectMagisha.getMonitorBlockFace(playerYaw) == BlockFace.EAST) {
            return new Location(location.getWorld(), location.getBlockX() - 2, location.getBlockY() + 1, location.getBlockZ());
        } else if (ProjectMagisha.getMonitorBlockFace(playerYaw) == BlockFace.NORTH) {
            return new Location(location.getWorld(), location.getBlockX(), location.getBlockY() + 1, location.getBlockZ() + 2);
        } else {
            return new Location(location.getWorld(), location.getBlockX() + 2, location.getBlockY() + 1, location.getBlockZ());
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        Bukkit.getOnlinePlayers().forEach(InboundHandler::detach);
    }

    public static ProjectMagisha getPlugin() {
        return plugin;
    }
}
