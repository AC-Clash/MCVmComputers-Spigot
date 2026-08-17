package com.acclash.vmcomputers.commands;

import com.acclash.vmcomputers.commands.computersubcommands.Audio;
import com.acclash.vmcomputers.commands.computersubcommands.Create;
import com.acclash.vmcomputers.utils.Permissions;
import com.acclash.vmcomputers.commands.computersubcommands.Debug;
import com.acclash.vmcomputers.commands.computersubcommands.Disk;
import com.acclash.vmcomputers.commands.computersubcommands.Floppy;
import com.acclash.vmcomputers.commands.computersubcommands.Iso;
import com.acclash.vmcomputers.commands.computersubcommands.Keys;
import com.acclash.vmcomputers.commands.computersubcommands.Order;
import com.acclash.vmcomputers.commands.computersubcommands.Parts;
import com.acclash.vmcomputers.commands.computersubcommands.Phone;
import com.acclash.vmcomputers.commands.computersubcommands.Profile;
import com.acclash.vmcomputers.commands.computersubcommands.Remove;
import com.acclash.vmcomputers.commands.computersubcommands.TestDisplay;
import com.acclash.vmcomputers.commands.computersubcommands.Type;
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
        subCommands.add(new Iso());
        subCommands.add(new Disk());
        subCommands.add(new Floppy());
        subCommands.add(new Profile());
        subCommands.add(new Type());
        subCommands.add(new Keys());
        subCommands.add(new TestDisplay());
        subCommands.add(new Order());
        subCommands.add(new Phone());
        subCommands.add(new Parts());
        subCommands.add(new Debug());
        subCommands.add(new Audio());
    }


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;

            if (args.length == 0) {
                player.sendMessage(ChatColor.RED + "You need to enter a command.");
                player.sendMessage(ChatColor.GOLD + "/npc <command>");
            } else {
                for (int i = 0; i < getSubCommands().size(); i++) {
                    ComputerSubCommand sub = getSubCommands().get(i);
                    if (!args[0].equalsIgnoreCase(sub.getName())) {
                        continue;
                    }
                    // Checked here rather than in each subcommand, so a new one cannot ship
                    // unguarded by being forgotten.
                    if (!player.hasPermission(sub.getPermission())
                            && !player.hasPermission(Permissions.ADMIN)) {
                        player.sendMessage(ChatColor.RED
                                + "You do not have permission to do that.");
                        return true;
                    }
                    sub.perform(player, args);
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
