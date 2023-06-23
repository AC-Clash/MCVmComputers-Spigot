package com.acclash.projectmagisha.files;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class Classified {

    private static File file;
    private static FileConfiguration classifiedConfig;

    public static void setup() {
        file = new File(Bukkit.getServer().getPluginManager().getPlugin("Project_Magisha").getDataFolder(), "classified.yml");

        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                Bukkit.getLogger().severe("Unable to create classified.yml");
            }
        }
        classifiedConfig = YamlConfiguration.loadConfiguration(file);
    }
    public static FileConfiguration get() {
        return classifiedConfig;
    }

    public static void save() {
        try {
            classifiedConfig.save(file);
        } catch (IOException e) {
            Bukkit.getLogger().severe("Unable to save classified.yml");
        }
    }

    public static void reload() {
        classifiedConfig = YamlConfiguration.loadConfiguration(file);
    }
}
