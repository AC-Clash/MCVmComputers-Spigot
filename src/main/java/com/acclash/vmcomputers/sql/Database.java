package com.acclash.vmcomputers.sql;

import com.acclash.vmcomputers.VMComputers;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;

public abstract class Database {
    VMComputers plugin;
    Connection connection;
    public Database(VMComputers instance){
        plugin = instance;
    }

    public abstract Connection getSQLConnection();

    public abstract void load();

    public abstract ResultSet executeQuery(String command);

    public abstract void executeUpdate(String command);

    public abstract boolean tableContainsValue(String value);

    public void initialize(){
        connection = getSQLConnection();
    }


    public void close(PreparedStatement ps, ResultSet rs) {
        try {
            if (ps != null)
                ps.close();
            if (rs != null)
                rs.close();
        } catch (SQLException ex) {
            Error.close(plugin, ex);
        }
    }
}
