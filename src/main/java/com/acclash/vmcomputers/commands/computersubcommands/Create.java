package com.acclash.vmcomputers.commands.computersubcommands;

import com.acclash.vmcomputers.VMComputers;
import com.acclash.vmcomputers.commands.ComputerSubCommand;
import com.acclash.vmcomputers.computer.Computer;
import com.acclash.vmcomputers.computer.ComputerBuilder;
import com.acclash.vmcomputers.computer.ComputerLayout;
import com.acclash.vmcomputers.display.MonitorSize;
import com.acclash.vmcomputers.display.MonitorScreen;
import com.acclash.vmcomputers.emu.QemuBinary;
import com.acclash.vmcomputers.emu.VmSpec;
import com.acclash.vmcomputers.parts.ComponentSlot;
import com.acclash.vmcomputers.parts.ComponentType;
import com.acclash.vmcomputers.parts.Furniture;
import com.acclash.vmcomputers.parts.PartModel;
import com.acclash.vmcomputers.parts.PartRenderer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds a computer in the world.
 *
 * <p>Placement is driven entirely by {@link ComputerLayout}, so this command never contains a
 * hard-coded offset. That is what lets a 2x2 desktop and a 24-panel projector share one code path,
 * and it guarantees that what gets built matches what {@code Remove} tears down and what the click
 * index believes.
 */
public class Create extends ComputerSubCommand {

    private static final String DEFAULT_TYPE = "Generic PC";

    @Override
    public String getName() {
        return "create";
    }

    @Override
    public String getDescription() {
        return "Creates a computer where you are standing.";
    }

    @Override
    public String getSyntax() {
        return ChatColor.GOLD + "Stand where the computer should go, face the screen, then: "
                + "/vmcomputers create <" + sizeList() + "> [x86_64|aarch64]";
    }

    private static String sizeList() {
        StringBuilder sb = new StringBuilder();
        for (MonitorSize size : MonitorSize.values()) {
            if (sb.length() > 0) {
                sb.append('|');
            }
            sb.append(size.name());
        }
        return sb.toString();
    }

    @Override
    public void perform(Player player, String[] args) {
        if (args.length < 2) {
            // TODO: open the size-selection menu here instead once the GUI exists.
            player.sendMessage(getSyntax());
            for (MonitorSize size : MonitorSize.values()) {
                player.sendMessage(ChatColor.GRAY + "  " + size.name() + " - " + size.describe()
                        + ", " + size.form().name().toLowerCase(Locale.ROOT)
                        + (size.requiresScaling() ? ", scaled (soft text)" : ", 1:1"));
            }
            return;
        }

        MonitorSize size;
        try {
            size = MonitorSize.valueOf(args[1].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            player.sendMessage(ChatColor.RED + "Unknown monitor size '" + args[1] + "'.");
            player.sendMessage(getSyntax());
            return;
        }

        // Architecture is fixed at build time, like real hardware. It defaults to the host CPU
        // because only a matching guest can be hardware accelerated -- anything else is emulated
        // instruction by instruction and roughly two orders of magnitude slower.
        VmSpec.Architecture architecture = QemuBinary.nativeArchitecture();
        if (args.length >= 3) {
            try {
                architecture = VmSpec.Architecture.valueOf(args[2].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                player.sendMessage(ChatColor.RED + "Unknown architecture '" + args[2]
                        + "'. Use X86_64 or AARCH64.");
                return;
            }
        }

        if (player.isInsideVehicle()) {
            player.sendMessage(ChatColor.YELLOW + "Get out of the chair first.");
            return;
        }

        BlockFace facing = player.getFacing();
        if (!Computer.isCardinal(facing)) {
            player.sendMessage(ChatColor.RED + "Face north, east, south or west.");
            return;
        }

        World world = player.getWorld();
        Block anchor = player.getLocation().getBlock();

        Computer computer = new Computer(-1, world.getName(), anchor.getX(), anchor.getY(),
                anchor.getZ(), facing, size, DEFAULT_TYPE, Computer.State.OFF);

        // Refuse before touching the world, so a rejected build never leaves debris behind.
        Computer clash = VMComputers.getPlugin().getRegistry().findOverlap(computer);
        if (clash != null) {
            player.sendMessage(ChatColor.RED + "That overlaps computer #" + clash.id() + ".");
            return;
        }

        Computer saved;
        try {
            saved = VMComputers.getPlugin().getComputerDao().insert(computer);
        } catch (SQLException e) {
            player.sendMessage(ChatColor.RED + "Could not save the computer; see the console.");
            VMComputers.getPlugin().getLogger().severe("Insert failed: " + e.getMessage());
            return;
        }

        // A computer built by this command arrives assembled: it builds the whole machine, desk
        // and all, so handing back an empty case that cannot boot would be a strange result. The
        // ordering route is where parts get fitted one at a time.
        for (java.util.Map.Entry<ComponentSlot, ComponentType> entry
                : ComponentType.defaultLoadout(saved.monitorSize()).entrySet()) {
            saved.install(entry.getKey(), entry.getValue());
            try {
                VMComputers.getPlugin().getComputerDao()
                        .saveComponent(saved.id(), entry.getKey().name(), entry.getValue().id());
            } catch (SQLException e) {
                VMComputers.getPlugin().getLogger()
                        .severe("Could not save components: " + e.getMessage());
            }
        }

        List<Integer> mapIds = ComputerBuilder.build(world, saved);
        VMComputers.getPlugin().getRegistry().add(saved);

        try {
            VMComputers.getPlugin().getComputerDao().savePanels(saved.id(), mapIds);
        } catch (SQLException e) {
            VMComputers.getPlugin().getLogger().severe("Could not save panel maps: " + e.getMessage());
        }

        saved.setArchitecture(architecture);
        try {
            VMComputers.getPlugin().getComputerDao().updateArchitecture(saved.id(), architecture);
        } catch (SQLException e) {
            VMComputers.getPlugin().getLogger().severe("Could not save architecture: " + e.getMessage());
        }

        MonitorScreen screen = MonitorScreen.attach(saved, mapIds);
        if (screen != null) {
            VMComputers.getPlugin().registerScreen(screen);
            screen.fill(VMComputers.getPlugin().getMapPalette().match(0, 0, 0));
        }

        player.sendMessage(ChatColor.GREEN + "Built computer #" + saved.id() + " - "
                + size.describe());
        boolean nativeArch = architecture == QemuBinary.nativeArchitecture();
        player.sendMessage((nativeArch ? ChatColor.GREEN : ChatColor.YELLOW) + "Architecture: "
                + architecture + (nativeArch ? " (hardware accelerated)" : " (emulated, slow)"));
        if (size.form() == MonitorSize.Form.PROJECTOR) {
            player.sendMessage(ChatColor.GRAY + "Projector screen. Stand back about "
                    + (int) size.viewingDistance() + " blocks; walk closer for finer pointer control.");
        } else {
            player.sendMessage(ChatColor.GRAY + "Right-click the chair to sit, the tower to power on.");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            List<String> sizes = new ArrayList<String>();
            for (MonitorSize size : MonitorSize.values()) {
                sizes.add(size.name());
            }
            return sizes;
        }
        if (args.length == 3) {
            List<String> architectures = new ArrayList<String>();
            for (VmSpec.Architecture architecture : VmSpec.Architecture.values()) {
                architectures.add(architecture.name());
            }
            return architectures;
        }
        return null;
    }
}
