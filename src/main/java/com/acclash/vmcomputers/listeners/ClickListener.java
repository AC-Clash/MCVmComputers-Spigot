package com.acclash.vmcomputers.listeners;

import com.acclash.vmcomputers.VMComputers;
import com.acclash.vmcomputers.utils.ChatUtil;
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

import java.util.HashMap;
import java.util.Optional;

public class ClickListener implements Listener {

    HashMap<Player, Boolean> isCalling = new HashMap<>();

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
    public void onClick(PlayerInteractEvent e) {
        Player player = e.getPlayer();
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() == Action.LEFT_CLICK_BLOCK || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
                Location clickedBlockLoc = e.getClickedBlock().getLocation();
                String clickedS = Serialization.serialize(clickedBlockLoc);
                if (!VMComputers.getPlugin().getDB().tableContainsValue(clickedS)) return;
                if (e.getClickedBlock().getType() == Material.SANDSTONE_WALL) {
                    // Start emulator
                    Bukkit.broadcastMessage("starting emulator");
                    String[] args = {"-noconsole"};
                    MainFrame.main(args);
                }
            } else {
                handlePhone(e, player);
                Location clickedBlockLoc = e.getClickedBlock().getLocation();
                String clickedS = Serialization.serialize(clickedBlockLoc);
                if (!VMComputers.getPlugin().getDB().tableContainsValue(clickedS)) return;
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
            }
        } else if (e.getAction() == Action.RIGHT_CLICK_AIR) {
            handlePhone(e, player);
        }
    }

    private void handlePhone(PlayerInteractEvent e, Player player) {
        if (e.getItem() != null && e.getItem().getItemMeta().getDisplayName().contains("DynaTAC Brick Phone")) {
            if (isCalling.containsKey(player)) return;

            e.setCancelled(true);
            isCalling.put(player, true);

            player.sendMessage(ChatColor.GOLD + "You must call one of the following companies to order PC parts: ");
            player.spigot().sendMessage(ChatUtil.createClickableMessage(net.md_5.bungee.api.ChatColor.GOLD, "Amazon", "Click here to call Amazon", "/vmcomputers converse call amazon", true));
            player.spigot().sendMessage(ChatUtil.createClickableMessage(net.md_5.bungee.api.ChatColor.YELLOW, "Newegg", "Click here to call Newegg", "/vmcomputers converse call newegg", true));
            player.spigot().sendMessage(ChatUtil.createClickableMessage(net.md_5.bungee.api.ChatColor.BLUE, "Best Buy", "Click here to call Best Buy", "/vmcomputers converse call best_buy", true));
            player.spigot().sendMessage(ChatUtil.createClickableMessage(net.md_5.bungee.api.ChatColor.GREEN, "Micro Center", "Click here to call Micro Center", "/vmcomputers converse call micro_center", true));
            player.sendMessage(ChatColor.GOLD + "Click their name to dial their number");
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
