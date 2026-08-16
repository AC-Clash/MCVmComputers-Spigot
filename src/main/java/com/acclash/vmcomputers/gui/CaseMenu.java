package com.acclash.vmcomputers.gui;

import com.acclash.vmcomputers.VMComputers;
import com.acclash.vmcomputers.computer.Computer;
import com.acclash.vmcomputers.computer.ComputerBuilder;
import com.acclash.vmcomputers.display.MonitorScreen;
import com.acclash.vmcomputers.emu.VmService;
import com.acclash.vmcomputers.parts.ComponentSlot;
import com.acclash.vmcomputers.parts.ComponentType;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The inside of a computer: what is fitted, what disc is in the drive, and whether it runs.
 *
 * <p>Opened by shift-right-clicking the tower. A plain right-click still toggles power, which is
 * a deliberate divergence from the mod -- there the power button lives inside this menu, and
 * turning a machine on costs two clicks and a menu. Keeping the common action on the bare click
 * and the configuration behind a modifier reverses that trade.
 *
 * <p>Installing takes the part out of the player's inventory; removing hands it back. Nothing is
 * destroyed by either, so a machine can be taken apart and rebuilt without losing hardware.
 */
public class CaseMenu extends Menu {

    private static final int SIZE = 27;

    private static final int ISO_SLOT = 22;
    private static final int POWER_SLOT = 26;
    private static final int STATUS_SLOT = 18;

    /** Bay slots along the middle row, in enum order. */
    private static final int SLOT_ROW = 1;

    /** How often to check whether the machine's power state still matches what is on screen. */
    private static final int WATCH_TICKS = 5;

    private final Computer computer;
    private final Map<Integer, ComponentSlot> bays = new HashMap<Integer, ComponentSlot>();

    /** What the power slot was last drawn from, so a redraw only happens when it changes. */
    private String shownState = "";

    public CaseMenu(Player viewer, Computer computer) {
        super(viewer);
        this.computer = computer;
    }

    @Override
    public String title() {
        return ChatColor.DARK_GRAY + "Computer #" + computer.id();
    }

    @Override
    public int size() {
        return SIZE;
    }

    @Override
    public void draw() {
        bays.clear();
        fillRow(0);
        fillRow(2);

        ComponentSlot[] slots = ComponentSlot.values();
        int start = SLOT_ROW * ROW + Math.max(0, (ROW - slots.length) / 2);
        for (int i = 0; i < slots.length; i++) {
            ComponentSlot slot = slots[i];
            ComponentType fitted = computer.installedIn(slot);
            int index = start + i;
            bays.put(Integer.valueOf(index), slot);

            if (fitted != null) {
                ItemStack stack = fitted.toItemStack(1);
                withLore(stack, "", ChatColor.GRAY + slot.label(),
                        ChatColor.YELLOW + "Click to remove");
                set(index, stack);
            } else {
                set(index, button(slot.emptyStack(),
                        (slot.required() ? ChatColor.RED : ChatColor.GRAY) + slot.label()
                                + (slot.required() ? " (required)" : " (optional)"),
                        slot.description(),
                        "",
                        ChatColor.YELLOW + "Click to fit one from your inventory"));
            }
        }

        drawStatus();
        drawIso();
        drawPower();
    }

    private void drawStatus() {
        List<ComponentSlot> missing = computer.missingComponents();
        if (missing.isEmpty()) {
            set(STATUS_SLOT, button(Material.LIME_DYE, ChatColor.GREEN + "Ready",
                    "All required parts are fitted.",
                    computer.architecture() + ", " + computer.monitorSize().describe()));
            return;
        }
        StringBuilder names = new StringBuilder();
        for (ComponentSlot slot : missing) {
            names.append(names.length() == 0 ? "" : ", ").append(slot.label());
        }
        set(STATUS_SLOT, button(Material.RED_DYE, ChatColor.RED + "Incomplete",
                "Still needs: " + names,
                "It will not power on until then."));
    }

    private void drawIso() {
        String iso = computer.isoName();
        set(ISO_SLOT, button(iso == null ? Material.MUSIC_DISC_11 : Material.MUSIC_DISC_CAT,
                iso == null ? ChatColor.GRAY + "Drive empty" : ChatColor.AQUA + iso,
                iso == null ? "No disc inserted." : "Disc in the drive.",
                "",
                ChatColor.YELLOW + "Click to change disc"));
    }

    private void drawPower() {
        shownState = powerToken();

        // Three answers, not two. A machine that has been asked to start is neither on nor off,
        // and showing it as either is a lie the player can see through -- the screen is still
        // black, or still has the guest's desktop on it.
        VmService.Transition moving = VmService.transitionOf(computer.id());
        if (moving != null) {
            boolean up = moving == VmService.Transition.STARTING;
            set(POWER_SLOT, button(Material.CLOCK,
                    ChatColor.YELLOW + (up ? "Starting up..." : "Shutting down..."),
                    up ? "QEMU is coming up." : "Waiting for the guest to close down.",
                    "This can take a few seconds."));
            return;
        }

        boolean running = VmService.isRunning(computer.id());
        set(POWER_SLOT, button(running ? Material.REDSTONE_TORCH : Material.LEVER,
                running ? ChatColor.GREEN + "Running" : ChatColor.GRAY + "Powered off",
                running ? "Click to shut down." : "Click to power on.",
                "",
                "Right-clicking the tower does",
                "this without opening the menu."));
    }

