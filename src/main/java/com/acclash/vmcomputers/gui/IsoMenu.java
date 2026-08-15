package com.acclash.vmcomputers.gui;

import com.acclash.vmcomputers.VMComputers;
import com.acclash.vmcomputers.computer.Computer;
import com.acclash.vmcomputers.emu.VmPaths;
import com.acclash.vmcomputers.emu.VmService;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Disc picker for a computer's drive.
 *
 * <p>Lists whatever is in {@code plugins/vm_computers/isos}, which is the same source
 * {@code /vmcomputers iso} reads, so an admin still adds operating systems by dropping files in a
 * folder. This only replaces the typing.
 */
public class IsoMenu extends Menu {

    private static final int MAX_ROWS = 5;

    private final Computer computer;
    private final Menu parent;
    private final Map<Integer, String> choices = new HashMap<Integer, String>();

    public IsoMenu(Player viewer, Computer computer, Menu parent) {
        super(viewer);
        this.computer = computer;
        this.parent = parent;
    }

    @Override
    public String title() {
        return ChatColor.DARK_GRAY + "Insert disc";
    }

    @Override
    public int size() {
        return (MAX_ROWS + 1) * ROW;
    }

    @Override
    public void draw() {
        choices.clear();
        List<String> isos = VmPaths.availableIsos();

        for (int i = 0; i < isos.size() && i < MAX_ROWS * ROW; i++) {
            String name = isos.get(i);
            boolean current = name.equals(computer.isoName());
            set(i, button(current ? Material.MUSIC_DISC_CAT : Material.MUSIC_DISC_11,
                    (current ? ChatColor.GREEN : ChatColor.WHITE) + name,
                    current ? "Currently in the drive." : "Click to insert."));
            choices.put(Integer.valueOf(i), name);
        }

        if (isos.isEmpty()) {
            set(0, button(Material.BARRIER, ChatColor.RED + "No discs available",
                    "Drop .iso files into",
                    VmPaths.isoDirectory().toAbsolutePath().toString()));
        }

        fillRow(MAX_ROWS);
        set(MAX_ROWS * ROW, button(Material.ARROW, ChatColor.YELLOW + "Back",
                "Return to the computer."));
        set(MAX_ROWS * ROW + 8, button(Material.HOPPER, ChatColor.YELLOW + "Eject",
                "Take the disc out."));
    }

    @Override
    public void onClick(int slot, ClickType click) {
        if (slot == MAX_ROWS * ROW) {
            parent.open();
            return;
        }
        if (slot == MAX_ROWS * ROW + 8) {
            insert(null);
            return;
        }
        String name = choices.get(Integer.valueOf(slot));
        if (name != null) {
            insert(name);
        }
    }

    private void insert(String isoName) {
        computer.setIsoName(isoName);
        try {
            VMComputers.getPlugin().getComputerDao().updateIso(computer.id(), isoName);
        } catch (SQLException e) {
            VMComputers.getPlugin().getLogger().severe("Iso update failed: " + e.getMessage());
            viewer.sendMessage(ChatColor.RED + "That did not save; see the console.");
            return;
        }

        viewer.sendMessage(isoName == null
                ? ChatColor.YELLOW + "Ejected the disc."
                : ChatColor.GREEN + "Inserted " + isoName + ".");
        if (VmService.isRunning(computer.id())) {
            viewer.sendMessage(ChatColor.GRAY
                    + "It is running -- power it off and on for this to take effect.");
        }
        parent.open();
    }
}
