package me.acclashcorporation.project_magisha.commands;

import me.acclashcorporation.project_magisha.Project_Magisha;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class Magisha implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender instanceof Player) {

            Player player = (Player) sender;

            if (args.length == 0) {
                player.sendMessage(ChatColor.RED + "You need to enter some arguments.");
                player.sendMessage(ChatColor.YELLOW + "To run the Magisha command: /magisha <classified item> <pitch> <times to run>");
            } else if (args.length == 1) {
                player.sendMessage(ChatColor.RED + "You need to enter some more arguments.");
                player.sendMessage(ChatColor.YELLOW + "To run the Magisha command: /magisha <classified item> <pitch> <times to run>");
            } else if (args.length == 2) {
                player.sendMessage(ChatColor.RED + "You need to enter some more arguments.");
                player.sendMessage(ChatColor.YELLOW + "To run the Magisha command: /magisha <classified item> <pitch> <times to run>");
            } else if (args.length == 3) {
                new BukkitRunnable() {
                    private int tick;

                    @Override
                    public void run() {
                        if (tick >= (Integer.parseInt(args[2]) - 1)) {
                            cancel();
                        }
                        try {
                            player.getWorld().playSound(player.getLocation(), Sound.valueOf("MUSIC_DISC_" + args[0].toUpperCase()), 10, Float.parseFloat(args[1]));
                        } catch (IllegalArgumentException e) {
                            cancel();
                            player.sendMessage(ChatColor.RED + "That didn't work. Please check your spelling and try again.");
                        }
                        tick++;
                    }
                }.runTaskTimer(Project_Magisha.getPlugin(), 0L, 2L);

            } else {
                player.sendMessage(ChatColor.RED + "Too many arguments.");
                player.sendMessage(ChatColor.YELLOW + "To run the Magisha command: /magisha <classified item> <pitch> <times to run>");
            }
        }
        return true;
    }
}
