package com.acclash.magishacomputerdivision.commands;

import com.acclash.magishacomputerdivision.MagishaComputerDivision;
import com.acclash.magishacomputerdivision.utils.Compress;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;

public class ResetDB implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender instanceof Player player) {

        }

        MagishaComputerDivision.getPlugin().getDB().executeUpdate("DELETE FROM `computers`");

        return true;
    }
}
