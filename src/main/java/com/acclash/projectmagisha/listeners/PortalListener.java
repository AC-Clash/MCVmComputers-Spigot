package com.acclash.projectmagisha.listeners;

import com.acclash.projectmagisha.ProjectMagisha;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class PortalListener implements Listener {

    @EventHandler
    public void onPortalEnter(PlayerPortalEvent e) {
        if (e.getFrom().getBlockX() == 233) {
            e.setTo(new Location(e.getPlayer().getWorld(), -120.5, 64, 105.5, -70, 0));
        } else if (e.getFrom().getBlockX() == -122) {
            e.setTo(new Location(e.getPlayer().getWorld(), 233.5, 113, -147.5, -150, 0));
        } else {
            e.setCancelled(true);
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                if (e.getPlayer().getGameMode() != GameMode.CREATIVE) {
                    e.getPlayer().setGameMode(GameMode.CREATIVE);
                }
            }
        }.runTaskLater(ProjectMagisha.getPlugin(), 2);
    }

    @EventHandler
    public void onPortalEnter(EntityPortalEvent e) {
        if (e.getFrom().getBlockX() == 233) {
            e.setTo(new Location(e.getEntity().getWorld(), -120.5, 64, 105.5, -70, 0));
        } else if (e.getFrom().getBlockX() == -122) {
            e.setTo(new Location(e.getEntity().getWorld(), 233.5, 113, -147.5, -150, 0));
        } else {
            e.setCancelled(true);
        }
    }
}
