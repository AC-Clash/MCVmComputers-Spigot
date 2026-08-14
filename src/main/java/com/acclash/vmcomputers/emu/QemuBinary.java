package com.acclash.vmcomputers.emu;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Locates the QEMU binaries and reports what the host build can actually do.
 *
 * <p>We cannot assume a server admin has QEMU, nor that their build supports any particular
 * accelerator, so both are probed once at startup rather than guessed from the OS name. The
 * accelerator probe matters most: falling back to TCG silently would turn a fast VM into an
 * unusably slow one with no explanation.
 */
public final class QemuBinary {

    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    private final VmSpec.Architecture architecture;
    private final Path system;
    private final Path img;
    private final String version;
    private final Set<String> accelerators;

    private QemuBinary(VmSpec.Architecture architecture, Path system, Path img, String version,
                       Set<String> accelerators) {
        this.architecture = architecture;
        this.system = system;
        this.img = img;
        this.version = version;
        this.accelerators = accelerators;
    }

    public static QemuBinary discover() throws IOException {
        return discover(VmSpec.Architecture.X86_64);
    }

    /** Finds the system emulator for an architecture, plus {@code qemu-img}, and probes them. */
    public static QemuBinary discover(VmSpec.Architecture architecture) throws IOException {
        Path system = findOnPath(architecture.binaryName());
        if (system == null) {
            throw new IOException(architecture.binaryName() + " was not found on PATH. Install QEMU "
                    + "(macOS: brew install qemu, Debian/Ubuntu: apt install qemu-system-x86, "
                    + "Windows: https://qemu.weilnetz.de/w64/) or configure an explicit path.");
        }
        Path img = findOnPath("qemu-img");
        if (img == null) {
            throw new IOException("found " + system + " but qemu-img is not on PATH; "
                    + "it is needed to create and inspect disk images");
        }
        return at(architecture, system, img);
    }

    /** Probes an explicitly configured pair of binaries. */
    public static QemuBinary at(VmSpec.Architecture architecture, Path system, Path img)
            throws IOException {
        if (!Files.isExecutable(system)) {
            throw new IOException("not executable: " + system);
        }
        if (!Files.isExecutable(img)) {
            throw new IOException("not executable: " + img);
        }
        return new QemuBinary(architecture, system, img, probeVersion(system),
                probeAccelerators(system));
    }

    public VmSpec.Architecture architecture() {
        return architecture;
    }

    public Path systemBinary() {
        return system;
    }

    /**
     * Locates a firmware image shipped alongside the binary, such as the UEFI code ARM guests boot
     * from. Installations put these in {@code <prefix>/share/qemu}, one level up from {@code bin}.
     *
     * @return the path, or null if this build does not ship it
     */
    public Path firmware(String fileName) {
        Path binDirectory = system.getParent();
        if (binDirectory == null) {
            return null;
        }
        Path prefix = binDirectory.getParent();
        if (prefix == null) {
            return null;
        }
        Path candidate = prefix.resolve("share").resolve("qemu").resolve(fileName);
        return Files.isReadable(candidate) ? candidate : null;
    }

    public Path imgBinary() {
        return img;
    }

    /** Version string as reported by {@code -version}, or {@code "unknown"}. */
    public String version() {
        return version;
    }

    /** Accelerators this build supports, e.g. {@code [tcg, hvf]}. */
    public Set<String> accelerators() {
        return accelerators;
    }

    /**
     * Picks the best available accelerator, preferring hardware virtualization. Returns
     * {@code "tcg"} only if nothing better is present -- callers should warn loudly in that case,
     * because pure emulation is roughly two orders of magnitude slower.
     */
    public String bestAccelerator() {
        for (String candidate : preferredAccelerators()) {
            if (accelerators.contains(candidate)) {
                return candidate;
            }
        }
        return "tcg";
    }

    /** True when {@link #bestAccelerator()} found real hardware virtualization. */
    public boolean hasHardwareAcceleration() {
        return !"tcg".equals(bestAccelerator());
    }

