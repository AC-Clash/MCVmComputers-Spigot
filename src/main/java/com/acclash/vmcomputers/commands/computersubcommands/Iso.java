package com.acclash.vmcomputers.commands.computersubcommands;

import com.acclash.vmcomputers.VMComputers;
import com.acclash.vmcomputers.commands.ComputerSubCommand;
import com.acclash.vmcomputers.computer.Computer;
import com.acclash.vmcomputers.emu.VmPaths;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Puts installer media in a computer's drive.
 *
 * <p>Reads whatever is in {@code plugins/vm_computers/isos}, so an admin adds operating systems by
 * dropping files in a folder. A curated download catalogue can sit on top of this later; the drive
 * itself does not need to know where the file came from.
 */
public class Iso extends ComputerSubCommand {

    @Override
    public String getName() {
        return "iso";
    }

    @Override
    public String getDescription() {
        return "Lists available ISOs, or puts one in a computer's drive.";
    }

    @Override
    public String getSyntax() {
        return ChatColor.GOLD + "/vmcomputers iso  (list)  |  /vmcomputers iso <id> <file.iso|none>";
    }

    @Override
    public void perform(Player player, String[] args) {
        List<String> available = VmPaths.availableIsos();

        if (args.length < 3) {
            player.sendMessage(ChatColor.AQUA + "ISOs in "
                    + VmPaths.isoDirectory().toAbsolutePath() + ":");
            if (available.isEmpty()) {
                player.sendMessage(ChatColor.GRAY + "  (none -- drop .iso files in that folder)");
            } else {
                for (String name : available) {
                    player.sendMessage(ChatColor.WHITE + "  " + name);
                }
            }
            player.sendMessage(getSyntax());
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

        String requested = args[2];
        String isoName;
        if (requested.equalsIgnoreCase("none")) {
            isoName = null;
        } else {
            Path resolved = VmPaths.resolveIso(requested);
            if (resolved == null) {
                player.sendMessage(ChatColor.RED + "No readable ISO called '" + requested + "'.");
                return;
            }
            isoName = requested;
        }

        computer.setIsoName(isoName);
        try {
            VMComputers.getPlugin().getComputerDao().updateIso(id, isoName);
        } catch (SQLException e) {
            player.sendMessage(ChatColor.RED + "Could not save; see the console.");
            VMComputers.getPlugin().getLogger().severe("Iso update failed: " + e.getMessage());
            return;
        }

        if (isoName == null) {
            player.sendMessage(ChatColor.GREEN + "Ejected the disc from computer #" + id + ".");
        } else {
            player.sendMessage(ChatColor.GREEN + "Inserted " + isoName + " into computer #" + id + ".");
            player.sendMessage(ChatColor.GRAY + "Power it on to boot from it. Changes are kept on "
                    + "its virtual disk, which is created on first boot.");
        }
        if (com.acclash.vmcomputers.emu.VmService.isRunning(id)) {
            player.sendMessage(ChatColor.GRAY + "It is running -- power it off and on to take effect.");
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
        if (args.length == 3) {
            List<String> options = new ArrayList<String>(VmPaths.availableIsos());
            options.add("none");
            return options;
        }
        return null;
    }
}
