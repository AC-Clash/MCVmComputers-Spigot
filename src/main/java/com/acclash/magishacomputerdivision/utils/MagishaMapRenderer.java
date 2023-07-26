package com.acclash.magishacomputerdivision.utils;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.*;

public class MagishaMapRenderer extends MapRenderer {

    private final Image image;

    public MagishaMapRenderer(Image image) {
        this.image = image;
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {

        canvas.drawImage(0, 0, MapPalette.resizeImage(image));
        //canvas.drawText(0, 0, MinecraftFont.Font, "Test EL-5");

    }
}
