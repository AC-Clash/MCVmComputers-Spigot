package com.acclash.vmcomputers.listeners;

import com.acclash.vmcomputers.VMComputers;
import com.acclash.vmcomputers.parts.EChair;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Player-facing behaviour that is not tied to a computer.
 *
 * <p>There is no resource pack. Every player who joined used to be pushed one from a Dropbox link
 * that has been dead for years, with {@code force = true}, which kicks anyone whose download
 * fails -- so the only thing it reliably did was refuse entry. It was there for guest audio, and
 * nothing else ever needed it: components are display entities and player heads, and the screen is
 * item-framed maps, precisely so that a vanilla client needs nothing.
 */
public class PlayerListener implements Listener {

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
        if (!EChair.is(player.getVehicle())) {
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
