package com.acclash.vmcomputers.computer;

import com.acclash.vmcomputers.VMComputers;
import com.acclash.vmcomputers.parts.EChair;
import com.acclash.vmcomputers.parts.ComponentSlot;
import com.acclash.vmcomputers.parts.ComponentType;
import com.acclash.vmcomputers.parts.Furniture;
import com.acclash.vmcomputers.parts.PartModel;
import com.acclash.vmcomputers.parts.PartRenderer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Puts a computer into the world: backing, desk, screen panels, chair, tower and peripherals.
 *
 * <p>Lifted out of the create command so that assembling a case a player placed themselves builds
 * exactly the same thing. Two build paths that drift apart would be two different computers, and
 * only one of them would match what {@code Remove} tears down.
 *
 * <p>Every position comes from {@link ComputerLayout}, so this contains no hard-coded offsets.
 */
public final class ComputerBuilder {

    private ComputerBuilder() {
    }

    public static List<Integer> build(World world, Computer computer) {
        ComputerLayout layout = computer.layout();
        NamespacedKey monitorKey = new NamespacedKey(VMComputers.getPlugin(), "isMonitor");
        NamespacedKey idKey = new NamespacedKey(VMComputers.getPlugin(), "computerId");

        // Backing first: item frames need something solid behind them.
        // applyPhysics=false throughout: physics updates would pop attached blocks such as the
        // button and pressure plate off as dropped items.
        for (ComputerLayout.Offset offset : layout.backingBlocks()) {
            computer.locationOf(world, offset).getBlock().setType(Material.SMOOTH_STONE, false);
        }
        // The desk is drawn by display entities, so its blocks exist only to stop players walking
        // through it. BARRIER because it is invisible: a slab would show as a solid mass beneath
        // the desktop and hide the legs, which is the whole difference between a desk and a bench.
        for (ComputerLayout.Offset offset : layout.deskBlocks()) {
            computer.locationOf(world, offset).getBlock().setType(Material.BARRIER, false);
        }
        PartModel desk = Furniture.desk(layout);
        if (desk != null) {
            PartRenderer.spawn(computer.anchorLocation(world).add(0.5, 0.0, 0.5),
                    computer.facing(), desk, 1.0f, computer.id());
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

            // The seat itself is an eChair: an entity, because only an entity carries a passenger,
            // and a passenger is what a sitting player is. What it is made of lives in EChair and
            // is not this class's business.
            Location seat = chairBlock.getLocation().add(0.5, 0, 0.5);
            EChair.spawn(world, seat, computer.id(), yawOf(computer.facing()));
        }

        // The tower and the control block are what a player right-clicks to power the machine on,
        // and that path is driven by PlayerInteractEvent, which needs a real block -- a display
        // entity has no hitbox and an air click arrives with no block attached at all. So the
        // clickable volume stays a block and only its appearance moves to display entities.
        // BARRIER is used because it is invisible, solid and unbreakable in survival: the case the
        // player sees is the model, and the block behind it neither shows through nor pops off.
        if (layout.tower() != null) {
            Location tower = computer.locationOf(world, layout.tower());
            tower.getBlock().setType(Material.BARRIER, false);
            // Bottom centre of the block: the model's own origin is its bottom centre, so it
            // stands on the floor rather than sinking into it.
            PartRenderer.spawnNamed(tower.clone().add(0.5, 0.0, 0.5), computer.facing(),
                    "pc_case_sidepanel", 1.0f, computer.id());
        }
        if (layout.control() != null) {
            Location control = computer.locationOf(world, layout.control());
            control.getBlock().setType(Material.BARRIER, false);
            PartRenderer.spawnNamed(control.clone().add(0.5, 0.0, 0.5), computer.facing(),
                    "pc_case_sidepanel", 1.0f, computer.id());
        }

        // The keyboard and mouse are decoration -- nothing reads them as blocks -- so they become
        // pure display entities and their blocks are left as air.
        //
        // They sit on the desk surface, which is the top of a bottom slab: half a block above the
        // slab's own position, and half a block *below* the layout offset. The offset names the
        // block a plate would have occupied, and a plate rests on the floor of it; a model has to
        // be told the height explicitly.
        drawPeripherals(world, computer);

        return mapIds;
    }

    /**
     * Draws a desk accessory on the surface below its layout offset.
     *
     * <p>Facing is reversed: the layout's facing points away from the seated player, towards the
     * screen, but a keyboard and mouse are used from the player's side and so look back at them.
     */
    private static void placeOnDesk(World world, Computer computer, ComputerLayout.Offset offset,
                             String modelName) {
        if (offset == null) {
            return;
        }
        Location surface = computer.locationOf(world, offset).add(0.5, -0.5, 0.5);
        PartRenderer.spawnNamed(surface, computer.facing().getOppositeFace(), modelName,
                1.0f, computer.id());
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


    /**
     * Draws the desk peripherals that are actually fitted.
     *
     * <p>Unlike the tower and the screen, a keyboard and a mouse are optional components, so this
     * runs again whenever one is fitted or removed rather than only at build time.
     */
    public static void drawPeripherals(World world, Computer computer) {
        if (computer.installedIn(ComponentSlot.KEYBOARD) != null) {
            placeOnDesk(world, computer, computer.layout().keyboard(), "keyboard");
        }
        if (computer.installedIn(ComponentSlot.MOUSE) != null) {
            placeOnDesk(world, computer, computer.layout().mouse(), "mouse");
        }
    }

    /**
     * Clears the drawn peripherals so they can be redrawn from what is now fitted.
     *
     * <p>Removes every part display near the desk and puts back whatever is still installed --
     * simpler and less error-prone than tracking which entity belongs to which bay, and cheap
     * because it is only a handful of entities and only happens on a click.
     */
    public static void redrawPeripherals(World world, Computer computer) {
        ComputerLayout layout = computer.layout();
        for (ComputerLayout.Offset offset : new ComputerLayout.Offset[]{
                layout.keyboard(), layout.mouse()}) {
            if (offset != null) {
                PartRenderer.despawn(computer.locationOf(world, offset).add(0.5, 0.0, 0.5),
                        1.5, computer.id());
            }
        }
        drawPeripherals(world, computer);
    }
}
