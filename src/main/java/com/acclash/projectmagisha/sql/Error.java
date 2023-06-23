package com.acclash.projectmagisha.sql;

import com.acclash.projectmagisha.ProjectMagisha;

import java.util.logging.Level;

public class Error {
    public static void execute(ProjectMagisha plugin, Exception ex){
        ProjectMagisha.getPlugin().getLogger().log(Level.SEVERE, "Couldn't execute MySQL statement: ", ex);
    }
    public static void close(ProjectMagisha plugin, Exception ex){
        ProjectMagisha.getPlugin().getLogger().log(Level.SEVERE, "Failed to close MySQL connection: ", ex);
    }
}