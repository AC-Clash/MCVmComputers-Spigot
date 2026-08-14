package com.acclash.vmcomputers.emu;

import com.acclash.vmcomputers.VMComputers;
import com.acclash.vmcomputers.computer.Computer;
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

    private static volatile QemuBinary qemu;

    private VmService() {
    }

    /**
     * Locates QEMU once and caches it.
     *
     * @throws IOException if QEMU is not installed, with an actionable message
     */
    public static QemuBinary qemu() throws IOException {
        QemuBinary cached = qemu;
        if (cached == null) {
            synchronized (VmService.class) {
                if (qemu == null) {
                    qemu = QemuBinary.discover();
                }
                cached = qemu;
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
     * Boots a computer and connects its screen. Returns immediately; {@code feedback} is invoked on
     * the main thread as the boot progresses.
     */
    public static void start(Computer computer, MonitorScreen screen, Consumer<String> feedback) {
        if (isRunning(computer.id())) {
            feedback.accept("That computer is already running.");
            return;
        }

        VMComputers plugin = VMComputers.getPlugin();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                QemuBinary binary = qemu();
                if (!binary.hasHardwareAcceleration()) {
                    post(feedback, "No hardware acceleration on this host (" + binary.accelerators()
                            + "); the guest will be very slow.");
                }

                // No disk and no ISO yet: the guest lands on the firmware boot screen, which is
                // enough to prove the whole path from QEMU to the map wall.
                QemuVirtualMachine machine = QemuVirtualMachine.forComputer(
                        computer.id(), binary, computer.monitorSize(),
                        null, null, 2048, line -> plugin.getLogger().info(line));

                MapColorLut palette = plugin.getMapPalette();
                byte black = palette.match(0, 0, 0);
                machine.setFrameListener(new FramePump(screen, palette, black));

                machine.start();
                ComputerFunctions.register(machine);
                computer.setState(Computer.State.RUNNING);

                post(feedback, "Computer #" + computer.id() + " powered on ("
                        + machine.width() + "x" + machine.height() + ").");
            } catch (IOException e) {
                computer.setState(Computer.State.ERROR);
                post(feedback, "Could not start: " + e.getMessage());
                plugin.getLogger().severe("VM " + computer.id() + " failed to start: " + e);
            }
        });
    }

    /** Stops a computer and blanks its screen. */
    public static void stop(Computer computer, MonitorScreen screen, Consumer<String> feedback) {
        VMComputers plugin = VMComputers.getPlugin();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            ComputerFunctions.stop(computer.id());
            computer.setState(Computer.State.OFF);
            if (screen != null) {
                screen.fill(plugin.getMapPalette().match(0, 0, 0));
            }
            post(feedback, "Computer #" + computer.id() + " powered off.");
        });
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
        private byte[] quantized = new byte[0];

        FramePump(MonitorScreen screen, MapColorLut palette, byte border) {
            this.screen = screen;
            this.palette = palette;
            this.border = border;
        }

        @Override
        public void onFrame(int[] argb, int width, int height, List<RfbClient.Rect> damage) {
            int needed = width * height;
            if (quantized.length < needed) {
                quantized = new byte[needed];
            }
            // Ordered dithering: it depends only on (x, y), so identical input always produces
            // identical output. Error diffusion would make one changed pixel alter everything after
            // it, defeating the per-pixel comparison that keeps map traffic small.
            palette.quantizeDithered(argb, width, height, quantized, DITHER_SPREAD);
            screen.present(quantized, width, height, border);
        }

        @Override
        public void onResize(int width, int height) {
            screen.setGuestResolution(width, height);
        }
    }
}
