package com.acclash.vmcomputers.emu;

import com.acclash.vmcomputers.display.MonitorSize;
import com.acclash.vmcomputers.rfb.RfbClient;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * A {@link VirtualMachine} backed by a QEMU process, controlled over QMP and read over RFB.
 *
 * <p>Ties the three pieces together and owns their lifetimes: the process, the control channel and
 * the display transport all start and stop as a unit.
 */
public final class QemuVirtualMachine implements VirtualMachine {

    private static final Duration QMP_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration VNC_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration GUEST_SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

    private final int computerId;
    private final QemuBinary qemu;
    private final VmSpec spec;
    private final Consumer<String> logger;

    private QemuProcess process;
    private RfbClient rfb;
    private volatile FrameListener frameListener;

    /**
     * Serialises input writes off the server thread. Single-threaded so events keep their order --
     * a key release must never overtake its press.
     */
    private final ExecutorService inputThread;

    public QemuVirtualMachine(int computerId, QemuBinary qemu, VmSpec spec, Consumer<String> logger) {
        this.computerId = computerId;
        this.qemu = qemu;
        this.spec = spec;
        this.logger = logger != null ? logger : message -> {
        };
        this.inputThread = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "vm-input-" + computerId);
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Builds a spec sized for a monitor and creates the VM.
     *
     * @param disk        the disk to attach, or null for a machine with no drive
     * @param createDisk  whether to create {@code disk} at the standard size if it does not exist.
     *                    True for the plugin's own images, false for one an admin supplied: an
     *                    image someone else made must never be created, resized or reformatted
     *                    here, and a missing one is a mistake to report rather than paper over
     *                    with a blank disk that silently loses whatever they meant to boot.
     */
    public static QemuVirtualMachine forComputer(int computerId, QemuBinary qemu, MonitorSize monitor,
                                                 VmSpec.DiskImage disk, boolean createDisk, Path iso,
                                                 Path floppy, int memoryMb, int cpus,
                                                 boolean networking, GuestProfile profile,
                                                 VmSpec.Vga vga, Consumer<String> logger)
            throws IOException {
        VmSpec.Builder builder = VmSpec.builder("vmcomputer-" + computerId)
                .architecture(qemu.architecture())
                .resolution(monitor.guestWidth(), monitor.guestHeight())
                .cpus(cpus)
                // Passed in rather than read here: this package stays free of Bukkit, and whether
                // guests get a network card is an admin's decision rather than an emulator one.
                .networking(networking);

        // The era the guest belongs to, which is what actually decides its hardware. AUTO is
        // resolved rather than applied -- it is a question, not an answer.
        GuestProfile era = (profile != null ? profile : GuestProfile.AUTO)
                .resolve(qemu.architecture(), qemu.hasHardwareAcceleration());
        era.applyTo(builder);
        // Applied after the profile, so a card a player deliberately bought beats the era default.
        // The plain graphics card passes null and keeps the era's choice.
        if (vga != null && qemu.architecture() != VmSpec.Architecture.AARCH64) {
            builder.vga(vga);
        }
        builder.memoryMb(era.clampMemory(memoryMb));
        if (era.wantsSharedFolder()) {
            // No network drivers, no clipboard: a folder on a fake disk is how anything gets in or
            // out of a guest this old.
            builder.sharedFolder(VmPaths.sharedDirectory());
        }

        if (qemu.architecture() == VmSpec.Architecture.AARCH64) {
            // ARM guests boot UEFI rather than a BIOS, and need their own writable variable store.
            builder.uefiVars(VmPaths.ensureUefiVars(computerId,
                    qemu.firmware("edk2-arm-vars.fd")));
        }

        if (disk != null) {
            if (createDisk) {
                qemu.createDisk(disk.path(), 16L * 1024 * 1024 * 1024);
            }
            builder.addDisk(disk);
        }
        if (iso != null) {
            builder.cdrom(iso);
        }
        if (floppy != null && qemu.architecture() != VmSpec.Architecture.AARCH64) {
            builder.floppy(floppy);
        }
        // A floppy in the drive is a deliberate act, so it goes to the front of the boot order --
        // otherwise inserting a Windows 95 boot disk into a machine with a hard disk would do
        // nothing visible. Without one the order is unchanged: disk, then disc.
        builder.bootOrder(floppy != null ? "adc" : "dc");
        return new QemuVirtualMachine(computerId, qemu, builder.build(), logger);
    }

    @Override
    public int computerId() {
        return computerId;
    }

