package com.acclash.vmcomputers.sql;

import com.acclash.vmcomputers.VMComputers;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.logging.Level;

public class SQLite extends Database {
    String dbname = "hardware";

    public SQLite(VMComputers instance) {
        super(instance);
    }

    public String SQLiteCreateTokensTable = "CREATE TABLE IF NOT EXISTS computers (" + // make sure to put your table name in here too.
            "`id` INTEGER NOT NULL," + // This creates the different colums you will save data too. varchar(32) Is a string, int = integer
            "`type` varchar(50) NOT NULL," +
            "`block_loc` varchar(50) NOT NULL," +
            "`monitor_loc` varchar(50) NOT NULL," +
            "`tower_loc` varchar(50) NOT NULL," +
            "`keyboard_loc` varchar(50) NOT NULL," +
            "`button_loc` varchar(50) NOT NULL," +
            "`block_face` varchar(50) NOT NULL," +
            "`state` varchar(50) NOT NULL," +
            "PRIMARY KEY (`id`)" +  // This is creating 3 columns Player, Kills, Total. Primary key is what you are going to use as your indexer. Here we want to use player so
            ");"; // we can search by player, and get kills and total. If you somehow were searching kills it would provide total and player.

    // SQL creation stuff, You can leave the blow stuff untouched.
    public Connection getSQLConnection() {
        File dataFolder = VMComputers.getPlugin().getDataFolder();
        File newFolder =  new File("plugins" + File.separator + "vm_computers");
        File isoFolder =  new File(newFolder, "isos");
        File hddFolder =  new File(newFolder, "hdds");
        File dataFile = new File(newFolder, dbname + ".db");
        System.out.println(dataFolder);
        if (!newFolder.exists()) {
            newFolder.mkdir();
        }
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                VMComputers.getPlugin().getLogger().log(Level.SEVERE, "File write error: " + dbname + ".db");
            }
        }
        if (!isoFolder.exists()) {
            isoFolder.mkdir();
        }
        if (!hddFolder.exists()) {
            hddFolder.mkdir();
        }
        try {
            if (connection != null && !connection.isClosed()) {
                return connection;
            }
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dataFile);
            return connection;
        } catch (SQLException ex) {
            VMComputers.getPlugin().getLogger().log(Level.SEVERE, "SQLite exception on initialize", ex);
        } catch (ClassNotFoundException ex) {
            VMComputers.getPlugin().getLogger().log(Level.SEVERE, "You need the SQLite JBDC library. Google it. Put it in /lib folder.");
        }
        return null;
    }

    public void load() {
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
            VMComputers.getPlugin().getLogger().severe("Unable to execute query. FIX THIS ASAP!");
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
            VMComputers.getPlugin().getLogger().severe("Unable to execute query. FIX THIS ASAP!");
            e.printStackTrace(); // prints out SQLException errors to the console (if any)
        }
    }

    public boolean tableContainsValue(String value) {
        connection = getSQLConnection();
        try {
            // Get the column names in the table
            DatabaseMetaData metaData = connection.getMetaData();
            ResultSet columns = metaData.getColumns(null, null, "computers", null);

            // Build the SQL query dynamically
            StringBuilder queryBuilder = new StringBuilder("SELECT COUNT(*) FROM ");
            queryBuilder.append("`computers`");
            queryBuilder.append(" WHERE ");

            // Add conditions for each column
            boolean isFirstColumn = true;
            while (columns.next()) {
                String columnName = columns.getString("COLUMN_NAME");
                if (!isFirstColumn) {
                    queryBuilder.append(" OR ");
                }
                queryBuilder.append(columnName);
                queryBuilder.append(" LIKE ?");
                isFirstColumn = false;
            }

            // Create the prepared statement
            PreparedStatement statement = connection.prepareStatement(queryBuilder.toString());
            for (int i = 1; i <= columns.getRow(); i++) {
                statement.setString(i, "%" + value + "%");
            }

            // Execute the query
            ResultSet resultSet = statement.executeQuery();

            // Check if any rows match the query
            if (resultSet.next()) {
                int count = resultSet.getInt(1);
                return count > 0;
            }
            statement.close();
            resultSet.close();
        } catch (SQLException e) {
            VMComputers.getPlugin().getLogger().severe("Failed to check if table contains value");
            throw new RuntimeException();
        }

        return false;
    }
}