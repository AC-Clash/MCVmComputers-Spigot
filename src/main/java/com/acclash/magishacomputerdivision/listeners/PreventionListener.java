package com.acclash.magishacomputerdivision.listeners;

import com.acclash.magishacomputerdivision.MagishaComputerDivision;
import com.acclash.magishacomputerdivision.utils.Serialization;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class PreventionListener implements Listener {


    // Cancels the computer from being broken normally
    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        Location blockLoc = e.getBlock().getLocation();
        String blockS = Serialization.serialize(blockLoc);
        if (MagishaComputerDivision.getPlugin().getDB().tableContainsValue(blockS)) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(ChatColor.YELLOW + "To remove the computer or it's peripherals properly, hop in the chair and enter: /computer remove");
            return;
        }
        Location componentLoc = e.getBlock().getRelative(BlockFace.UP).getLocation();
        Material component = componentLoc.getBlock().getType();
        BlockData componentData = componentLoc.getBlock().getBlockData();
        String componentS = Serialization.serialize(componentLoc);
        if (MagishaComputerDivision.getPlugin().getDB().tableContainsValue(componentS)) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    componentLoc.getBlock().setType(component);
                    componentLoc.getBlock().setBlockData(componentData);
                }
            }.runTaskLater(MagishaComputerDivision.getPlugin(), 1);
            //e.getPlayer().sendMessage(ChatColor.YELLOW + "To remove the coeifjefheor it's peripherals properly, hop in the chair and enter: /computer remove");
        }
    }

    @EventHandler
    public void onItemFrameBreak(HangingBreakEvent e) {
        Location blockLoc = e.getEntity().getLocation().getBlock().getLocation();
        String blockS = Serialization.serialize(blockLoc);
        if (MagishaComputerDivision.getPlugin().getDB().tableContainsValue(blockS)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDamageByBlockEvent e) {
        Location blockLoc = e.getEntity().getLocation().getBlock().getLocation();
        String blockS = Serialization.serialize(blockLoc);
        if (MagishaComputerDivision.getPlugin().getDB().tableContainsValue(blockS)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        Location blockLoc = e.getEntity().getLocation().getBlock().getLocation();
        String blockS = Serialization.serialize(blockLoc);
        if (MagishaComputerDivision.getPlugin().getDB().tableContainsValue(blockS)) {
            e.setCancelled(true);
            e.getDamager().sendMessage(ChatColor.YELLOW + "To remove the computer or it's peripherals properly, hop in the chair and enter: /computer remove");
        }
    }

    @EventHandler
    public void onBlockDrop(BlockDropItemEvent e) {
        Location blockLoc = e.getBlock().getRelative(BlockFace.UP).getLocation();
        String blockS = Serialization.serialize(blockLoc);
        if (MagishaComputerDivision.getPlugin().getDB().tableContainsValue(blockS)) {
            e.setCancelled(true);
            //e.getPlayer().sendMessage(ChatColor.YELLOW + "To remove the computer or it's peripherals properly, hop in the chair and enter: /computer remove");
        }
    }
}