    @Override
    public void start() throws IOException {
        if (process != null) {
            throw new IllegalStateException("VM " + computerId + " is already started");
        }

        logger.accept("Starting VM " + computerId + ": " + spec);
        if (!qemu.hasHardwareAcceleration()) {
            logger.accept("WARNING: QEMU has no hardware acceleration on this host (accelerators: "
                    + qemu.accelerators() + "). Guests will be extremely slow.");
        }

        process = QemuProcess.start(qemu, spec, line -> logger.accept("[vm " + computerId + "] " + line));
        try {
            process.connectQmp(QMP_TIMEOUT, (event, data) ->
                    logger.accept("[vm " + computerId + "] event " + event));

            rfb = RfbClient.connect("127.0.0.1", process.vncPort(), VNC_TIMEOUT);
            rfb.setListener(new RfbClient.Listener() {
                @Override
                public void onFramebuffer(int[] argb, int w, int h, List<RfbClient.Rect> damage) {
                    FrameListener l = frameListener;
                    if (l != null) {
                        l.onFrame(argb, w, h, damage);
                    }
                }

                @Override
                public void onResize(int w, int h) {
                    logger.accept("[vm " + computerId + "] guest mode changed to " + w + "x" + h);
                    FrameListener l = frameListener;
                    if (l != null) {
                        l.onResize(w, h);
                    }
                }

                @Override
                public void onAudio(byte[] pcm, int length) {
                    FrameListener l = frameListener;
                    if (l != null) {
                        l.onAudio(pcm, length);
                    }
                }

                @Override
                public void onDisconnect(IOException cause) {
                    logger.accept("[vm " + computerId + "] display disconnected: " + cause);
                }
            });
            rfb.start();
            logger.accept("VM " + computerId + " running at " + rfb.width() + "x" + rfb.height());
        } catch (IOException e) {
            // Never leave a stray QEMU process behind when startup fails partway.
            shutdown();
            throw e;
        }
    }

    @Override
    public void setAudioEnabled(boolean enabled, int sampleRate, int channels) throws IOException {
        RfbClient client = rfb;
        if (client == null) {
            return;
        }
        if (enabled) {
            client.enableAudio(sampleRate, channels);
        } else {
            client.disableAudio();
        }
    }

    @Override
    public boolean isRunning() {
        return process != null && process.isAlive();
    }

    @Override
    public int width() {
        return rfb != null ? rfb.width() : spec.width();
    }

    @Override
    public int height() {
        return rfb != null ? rfb.height() : spec.height();
    }

    @Override
    public void setFrameListener(FrameListener listener) {
        this.frameListener = listener;
    }

    @Override
    public void sendKey(int keysym, boolean pressed) {
        dispatch(client -> client.sendKey(keysym, pressed));
    }

    @Override
    public void sendPointer(int x, int y, int buttonMask) {
        dispatch(client -> client.sendPointer(x, y, buttonMask));
    }

    @Override
    public void sendScroll(int x, int y, boolean up) {
        dispatch(client -> client.sendScroll(x, y, up));
    }

    /** Queues an input write; failures are logged rather than surfaced to the game thread. */
    private void dispatch(InputAction action) {
        RfbClient client = rfb;
        if (client == null) {
            return;
        }
        try {
            inputThread.execute(() -> {
                try {
                    action.perform(client);
                } catch (IOException e) {
                    logger.accept("[vm " + computerId + "] input send failed: " + e);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // The machine is shutting down.
        }
    }

    private interface InputAction {
        void perform(RfbClient client) throws IOException;
    }

    @Override
    public void insertCdrom(Path iso) throws IOException {
        QmpClient qmp = process != null ? process.qmp() : null;
        if (qmp == null) {
            throw new IOException("VM " + computerId + " is not running");
        }
        qmp.changeCdrom(iso.toAbsolutePath().toString());
    }

    private RfbClient requireDisplay() throws IOException {
        RfbClient client = rfb;
        if (client == null) {
            throw new IOException("VM " + computerId + " has no display connection");
        }
        return client;
    }

    @Override
    public void shutdown() {
        inputThread.shutdownNow();
        if (rfb != null) {
            rfb.close();
            rfb = null;
        }
        if (process != null) {
            process.shutdownGracefully(GUEST_SHUTDOWN_TIMEOUT);
            process = null;
        }
    }

    @Override
    public void kill() {
        inputThread.shutdownNow();
        if (rfb != null) {
            rfb.close();
            rfb = null;
        }
        if (process != null) {
            process.kill();
            process = null;
        }
    }

    @Override
    public void close() {
        shutdown();
    }
}
