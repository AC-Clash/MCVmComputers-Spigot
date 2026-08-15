package com.acclash.vmcomputers.parts;

import com.acclash.vmcomputers.VMComputers;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws {@link PartModel}s in the world as block display entities.
 *
 * <h2>The transform</h2>
 *
 * A block display renders its block in the unit cube from {@code (0,0,0)} to {@code (1,1,1)} with
 * the entity's location at the cube's <em>corner</em>, then applies
 * {@code translation + leftRotation * (scale * (rightRotation * v))} to every vertex.
 *
 * <p>Note that the translation is applied <em>after</em> the rotation and is not itself rotated.
 * That is the part which catches people out: turning a part to face a direction is not just a
 * matter of setting {@code leftRotation}, because each box's offset from the part's origin has to
 * be rotated by hand as well. Skip that and every box keeps its original offset while spinning in
 * place, which scatters the part instead of turning it.
 *
 * <p>So for a box of size {@code s} centred at {@code c}, with the part turned by {@code R}:
 *
 * <pre>
 *   scale        = s
 *   leftRotation = R
 *   translation  = R * (c - s/2)
 * </pre>
 *
 * and with a per-box rotation {@code Rp} about pivot {@code p} (only the CRT monitor uses one):
 *
 * <pre>
 *   leftRotation = R * Rp
 *   translation  = R * (Rp * (c - s/2 - p) + p)
 * </pre>
 *
 * <h2>Facing</h2>
 *
 * Blockbench models are authored looking towards {@code -Z}, which is north, so a part is turned
 * from north to its target direction. {@link #yawFor} is the only place that convention lives.
 */
public final class PartRenderer {

    /** Marks an entity as part of a computer's decoration, so cleanup can find it. */
    public static final String PART_KEY = "vmcPart";

    private PartRenderer() {
    }

    /**
     * Spawns one part.
     *
     * <p>Every display is tagged with the computer's id, which is what {@code Remove} already
     * sweeps on, so parts are torn down with the machine without needing their own bookkeeping.
     *
     * @param origin     where the part's bottom centre goes
     * @param facing     the direction the part should look
     * @param model      the shape to draw
     * @param scale      uniform size multiplier; 1.0 is the mod's own proportions
     * @param computerId owning computer, written to each entity for cleanup
     * @return the spawned displays, in model order
     */
    public static List<BlockDisplay> spawn(Location origin, BlockFace facing, PartModel model,
                                           float scale, int computerId) {
        World world = origin.getWorld();
        NamespacedKey idKey = new NamespacedKey(VMComputers.getPlugin(), "computerId");
        NamespacedKey partKey = new NamespacedKey(VMComputers.getPlugin(), PART_KEY);

        Quaternionf turn = new Quaternionf().rotateY(yawFor(facing));
        List<BlockDisplay> spawned = new ArrayList<BlockDisplay>(model.pieceCount());

        for (PartModel.Piece piece : model.pieces()) {
            final Transformation transformation = transformFor(piece, turn, scale);

            spawned.add(world.spawn(origin, BlockDisplay.class, display -> {
                display.setBlock(piece.block());
                display.setTransformation(transformation);
                // Displays interpolate towards a new transformation; these never move, and a zero
                // duration makes the first frame land in place instead of sliding in from the
                // entity's position.
                display.setInterpolationDuration(0);
                display.setInterpolationDelay(0);
                // Parts are small and there are several per computer, so they are not worth
                // rendering from across the map.
                display.setViewRange(0.6f);
                display.setPersistent(true);
                display.getPersistentDataContainer()
                        .set(idKey, PersistentDataType.INTEGER, Integer.valueOf(computerId));
                display.getPersistentDataContainer()
                        .set(partKey, PersistentDataType.STRING, model.name());
            }));
        }
        return spawned;
    }

    /**
     * The display transformation for one box: pure geometry, no Bukkit state.
     *
     * <p>Separated out because this is the one piece of real maths here and the mistake it guards
     * against is silent. Getting the translation wrong -- rotating the box but not its offset --
     * still produces a plausible-looking pile of blocks at roughly the right place, so it is not
     * obvious from a screenshot; it only shows up as a part that comes apart when built facing
     * south. Being pure, it can be checked against known bounds without a server.
     *
     * @param turn  rotation from the model's authored north to its target facing
     * @param scale uniform size multiplier
     */
    public static Transformation transformFor(PartModel.Piece piece, Quaternionf turn, float scale) {
        Vector3f size = piece.size().mul(scale);
        Vector3f corner = piece.centre().mul(scale).sub(size.x / 2f, size.y / 2f, size.z / 2f);

        Quaternionf left = new Quaternionf(turn);
        Vector3f translation;
        if (piece.isRotated()) {
            Quaternionf spin = new Quaternionf().rotateAxis(piece.angle(), piece.axis());
            Vector3f pivot = piece.pivot().mul(scale);
            // Rotate about the box's own pivot first, then turn the whole part. Both rotations
            // have to be applied to the offset as well, because a display's translation is added
            // after its rotation and is not itself rotated.
            translation = corner.sub(pivot).rotate(spin).add(pivot).rotate(turn);
            left.mul(spin);
        } else {
            translation = corner.rotate(turn);
        }
        return new Transformation(translation, left, size, new Quaternionf());
    }

    /**
     * Convenience for a part named in {@code parts.json}. Does nothing if the model is unknown,
     * which keeps a missing or mis-generated model from aborting a build half-finished.
     *
     * @return true if the part was drawn
     */
    public static boolean spawnNamed(Location origin, BlockFace facing, String modelName,
                                     float scale, int computerId) {
        PartModel model = PartModels.get(modelName);
        if (model == null) {
            VMComputers.getPlugin().getLogger()
                    .warning("No part model named '" + modelName + "'; nothing drawn.");
            return false;
        }
        spawn(origin, facing, model, scale, computerId);
        return true;
    }

    /**
     * Rotation that turns a model from its authored north-facing orientation to {@code facing}.
     *
     * <p>Rotating about {@code +Y} by {@code t} sends {@code (0,0,-1)} to {@code (-sin t, 0,
     * -cos t)}, which gives north 0, west 90, south 180, east 270. This is not Minecraft's entity
     * yaw convention (where 0 is south) and is not interchangeable with {@code Create.yawOf}.
     */
    public static float yawFor(BlockFace facing) {
        switch (facing) {
            case WEST:
                return (float) Math.toRadians(90);
            case SOUTH:
                return (float) Math.toRadians(180);
            case EAST:
                return (float) Math.toRadians(270);
            default:
                return 0f;
        }
    }

    /** Removes every part belonging to a computer within {@code radius} of a point. */
    public static int despawn(Location centre, double radius, int computerId) {
        NamespacedKey idKey = new NamespacedKey(VMComputers.getPlugin(), "computerId");
        NamespacedKey partKey = new NamespacedKey(VMComputers.getPlugin(), PART_KEY);
        int removed = 0;
        for (Entity entity : centre.getWorld().getNearbyEntities(centre, radius, radius, radius)) {
            if (!entity.getPersistentDataContainer().has(partKey, PersistentDataType.STRING)) {
                continue;
            }
            Integer owner = entity.getPersistentDataContainer()
                    .get(idKey, PersistentDataType.INTEGER);
            if (owner != null && owner.intValue() == computerId) {
                entity.remove();
                removed++;
            }
        }
        return removed;
    }
}
