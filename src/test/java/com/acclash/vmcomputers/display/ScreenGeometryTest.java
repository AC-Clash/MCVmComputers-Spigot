package com.acclash.vmcomputers.display;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a look ray lands on the screen.
 *
 * <p>The bug these guard against already happened and took a session to name: the ray was
 * intersected with the near face of the panel block rather than with the picture, which hangs
 * almost a block further in. That is not a small offset, it is parallax -- the pointer falls short
 * of wherever the player aims, by an amount that grows the further off-axis they are and vanishes
 * when they stand square in front. It was reported as "the cursor never reaches all the way I
 * turn, unless I walk in front of the spot", which is not an obvious description of a depth error.
 *
 * <p>So the test that matters is {@link #sweepingTheAimAcrossThePictureReachesBothEdges()}.
 * Measured on a LARGE desk before the fix, a full sweep covered 69% of the width.
 *
 * <p>Note the size handed to {@code setGuestResolution} is the <em>displayed</em> size, after the
 * frame has been scaled to fit the panel grid -- that is what {@code MonitorScreen} passes.
 */
class ScreenGeometryTest {

    /** LARGE is 4x3 panels, 512x384 pixels. Bottom-left panel at the origin, viewer looking -Z. */
    private static final MonitorSize SIZE = MonitorSize.LARGE;

    private static ScreenGeometry screen() {
        ScreenGeometry geometry = ScreenGeometry.wallMounted(SIZE, new int[]{0, 64, 0}, 0, -1);
        // A 640x480 guest scaled to fit 512x384 lands exactly on the grid, so no letterbox.
        geometry.setGuestResolution(SIZE.pixelWidth(), SIZE.pixelHeight());
        return geometry;
    }

    // The picture spans x 0..4 and y 64..67, at z = 1 - 0.96875.
    private static final double PLANE_Z = 0.03125;
    private static final double MID_X = SIZE.columns() / 2.0;
    private static final double MID_Y = 64 + SIZE.rows() / 2.0;

    @Test
    void aimingAtTheMiddleLandsInTheMiddle() {
        ScreenGeometry.Hit hit = screen().trace(
                new double[]{MID_X, MID_Y, 4.0}, new double[]{0, 0, -1});

        assertNotNull(hit, "a ray aimed straight at the screen must hit it");
        assertTrue(hit.onImage, "the middle of the screen is picture, not letterbox");
        assertTrue(Math.abs(hit.imageX - SIZE.pixelWidth() / 2) <= 1, "horizontally centred");
        assertTrue(Math.abs(hit.imageY - SIZE.pixelHeight() / 2) <= 1, "vertically centred");
    }

    @Test
    void aRayPointingAwayFromTheScreenMisses() {
        assertNull(screen().trace(new double[]{MID_X, MID_Y, 4.0}, new double[]{0, 0, 1}),
                "the screen is behind the viewer, so this is a miss and not a hit through the wall");
    }

    @Test
    void aRayPastTheEdgeOfTheScreenMisses() {
        assertNull(screen().trace(new double[]{MID_X, MID_Y, 4.0},
                        new double[]{20, 0, -1}),
                "aiming well off to the side hits nothing");
    }

    @Test
    void pixelRowsCountDownFromTheTop() {
        ScreenGeometry geometry = screen();
        ScreenGeometry.Hit high = geometry.trace(
                new double[]{MID_X, 66.5, 4.0}, new double[]{0, 0, -1});
        ScreenGeometry.Hit low = geometry.trace(
                new double[]{MID_X, 64.5, 4.0}, new double[]{0, 0, -1});

        assertNotNull(high);
        assertNotNull(low);
        assertTrue(high.imageY < low.imageY,
                "looking higher up the wall is a smaller pixel row, because images count downward");
    }

    /**
     * The parallax regression, stated as what a player would notice: sweeping the aim from one edge
     * of the picture to the other has to move the pointer from one edge to the other.
     */
    @Test
    void sweepingTheAimAcrossThePictureReachesBothEdges() {
        ScreenGeometry geometry = screen();
        double eyeZ = 3.0;

        int leftmost = Integer.MAX_VALUE;
        int rightmost = Integer.MIN_VALUE;
        for (int step = 0; step <= 400; step++) {
            double targetX = SIZE.columns() * (step / 400.0);
            ScreenGeometry.Hit hit = geometry.trace(
                    MID_X, MID_Y, eyeZ,
                    targetX - MID_X, 0, PLANE_Z - eyeZ);
            if (hit != null) {
                leftmost = Math.min(leftmost, hit.imageX);
                rightmost = Math.max(rightmost, hit.imageX);
            }
        }

        assertTrue(leftmost <= 2,
                "the left edge of the picture must be reachable; nearest was " + leftmost);
        assertTrue(rightmost >= SIZE.pixelWidth() - 3,
                "the right edge must be reachable; furthest was " + rightmost);
    }

    /**
     * A guest that does not fill the grid is centred, and the bands either side are not the guest.
     * Clicking there must not be reported as a click at the guest's edge.
     */
    @Test
    void theLetterboxIsNotPartOfThePicture() {
        ScreenGeometry geometry = ScreenGeometry.wallMounted(SIZE, new int[]{0, 64, 0}, 0, -1);
        // A 4:3 guest displayed narrower than the grid leaves a band on each side.
        int displayedWidth = SIZE.pixelWidth() - 128;
        geometry.setGuestResolution(displayedWidth, SIZE.pixelHeight());

        double eyeZ = 3.0;
        ScreenGeometry.Hit edge = geometry.trace(
                MID_X, MID_Y, eyeZ, 0.02 - MID_X, 0, PLANE_Z - eyeZ);

        assertNotNull(edge, "the ray still lands on the panel wall");
        assertFalse(edge.onImage, "but the far edge of the wall is letterbox, not guest");

        ScreenGeometry.Hit middle = geometry.trace(
                new double[]{MID_X, MID_Y, eyeZ}, new double[]{0, 0, -1});
        assertNotNull(middle);
        assertTrue(middle.onImage, "while the middle still is the guest");
    }
}
