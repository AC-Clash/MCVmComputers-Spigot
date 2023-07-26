package com.acclash.magishacomputerdivision.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public class Serialization {

    public static String serialize(Location location) {
        String w = location.getWorld().getName();
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();

        return w + "," + x + "," + y + "," + z;
    }

    public static Location deserialize(String locS) {
        String[] split = locS.split(",");
        World w = Bukkit.getWorld(split[0]);
        double x = Double.parseDouble(split[1]);
        double y = Double.parseDouble(split[2]);
        double z = Double.parseDouble(split[3]);

        return new Location(w, x, y, z);
    }

}
