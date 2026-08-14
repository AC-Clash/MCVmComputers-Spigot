package com.acclash.vmcomputers.commands.computersubcommands;

import com.acclash.vmcomputers.VMComputers;
import com.acclash.vmcomputers.commands.ComputerSubCommand;
import com.acclash.vmcomputers.display.MapColorLut;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Diagnostic: does an {@link ItemDisplay} render a filled map's <em>contents</em>, or only the map
 * item icon?
 *
 * <p>Item frames special-case map rendering, and so does a held item, but an ItemDisplay goes
 * through the generic item renderer. If it does render contents, a monitor could be scaled to any
 * physical size -- a 6x4 grid at quarter scale becomes a believable 1.5x1 block desk monitor while
 * still carrying 768x512 pixels. If it does not, monitors are locked to one map per block.
 *
 * <p>This cannot be answered from the server side: it depends entirely on client rendering, so it
 * has to be looked at.
 */
public class TestDisplay extends ComputerSubCommand {

    private static final String TAG = "vmc-testdisplay";
    private static MapColorLut lut;

    @Override
    public String getName() {
        return "testdisplay";
    }

    @Override
    public String getDescription() {
        return "Spawns an item frame and item displays showing the same map, to compare rendering.";
    }

    @Override
    public String getSyntax() {
        return ChatColor.GOLD + "/vmcomputers testdisplay [clear]";
    }

    @Override
    public void perform(Player player, String[] args) {
        World world = player.getWorld();

        if (args.length > 1 && args[1].equalsIgnoreCase("clear")) {
            int removed = clearNearby(player);
            player.sendMessage(ChatColor.GREEN + "Removed " + removed + " test entities.");
            return;
        }

        if (lut == null) {
            player.sendMessage(ChatColor.GRAY + "Building colour lookup table...");
            long start = System.nanoTime();
            lut = MapColorLut.build(MapColorLut.DEFAULT_BITS);
            player.sendMessage(ChatColor.GRAY + "LUT built in "
                    + ((System.nanoTime() - start) / 1_000_000) + "ms, "
                    + lut.paletteSize() + " usable colours.");
        }

        MapView view = Bukkit.createMap(world);
        for (MapRenderer existing : new ArrayList<MapRenderer>(view.getRenderers())) {
            view.removeRenderer(existing);
        }
        view.addRenderer(new TestPatternRenderer(lut));

        ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) mapItem.getItemMeta();
        meta.setMapView(view);
        mapItem.setItemMeta(meta);

        BlockFace facing = player.getFacing();
        NamespacedKey key = new NamespacedKey(VMComputers.getPlugin(), TAG);

        // Control: an item frame, which is known to render map contents.
        Block frameBlock = player.getLocation().getBlock().getRelative(facing, 3).getRelative(BlockFace.UP);
        frameBlock.getRelative(facing).setType(Material.STONE);
        ItemFrame frame = world.spawn(frameBlock.getLocation(), ItemFrame.class, spawned -> {
            spawned.setFacingDirection(facing.getOppositeFace(), true);
            spawned.setItem(mapItem);
            spawned.setFixed(true);
            spawned.getPersistentDataContainer().set(key, PersistentDataType.STRING, "frame");
        });

        // Test subjects: same map, rendered as item displays at two scales.
        Location left = centreOf(frameBlock, facing, -2);
        Location right = centreOf(frameBlock, facing, 2);
        spawnDisplay(world, left, mapItem, facing, 1.0f, key, "display-1.0");
        spawnDisplay(world, right, mapItem, facing, 0.25f, key, "display-0.25");

        player.sendMessage("");
        player.sendMessage(ChatColor.AQUA + "Look at the three objects in front of you.");
        player.sendMessage(ChatColor.GRAY + "The map shows a " + ChatColor.RED + "red"
                + ChatColor.GRAY + " field, a " + ChatColor.WHITE + "white"
                + ChatColor.GRAY + " border, a diagonal, and a 4x4 checker in the middle.");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "  middle " + ChatColor.WHITE
                + "= item frame (control, should show the pattern)");
        player.sendMessage(ChatColor.YELLOW + "  left   " + ChatColor.WHITE
                + "= ItemDisplay at scale 1.0");
        player.sendMessage(ChatColor.YELLOW + "  right  " + ChatColor.WHITE
                + "= ItemDisplay at scale 0.25");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "If the displays show the pattern, scaled monitors work.");
        player.sendMessage(ChatColor.GRAY + "If they show a small parchment map icon instead, they do not.");
        player.sendMessage(ChatColor.GRAY + "Clean up with " + ChatColor.WHITE
                + "/vmcomputers testdisplay clear");
        frame.getLocation();
    }

    private Location centreOf(Block block, BlockFace facing, int sidewaysOffset) {
        BlockFace side = right(facing);
        Location location = block.getLocation().add(0.5, 0.5, 0.5);
        location.add(side.getModX() * sidewaysOffset, 0, side.getModZ() * sidewaysOffset);
        return location;
    }

    /** The block face to the right of a viewer looking along {@code facing}. */
    private static BlockFace right(BlockFace facing) {
        switch (facing) {
            case NORTH:
                return BlockFace.EAST;
            case EAST:
                return BlockFace.SOUTH;
            case SOUTH:
                return BlockFace.WEST;
            default:
                return BlockFace.NORTH;
        }
    }

    private void spawnDisplay(World world, Location location, ItemStack mapItem, BlockFace facing,
                              float scale, NamespacedKey key, String tag) {
        world.spawn(location, ItemDisplay.class, display -> {
            display.setItemStack(mapItem);
            // FIXED is the transform item frames use, so it is the best chance of getting the
            // map-content rendering path rather than the inventory icon.
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new AxisAngle4f(0f, 0f, 0f, 1f),
                    new Vector3f(scale, scale, scale),
                    new AxisAngle4f(0f, 0f, 0f, 1f)));
            display.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
            display.setRotation(yawOf(facing.getOppositeFace()), 0f);
            display.getPersistentDataContainer().set(key, PersistentDataType.STRING, tag);
        });
    }

    private static float yawOf(BlockFace face) {
        switch (face) {
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

    private int clearNearby(Player player) {
        NamespacedKey key = new NamespacedKey(VMComputers.getPlugin(), TAG);
        int removed = 0;
        for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), 24, 24, 24)) {
            if (entity.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                entity.remove();
                removed++;
            }
        }
        return removed;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return args.length == 2 ? Arrays.asList("clear") : null;
    }

    /** Draws something unmistakable, so the map picture cannot be confused with the item icon. */
    private static final class TestPatternRenderer extends MapRenderer {
        private final MapColorLut palette;
        private boolean drawn;

        TestPatternRenderer(MapColorLut palette) {
            super(false);
            this.palette = palette;
        }

        @Override
        public void render(MapView map, MapCanvas canvas, Player player) {
            if (drawn) {
                return;
            }
            drawn = true;

            byte red = palette.match(220, 30, 30);
            byte white = palette.match(245, 245, 245);
            byte black = palette.match(10, 10, 10);

            for (int y = 0; y < 128; y++) {
                for (int x = 0; x < 128; x++) {
                    byte colour;
                    if (x < 4 || y < 4 || x > 123 || y > 123) {
                        colour = white;
                    } else if (Math.abs(x - y) < 3) {
                        colour = white;
                    } else if (x > 40 && x < 88 && y > 40 && y < 88) {
                        colour = (((x - 40) / 12 + (y - 40) / 12) % 2 == 0) ? black : white;
                    } else {
                        colour = red;
                    }
                    canvas.setPixel(x, y, colour);
                }
            }
        }
    }
}
