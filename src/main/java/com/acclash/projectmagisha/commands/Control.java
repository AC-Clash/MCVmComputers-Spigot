package com.acclash.projectmagisha.commands;

import com.acclash.projectmagisha.ProjectMagisha;
import com.acclash.projectmagisha.listeners.ControlListener;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

public class Control implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender instanceof Player player) {

            NamespacedKey cKey = new NamespacedKey(ProjectMagisha.getPlugin(), "isInControlMode");
            NamespacedKey sKey = new NamespacedKey(ProjectMagisha.getPlugin(), "isSpectating");
            if (!player.getPersistentDataContainer().has(cKey, PersistentDataType.STRING)) {
                player.getPersistentDataContainer().set(cKey, PersistentDataType.STRING, "false");
            }
            if (!player.getPersistentDataContainer().has(sKey, PersistentDataType.STRING)) {
                player.getPersistentDataContainer().set(sKey, PersistentDataType.STRING, "false");
            }
            if (player.getPersistentDataContainer().get(cKey, PersistentDataType.STRING).equals("true")) {
                String message = ChatColor.AQUA + "Out of Control Mode";
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
                player.getPersistentDataContainer().set(cKey, PersistentDataType.STRING, "false");
                if (player.getPersistentDataContainer().get(sKey, PersistentDataType.STRING).equals("true")) {
                    ServerPlayer sp = ((CraftPlayer) player).getHandle();
                    ClientboundSetCameraPacket setCameraPacket = new ClientboundSetCameraPacket(sp);
                    sp.connection.send(setCameraPacket);
                    if (ControlListener.controlled.get(player) != null && ControlListener.controlled.get(player).getPassengers().contains(ControlListener.passengers.get(player))) {
                        ControlListener.controlled.get(player).removePassenger(ControlListener.passengers.get(player));
                    }
                    if (ControlListener.passengers.get(player) != null && ControlListener.passengers.get(player).getPassengers().contains(player)) {
                        ControlListener.passengers.get(player).removePassenger(player);
                    }
                    player.getPersistentDataContainer().set(sKey, PersistentDataType.STRING, "false");
                }
            } else if (player.getPersistentDataContainer().get(cKey, PersistentDataType.STRING).equals("false")) {
                String message = ChatColor.AQUA + "In Control Mode";
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
                player.getPersistentDataContainer().set(cKey, PersistentDataType.STRING, "true");
            }

        }
        return true;
    }
}
