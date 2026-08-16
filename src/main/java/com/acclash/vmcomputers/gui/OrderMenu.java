package com.acclash.vmcomputers.gui;

import com.acclash.vmcomputers.parts.ComponentType;
import com.acclash.vmcomputers.parts.Currency;
import com.acclash.vmcomputers.parts.Delivery;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The parts shop: what you get when you call Steve on the brick phone.
 *
 * <p>Stands in for the mod's ordering tablet. The mod acquires a satellite, drops a payment chest
 * for you to load with payment, then delivers a second chest with the order. Here the money comes
 * straight out of your inventory when you order and the parts arrive in a package a few seconds
 * later -- one delivery rather than two, and no waiting on a satellite.
 *
 * <p>Prices are the mod's own numbers, charged in {@link Currency}.
 */
public class OrderMenu extends Menu {

    private static final int SIZE = 54;
    private static final int TAB_ROW = 5;

    private static final int CART_SLOT = TAB_ROW * ROW + 4;
    private static final int CLEAR_SLOT = TAB_ROW * ROW + 5;

    /**
     * What the player has picked out but not yet ordered.
     *
     * <p>The mod's tablet has one too, and it is the reason an order is a single delivery: without
     * it, buying five parts meant five boxes, and five boxes land on the same spot in front of the
     * player, each one hiding the last.
     */
    private final List<ComponentType> cart = new ArrayList<ComponentType>();

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
            int inCart = count(type);
            if (inCart > 0) {
                icon.setAmount(Math.min(64, inCart));
                withLore(icon, "", ChatColor.AQUA + "In cart: " + inCart,
                        ChatColor.GREEN + "Left-click to add another",
                        ChatColor.YELLOW + "Right-click to take one out");
            } else {
                withLore(icon, "", ChatColor.GREEN + "Left-click to add to cart");
            }
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

        set(TAB_ROW * ROW + 8, button(Currency.create(1),
                ChatColor.GOLD + "Your balance: " + Currency.format(held()),
                "Paid when you place the order.",
                "Craft more from paper."));

        drawCart();
    }

    private void drawCart() {
        int total = cartTotal();
        if (cart.isEmpty()) {
            set(CART_SLOT, button(Material.CHEST, ChatColor.GRAY + "Cart is empty",
                    "Pick out some parts first."));
            return;
        }

        List<String> lines = new ArrayList<String>();
        for (ComponentType type : ComponentType.all()) {
            int n = count(type);
            if (n > 0) {
                lines.add(n + "x " + type.displayName());
            }
        }
        lines.add("");
        lines.add(total <= held()
                ? ChatColor.GOLD + "Total: " + Currency.format(total)
                : ChatColor.RED + "Total: " + Currency.format(total)
                        + " (you have " + held() + ")");
        lines.add("");
        lines.add(total <= held()
                ? ChatColor.GREEN + "Click to place the order"
                : ChatColor.RED + "Not enough Auros");

        set(CART_SLOT, button(Material.CHEST,
                ChatColor.AQUA + "Cart (" + cart.size() + " item"
                        + (cart.size() == 1 ? ")" : "s)"),
                lines.toArray(new String[0])));
        set(CLEAR_SLOT, button(Material.BARRIER, ChatColor.YELLOW + "Empty the cart",
                "Puts everything back."));
    }

    @Override
    public void onClick(int slot, ClickType click) {
        if (slot == CART_SLOT) {
            placeOrder();
            return;
        }
        if (slot == CLEAR_SLOT && !cart.isEmpty()) {
            cart.clear();
            refresh();
            return;
        }

        // Tabs next: their slots are in the same range the offers map is keyed on.
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
        if (click.isRightClick()) {
            cart.remove(type);
        } else {
            cart.add(type);
        }
        refresh();
    }

    private int count(ComponentType type) {
        int n = 0;
        for (ComponentType entry : cart) {
            if (entry == type) {
                n++;
            }
        }
        return n;
    }

    private int cartTotal() {
        int total = 0;
        for (ComponentType type : cart) {
            total += type.price();
        }
        return total;
    }

    private void placeOrder() {
        if (cart.isEmpty()) {
            return;
        }
        int total = cartTotal();
        if (held() < total) {
            viewer.sendMessage(ChatColor.RED + "That comes to " + Currency.format(total)
                    + "; you have " + held() + ".");
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            return;
        }

        Currency.take(viewer, total);
        final List<ComponentType> ordered = new ArrayList<ComponentType>(cart);
        cart.clear();

        viewer.sendMessage(ChatColor.GOLD + "Steve: " + ChatColor.WHITE + ordered.size()
                + " ITEMS! IT'S ON THE TRUCK!");
        viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.7f, 1.2f);
        refresh();

        // Delivered rather than handed over, so an order is a thing that arrives. Timing, routing
        // and the messages that go with them belong to Delivery, which knows whether a truck can
        // get here; the shop's job ends at taking the money.
        Delivery.deliver(viewer, ordered);
    }

    private int held() {
        return Currency.heldBy(viewer);
    }

}
