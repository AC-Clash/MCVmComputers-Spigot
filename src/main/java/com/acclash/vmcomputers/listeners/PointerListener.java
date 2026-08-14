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
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

/**
 * Aims the guest's mouse by where the player is looking.
 *
 * <p>A ray is cast from the player's eyes to the screen plane and the hit point becomes the pointer
 * position. Nothing is ever cancelled or corrected, so there is no rubber-banding: the player looks
 * wherever they like and the pointer follows. RFB's PointerEvent is already absolute, so the hit
 * point maps onto it with no conversion.
 *
 * <p>Precision scales with proximity for free. Standing closer makes the screen cover more of the
 * view, so a degree of head movement crosses fewer pixels. Walking up to read small text also makes
 * it easier to click.
 */
public class PointerListener implements Listener {

    /** How far away a player can still drive a screen, in blocks. */
    private static final double MAX_RANGE = 24.0;

    // RFB button mask bits.
    private static final int BUTTON_LEFT = 1;
    private static final int BUTTON_RIGHT = 1 << 2;

    /** Where each player's pointer last landed, so clicks land at the current position. */
    private final java.util.Map<java.util.UUID, Aim> lastAim =
            new java.util.concurrent.ConcurrentHashMap<java.util.UUID, Aim>();

    private static final class Aim {
        final int computerId;
        /** Guest pixel, which is what the machine understands. */
        final int x;
        final int y;

        Aim(int computerId, int x, int y) {
            this.computerId = computerId;
            this.x = x;
            this.y = y;
        }
    }

    /**
     * The computer a player is currently aiming at, or null.
     *
     * <p>Lets the keyboard reuse the pointer's notion of "the screen you are looking at" rather
     * than making the player name a computer id for every keystroke.
     */
    public Integer targetComputerId(Player player) {
        Aim current = lastAim.get(player.getUniqueId());
        if (current == null) {
            current = aim(player);
        }
        return current == null ? null : Integer.valueOf(current.computerId);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        // Fires constantly, so bail out before doing any work when nothing is running.
        if (ComputerFunctions.getMachines().isEmpty()) {
            return;
        }
        Location to = e.getTo();
        if (to == null) {
            return;
        }
        // Position-only changes cannot alter where the ray lands from a standing player, but they
        // can when walking, so both are handled -- only skip when literally nothing moved.
        if (!hasChanged(e.getFrom(), to)) {
            return;
        }
        aim(e.getPlayer());
    }

    private static boolean hasChanged(Location from, Location to) {
        return from.getYaw() != to.getYaw() || from.getPitch() != to.getPitch()
                || from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ();
    }

    /** Traces the player's look ray and moves the guest pointer if it lands on a live screen. */
    private Aim aim(Player player) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        double[] origin = {eye.getX(), eye.getY(), eye.getZ()};
        double[] ray = {direction.getX(), direction.getY(), direction.getZ()};

        Aim best = null;
        MonitorScreen bestScreen = null;
        ScreenGeometry.Hit bestHit = null;
        double bestDistance = Double.MAX_VALUE;

        for (Computer computer : VMComputers.getPlugin().getRegistry().all()) {
            if (!computer.worldName().equals(player.getWorld().getName())) {
                continue;
            }
            VirtualMachine machine = ComputerFunctions.get(computer.id());
            if (machine == null || !machine.isRunning()) {
                continue;
            }
            if (eye.distanceSquared(computer.anchorLocation(player.getWorld())) > MAX_RANGE * MAX_RANGE) {
                continue;
            }
            MonitorScreen screen = VMComputers.getPlugin().getScreen(computer.id());
            if (screen == null) {
                continue;
            }

            ScreenGeometry.Hit hit = screen.geometry().trace(origin, ray);
            // A hit in the letterbox border is not on the guest image, so the pointer stays put
            // rather than jumping to a clamped edge position.
            if (hit == null || !hit.onImage || hit.distance > MAX_RANGE || hit.distance >= bestDistance) {
                continue;
            }
            bestDistance = hit.distance;
            // The ray lands on a displayed pixel; the guest only understands its own coordinates,
            // which differ whenever the image had to be scaled down to fit.
            best = new Aim(computer.id(), screen.toGuestX(hit.imageX), screen.toGuestY(hit.imageY));
            bestScreen = screen;
            bestHit = hit;
        }

        if (best == null) {
            Aim previous = lastAim.remove(player.getUniqueId());
            if (previous != null) {
                MonitorScreen screen = VMComputers.getPlugin().getScreen(previous.computerId);
                if (screen != null) {
                    screen.hideCursor();
                }
            }
            return null;
        }

        // Drawn at displayed coordinates, since that is the space the framebuffer is in.
        bestScreen.setCursor(bestHit.imageX, bestHit.imageY);

        lastAim.put(player.getUniqueId(), best);
        VirtualMachine machine = ComputerFunctions.get(best.computerId);
        if (machine != null) {
            // Queued, not written here: this runs on the server tick.
            machine.sendPointer(best.x, best.y, 0);
        }
        return best;
    }

    /** Attack and use become the left and right mouse buttons. */
    @EventHandler
    public void onClick(PlayerInteractEvent e) {
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return;
        }
        Action action = e.getAction();
        boolean left = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        boolean right = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        if (!left && !right) {
            return;
        }

        Aim target = aim(e.getPlayer());
        if (target == null) {
            return;
        }
        VirtualMachine machine = ComputerFunctions.get(target.computerId);
        if (machine == null) {
            return;
        }

        int mask = left ? BUTTON_LEFT : BUTTON_RIGHT;
        machine.sendPointer(target.x, target.y, mask);
        machine.sendPointer(target.x, target.y, 0);
        // Stop the click also punching the block behind the screen.
        e.setCancelled(true);
    }

    /** The hotbar becomes the scroll wheel, which is the only wheel vanilla can offer. */
    @EventHandler
    public void onScroll(PlayerItemHeldEvent e) {
        Aim target = lastAim.get(e.getPlayer().getUniqueId());
        if (target == null) {
            return;
        }
        VirtualMachine machine = ComputerFunctions.get(target.computerId);
        if (machine == null || !machine.isRunning()) {
            return;
        }

        int previous = e.getPreviousSlot();
        int current = e.getNewSlot();
        int delta = current - previous;
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
