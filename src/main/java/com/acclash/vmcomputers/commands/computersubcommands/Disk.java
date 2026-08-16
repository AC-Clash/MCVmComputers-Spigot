package com.acclash.vmcomputers.commands.computersubcommands;

import com.acclash.vmcomputers.VMComputers;
import com.acclash.vmcomputers.commands.ComputerSubCommand;
import com.acclash.vmcomputers.parts.ComponentSlot;
import com.acclash.vmcomputers.computer.Computer;
import com.acclash.vmcomputers.emu.VmPaths;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import com.acclash.vmcomputers.utils.Permissions;
import org.bukkit.entity.Player;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Boots a computer from a disk image the admin supplied, instead of the one the plugin makes.
 *
 * <p>This is the way in for every guest that is painful to install through a wall of maps. Install
 * Windows 95 once in a normal QEMU window, copy the image into {@code plugins/vm_computers/disks},
 * and attach it here -- the machine boots a system that is already installed.
 *
 * <p>Deliberately not a way to make disks: nothing in this command creates, resizes or deletes an
 * image. An admin's file is only ever opened.
 */
public class Disk extends ComputerSubCommand {

    @Override
    public String getName() {
        return "disk";
    }

    @Override
    public String getDescription() {
        return "Lists supplied disk images, or boots a computer from one.";
    }

    /**
     * Admin, where {@code iso} is not.
     *
     * <p>The difference is that a disk is attached read-write. A CD cannot be harmed by the guest
     * that boots it, but an image here is written to in place, so letting any player point their
     * own machine at the admin's carefully-installed Windows 95 is letting them reformat it.
     */
    @Override
    public String getPermission() {
        return Permissions.ADMIN;
    }

    @Override
    public String getSyntax() {
        return ChatColor.GOLD + "/vmcomputers disk  (list)  |  /vmcomputers disk <id> <file|none>";
    }

    @Override
    public void perform(Player player, String[] args) {
        if (args.length < 3) {
            listImages(player);
            return;
        }

        int id;
        try {
            id = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "'" + args[1] + "' is not a computer id.");
            return;
        }

        Computer computer = VMComputers.getPlugin().getRegistry().byId(id);
        if (computer == null) {
            player.sendMessage(ChatColor.YELLOW + "No computer with id " + id + ".");
            return;
        }
        if (!Permissions.requireModify(player, computer, "change the disk in")) {
            return;
        }

        String requested = args[2];
        String diskName;
        if (requested.equalsIgnoreCase("none")) {
            diskName = null;
        } else {
            Path resolved = VmPaths.resolveDisk(requested);
            if (resolved == null) {
                player.sendMessage(ChatColor.RED + "No readable disk image called '" + requested
                        + "'. Run /vmcomputers disk to see what is there.");
                return;
            }
            if (VmPaths.diskFormat(requested) == null) {
                player.sendMessage(ChatColor.RED + "'" + requested + "' is not a format QEMU reads.");
                return;
            }
            diskName = requested;
        }

        computer.setDiskImage(diskName);
        try {
            VMComputers.getPlugin().getComputerDao().updateDisk(id, diskName);
        } catch (SQLException e) {
            player.sendMessage(ChatColor.RED + "Could not save; see the console.");
            VMComputers.getPlugin().getLogger().severe("Disk update failed: " + e.getMessage());
            return;
        }

        if (diskName == null) {
            player.sendMessage(ChatColor.GREEN + "Computer #" + id
                    + " is back on its own disk.");
            player.sendMessage(ChatColor.GRAY + "Whatever was on it before is still there; the "
                    + "supplied image was never written to by this.");
        } else {
            player.sendMessage(ChatColor.GREEN + "Computer #" + id + " will boot from "
                    + diskName + ".");
            player.sendMessage(ChatColor.GRAY + "This replaces its own disk rather than adding a "
                    + "second one, and the guest writes straight into your file.");
            // The bay gates whether a disk is attached at all, so an imported image in a machine
            // with no drive fitted would be silently ignored at power-on. Say so now instead.
            if (computer.installedIn(ComponentSlot.HARD_DRIVE) == null) {
                player.sendMessage(ChatColor.YELLOW + "No hard drive is fitted, so nothing will be "
                        + "attached until one is. Fit a drive to use this image.");
            }
        }
        if (com.acclash.vmcomputers.emu.VmService.isRunning(id)) {
            player.sendMessage(ChatColor.GRAY + "It is running -- power it off and on to take effect.");
        }
    }

    private void listImages(Player player) {
        List<String> available = VmPaths.availableDisks();
        player.sendMessage(ChatColor.AQUA + "Disk images in "
                + VmPaths.diskDirectory().toAbsolutePath() + ":");
        if (available.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "  (none -- drop qcow2, img, raw, vmdk or vdi "
                    + "files in that folder)");
        } else {
            for (String name : available) {
                player.sendMessage(ChatColor.WHITE + "  " + name
                        + ChatColor.DARK_GRAY + "  " + VmPaths.diskFormat(name));
            }
        }
        player.sendMessage(getSyntax());
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
        if (args.length == 3) {
            List<String> options = new ArrayList<String>(VmPaths.availableDisks());
            options.add("none");
            return options;
        }
        return null;
    }
}
