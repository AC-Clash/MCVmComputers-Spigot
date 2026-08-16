package com.acclash.vmcomputers.parts;

import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The display transform maths, which is the one piece of real geometry in the plugin and the one
 * whose mistakes are invisible.
 *
 * <p>A wrong transform still renders. It renders a plausible pile of blocks slightly in the wrong
 * place, or a part that comes apart only when built facing south, and a screenshot will not show
 * it. So these tests reconstruct the eight corners of each rendered box and check where they
 * actually land, rather than asserting on the transform's fields -- the fields are the thing most
 * likely to be reasoned about wrongly.
 *
 * <p>In this package because {@link PartModel.Piece} is package-private to construct.
 */
class PartRendererTest {

    /** No server, so no block data. Nothing in the maths reads it. */
    private static PartModel.Piece box(Vector3f size, Vector3f centre) {
        return new PartModel.Piece(null, size, centre, null, 0f, null, null, false);
    }

    private static PartModel.Piece spun(Vector3f size, Vector3f centre, Vector3f axis,
                                        float radians, Vector3f pivot) {
        return new PartModel.Piece(null, size, centre, axis, radians, pivot, null, false);
    }

    /**
     * Where a rendered box actually is: a display draws the unit cube, scaled, rotated, then
     * translated, so its world corners are the eight unit corners put through the transform.
     *
     * @return {minX, minY, minZ, maxX, maxY, maxZ} relative to the entity position
     */
    private static double[] bounds(Transformation t) {
        double[] lo = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] hi = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (int corner = 0; corner < 8; corner++) {
            Vector3f v = new Vector3f(
                    (corner & 1) == 0 ? 0f : t.getScale().x(),
                    (corner & 2) == 0 ? 0f : t.getScale().y(),
                    (corner & 4) == 0 ? 0f : t.getScale().z());
            t.getLeftRotation().transform(v);
            v.add(t.getTranslation());
            double[] p = {v.x, v.y, v.z};
            for (int i = 0; i < 3; i++) {
                lo[i] = Math.min(lo[i], p[i]);
                hi[i] = Math.max(hi[i], p[i]);
            }
        }
        return new double[]{lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]};
    }

    private static void assertBounds(double[] actual, double[] expected) {
        String[] names = {"minX", "minY", "minZ", "maxX", "maxY", "maxZ"};
        for (int i = 0; i < 6; i++) {
            assertEquals(expected[i], actual[i], 1.0e-5, names[i]);
        }
    }

    @Test
    void unrotatedBoxSitsOnItsOwnCentre() {
        // A 0.5 cube centred half a block up is a cube resting on the origin.
        Transformation t = PartRenderer.transformFor(
                box(new Vector3f(0.5f, 0.5f, 0.5f), new Vector3f(0f, 0.25f, 0f)),
                new Quaternionf(), 1.0f);
        assertBounds(bounds(t), new double[]{-0.25, 0.0, -0.25, 0.25, 0.5, 0.25});
    }

    @Test
    void scaleMultipliesBothSizeAndOffset() {
        Transformation t = PartRenderer.transformFor(
                box(new Vector3f(0.5f, 0.5f, 0.5f), new Vector3f(1f, 0.25f, 0f)),
                new Quaternionf(), 2.0f);
        // Centre moves to 2.0, box becomes 1.0 across: 1.5 to 2.5.
        assertBounds(bounds(t), new double[]{1.5, 0.0, -0.5, 2.5, 1.0, 0.5});
    }

    /**
     * The mistake this whole class exists for. A display's translation is applied after its
     * rotation and is not itself rotated, so turning a part means rotating each box's offset by
     * hand as well. Forget it and every box spins in place while keeping its original offset,
     * which scatters the part instead of turning it.
     */
    @Test
    void turningAPartMovesOffCentreBoxesRatherThanSpinningThemInPlace() {
        PartModel.Piece offset = box(new Vector3f(0.2f, 0.2f, 0.2f), new Vector3f(1f, 0.1f, 0f));

        Transformation south = PartRenderer.transformFor(
                offset, new Quaternionf().rotateY((float) Math.toRadians(180)), 1.0f);
        double[] b = bounds(south);

        // Turned to face south, a box that was one block to the +X side is now one block to -X.
        assertEquals(-1.0, (b[0] + b[3]) / 2, 1.0e-5, "centre X after a half turn");
        assertEquals(0.0, (b[2] + b[5]) / 2, 1.0e-5, "centre Z after a half turn");
        assertEquals(0.1, (b[1] + b[4]) / 2, 1.0e-5, "height is unaffected by a turn about Y");
    }

    @Test
    void aQuarterTurnSendsPlusXToMinusZ() {
        // yawFor(WEST) is +90 degrees, and rotating +X by that lands on -Z.
        PartModel.Piece offset = box(new Vector3f(0.2f, 0.2f, 0.2f), new Vector3f(1f, 0.1f, 0f));
        double[] b = bounds(PartRenderer.transformFor(
                offset, new Quaternionf().rotateY((float) Math.toRadians(90)), 1.0f));
        assertEquals(0.0, (b[0] + b[3]) / 2, 1.0e-5, "centre X");
        assertEquals(-1.0, (b[2] + b[5]) / 2, 1.0e-5, "centre Z");
    }

    /**
     * A box with its own rotation about its own centre keeps its centre and swaps its extents,
     * which is exactly what the truck's landing gear does when it folds flat.
     */
    @Test
    void rotatingAboutItsOwnCentreKeepsTheCentreAndSwapsTheExtents() {
        Vector3f size = new Vector3f(0.32f, 0.72f, 0.72f);
        Vector3f centre = new Vector3f(-0.86f, 0.38f, -1.45f);
        Transformation t = PartRenderer.transformFor(
                spun(size, centre, new Vector3f(0f, 0f, 1f), (float) Math.toRadians(90), centre),
                new Quaternionf(), 1.0f);
        double[] b = bounds(t);

        assertEquals(-0.86, (b[0] + b[3]) / 2, 1.0e-5, "centre X is unmoved by an in-place spin");
        assertEquals(0.38, (b[1] + b[4]) / 2, 1.0e-5, "centre Y is unmoved");
        assertEquals(-1.45, (b[2] + b[5]) / 2, 1.0e-5, "centre Z is unmoved");

        // A quarter turn about Z trades the X and Y extents: a tall thin wheel becomes a flat disc.
        assertEquals(0.72, b[3] - b[0], 1.0e-5, "width after the fold");
        assertEquals(0.32, b[4] - b[1], 1.0e-5, "height after the fold");
        assertEquals(0.72, b[5] - b[2], 1.0e-5, "depth is untouched by a spin about Z");
    }

    @Test
    void foldedGearLiftsClearOfTheGround() {
        Vector3f size = new Vector3f(0.32f, 0.72f, 0.72f);
        // Deployed: a wheel whose centre is half its height up, so it touches y=0.
        double[] down = bounds(PartRenderer.transformFor(
                box(size, new Vector3f(-0.86f, 0.36f, -1.45f)), new Quaternionf(), 1.0f));
        // Stowed: turned flat and lifted slightly.
        Vector3f up = new Vector3f(-0.86f, 0.38f, -1.45f);
        double[] folded = bounds(PartRenderer.transformFor(
                spun(size, up, new Vector3f(0f, 0f, 1f), (float) Math.toRadians(90), up),
                new Quaternionf(), 1.0f));

        assertEquals(0.0, down[1], 1.0e-5, "deployed gear reaches the ground");
        assertTrue(folded[1] > down[1], "stowed gear is off the ground");
        assertTrue(folded[4] - folded[1] < down[4] - down[1], "stowed gear is flatter");
    }

    @Test
    void yawForFollowsTheAuthoredNorthConvention() {
        assertEquals(0f, PartRenderer.yawFor(org.bukkit.block.BlockFace.NORTH), 1.0e-6);
        assertEquals((float) Math.toRadians(90),
                PartRenderer.yawFor(org.bukkit.block.BlockFace.WEST), 1.0e-6);
        assertEquals((float) Math.toRadians(180),
                PartRenderer.yawFor(org.bukkit.block.BlockFace.SOUTH), 1.0e-6);
        assertEquals((float) Math.toRadians(270),
                PartRenderer.yawFor(org.bukkit.block.BlockFace.EAST), 1.0e-6);
    }
}
