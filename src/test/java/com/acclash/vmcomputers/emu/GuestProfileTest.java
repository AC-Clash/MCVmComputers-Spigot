package com.acclash.vmcomputers.emu;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The era table, which decides whether an old guest boots at all.
 *
 * <p>Everything here is the kind of rule that fails silently. A memory ceiling that does not apply
 * gives Windows 95 four gigabytes and a machine that will not start; an ISA sound card on a machine
 * with no ISA bus is simply quiet. None of it shows up as an error, so it is worth pinning down.
 */
class GuestProfileTest {

    @Test
    void autoIsNeverWhatReachesQemu() {
        for (VmSpec.Architecture arch : VmSpec.Architecture.values()) {
            for (boolean accelerated : new boolean[]{true, false}) {
                assertNotSame(GuestProfile.AUTO,
                        GuestProfile.AUTO.resolve(arch, accelerated),
                        "AUTO must resolve to a real profile");
            }
        }
    }

    private static void assertNotSame(Object unexpected, Object actual, String message) {
        assertFalse(unexpected == actual, message);
    }

    /** There is no legacy ARM: nothing old runs on the virt board, accelerated or not. */
    @Test
    void armIsAlwaysModern() {
        assertSame(GuestProfile.MODERN_ARM,
                GuestProfile.AUTO.resolve(VmSpec.Architecture.AARCH64, true));
        assertSame(GuestProfile.MODERN_ARM,
                GuestProfile.AUTO.resolve(VmSpec.Architecture.AARCH64, false));
    }

    /**
     * The whole reason the old guess was wrong. On a host that accelerates x86 -- most servers --
     * an x86 guest is a modern one, and capping it at a legacy profile is what left Ubuntu with
     * 256 MB and no mouse. Only where x86 cannot be accelerated does it still imply something old.
     */
    @Test
    void x86MeansModernWhereItIsAccelerated() {
        assertSame(GuestProfile.MODERN_LINUX,
                GuestProfile.AUTO.resolve(VmSpec.Architecture.X86_64, true));
        assertSame(GuestProfile.WIN9X,
                GuestProfile.AUTO.resolve(VmSpec.Architecture.X86_64, false));
    }

    /**
     * A 32-bit board is only ever fitted deliberately, and nothing modern is 32-bit, so it says
     * "old guest" far more directly than the accelerator ever could.
     */
    @Test
    void a32BitBoardMeansAnOldGuest() {
        assertSame(GuestProfile.WIN9X,
                GuestProfile.AUTO.resolve(VmSpec.Architecture.I386, true));
        assertSame(GuestProfile.WIN9X,
                GuestProfile.AUTO.resolve(VmSpec.Architecture.I386, false));
    }

    /** 32-bit guests run on either width; 64-bit ones do not. That is the board's whole job. */
    @Test
    void thirtyTwoBitGuestsRunOnEitherWidth() {
        assertTrue(GuestProfile.DOS.runsOn(VmSpec.Architecture.I386));
        assertTrue(GuestProfile.DOS.runsOn(VmSpec.Architecture.X86_64));
        assertTrue(GuestProfile.DELL_DIMENSION_L500R.runsOn(VmSpec.Architecture.I386));
        assertTrue(GuestProfile.WINXP.runsOn(VmSpec.Architecture.I386));
    }

    @Test
    void sixtyFourBitGuestsRefuseTheSmallerBoard() {
        assertFalse(GuestProfile.COMPAQ_PRESARIO.runsOn(VmSpec.Architecture.I386));
        assertFalse(GuestProfile.MODERN_LINUX.runsOn(VmSpec.Architecture.I386));
        assertFalse(GuestProfile.MODERN_WINDOWS.runsOn(VmSpec.Architecture.I386));
        assertTrue(GuestProfile.COMPAQ_PRESARIO.runsOn(VmSpec.Architecture.X86_64));
    }

    /** XP x64 on a 32-bit board is the case this exists to catch, in both directions. */
    @Test
    void thePresarioAndTheDimensionDisagreeAboutWidth() {
        assertTrue(GuestProfile.COMPAQ_PRESARIO.needs64Bit());
        assertFalse(GuestProfile.DELL_DIMENSION_L500R.needs64Bit());
    }

