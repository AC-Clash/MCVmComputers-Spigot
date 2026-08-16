package com.acclash.vmcomputers.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Routes inventory events to the {@link Menu} that owns the window.
 *
 * <p>Ownership comes from the inventory's holder, not its title, so a player cannot reach a menu
 * action by renaming a container.
 */
public class MenuListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Menu menu = menuOf(event.getView().getTopInventory());
        if (menu == null) {
            return;
        }

        // Cancelled for every click in the window, including clicks in the player's own inventory
        // while a menu is open. Shift-clicking from the lower inventory would otherwise push items
        // into menu slots, and a menu's contents are display only -- anything a player takes away
        // is handed over deliberately by the menu itself.
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        menu.onClick(event.getRawSlot(), event.getClick());
    }

    /**
     * Dragging spans several slots at once and would otherwise deposit items into a menu, which
     * the click handler above never sees.
     */
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (menuOf(event.getView().getTopInventory()) != null) {
            event.setCancelled(true);
        }
    }

    /**
     * Lets a menu stop watching whatever it was watching.
     *
     * <p>Menus that track something they do not own keep a repeating task alive while open; this
     * is what ends it. Without it every open of such a menu would leak a task that redraws an
     * inventory nobody is looking at.
     */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Menu menu = menuOf(event.getView().getTopInventory());
        if (menu != null) {
            menu.closed();
        }
    }

    private Menu menuOf(Inventory inventory) {
        if (inventory == null) {
            return null;
        }
        InventoryHolder holder = inventory.getHolder();
        return holder instanceof Menu ? (Menu) holder : null;
    }
}
