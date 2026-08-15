package com.acclash.vmcomputers.gui;

import com.acclash.vmcomputers.parts.ComponentType;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The parts shop, standing in for the mod's ordering tablet.
 *
 * <p>The mod wraps buying in a good deal of theatre: the tablet waits to find a satellite, a
 * payment chest falls out of the sky, you load it with iron, and a second chest arrives with the
 * order. None of that is here yet. This is the transaction underneath it -- pick a part, pay iron
 * from your inventory, receive the part -- so that the theatre can be layered on later without the
 * catalogue or the pricing moving.
 *
 * <p>Prices are the mod's own, in iron ingots, from {@link ComponentType}.
 */
public class OrderMenu extends Menu {

    private static final int SIZE = 54;
    private static final int TAB_ROW = 5;

    private ComponentType.Category category;

    /** Menu slot -> what buying it orders. Rebuilt on every draw. */
    private final Map<Integer, ComponentType> offers = new HashMap<Integer, ComponentType>();

    public OrderMenu(Player viewer) {
        this(viewer, ComponentType.Category.PARTS);
    }

    public OrderMenu(Player viewer, ComponentType.Category category) {
        super(viewer);
        this.category = category;
    }

    @Override
    public String title() {
        return ChatColor.DARK_GRAY + "Parts Catalogue";
    }

    @Override
    public int size() {
        return SIZE;
    }

    @Override
    public void draw() {
        offers.clear();

        List<ComponentType> items = ComponentType.inCategory(category);
        for (int i = 0; i < items.size() && i < TAB_ROW * ROW; i++) {
            ComponentType type = items.get(i);
            ItemStack icon = type.toItemStack(1);
            boolean affordable = ironHeld() >= type.price();
            withLore(icon, "", affordable
                    ? ChatColor.GREEN + "Click to buy"
                    : ChatColor.RED + "You need " + (type.price() - ironHeld()) + " more iron");
            set(i, icon);
            offers.put(Integer.valueOf(i), type);
        }

        fillRow(TAB_ROW);
        for (ComponentType.Category tab : ComponentType.Category.values()) {
            int slot = TAB_ROW * ROW + tab.ordinal();
            boolean active = tab == category;
            set(slot, button(active ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
                    (active ? ChatColor.GREEN : ChatColor.GRAY) + tab.label(),
                    active ? "Showing this section" : "Click to view"));
        }

        set(TAB_ROW * ROW + 8, button(Material.IRON_INGOT,
                ChatColor.GOLD + "Your iron: " + ironHeld(),
                "Parts are paid for from your",
                "inventory when you buy them."));
    }

    @Override
    public void onClick(int slot, ClickType click) {
        // Tabs first: their slots are in the same range the offers map is keyed on.
        if (slot >= TAB_ROW * ROW && slot < SIZE) {
            int index = slot - TAB_ROW * ROW;
            ComponentType.Category[] tabs = ComponentType.Category.values();
            if (index < tabs.length) {
                category = tabs[index];
                refresh();
            }
            return;
        }

        ComponentType type = offers.get(Integer.valueOf(slot));
        if (type == null) {
            return;
        }
        buy(type);
    }

    private void buy(ComponentType type) {
        int held = ironHeld();
        if (held < type.price()) {
            viewer.sendMessage(ChatColor.RED + "You need " + type.price() + " iron for a "
                    + type.displayName() + "; you have " + held + ".");
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            return;
        }

        // The part is created before the iron is taken, and the iron is only taken if there is
        // somewhere to put the part. Taking payment first would charge a player whose inventory is
        // full and give them nothing.
        ItemStack part = type.toItemStack(1);
        if (viewer.getInventory().firstEmpty() == -1) {
            viewer.sendMessage(ChatColor.RED + "Your inventory is full.");
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            return;
        }

        takeIron(type.price());
        viewer.getInventory().addItem(part);
        viewer.sendMessage(ChatColor.GREEN + "Bought " + ChatColor.WHITE + type.displayName()
                + ChatColor.GREEN + " for " + type.price() + " iron.");
        viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.7f, 1.2f);
        refresh();
    }

    private int ironHeld() {
        int total = 0;
        for (ItemStack stack : viewer.getInventory().getContents()) {
            if (stack != null && stack.getType() == Material.IRON_INGOT) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /** Removes exactly {@code amount} iron ingots. Only called once the player is known to have it. */
    private void takeIron(int amount) {
        int remaining = amount;
        ItemStack[] contents = viewer.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != Material.IRON_INGOT) {
                continue;
            }
            int take = Math.min(remaining, stack.getAmount());
            remaining -= take;
            if (take >= stack.getAmount()) {
                viewer.getInventory().setItem(i, null);
            } else {
                stack.setAmount(stack.getAmount() - take);
            }
        }
    }
}
