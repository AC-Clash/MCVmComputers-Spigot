package com.acclash.vmcomputers.listeners;

import com.acclash.vmcomputers.VMComputers;
import com.acclash.vmcomputers.utils.ComputerFunctions;
import com.acclash.vmcomputers.utils.Serialization;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class ClickListener implements Listener {

    // Mounts the player on the chair
    @EventHandler
    public void onRightClick(PlayerInteractEntityEvent e) {
        if (e.getRightClicked() instanceof LivingEntity) {
            LivingEntity entity = (LivingEntity) e.getRightClicked();
            if (entity.getPersistentDataContainer().has(new NamespacedKey(VMComputers.getPlugin(), "isEChair"), PersistentDataType.STRING)) {
                entity.addPassenger(e.getPlayer());
            }
        }
    }

    // If the player right-clicks on the block in general
    @EventHandler
    public void onRightClick(PlayerInteractEvent e) throws SQLException {
        Player player = e.getPlayer();
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (!(e.getClickedBlock().getType() == Material.SANDSTONE_WALL || e.getClickedBlock().getType() == Material.SPRUCE_STAIRS))
                return;
            Location blockLoc = e.getClickedBlock().getLocation();
            String blockS = Serialization.serialize(blockLoc);
            String sql = "SELECT * FROM `computers` WHERE `block_loc` = '" + blockS + "'";
            ResultSet resultSet = VMComputers.getPlugin().getDB().executeQuery(sql);
            if (e.getClickedBlock().getType() == Material.SPRUCE_STAIRS) {
                if (resultSet.next()) {
                    Optional<Entity> po = blockLoc.getWorld().getNearbyEntities(blockLoc, 1, 1, 1).stream().filter(entity -> entity.getPersistentDataContainer().has(new NamespacedKey(VMComputers.getPlugin(), "isEChair"), PersistentDataType.STRING)).findFirst();
                    if (po.isPresent()) {
                        Entity eChair = po.get();
                        eChair.addPassenger(e.getPlayer());
                    }
                }
            } else if (e.getClickedBlock().getType() == Material.SANDSTONE_WALL) {
                if (resultSet.next()) {
                    player.sendMessage(ChatColor.YELLOW + "You right clicked the tower");
                    // In the future emulator will start up here
                }
            }
        } else if (e.getAction() == Action.LEFT_CLICK_BLOCK || e.getAction() == Action.LEFT_CLICK_AIR) {
            if (!player.isInsideVehicle()) return;
            if (player.getVehicle().getPersistentDataContainer().has(new NamespacedKey(VMComputers.getPlugin(), "isEChair"), PersistentDataType.STRING)) {
                Location blockLoc = e.getClickedBlock().getLocation();
                String blockS = Serialization.serialize(blockLoc);
                String sql = "SELECT * FROM `computers` WHERE `block_loc` = '" + blockS + "'";
                ResultSet resultSet = VMComputers.getPlugin().getDB().executeQuery(sql);
                    if (resultSet.next()) {
                        int id = resultSet.getInt("id");
                        //ComputerFunctions.getFrameMap().get(id) ??
                        // Send mouse click?
                    }
            }
        }
    }
}
