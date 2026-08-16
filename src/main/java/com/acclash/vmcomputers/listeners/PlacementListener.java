package com.acclash.vmcomputers.listeners;

import com.acclash.vmcomputers.VMComputers;
import com.acclash.vmcomputers.computer.Computer;
import com.acclash.vmcomputers.computer.PendingCase;
import com.acclash.vmcomputers.gui.AssemblyMenu;
import com.acclash.vmcomputers.parts.ComponentType;
import com.acclash.vmcomputers.parts.PartRenderer;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import com.acclash.vmcomputers.utils.Permissions;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;

/**
 * Putting a PC case down, and opening one that is already down.
 *
 * <p>This is the start of the build-it-yourself route: a case bought from Steve is placed like a
 * block, then filled with parts and assembled. {@code /vmcomputers create} still builds a whole
 * machine in one go and is unaffected.
 */
public class PlacementListener implements Listener {

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block clicked = event.getClickedBlock();
            if (clicked == null) {
                return;
            }

            // An already-placed case opens for building. Checked before placement, so clicking a
            // case while holding another one opens the first rather than stacking a second on it.
            PendingCase existing = VMComputers.getPlugin().pendingCaseAt(
                    clicked.getWorld().getName(), clicked.getX(), clicked.getY(), clicked.getZ());
            if (existing != null) {
                event.setCancelled(true);
                if (Permissions.requireBuild(player)) {
                    new AssemblyMenu(player, existing).open();
                }
                return;
            }

            ComponentType held = ComponentType.of(event.getItem());
            if (held != null && held.isCase()) {
                event.setCancelled(true);
                place(player, clicked.getRelative(event.getBlockFace()), held);
            }
        }
    }

    private void place(Player player, Block target, ComponentType caseType) {
        if (!Permissions.requireBuild(player)) {
            return;
        }
        if (!target.isPassable()) {
            player.sendMessage(ChatColor.RED + "There is no room there.");
            return;
        }
        BlockFace facing = player.getFacing();
        if (!Computer.isCardinal(facing)) {
            player.sendMessage(ChatColor.RED + "Face north, east, south or west.");
            return;
        }

        World world = target.getWorld();
        // The case looks back at whoever put it down, the way a tower on a desk faces the room.
        BlockFace caseFacing = facing.getOppositeFace();

        PendingCase pending;
        try {
            pending = VMComputers.getPlugin().getComputerDao().insertCase(new PendingCase(
                    -1, world.getName(), target.getX(), target.getY(), target.getZ(), caseFacing,
                    caseType));
        } catch (SQLException e) {
            player.sendMessage(ChatColor.RED + "Could not place that; see the console.");
            VMComputers.getPlugin().getLogger().severe("Case insert failed: " + e.getMessage());
            return;
        }

        // Solid so the case cannot be walked through, invisible so only the model shows. Same
        // reasoning as the assembled tower: a display entity has no hitbox of its own.
        target.setType(Material.BARRIER, false);
        Location at = new Location(world, target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
        PartRenderer.spawnNamed(at, caseFacing, caseType.modelName(), 1.0f,
                PendingCase.DISPLAY_OWNER);

        VMComputers.getPlugin().rememberPendingCase(pending);
        takeOne(player);

        world.playSound(at, Sound.BLOCK_METAL_PLACE, 1.0f, 0.9f);
        player.sendMessage(ChatColor.GREEN + "Case placed. "
                + ChatColor.GRAY + "Right-click it to fit parts.");
    }

    private void takeOne(Player player) {
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getAmount() > 1) {
            held.setAmount(held.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }
}
