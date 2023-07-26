package com.acclash.magishacomputerdivision.commands;

import com.acclash.magishacomputerdivision.commands.computersubcommands.Create;
import com.acclash.magishacomputerdivision.commands.computersubcommands.Remove;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class ComputerCM implements TabExecutor {

    private final ArrayList<ComputerSubCommand> subCommands = new ArrayList<>();

    public ArrayList<ComputerSubCommand> getSubCommands() {
        return subCommands;
    }

    public ComputerCM() {
        subCommands.add(new Create());
        subCommands.add(new Remove());
    }


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player) {

            if (args.length == 0) {
                player.sendMessage(ChatColor.RED + "You need to enter a command.");
                player.sendMessage(ChatColor.GOLD + "/npc <command>");
            } else {
                for (int i = 0; i < getSubCommands().size(); i++) {
                    if (args[0].equalsIgnoreCase(getSubCommands().get(i).getName())) {
                        getSubCommands().get(i).perform(player, args);
                    }
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> commands = new ArrayList<>();
            for (ComputerSubCommand subCommand : subCommands) {
                commands.add(subCommand.getName());
            }

            return commands;
        }else if (args.length > 1) {
            //Remove.stopIndicator();
            for (int i = 0; i < getSubCommands().size(); i++) {
                if (args[0].equalsIgnoreCase(getSubCommands().get(i).getName())) {
                    return getSubCommands().get(i).onTabComplete(sender, args);
                }
            }
        }
        return null;
    }
}
