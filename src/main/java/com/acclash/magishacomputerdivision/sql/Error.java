package com.acclash.magishacomputerdivision.sql;

import com.acclash.magishacomputerdivision.MagishaComputerDivision;

import java.util.logging.Level;

public class Error {
    public static void execute(MagishaComputerDivision plugin, Exception ex){
        MagishaComputerDivision.getPlugin().getLogger().log(Level.SEVERE, "Couldn't execute MySQL statement: ", ex);
    }
    public static void close(MagishaComputerDivision plugin, Exception ex){
        MagishaComputerDivision.getPlugin().getLogger().log(Level.SEVERE, "Failed to close MySQL connection: ", ex);
    }
}