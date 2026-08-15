package com.acclash.vmcomputers.emu;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * One running QEMU process, plus its QMP control channel.
 *
 * <p>Everything QEMU writes to stdout/stderr is drained into a bounded ring buffer. That matters
 * more than it looks: when a VM fails to start, QEMU's stderr line is the only useful diagnostic
 * there is, and a process whose output nobody reads will also block once the pipe buffer fills.
 */
public final class QemuProcess implements Closeable {

    private static final int OUTPUT_HISTORY_LINES = 200;

    private final VmSpec spec;
    private final Process process;
    private final int qmpPort;
    private final int vncDisplay;
    private final List<String> argv;
    private final Deque<String> output = new ArrayDeque<String>();
    private final Thread drainThread;

    private volatile QmpClient qmp;
    private volatile boolean closing;

    private QemuProcess(VmSpec spec, Process process, int qmpPort, int vncDisplay,
                        List<String> argv, Consumer<String> outputConsumer) {
        this.spec = spec;
        this.process = process;
        this.qmpPort = qmpPort;
        this.vncDisplay = vncDisplay;
        this.argv = argv;

        this.drainThread = new Thread(() -> drain(outputConsumer), "qemu-output-" + spec.name());
        this.drainThread.setDaemon(true);
        this.drainThread.start();
    }

    /**
     * Allocates ports, launches QEMU and returns immediately. The process is running but the guest
     * has not booted; call {@link #connectQmp} to wait for the control channel.
     *
     * @param outputConsumer receives each line QEMU writes, or {@code null} to only buffer it
     */
    public static QemuProcess start(QemuBinary qemu, VmSpec spec, Consumer<String> outputConsumer)
            throws IOException {
        int qmpPort = allocatePort();
        int vncDisplay = allocateVncDisplay();

        List<String> argv = spec.toArgv(qemu, qmpPort, vncDisplay);

        ProcessBuilder pb = new ProcessBuilder(argv);
        pb.redirectErrorStream(true);
        Process p = pb.start();

        return new QemuProcess(spec, p, qmpPort, vncDisplay, argv, outputConsumer);
    }

    public VmSpec spec() {
        return spec;
    }

    public int qmpPort() {
        return qmpPort;
    }

    public int vncDisplay() {
        return vncDisplay;
    }

    /** VNC is served on 5900 plus the display number. */
    public int vncPort() {
        return 5900 + vncDisplay;
    }

    public boolean isAlive() {
        return process.isAlive();
    }

    /** Exit code, or {@code null} while still running. */
    public Integer exitCode() {
        return process.isAlive() ? null : Integer.valueOf(process.exitValue());
    }

    /** The exact command line used, for logging and bug reports. */
    public List<String> commandLine() {
        return argv;
    }

    /** Most recent lines QEMU printed, oldest first. */
    public List<String> recentOutput() {
        synchronized (output) {
            return new ArrayList<String>(output);
        }
    }

    /**
     * Connects the QMP channel, retrying until QEMU opens the port.
     *
     * <p>If QEMU dies during startup this fails fast and includes its output in the message, which
     * is almost always the actual explanation (bad flag, missing file, unavailable accelerator).
     */
    public QmpClient connectQmp(Duration timeout, QmpClient.EventListener listener)
            throws IOException {
        QmpClient existing = qmp;
        if (existing != null && existing.isOpen()) {
            return existing;
        }
        try {
            QmpClient client = QmpClient.connect("127.0.0.1", qmpPort, timeout, this::isAlive, listener);
            this.qmp = client;
            return client;
        } catch (IOException e) {
            throw new IOException(describeFailure("could not establish QMP control channel"), e);
        }
    }

    /** The connected QMP client, or {@code null} if {@link #connectQmp} has not succeeded. */
    public QmpClient qmp() {
        return qmp;
    }

    private String describeFailure(String what) {
        StringBuilder sb = new StringBuilder(what);
        Integer code = exitCode();
        if (code != null) {
            sb.append(" (QEMU exited with code ").append(code).append(")");
        }
        List<String> lines = recentOutput();
        if (!lines.isEmpty()) {
            sb.append("\nQEMU output:\n");
            for (String line : lines) {
                sb.append("  ").append(line).append('\n');
            }
        }
        sb.append("\nCommand line:\n  ").append(String.join(" ", argv));
        return sb.toString();
    }

    /**
     * Attempts an orderly shutdown: ACPI power button, then a QMP quit, then SIGKILL. A guest that
     * ignores the power button (a BIOS prompt, an installer) is normal, hence the escalation.
     */
    public void shutdownGracefully(Duration guestTimeout) {
        closing = true;
        QmpClient client = qmp;

        if (client != null && client.isOpen()) {
            try {
                client.systemPowerdown();
                if (waitFor(guestTimeout)) {
                    client.close();
                    return;
                }
            } catch (IOException ignored) {
                // Fall through to the harder options.
            }
            client.quit();
            if (waitFor(Duration.ofSeconds(3))) {
                client.close();
                return;
            }
            client.close();
        }

        process.destroy();
        if (!waitFor(Duration.ofSeconds(3))) {
            process.destroyForcibly();
            waitFor(Duration.ofSeconds(3));
        }
    }

    private boolean waitFor(Duration timeout) {
        try {
            return process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return !process.isAlive();
        }
    }

    /** Immediate, unclean termination. */
    public void kill() {
        closing = true;
        QmpClient client = qmp;
        if (client != null) {
            client.close();
        }
        process.destroyForcibly();
    }

    @Override
    public void close() {
        shutdownGracefully(Duration.ofSeconds(10));
    }

    private void drain(Consumer<String> consumer) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                synchronized (output) {
                    if (output.size() >= OUTPUT_HISTORY_LINES) {
                        output.removeFirst();
                    }
                    output.addLast(line);
                }
                if (consumer != null) {
                    try {
                        consumer.accept(line);
                    } catch (RuntimeException ignored) {
                        // Never let a logging callback break the drain loop.
                    }
                }
            }
        } catch (IOException e) {
            if (!closing) {
                synchronized (output) {
                    output.addLast("[output drain failed: " + e + "]");
                }
            }
        }
    }

    // ---- port allocation -------------------------------------------------

    /**
     * Picks a free loopback port by binding and releasing it.
     *
     * <p>Technically racy -- something else could take the port before QEMU binds it -- but there
     * is no way to hand a bound socket to a child process, and this is the same approach every
     * other supervisor uses. Startup failure is detected and reported rather than hanging.
     */
    static int allocatePort() throws IOException {
        ServerSocket s = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        try {
            return s.getLocalPort();
        } finally {
            s.close();
        }
    }

    /** Finds a VNC display number whose port (5900 + n) is free. */
    static int allocateVncDisplay() throws IOException {
        for (int display = 0; display < 100; display++) {
            ServerSocket s = null;
            try {
                s = new ServerSocket(5900 + display, 1, InetAddress.getLoopbackAddress());
                return display;
            } catch (IOException notFree) {
                // Try the next one.
            } finally {
                if (s != null) {
                    try {
                        s.close();
                    } catch (IOException ignored) {
                        // Nothing useful to do.
                    }
                }
            }
        }
        throw new IOException("no free VNC display in the range 5900-5999");
    }

    /** Convenience for logging QMP events during development. */
    public static QmpClient.EventListener loggingEventListener(Consumer<String> sink) {
        return new QmpClient.EventListener() {
            @Override
            public void onEvent(String event, Map<String, Object> data) {
                sink.accept("QMP event: " + event + (data != null ? " " + Json.write(data) : ""));
            }
        };
    }
}