    @Test
    void a32BitMachineIsNotOfferedGuestsItCannotRun() {
        List<GuestProfile> i386 = GuestProfile.forArchitecture(VmSpec.Architecture.I386);
        assertTrue(i386.contains(GuestProfile.DELL_DIMENSION_L500R));
        assertTrue(i386.contains(GuestProfile.DOS));
        assertFalse(i386.contains(GuestProfile.COMPAQ_PRESARIO));
        assertFalse(i386.contains(GuestProfile.MODERN_LINUX));
        assertFalse(i386.contains(GuestProfile.MODERN_ARM));
    }

    /** Everything BIOS-shaped must apply to both PC widths, or i386 silently loses it. */
    @Test
    void bothPcWidthsCountAsX86() {
        assertTrue(VmSpec.Architecture.I386.isX86());
        assertTrue(VmSpec.Architecture.X86_64.isX86());
        assertFalse(VmSpec.Architecture.AARCH64.isX86());
        assertFalse(VmSpec.Architecture.I386.is64Bit());
        assertTrue(VmSpec.Architecture.X86_64.is64Bit());
        assertTrue(VmSpec.Architecture.AARCH64.is64Bit());
    }

    @Test
    void namedProfilesResolveToThemselves() {
        assertSame(GuestProfile.DELL_DIMENSION_L500R,
                GuestProfile.DELL_DIMENSION_L500R.resolve(VmSpec.Architecture.X86_64, true));
    }

    @Test
    void memoryCeilingsApplyOnlyWhereSet() {
        // Too much memory is as fatal as too little for these two.
        assertEquals(64, GuestProfile.DOS.clampMemory(4096));
        assertEquals(512, GuestProfile.WIN9X.clampMemory(4096));
        assertEquals(512, GuestProfile.DELL_DIMENSION_L500R.clampMemory(4096));
        // And a ceiling never raises what was asked for.
        assertEquals(64, GuestProfile.WIN9X.clampMemory(64));
        // Modern guests have none.
        assertEquals(4096, GuestProfile.MODERN_LINUX.clampMemory(4096));
        assertEquals(4096, GuestProfile.MODERN_ARM.clampMemory(4096));
    }

    /** XP x64 is the point of this one, and it needs more than the 2 GB ordinary XP is capped at. */
    @Test
    void thePresarioCanTakeMoreThanOrdinaryXp() {
        assertEquals(2048, GuestProfile.WINXP.clampMemory(4096));
        assertEquals(4096, GuestProfile.COMPAQ_PRESARIO.clampMemory(4096));
    }

    /**
     * DOS and Windows 9x are uniprocessor. Handed a second core they either ignore it or trip over
     * the MP tables that come with it, and the machine that shipped as a Windows 98 box was never
     * going to have two.
     */
    @Test
    void preSmpGuestsGetOneCore() {
        assertEquals(1, GuestProfile.DOS.clampCpus(8));
        assertEquals(1, GuestProfile.WIN9X.clampCpus(8));
        assertEquals(1, GuestProfile.DELL_DIMENSION_L500R.clampCpus(8));
        // And a ceiling never raises what was asked for.
        assertEquals(1, GuestProfile.WIN9X.clampCpus(1));
    }

    @Test
    void everythingWithSmpKeepsItsCores() {
        assertEquals(8, GuestProfile.WINXP.clampCpus(8));
        assertEquals(8, GuestProfile.COMPAQ_PRESARIO.clampCpus(8));
        assertEquals(8, GuestProfile.MODERN_LINUX.clampCpus(8));
        assertEquals(8, GuestProfile.MODERN_ARM.clampCpus(8));
    }

    /** Pre-USB guests need a relative PS/2 mouse; an absolute tablet leaves them with no pointer. */
    @Test
    void onlyGuestsWithUsbGetAnAbsolutePointer() {
        assertFalse(GuestProfile.DOS.hasAbsolutePointer());
        assertFalse(GuestProfile.WIN9X.hasAbsolutePointer());
        assertFalse(GuestProfile.DELL_DIMENSION_L500R.hasAbsolutePointer());
        assertTrue(GuestProfile.WINXP.hasAbsolutePointer());
        assertTrue(GuestProfile.COMPAQ_PRESARIO.hasAbsolutePointer());
        assertTrue(GuestProfile.MODERN_ARM.hasAbsolutePointer());
    }

