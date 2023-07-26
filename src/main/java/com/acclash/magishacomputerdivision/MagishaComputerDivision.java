package com.acclash.magishacomputerdivision;

import com.acclash.magishacomputerdivision.commands.ComputerCM;
import com.acclash.magishacomputerdivision.commands.ResetDB;
import com.acclash.magishacomputerdivision.commands.Tets;
import com.acclash.magishacomputerdivision.listeners.ClickListener;
import com.acclash.magishacomputerdivision.listeners.PlayerListener;
import com.acclash.magishacomputerdivision.listeners.PreventionListener;
import com.acclash.magishacomputerdivision.net.InboundHandler;
import com.acclash.magishacomputerdivision.sql.Database;
import com.acclash.magishacomputerdivision.sql.SQLite;
import org.bukkit.Bukkit;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

public final class MagishaComputerDivision extends JavaPlugin {

    static MagishaComputerDivision plugin;

    public static MagishaComputerDivision getPlugin() {
        return plugin;
    }

    private Database db;

    public Database getDB() {
        return db;
    }

    @Override
    public void onEnable() {
        // Plugin startup logic
        plugin = this;
        getCommand("test").setExecutor(new Tets());
        getCommand("computer").setExecutor(new ComputerCM());
        getCommand("resetdb").setExecutor(new ResetDB());
        getServer().getPluginManager().registerEvents(new ClickListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(), this);
        getServer().getPluginManager().registerEvents(new PreventionListener(), this);
        Bukkit.getOnlinePlayers().forEach(InboundHandler::attach);

        this.db = new SQLite(this);
        this.db.load();
    }

    public Database getRDatabase() {
        return this.db;
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        try {
            getDB().getSQLConnection().close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        Bukkit.getOnlinePlayers().forEach(InboundHandler::detach);
    }
}
