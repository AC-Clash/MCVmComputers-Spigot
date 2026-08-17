package com.acclash.vmcomputers.emu;

import com.acclash.vmcomputers.VMComputers;
import com.acclash.vmcomputers.audio.AudioBus;
import com.acclash.vmcomputers.computer.Computer;
import com.acclash.vmcomputers.parts.ComponentSlot;
import com.acclash.vmcomputers.parts.ComponentType;
import com.acclash.vmcomputers.display.ImageScaler;
import com.acclash.vmcomputers.display.MapColorLut;
import com.acclash.vmcomputers.display.MonitorScreen;
import com.acclash.vmcomputers.rfb.RfbClient;
import com.acclash.vmcomputers.utils.ComputerFunctions;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

/**
 * Starts and stops the virtual machine behind a computer, and pipes its framebuffer to the screen.
 *
 * <p>Booting blocks for as long as QEMU takes to open its control and display sockets, so it runs
 * off the main thread. Frames then arrive on the RFB pump thread and are quantized and written
 * straight into the panel buffers there -- never on the server tick. The tick only does the map
 * render, which is where CraftBukkit compares pixels and decides what to transmit.
 */
public final class VmService {

    /** Ordered-dither strength in 0-255 colour units; enough to smooth a 244-colour palette. */
    private static final int DITHER_SPREAD = 20;

    private static final java.util.Map<VmSpec.Architecture, QemuBinary> BINARIES =
            new java.util.concurrent.ConcurrentHashMap<VmSpec.Architecture, QemuBinary>();

    private VmService() {
    }

    /**
     * Locates QEMU once and caches it.
     *
     * @throws IOException if QEMU is not installed, with an actionable message
     */
    public static QemuBinary qemu(VmSpec.Architecture architecture) throws IOException {
        QemuBinary cached = BINARIES.get(architecture);
        if (cached == null) {
            synchronized (VmService.class) {
                cached = BINARIES.get(architecture);
                if (cached == null) {
                    cached = QemuBinary.discover(architecture);
                    BINARIES.put(architecture, cached);
                }
            }
        }
        return cached;
    }

    /** True when a machine is already running for this computer. */
    public static boolean isRunning(int computerId) {
        VirtualMachine machine = ComputerFunctions.get(computerId);
        return machine != null && machine.isRunning();
    }

    /**
     * What a computer is doing between being asked and having done it.
     *
     * <p>Powering on and off are both slow enough to see -- QEMU has to be found, spawned and
     * connected to, and a graceful stop waits on the guest -- and both happen off the server
     * thread. Without somewhere to record that, anything showing power state has only two answers
     * for three situations, and a machine that has been asked to start looks identical to one
     * sitting switched off.
     *
     * <p>Deliberately not part of {@link Computer.State}: that is persisted, and a server killed
     * mid-boot would come back up claiming to be starting forever.
     */
    public enum Transition {
        STARTING,
        STOPPING
    }

    private static final java.util.Map<Integer, Transition> TRANSITIONS =
            new java.util.concurrent.ConcurrentHashMap<Integer, Transition>();

    /** What this computer is partway through, or null if it is simply on or off. */
    public static Transition transitionOf(int computerId) {
        return TRANSITIONS.get(Integer.valueOf(computerId));
    }

    /** True while a computer is starting or stopping and should not be asked to do either. */
    public static boolean isBusy(int computerId) {
        return TRANSITIONS.containsKey(Integer.valueOf(computerId));
    }

