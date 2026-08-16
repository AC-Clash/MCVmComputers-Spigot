package com.acclash.vmcomputers.commands.computersubcommands;

import com.acclash.vmcomputers.commands.ComputerSubCommand;
import com.acclash.vmcomputers.parts.BrickPhone;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Hands out a brick phone.
 *
 * <p>The mod crafts its ordering tablet. There is no recipe here yet, so this is how a phone gets
 * into a player's hands at all -- which makes it the one thing standing between a fresh server and
 * the whole ordering loop.
 */
public class Phone extends ComputerSubCommand {

    @Override
    public String getName() {
        return "phone";
    }

    @Override
    public String getDescription() {
        return "Gives you a brick phone for ordering parts.";
    }

    @Override
    public String getSyntax() {
        return ChatColor.GOLD + "/vmcomputers phone";
    }

    @Override
    public void perform(Player player, String[] args) {
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(ChatColor.RED + "Your inventory is full.");
            return;
        }
        player.getInventory().addItem(BrickPhone.create());
        player.sendMessage(ChatColor.GREEN + "You are handed a brick phone.");
        player.sendMessage(ChatColor.GRAY + "Right-click it to call Steve about parts.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return null;
    }
}
