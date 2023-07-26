package com.acclash.magishacomputerdivision.commands;

import com.acclash.magishacomputerdivision.utils.Compress;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.io.IOException;

public class Tets implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        try {
            Compress.farts();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return true;
    }
}
