package com.acclash.vmcomputers.emu;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/**
 * QEMU Machine Protocol client: line-delimited JSON over a loopback socket.
 *
 * <p>This is the control channel -- start, stop, media swap, hotplug, snapshots, status. It is
 * deliberately separate from the framebuffer path, so display code never has to care about VM
 * lifecycle and vice versa.
 */
public final class QmpClient implements Closeable {

    /** Asynchronous notifications from QEMU, e.g. {@code SHUTDOWN}, {@code RESET}, {@code STOP}. */
    public interface EventListener {
        void onEvent(String event, Map<String, Object> data);
    }

    /** Thrown when QEMU returns an {@code error} object rather than a {@code return}. */
    public static final class QmpException extends IOException {
        private static final long serialVersionUID = 1L;
        private final String errorClass;

        QmpException(String errorClass, String description) {
            super(errorClass + ": " + description);
            this.errorClass = errorClass;
        }

        public String errorClass() {
            return errorClass;
        }
    }

    private static final Duration DEFAULT_COMMAND_TIMEOUT = Duration.ofSeconds(10);

    private final Socket socket;
    private final BufferedReader in;
    private final Writer out;
    private final Map<Long, CompletableFuture<Map<String, Object>>> pending =
            new ConcurrentHashMap<Long, CompletableFuture<Map<String, Object>>>();
    private final AtomicLong nextId = new AtomicLong(1);
    private final EventListener listener;
    private final Thread readerThread;

    private volatile boolean closed;
    private volatile String greeting = "";

    private QmpClient(Socket socket, EventListener listener) throws IOException {
        this.socket = socket;
        this.listener = listener;
        this.in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
        this.readerThread = new Thread(this::readLoop, "qmp-reader");
        this.readerThread.setDaemon(true);
    }

