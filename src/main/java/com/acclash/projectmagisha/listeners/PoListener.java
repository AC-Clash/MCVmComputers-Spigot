package com.acclash.projectmagisha.listeners;

import org.bukkit.ChatColor;
import org.bukkit.entity.Panda;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;

public class PoListener implements Listener {

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent e) {

        if (e.getDamager() instanceof Player player &&  e.getEntity() instanceof Panda po && e.getEntity().getCustomName().equals(ChatColor.GOLD + "Po")) {

            player.sendMessage(ChatColor.YELLOW + "[" + ChatColor.GOLD + "Po" + ChatColor.YELLOW + "]" + ChatColor.GOLD + " Hey! That wasn't nice.");

            //po.setSitting(true);

        }

    }

    @EventHandler
    public void onGlide(EntityToggleGlideEvent e) {
        e.setCancelled(true);
    }

}
