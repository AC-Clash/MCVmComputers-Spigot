package com.acclash.vmcomputers.net;

import com.acclash.vmcomputers.VMComputers;
import com.acclash.vmcomputers.utils.ComputerFunctions;
import com.acclash.vmcomputers.utils.NetworkUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.v1_20_R2.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.NoSuchElementException;
import java.util.UUID;

public class InboundHandler extends ChannelInboundHandlerAdapter {

    Entity vehicle;

    public static final String NAME = "com.acclash.vmcomputers:inbound_handler";

    public final Player player;
    public final UUID playerUUID;

    long lastAttack = 0;
    long cooldownTime = 1000; // 1000 milliseconds

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
        if (msg instanceof ServerboundPlayerInputPacket) {
            ServerboundPlayerInputPacket input = (ServerboundPlayerInputPacket) msg;
                vehicle = player.getVehicle();
            if (vehicle == null) return;
            boolean jumping = input.isJumping();
            if (jumping) {
                if (vehicle.getPersistentDataContainer().has(new NamespacedKey(VMComputers.getPlugin(), "isEChair"), PersistentDataType.STRING)) {
                    long time = System.currentTimeMillis();
                    if (time > lastAttack + cooldownTime) {
                        // send input
                        lastAttack = time;
                    }
                }
            }
        }
        super.channelRead(ctx, msg);
    }
}
