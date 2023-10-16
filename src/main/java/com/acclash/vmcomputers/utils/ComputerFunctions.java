package com.acclash.vmcomputers.utils;

import com.acclash.vmcomputers.VMComputers;
import jdos.gui.Main;
import jdos.gui.MainFrame;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.v1_20_R2.map.CraftMapView;
import org.bukkit.craftbukkit.v1_20_R2.map.RenderData;
import org.bukkit.craftbukkit.v1_20_R2.util.CraftChatMessage;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.awt.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class ComputerFunctions {

    static HashMap<Integer, Integer> taskMap = new HashMap<>();

    static HashMap<Integer, MainFrame> frameMap = new HashMap<>();

    public static HashMap<Integer, MainFrame> getFrameMap() {
        return frameMap;
    }


}
