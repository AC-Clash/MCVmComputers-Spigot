package com.acclash.projectmagisha.commands;

import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftItemStack;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public class ArmSwingTest implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender instanceof Player player) {

            CraftPlayer craftPlayer = (CraftPlayer) player;
            ServerPlayer sp = craftPlayer.getHandle();
            ServerGamePacketListenerImpl ps = sp.connection;

            net.minecraft.world.item.ItemStack testItem = CraftItemStack.asNMSCopy(new ItemStack(Material.CARVED_PUMPKIN));
            ClientboundContainerSetSlotPacket setSlotPacket = new ClientboundContainerSetSlotPacket(sp.inventoryMenu.containerId, 0, 5, testItem);
            ps.send(setSlotPacket);

            player.swingMainHand();

            Mob grenade = (Mob) player.getWorld().spawnEntity(player.getLocation(), EntityType.OCELOT);
            grenade.setAware(false);
            Vector direction = player.getLocation().getDirection();
            grenade.setVelocity(direction.add(new Vector(0, 0.3, 0)).multiply(2));

            player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1, 1);
        }
        return true;
    }
}
