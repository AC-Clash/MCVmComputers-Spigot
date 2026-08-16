package com.acclash.vmcomputers.gui;

import com.acclash.vmcomputers.VMComputers;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Base class for the plugin's chest-style menus.
 *
 * <p>Menus are identified by being their own {@link InventoryHolder}. {@link MenuListener} routes a
 * click by asking the inventory who owns it, so a click can never be misattributed. The obvious
 * alternative -- comparing the window title -- breaks the moment a player renames a chest to match,
 * and would let them run menu actions from their own container.
 *
 * <p>Every click in a menu is cancelled before {@link #onClick} runs, so a subclass cannot leak
 * items by forgetting to. Anything a menu hands to a player is given deliberately.
 */
public abstract class Menu implements InventoryHolder {

    /** Slots per row in a chest interface. */
    public static final int ROW = 9;

    protected final Player viewer;
    private Inventory inventory;
    private BukkitTask ticker;

    protected Menu(Player viewer) {
        this.viewer = viewer;
    }

    /** Window title. Read once when the menu is opened. */
    public abstract String title();

    /** Total slots; must be a multiple of {@link #ROW} and at most 54. */
    public abstract int size();

    /** Fills the inventory. Called on open and by {@link #refresh}. */
    public abstract void draw();

    /**
     * Handles a click on a menu slot.
     *
     * <p>The click is already cancelled. {@code slot} is within this menu; clicks in the player's
     * own inventory are dispatched with the raw slot, so check {@code slot < size()} if that
     * matters.
     */
    public abstract void onClick(int slot, ClickType click);

    @Override
    public Inventory getInventory() {
        if (inventory == null) {
            inventory = Bukkit.createInventory(this, size(), title());
        }
        return inventory;
    }

    public void open() {
        draw();
        viewer.openInventory(getInventory());
        if (refreshTicks() > 0) {
            ticker = Bukkit.getScheduler().runTaskTimer(
                    VMComputers.getPlugin(), this::tick, refreshTicks(), refreshTicks());
        }
    }

    /** Redraws in place, keeping the window open. */
    public void refresh() {
        if (inventory != null) {
            inventory.clear();
            draw();
        }
    }

    /**
     * How often to call {@link #tick} while this menu is open, or 0 to never.
     *
     * <p>For menus showing something they do not themselves control. A chest GUI is a snapshot
     * taken when it was drawn, so anything that changes behind it -- a machine finishing its boot
     * on a background thread, someone else hitting the power button on the tower, a guest falling
     * over -- leaves the window quietly lying until the player closes and reopens it.
     */
    protected int refreshTicks() {
        return 0;
    }

    /** Called every {@link #refreshTicks} ticks while open. Redraw here only if something changed. */
    protected void tick() {
    }

    /** Called when the window closes. Stops the ticker; override to add cleanup, and call super. */
    public void closed() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    // ---- helpers for subclasses -----------------------------------------

    protected void set(int slot, ItemStack stack) {
        if (slot >= 0 && slot < size()) {
            getInventory().setItem(slot, stack);
        }
    }

    /** An item with a coloured name and grey lore lines. */
    protected static ItemStack button(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RESET + name);
            if (lore.length > 0) {
                List<String> lines = new ArrayList<String>();
                for (String line : lore) {
                    lines.add(line.isEmpty() ? "" : ChatColor.GRAY + line);
                }
                meta.setLore(lines);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** As {@link #button(Material, String, String...)}, but for a stack that is already built. */
    protected static ItemStack button(ItemStack stack, String name, String... lore) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RESET + name);
            if (lore.length > 0) {
                List<String> lines = new ArrayList<String>();
                for (String line : lore) {
                    lines.add(line.isEmpty() ? "" : ChatColor.GRAY + line);
                }
                meta.setLore(lines);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** Adds lore to an existing stack without disturbing what is already there. */
    protected static ItemStack withLore(ItemStack stack, String... extra) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        List<String> lines = meta.getLore() == null
                ? new ArrayList<String>() : new ArrayList<String>(meta.getLore());
        lines.addAll(Arrays.asList(extra));
        meta.setLore(lines);
        stack.setItemMeta(meta);
        return stack;
    }

    /** Blank filler, so empty slots do not look clickable. */
    protected static ItemStack filler() {
        return button(Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    protected void fillRow(int row) {
        for (int i = 0; i < ROW; i++) {
            set(row * ROW + i, filler());
        }
    }
}