    /**
     * Connects, performs the capabilities handshake, and returns a ready client.
     *
     * <p>QEMU needs a moment to open the port after exec, so connection is retried until
     * {@code timeout}. If {@code stillAlive} reports the process has died we give up immediately
     * rather than waiting out the whole timeout -- a dead process is never going to accept.
     *
     * @param stillAlive liveness probe, or {@code null} to always keep retrying
     */
    public static QmpClient connect(String host, int port, Duration timeout,
                                    BooleanSupplier stillAlive, EventListener listener)
            throws IOException {
        long deadline = System.nanoTime() + timeout.toNanos();
        IOException last = null;

        while (System.nanoTime() < deadline) {
            if (stillAlive != null && !stillAlive.getAsBoolean()) {
                throw new IOException("QEMU exited before the QMP port became available", last);
            }
            Socket s = new Socket();
            try {
                s.connect(new InetSocketAddress(host, port), 1000);
                s.setTcpNoDelay(true);
                QmpClient client = new QmpClient(s, listener);
                client.handshake();
                return client;
            } catch (IOException e) {
                last = e;
                closeQuietly(s);
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted while connecting to QMP", ie);
                }
            }
        }
        throw new IOException("timed out after " + timeout.toMillis()
                + "ms connecting to QMP at " + host + ":" + port, last);
    }

    private void handshake() throws IOException {
        // QEMU speaks first with a greeting describing its version and capabilities.
        String line = in.readLine();
        if (line == null) {
            throw new IOException("QMP connection closed before the greeting arrived");
        }
        Map<String, Object> msg = Json.asObject(Json.parse(line));
        if (Json.getObject(msg, "QMP") == null) {
            throw new IOException("unexpected first QMP message: " + line);
        }
        this.greeting = line;

        // Until capabilities are negotiated QEMU rejects every other command.
        readerThread.start();
        execute("qmp_capabilities");
    }

    /** The raw greeting message, useful for logging which QEMU we actually got. */
    public String greeting() {
        return greeting;
    }

    public boolean isOpen() {
        return !closed && !socket.isClosed();
    }

    // ---- commands --------------------------------------------------------

    public Map<String, Object> execute(String command) throws IOException {
        return execute(command, null, DEFAULT_COMMAND_TIMEOUT);
    }

    public Map<String, Object> execute(String command, Map<String, Object> arguments)
            throws IOException {
        return execute(command, arguments, DEFAULT_COMMAND_TIMEOUT);
    }

    /**
     * Sends a command and blocks for its reply.
     *
     * @return the {@code return} value as an object, or an empty map if QEMU returned a non-object
     */
    public Map<String, Object> execute(String command, Map<String, Object> arguments,
                                       Duration timeout) throws IOException {
        if (closed) {
            throw new IOException("QMP client is closed");
        }
        long id = nextId.getAndIncrement();

        Map<String, Object> request = Json.map("execute", command, "id", Long.valueOf(id));
        if (arguments != null && !arguments.isEmpty()) {
            request.put("arguments", arguments);
        }

        CompletableFuture<Map<String, Object>> future =
                new CompletableFuture<Map<String, Object>>();
        pending.put(Long.valueOf(id), future);
        try {
            synchronized (out) {
                out.write(Json.write(request));
                out.write("\r\n");
                out.flush();
            }
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new IOException("QMP command '" + command + "' timed out after "
                    + timeout.toMillis() + "ms", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted waiting for QMP command '" + command + "'", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new IOException("QMP command '" + command + "' failed", cause);
        } finally {
            pending.remove(Long.valueOf(id));
        }
    }

    /** Current run state, e.g. {@code running}, {@code paused}, {@code shutdown}. */
    public String queryStatus() throws IOException {
        return Json.getString(execute("query-status"), "status", "unknown");
    }

    /** Requests an ACPI power button press. The guest may ignore this. */
    public void systemPowerdown() throws IOException {
        execute("system_powerdown");
    }

    public void systemReset() throws IOException {
        execute("system_reset");
    }

    public void cont() throws IOException {
        execute("cont");
    }

    public void stop() throws IOException {
        execute("stop");
    }

    /**
     * Swaps the CD medium. Matches the {@code id=cd0} drive that {@link VmSpec} emits, which is why
     * the spec uses an explicit {@code -drive} rather than {@code -cdrom}.
     */
    public void changeCdrom(String isoPath) throws IOException {
        execute("blockdev-change-medium",
                Json.map("id", "cd0", "filename", isoPath, "format", "raw"));
    }

    public void ejectCdrom() throws IOException {
        execute("eject", Json.map("id", "cd0", "force", Boolean.TRUE));
    }

    /**
     * Asks QEMU to exit. The socket usually drops before the reply arrives, which is expected and
     * not an error.
     */
    public void quit() {
        try {
            execute("quit", null, Duration.ofSeconds(2));
        } catch (IOException ignored) {
            // Losing the connection is the normal outcome here.
        }
    }

    // ---- reader ----------------------------------------------------------

    private void readLoop() {
        try {
            String line;
            while (!closed && (line = in.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                dispatch(line);
            }
            failPending(new IOException("QMP connection closed"));
        } catch (IOException e) {
            if (!closed) {
                failPending(e);
            }
        } catch (RuntimeException e) {
            failPending(new IOException("malformed QMP message", e));
        }
    }

    private void dispatch(String line) {
        Map<String, Object> msg;
        try {
            msg = Json.asObject(Json.parse(line));
        } catch (RuntimeException e) {
            // A message we cannot parse must not take down the reader thread.
            return;
        }
        if (msg == null) {
            return;
        }

        String event = Json.getString(msg, "event", null);
        if (event != null) {
            if (listener != null) {
                Map<String, Object> data = Json.getObject(msg, "data");
                try {
                    listener.onEvent(event, data);
                } catch (RuntimeException ignored) {
                    // A misbehaving listener must not kill the reader.
                }
            }
            return;
        }

        Object rawId = msg.get("id");
        if (!(rawId instanceof Number)) {
            // The greeting, or an out-of-band message we did not ask for.
            return;
        }
        Long id = Long.valueOf(((Number) rawId).longValue());
        CompletableFuture<Map<String, Object>> future = pending.get(id);
        if (future == null) {
            return;
        }

        Map<String, Object> error = Json.getObject(msg, "error");
        if (error != null) {
            future.completeExceptionally(new QmpException(
                    Json.getString(error, "class", "GenericError"),
                    Json.getString(error, "desc", "(no description)")));
            return;
        }

        Object ret = msg.get("return");
        Map<String, Object> result;
        if (ret instanceof Map) {
            result = Json.asObject(ret);
        } else {
            // Many commands return {} or a bare value; callers of those ignore the result.
            result = Json.map();
            if (ret != null) {
                result.put("return", ret);
            }
        }
        future.complete(result);
    }

    private void failPending(IOException cause) {
        for (Map.Entry<Long, CompletableFuture<Map<String, Object>>> e : pending.entrySet()) {
            e.getValue().completeExceptionally(cause);
        }
        pending.clear();
    }

    @Override
    public void close() {
        closed = true;
        failPending(new IOException("QMP client closed"));
        closeQuietly(socket);
    }

    private static void closeQuietly(Socket s) {
        try {
            s.close();
        } catch (IOException ignored) {
            // Nothing useful to do.
        }
    }
}
