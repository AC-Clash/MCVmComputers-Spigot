package com.acclash.vmcomputers.emu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Extension to QEMU format, for disk images an admin supplied rather than ones the plugin made.
 *
 * <p>Worth testing because getting it wrong fails in a way that reads as something else entirely.
 * QEMU told the wrong format does not say "wrong format" -- it reads the guest's boot sector as a
 * header and reports a corrupt image, so a perfectly good raw disk looks like a broken one.
 */
class DiskFormatTest {

    @Test
    void readsTheFormatsQemuNames() {
        assertEquals("qcow2", VmPaths.diskFormat("win95.qcow2"));
        assertEquals("vmdk", VmPaths.diskFormat("from-vmware.vmdk"));
        assertEquals("vdi", VmPaths.diskFormat("from-virtualbox.vdi"));
        assertEquals("qed", VmPaths.diskFormat("old.qed"));
    }

    @Test
    void rawGoesByEitherOfItsNames() {
        assertEquals("raw", VmPaths.diskFormat("dos.img"));
        assertEquals("raw", VmPaths.diskFormat("dos.raw"));
    }

    /** vhd and vhdx are different formats, and vhd's QEMU name is not either of its extensions. */
    @Test
    void hyperVsTwoFormatsDoNotCollide() {
        assertEquals("vpc", VmPaths.diskFormat("disk.vhd"));
        assertEquals("vhdx", VmPaths.diskFormat("disk.vhdx"));
    }

    @Test
    void isCaseInsensitiveBecauseWindowsAndMacsAre() {
        assertEquals("qcow2", VmPaths.diskFormat("WIN95.QCOW2"));
        assertEquals("raw", VmPaths.diskFormat("Dos.Img"));
    }

    /**
     * An unknown extension has to come back null rather than guessing. Guessing means picking a
     * format for a file nobody has checked, which is exactly the probing this avoids.
     */
    @Test
    void refusesWhatItDoesNotRecognise() {
        assertNull(VmPaths.diskFormat("notes.txt"));
        assertNull(VmPaths.diskFormat("ubuntu.iso"));
        assertNull(VmPaths.diskFormat("noextension"));
        assertNull(VmPaths.diskFormat(null));
    }

    /** The extension is the end of the name, not merely somewhere in it. */
    @Test
    void doesNotMatchAnExtensionInTheMiddle() {
        assertNull(VmPaths.diskFormat("qcow2-backups.tar"));
        assertNull(VmPaths.diskFormat("disk.img.gz"));
    }
}
