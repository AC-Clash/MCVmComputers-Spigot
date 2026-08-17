package com.acclash.vmcomputers.emu;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Where disk images and installer media live on the host.
 *
 * <p>These sit outside the plugin's own data folder, under {@code plugins/vm_computers}, because
 * disk images are large and long-lived: an admin should be able to drop an ISO in without going
 * near plugin internals, and reinstalling the plugin must not delete anyone's virtual machines.
 */
public final class VmPaths {

    private static final String ROOT = "plugins" + File.separator + "vm_computers";

    private VmPaths() {
    }

    public static Path root() {
        return Paths.get(ROOT);
    }

    /** Installer media dropped in by the server admin. */
    public static Path isoDirectory() {
        return root().resolve("isos");
    }

    /**
     * Disk images the admin supplied, as opposed to the ones this plugin creates.
     *
     * <p>Separate from {@code hdds} on purpose. Everything in {@code hdds} is owned by the plugin,
     * named after a computer id and deleted with that computer; everything here belongs to the
     * admin and is only ever opened, never created, resized or removed. Keeping them in one folder
     * would make "delete the computer's disk" ambiguous in exactly the case where being wrong is
     * unrecoverable.
     *
     * <p>The point is to install the awkward guests once, by hand, in whatever tool is comfortable,
     * and copy the result in. Windows 95 is far easier to install in a normal QEMU window than
     * through a wall of maps.
     */
    public static Path diskDirectory() {
        return root().resolve("disks");
    }

    /**
     * Floppy images, which are how the oldest guests get installed at all.
     *
     * <p>Not a nostalgia feature. A retail Windows 95 CD is not bootable, so the supported way to
     * install it is to boot a DOS floppy carrying CD-ROM drivers and run setup from there; without
     * a floppy drive that guest cannot be installed from its own media. Windows XP's F6 driver
     * prompt reads a floppy and nothing else either.
     */
    public static Path floppyDirectory() {
        return root().resolve("floppies");
    }

    /** One qcow2 per computer, named by its id. */
    /**
     * Folder handed to guests as a read-only drive.
     *
     * <p>The way to get files into a machine that has no network drivers, no shared clipboard and
     * no way to accept a download -- which is most of the interesting ones. Drop something in here
     * and it appears as a disk inside every guest that gets one.
     */
    public static Path sharedDirectory() {
        return root().resolve("shared");
    }

    public static Path diskFor(int computerId) {
        return root().resolve("hdds").resolve("computer-" + computerId + ".qcow2");
    }

    /**
     * Private UEFI variable store for a computer, on architectures that boot via UEFI.
     *
     * <p>Must be per machine and writable: an installed system records its boot entry here, and
     * sharing one copy between machines would have them overwrite each other's.
     */
    public static Path uefiVarsFor(int computerId) {
        return root().resolve("hdds").resolve("computer-" + computerId + "-vars.fd");
    }

    /** Copies the firmware's blank variable template into place if this computer has none yet. */
    public static Path ensureUefiVars(int computerId, Path template) throws IOException {
        Path vars = uefiVarsFor(computerId);
        if (template == null) {
            return null;
        }
        if (!Files.exists(vars)) {
            Files.createDirectories(vars.getParent());
            Files.copy(template, vars);
        }
        return vars;
    }

    public static void ensureDirectories() throws IOException {
        java.nio.file.Files.createDirectories(sharedDirectory());
        Files.createDirectories(isoDirectory());
        Files.createDirectories(diskDirectory());
        Files.createDirectories(floppyDirectory());
        Files.createDirectories(root().resolve("hdds"));
    }

