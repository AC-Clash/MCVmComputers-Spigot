/*
package com.acclash.projectmagisha.commands;

import com.acclash.projectmagisha.util.MagishaMapRenderer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SampleCrapVideo implements TabExecutor {

    private MagishaMapRenderer mapRenderer;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender instanceof Player player) {

            if (args.length > 0) {
                long interval;
                if (args[0].equalsIgnoreCase("30")) {
                    interval = 33333333;
                    ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
                    MapMeta mapmeta = (MapMeta) mapItem.getItemMeta();

                    Objects.requireNonNull(mapmeta).setDisplayName(ChatColor.AQUA + "Test Map");

                    MapView mapView = Bukkit.createMap(player.getWorld());

                    ArrayList<BufferedImage> frames = new ArrayList<>();
                    try {
                        File videoFile = new File("Storm - 106630.mp4");
                        FFmpegFrameGrabber frameGrabber = new FFmpegFrameGrabber(videoFile.getAbsoluteFile());
                        frameGrabber.start();
                        Java2DFrameConverter c = new Java2DFrameConverter();
                        int frameCount = frameGrabber.getLengthInFrames();
                        for (int frameNumber = 0; frameNumber < frameCount; frameNumber++) {
                            System.out.println("Extracting " + String.format("%04d", frameNumber) + " of " + String.format("%04d", frameCount) + " frames");
                            frameGrabber.setFrameNumber(frameNumber);
                            Frame f = frameGrabber.grab();
                            BufferedImage bi = c.convert(f);
                            frames.add(bi);
                            ImageIO.write(bi, "png", new File("Frame " + String.format("%04d", frameNumber) + "-" + String.format("%04d", frameCount) + ".png"));
                        }
                        frameGrabber.stop();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    player.getInventory().addItem(mapItem);

                    for (BufferedImage image : frames) {
                        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

                        Runnable task = () -> {
                            mapRenderer = new MagishaMapRenderer(image);

                            mapView.removeRenderer(mapView.getRenderers().get(0));
                            mapView.addRenderer(mapRenderer);

                            mapmeta.setMapView(mapView);
                            mapItem.setItemMeta(mapmeta);
                        };

                        executor.scheduleAtFixedRate(task, 0, interval, TimeUnit.NANOSECONDS);
                    }
                    //URL url = new URL("https://media-be.chewy.com/wp-content/uploads/2021/05/27140116/Pug_FeaturedImage.jpg");

                    player.sendMessage(ChatColor.GREEN + "Successfully created a test map with the ID of " + mapmeta.getMapView().getId());
                } else if (args[0].equalsIgnoreCase("60")) {
                    interval = 16666666;
                    ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
                    MapMeta mapmeta = (MapMeta) mapItem.getItemMeta();

                    Objects.requireNonNull(mapmeta).setDisplayName(ChatColor.AQUA + "Test Map");

                    MapView mapView = Bukkit.createMap(player.getWorld());

                    ArrayList<BufferedImage> frames = new ArrayList<BufferedImage>();
                    try {
                        File videoFile = new File("Storm - 106630.mp4");
                        FFmpegFrameGrabber frameGrabber = new FFmpegFrameGrabber(videoFile.getAbsoluteFile());
                        frameGrabber.start();
                        Java2DFrameConverter c = new Java2DFrameConverter();
                        int frameCount = frameGrabber.getLengthInFrames();
                        for (int frameNumber = 0; frameNumber < frameCount; frameNumber++) {
                            System.out.println("Extracting " + String.format("%04d", frameNumber) + " of " + String.format("%04d", frameCount) + " frames");
                            frameGrabber.setFrameNumber(frameNumber);
                            Frame f = frameGrabber.grab();
                            BufferedImage bi = c.convert(f);
                            frames.add(bi);
                            ImageIO.write(bi, "png", new File("Frame " + String.format("%04d", frameNumber) + "-" + String.format("%04d", frameCount) + ".png"));
                        }
                        frameGrabber.stop();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    player.getInventory().addItem(mapItem);

                    for (BufferedImage image : frames) {
                        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

                        Runnable task = () -> {
                            mapRenderer = new MagishaMapRenderer(image);

                            mapView.removeRenderer(mapView.getRenderers().get(0));
                            mapView.addRenderer(mapRenderer);

                            mapmeta.setMapView(mapView);
                            mapItem.setItemMeta(mapmeta);
                        };

                        executor.scheduleAtFixedRate(task, 0, interval, TimeUnit.NANOSECONDS);
                    }
                    //URL url = new URL("https://media-be.chewy.com/wp-content/uploads/2021/05/27140116/Pug_FeaturedImage.jpg");

                    player.sendMessage(ChatColor.GREEN + "Successfully created a test map with the ID of " + mapmeta.getMapView().getId());
                } else {
                    player.sendMessage(ChatColor.RED + "Please specify either 30 or 60 frames per second and try again");
                }

            } else {
                player.sendMessage(ChatColor.RED + "Please specify the frames per second and try again");
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 1) {
            List<String> fps = new ArrayList<>();
            fps.add("30");
            fps.add("60");

            return fps;
        }
        return null;
    }
}
 */
