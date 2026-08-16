package com.acclash.vmcomputers.commands.computersubcommands;

import com.acclash.vmcomputers.commands.ComputerSubCommand;
import com.acclash.vmcomputers.gui.OrderMenu;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Opens the parts shop.
 *
 * <p>The mod reaches this through an ordering tablet, which has to find a satellite first and then
 * arranges a delivery. This is the same catalogue without the errand -- the tablet and its theatre
 * can be layered on top later without the shop itself changing.
 */
public class Order extends ComputerSubCommand {

    @Override
    public String getName() {
        return "order";
    }

    @Override
    public String getDescription() {
        return "Opens the parts catalogue.";
    }

    @Override
    public String getSyntax() {
        return ChatColor.GOLD + "/vmcomputers order";
    }

    @Override
    public void perform(Player player, String[] args) {
        new OrderMenu(player).open();
        player.sendMessage(ChatColor.GRAY + "Parts are paid for in Auros, which are crafted from paper.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return null;
    }
}