    /**
     * Disk image extensions QEMU can open, mapped to the format name it wants told.
     *
     * <p>The format is always passed explicitly rather than left to QEMU's probing. Probing a raw
     * image means the guest's own first sector decides how the host reads the file, which QEMU
     * warns about for good reason; naming the format closes that off. It also turns "this file is
     * not what you think" into an error at boot rather than a guest that reads garbage.
     */
    private static final String[][] DISK_FORMATS = {
        { ".qcow2", "qcow2" },
        { ".qcow", "qcow" },
        { ".img", "raw" },
        { ".raw", "raw" },
        { ".vmdk", "vmdk" },
        { ".vdi", "vdi" },
        { ".vhdx", "vhdx" },
        { ".vhd", "vpc" },
        { ".vpc", "vpc" },
        { ".qed", "qed" },
    };

    /** ISO file names available to insert, sorted. */
    public static List<String> availableIsos() {
        return namesIn(isoDirectory(), ".iso");
    }

    /**
     * Floppy image extensions. All of them are raw sector dumps whatever they are called, which is
     * why there is no format table here the way there is for disks.
     */
    private static final String[] FLOPPY_EXTENSIONS =
            { ".img", ".ima", ".vfd", ".flp", ".dsk", ".fd" };

    /** Floppy image names available to insert, sorted. */
    public static List<String> availableFloppies() {
        return namesIn(floppyDirectory(), FLOPPY_EXTENSIONS);
    }

    /** Resolves a floppy image name to a readable file, or null. */
    public static Path resolveFloppy(String name) {
        return resolveWithin(floppyDirectory(), name);
    }

    /** Whether this name looks like a floppy image at all. */
    public static boolean isFloppyName(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (String extension : FLOPPY_EXTENSIONS) {
            if (lower.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    /** Admin-supplied disk image names available to attach, sorted. */
    public static List<String> availableDisks() {
        List<String> extensions = new ArrayList<String>();
        for (String[] entry : DISK_FORMATS) {
            extensions.add(entry[0]);
        }
        return namesIn(diskDirectory(), extensions.toArray(new String[0]));
    }

    /**
     * The QEMU format name for a disk image, taken from its extension.
     *
     * @return the format, or null if the extension is not one QEMU reads
     */
    public static String diskFormat(String name) {
        if (name == null) {
            return null;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (String[] entry : DISK_FORMATS) {
            if (lower.endsWith(entry[0])) {
                return entry[1];
            }
        }
        return null;
    }

    /**
     * Resolves an ISO name to a readable file.
     *
     * @return the path, or null if it does not exist or escapes the ISO directory
     */
    public static Path resolveIso(String name) {
        return resolveWithin(isoDirectory(), name);
    }

    /**
     * Resolves an admin-supplied disk image name to a readable file.
     *
     * @return the path, or null if it does not exist or escapes the disk directory
     */
    public static Path resolveDisk(String name) {
        return resolveWithin(diskDirectory(), name);
    }

    private static List<String> namesIn(Path directory, String... extensions) {
        List<String> names = new ArrayList<String>();
        File[] files = directory.toFile().listFiles();
        if (files == null) {
            return names;
        }
        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            String lower = file.getName().toLowerCase(Locale.ROOT);
            for (String extension : extensions) {
                if (lower.endsWith(extension)) {
                    names.add(file.getName());
                    break;
                }
            }
        }
        Collections.sort(names);
        return names;
    }

    /**
     * Resolves a bare file name inside one of the plugin's folders.
     *
     * <p>The containment check is the whole reason this is not just {@code resolve}. These names
     * arrive from chat, so a name like {@code ../../server.properties} would otherwise turn a
     * command any player can run into a way to read -- or with a disk, write -- arbitrary files on
     * the host.
     */
    private static Path resolveWithin(Path directory, String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        Path base = directory.toAbsolutePath().normalize();
        Path candidate = base.resolve(name).normalize();
        if (!candidate.startsWith(base) || !Files.isReadable(candidate)) {
            return null;
        }
        return candidate;
    }

    /** Deletes a computer's disk image, if it has one. */
    public static void deleteDisk(int computerId) {
        try {
            Files.deleteIfExists(uefiVarsFor(computerId));
            Files.deleteIfExists(diskFor(computerId));
        } catch (IOException ignored) {
            // Leaving a stale image behind is harmless.
        }
    }
}
