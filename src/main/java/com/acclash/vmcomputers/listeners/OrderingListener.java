package com.acclash.vmcomputers.listeners;

import com.acclash.vmcomputers.gui.OrderMenu;
import com.acclash.vmcomputers.parts.BrickPhone;
import com.acclash.vmcomputers.parts.ComponentType;
import com.acclash.vmcomputers.parts.Delivery;
import org.bukkit.ChatColor;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.List;

/** The phone in your hand and the package on the ground. */
public class OrderingListener implements Listener {

    /** Right-clicking the brick phone places the call. */
    @EventHandler
    public void onUsePhone(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!BrickPhone.is(event.getItem())) {
            return;
        }

        // Cancelled so a phone aimed at a block does not also place or activate something.
        event.setCancelled(true);
        Player player = event.getPlayer();
        player.sendMessage(ChatColor.GRAY + "You dial the only number in the phone.");
        player.sendMessage(ChatColor.GOLD + "Steve: " + ChatColor.WHITE
                + "YEAH? WHAT DO YOU NEED?");
        new OrderMenu(player).open();
    }

    /** Right-clicking a delivered package opens it. */
    @EventHandler
    public void onOpenPackage(PlayerInteractEntityEvent event) {
        if (!Delivery.isPackage(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        List<ComponentType> contents = Delivery.open(player, (Interaction) event.getRightClicked());
        if (contents == null) {
            player.sendMessage(ChatColor.RED
                    + "There is no room in your inventory for what is in the box.");
            return;
        }

        if (contents.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "The box is empty. Odd.");
            return;
        }
        player.sendMessage(ChatColor.GREEN + "You open the package:");
        for (ComponentType type : contents) {
            player.sendMessage(ChatColor.GRAY + "  " + type.displayName());
        }
    }
}
