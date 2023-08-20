package com.acclash.vmcomputers.sql;

import com.acclash.vmcomputers.VMComputers;

import java.util.logging.Level;

public class Error {
    public static void execute(VMComputers plugin, Exception ex){
        VMComputers.getPlugin().getLogger().log(Level.SEVERE, "Couldn't execute MySQL statement: ", ex);
    }
    public static void close(VMComputers plugin, Exception ex){
        VMComputers.getPlugin().getLogger().log(Level.SEVERE, "Failed to close MySQL connection: ", ex);
    }
}