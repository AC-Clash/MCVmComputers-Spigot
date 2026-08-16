package com.acclash.vmcomputers.parts;

import com.acclash.vmcomputers.VMComputers;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The company whose name is on the truck and the boxes.
 *
 * <p>Lettering is a {@link TextDisplay}, which is the only way to put arbitrary text on a surface
 * in the world without a resource pack -- and a pack is ruled out for the same reason it is ruled
 * out for the components themselves: a vanilla client should need nothing.
 *
 * <h2>Which way the text faces</h2>
 *
 * A text display with {@link Display.Billboard#FIXED} does not turn to follow the viewer; it hangs
 * in a plane fixed by the entity's rotation and its transformation. Every sign here is spawned
 * unrotated and given its facing through the transformation, exactly as {@link PartRenderer} does
 * for block displays, so that a sign and the panel it is stuck to are turned by the same quaternion
 * and cannot drift apart.
 *
 * <p>{@link #flank} holds the one assumption in all of this: that an unrotated fixed text display
 * reads from {@code +Z}. If lettering comes out mirrored or edge-on, that constant is the only
 * thing to change -- and {@code /vmcomputers parts delivery_truck} is the loop for checking it.
 */
public final class Branding {

    /**
     * What goes on the side of the truck. Gold lettering, to match what an Auro looks like in a
     * slot -- the money and the company that takes it read as the same brand.
     */
    public static final String COMPANY = ChatColor.GOLD + "AURA CHARISMA INC.";

    /**
     * What goes on a box. Two short lines rather than the full name: a package is 0.81 blocks on
     * its long side, and the whole company name scaled to fit that is a smear.
     */
    public static final String COMPANY_SHORT = ChatColor.GOLD + "AURA\nCHARISMA";

    /** Marks a sign so cleanup can find it without knowing who spawned it. */
    private static final String SIGN_KEY = "vmcSign";

    private Branding() {
    }

    /**
     * Rotation that lays a sign flat against a flank of a north-authored model.
     *
     * <p>Rotating {@code +Z} by {@code t} about {@code +Y} gives {@code (sin t, 0, cos t)}, so the
     * west flank (outward normal {@code -X}) wants {@code -90} and the east flank {@code +90}.
     *
     * @param east true for the {@code +X} side, false for the {@code -X} side
     */
    public static Quaternionf flank(boolean east) {
        return new Quaternionf().rotateY((float) Math.toRadians(east ? 90 : -90));
    }

    /**
     * Hangs a line of text on a surface.
     *
     * <p>Spawned at {@code origin} and positioned by its transformation, so a caller that moves a
     * model only has to move the entities -- the offsets ride along in the transform and never have
     * to be recomputed.
     *
     * @param origin where the model's bottom centre is
     * @param text   what to write; may contain newlines
     * @param offset centre of the lettering, relative to the model's bottom centre, before turning
     * @param turn   the model's own rotation, from {@link PartRenderer#yawFor}
     * @param east   which flank the sign is on
     * @param scale  size multiplier; 1.0 is roughly a quarter-block line height
     */
    public static TextDisplay sign(Location origin, String text, Vector3f offset, Quaternionf turn,
                                   boolean east, float scale) {
        Quaternionf left = new Quaternionf(turn).mul(flank(east));
        Vector3f translation = new Vector3f(offset).rotate(turn);
        Transformation transformation = new Transformation(
                translation, left, new Vector3f(scale, scale, scale), new Quaternionf());

        return origin.getWorld().spawn(origin, TextDisplay.class, display -> {
            display.setText(text);
            display.setBillboard(Display.Billboard.FIXED);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            // Wide enough that nothing wraps by accident. Line breaks here are the ones in the
            // string, so a two-line box label stays two lines and the truck's name stays one.
            display.setLineWidth(400);
            // Painted lettering, not a floating label: no dark plate behind it, no shadow, and it
            // must be occluded by whatever is in front of it rather than glowing through walls.
            display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            display.setShadowed(false);
            display.setSeeThrough(false);
            display.setDefaultBackground(false);
            display.setTransformation(transformation);
            display.setInterpolationDuration(0);
            display.setInterpolationDelay(0);
            // Saved with the chunk, matching PartRenderer: a sign on a delivered box has to come
            // back after a restart alongside the box it is painted on. Callers whose signs are
            // scenery for a few seconds turn this off again.
            display.setPersistent(true);
            display.getPersistentDataContainer()
                    .set(signKey(), PersistentDataType.STRING, "true");
        });
    }

    private static NamespacedKey signKey() {
        return new NamespacedKey(VMComputers.getPlugin(), SIGN_KEY);
    }
}
