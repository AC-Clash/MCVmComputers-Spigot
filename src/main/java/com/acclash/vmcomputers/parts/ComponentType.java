package com.acclash.vmcomputers.parts;

import com.acclash.vmcomputers.VMComputers;
import com.acclash.vmcomputers.display.MonitorSize;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The catalogue of orderable components: what they are called, what they cost, what they look like
 * in an inventory, and which {@link PartModel} draws them in the world.
 *
 * <p>Prices and tiers come from the mod's {@code ItemList}, in iron ingots, so a player who knows
 * the mod pays what they expect.
 *
 * <h2>Why the icons are ordinary vanilla items</h2>
 *
 * <p>An inventory slot renders an {@link ItemStack} and nothing else. Display entities -- which is
 * how these parts are drawn in the world -- cannot appear in a chest GUI at all, and the only
 * vanilla item whose texture the server can choose is a player head, whose skin has to be hosted
 * on Mojang's texture servers. That is an outside dependency for artwork that would still read as
 * a head.
 *
 * <p>So the icons are vanilla items picked to be recognisable and, more importantly, to be
 * distinct from each other at a glance: a player scanning the shop should be able to tell a GPU
 * from a motherboard without reading. Naming carries the precision, the icon carries the
 * silhouette.
 *
 * <p>Nothing here is throwable, edible or placeable-by-accident. A part is an inventory item a
 * player carries around, and a snowball -- much the closest match for a mouse -- can be thrown
 * away and lost for good.
 */
public final class ComponentType {

    /** Shop sections, matching how the mod's ordering tablet groups its catalogue. */
    public enum Category {
        PARTS("Components"),
        PERIPHERALS("Peripherals");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private static final Map<String, ComponentType> REGISTRY =
            new LinkedHashMap<String, ComponentType>();

    // ---- the catalogue ---------------------------------------------------

    public static final ComponentType PC_CASE = register(new ComponentType(
            "pc_case", "PC Case", Material.BLACK_SHULKER_BOX, 6, Category.PARTS, null,
            "pc_case_sidepanel", "Houses the machine. Right-click to open it."));

    public static final ComponentType CASE_SIDE_PANEL = register(new ComponentType(
            "case_side_panel", "Case Side Panel", Material.GRAY_STAINED_GLASS_PANE, 2,
            Category.PARTS, null, "pc_case_only_glass_sidepanel", "Tinted window for the case."));

    public static final ComponentType MOTHERBOARD = register(new ComponentType(
            "motherboard", "Motherboard", Material.REPEATER, 4, Category.PARTS, ComponentSlot.MOTHERBOARD,
            "motherboard", "32-bit board. Takes one CPU and one stick of RAM."));

    public static final ComponentType MOTHERBOARD_64 = register(new ComponentType(
            "motherboard64", "64-bit Motherboard", Material.COMPARATOR, 8, Category.PARTS,
            ComponentSlot.MOTHERBOARD, "motherboard64", "Required for a 64-bit guest."));

    public static final ComponentType CPU_2 = register(new ComponentType(
            "cpu2", "CPU (host cores / 2)", Material.NETHERITE_SCRAP, 10, Category.PARTS, ComponentSlot.CPU,
            "cpu_divided_by_2", "Half the host's cores.", 2));

    public static final ComponentType CPU_4 = register(new ComponentType(
            "cpu4", "CPU (host cores / 4)", Material.NETHERITE_SCRAP, 8, Category.PARTS, ComponentSlot.CPU,
            "cpu_divided_by_4", "A quarter of the host's cores.", 4));

    public static final ComponentType CPU_6 = register(new ComponentType(
            "cpu6", "CPU (host cores / 6)", Material.NETHERITE_SCRAP, 6, Category.PARTS, ComponentSlot.CPU,
            "cpu_divided_by_6", "A sixth of the host's cores.", 6));

    public static final ComponentType GPU = register(new ComponentType(
            "gpu", "Graphics Card", Material.DAYLIGHT_DETECTOR, 12, Category.PARTS, ComponentSlot.GPU,
            "gpu", "Drives the monitor."));

