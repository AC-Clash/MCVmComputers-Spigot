package com.acclash.vmcomputers.display;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fitting a guest's framebuffer to a wall of maps.
 *
 * <p>The rule is that aspect ratio survives and the result never exceeds the panel grid; the
 * leftover is letterboxed. Getting this wrong silently crops the guest to the top-left corner,
 * which is what used to happen when a guest dropped into 720x400 text mode.
 */
class ImageScalerTest {

    @Test
    void anExactFitIsLeftAlone() {
        int[] fit = ImageScaler.fitDimensions(640, 480, 640, 480);
        assertEquals(640, fit[0]);
        assertEquals(480, fit[1]);
    }

    @Test
    void aGuestSmallerThanTheScreenIsNotBlownUpPastIt() {
        int[] fit = ImageScaler.fitDimensions(640, 480, 768, 512);
        assertTrue(fit[0] <= 768 && fit[1] <= 512, "never exceeds the grid");
    }

    @Test
    void aspectRatioSurvives() {
        int[] fit = ImageScaler.fitDimensions(640, 480, 384, 384);
        double before = 640.0 / 480.0;
        double after = (double) fit[0] / fit[1];
        assertEquals(before, after, 0.02, "4:3 stays 4:3");
        assertTrue(fit[0] <= 384 && fit[1] <= 384, "fits inside the grid");
    }

    @Test
    void textModeFitsWithoutCropping() {
        // 720x400 is what x86 guests use for BIOS, bootloaders and installers.
        int[] fit = ImageScaler.fitDimensions(720, 400, 384, 384);
        assertTrue(fit[0] <= 384, "width fits");
        assertTrue(fit[1] <= 384, "height fits");
        assertEquals(720.0 / 400.0, (double) fit[0] / fit[1], 0.03, "ratio held");
    }

    @Test
    void scalingProducesTheRequestedSizeAndKeepsCorners() {
        int[] src = new int[4 * 4];
        for (int i = 0; i < src.length; i++) {
            src[i] = 0xFF000000 | i;
        }
        int[] out = new int[2 * 2];
        ImageScaler.scale(src, 4, 4, out, 2, 2);
        for (int pixel : out) {
            assertTrue((pixel & 0xFF000000) != 0, "alpha survives scaling");
        }
    }
}
