package com.acclash.vmcomputers.gui;

import com.acclash.vmcomputers.VMComputers;
import com.acclash.vmcomputers.computer.Computer;
import com.acclash.vmcomputers.computer.ComputerBuilder;
import com.acclash.vmcomputers.display.MonitorScreen;
import com.acclash.vmcomputers.display.MonitorSize;
import com.acclash.vmcomputers.emu.QemuBinary;
import com.acclash.vmcomputers.emu.VmSpec;
import com.acclash.vmcomputers.computer.PendingCase;
import com.acclash.vmcomputers.parts.ComponentSlot;
import com.acclash.vmcomputers.parts.ComponentType;
import com.acclash.vmcomputers.parts.PartRenderer;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Building a computer up from a case the player placed.
 *
 * <p>The same bays as {@link CaseMenu}, but there is no power button and no drive yet -- there is
 * no machine to power on. In its place is an Assemble button, which is the moment the case stops
 * being furniture and becomes a computer with a desk, a screen and an entry in the registry.
 */
public class AssemblyMenu extends Menu {

    private static final int SIZE = 27;
    private static final int ASSEMBLE_SLOT = 22;
    private static final int STATUS_SLOT = 18;
    private static final int SLOT_ROW = 1;

    private final PendingCase pending;
    private final Map<Integer, ComponentSlot> bays = new HashMap<Integer, ComponentSlot>();

    public AssemblyMenu(Player viewer, PendingCase pending) {
        super(viewer);
        this.pending = pending;
    }

    @Override
    public String title() {
        return ChatColor.DARK_GRAY + "PC Case";
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
        for (int i = 0; i < slots.length && start + i < SLOT_ROW * ROW + ROW; i++) {
            ComponentSlot slot = slots[i];
            ComponentType fitted = pending.installedIn(slot);
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
        drawAssemble();
    }

    private void drawStatus() {
        List<ComponentSlot> missing = pending.missingComponents();
        if (missing.isEmpty()) {
            set(STATUS_SLOT, button(Material.LIME_DYE, ChatColor.GREEN + "Ready to assemble",
                    "Everything it needs is fitted."));
            return;
        }
        StringBuilder names = new StringBuilder();
        for (ComponentSlot slot : missing) {
            names.append(names.length() == 0 ? "" : ", ").append(slot.label());
        }
        set(STATUS_SLOT, button(Material.RED_DYE, ChatColor.RED + "Not finished",
                "Still needs: " + names));
    }

    private void drawAssemble() {
        MonitorSize size = pending.plannedSize();
        if (!pending.canAssemble()) {
            set(ASSEMBLE_SLOT, button(Material.BARRIER, ChatColor.GRAY + "Cannot assemble yet",
                    "Fit the missing parts first."));
            return;
        }
        set(ASSEMBLE_SLOT, button(Material.CRAFTING_TABLE, ChatColor.GREEN + "Assemble",
                "Builds the desk and the screen",
                "around this case.",
                "",
                "Screen: " + size.describe(),
                "",
                ChatColor.YELLOW + "Make sure there is room."));
    }

    @Override
    public void onClick(int slot, ClickType click) {
        if (slot == ASSEMBLE_SLOT) {
            assemble();
            return;
        }
        ComponentSlot bay = bays.get(Integer.valueOf(slot));
        if (bay == null) {
            return;
        }
        if (pending.installedIn(bay) != null) {
            remove(bay);
        } else {
            fit(bay);
        }
    }

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
                    + " to fit. Call Steve on the brick phone.");
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

