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

    /** One qcow2 per computer, named by its id. */
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
        Files.createDirectories(isoDirectory());
        Files.createDirectories(root().resolve("hdds"));
    }

    /** ISO file names available to insert, sorted. */
    public static List<String> availableIsos() {
        List<String> names = new ArrayList<String>();
        File directory = isoDirectory().toFile();
        File[] files = directory.listFiles();
        if (files == null) {
            return names;
        }
        for (File file : files) {
            if (file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".iso")) {
                names.add(file.getName());
            }
        }
        Collections.sort(names);
        return names;
    }

    /**
     * Resolves an ISO name to a readable file.
     *
     * @return the path, or null if it does not exist or escapes the ISO directory
     */
    public static Path resolveIso(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        Path directory = isoDirectory().toAbsolutePath().normalize();
        Path candidate = directory.resolve(name).normalize();
        // A name like "../../etc/passwd" must not reach outside the ISO folder.
        if (!candidate.startsWith(directory) || !Files.isReadable(candidate)) {
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
