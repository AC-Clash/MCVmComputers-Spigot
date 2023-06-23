package com.acclash.projectmagisha.commands;

import com.acclash.projectmagisha.goals.FollowPlayerGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPanda;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Panda;
import org.bukkit.entity.Player;

import java.util.function.Predicate;

public class SummonPO implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender instanceof Player player) {

            Panda po = (Panda) player.getWorld().spawnEntity(player.getLocation(), EntityType.PANDA);
            player.sendMessage(ChatColor.GREEN + "Summoned Po");
            net.minecraft.world.entity.animal.Panda nmsPo = ((CraftPanda) po).getHandle();
            nmsPo.goalSelector.removeAllGoals((Predicate<Goal>) nmsPo.goalSelector.getRunningGoals());
            nmsPo.goalSelector.addGoal(1, new FollowPlayerGoal(nmsPo, 2, player));
            player.sendMessage(ChatColor.GREEN + "Added goal");

            //po.setEating(true);
            po.setSitting(true);
            //po.setSneezing(true);
            po.setMainGene(Panda.Gene.PLAYFUL);
            po.setHiddenGene(Panda.Gene.AGGRESSIVE);
            po.setCustomName(ChatColor.GOLD + "Po");
            po.setCustomNameVisible(true);
            po.setGliding(true);

            player.sendMessage(ChatColor.YELLOW + "[" + ChatColor.GOLD + "Po" + ChatColor.YELLOW + "]" + ChatColor.GOLD + " Hello, I am Po. How are you?");

        }

        return true;
    }
}