    /** Host-appropriate accelerator preference order, best first. */
    /**
     * The architecture that can be hardware accelerated on this host, which is simply the one
     * matching the host CPU. Anything else has to be interpreted.
     */
    public static VmSpec.Architecture nativeArchitecture() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            return VmSpec.Architecture.AARCH64;
        }
        return VmSpec.Architecture.X86_64;
    }

    public static List<String> preferredAccelerators() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac") || os.contains("darwin")) {
            return Arrays.asList("hvf", "tcg");
        }
        if (os.contains("win")) {
            return Arrays.asList("whpx", "tcg");
        }
        // Linux and the BSDs.
        return Arrays.asList("kvm", "nvmm", "tcg");
    }

    @Override
    public String toString() {
        return "QemuBinary{" + architecture + ", " + system + ", version=" + version
                + ", accel=" + accelerators + ", best=" + bestAccelerator() + "}";
    }

    // ---- probing ---------------------------------------------------------

    private static String probeVersion(Path system) {
        try {
            String out = runAndCapture(Arrays.asList(system.toString(), "-version"), 10);
            // First line looks like "QEMU emulator version 9.1.0".
            int nl = out.indexOf('\n');
            String line = (nl < 0 ? out : out.substring(0, nl)).trim();
            return line.isEmpty() ? "unknown" : line;
        } catch (IOException e) {
            return "unknown";
        }
    }

    private static Set<String> probeAccelerators(Path system) {
        Set<String> found = new LinkedHashSet<String>();
        try {
            // Prints a header line then one accelerator name per line.
            String out = runAndCapture(Arrays.asList(system.toString(), "-accel", "help"), 15);
            for (String raw : out.split("\\R")) {
                String line = raw.trim();
                if (line.isEmpty() || line.endsWith(":")) {
                    continue;
                }
                if (line.toLowerCase(Locale.ROOT).contains("accelerator")) {
                    continue;
                }
                // Guard against future formatting changes leaking prose in here.
                if (line.matches("[a-z0-9_-]+")) {
                    found.add(line);
                }
            }
        } catch (IOException e) {
            // Leave the set empty; bestAccelerator() then falls back to tcg.
        }
        if (found.isEmpty()) {
            found.add("tcg");
        }
        return found;
    }

    private static Path findOnPath(String name) {
        List<String> names = new ArrayList<String>();
        if (WINDOWS) {
            // ProcessBuilder appends .exe when searching, but we resolve ourselves so that the
            // "not installed" error can name a concrete path.
            names.add(name + ".exe");
            names.add(name);
        } else {
            names.add(name);
        }

        String path = System.getenv("PATH");
        if (path == null) {
            return null;
        }
        for (String dir : path.split(File.pathSeparator)) {
            if (dir.isEmpty()) {
                continue;
            }
            for (String candidateName : names) {
                try {
                    Path candidate = Paths.get(dir).resolve(candidateName);
                    if (Files.isExecutable(candidate) && !Files.isDirectory(candidate)) {
                        return candidate;
                    }
                } catch (RuntimeException ignored) {
                    // Malformed PATH entry; skip it.
                }
            }
        }
        return null;
    }

    /**
     * Runs a short-lived helper command and returns its merged output. Used only for probes and
     * {@code qemu-img}, never for the VM process itself.
     */
    static String runAndCapture(List<String> command, int timeoutSeconds) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process p = pb.start();

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        try (InputStream in = p.getInputStream()) {
            int n;
            while ((n = in.read(chunk)) > 0) {
                buf.write(chunk, 0, n);
            }
        }

        boolean exited;
        try {
            exited = p.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
            throw new IOException("interrupted while running " + command.get(0), e);
        }
        String output = new String(buf.toByteArray(), StandardCharsets.UTF_8);
        if (!exited) {
            p.destroyForcibly();
            throw new IOException("timed out after " + timeoutSeconds + "s: " + command);
        }
        if (p.exitValue() != 0) {
            throw new IOException(command.get(0) + " exited " + p.exitValue() + ": " + output.trim());
        }
        return output;
    }

    /** Creates a qcow2 image. No-op if the file already exists. */
    public void createDisk(Path file, long sizeBytes) throws IOException {
        if (Files.exists(file)) {
            return;
        }
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        runAndCapture(Arrays.asList(
                img.toString(), "create", "-f", "qcow2", file.toString(), Long.toString(sizeBytes)), 60);
    }
}
