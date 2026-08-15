package com.acclash.vmcomputers.listeners;

import com.acclash.vmcomputers.VMComputers;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.persistence.PersistentDataType;

public class PlayerListener implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {

        Player player = e.getPlayer();
        player.setResourcePack("https://www.dropbox.com/s/gvovwkn4mw11muu/Project%20Magisha.zip?dl=1", (byte[]) null, true);

    }

    /**
     * Keeps a seated player in their chair without freezing their head.
     *
     * <p>Cancelling every move event also cancels rotation, and the client predicts rotation
     * locally, so the correction shows up as jitter. Since the pointer is aimed by looking at the
     * screen, rotation has to stay completely untouched -- only actual position changes are
     * refused.
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {
        Player player = e.getPlayer();
        if (!player.isInsideVehicle()) return;
        if (!player.getVehicle().getPersistentDataContainer()
                .has(new NamespacedKey(VMComputers.getPlugin(), "isEChair"), PersistentDataType.STRING)) {
            return;
        }

        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;

        boolean moved = from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ();
        if (moved) {
            // Preserve the new look direction while refusing the positional change.
            Location held = from.clone();
            held.setYaw(to.getYaw());
            held.setPitch(to.getPitch());
            e.setTo(held);
        }
    }
}
