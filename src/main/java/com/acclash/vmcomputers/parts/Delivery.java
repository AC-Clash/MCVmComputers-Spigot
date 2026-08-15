package com.acclash.vmcomputers.parts;

import com.acclash.vmcomputers.VMComputers;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Packages: what an order turns into on its way to the player.
 *
 * <p>The mod drops a chest out of the sky, and delivery is most of the fun of ordering. Here a
 * package is a cardboard box drawn from the mod's own {@code package} model, plus an
 * {@link Interaction} entity to click -- a display entity has no hitbox, so on its own it is
 * scenery you cannot touch.
 *
 * <p>The contents live in the interaction's persistent data rather than a table. A package is a
 * short-lived thing sitting in the world, and an entity's data is saved and restored with the chunk
 * it is in, so a delivery survives a restart without the plugin tracking it at all. Nothing else
 * needs to know a package exists.
 */
public final class Delivery {

    private static final String CONTENTS_KEY = "packageContents";
    private static final String PACKAGE_KEY = "vmcPackage";

    /** Owner id for delivery decoration, so it is never mistaken for part of a computer. */
    private static final int DELIVERY_OWNER = -2;

    private Delivery() {
    }

    /**
     * Drops a package near a player.
     *
     * @param contents components in the box, in order
     */
    public static void drop(Player player, List<ComponentType> contents) {
        Location spot = landingSpot(player);
        World world = spot.getWorld();

        StringBuilder ids = new StringBuilder();
        for (ComponentType type : contents) {
            ids.append(ids.length() == 0 ? "" : ",").append(type.id());
        }
        final String packed = ids.toString();

        PartRenderer.spawnNamed(spot, player.getFacing().getOppositeFace(), "package",
                1.0f, DELIVERY_OWNER);

        world.spawn(spot, Interaction.class, interaction -> {
            // Sized to the package model, which is roughly half a block across and a bit under
            // half a block tall.
            interaction.setInteractionWidth(0.7f);
            interaction.setInteractionHeight(0.5f);
            interaction.setResponsive(true);
            PersistentDataContainer data = interaction.getPersistentDataContainer();
            data.set(packageKey(), PersistentDataType.STRING, "true");
            data.set(contentsKey(), PersistentDataType.STRING, packed);
        });

        world.playSound(spot, Sound.BLOCK_WOOL_PLACE, 1.0f, 0.8f);
    }

    /** True if this entity is a package waiting to be opened. */
    public static boolean isPackage(org.bukkit.entity.Entity entity) {
        return entity instanceof Interaction
                && entity.getPersistentDataContainer()
                .has(packageKey(), PersistentDataType.STRING);
    }

    /**
     * Empties a package into a player's inventory and clears it away.
     *
     * @return what they received, or null if their inventory was too full to take it all
     */
    public static List<ComponentType> open(Player player, Interaction box) {
        String packed = box.getPersistentDataContainer()
                .get(contentsKey(), PersistentDataType.STRING);
        List<ComponentType> contents = new ArrayList<ComponentType>();
        if (packed != null && !packed.isEmpty()) {
            for (String id : packed.split(",")) {
                ComponentType type = ComponentType.byId(id);
                if (type != null) {
                    contents.add(type);
                }
            }
        }

        // Refuse rather than part-deliver: a package that empties halfway leaves the player with
        // no way to get the rest, since opening it removes it.
        int free = 0;
        for (org.bukkit.inventory.ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null) {
                free++;
            }
        }
        if (free < contents.size()) {
            return null;
        }

        for (ComponentType type : contents) {
            player.getInventory().addItem(type.toItemStack(1));
        }

        Location at = box.getLocation();
        PartRenderer.despawn(at, 2.0, DELIVERY_OWNER);
        box.remove();
        at.getWorld().playSound(at, Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
        return contents;
    }

    /**
     * Somewhere sensible to put a box: just in front of the player, on the ground.
     *
     * <p>Falls back to their own feet if the spot in front is obstructed, which is better than a
     * package inside a wall.
     */
    private static Location landingSpot(Player player) {
        Location base = player.getLocation();
        Vector forward = base.getDirection().setY(0);
        Location candidate = forward.lengthSquared() < 1.0e-6
                ? base.clone()
                : base.clone().add(forward.normalize().multiply(1.5));

        candidate = new Location(base.getWorld(),
                Math.floor(candidate.getX()) + 0.5,
                base.getY(),
                Math.floor(candidate.getZ()) + 0.5);

        if (!candidate.getBlock().isPassable()
                || !candidate.getBlock().getRelative(BlockFace.UP).isPassable()) {
            return new Location(base.getWorld(),
                    Math.floor(base.getX()) + 0.5, base.getY(), Math.floor(base.getZ()) + 0.5);
        }
        return candidate;
    }

    private static NamespacedKey contentsKey() {
        return new NamespacedKey(VMComputers.getPlugin(), CONTENTS_KEY);
    }

    private static NamespacedKey packageKey() {
        return new NamespacedKey(VMComputers.getPlugin(), PACKAGE_KEY);
    }
}
