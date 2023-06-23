package com.acclash.projectmagisha.listeners;

import com.acclash.projectmagisha.util.NetworkUtil;
import com.acclash.projectmagisha.ProjectMagisha;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

public class InboundHandler extends ChannelInboundHandlerAdapter {
    Entity vehicle;

    Vector total;

    public static final String NAME = "com.acclash.project_magisha:inbound_packet_handler";

    public final Player player;
    public final UUID playerUUID;

    public InboundHandler(Player player) {
        this.player = player;
        this.playerUUID = player.getUniqueId();
    }

    public static void attach(Player player) {
        ChannelPipeline pipe = NetworkUtil.getConnection(((CraftPlayer) player).getHandle().connection).channel.pipeline();
        detach(player);
        pipe.addBefore("packet_handler", NAME, new InboundHandler(player));
    }

    public static void detach(Player player) {
        try {
            NetworkUtil.getConnection(((CraftPlayer) player).getHandle().connection).channel.pipeline().remove(NAME);
        } catch (NoSuchElementException ignored) {}
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ServerboundPlayerInputPacket input) {
            if (Objects.equals(player.getPersistentDataContainer().get(new NamespacedKey(ProjectMagisha.getPlugin(), "isInControlMode"), PersistentDataType.STRING), "true")) {
                vehicle = ControlListener.controlled.get(player);
            } else {
                vehicle = player.getVehicle();
            }
            if (vehicle == null) return;
            float swSpeed = input.getXxa();
            float fwSpeed = input.getZza();
            boolean jumping = input.isJumping();
            if (vehicle.getPersistentDataContainer().has(new NamespacedKey(ProjectMagisha.getPlugin(), "isDARPATrain"), PersistentDataType.STRING) || vehicle.getType() == EntityType.FALLING_BLOCK || vehicle.getType() == EntityType.CHICKEN || Objects.equals(player.getPersistentDataContainer().get(new NamespacedKey(ProjectMagisha.getPlugin(), "isInControlMode"), PersistentDataType.STRING), "true")) {
                player.sendMessage(String.valueOf(fwSpeed));
                player.sendMessage(String.valueOf(swSpeed));
                Location pLoc = player.getLocation().clone();
                vehicle.setRotation(pLoc.getYaw(), pLoc.getPitch()); // PITCH COULD BE -- pLoc.getPitch()
                Vector forwardDir = pLoc.getDirection();
                Vector sideways = forwardDir.clone().crossProduct(new Vector(0, -1, 0));
                total = forwardDir.multiply(fwSpeed / 4).add(sideways.multiply(swSpeed / 4));
                total.setY(0);
                vehicle.setVelocity(vehicle.getVelocity().add(total));
            }
            if (jumping) {
                if (vehicle.getType() == EntityType.CHICKEN) {
                    ComputerManager.sendSpaceInput(player, vehicle);
                } else if (vehicle.getPersistentDataContainer().has(new NamespacedKey(ProjectMagisha.getPlugin(), "isDARPATrain"), PersistentDataType.STRING)) {
                    vehicle.setVelocity(vehicle.getVelocity().setY(1));
                } else {
                    player.sendMessage("po");
                    total.setY(vehicle.isOnGround() ? 0.5 : 0);
                }
            }
        }
        super.channelRead(ctx, msg);
    }
}
