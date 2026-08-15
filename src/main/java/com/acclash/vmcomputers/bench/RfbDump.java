package com.acclash.vmcomputers.bench;

import com.acclash.vmcomputers.emu.QemuBinary;
import com.acclash.vmcomputers.emu.QemuProcess;
import com.acclash.vmcomputers.emu.QmpClient;
import com.acclash.vmcomputers.emu.VmSpec;
import com.acclash.vmcomputers.rfb.RfbClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Standalone smoke test for the emulator and transport layers: boots a VM, pulls frames over RFB,
 * writes them out as PNGs and reports timing.
 *
 * <p>Deliberately has no Bukkit or NMS dependency, so it runs from a plain JVM with no Minecraft
 * server involved. With no disk and no ISO it still does something useful -- QEMU lands on the
 * SeaBIOS "no bootable device" screen, which is enough to prove the whole path end to end.
 *
 * <pre>
 *   java -cp out com.acclash.vmcomputers.bench.RfbDump --frames 20 --out /tmp/vmshots
 *   java -cp out com.acclash.vmcomputers.bench.RfbDump --iso ~/Downloads/alpine.iso --seconds 30
 * </pre>
 */
public final class RfbDump {

    private RfbDump() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        Map<String, String> opts = parseArgs(args);

        if (opts.containsKey("help")) {
            printUsage();
            return;
        }

        int width = intOpt(opts, "width", 640);
        int height = intOpt(opts, "height", 480);
        int maxFrames = intOpt(opts, "frames", 20);
        int maxSeconds = intOpt(opts, "seconds", 60);
        int memoryMb = intOpt(opts, "mem", 2048);
        int cpus = intOpt(opts, "cpus", 2);
        // Left null unless asked for, so the architecture picks its own default machine.
        String machine = opts.get("machine");
        Path outDir = Paths.get(opts.getOrDefault("out", "vmshots"));

        VmSpec.Architecture architecture = opts.containsKey("arch")
                ? VmSpec.Architecture.valueOf(opts.get("arch").toUpperCase(Locale.ROOT))
                : QemuBinary.nativeArchitecture();

        log("Locating QEMU for " + architecture + "...");
        QemuBinary qemu = QemuBinary.discover(architecture);
        log("  binary       " + qemu.systemBinary());
        log("  " + qemu.version());
        log("  accelerators " + qemu.accelerators());
        log("  chosen       " + qemu.bestAccelerator());
        if (!qemu.hasHardwareAcceleration()) {
            log("  WARNING: no hardware acceleration available, falling back to TCG.");
            log("           Expect the guest to be roughly two orders of magnitude slower.");
        }

        VmSpec.Builder builder = VmSpec.builder("rfbdump")
                .architecture(architecture)
                .resolution(width, height)
                .memoryMb(memoryMb)
                .cpus(cpus);
        if (machine != null) {
            builder.machine(machine);
        }

        if (opts.containsKey("vga")) {
            builder.vga(VmSpec.Vga.valueOf(opts.get("vga").toUpperCase(Locale.ROOT)));
        }
        if (opts.containsKey("disk")) {
            Path disk = Paths.get(opts.get("disk"));
            qemu.createDisk(disk, 8L * 1024 * 1024 * 1024);
            builder.addDisk(disk);
        }
        if (opts.containsKey("iso")) {
            Path iso = Paths.get(opts.get("iso"));
            if (!Files.isReadable(iso)) {
                throw new IOException("cannot read ISO: " + iso);
            }
            builder.cdrom(iso).bootOrder("dc");
        }

        if (architecture == VmSpec.Architecture.AARCH64) {
            java.nio.file.Path template = qemu.firmware("edk2-arm-vars.fd");
            java.nio.file.Path vars = Paths.get("rfbdump-vars.fd");
            if (template != null && !Files.exists(vars)) {
                Files.copy(template, vars);
            }
            builder.uefiVars(Files.exists(vars) ? vars : null);
            log("  UEFI code   " + qemu.firmware("edk2-aarch64-code.fd"));
        }

        VmSpec spec = builder.build();
        log("");
        log("Spec: " + spec);

        Files.createDirectories(outDir);

        QemuProcess vm = QemuProcess.start(qemu, spec, line -> log("  [qemu] " + line));
        Runtime.getRuntime().addShutdownHook(new Thread(vm::kill));
        log("Started QEMU: qmp=" + vm.qmpPort() + " vnc=" + vm.vncPort());

