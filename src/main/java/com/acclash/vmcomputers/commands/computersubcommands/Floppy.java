package com.acclash.vmcomputers.commands.computersubcommands;

import com.acclash.vmcomputers.VMComputers;
import com.acclash.vmcomputers.commands.ComputerSubCommand;
import com.acclash.vmcomputers.computer.Computer;
import com.acclash.vmcomputers.emu.VmPaths;
import com.acclash.vmcomputers.emu.VmSpec;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import com.acclash.vmcomputers.utils.Permissions;
import org.bukkit.entity.Player;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Puts a floppy in a computer's drive.
 *
 * <p>The missing piece for the oldest guests. A retail Windows 95 CD is not bootable, so the way it
 * was actually installed was to boot a DOS floppy carrying CD-ROM drivers and run setup from the
 * disc -- which needs a floppy and a CD at the same time, and is why this is its own drive rather
 * than another use of {@code /vmcomputers iso}.
 *
 * <p>Like a disc and unlike a hard disk, this is at {@code use} level: the image is attached
 * read-only, so a guest cannot damage what the admin put in the folder.
 */
public class Floppy extends ComputerSubCommand {

    @Override
    public String getName() {
        return "floppy";
    }

    @Override
    public String getDescription() {
        return "Lists floppy images, or puts one in a computer's drive.";
    }

    @Override
    public String getSyntax() {
        return ChatColor.GOLD + "/vmcomputers floppy  (list)  |  /vmcomputers floppy <id> <file|none>";
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
        if (!Permissions.requireModify(player, computer, "change the floppy in")) {
            return;
        }

        String requested = args[2];
        String floppyName;
        if (requested.equalsIgnoreCase("none")) {
            floppyName = null;
        } else {
            if (computer.architecture() == VmSpec.Architecture.AARCH64) {
                player.sendMessage(ChatColor.RED + "An ARM machine has no floppy controller. "
                        + "Floppies are an x86 thing.");
                return;
            }
            Path resolved = VmPaths.resolveFloppy(requested);
            if (resolved == null) {
                player.sendMessage(ChatColor.RED + "No readable floppy image called '" + requested
                        + "'. Run /vmcomputers floppy to see what is there.");
                return;
            }
            floppyName = requested;
        }

        computer.setFloppyImage(floppyName);
        try {
            VMComputers.getPlugin().getComputerDao().updateFloppy(id, floppyName);
        } catch (SQLException e) {
            player.sendMessage(ChatColor.RED + "Could not save; see the console.");
            VMComputers.getPlugin().getLogger().severe("Floppy update failed: " + e.getMessage());
            return;
        }

        if (floppyName == null) {
            player.sendMessage(ChatColor.GREEN + "Ejected the floppy from computer #" + id + ".");
        } else {
            player.sendMessage(ChatColor.GREEN + "Inserted " + floppyName
                    + " into computer #" + id + ".");
            player.sendMessage(ChatColor.GRAY + "It will boot from this first. The image is "
                    + "write-protected, so the guest cannot change it.");
        }
        if (com.acclash.vmcomputers.emu.VmService.isRunning(id)) {
            player.sendMessage(ChatColor.GRAY + "It is running -- power it off and on to take effect.");
        }
    }

    private void listImages(Player player) {
        List<String> available = VmPaths.availableFloppies();
        player.sendMessage(ChatColor.AQUA + "Floppy images in "
                + VmPaths.floppyDirectory().toAbsolutePath() + ":");
        if (available.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "  (none -- drop img, ima, vfd, flp or dsk files "
                    + "in that folder)");
        } else {
            for (String name : available) {
                player.sendMessage(ChatColor.WHITE + "  " + name);
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
            List<String> options = new ArrayList<String>(VmPaths.availableFloppies());
            options.add("none");
            return options;
        }
        return null;
    }
}
