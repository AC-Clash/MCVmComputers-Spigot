package com.acclash.vmcomputers.parts;

import com.acclash.vmcomputers.VMComputers;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
 * it is in, so a delivery survives a restart without the plugin tracking it at all.
 *
 * <p>A second order does not make a second box. It goes into the one already standing there, if
 * that box belongs to the same player and is close enough to be the one they are looking at.
 * Stacking boxes in one spot put an unopened package inside another, where its click target was
 * shadowed by the one in front and its contents were unreachable.
 */
public final class Delivery {

    private static final String CONTENTS_KEY = "packageContents";
    private static final String PACKAGE_KEY = "vmcPackage";
    private static final String DISPLAYS_KEY = "packageDisplays";
    private static final String OWNER_KEY = "packageOwner";

    /** How far away an existing package can be and still be the one to add to. */
    private static final double MERGE_RADIUS = 4.0;

    /** Owner id for delivery decoration, so it is never mistaken for part of a computer. */
    private static final int DELIVERY_OWNER = -2;

    private Delivery() {
    }

    /**
     * Sends an order to a player, as one box.
     *
     * @return true if a new box was dropped, false if it went into one already standing there
     */
    public static boolean send(Player player, List<ComponentType> contents) {
        Interaction existing = nearbyPackageOf(player);
        if (existing != null) {
            List<ComponentType> merged = new ArrayList<ComponentType>(read(existing));
            merged.addAll(contents);
            write(existing, merged);
            Location at = existing.getLocation();
            at.getWorld().playSound(at, Sound.BLOCK_WOOL_PLACE, 1.0f, 1.1f);
            return false;
        }
        drop(player, contents);
        return true;
    }

    private static void drop(Player player, List<ComponentType> contents) {
        Location spot = landingSpot(player);
        World world = spot.getWorld();

        List<BlockDisplay> displays = PartRenderer.spawnNamedDisplays(
                spot, player.getFacing().getOppositeFace(), "package", 1.0f, DELIVERY_OWNER);

        StringBuilder ids = new StringBuilder();
        for (BlockDisplay display : displays) {
            ids.append(ids.length() == 0 ? "" : ",").append(display.getUniqueId());
        }
        final String displayIds = ids.toString();
        final UUID owner = player.getUniqueId();

        world.spawn(spot, Interaction.class, interaction -> {
            // Sized to the package model, which is roughly half a block across and a bit under
            // half a block tall.
            interaction.setInteractionWidth(0.7f);
            interaction.setInteractionHeight(0.5f);
            interaction.setResponsive(true);
            PersistentDataContainer data = interaction.getPersistentDataContainer();
            data.set(packageKey(), PersistentDataType.STRING, "true");
            data.set(ownerKey(), PersistentDataType.STRING, owner.toString());
            // The box's own displays, by id. Clearing them by proximity instead would take the
            // neighbouring box's model with them and leave an invisible thing to click.
            data.set(displaysKey(), PersistentDataType.STRING, displayIds);
            data.set(contentsKey(), PersistentDataType.STRING, pack(contents));
        });

        world.playSound(spot, Sound.BLOCK_WOOL_PLACE, 1.0f, 0.8f);
    }

    /** True if this entity is a package waiting to be opened. */
    public static boolean isPackage(Entity entity) {
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
        List<ComponentType> contents = read(box);

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
        removeDisplays(box);
        box.remove();
        at.getWorld().playSound(at, Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
        return contents;
    }

    /** Removes exactly the displays this box spawned, by id. */
    private static void removeDisplays(Interaction box) {
        String ids = box.getPersistentDataContainer()
                .get(displaysKey(), PersistentDataType.STRING);
        if (ids == null || ids.isEmpty()) {
            // Older packages, from before ids were recorded. Falling back to proximity is what
            // this exists to avoid, but leaving the model behind is worse.
            PartRenderer.despawn(box.getLocation(), 1.0, DELIVERY_OWNER);
            return;
        }
        for (String id : ids.split(",")) {
            try {
                Entity display = org.bukkit.Bukkit.getEntity(UUID.fromString(id));
                if (display != null) {
                    display.remove();
                }
            } catch (IllegalArgumentException ignored) {
                // Not a uuid; nothing to remove.
            }
        }
    }

    /** An unopened package belonging to this player, close enough to be theirs to add to. */
    private static Interaction nearbyPackageOf(Player player) {
        String owner = player.getUniqueId().toString();
        for (Entity entity : player.getNearbyEntities(MERGE_RADIUS, MERGE_RADIUS, MERGE_RADIUS)) {
            if (!isPackage(entity)) {
                continue;
            }
            String entityOwner = entity.getPersistentDataContainer()
                    .get(ownerKey(), PersistentDataType.STRING);
            if (owner.equals(entityOwner)) {
                return (Interaction) entity;
            }
        }
        return null;
    }

    private static List<ComponentType> read(Interaction box) {
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
        return contents;
    }

    private static void write(Interaction box, List<ComponentType> contents) {
        box.getPersistentDataContainer()
                .set(contentsKey(), PersistentDataType.STRING, pack(contents));
    }

    private static String pack(List<ComponentType> contents) {
        StringBuilder ids = new StringBuilder();
        for (ComponentType type : contents) {
            ids.append(ids.length() == 0 ? "" : ",").append(type.id());
        }
        return ids.toString();
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

    private static NamespacedKey displaysKey() {
        return new NamespacedKey(VMComputers.getPlugin(), DISPLAYS_KEY);
    }

    private static NamespacedKey ownerKey() {
        return new NamespacedKey(VMComputers.getPlugin(), OWNER_KEY);
    }
}
