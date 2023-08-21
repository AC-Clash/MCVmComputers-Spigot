package com.acclash.vmcomputers.listeners;

import com.acclash.vmcomputers.VMComputers;
import com.acclash.vmcomputers.net.InboundHandler;
import io.netty.channel.ChannelPipeline;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.persistence.PersistentDataType;

import java.net.InetSocketAddress;
import java.util.NoSuchElementException;

public class PlayerListener implements Listener {

    @EventHandler
    public void onPlayerLoginEvent(PlayerLoginEvent e) {
        if (!e.getPlayer().hasMetadata("NPC")) {
            CraftPlayer p = (CraftPlayer) e.getPlayer();
            for (Connection c : MinecraftServer.getServer().getConnection().getConnections()) {
                // cheeky pointer equality check to know if we're targeting the right player.
                if (c.getRemoteAddress() instanceof InetSocketAddress) {
                    InetSocketAddress addr = (InetSocketAddress) c.getRemoteAddress();
                    if (addr.getAddress() == e.getAddress()) {
                        ChannelPipeline pipe = c.channel.pipeline();
                        // login might be called by another plugin twice
                        try {
                            pipe.remove(InboundHandler.NAME);
                        } catch (NoSuchElementException ignored) {
                        }
                        if (pipe.get("packet_handler") == null) {
                            pipe.addLast(InboundHandler.NAME, new InboundHandler(e.getPlayer()));
                        } else {
                            pipe.addBefore("packet_handler", InboundHandler.NAME, new InboundHandler(e.getPlayer()));
                        }
                        return;
                    }
                }
            }
            VMComputers.getPlugin().getLogger().warning("Failed to resolve \"" + e.getPlayer().getName() + "\"'s network pipeline. Messages sent to this user will be reportable!");
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {

        Player player = e.getPlayer();
        player.setResourcePack("https://www.dropbox.com/s/gvovwkn4mw11muu/Project%20Magisha.zip?dl=1", null, true);

    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {

        Player player = e.getPlayer();
        if (!player.isInsideVehicle()) return;
        if (player.getVehicle().getPersistentDataContainer().has(new NamespacedKey(VMComputers.getPlugin(), "isEChair"), PersistentDataType.STRING)) {
            e.setCancelled(true);
        }

    }
}
