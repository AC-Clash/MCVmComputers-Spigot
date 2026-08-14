package com.acclash.vmcomputers.listeners;

import com.acclash.vmcomputers.VMComputers;
import com.acclash.vmcomputers.computer.Computer;
import com.acclash.vmcomputers.display.MonitorScreen;
import com.acclash.vmcomputers.display.ScreenGeometry;
import com.acclash.vmcomputers.emu.VirtualMachine;
import com.acclash.vmcomputers.utils.ComputerFunctions;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

/**
 * Treats the screen as a touchscreen: the guest's pointer is put where the player is looking at the
 * moment they click, and clicked there.
 *
 * <p>An earlier version tracked the pointer continuously and drew a cursor into the framebuffer.
 * That was both expensive and unnecessary. Expensive because every head movement repainted part of
 * the screen and dirtied map panels, twenty times a second, for as long as anyone looked around.
 * Unnecessary because Minecraft already draws a crosshair in the centre of the player's view --
 * that crosshair is the pointer, rendered client-side for free and always exactly accurate.
 *
 * <p>So nothing happens until a click, and a click costs one ray and three small packets. Looking
 * around a running computer now costs nothing at all.
 */
public class PointerListener implements Listener {

    /** How far away a player can still drive a screen, in blocks. */
    private static final double MAX_RANGE = 24.0;

    // RFB button mask bits.
    private static final int BUTTON_LEFT = 1;
    private static final int BUTTON_RIGHT = 1 << 2;

    /** Where a look ray landed on a running computer. */
    private static final class Target {
        final int computerId;
        /** Guest pixel, which is what the machine understands. */
        final int x;
        final int y;

        Target(int computerId, int x, int y) {
            this.computerId = computerId;
            this.x = x;
            this.y = y;
        }
    }

    /**
     * Casts the player's look ray at every running computer and returns the nearest screen it hits.
     *
     * <p>Only called on interaction, so the cost is one ray per click rather than one per movement
     * packet.
     */
    private Target trace(Player player) {
        if (ComputerFunctions.getMachines().isEmpty()) {
            return null;
        }

        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        double[] origin = {eye.getX(), eye.getY(), eye.getZ()};
        double[] ray = {direction.getX(), direction.getY(), direction.getZ()};

        Target best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Computer computer : VMComputers.getPlugin().getRegistry().all()) {
            if (!computer.worldName().equals(player.getWorld().getName())) {
                continue;
            }
            VirtualMachine machine = ComputerFunctions.get(computer.id());
            if (machine == null || !machine.isRunning()) {
                continue;
            }
            if (eye.distanceSquared(computer.anchorLocation(player.getWorld()))
                    > MAX_RANGE * MAX_RANGE) {
                continue;
            }
            MonitorScreen screen = VMComputers.getPlugin().getScreen(computer.id());
            if (screen == null) {
                continue;
            }

            ScreenGeometry.Hit hit = screen.geometry().trace(origin, ray);
            // A hit in the letterbox border is not on the guest image at all.
            if (hit == null || !hit.onImage || hit.distance > MAX_RANGE
                    || hit.distance >= bestDistance) {
                continue;
            }
            bestDistance = hit.distance;
            // The ray lands on a displayed pixel; the guest only understands its own coordinates,
            // which differ whenever the image had to be scaled down to fit.
            best = new Target(computer.id(),
                    screen.toGuestX(hit.imageX), screen.toGuestY(hit.imageY));
        }
        return best;
    }

    /**
     * The computer a player is looking at, or null. Used by the keyboard so typing goes to the
     * screen in front of you rather than one named by id.
     */
    public Integer targetComputerId(Player player) {
        Target target = trace(player);
        return target == null ? null : Integer.valueOf(target.computerId);
    }

    /** Attack and use become the left and right mouse buttons, wherever the crosshair is pointing. */
    @EventHandler
    public void onClick(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = e.getAction();
        boolean left = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        boolean right = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        if (!left && !right) {
            return;
        }

        Target target = trace(e.getPlayer());
        if (target == null) {
            return;
        }
        VirtualMachine machine = ComputerFunctions.get(target.computerId);
        if (machine == null) {
            return;
        }

        // Move first, then press, then release. Sending the position on its own gives the guest a
        // chance to update hover state before the button arrives, which some interfaces need in
        // order to register the click against the right widget.
        machine.sendPointer(target.x, target.y, 0);
        machine.sendPointer(target.x, target.y, left ? BUTTON_LEFT : BUTTON_RIGHT);
        machine.sendPointer(target.x, target.y, 0);

        // Stop the click also reaching the world behind the screen.
        e.setCancelled(true);
    }

    /** The hotbar becomes the scroll wheel, which is the only wheel vanilla can offer. */
    @EventHandler
    public void onScroll(PlayerItemHeldEvent e) {
        Target target = trace(e.getPlayer());
        if (target == null) {
            return;
        }
        VirtualMachine machine = ComputerFunctions.get(target.computerId);
        if (machine == null) {
            return;
        }

        int delta = e.getNewSlot() - e.getPreviousSlot();
        // The hotbar wraps between slots 8 and 0, so take the shorter way round.
        if (delta > 4) {
            delta -= 9;
        } else if (delta < -4) {
            delta += 9;
        }
        if (delta == 0) {
            return;
        }

        boolean up = delta < 0;
        for (int i = 0; i < Math.abs(delta); i++) {
            machine.sendScroll(target.x, target.y, up);
        }
    }
}