        RfbClient rfb = null;
        try {
            QmpClient qmp = vm.connectQmp(Duration.ofSeconds(15),
                    QemuProcess.loggingEventListener(RfbDump::log));
            log("QMP connected, guest status: " + qmp.queryStatus());

            rfb = RfbClient.connect("127.0.0.1", vm.vncPort(), Duration.ofSeconds(15));
            log("RFB connected: " + rfb.width() + "x" + rfb.height()
                    + " \"" + rfb.desktopName() + "\"");
            log("");
            log(String.format("%-6s %8s %7s %10s %8s", "frame", "dt(ms)", "rects", "changed", "%"));

            final RfbClient client = rfb;
            final CountDownLatch done = new CountDownLatch(1);
            final AtomicInteger frames = new AtomicInteger();
            final long[] lastNanos = {System.nanoTime()};
            final long[] totalChanged = {0};
            final long startNanos = System.nanoTime();

            rfb.setListener(new RfbClient.Listener() {
                @Override
                public void onFramebuffer(int[] argb, int w, int h, List<RfbClient.Rect> damage) {
                    long now = System.nanoTime();
                    double dtMs = (now - lastNanos[0]) / 1e6;
                    lastNanos[0] = now;

                    long changed = 0;
                    for (RfbClient.Rect r : damage) {
                        changed += r.area();
                    }
                    totalChanged[0] += changed;

                    int index = frames.incrementAndGet();
                    log(String.format("%-6d %8.1f %7d %10d %7.1f%%",
                            index, dtMs, damage.size(), changed, 100.0 * changed / (w * h)));

                    try {
                        writePng(argb, w, h, outDir.resolve(String.format("frame-%04d.png", index)));
                    } catch (IOException e) {
                        log("  failed to write PNG: " + e);
                    }

                    if (index >= maxFrames) {
                        done.countDown();
                    }
                }

                @Override
                public void onResize(int w, int h) {
                    log("  guest changed mode to " + w + "x" + h);
                }

                @Override
                public void onDisconnect(IOException cause) {
                    log("  RFB disconnected: " + cause);
                    done.countDown();
                }
            });

            rfb.start();

            boolean reachedTarget = done.await(maxSeconds, TimeUnit.SECONDS);
            double elapsed = (System.nanoTime() - startNanos) / 1e9;
            int captured = frames.get();

            log("");
            log("---- summary ----");
            log(String.format("frames        %d%s", captured, reachedTarget ? "" : " (timed out)"));
            log(String.format("elapsed       %.1fs", elapsed));
            if (captured > 0 && elapsed > 0) {
                log(String.format("average fps   %.1f", captured / elapsed));
                log(String.format("avg changed   %.1f%% of screen per frame",
                        100.0 * totalChanged[0] / ((double) captured * client.width() * client.height())));
            }
            log("PNGs written to " + outDir.toAbsolutePath());
            log("");
            log("Note: fps here is bounded by QEMU's own damage polling (~30 Hz) and by how much");
            log("      the guest is actually changing, not by the transport.");
        } finally {
            if (rfb != null) {
                rfb.close();
            }
            log("Shutting down the VM...");
            vm.shutdownGracefully(Duration.ofSeconds(5));
            Integer code = vm.exitCode();
            log("QEMU exited with " + (code == null ? "(still running)" : code.toString()));
        }
    }

    private static void writePng(int[] argb, int width, int height, Path file) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, width, height, argb, 0, width);
        ImageIO.write(image, "png", file.toFile());
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> opts = new HashMap<String, String>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (!a.startsWith("--")) {
                throw new IllegalArgumentException("unexpected argument: " + a);
            }
            String key = a.substring(2);
            if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                opts.put(key, args[++i]);
            } else {
                opts.put(key, "true");
            }
        }
        return opts;
    }

    private static int intOpt(Map<String, String> opts, String key, int fallback) {
        String v = opts.get(key);
        return v == null ? fallback : Integer.parseInt(v);
    }

    private static void log(String message) {
        System.out.println(message);
    }

    private static void printUsage() {
        log("Usage: RfbDump [options]");
        log("  --iso <path>      boot from this ISO");
        log("  --disk <path>     qcow2 disk, created at 8G if missing");
        log("  --frames <n>      stop after n frames (default 20)");
        log("  --seconds <n>     stop after n seconds regardless (default 60)");
        log("  --out <dir>       PNG output directory (default ./vmshots)");
        log("  --width <px>      guest width (default 640)");
        log("  --height <px>     guest height (default 480)");
        log("  --mem <mb>        guest memory (default 2048)");
        log("  --cpus <n>        guest vCPUs (default 2)");
        log("  --machine <name>  q35 (x86) or virt (arm)");
        log("  --arch <name>     X86_64 or AARCH64 (defaults to the host CPU)");
        log("  --vga <name>      STD (default), VIRTIO, CIRRUS, VMWARE");
        log("");
        log("With no --iso or --disk the guest lands on the SeaBIOS boot-failure screen,");
        log("which is enough to verify the whole path.");
    }
}
