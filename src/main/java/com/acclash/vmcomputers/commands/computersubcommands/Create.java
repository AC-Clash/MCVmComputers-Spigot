package com.acclash.vmcomputers.commands.computersubcommands;

import com.acclash.vmcomputers.VMComputers;
import com.acclash.vmcomputers.commands.ComputerSubCommand;
import com.acclash.vmcomputers.computer.Computer;
import com.acclash.vmcomputers.computer.ComputerLayout;
import com.acclash.vmcomputers.display.MonitorSize;
import com.acclash.vmcomputers.display.MonitorScreen;
import com.acclash.vmcomputers.emu.QemuBinary;
import com.acclash.vmcomputers.emu.VmSpec;
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
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
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

        List<Integer> mapIds = build(world, saved);
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

    private List<Integer> build(World world, Computer computer) {
        ComputerLayout layout = computer.layout();
        NamespacedKey monitorKey = new NamespacedKey(VMComputers.getPlugin(), "isMonitor");
        NamespacedKey chairKey = new NamespacedKey(VMComputers.getPlugin(), "isEChair");
        NamespacedKey idKey = new NamespacedKey(VMComputers.getPlugin(), "computerId");

        // Backing first: item frames need something solid behind them.
        // applyPhysics=false throughout: physics updates would pop attached blocks such as the
        // button and pressure plate off as dropped items.
        for (ComputerLayout.Offset offset : layout.backingBlocks()) {
            computer.locationOf(world, offset).getBlock().setType(Material.SMOOTH_STONE, false);
        }
        for (ComputerLayout.Offset offset : layout.deskBlocks()) {
            computer.locationOf(world, offset).getBlock().setType(Material.SMOOTH_STONE_SLAB, false);
        }

        // One map per panel, row-major from the top-left so tile order matches the framebuffer.
        List<Integer> mapIds = new ArrayList<Integer>();
        BlockFace screenFacing = computer.facing().getOppositeFace();
        for (ComputerLayout.Offset offset : layout.screenPanels()) {
            Location location = computer.locationOf(world, offset);
            location.getBlock().setType(Material.AIR, false);

            // Renderers are installed by MonitorScreen, which also reattaches them after a
            // restart. A fresh map is entirely colour 0, which is transparent rather than black,
            // so until that happens the frames would show the wall behind them.
            MapView view = Bukkit.createMap(world);
            mapIds.add(Integer.valueOf(view.getId()));

            ItemStack screen = new ItemStack(Material.FILLED_MAP);
            MapMeta meta = (MapMeta) screen.getItemMeta();
            meta.setMapView(view);
            screen.setItemMeta(meta);

            world.spawn(location, ItemFrame.class, frame -> {
                frame.setFacingDirection(screenFacing, true);
                frame.setItem(screen);
                frame.setVisible(false);
                frame.setFixed(true);
                frame.getPersistentDataContainer().set(monitorKey, PersistentDataType.STRING, "true");
                frame.getPersistentDataContainer().set(idKey, PersistentDataType.INTEGER, computer.id());
            });
        }

        if (layout.chair() != null) {
            Block chairBlock = computer.locationOf(world, layout.chair()).getBlock();
            chairBlock.setType(Material.SPRUCE_STAIRS, false);
            Stairs stairs = (Stairs) chairBlock.getBlockData();
            stairs.setFacing(computer.facing().getOppositeFace());
            chairBlock.setBlockData(stairs, false);

            Location seat = chairBlock.getLocation().add(0.5, 0, 0.5);
            world.spawn(seat, org.bukkit.entity.Chicken.class, chicken -> {
                chicken.getPersistentDataContainer().set(chairKey, PersistentDataType.STRING, "true");
                chicken.getPersistentDataContainer().set(idKey, PersistentDataType.INTEGER, computer.id());
                chicken.setAI(false);
                chicken.setInvisible(true);
                chicken.setInvulnerable(true);
                chicken.setSilent(true);
                chicken.setRotation(yawOf(computer.facing()), 0f);
            });
        }

        if (layout.tower() != null) {
            computer.locationOf(world, layout.tower()).getBlock().setType(Material.SANDSTONE_WALL, false);
        }
        if (layout.control() != null) {
            computer.locationOf(world, layout.control()).getBlock().setType(Material.SANDSTONE_WALL, false);
        }
        if (layout.keyboard() != null) {
            computer.locationOf(world, layout.keyboard()).getBlock()
                    .setType(Material.HEAVY_WEIGHTED_PRESSURE_PLATE, false);
        }
        if (layout.mouse() != null) {
            Block mouse = computer.locationOf(world, layout.mouse()).getBlock();
            mouse.setBlockData(Material.STONE_BUTTON.createBlockData("[face=floor]"), false);
        }

        return mapIds;
    }

    private static float yawOf(BlockFace facing) {
        switch (facing) {
            case SOUTH:
                return 0f;
            case WEST:
                return 90f;
            case NORTH:
                return 180f;
            default:
                return -90f;
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
