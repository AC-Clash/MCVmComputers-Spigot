package com.acclash.vmcomputers.commands.computersubcommands;

import com.acclash.vmcomputers.VMComputers;
import com.acclash.vmcomputers.commands.ComputerSubCommand;
import com.acclash.vmcomputers.gui.ShopGUI;
import com.acclash.vmcomputers.utils.ChatUtil;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public class Converse extends ComputerSubCommand {

    String nameNoColor;

    String name;

    @Override
    public String getName() {
        return "converse";
    }

    @Override
    public String getDescription() {
        return "Creates a computer of the specified type.";
    }

    @Override
    public String getSyntax() {
        return ChatColor.GOLD + "To create a computer, stand where the chair should be, face where the monitor should be, and enter: /computer create <type>";
    }

    @Override
    public void perform(Player player, String[] args) {

        if (args.length == 1) {
            player.sendMessage(ChatColor.RED + "You need to enter some arguments.");
            player.sendMessage(getSyntax());
        } else {
            if (args[1].equalsIgnoreCase("call")) {
                name = switch (args[2]) {
                    case "amazon" -> ChatColor.GOLD + "Amazon";
                    case "newegg" -> ChatColor.YELLOW + "Newegg";
                    case "best_buy" -> ChatColor.BLUE + "Best Buy";
                    case "micro_center" -> ChatColor.GREEN + "Micro Center";
                    default -> "";
                };

                player.sendMessage(ChatColor.AQUA + "Dialing...");
                nameNoColor = ChatColor.stripColor(name);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        player.sendMessage("<" + name + "> Hello?");
                    }
                }.runTaskLater(VMComputers.getPlugin(), 20);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        player.sendMessage(ChatColor.GOLD + "This doesn't seem to be " + nameNoColor + "! The phone might have malfunctioned");
                    }
                }.runTaskLater(VMComputers.getPlugin(), 50);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        player.sendMessage(ChatColor.GOLD + "Click one of the following options: ");
                        player.spigot().sendMessage(ChatUtil.createClickableMessage(net.md_5.bungee.api.ChatColor.GOLD, "\"Hi, is this " + nameNoColor + "?\"", "Click here to respond with this", "/vmcomputers converse answer isthis"));
                        player.spigot().sendMessage(ChatUtil.createClickableMessage(net.md_5.bungee.api.ChatColor.GOLD, "\"Hi, I was wondering if I could order some PC parts.\"", "Click here to respond with this", "/vmcomputers converse answer pc"));

                    }
                }.runTaskLater(VMComputers.getPlugin(), 70);
            } else if (args[1].equalsIgnoreCase("answer")) {
                if (args[2].equalsIgnoreCase("isthis")) {
                    player.sendMessage("<Not " + name + ChatColor.RESET + "> No. I think you have the wrong number buddy!");
                } else {
                    player.sendMessage("<Not " + name + ChatColor.RESET + "> *laughs* Who do you think I am? " + nameNoColor + "?");
                }

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        player.sendMessage(ChatColor.GOLD + "Click one of the following options: ");
                        player.spigot().sendMessage(ChatUtil.createClickableMessage(net.md_5.bungee.api.ChatColor.GOLD, "\"Sorry\"", "Click here to respond with this", "/vmcomputers converse oh sorry"));
                        player.spigot().sendMessage(ChatUtil.createClickableMessage(net.md_5.bungee.api.ChatColor.GOLD, "\"Well, I was trying to call them to order some computer parts.\"", "Click here to respond with this", "/vmcomputers converse oh ordering"));
                    }
                }.runTaskLater(VMComputers.getPlugin(), 40);
            } else if (args[1].equalsIgnoreCase("oh")) {
                if (args[2].equalsIgnoreCase("sorry")) {
                    player.sendMessage("<Not " + name + ChatColor.RESET + "> It's okay. Tell you what. You just called the former CEO of Microsoft!");
                } else {
                    player.sendMessage("<Not " + name + ChatColor.RESET + "> Well, guess what? You're talking to the former CEO of Microsoft!");
                }

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        player.sendMessage(ChatColor.GOLD + "Click one of the following options: ");
                        player.spigot().sendMessage(ChatUtil.createClickableMessage(net.md_5.bungee.api.ChatColor.GOLD, "\"Oh my\"", "Click here to respond with this", "/vmcomputers converse steve oh_my"));
                        player.spigot().sendMessage(ChatUtil.createClickableMessage(net.md_5.bungee.api.ChatColor.GOLD, "\"Oh no, sorry to bother you!\"", "Click here to respond with this", "/vmcomputers converse steve oh_no"));
                    }
                }.runTaskLater(VMComputers.getPlugin(), 40);
            } else if (args[1].equalsIgnoreCase("steve")) {
                if (args[2].equalsIgnoreCase("oh_my")) {
                    player.sendMessage(ChatColor.YELLOW + "[" + ChatColor.AQUA + "⊞" + ChatColor.GREEN + "Steve Ballmer" + ChatColor.YELLOW + "]" + ChatColor.AQUA + " Yeah, how the turns have tabled. Err, tables... Anyway, I suppose I was the one who decided to step down. Some of my ideas didn't go so well and I wanted someone else to take over. Hey, this has to be more than a coincidence.");
                } else {
                    player.sendMessage(ChatColor.YELLOW + "[" + ChatColor.AQUA + "⊞" + ChatColor.GREEN + "Steve Ballmer" + ChatColor.YELLOW + "]" + ChatColor.AQUA + " No it's all good. Oh boy, how the turns have tabled. Err, tables... Anyway, I suppose I was the one who decided to step down. Some of my ideas didn't go so well and I wanted someone else to take over. Hey, this has to be more than a coincidence.");
                }
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        player.sendMessage(ChatColor.YELLOW + "[" + ChatColor.AQUA + "⊞" + ChatColor.GREEN + "Steve Ballmer" + ChatColor.YELLOW + "]" + ChatColor.AQUA + " Tell you what, I've got some things you might be able to use. I've got some computers that I bought, but never used. I also collected some of the things they put out of service at Microsoft. I won't charge you anything and I'll ship it for free.");
                    }
                }.runTaskLater(VMComputers.getPlugin(), 120);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        player.sendMessage(ChatColor.YELLOW + "[" + ChatColor.AQUA + "⊞" + ChatColor.GREEN + "Steve Ballmer" + ChatColor.YELLOW + "]" + ChatColor.AQUA + " You're welcome to pick from anything I have. Once you've decided what you'd like, I'll go ahead and ship it.");
                    }
                }.runTaskLater(VMComputers.getPlugin(), 240);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        ShopGUI.openMenu(player);
                    }
                }.runTaskLater(VMComputers.getPlugin(), 300);
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}