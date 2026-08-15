package com.acclash.vmcomputers.parts;

import com.acclash.vmcomputers.computer.ComputerLayout;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Furniture built to fit a computer, rather than converted from the mod.
 *
 * <p>The mod has no desk -- it puts a case on the ground and a monitor on a block. The desk is this
 * plugin's own idea, and it has to be, because a screen here is a wall of maps two to six blocks
 * across and a player has to sit at a sensible distance from it. So there is no model to port:
 * the geometry is generated to match whatever {@link ComputerLayout} decided the desk footprint is.
 *
 * <p>That is also why this cannot live in {@code parts.json}. A generated model is one fixed shape;
 * a desk is a different size for every monitor, so it is built at placement time from the layout's
 * own bounds. Deriving it from {@link ComputerLayout#deskBlocks()} rather than recomputing the
 * footprint keeps the visible desk and the blocks the computer claims from ever disagreeing.
 *
 * <p>Coordinates match {@link PartRenderer}'s convention: {@code x} is the computer's right,
 * {@code y} is up, and {@code z} is <em>negative</em> forward, since a model is authored facing
 * north and north is {@code -Z}. Everything is relative to the anchor block's bottom centre.
 */
public final class Furniture {

    /**
     * Height of the desk surface above the anchor, in blocks.
     *
     * <p>Fixed at half a block because that is where the top of the smooth stone slab used to be,
     * and both the keyboard and mouse placement and the seated player's sight line to the screen
     * were tuned against it. Raising the desk means re-tuning the seat distances in
     * {@code MonitorSize}.
     */
    public static final float SURFACE_HEIGHT = 0.5f;

    private static final float TOP_THICKNESS = 0.125f;
    private static final float LEG_THICKNESS = 0.125f;
    /** How far the legs sit in from the edge of the top, so the top visibly overhangs them. */
    private static final float LEG_INSET = 0.09375f;

    // The palette knob. A light top over dark legs reads as a desk at a glance and keeps the black
    // PC case from disappearing into it. Both are blocks whose texture is the same on all six
    // faces, which matters -- a display shows the whole block model, so a log would bring its end
    // grain along to the underside.
    private static final String TOP_BLOCK = "minecraft:stripped_oak_wood";
    private static final String LEG_BLOCK = "minecraft:black_concrete";

    private Furniture() {
    }

    /**
     * Builds a desk covering the layout's desk footprint.
     *
     * @return the desk, or null if this layout has no desk (a projector has none)
     */
    public static PartModel desk(ComputerLayout layout) {
        List<ComputerLayout.Offset> blocks = layout.deskBlocks();
        if (blocks.isEmpty()) {
            return null;
        }

        int minRight = Integer.MAX_VALUE;
        int maxRight = Integer.MIN_VALUE;
        int minForward = Integer.MAX_VALUE;
        int maxForward = Integer.MIN_VALUE;
        for (ComputerLayout.Offset offset : blocks) {
            minRight = Math.min(minRight, offset.right);
            maxRight = Math.max(maxRight, offset.right);
            minForward = Math.min(minForward, offset.forward);
            maxForward = Math.max(maxForward, offset.forward);
        }

        // A block at offset (r, u, f) spans r +/- 0.5 across and sits at -f on the z axis, so the
        // footprint runs half a block past the outermost blocks in each direction.
        float x0 = minRight - 0.5f;
        float x1 = maxRight + 0.5f;
        float z0 = -maxForward - 0.5f;
        float z1 = -minForward + 0.5f;

        // TOP_BLOCK and LEG_BLOCK are hand-edited constants, so a typo is a live possibility.
        // Failing here would abort Create half way through and leave a partly built computer
        // behind; skipping the desk leaves a usable machine and a line in the log.
        BlockData top;
        BlockData leg;
        try {
            top = Bukkit.createBlockData(TOP_BLOCK);
            leg = Bukkit.createBlockData(LEG_BLOCK);
        } catch (IllegalArgumentException e) {
            Bukkit.getLogger().warning("[VMComputers] Desk block is not valid on this server ("
                    + TOP_BLOCK + " / " + LEG_BLOCK + "): " + e.getMessage() + " -- desk skipped.");
            return null;
        }

        List<PartModel.Piece> pieces = new ArrayList<PartModel.Piece>();
        pieces.add(box(top, x0, SURFACE_HEIGHT - TOP_THICKNESS, z0, x1, SURFACE_HEIGHT, z1));

        // One leg per corner. Inset far enough that the top overhangs, which is what makes it read
        // as a desk rather than a solid block with a lid.
        float legTop = SURFACE_HEIGHT - TOP_THICKNESS;
        float[] legX = {x0 + LEG_INSET, x1 - LEG_INSET - LEG_THICKNESS};
        float[] legZ = {z0 + LEG_INSET, z1 - LEG_INSET - LEG_THICKNESS};
        for (float lx : legX) {
            for (float lz : legZ) {
                pieces.add(box(leg, lx, 0f, lz, lx + LEG_THICKNESS, legTop, lz + LEG_THICKNESS));
            }
        }

        return new PartModel("desk", pieces);
    }

    /** A box from one corner to the other, in the local frame. */
    private static PartModel.Piece box(BlockData block, float x0, float y0, float z0,
                                       float x1, float y1, float z1) {
        Vector3f size = new Vector3f(x1 - x0, y1 - y0, z1 - z0);
        Vector3f centre = new Vector3f((x0 + x1) / 2f, (y0 + y1) / 2f, (z0 + z1) / 2f);
        return new PartModel.Piece(block, size, centre, null, 0f, null);
    }
}