    /** The fake disk is how files reach a guest with no networking and no clipboard. */
    @Test
    void onlyGuestsTooOldToDownloadGetTheSharedFolder() {
        assertTrue(GuestProfile.DOS.wantsSharedFolder());
        assertTrue(GuestProfile.DELL_DIMENSION_L500R.wantsSharedFolder());
        assertFalse(GuestProfile.WINXP.wantsSharedFolder());
        assertFalse(GuestProfile.MODERN_LINUX.wantsSharedFolder());
    }

    @Test
    void anArchitectureIsOnlyOfferedProfilesItCanRun() {
        List<GuestProfile> arm = GuestProfile.forArchitecture(VmSpec.Architecture.AARCH64);
        assertTrue(arm.contains(GuestProfile.MODERN_ARM));
        assertTrue(arm.contains(GuestProfile.AUTO), "AUTO suits any architecture");
        assertFalse(arm.contains(GuestProfile.DOS), "there is no ARM DOS");
        assertFalse(arm.contains(GuestProfile.DELL_DIMENSION_L500R));

        List<GuestProfile> x86 = GuestProfile.forArchitecture(VmSpec.Architecture.X86_64);
        assertTrue(x86.contains(GuestProfile.DELL_DIMENSION_L500R));
        assertTrue(x86.contains(GuestProfile.COMPAQ_PRESARIO));
        assertFalse(x86.contains(GuestProfile.MODERN_ARM));
    }

    @Test
    void parsesWhatTheDatabaseAndChatSend() {
        assertSame(GuestProfile.DOS, GuestProfile.parse("DOS"));
        assertSame(GuestProfile.DELL_DIMENSION_L500R,
                GuestProfile.parse("dell_dimension_l500r"));
        // A name that no longer exists must not stop the machine loading.
        assertNull(GuestProfile.parse("WIN311"));
        assertNull(GuestProfile.parse(null));
    }

    /**
     * Sound Blaster is an ISA card, so a profile that picks one must also pick a board with an ISA
     * bus. Selecting SB16 while leaving the machine at q35 is exactly the combination that shipped
     * once and made no sound at all.
     */
    @Test
    void everyIsaSoundCardComesWithAnIsaBoard() {
        for (GuestProfile profile : GuestProfile.values()) {
            if (profile.soundCard() != VmSpec.SoundCard.SB16) {
                continue;
            }
            assertEquals("pc", profile.machine(),
                    profile + " picks an ISA sound card, so it needs the pc board to put it on");
        }
    }

    /**
     * Cirrus is the only adapter Windows 9x has a driver for in the box, and it is also the one
     * with no EDID -- so a profile that picks it must be one that does not need to ask for a mode.
     */
    @Test
    void theWin9xEraGetsTheOnlyCardItHasDriversFor() {
        assertSame(VmSpec.Vga.CIRRUS, GuestProfile.WIN9X.vga());
        assertSame(VmSpec.Vga.CIRRUS, GuestProfile.DELL_DIMENSION_L500R.vga());
        assertSame(VmSpec.Vga.CIRRUS, GuestProfile.DOS.vga());
    }

    /**
     * XP x64 cannot load the Cirrus driver Windows ships, because it is 32-bit only. Plain VGA is
     * the one that works, and picking Cirrus here would be a black screen after install.
     */
    @Test
    void thePresarioAvoidsTheDriverXpX64CannotLoad() {
        assertSame(VmSpec.Vga.STD, GuestProfile.COMPAQ_PRESARIO.vga());
    }

    /**
     * Windows has no virtio drivers in the box, so a virtio disk means an installer that runs and
     * then reports no drive to install onto.
     */
    @Test
    void theWindowsProfilesNeverAskForVirtio() {
        assertSame("pc", GuestProfile.COMPAQ_PRESARIO.machine());
        assertSame(VmSpec.Vga.VIRTIO, GuestProfile.MODERN_LINUX.vga());
        assertFalse(GuestProfile.MODERN_WINDOWS.vga() == VmSpec.Vga.VIRTIO);
    }

    @Test
    void everyProfileNamesItselfAndSaysWhatItIsFor() {
        for (GuestProfile profile : GuestProfile.values()) {
            assertNotNull(profile.label());
            assertFalse(profile.label().isEmpty());
            assertNotNull(profile.description());
            assertFalse(profile.description().isEmpty(), profile + " needs a description");
        }
    }
}
