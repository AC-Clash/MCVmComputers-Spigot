package com.acclash.projectmagisha.sql;

import com.acclash.projectmagisha.ProjectMagisha;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.logging.Level;

public class SQLite extends Database {
    String dbname;

    public SQLite(ProjectMagisha instance) {
        super(instance);
        dbname = "computers";
    }

    public String SQLiteCreateTokensTable = "CREATE TABLE IF NOT EXISTS computers (" + // make sure to put your table name in here too.
            "`computer_id` INTEGER NOT NULL," + // This creates the different colums you will save data too. varchar(32) Is a string, int = integer
            "`type` varchar(50) NOT NULL," +
            "`world` varchar(50) NOT NULL," +
            "`block_loc` varchar(50) NOT NULL," +
            "`tower_loc` varchar(50) NOT NULL," +
            "`direction` float NOT NULL," +
            "PRIMARY KEY (`computer_id`)" +  // This is creating 3 columns Player, Kills, Total. Primary key is what you are going to use as your indexer. Here we want to use player so
            ");"; // we can search by player, and get kills and total. If you somehow were searching kills it would provide total and player.

    // SQL creation stuff, You can leave the blow stuff untouched.
    public Connection getSQLConnection() {
        File dataFolder = ProjectMagisha.getPlugin().getDataFolder();
        File dataFile = new File(ProjectMagisha.getPlugin().getDataFolder(), dbname + ".db");
        if (!dataFolder.exists()) {
            dataFolder.mkdir();
        }
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                ProjectMagisha.getPlugin().getLogger().log(Level.SEVERE, "File write error: " + dbname + ".db");
            }
        }
        try {
            if (connection != null && !connection.isClosed()) {
                return connection;
            }
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dataFile);
            return connection;
        } catch (SQLException ex) {
            ProjectMagisha.getPlugin().getLogger().log(Level.SEVERE, "SQLite exception on initialize", ex);
        } catch (ClassNotFoundException ex) {
            ProjectMagisha.getPlugin().getLogger().log(Level.SEVERE, "You need the SQLite JBDC library. Google it. Put it in /lib folder.");
        }
        return null;
    }

    public void load() {
        ProjectMagisha.getPlugin().getLogger().severe("This version of VM Computers is deprecated. Please use the version in the Magisha Computer Division");
        connection = getSQLConnection();
        try {
            Statement s = connection.createStatement();
            s.executeUpdate(SQLiteCreateTokensTable);
            s.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        initialize();
    }


    public ResultSet executeQuery(String command) {
        connection = getSQLConnection();
        ResultSet resultSet = null;
        try {
            Statement stmt = connection.createStatement();
            resultSet = stmt.executeQuery(command);
        } catch (SQLException e) {
            ProjectMagisha.getPlugin().getLogger().severe("Unable to execute query. FIX THIS ASAP!");
            e.printStackTrace(); // prints out SQLException errors to the console (if any)
        }
        return resultSet;
    }

    public void executeUpdate(String command) {
        connection = getSQLConnection();
        try {
            Statement stmt = connection.createStatement();
            stmt.executeUpdate(command);
        } catch (SQLException e) {
            ProjectMagisha.getPlugin().getLogger().severe("Unable to execute query. FIX THIS ASAP!");
            e.printStackTrace(); // prints out SQLException errors to the console (if any)
        }
    }
}