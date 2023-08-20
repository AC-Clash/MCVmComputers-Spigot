package com.acclash.vmcomputers;

import com.acclash.vmcomputers.commands.ComputerCM;
import com.acclash.vmcomputers.commands.ResetDB;
import com.acclash.vmcomputers.commands.SampleCrap;
import com.acclash.vmcomputers.commands.Tets;
import com.acclash.vmcomputers.listeners.ClickListener;
import com.acclash.vmcomputers.listeners.PlayerListener;
import com.acclash.vmcomputers.listeners.PreventionListener;
import com.acclash.vmcomputers.net.InboundHandler;
import com.acclash.vmcomputers.sql.Database;
import com.acclash.vmcomputers.sql.SQLite;
import com.acclash.vmcomputers.utils.ComputerFunctions;
import jdos.gui.MainFrame;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

public final class VMComputers extends JavaPlugin {

    static VMComputers plugin;

    public static VMComputers getPlugin() {
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
