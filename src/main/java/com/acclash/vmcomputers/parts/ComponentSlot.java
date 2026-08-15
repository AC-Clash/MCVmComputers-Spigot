package com.acclash.vmcomputers.parts;

import org.bukkit.Material;

/**
 * The bays on a computer, one component each.
 *
 * <p>Deliberately a small fixed set rather than a free-form list. A computer in the mod takes one
 * board, one chip, one stick and one card, and modelling it that way means an assembled machine is
 * a handful of nullable references instead of an inventory that has to be validated on every read.
 *
 * <p>Peripherals are bays too, even though they hang off the outside rather than sitting in the
 * case. It is the same question -- what is fitted, and what does it do -- and giving them their own
 * mechanism would mean two ways to ask it.
 */
public enum ComponentSlot {

    MOTHERBOARD("Motherboard", Material.REPEATER, true,
            "Everything else plugs into it."),
    CPU("Processor", Material.NETHERITE_SCRAP, true,
            "How many host cores the guest gets."),
    RAM("Memory", Material.LIGHT_WEIGHTED_PRESSURE_PLATE, true,
            "How much memory the guest gets."),
    GPU("Graphics", Material.DAYLIGHT_DETECTOR, true,
            "Drives the monitor."),
    /**
     * Required, and the one that decides the shape of the whole machine: its tier sets the monitor
     * size, and fitting it is what turns a bare case into a built computer with a desk and a
     * screen. Nothing can be assembled without knowing how big the screen is.
     */
    MONITOR("Monitor", Material.ITEM_FRAME, true,
            "Sets the screen size. Needed to assemble."),
    HARD_DRIVE("Storage", Material.IRON_BLOCK, false,
            "Optional. Without one, nothing survives a power cycle."),
    KEYBOARD("Keyboard", Material.POLISHED_BLACKSTONE_PRESSURE_PLATE, false,
            "Optional. Sits on the desk."),
    MOUSE("Mouse", Material.QUARTZ, false,
            "Optional. Sits on the desk.");

    private final String label;
    private final Material emptyIcon;
    private final boolean required;
    private final String description;

    ComponentSlot(String label, Material emptyIcon, boolean required, String description) {
        this.label = label;
        this.emptyIcon = emptyIcon;
        this.required = required;
        this.description = description;
    }

    public String label() {
        return label;
    }

    /** Shown greyed out when the bay is empty. */
    public Material emptyIcon() {
        return emptyIcon;
    }

    /** Whether the machine refuses to run without it. */
    public boolean required() {
        return required;
    }

    public String description() {
        return description;
    }

    /** Bays that sit on the desk and are drawn there when fitted. */
    public boolean isDeskPeripheral() {
        return this == KEYBOARD || this == MOUSE;
    }
}
