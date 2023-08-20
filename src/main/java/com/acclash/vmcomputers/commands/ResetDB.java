package com.acclash.vmcomputers.commands;

import com.acclash.vmcomputers.VMComputers;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ResetDB implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        VMComputers.getPlugin().getDB().executeUpdate("DELETE FROM `computers`");

        return true;
    }
}
