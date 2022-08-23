package me.acclashcorporation.project_magisha.commands;

import me.acclashcorporation.project_magisha.MagishaMapRenderer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;

public class SampleCrap implements CommandExecutor {

    private MagishaMapRenderer mapRenderer;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender instanceof Player) {

            Player player = (Player) sender;

            ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
            MapMeta mapmeta = (MapMeta) mapItem.getItemMeta();

            MapView mapView = Bukkit.createMap(player.getWorld());

            try {
                URL url = new URL("https://media-be.chewy.com/wp-content/uploads/2021/05/27140116/Pug_FeaturedImage.jpg");
                BufferedImage image = ImageIO.read(url);
                mapRenderer = new MagishaMapRenderer(image);
            } catch (IOException ex) {
                ex.printStackTrace();
            }

            mapView.removeRenderer(mapView.getRenderers().get(0));
            mapView.addRenderer(mapRenderer);

            mapmeta.setMapView(mapView);
            mapItem.setItemMeta(mapmeta);

            player.getInventory().addItem(mapItem);

        }
        return true;
    }
}
