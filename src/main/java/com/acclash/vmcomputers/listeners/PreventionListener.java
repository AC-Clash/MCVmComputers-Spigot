package com.acclash.vmcomputers.listeners;

import com.acclash.vmcomputers.VMComputers;
import com.acclash.vmcomputers.computer.Computer;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.Iterator;
import java.util.List;

/**
 * Stops a computer being taken apart by hand.
 *
 * <p>Every check goes through the in-memory registry, or the computer id stamped on an entity when
 * it was built. The previous version asked the database whether a formatted location string
 * appeared in any column, which stopped matching anything at all once computers began being stored
 * as an anchor rather than a list of component positions -- so nothing was actually protected.
 *
 * <p>Item frames need more care than blocks. Left-clicking one in creative destroys it outright
 * without ever firing a block event, and right-clicking rotates the item inside, which would spin
 * the screen panel. Neither goes through {@code PlayerInteractEvent}, so cancelling that is not
 * enough.
 */
public class PreventionListener implements Listener {

    private final NamespacedKey idKey = new NamespacedKey(VMComputers.getPlugin(), "computerId");

    /** The computer an entity belongs to, from the id stamped on it at build time. */
    private Computer ownerOf(Entity entity) {
        Integer id = entity.getPersistentDataContainer().get(idKey, PersistentDataType.INTEGER);
        return id == null ? null : VMComputers.getPlugin().getRegistry().byId(id.intValue());
    }

    private Computer ownerOf(Block block) {
        return VMComputers.getPlugin().getRegistry()
                .at(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    private void refuse(Cancellable event, Entity actor, Computer computer) {
        event.setCancelled(true);
        if (actor instanceof Player) {
            ((Player) actor).sendMessage(ChatColor.YELLOW + "That is part of computer #"
                    + computer.id() + ". Remove it with /vmcomputers remove " + computer.id());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        Computer computer = ownerOf(e.getBlock());
        if (computer != null) {
            refuse(e, e.getPlayer(), computer);
        }
    }

    /** Covers breaking an item frame by any means, including a creative left-click. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent e) {
        if (ownerOf(e.getEntity()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamaged(EntityDamageEvent e) {
        if (ownerOf(e.getEntity()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamagedByEntity(EntityDamageByEntityEvent e) {
        Computer computer = ownerOf(e.getEntity());
        if (computer != null) {
            refuse(e, e.getDamager(), computer);
        }
    }

    /**
     * Right-clicking an item frame rotates the item inside it, which would turn a screen panel
     * sideways. The chair is handled elsewhere and must still be clickable.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Entity entity = e.getRightClicked();
        if (entity instanceof org.bukkit.entity.ItemFrame && ownerOf(entity) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent e) {
        if (ownerOf(e.getBlock()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        removeProtected(e.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        removeProtected(e.blockList());
    }

    /** Explosions take a block list, so protect by pruning rather than cancelling outright. */
    private void removeProtected(List<Block> blocks) {
        Iterator<Block> iterator = blocks.iterator();
        while (iterator.hasNext()) {
            if (ownerOf(iterator.next()) != null) {
                iterator.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        for (Block block : e.getBlocks()) {
            if (ownerOf(block) != null) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        for (Block block : e.getBlocks()) {
            if (ownerOf(block) != null) {
                e.setCancelled(true);
                return;
            }
        }
    }
}
