package com.acclash.vmcomputers.display;

import com.acclash.vmcomputers.VMComputers;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.map.MapView;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pushes changed screen panels to their viewers, once per tick.
 *
 * <h2>Why this exists</h2>
 *
 * <p>The server will not do it often enough. A map in an item frame is updated from
 * {@code ServerEntity.sendChanges()}, and that code is gated:
 *
 * <pre>if (!trackedPlayers.isEmpty() &amp;&amp; entity instanceof ItemFrame
 *        &amp;&amp; tickCount % itemFrameCursorUpdateInterval == 0) { ... send map ... }</pre>
 *
 * <p>Vanilla and Spigot hardcode that interval to <b>10 ticks</b>; Paper made it configurable as
 * {@code maps.item-frame-cursor-update-interval} and kept 10 as the default. Ten ticks is half a
 * second, so a screen built from item frames repaints at <b>2 frames per second</b> no matter what
 * anything else does. Emulator speed, transport, quantization and panel count were all irrelevant
 * next to it, and no amount of making them faster could have shown up.
 *
 * <p>{@link Player#sendMap} is the whole answer, and it is plain Bukkit: it renders a map and sends
 * it to one player immediately. Driving it here gives a 20 fps ceiling -- one frame per tick, which
 * is as often as a map can be redrawn at all -- on Spigot and Paper alike, with no server config to
 * get right. <b>NMS was never the obstacle.</b>
 *
 * <h2>What it costs</h2>
 *
 * <p>{@code sendMap} always sends a full 128x128 patch, so it gives up the server's dirty-rectangle
 * trick, where an unchanged pixel costs nothing on the wire. That trades bytes for a tenfold frame
 * rate, and the trade is only worth making if the bytes are kept honest, so:
 *
 * <ul>
 *   <li>A panel is sent only when its pixels actually differ from the ones already sent --
 *       {@link PanelRenderer#blit} compares before it copies. An idle guest sends nothing at all.</li>
 *   <li>Only players close enough to read the screen are sent anything.</li>
 *   <li>A player who has just come into range gets every panel once, since they have no picture
 *       yet; after that they get only what changes.</li>
 *   <li>A budget caps how much any one player is sent per tick. Past it, the remaining panels wait
 *       for the next tick, and the starting panel rotates so a screen changing faster than the
 *       budget allows loses frame rate evenly instead of leaving one corner frozen.</li>
 * </ul>
 *
 * <p>The one thing left on the table is sub-panel patches: sending only the changed rectangle of a
 * panel needs the packet built by hand, which is NMS. That would cut bytes, not add frames -- the
 * rate would still be one tick. It is not worth a version-specific jar.
 */
public final class ScreenPump {

    /**
     * How far away a player is still sent frames, in blocks.
     *
     * <p>Well past the point where a 128-pixel map is legible, and comfortably inside the range at
     * which the client stops rendering the frames at all.
     */
    private static final double VIEW_RANGE = 64.0;
    private static final double VIEW_RANGE_SQUARED = VIEW_RANGE * VIEW_RANGE;

    /**
     * Panels one player may be sent per tick, across every screen they can see.
     *
     * <p>Each is 16 KB, so this is the ceiling on what a player costs: 12 panels a tick is about
     * 3.9 MB/s, and only while something is changing everywhere on screen every single tick. Twelve
     * also happens to be a whole LARGE monitor, so every desk size can repaint completely at 20 fps
     * and only the 24-panel projector has to share across ticks.
     */
    private static final int PANEL_BUDGET_PER_PLAYER = 12;

    private final VMComputers plugin;
    private BukkitTask task;

    /** Per-screen sending state, keyed by computer id. */
    private final Map<Integer, ScreenState> states = new HashMap<Integer, ScreenState>();
    /** Reused each tick: how much of their budget each player has spent. */
    private final Map<UUID, int[]> spent = new HashMap<UUID, int[]>();
    private final List<Player> viewers = new ArrayList<Player>();
    private final Set<UUID> present = new HashSet<UUID>();
    private long[] generations = new long[0];

    private static final class ScreenState {
        /**
         * The generation of each panel that a viewer has actually been sent.
         *
         * <p>Per viewer rather than per screen because the budget can serve one player and not
         * another in the same tick. A player with no entry has seen nothing and gets everything.
         */
        final Map<UUID, long[]> delivered = new HashMap<UUID, long[]>();
        /** Where to start sending, so a truncated tick does not always drop the same panels. */
        int rotation;
    }

    public ScreenPump(VMComputers plugin) {
        this.plugin = plugin;
    }

    public void start(Plugin owner) {
        this.task = Bukkit.getScheduler().runTaskTimer(owner, this::tick, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        Collection<MonitorScreen> screens = plugin.screens();
        if (screens.isEmpty()) {
            return;
        }
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        if (online.isEmpty()) {
            return;
        }

        spent.clear();
        for (MonitorScreen screen : screens) {
            pump(screen, online);
        }
    }

    private void pump(MonitorScreen screen, Collection<? extends Player> online) {
        List<PanelRenderer> panels = screen.panels();
        List<MapView> views = screen.mapViews();
        int count = panels.size();
        if (count == 0) {
            return;
        }

        ScreenState state = states.get(Integer.valueOf(screen.computer().id()));
        if (state == null) {
            state = new ScreenState();
            states.put(Integer.valueOf(screen.computer().id()), state);
        }

        collectViewers(screen, online);
        if (viewers.isEmpty()) {
            // Nobody is watching. Their delivery records go with them, so whoever walks up next is
            // treated as new and gets the current picture rather than resuming a stale one.
            state.delivered.clear();
            return;
        }

        // Read the generations once, so every viewer this tick is compared against the same
        // picture even though the frame thread keeps writing underneath.
        if (generations.length < count) {
            generations = new long[count];
        }
        for (int i = 0; i < count; i++) {
            generations[i] = panels.get(i).generation();
        }

        present.clear();
        int furthest = 0;
        for (Player player : viewers) {
            UUID id = player.getUniqueId();
            present.add(id);
            long[] delivered = state.delivered.get(id);
            if (delivered == null || delivered.length != count) {
                // Never seen this screen, so nothing can be skipped as already known.
                delivered = new long[count];
                state.delivered.put(id, delivered);
            }
            int sent = send(player, views, count, state.rotation, delivered);
            furthest = Math.max(furthest, sent);
        }

        state.delivered.keySet().retainAll(present);
        // Move past what went out, so a screen changing faster than the budget allows loses frame
        // rate evenly instead of leaving the same corner frozen.
        if (furthest > 0) {
            state.rotation = (state.rotation + furthest) % count;
        }
    }

    /** Sends what this player is missing, within their budget. Returns how many went out. */
    private int send(Player player, List<MapView> views, int count, int rotation,
                     long[] delivered) {
        int[] budget = spent.get(player.getUniqueId());
        if (budget == null) {
            budget = new int[]{0};
            spent.put(player.getUniqueId(), budget);
        }

        int sent = 0;
        for (int step = 0; step < count; step++) {
            if (budget[0] >= PANEL_BUDGET_PER_PLAYER) {
                break;
            }
            int index = (rotation + step) % count;
            if (delivered[index] == generations[index]) {
                continue;
            }
            player.sendMap(views.get(index));
            delivered[index] = generations[index];
            budget[0]++;
            sent++;
        }
        return sent;
    }

    private void collectViewers(MonitorScreen screen, Collection<? extends Player> online) {
        viewers.clear();
        int[] anchor = screen.computer().blockAt(screen.computer().layout().screenBottomLeft());
        String world = screen.computer().worldName();

        for (Player player : online) {
            if (!player.getWorld().getName().equals(world)) {
                continue;
            }
            Location at = player.getLocation();
            double dx = anchor[0] - at.getX();
            double dy = anchor[1] - at.getY();
            double dz = anchor[2] - at.getZ();
            if (dx * dx + dy * dy + dz * dz <= VIEW_RANGE_SQUARED) {
                viewers.add(player);
            }
        }
    }

    /** Forgets a screen's send state, so a rebuilt computer starts clean. */
    public void forget(int computerId) {
        states.remove(Integer.valueOf(computerId));
    }
}