    public static final ComponentType RAM_64M = register(ram("ram64m", "64 MB", 2, 64));
    public static final ComponentType RAM_128M = register(ram("ram128m", "128 MB", 2, 128));
    public static final ComponentType RAM_256M = register(ram("ram256m", "256 MB", 3, 256));
    public static final ComponentType RAM_512M = register(ram("ram512m", "512 MB", 4, 512));
    public static final ComponentType RAM_1G = register(ram("ram1g", "1 GB", 6, 1024));
    public static final ComponentType RAM_2G = register(ram("ram2g", "2 GB", 8, 2048));
    public static final ComponentType RAM_4G = register(ram("ram4g", "4 GB", 14, 4096));

    public static final ComponentType HARD_DRIVE = register(new ComponentType(
            "harddrive", "Hard Drive", Material.IRON_BLOCK, 6, Category.PARTS, ComponentSlot.HARD_DRIVE,
            "harddrive", "Where the guest operating system lives."));

    public static final ComponentType KEYBOARD = register(new ComponentType(
            "keyboard", "Keyboard", Material.POLISHED_BLACKSTONE_PRESSURE_PLATE, 4,
            Category.PERIPHERALS, ComponentSlot.KEYBOARD, "keyboard", "Sits on the desk."));

    public static final ComponentType MOUSE = register(new ComponentType(
            "mouse", "Mouse", Material.QUARTZ, 4, Category.PERIPHERALS, ComponentSlot.MOUSE,
            "mouse", "Sits on the desk."));

    // Monitors are the one component whose tier changes the shape of the build rather than a
    // number inside it: the size fitted is the size the screen is assembled at. Prices climb with
    // panel count, since a bigger screen is genuinely more of the plugin's frame budget.
    public static final ComponentType MONITOR_SMALL = register(monitor(
            "monitor_small", MonitorSize.SMALL, 10));
    public static final ComponentType MONITOR_MEDIUM = register(monitor(
            "monitor_medium", MonitorSize.MEDIUM, 16));
    public static final ComponentType MONITOR_LARGE = register(monitor(
            "monitor_large", MonitorSize.LARGE, 24));
    public static final ComponentType MONITOR_XLARGE = register(monitor(
            "monitor_xlarge", MonitorSize.XLARGE, 40));

    private static ComponentType monitor(String id, MonitorSize size, int price) {
        return new ComponentType(id, size.name().charAt(0) + size.name().substring(1).toLowerCase(
                Locale.ROOT) + " Monitor", Material.ITEM_FRAME, price, Category.PERIPHERALS,
                ComponentSlot.MONITOR,
                size.form() == MonitorSize.Form.PROJECTOR ? "walltv" : "flatscreen",
                size.describe() + ".", size.ordinal());
    }

    /** The monitor size this component builds, or null if it is not a monitor. */
    public MonitorSize monitorSize() {
        return slot == ComponentSlot.MONITOR ? MonitorSize.values()[rating] : null;
    }

    /**
     * RAM is one shape in seven capacities, so it is one icon in seven names. A thin gold bar is
     * about as close as vanilla gets to a stick of memory, and the tier is in the name anyway.
     */
    private static ComponentType ram(String id, String capacity, int price, int megabytes) {
        return new ComponentType(id, capacity + " RAM", Material.LIGHT_WEIGHTED_PRESSURE_PLATE,
                price, Category.PARTS, ComponentSlot.RAM, id,
                "Guest memory. A modern desktop needs 4 GB.", megabytes);
    }

    // ---- instance --------------------------------------------------------

    private final String id;
    private final String displayName;
    private final Material icon;
    private final int price;
    private final Category category;
    private final ComponentSlot slot;
    private final int rating;
    private final String modelName;
    private final String description;

    private ComponentType(String id, String displayName, Material icon, int price,
                          Category category, ComponentSlot slot, String modelName,
                          String description) {
        this(id, displayName, icon, price, category, slot, modelName, description, 0);
    }

    private ComponentType(String id, String displayName, Material icon, int price,
                          Category category, ComponentSlot slot, String modelName,
                          String description, int rating) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.price = price;
        this.category = category;
        this.slot = slot;
        this.modelName = modelName;
        this.description = description;
        this.rating = rating;
    }

    private static ComponentType register(ComponentType type) {
        REGISTRY.put(type.id, type);
        return type;
    }

    /** Stable identifier, used in persistent data and configuration. */
    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public Material icon() {
        return icon;
    }

