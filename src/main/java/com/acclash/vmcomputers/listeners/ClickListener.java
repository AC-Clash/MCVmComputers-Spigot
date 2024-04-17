package com.acclash.vmcomputers.listeners;

import com.acclash.vmcomputers.VMComputers;
import com.acclash.vmcomputers.utils.Serialization;
import jdos.gui.MainFrame;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;

public class ClickListener implements Listener {

    NamespacedKey eChair = new NamespacedKey(VMComputers.getPlugin(), "isEChair");

    // Mounts the player on the chair if they click the hidden chicken
    @EventHandler
    public void onRightClick(PlayerInteractEntityEvent e) {
        if (e.getRightClicked() instanceof LivingEntity) {
            LivingEntity entity = (LivingEntity) e.getRightClicked();
            if (!entity.getPersistentDataContainer().has(eChair, PersistentDataType.STRING)) return;
            if (!entity.getPassengers().isEmpty()) return;
            entity.addPassenger(e.getPlayer());
        }
    }

    // If the player clicks blocks that are part of the computer
    @EventHandler
    public void onRightClick(PlayerInteractEvent e) {
        Player player = e.getPlayer();
        if (e.getHand() != EquipmentSlot.HAND) return;
        Location clickedBlockLoc = e.getClickedBlock().getLocation();
        String clickedS = Serialization.serialize(clickedBlockLoc);
        if (!VMComputers.getPlugin().getDB().tableContainsValue(clickedS)) return;
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (e.getClickedBlock().getType() == Material.SPRUCE_STAIRS) {
                // Mounts the player if they right-click the stairs
                Optional<Entity> po = clickedBlockLoc.getWorld().getNearbyEntities(clickedBlockLoc, 1, 1, 1).stream().filter(entity -> entity.getPersistentDataContainer().has(new NamespacedKey(VMComputers.getPlugin(), "isEChair"), PersistentDataType.STRING)).findFirst();
                if (po.isPresent()) {
                    Entity eChair = po.get();
                    eChair.addPassenger(e.getPlayer());
                }
            } else if (e.getClickedBlock().getType() == Material.SANDSTONE_WALL) {
                player.sendMessage(ChatColor.YELLOW + "You right clicked the tower");
            }
        } else if (e.getAction() == Action.LEFT_CLICK_BLOCK || e.getAction() == Action.LEFT_CLICK_AIR) {
            if (e.getClickedBlock().getType() == Material.SANDSTONE_WALL) {
                // Start emulator
                Bukkit.broadcastMessage("starting emulator");
                String[] args = {"-noconsole"};
                MainFrame.main(args);
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {
        Player player = e.getPlayer();
        if (!player.isInsideVehicle()) return;
        if (!player.getVehicle().getPersistentDataContainer().has(eChair, PersistentDataType.STRING)) return;
        Location newTo = e.getTo();
        newTo.setPitch(e.getFrom().getPitch());
        newTo.setYaw(e.getFrom().getYaw());
        e.setTo(newTo);
    }
}