    /**
     * Boots a computer and connects its screen. Returns immediately; {@code feedback} is invoked on
     * the main thread as the boot progresses.
     */
    public static void start(Computer computer, MonitorScreen screen, Consumer<String> feedback) {
        if (isRunning(computer.id())) {
            feedback.accept("That computer is already running.");
            return;
        }

        String refusal = overLimit(computer);
        if (refusal != null) {
            feedback.accept(refusal);
            return;
        }

        VMComputers plugin = VMComputers.getPlugin();
        // Marked before the task is queued, on the thread the caller is already on, so a menu
        // redrawn on the very next tick already knows this machine is on its way up.
        TRANSITIONS.put(Integer.valueOf(computer.id()), Transition.STARTING);
        computer.setState(Computer.State.BOOTING);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // The fitted board decides the width, so this is asked before the binary is looked
                // up -- a 32-bit board is a different emulator, not a flag on the same one. The
                // profile is read unresolved on purpose: AUTO cannot need 64 bits, and resolving it
                // would need the accelerator, which needs the binary we are still choosing.
                VmSpec.Architecture arch = architectureFor(computer, computer.profile());
                if (arch != computer.architecture()) {
                    post(feedback, "32-bit motherboard fitted: running as " + arch + ".");
                }
                QemuBinary binary = qemu(arch);
                if (!binary.hasHardwareAcceleration()) {
                    post(feedback, "No hardware acceleration for " + arch
                            + " on this host (" + binary.accelerators() + "); the guest will be very"
                            + " slow. " + QemuBinary.nativeArchitecture() + " guests run natively.");
                }

                VmPaths.ensureDirectories();
                // Every computer gets its own disk so anything installed survives a power cycle.
                // It is created on first boot rather than at build time, so a computer that is only
                // ever used with live media costs no disk space.
                // No hard drive fitted, no disk attached. The bay is the only optional one, and
                // this is what makes it mean anything: a machine without one boots live media and
                // forgets everything when it stops, exactly as the bay's description promises.
                VmSpec.DiskImage disk = null;
                // Created on demand for the plugin's own images, never for an admin's.
                boolean createDisk = true;
                if (computer.installedIn(ComponentSlot.HARD_DRIVE) == null) {
                    post(feedback, "No hard drive fitted: nothing will survive a power cycle.");
                } else if (computer.diskImage() != null) {
                    // An image the admin installed elsewhere and copied in. The bay still has to be
                    // filled -- the drive is where the fiction says a disk lives, and letting an
                    // imported one bypass it would make the one optional bay meaningless.
                    java.nio.file.Path imported = VmPaths.resolveDisk(computer.diskImage());
                    String format = VmPaths.diskFormat(computer.diskImage());
                    if (imported == null || format == null) {
                        // Thrown rather than returned so it lands in the catch below, which is what
                        // sets ERROR and logs. Returning here would leave the machine sitting in
                        // BOOTING with nothing coming.
                        throw new IOException("disk image '" + computer.diskImage()
                                + "' is missing or not a format QEMU reads. Refusing to boot a"
                                + " blank disk in its place -- run /vmcomputers disk to see what is"
                                + " available.");
                    }
                    disk = new VmSpec.DiskImage(imported, format);
                    createDisk = false;
                } else {
                    disk = VmSpec.DiskImage.qcow2(VmPaths.diskFor(computer.id()));
                }
                java.nio.file.Path floppy = VmPaths.resolveFloppy(computer.floppyImage());
                if (computer.floppyImage() != null && floppy == null) {
                    post(feedback, "Floppy '" + computer.floppyImage()
                            + "' is missing; booting with an empty drive.");
                }
                java.nio.file.Path iso = VmPaths.resolveIso(computer.isoName());
                if (computer.isoName() != null && iso == null) {
                    post(feedback, "ISO '" + computer.isoName() + "' is missing; booting without it.");
                }

                // Memory and cores now come from the parts fitted in the case, so a player who
                // buys a 64 MB stick gets a machine that behaves like it has 64 MB. That is the
                // point of the components, and it means a modern desktop needs the 4 GB stick --
                // an Ubuntu live session unpacks itself into a RAM-backed overlay and does not
                // get anywhere on 2 GB.
                int memoryMb = memoryFor(computer);
                int cores = coresFor(computer);
                plugin.getLogger().info("Computer #" + computer.id() + " booting with "
                        + memoryMb + " MB and " + cores + " core(s).");

                // User-mode NAT reaches whatever this host reaches, LAN included, so whether a
                // guest gets a card at all is the admin's call rather than a fixed default.
                boolean networking = plugin.getConfig().getBoolean("guest.networking", true);
                QemuVirtualMachine machine = QemuVirtualMachine.forComputer(
                        computer.id(), binary, computer.monitorSize(),
                        disk, createDisk, iso, floppy, memoryMb, cores, networking, computer.profile(),
                        vgaFor(computer, feedback),
                        line -> plugin.getLogger().info(line));

                MapColorLut palette = plugin.getMapPalette();
                byte black = palette.match(0, 0, 0);
                // Only used when /vmcomputers debug is on, but the palette is right here and the
                // toggle is not, so set them now rather than plumbing the palette to the command.
                screen.setCursorColours(palette.match(255, 255, 255), black);
                machine.setFrameListener(new FramePump(screen, palette, black,
                        plugin.getAudioService().busFor(computer.id())));

                machine.start();
                ComputerFunctions.register(machine);
                computer.setState(Computer.State.RUNNING);

                post(feedback, "Computer #" + computer.id() + " powered on ("
                        + machine.width() + "x" + machine.height() + ").");
            } catch (IOException e) {
                computer.setState(Computer.State.ERROR);
                post(feedback, "Could not start: " + e.getMessage());
                plugin.getLogger().severe("VM " + computer.id() + " failed to start: " + e);
            } finally {
                // Cleared in a finally so a boot that throws does not leave the machine looking
                // like it is still coming up forever.
                TRANSITIONS.remove(Integer.valueOf(computer.id()));
            }
        });
    }

    /** Stops a computer and blanks its screen. */
    public static void stop(Computer computer, MonitorScreen screen, Consumer<String> feedback) {
        VMComputers plugin = VMComputers.getPlugin();
        TRANSITIONS.put(Integer.valueOf(computer.id()), Transition.STOPPING);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Blocks until the guest has really gone -- a graceful stop waits on ACPI.
                ComputerFunctions.stop(computer.id());
                // Ends any browser still listening, rather than leaving it on a stream that will
                // never produce another sample.
                plugin.getAudioService().release(computer.id());
                computer.setState(Computer.State.OFF);
                if (screen != null) {
                    screen.fill(plugin.getMapPalette().match(0, 0, 0));
                }
                post(feedback, "Computer #" + computer.id() + " powered off.");
            } finally {
                TRANSITIONS.remove(Integer.valueOf(computer.id()));
            }
        });
    }

    /**
     * Why this machine may not start right now, or null if it may.
     *
     * <p>A running computer is a whole QEMU process holding whatever its memory component says, so
     * without a ceiling a busy server is one popular build away from swapping itself to death.
     * There is no queue on purpose: a machine that boots ten minutes after the player asked, once
     * they have wandered off, is worse than a refusal that says why.
     *
     * <p>Counted from the machines actually registered rather than from stored state, so a guest
     * still shutting down still counts -- it has not given its memory back yet.
     */
    private static String overLimit(Computer computer) {
        VMComputers plugin = VMComputers.getPlugin();

        int maxRunning = plugin.getConfig().getInt("limits.max-running", 4);
        int running = ComputerFunctions.getMachines().size();
        if (maxRunning > 0 && running >= maxRunning) {
            return "The server is already running " + running + " machine"
                    + (running == 1 ? "" : "s") + ", which is the limit. Wait for one to shut down.";
        }

        int maxEach = plugin.getConfig().getInt("limits.max-per-player", 2);
        java.util.UUID owner = computer.owner();
        if (maxEach <= 0 || owner == null) {
            return null;
        }

        int mine = 0;
        for (Integer id : ComputerFunctions.getMachines().keySet()) {
            Computer other = plugin.getRegistry().byId(id.intValue());
            if (other != null && owner.equals(other.owner())) {
                mine++;
            }
        }
        if (mine >= maxEach) {
            return "You already have " + mine + " machine" + (mine == 1 ? "" : "s")
                    + " running, which is your limit. Shut one down first.";
        }
        return null;
    }

    /**
     * Guest memory from the fitted RAM stick.
     *
     * <p>Falls back to 2048 only if a machine somehow starts with no RAM; power-on requires the
     * bay to be filled, so in practice this always reads a real component.
     */
    /**
     * The adapter the fitted graphics card asks for, or null to leave the profile's choice.
     *
     * <p>Says so when it cannot be honoured. An ARM machine has exactly one adapter that works --
     * the UEFI firmware and every ARM guest expect virtio-gpu and nothing else -- so a Cirrus card
     * fitted there is a black screen rather than an old-looking one, and silently ignoring it would
     * leave a player staring at a card they bought and a screen that never lights up.
     */
    /**
     * The architecture this machine actually runs at, once the fitted motherboard has its say.
     *
     * <p>This is the 32-bit board's whole job. The machine's own architecture says which family it
     * is; the board says how wide it is, and a 32-bit board means {@code qemu-system-i386}. That is
     * a real distinction rather than a cosmetic one, because an i386 emulator genuinely cannot run
     * a 64-bit guest -- which is why fitting the cheap board and then trying to boot XP x64 has to
     * be refused rather than quietly upgraded.
     *
     * @throws IOException when the board and the guest cannot be reconciled
     */
    private static VmSpec.Architecture architectureFor(Computer computer, GuestProfile era)
            throws IOException {
        VmSpec.Architecture declared = computer.architecture();
        ComponentType board = computer.installedIn(ComponentSlot.MOTHERBOARD);
        if (board == null || board.rating() != 32 || !declared.isX86()) {
            return declared;
        }
        if (era.needs64Bit()) {
            throw new IOException(era.label() + " is a 64-bit guest and this machine has a 32-bit"
                    + " motherboard. Fit a 64-bit Motherboard, or choose a 32-bit profile.");
        }
        return VmSpec.Architecture.I386;
    }

    private static VmSpec.Vga vgaFor(Computer computer, Consumer<String> feedback) {
        ComponentType gpu = computer.installedIn(ComponentSlot.GPU);
        if (gpu == null || gpu.vga() == null) {
            return null;
        }
        if (computer.architecture() == VmSpec.Architecture.AARCH64
                && gpu.vga() != VmSpec.Vga.VIRTIO) {
            post(feedback, gpu.displayName() + " does not work on an ARM machine; using the "
                    + "virtio adapter instead. Fit a Virtio GPU to match.");
            return null;
        }
        return gpu.vga();
    }

    private static int memoryFor(Computer computer) {
        ComponentType ram = computer.installedIn(ComponentSlot.RAM);
        return ram != null && ram.rating() > 0 ? ram.rating() : 2048;
    }

    /**
     * Guest cores, as the host's core count divided by the CPU tier -- the mod's "divided by N"
     * naming, where a cheaper chip is a bigger divisor. Never fewer than one, and never more than
     * the host actually has.
     */
    private static int coresFor(Computer computer) {
        ComponentType cpu = computer.installedIn(ComponentSlot.CPU);
        int hostCores = Runtime.getRuntime().availableProcessors();
        int divisor = cpu != null && cpu.rating() > 0 ? cpu.rating() : 4;
        return Math.max(1, Math.min(hostCores, hostCores / divisor));
    }

    private static void post(Consumer<String> feedback, String message) {
        Bukkit.getScheduler().runTask(VMComputers.getPlugin(), () -> feedback.accept(message));
    }

    /**
     * Quantizes each frame and hands it to the screen.
     *
     * <p>Runs on the RFB pump thread. Because RFB is client-pull, taking longer here simply slows
     * the request rate rather than building a backlog, so the frame rate self-limits to whatever
     * this can sustain.
     */
    private static final class FramePump implements VirtualMachine.FrameListener {
        private final MonitorScreen screen;
        private final MapColorLut palette;
        private final byte border;
        private final AudioBus audio;
        private byte[] quantized = new byte[0];
        private int[] scaled = new int[0];

        FramePump(MonitorScreen screen, MapColorLut palette, byte border, AudioBus audio) {
            this.screen = screen;
            this.palette = palette;
            this.border = border;
            this.audio = audio;
        }

        @Override
        public void onAudio(byte[] pcm, int length) {
            // Returns immediately whatever listeners are doing; see AudioBus.
            audio.write(pcm, length);
        }

        @Override
        public void onFrame(int[] argb, int width, int height, List<RfbClient.Rect> damage) {
            // Guests ignore the EDID hint in text mode and come up at 720x400, which does not fit
            // the smaller grids. Fit it to the screen keeping aspect ratio; letterboxing handles
            // the remainder. Without this the image was silently cropped to the top-left corner.
            // Track the guest's own size every frame, not just on mode changes: the very first
            // frame arrives without a preceding resize event.
            screen.setGuestResolution(width, height);

            int[] fit = ImageScaler.fitDimensions(width, height,
                    screen.size().pixelWidth(), screen.size().pixelHeight());
            int targetWidth = fit[0];
            int targetHeight = fit[1];

            int[] source = argb;
            if (targetWidth != width || targetHeight != height) {
                int area = targetWidth * targetHeight;
                if (scaled.length < area) {
                    scaled = new int[area];
                }
                // Scale in RGB, before quantization: averaging palette indices is meaningless.
                ImageScaler.scale(argb, width, height, scaled, targetWidth, targetHeight);
                source = scaled;
                width = targetWidth;
                height = targetHeight;
            }

            int needed = width * height;
            if (quantized.length < needed) {
                quantized = new byte[needed];
            }
            // Ordered dithering: it depends only on (x, y), so identical input always produces
            // identical output. Error diffusion would make one changed pixel alter everything after
            // it, defeating the per-pixel comparison that keeps map traffic small.
            palette.quantizeDithered(source, width, height, quantized, DITHER_SPREAD);
            screen.setDisplayedSize(width, height);
            screen.present(quantized, width, height, border);
        }

        @Override
        public void onResize(int width, int height) {
            screen.setGuestResolution(width, height);
        }

        void noteGuestSize(int width, int height) {
            screen.setGuestResolution(width, height);
        }
    }
}