    /** Price in iron ingots, as in the mod. */
    public int price() {
        return price;
    }

    public Category category() {
        return category;
    }

    /**
     * The number that makes this tier different from its siblings: megabytes for RAM, and for a
     * CPU the divisor applied to the host's core count (the mod's "divided by N" naming). Zero for
     * anything with only one tier.
     */
    public int rating() {
        return rating;
    }

    /** Which bay this installs into, or null for a peripheral that is never installed. */
    public ComponentSlot slot() {
        return slot;
    }

    /** Every component that fits a given bay, cheapest tier first as declared. */
    public static List<ComponentType> forSlot(ComponentSlot slot) {
        List<ComponentType> out = new ArrayList<ComponentType>();
        for (ComponentType type : REGISTRY.values()) {
            if (type.slot == slot) {
                out.add(type);
            }
        }
        return out;
    }

    /** Name of the {@link PartModel} that draws this in the world, or null if it is never placed. */
    public String modelName() {
        return modelName;
    }

    public String description() {
        return description;
    }

    /** The model, or null when it is not placed in the world or failed to load. */
    public PartModel model() {
        return modelName == null ? null : PartModels.get(modelName);
    }

    /**
     * Builds the inventory representation: the icon, named and described, tagged so the plugin can
     * recognise it again when a player drops it into a case.
     */
    public ItemStack toItemStack(int amount) {
        ItemStack stack = new ItemStack(icon, amount);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RESET + "" + ChatColor.AQUA + displayName);
            List<String> lore = new ArrayList<String>();
            lore.add(ChatColor.GRAY + description);
            lore.add("");
            lore.add(ChatColor.GOLD + "Price: " + price + " iron");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(key(), PersistentDataType.STRING, id);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** Reads the component back off an item stack, or null if it is not one of ours. */
    public static ComponentType of(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        String id = meta.getPersistentDataContainer().get(key(), PersistentDataType.STRING);
        return id == null ? null : REGISTRY.get(id);
    }

    private static NamespacedKey key() {
        return new NamespacedKey(VMComputers.getPlugin(), "componentType");
    }

    public static ComponentType byId(String id) {
        return REGISTRY.get(id);
    }

    /**
     * What a computer built by {@code /vmcomputers create} comes fitted with.
     *
     * <p>That command builds an entire machine in one go, so it produces a working one rather than
     * an empty case; the ordering and assembly route is the piecemeal path. It is also what legacy
     * computers are backfilled with, since they predate components entirely and used to boot with
     * 4 GB hardcoded -- which is what the 4 GB stick here preserves.
     */
    public static Map<ComponentSlot, ComponentType> defaultLoadout(MonitorSize size) {
        Map<ComponentSlot, ComponentType> loadout =
                new LinkedHashMap<ComponentSlot, ComponentType>();
        loadout.put(ComponentSlot.MOTHERBOARD, MOTHERBOARD_64);
        loadout.put(ComponentSlot.CPU, CPU_4);
        loadout.put(ComponentSlot.RAM, RAM_4G);
        loadout.put(ComponentSlot.GPU, GPU);
        loadout.put(ComponentSlot.HARD_DRIVE, HARD_DRIVE);
        loadout.put(ComponentSlot.KEYBOARD, KEYBOARD);
        loadout.put(ComponentSlot.MOUSE, MOUSE);
        loadout.put(ComponentSlot.MONITOR, forMonitorSize(size));
        return loadout;
    }

    /** The monitor component that builds a given size. */
    public static ComponentType forMonitorSize(MonitorSize size) {
        for (ComponentType type : REGISTRY.values()) {
            if (type.slot == ComponentSlot.MONITOR && type.monitorSize() == size) {
                return type;
            }
        }
        return MONITOR_LARGE;
    }

    public static List<ComponentType> all() {
        return Collections.unmodifiableList(new ArrayList<ComponentType>(REGISTRY.values()));
    }

    public static List<ComponentType> inCategory(Category category) {
        List<ComponentType> out = new ArrayList<ComponentType>();
        for (ComponentType type : REGISTRY.values()) {
            if (type.category == category) {
                out.add(type);
            }
        }
        return out;
    }

    @Override
    public String toString() {
        return "ComponentType(" + id + ")";
    }
}
