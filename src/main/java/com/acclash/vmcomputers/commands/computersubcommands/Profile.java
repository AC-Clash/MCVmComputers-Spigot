package com.acclash.vmcomputers.commands.computersubcommands;

import com.acclash.vmcomputers.VMComputers;
import com.acclash.vmcomputers.commands.ComputerSubCommand;
import com.acclash.vmcomputers.computer.Computer;
import com.acclash.vmcomputers.emu.GuestProfile;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import com.acclash.vmcomputers.utils.Permissions;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Tells a computer which era of operating system it is pretending to be.
 *
 * <p>The one setting that decides whether an old guest boots at all. Windows 98 needs a Cirrus
 * card, a PS/2 mouse, an ISA sound card and a CPU old enough not to overflow its own timing loop --
 * none of which anyone should have to know. Naming the era supplies all of it at once.
 */
public class Profile extends ComputerSubCommand {

    @Override
    public String getName() {
        return "profile";
    }

    @Override
    public String getDescription() {
        return "Sets the guest hardware era a computer is built for.";
    }

    @Override
    public String getSyntax() {
        return ChatColor.GOLD + "/vmcomputers profile  (list)  |  /vmcomputers profile <id> <name>";
    }

    @Override
    public void perform(Player player, String[] args) {
        if (args.length < 3) {
            listProfiles(player, args);
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
        if (!Permissions.requireModify(player, computer, "change the hardware of")) {
            return;
        }

        GuestProfile profile = GuestProfile.parse(args[2]);
        if (profile == null) {
            player.sendMessage(ChatColor.RED + "No profile called '" + args[2]
                    + "'. Run /vmcomputers profile to see them.");
            return;
        }
        if (profile.architecture() != null && profile.architecture() != computer.architecture()) {
            player.sendMessage(ChatColor.RED + profile.label() + " is a "
                    + profile.architecture() + " profile, but computer #" + id + " is "
                    + computer.architecture() + ".");
            return;
        }

        computer.setProfile(profile);
        try {
            VMComputers.getPlugin().getComputerDao().updateProfile(id, profile);
        } catch (SQLException e) {
            player.sendMessage(ChatColor.RED + "Could not save; see the console.");
            VMComputers.getPlugin().getLogger().severe("Profile update failed: " + e.getMessage());
            return;
        }

        player.sendMessage(ChatColor.GREEN + "Computer #" + id + " is now a "
                + profile.label() + ".");
        player.sendMessage(ChatColor.GRAY + "  " + profile.description());
        warnAboutFittedParts(player, computer, profile);
        if (com.acclash.vmcomputers.emu.VmService.isRunning(id)) {
            player.sendMessage(ChatColor.GRAY + "It is running -- power it off and on to take effect.");
        }
    }

    /**
     * Says where the machine as built disagrees with the era it now claims to be.
     *
     * <p>An invisible incompatibility is the failure this whole feature exists to remove, so the
     * memory ceiling is worth saying out loud rather than silently applying at power-on.
     */
    private void warnAboutFittedParts(Player player, Computer computer, GuestProfile profile) {
        if (profile.maxMemoryMb() <= 0) {
            return;
        }
        com.acclash.vmcomputers.parts.ComponentType ram =
                computer.installedIn(com.acclash.vmcomputers.parts.ComponentSlot.RAM);
        if (ram != null && ram.rating() > profile.maxMemoryMb()) {
            player.sendMessage(ChatColor.YELLOW + "Note: " + ram.displayName() + " is fitted, but "
                    + profile.label() + " cannot boot above " + profile.maxMemoryMb()
                    + " MB. It will be given " + profile.maxMemoryMb() + " MB.");
        }
    }

    private void listProfiles(Player player, String[] args) {
        // Filtered by the named computer's architecture when there is one, because half of these
        // can never apply to it and a list of things you cannot pick is not a helpful list.
        Computer computer = null;
        if (args.length == 2) {
            try {
                computer = VMComputers.getPlugin().getRegistry().byId(Integer.parseInt(args[1]));
            } catch (NumberFormatException ignored) {
                // Not an id; just show everything.
            }
        }

        List<GuestProfile> profiles = computer != null
                ? GuestProfile.forArchitecture(computer.architecture())
                : java.util.Arrays.asList(GuestProfile.values());

        if (computer != null) {
            player.sendMessage(ChatColor.AQUA + "Profiles for computer #" + computer.id()
                    + " (" + computer.architecture() + "), currently "
                    + ChatColor.WHITE + computer.profile().label() + ChatColor.AQUA + ":");
        } else {
            player.sendMessage(ChatColor.AQUA + "Guest hardware profiles:");
        }
        for (GuestProfile profile : profiles) {
            player.sendMessage(ChatColor.WHITE + "  " + profile.name()
                    + ChatColor.GRAY + " -- " + profile.description());
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
            List<String> names = new ArrayList<String>();
            Computer computer = null;
            try {
                computer = VMComputers.getPlugin().getRegistry().byId(Integer.parseInt(args[1]));
            } catch (NumberFormatException ignored) {
                // Not an id yet; offer everything.
            }
            List<GuestProfile> profiles = computer != null
                    ? GuestProfile.forArchitecture(computer.architecture())
                    : java.util.Arrays.asList(GuestProfile.values());
            for (GuestProfile profile : profiles) {
                names.add(profile.name());
            }
            return names;
        }
        return null;
    }
}
