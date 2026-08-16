package com.acclash.vmcomputers.listeners;

import com.acclash.vmcomputers.emu.VirtualMachine;
import com.acclash.vmcomputers.parts.EChair;
import com.acclash.vmcomputers.utils.ComputerFunctions;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns a seated player's movement keys into keystrokes in the guest.
 *
 * <p>While a player rides anything, their client sends its raw movement state to the server every
 * time it changes. That is a keyboard: five keys, with no client mod, no resource pack and nothing
 * for the player to install -- which is the whole constraint this project is built around.
 *
 * <h2>Held, not tapped</h2>
 *
 * A game needs to know that forward is <em>being held</em>, not that it was pressed once, so the
 * keys are sent as presses and releases. That makes the held state something this class owns and
 * has to be careful with: a key sent down and never sent up is stuck down in the guest forever,
 * long after the player has wandered off. Every way out of a chair therefore ends up at
 * {@link #releaseAll} -- standing up, disconnecting, or the chair being taken away.
 *
 * <h2>Why the keys are recorded, not the profile</h2>
 *
 * Held keys are stored as the keysyms they were actually sent as. A player can switch profile or
 * rebind mid-keypress, and recording the profile instead would leave {@code w} down in the guest
 * while {@code Up} started arriving, with nothing left that could ever release the first one.
 */
public class EChairListener implements Listener {

    /**
     * What a seated player currently has held, and the exact keys it was sent as.
     *
     * <p>The keysyms are recorded rather than the mode they came from. A player can switch profile
     * or rebind a key while still holding it, and the only thing that can release what was
     * actually pressed is what was actually pressed.
     */
    private static final class Seated {
        private final int computerId;
        private final int[] keysyms;
        private final boolean[] down;

        Seated(int computerId, int[] keysyms, boolean[] down) {
            this.computerId = computerId;
            this.keysyms = keysyms;
            this.down = down;
        }
    }

    private final Map<UUID, Seated> seated = new ConcurrentHashMap<UUID, Seated>();

    /**
     * Movement changed. Fires for every player, so the not-in-a-chair case has to be the cheap one.
     */
    @EventHandler
    public void onInput(PlayerInputEvent event) {
        Player player = event.getPlayer();
        Entity chair = EChair.seatOf(player);
        if (chair == null) {
            // Covers standing up in ways that raise no dismount event of their own.
            releaseAll(player);
            return;
        }

        int computerId = EChair.computerIdOf(chair);
        VirtualMachine machine = ComputerFunctions.get(computerId);
        if (machine == null || !machine.isRunning()) {
            return;
        }

        int[] keysyms = EChair.bindingsOf(player, EChair.modeOf(player));
        Seated previous = seated.get(player.getUniqueId());

        // Switching profile or rebinding between packets is handled by letting go under the old
        // keys first, so nothing is left held by a key nothing maps to any more.
        if (previous != null && !java.util.Arrays.equals(previous.keysyms, keysyms)) {
            send(machine, previous, false);
            previous = null;
        }

        boolean[] now = EChair.read(event.getInput());
        boolean[] before = previous == null ? new boolean[EChair.KEYS.length] : previous.down;
        for (int i = 0; i < now.length; i++) {
            if (now[i] != before[i]) {
                machine.sendKey(keysyms[i], now[i]);
            }
        }
        seated.put(player.getUniqueId(), new Seated(computerId, keysyms, now));
    }

    /** Standing up lets go of everything. */
    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player && EChair.is(event.getDismounted())) {
            releaseAll((Player) event.getEntity());
        }
    }

    /** So does leaving. A key held at the moment of a disconnect would stay down forever. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        releaseAll(event.getPlayer());
    }

    /**
     * Swapping hands switches the mapping, because it is the one key a seated player has spare.
     *
     * <p>Cancelled so the hands do not actually swap. There is {@code /vmcomputers keys} as well;
     * this exists so that switching between driving a game and navigating a menu does not mean
     * opening the chat window in the middle of either.
     */
    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (EChair.seatOf(player) == null) {
            return;
        }
        event.setCancelled(true);
        switchMode(player, EChair.modeOf(player).other());
    }

    /**
     * Moves a player to a different mapping, letting go of anything held under the old one.
     *
     * <p>Shared with the command, so both routes are safe mid-keypress.
     */
    public void switchMode(Player player, EChair.KeyMode mode) {
        Seated held = seated.remove(player.getUniqueId());
        if (held != null) {
            VirtualMachine machine = ComputerFunctions.get(held.computerId);
            if (machine != null && machine.isRunning()) {
                send(machine, held, false);
            }
        }
        EChair.setMode(player, mode);
        player.sendMessage(ChatColor.AQUA + "Chair keys: " + ChatColor.WHITE + mode.keys()
                + ChatColor.GRAY + " (" + mode.purpose() + ")");
        player.playSound(player.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.6f,
                mode == EChair.KeyMode.GAME ? 1.4f : 0.9f);
    }

    /** Lets go of every key this player is holding in the guest, if any. */
    public void releaseAll(Player player) {
        Seated held = seated.remove(player.getUniqueId());
        if (held == null) {
            return;
        }
        VirtualMachine machine = ComputerFunctions.get(held.computerId);
        if (machine != null && machine.isRunning()) {
            send(machine, held, false);
        }
    }

    /** Sends one edge for every key recorded as held, as the key it was actually pressed as. */
    private void send(VirtualMachine machine, Seated held, boolean pressed) {
        for (int i = 0; i < held.down.length; i++) {
            if (held.down[i]) {
                machine.sendKey(held.keysyms[i], pressed);
            }
        }
    }
}
