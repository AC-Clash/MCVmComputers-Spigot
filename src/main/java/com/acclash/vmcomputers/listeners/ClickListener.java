package com.acclash.vmcomputers.listeners;

import com.acclash.vmcomputers.VMComputers;
import com.acclash.vmcomputers.computer.Computer;
import com.acclash.vmcomputers.display.MonitorScreen;
import com.acclash.vmcomputers.emu.VmService;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
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

import java.util.Optional;

public class ClickListener implements Listener {

    private final NamespacedKey eChair = new NamespacedKey(VMComputers.getPlugin(), "isEChair");

    /** Seats the player when they click the invisible chair entity. */
    @EventHandler
    public void onRightClick(PlayerInteractEntityEvent e) {
        if (e.getRightClicked() instanceof LivingEntity) {
            LivingEntity entity = (LivingEntity) e.getRightClicked();
            if (!entity.getPersistentDataContainer().has(eChair, PersistentDataType.STRING)) return;
            if (!entity.getPassengers().isEmpty()) return;
            entity.addPassenger(e.getPlayer());
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;

        // Air clicks carry no block. This fires on every swing at nothing, so it has to be the
        // first thing checked.
        Block clicked = e.getClickedBlock();
        if (clicked == null) return;

        // Hash lookup against the in-memory index -- no database round trip on the interaction
        // path, which previously ran a query against every column for every click in the world.
        Computer computer = VMComputers.getPlugin().getRegistry()
                .at(clicked.getWorld().getName(), clicked.getX(), clicked.getY(), clicked.getZ());
        if (computer == null) return;

        Player player = e.getPlayer();
        Action action = e.getAction();

        if (action == Action.RIGHT_CLICK_BLOCK) {
            if (isChairBlock(computer, clicked)) {
                seatPlayer(player, clicked.getLocation());
                e.setCancelled(true);
            } else if (isPowerBlock(computer, clicked)) {
                togglePower(player, computer);
                e.setCancelled(true);
            }
        }
    }

    private void togglePower(Player player, Computer computer) {
        MonitorScreen screen = VMComputers.getPlugin().getScreen(computer.id());
        if (screen == null) {
            player.sendMessage(ChatColor.RED + "Computer #" + computer.id()
                    + " has no screen attached; rebuild it.");
            return;
        }

        if (VmService.isRunning(computer.id())) {
            player.sendMessage(ChatColor.GRAY + "Powering off...");
            VmService.stop(computer, screen, message -> player.sendMessage(ChatColor.YELLOW + message));
        } else {
            player.sendMessage(ChatColor.GRAY + "Powering on...");
            VmService.start(computer, screen, message -> player.sendMessage(ChatColor.YELLOW + message));
        }
    }

    private boolean isChairBlock(Computer computer, Block block) {
        if (computer.layout().chair() == null) return false;
        int[] chair = computer.blockAt(computer.layout().chair());
        return chair[0] == block.getX() && chair[1] == block.getY() && chair[2] == block.getZ();
    }

    /** The tower on a desk computer, or the control block on a projector. */
    private boolean isPowerBlock(Computer computer, Block block) {
        var offset = computer.layout().tower() != null
                ? computer.layout().tower()
                : computer.layout().control();
        if (offset == null) return false;
        int[] p = computer.blockAt(offset);
        return p[0] == block.getX() && p[1] == block.getY() && p[2] == block.getZ();
    }

    private void seatPlayer(Player player, Location blockLocation) {
        Optional<Entity> chair = blockLocation.getWorld()
                .getNearbyEntities(blockLocation.clone().add(0.5, 0.5, 0.5), 1, 1, 1).stream()
                .filter(entity -> entity.getPersistentDataContainer()
                        .has(eChair, PersistentDataType.STRING))
                .findFirst();
        chair.ifPresent(entity -> entity.addPassenger(player));
    }
}
