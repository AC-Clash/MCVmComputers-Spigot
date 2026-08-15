package com.acclash.vmcomputers.listeners;

import com.acclash.vmcomputers.VMComputers;
import com.acclash.vmcomputers.computer.Computer;
import com.acclash.vmcomputers.computer.ComputerRegistry;
import com.acclash.vmcomputers.display.MonitorScreen;
import com.acclash.vmcomputers.display.ScreenGeometry;
import com.acclash.vmcomputers.emu.VirtualMachine;
import com.acclash.vmcomputers.utils.ComputerFunctions;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aims the guest's mouse by where the player is looking.
 *
 * <p>A ray is cast from the player's eyes to the screen plane and the hit point becomes the pointer
 * position. Nothing is ever cancelled or corrected, so there is no rubber-banding: the player looks
 * wherever they like and the pointer follows. RFB's PointerEvent is already absolute, so the hit
 * point maps onto it with no conversion.
 *
 * <p><strong>The pointer is deliberately invisible.</strong> Tracking exists so the guest receives
 * hover -- menus that open on mouseover, buttons that highlight, tooltips -- none of which a
 * click-only model can produce. Drawing the pointer is a separate question, and the answer is no:
 * a host-drawn cursor has to be painted into the framebuffer, which dirties map panels twenty times
 * a second for as long as anyone looks around, and every one of those repaints reaches the player a
 * map-packet later than their own head did. The result is a cursor visibly lagging the crosshair it
 * is trying to follow. Minecraft's own crosshair is already exactly where the pointer is, drawn
 * client-side and free, so the guest gets the position and the player gets the crosshair.
 *
 * <p>Aim is <strong>sampled once per tick</strong>, not taken from {@code PlayerMoveEvent}. See
 * {@link #tick()}: that event is not raised for a look that has swung less than ten degrees, which
 * makes it useless for aiming.
 *
 * <p>What remains is made as cheap as it can be: a player who has not moved at all is skipped before
 * any maths, the search runs over switched-on machines rather than every computer ever built, the
 * ray itself allocates nothing, and a position the guest already has is never sent twice. A player
 * sweeping their head across a screen costs a handful of floating-point operations per tick and a
 * six-byte packet, and no map traffic whatsoever.
 *
 * <p>Precision scales with proximity for free. Standing closer makes the screen cover more of the
 * view, so a degree of head movement crosses fewer pixels.
 *
 * <p>Clicks are harder than they look, because a screen is built out of item frames and an item
 * frame is an entity. Clicking an entity does not raise {@code PlayerInteractEvent} at all -- the
 * left button becomes an attack and the right button becomes an entity interaction -- so all three
 * paths have to be listened to or the buttons only work when aimed past arm's reach. It can be seen
 * with {@code /vmcomputers debug}, which draws the pointer.
 */
public class PointerListener implements Listener {

    /** How far away a player can still drive a screen, in blocks. */
    private static final double MAX_RANGE = 24.0;
    private static final double MAX_RANGE_SQUARED = MAX_RANGE * MAX_RANGE;

    // RFB button mask bits.
    private static final int BUTTON_LEFT = 1;
    private static final int BUTTON_RIGHT = 1 << 2;

    /**
     * The pointer position last handed to a machine, so the same one is never sent twice.
     *
     * <p>Keyed by computer rather than by player because a guest has exactly one pointer: two people
     * looking at the same pixel should cost one event, not two. The machine is kept alongside the
     * position so a power cycle invalidates the entry by itself -- a rebooted guest starts with its
     * pointer wherever it likes, and must not have the first move suppressed as a duplicate.
     */
    private final Map<Integer, Sent> lastSent = new ConcurrentHashMap<Integer, Sent>();

    private BukkitTask task;
    /** Reused by the sampler; only ever touched on the server thread. */
    private final Location scratch = new Location(null, 0, 0, 0);
    private final java.util.Map<java.util.UUID, Pose> poses =
            new java.util.HashMap<java.util.UUID, Pose>();

    /** How long a click blocks an identical one from another event path. One tick. */
    private static final long DUPLICATE_CLICK_NANOS = 50_000_000L;

    /** The last button each player delivered, for that de-duplication. */
    private final Map<java.util.UUID, Click> lastClick =
            new ConcurrentHashMap<java.util.UUID, Click>();

    private static final class Click {
        final int button;
        final long at;

        Click(int button, long at) {
            this.button = button;
            this.at = at;
        }
    }

    private static final class Sent {
        final VirtualMachine machine;
        /** Guest pixel packed as x &lt;&lt; 16 | y. */
        final int packed;

        Sent(VirtualMachine machine, int packed) {
            this.machine = machine;
            this.packed = packed;
        }
    }

    /** Where a look ray landed on a running computer. */
    private static final class Target {
        final int computerId;
        final VirtualMachine machine;
        final MonitorScreen screen;
        /** Guest pixel, which is what the machine understands. */
        final int x;
        final int y;
        /** The same point in screen pixels, which is what the debug cursor is drawn at. */
        final int screenX;
        final int screenY;

        Target(int computerId, VirtualMachine machine, MonitorScreen screen,
               int x, int y, int screenX, int screenY) {
            this.computerId = computerId;
            this.machine = machine;
            this.screen = screen;
            this.x = x;
            this.y = y;
            this.screenX = screenX;
            this.screenY = screenY;
        }
    }

    /**
     * Casts a look ray at every running computer and returns the nearest screen it hits.
     *
     * <p>Takes the eye and look angles rather than the player, because the one caller that matters
     * for cost is {@link #onMove}, where the player's own location is still the pre-move one.
     */
    private Target trace(Player player, double eyeX, double eyeY, double eyeZ,
                         float yaw, float pitch) {
        Map<Integer, VirtualMachine> machines = ComputerFunctions.getMachines();
        if (machines.isEmpty()) {
            return null;
        }

        // Minecraft's yaw runs clockwise from south and its pitch is negative looking up, which is
        // why this is easy to get subtly wrong by hand.
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        double cosPitch = Math.cos(pitchRadians);
        double dirX = -cosPitch * Math.sin(yawRadians);
        double dirY = -Math.sin(pitchRadians);
        double dirZ = cosPitch * Math.cos(yawRadians);

        VMComputers plugin = VMComputers.getPlugin();
        ComputerRegistry registry = plugin.getRegistry();
        String worldName = player.getWorld().getName();

        Target best = null;
        // Seeding the search at MAX_RANGE makes the range test and the nearest-hit test the same
        // comparison, since the direction above is a unit vector and the hit distance is in blocks.
        double bestDistance = MAX_RANGE;

        // Walking the running machines rather than the registry keeps this loop as long as the
        // number of switched-on computers, not the number ever built in the world.
        for (Map.Entry<Integer, VirtualMachine> entry : machines.entrySet()) {
            VirtualMachine machine = entry.getValue();
            if (!machine.isRunning()) {
                continue;
            }
            Computer computer = registry.byId(entry.getKey().intValue());
            if (computer == null || !computer.worldName().equals(worldName)) {
                continue;
            }
            // Cheap reject before any ray work, on raw doubles so no Location is built for it.
            double dx = computer.anchorX() - eyeX;
            double dy = computer.anchorY() - eyeY;
            double dz = computer.anchorZ() - eyeZ;
            if (dx * dx + dy * dy + dz * dz > MAX_RANGE_SQUARED) {
                continue;
            }
            MonitorScreen screen = plugin.getScreen(computer.id());
            if (screen == null) {
                continue;
            }

            ScreenGeometry.Hit hit = screen.geometry().trace(eyeX, eyeY, eyeZ, dirX, dirY, dirZ);
            // A hit in the letterbox border is not on the guest image, so the pointer stays put
            // rather than jumping to a clamped edge position.
            if (hit == null || !hit.onImage || hit.distance >= bestDistance) {
                continue;
            }
            bestDistance = hit.distance;
            // The ray lands on a displayed pixel; the guest only understands its own coordinates,
            // which differ whenever the image had to be scaled down to fit.
            best = new Target(computer.id(), machine, screen,
                    screen.toGuestX(hit.imageX), screen.toGuestY(hit.imageY),
                    hit.gridX, hit.gridY);
        }
        return best;
    }

    /** For the paths that are not the movement hot path and can afford the eye location. */
    private Target trace(Player player) {
        Location eye = player.getEyeLocation();
        return trace(player, eye.getX(), eye.getY(), eye.getZ(), eye.getYaw(), eye.getPitch());
    }

    /**
     * The computer a player is currently aiming at, or null.
     *
     * <p>Lets the keyboard reuse the pointer's notion of "the screen you are looking at" rather
     * than making the player name a computer id for every keystroke.
     */
    public Integer targetComputerId(Player player) {
        Target target = trace(player);
        return target == null ? null : Integer.valueOf(target.computerId);
    }

    /** Begins sampling player aim. Stopped on disable so no task outlives the plugin. */
    public void start(Plugin plugin) {
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /**
     * Samples where everyone is looking, once per tick.
     *
     * <p>Polling rather than listening to {@code PlayerMoveEvent}, which cannot drive a pointer.
     * CraftBukkit gates that event on
     * {@code squaredPositionDelta > 1/256 || |yawDelta| + |pitchDelta| > 10}, measured against the
     * last firing rather than the last packet -- so turning your head raises nothing at all until
     * the look has swung a cumulative <em>ten degrees</em>, and then it arrives in one jump. Ten
     * degrees is most of the width of a desk monitor at seating distance, which made aiming by
     * looking feel stuck while walking, which clears the position term on nearly every packet, felt
     * fine. Confirmed in Paper 26.2 bytecode, not inferred from the symptom.
     *
     * <p>A tick is also exactly the right rate: it is how often the client sends its position, and
     * how often a map can be redrawn, so sampling faster could not reach anyone anyway.
     */
    private void tick() {
        if (ComputerFunctions.getMachines().isEmpty()) {
            return;
        }
        boolean debug = VMComputers.getPlugin().isPointerDebug();

        for (Player player : Bukkit.getOnlinePlayers()) {
            // Fills a Location we own instead of allocating one per player per tick.
            Location at = player.getLocation(scratch);
            Pose pose = poses.get(player.getUniqueId());
            if (pose == null) {
                pose = new Pose();
                poses.put(player.getUniqueId(), pose);
            } else if (pose.matches(at)) {
                // Standing perfectly still: the ray cannot have moved, so do not cast it.
                continue;
            }
            pose.copyFrom(at);

            Target target = trace(player, at.getX(), at.getY() + player.getEyeHeight(), at.getZ(),
                    at.getYaw(), at.getPitch());
            if (target != null) {
                move(target);
            } else if (debug) {
                // Looking away leaves the drawn arrow stranded mid-screen, which reads as a freeze.
                // setCursor returns immediately when there is nothing to hide.
                for (MonitorScreen screen : VMComputers.getPlugin().screens()) {
                    screen.hideCursor();
                }
            }
        }
    }

    /** A player's last sampled aim. Mutable and reused, since this runs twenty times a second. */
    private static final class Pose {
        private double x;
        private double y;
        private double z;
        private float yaw;
        private float pitch;

        boolean matches(Location location) {
            return x == location.getX() && y == location.getY() && z == location.getZ()
                    && yaw == location.getYaw() && pitch == location.getPitch();
        }

        void copyFrom(Location location) {
            x = location.getX();
            y = location.getY();
            z = location.getZ();
            yaw = location.getYaw();
            pitch = location.getPitch();
        }
    }

    /**
     * Puts the guest pointer on a pixel, unless it is already there.
     *
     * <p>This is what makes continuous tracking affordable. A screen scaled down to 256x256 maps a
     * whole span of head movement onto one guest pixel, and a player standing still with a hand on
     * the mouse still produces a movement packet every tick; neither should reach the guest.
     */
    private void move(Target target) {
        if (VMComputers.getPlugin().isPointerDebug()) {
            // Drawn at screen coordinates, since that is the space the framebuffer is in. Outside
            // debug mode nothing is ever painted, which is the whole point.
            target.screen.setCursor(target.screenX, target.screenY);
        }

        int packed = (target.x << 16) | target.y;
        Integer id = Integer.valueOf(target.computerId);
        Sent previous = lastSent.get(id);
        if (previous != null && previous.machine == target.machine && previous.packed == packed) {
            return;
        }
        lastSent.put(id, new Sent(target.machine, packed));
        // Queued, not written here: this runs on the server tick.
        target.machine.sendPointer(target.x, target.y, 0);
    }

    /**
     * Left click, taken from the arm swing rather than from {@code PlayerInteractEvent}.
     *
     * <p>An item frame is an entity, and a screen is made of them. Left-clicking an entity is an
     * <em>attack</em>: the client sends it as such, the server raises
     * {@code EntityDamageByEntityEvent}, and {@code PlayerInteractEvent} is never fired at all. So
     * every left click that landed on a screen close enough to reach was silently swallowed, and
     * only clicks aimed past the reach limit -- which arrive as LEFT_CLICK_AIR -- ever worked. That
     * is why a button in an installer could not be pressed while sitting at the desk.
     *
     * <p>The arm swing has no such problem: it is sent for every left click regardless of what is
     * in front of the player, entity, block or nothing. It is also the only signal that survives
     * {@link PreventionListener} cancelling the damage to keep the frame from being broken.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        click(e.getPlayer(), BUTTON_LEFT);
    }

    /** Right click, when the ray passes the frames -- into the border, or beyond arm's reach. */
    @EventHandler
    public void onClick(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (click(e.getPlayer(), BUTTON_RIGHT)) {
            // Stop the click also reaching the world behind the screen.
            e.setCancelled(true);
        }
    }

    /**
     * Right click, when it lands on one of the screen's item frames.
     *
     * <p>Same blind spot as the left button: right-clicking an entity raises this instead of
     * {@code PlayerInteractEvent}, so without it the right button also stopped working as soon as
     * the player was close enough to touch the screen. Runs before {@link PreventionListener}
     * cancels the frame rotation at HIGH.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onRightClickEntity(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !(e.getRightClicked() instanceof ItemFrame)) {
            return;
        }
        if (click(e.getPlayer(), BUTTON_RIGHT)) {
            e.setCancelled(true);
        }
    }

    /** Presses and releases a button wherever the player is aiming. True if it hit a screen. */
    private boolean click(Player player, int button) {
        Target target = trace(player);
        if (target == null) {
            return false;
        }

        // One physical click can arrive down two paths. The screen's frames are fixed, and the
        // vanilla client treats a fixed frame as not having consumed the interaction, so it follows
        // the entity packet with a use-item packet -- raising PlayerInteractEntityEvent and then
        // PlayerInteractEvent for the same press. Both are needed, since which ones arrive depends
        // on what the ray hit, so the duplicate is dropped here instead. The window is one tick,
        // far below the gap between two clicks of a real double-click.
        long now = System.nanoTime();
        java.util.UUID id = player.getUniqueId();
        Click previous = lastClick.get(id);
        if (previous != null && previous.button == button
                && now - previous.at < DUPLICATE_CLICK_NANOS) {
            return true;
        }
        lastClick.put(id, new Click(button, now));

        // Tracking has usually already put the pointer here, in which case this costs nothing. It
        // is still attempted, because pressing at the wrong place is worse than a spare packet.
        move(target);
        target.machine.sendPointer(target.x, target.y, button);
        target.machine.sendPointer(target.x, target.y, 0);
        return true;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastClick.remove(e.getPlayer().getUniqueId());
        poses.remove(e.getPlayer().getUniqueId());
        // A listening link is tied to being on the server, so a tab left open goes quiet rather
        // than streaming a machine to someone who has walked away for good.
        if (VMComputers.getPlugin().getAudioService() != null) {
            VMComputers.getPlugin().getAudioService().revokeTokens(e.getPlayer().getUniqueId());
        }
    }

    /** The hotbar becomes the scroll wheel, which is the only wheel vanilla can offer. */
    @EventHandler
    public void onScroll(PlayerItemHeldEvent e) {
        Target target = trace(e.getPlayer());
        if (target == null) {
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

        // Keeps the tracked position honest: a wheel event carries coordinates of its own, so the
        // guest's pointer ends up here whether or not tracking had already moved it.
        move(target);

        boolean up = delta < 0;
        for (int i = 0; i < Math.abs(delta); i++) {
            target.machine.sendScroll(target.x, target.y, up);
        }
    }
}