        pending.install(bay, type);
        persist(bay, type);
        viewer.sendMessage(ChatColor.GREEN + "Fitted " + type.displayName() + ".");
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_PISTON_CONTRACT, 0.6f, 1.4f);
        refresh();
    }

    private void remove(ComponentSlot bay) {
        ComponentType type = pending.installedIn(bay);
        if (type == null) {
            return;
        }
        if (viewer.getInventory().firstEmpty() == -1) {
            viewer.sendMessage(ChatColor.RED + "Your inventory is full.");
            return;
        }
        pending.install(bay, null);
        persist(bay, null);
        viewer.getInventory().addItem(type.toItemStack(1));
        viewer.sendMessage(ChatColor.YELLOW + "Removed " + type.displayName() + ".");
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_PISTON_EXTEND, 0.6f, 1.4f);
        refresh();
    }

    private void persist(ComponentSlot bay, ComponentType type) {
        try {
            VMComputers.getPlugin().getComputerDao()
                    .saveCaseComponent(pending.id(), bay.name(), type == null ? null : type.id());
        } catch (SQLException e) {
            VMComputers.getPlugin().getLogger().severe("Could not save case part: " + e.getMessage());
            viewer.sendMessage(ChatColor.RED + "That did not save; see the console.");
        }
    }

    /** Turns the case into a real computer. */
    private void assemble() {
        if (!pending.canAssemble()) {
            return;
        }
        MonitorSize size = pending.plannedSize();
        World world = viewer.getWorld();

        Computer candidate = pending.toComputer(-1, size, "Generic PC");

        // Checked before anything is built or written, so a refused assembly leaves the case
        // exactly as it was rather than half a computer and a lost case.
        Computer clash = VMComputers.getPlugin().getRegistry().findOverlap(candidate);
        if (clash != null) {
            viewer.sendMessage(ChatColor.RED + "There is not room here -- it would run into "
                    + "computer #" + clash.id() + ".");
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
            return;
        }

        Computer saved;
        try {
            saved = VMComputers.getPlugin().getComputerDao().insert(candidate);
        } catch (SQLException e) {
            viewer.sendMessage(ChatColor.RED + "Could not save the computer; see the console.");
            VMComputers.getPlugin().getLogger().severe("Assemble insert failed: " + e.getMessage());
            return;
        }

        // Architecture defaults to X86_64 on a bare Computer, which is wrong everywhere it is not
        // also the host's. On an ARM host that machine gets qemu-system-x86_64 with no
        // acceleration, and an arm64 install image will not boot on it at all -- so an assembled
        // computer silently refused every ISO in the folder. It follows the host for the same
        // reason /vmcomputers create does: only a matching guest can be hardware accelerated.
        VmSpec.Architecture architecture = QemuBinary.nativeArchitecture();
        saved.setArchitecture(architecture);
        try {
            VMComputers.getPlugin().getComputerDao().updateArchitecture(saved.id(), architecture);
        } catch (SQLException e) {
            VMComputers.getPlugin().getLogger()
                    .severe("Could not save architecture: " + e.getMessage());
        }

        for (Map.Entry<ComponentSlot, ComponentType> entry
                : pending.installedComponents().entrySet()) {
            saved.install(entry.getKey(), entry.getValue());
            try {
                VMComputers.getPlugin().getComputerDao()
                        .saveComponent(saved.id(), entry.getKey().name(), entry.getValue().id());
            } catch (SQLException e) {
                VMComputers.getPlugin().getLogger()
                        .severe("Could not save components: " + e.getMessage());
            }
        }

        // The case's own tower model goes before the builder draws the computer's, or there would
        // be two towers in the same block.
        Location caseAt = pending.location(world).add(0.5, 0.0, 0.5);
        PartRenderer.despawn(caseAt, 2.0, PendingCase.DISPLAY_OWNER);

        List<Integer> mapIds = ComputerBuilder.build(world, saved);
        VMComputers.getPlugin().getRegistry().add(saved);
        try {
            VMComputers.getPlugin().getComputerDao().savePanels(saved.id(), mapIds);
            VMComputers.getPlugin().getComputerDao().deleteCase(pending.id());
        } catch (SQLException e) {
            VMComputers.getPlugin().getLogger().severe("Assemble cleanup failed: " + e.getMessage());
        }
        VMComputers.getPlugin().forgetPendingCase(pending);

        MonitorScreen screen = MonitorScreen.attach(saved, mapIds);
        if (screen != null) {
            VMComputers.getPlugin().registerScreen(screen);
            screen.fill(VMComputers.getPlugin().getMapPalette().match(0, 0, 0));
        }

        viewer.closeInventory();
        viewer.sendMessage(ChatColor.GREEN + "Assembled computer #" + saved.id() + " - "
                + size.describe() + ", " + architecture + ".");
        viewer.sendMessage(ChatColor.GRAY + "Right-click the tower to power it on, "
                + "sneak-right-click to open it up.");
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f);
    }
}
