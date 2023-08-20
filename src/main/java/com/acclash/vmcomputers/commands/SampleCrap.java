package com.acclash.vmcomputers.commands;

import com.acclash.vmcomputers.utils.MagishaMapRenderer;
import jdos.gui.Main;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;

import java.awt.*;

public class SampleCrap {

    public static void giveMap(Image image) {
        Player player = Bukkit.getPlayer("MasterSlayer");
        if (player == null) return;

        ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
        MapMeta mapmeta = (MapMeta) mapItem.getItemMeta();

        MapView mapView = Bukkit.createMap(player.getWorld());

        MagishaMapRenderer mapRenderer = new MagishaMapRenderer(image);

        mapView.removeRenderer(mapView.getRenderers().get(0));
        mapView.addRenderer(mapRenderer);

        mapmeta.setMapView(mapView);
        mapItem.setItemMeta(mapmeta);

        player.getInventory().addItem(mapItem);
    }
}