    /**
     * What the power slot is currently showing, as one comparable value.
     *
     * <p>The watcher redraws when this stops matching, so it has to fold every distinguishable
     * situation into one token -- otherwise a machine going from starting to running would not
     * count as a change and the clock would sit there forever.
     */
    private String powerToken() {
        VmService.Transition moving = VmService.transitionOf(computer.id());
        return moving != null
                ? moving.name()
                : Boolean.toString(VmService.isRunning(computer.id()));
    }

    @Override
    public void onClick(int slot, ClickType click) {
        if (slot == POWER_SLOT) {
            togglePower();
            return;
        }
        if (slot == ISO_SLOT) {
            new IsoMenu(viewer, computer, this).open();
            return;
        }

        ComponentSlot bay = bays.get(Integer.valueOf(slot));
        if (bay == null) {
            return;
        }
        if (computer.installedIn(bay) != null) {
            remove(bay);
        } else {
            fit(bay);
        }
    }

    /** Takes a matching part out of the player's inventory and fits it. */
    private void fit(ComponentSlot bay) {
        int index = -1;
        ItemStack[] contents = viewer.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ComponentType type = ComponentType.of(contents[i]);
            if (type != null && type.slot() == bay) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            viewer.sendMessage(ChatColor.RED + "You have no " + bay.label().toLowerCase()
                    + " to fit. Buy one with /vmcomputers order.");
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            return;
        }

        ItemStack stack = contents[index];
        ComponentType type = ComponentType.of(stack);
        if (stack.getAmount() > 1) {
            stack.setAmount(stack.getAmount() - 1);
        } else {
            viewer.getInventory().setItem(index, null);
        }

        computer.install(bay, type);
        persist(bay, type);
        if (bay.isDeskPeripheral()) {
            ComputerBuilder.redrawPeripherals(viewer.getWorld(), computer);
        }
        viewer.sendMessage(ChatColor.GREEN + "Fitted " + type.displayName() + ".");
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_PISTON_CONTRACT, 0.6f, 1.4f);
        refresh();
    }

    /** Pulls a part back out and returns it to the player. */
    private void remove(ComponentSlot bay) {
        ComponentType type = computer.installedIn(bay);
        if (type == null) {
            return;
        }
        if (bay == ComponentSlot.MONITOR) {
            // The screen was built at this monitor's size and the desk was sized to match it.
            // Taking it out would leave a wall of maps belonging to a machine that no longer
            // claims to have a screen, so the only way to change it is to remove the computer.
            viewer.sendMessage(ChatColor.RED
                    + "The screen is built around this monitor. Remove the computer to change it.");
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            return;
        }
        if (VmService.isRunning(computer.id())) {
            viewer.sendMessage(ChatColor.RED + "Shut it down before taking it apart.");
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            return;
        }
        if (viewer.getInventory().firstEmpty() == -1) {
            viewer.sendMessage(ChatColor.RED + "Your inventory is full.");
            return;
        }

        computer.install(bay, null);
        persist(bay, null);
        viewer.getInventory().addItem(type.toItemStack(1));
        if (bay.isDeskPeripheral()) {
            ComputerBuilder.redrawPeripherals(viewer.getWorld(), computer);
        }
        viewer.sendMessage(ChatColor.YELLOW + "Removed " + type.displayName() + ".");
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_PISTON_EXTEND, 0.6f, 1.4f);
        refresh();
    }

    private void persist(ComponentSlot bay, ComponentType type) {
        try {
            VMComputers.getPlugin().getComputerDao()
                    .saveComponent(computer.id(), bay.name(), type == null ? null : type.id());
        } catch (SQLException e) {
            VMComputers.getPlugin().getLogger()
                    .severe("Could not save component: " + e.getMessage());
            viewer.sendMessage(ChatColor.RED
                    + "That did not save; it will be back where it was after a restart.");
        }
    }

    private void togglePower() {
        // Already on its way somewhere. Without this, clicking twice during the seconds a boot
        // takes queues a second start against a machine that is halfway up.
        if (VmService.isBusy(computer.id())) {
            viewer.playSound(viewer.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.5f, 0.8f);
            return;
        }

        MonitorScreen screen = VMComputers.getPlugin().getScreen(computer.id());
        if (screen == null) {
            viewer.sendMessage(ChatColor.RED + "This computer has no screen attached; rebuild it.");
            return;
        }

        if (VmService.isRunning(computer.id())) {
            viewer.sendMessage(ChatColor.GRAY + "Powering off...");
            VmService.stop(computer, screen, m -> viewer.sendMessage(ChatColor.YELLOW + m));
        } else {
            if (!computer.isAssembled()) {
                viewer.sendMessage(ChatColor.RED + "It is missing parts; see the red bays.");
                viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
                return;
            }
            viewer.sendMessage(ChatColor.GRAY + "Powering on...");
            VmService.start(computer, screen, m -> viewer.sendMessage(ChatColor.YELLOW + m));
        }
        // Redrawn immediately so the button reflects what is true now rather than what was just
        // asked for -- but this alone shows the *old* state, because booting and shutting down
        // both happen on a background thread and have not finished yet. The watcher below is what
        // actually flips the lever, a fraction of a second later when the machine really is up.
        refresh();
    }

    /**
     * Power can change without this menu being touched: a boot started here finishes on another
     * thread, someone else can right-click the tower, and a guest can bring itself down. So the
     * lever is driven by what the machine is actually doing rather than by what was clicked.
     */
    @Override
    protected int refreshTicks() {
        return WATCH_TICKS;
    }

    @Override
    protected void tick() {
        if (!powerToken().equals(shownState)) {
            refresh();
        }
    }
}
