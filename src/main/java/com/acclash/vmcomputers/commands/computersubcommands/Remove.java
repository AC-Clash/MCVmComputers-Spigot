package com.acclash.vmcomputers.commands.computersubcommands;

import com.acclash.vmcomputers.VMComputers;
import com.acclash.vmcomputers.commands.ComputerSubCommand;
import com.acclash.vmcomputers.computer.Computer;
import com.acclash.vmcomputers.computer.ComputerLayout;
import com.acclash.vmcomputers.utils.ComputerFunctions;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Tears a computer back down.
 *
 * <p>Both the block clearing and the entity cleanup walk the same {@link ComputerLayout} that built
 * it, so nothing can be left behind by the two disagreeing. The previous version listed component
 * locations by hand and had the whole body duplicated between its two argument forms.
 */
public class Remove extends ComputerSubCommand {

    @Override
    public String getName() {
        return "remove";
    }

    @Override
    public String getDescription() {
        return "Removes the computer you are standing in, or one by id.";
    }

    @Override
    public String getSyntax() {
        return ChatColor.GOLD + "Stand in the computer and use /vmcomputers remove, "
                + "or /vmcomputers remove <id>";
    }

    @Override
    public void perform(Player player, String[] args) {
        Computer computer = args.length >= 2 ? byId(player, args[1]) : byPosition(player);
        if (computer == null) {
            return;
        }

        // Destroy the machine off-thread. A graceful stop waits up to ten seconds for a guest to
        // acknowledge the ACPI power button, and a guest sitting at a firmware prompt never will --
        // which froze the whole server for the entire timeout. The computer is being deleted, so
        // there is nothing to shut down cleanly.
        final int computerId = computer.id();
        Bukkit.getScheduler().runTaskAsynchronously(VMComputers.getPlugin(), () -> {
            ComputerFunctions.kill(computerId);
            // The disk belongs to this computer alone, so it goes with it.
            com.acclash.vmcomputers.emu.VmPaths.deleteDisk(computerId);
        });

        try {
            VMComputers.getPlugin().getComputerDao().deletePanels(computer.id());
            VMComputers.getPlugin().getComputerDao().deleteComponents(computer.id());
            VMComputers.getPlugin().getComputerDao().delete(computer.id());
        } catch (SQLException e) {
            player.sendMessage(ChatColor.RED + "Could not delete the computer; see the console.");
            VMComputers.getPlugin().getLogger().severe("Delete failed: " + e.getMessage());
            return;
        }

        VMComputers.getPlugin().getRegistry().remove(computer.id());
        VMComputers.getPlugin().unregisterScreen(computer.id());
        demolish(player.getWorld(), computer);

        player.stopSound("pmagisha.hdd-loop");
        player.sendMessage(ChatColor.GREEN + "Removed computer #" + computer.id()
                + " (" + computer.monitorSize() + ").");
    }

    private Computer byId(Player player, String raw) {
        int id;
        try {
            id = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "'" + raw + "' is not a number.");
            return null;
        }
        Computer computer = VMComputers.getPlugin().getRegistry().byId(id);
        if (computer == null) {
            player.sendMessage(ChatColor.YELLOW + "No computer with id " + id + ".");
        }
        return computer;
    }

    private Computer byPosition(Player player) {
        Location location = player.getLocation();
        // Check the feet block and the one below, so it works standing on the chair or in it.
        for (int dy = 0; dy >= -1; dy--) {
            Computer computer = VMComputers.getPlugin().getRegistry().at(
                    location.getWorld().getName(),
                    location.getBlockX(), location.getBlockY() + dy, location.getBlockZ());
            if (computer != null) {
                return computer;
            }
        }
        player.sendMessage(ChatColor.YELLOW + "You are not standing in a computer. "
                + "Use /vmcomputers remove <id> instead.");
        return null;
    }

    private void demolish(World world, Computer computer) {
        NamespacedKey idKey = new NamespacedKey(VMComputers.getPlugin(), "computerId");

        // Entities first: item frames sitting on a block that is about to vanish would pop off as
        // dropped items.
        Location centre = computer.anchorLocation(world).add(0.5, 0.5, 0.5);
        int reach = Math.max(computer.monitorSize().columns(), computer.layout().screenDepth()) + 4;
        for (Entity entity : world.getNearbyEntities(centre, reach, reach, reach)) {
            Integer owner = entity.getPersistentDataContainer()
                    .get(idKey, PersistentDataType.INTEGER);
            if (owner != null && owner.intValue() == computer.id()) {
                entity.remove();
            }
        }

        // Attached blocks come out first, and every clear runs with applyPhysics=false. Removing
        // a support with physics enabled makes the button and pressure plate pop off as dropped
        // items instead of simply vanishing.
        for (ComputerLayout.Offset offset : new ComputerLayout.Offset[]{
                computer.layout().mouse(), computer.layout().keyboard()}) {
            if (offset != null) {
                computer.locationOf(world, offset).getBlock().setType(Material.AIR, false);
            }
        }
        for (ComputerLayout.Offset offset : computer.layout().occupiedBlocks()) {
            computer.locationOf(world, offset).getBlock().setType(Material.AIR, false);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            List<String> ids = new ArrayList<String>();
            for (Computer computer : VMComputers.getPlugin().getRegistry().all()) {
                ids.add(String.valueOf(computer.id()));
            }
            return ids;
        }
        return null;
    }
}
