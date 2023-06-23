package com.acclash.projectmagisha.listeners;

import com.acclash.projectmagisha.ProjectMagisha;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.world.entity.Entity;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Objects;

public class ControlListener implements Listener {

    public static HashMap<Player, Mob> controlled = new HashMap<>();

    public static HashMap<Player, Mob> passengers = new HashMap<>();

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent e) {

        if (e.getDamager() instanceof Player player && e.getEntity() instanceof Mob mob) {
            NamespacedKey cKey = new NamespacedKey(ProjectMagisha.getPlugin(), "isInControlMode");
            NamespacedKey sKey = new NamespacedKey(ProjectMagisha.getPlugin(), "isSpectating");
            if (Objects.equals(player.getPersistentDataContainer().get(cKey, PersistentDataType.STRING), "true")) {
                player.getPersistentDataContainer().set(sKey, PersistentDataType.STRING, "true");
                controlled.put(player, mob);
                Mob passenger = (Mob) player.getWorld().spawnEntity(player.getLocation(), EntityType.CHICKEN);
                passengers.put(player, passenger);
                mob.setAware(false);
                passenger.setAware(false);
                passenger.setInvisible(true);
                Entity entity = ((CraftEntity) mob).getHandle();
                ClientboundSetCameraPacket setCameraPacket = new ClientboundSetCameraPacket(entity);
                ((CraftPlayer) player).getHandle().connection.send(setCameraPacket);
                if (mob.getCustomName() != null) {
                    String message = ChatColor.GREEN + "Now controlling " + mob.getCustomName();
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
                } else {
                    String message = ChatColor.GREEN + "Now controlling " + mob.getName();
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
                }

            }
        }


    }

    @EventHandler
    public void onActionTitle(AsyncPlayerChatEvent e) {
        e.getPlayer().sendMessage("fired");
    }

}
