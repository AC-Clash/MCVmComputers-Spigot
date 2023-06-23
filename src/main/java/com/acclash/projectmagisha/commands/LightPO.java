package com.acclash.projectmagisha.commands;


import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.BitSet;

public class LightPO implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender instanceof Player player) {
            CraftPlayer craftPlayer = (CraftPlayer) player;
            ServerPlayer serverPlayer = craftPlayer.getHandle();

            ServerGamePacketListenerImpl listener = serverPlayer.connection;

            ChunkPos chunk = new ChunkPos(player.getLocation().getChunk().getX(), player.getLocation().getChunk().getX());
            ServerLevel level = serverPlayer.serverLevel();
            LevelLightEngine lightEngine = level.getLightEngine();

            ClientboundLightUpdatePacket lightUpdatePacket = new ClientboundLightUpdatePacket(chunk, lightEngine, new BitSet(), new BitSet());

            listener.send(lightUpdatePacket);

            player.sendMessage(ChatColor.GOLD + "Sending light change...");
        }
        return true;
    }
}
