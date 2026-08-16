package com.acclash.vmcomputers.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public abstract class ComputerSubCommand {

    public abstract String getName();

    public abstract String getDescription();

    public abstract String getSyntax();

    public abstract void perform(Player player, String[] args);

    /**
     * The permission needed to run this, checked before {@link #perform}.
     *
     * <p>Defaults to plain use, which is the right answer for anything that only touches a machine
     * a player is already standing at. Subcommands that build, delete or reconfigure override it.
     */
    public String getPermission() {
        return com.acclash.vmcomputers.utils.Permissions.USE;
    }

    public abstract List<String> onTabComplete(CommandSender sender, String[] args);

}
