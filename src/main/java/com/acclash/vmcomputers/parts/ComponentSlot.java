package com.acclash.vmcomputers.parts;

import org.bukkit.Material;

/**
 * The bays inside a case, one component each.
 *
 * <p>Deliberately a small fixed set rather than a free-form list. A computer in the mod takes one
 * board, one chip, one stick and one card, and modelling it that way means an installed machine is
 * five nullable references instead of an inventory that has to be validated on every read.
 *
 * <p>Peripherals are not here. A keyboard, a mouse and a monitor sit on the desk rather than in the
 * case, and their {@link ComponentType}s have no slot.
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
    HARD_DRIVE("Storage", Material.IRON_BLOCK, false,
            "Optional. Without one, nothing survives a power cycle.");

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

    /** Whether the machine refuses to power on without it. */
    public boolean required() {
        return required;
    }

    public String description() {
        return description;
    }
}
