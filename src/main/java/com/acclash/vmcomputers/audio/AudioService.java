package com.acclash.vmcomputers.audio;

import com.acclash.vmcomputers.VMComputers;
import com.acclash.vmcomputers.emu.VirtualMachine;
import com.acclash.vmcomputers.utils.ComputerFunctions;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * Serves guest audio to browsers, because Minecraft will not carry it.
 *
 * <p>The sound packet takes a name and a pitch, not samples, so there is no way to put a guest's
 * audio into the game itself. A player who wants to hear their machine opens a link instead, and
 * gets raw PCM over a chunked HTTP response that the page feeds straight into Web Audio.
 *
 * <p>Chunked HTTP rather than a WebSocket on purpose. The browser can read a response body
 * incrementally through {@code fetch} and a {@code ReadableStream}, which gives the same latency
 * without hand-rolling RFC 6455 framing, and {@link HttpServer} is already in the JDK -- no
 * dependency, and far less to get wrong.
 *
 * <p>Nothing runs until it is wanted: the guest's audio is only switched on when the first listener
 * for that machine connects, and switched off again when the last one leaves.
 */
public final class AudioService {

    private static final int BYTES_PER_SAMPLE = 2;
    private static final int CHANNELS = 2;
    /** Chunk handed to the browser at a time. A quarter of a second at CD rate. */
    private static final int STREAM_CHUNK = 8192;
    /** How long a stream waits for audio before checking whether it should still be running. */
    private static final long IDLE_POLL_MILLIS = 500L;

    private final VMComputers plugin;
    private final int port;
    private final int sampleRate;
    private final int bufferBytes;
    private final String publicAddress;

    private HttpServer server;
    private final Map<Integer, AudioBus> buses = new ConcurrentHashMap<Integer, AudioBus>();
    /** Token -> player. A listener's ticket; checked once when the stream opens, never after. */
    private final Map<String, UUID> tokens = new ConcurrentHashMap<String, UUID>();
    private final SecureRandom random = new SecureRandom();

    public AudioService(VMComputers plugin) {
        this.plugin = plugin;
        this.port = plugin.getConfig().getInt("audio.port", 25566);
        this.sampleRate = Math.min(48000, Math.max(8000,
                plugin.getConfig().getInt("audio.sample-rate", 44100)));
        int millis = Math.max(200, plugin.getConfig().getInt("audio.buffer-millis", 2000));
        this.bufferBytes = sampleRate * CHANNELS * BYTES_PER_SAMPLE * millis / 1000;
        this.publicAddress = resolvePublicAddress();
    }

    public int sampleRate() {
        return sampleRate;
    }

    private String resolvePublicAddress() {
        String configured = plugin.getConfig().getString("audio.public-address", "");
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        // server.properties knows what the admin bound to, which is better than nothing but is
        // often blank (meaning "all interfaces") and is never the outside world's name for us.
        String bound = plugin.getServer().getIp();
        return bound != null && !bound.isBlank() ? bound : "localhost";
    }

    /** Starts the HTTP server. Does nothing if audio is switched off in the config. */
    public void start() {
        if (!plugin.getConfig().getBoolean("audio.enabled", true)) {
            plugin.getLogger().info("Guest audio is disabled in config.yml.");
            return;
        }
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/listen", this::handlePage);
            server.createContext("/stream", this::handleStream);
            server.createContext("/worklet.js", this::handleWorklet);
            // A streaming response holds its thread for as long as the listener stays, so this is
            // a listener limit as much as a thread pool.
            server.setExecutor(Executors.newFixedThreadPool(16, runnable -> {
                Thread thread = new Thread(runnable, "vm-audio-http");
                thread.setDaemon(true);
                return thread;
            }));
            server.start();
            plugin.getLogger().info("Guest audio server listening on port " + port
                    + "; links will point at " + publicAddress + ".");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not start the guest audio server on port "
                    + port + ". Audio links will not work; change audio.port in config.yml if "
                    + "something else is using it.", e);
            server = null;
        }
    }

    public void stop() {
        for (AudioBus bus : buses.values()) {
            bus.close();
        }
        buses.clear();
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    /** The bus a machine writes into. Created on demand when the machine boots. */
    public AudioBus busFor(int computerId) {
        return buses.computeIfAbsent(Integer.valueOf(computerId), id -> new AudioBus(bufferBytes));
    }

    /** Drops a machine's audio, waking anyone still listening so their stream ends. */
    public void release(int computerId) {
        AudioBus bus = buses.remove(Integer.valueOf(computerId));
        if (bus != null) {
            bus.close();
        }
    }

    /** Issues a listening ticket for a player. Replaces any ticket they already had. */
    public String issueToken(UUID player) {
        tokens.values().removeIf(player::equals);
        byte[] raw = new byte[18];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        tokens.put(token, player);
        return token;
    }

    /** Revokes a player's ticket, so a tab left open stops when they leave the server. */
    public void revokeTokens(UUID player) {
        tokens.values().removeIf(player::equals);
    }

    public String linkFor(int computerId, String token) {
        return "http://" + publicAddress + ":" + port + "/listen/" + computerId + "?t=" + token;
    }

    public boolean isRunning() {
        return server != null;
    }

    // ---- request handling -------------------------------------------------------------------

    private void handlePage(HttpExchange exchange) throws IOException {
        int computerId = idFromPath(exchange, "/listen/");
        if (computerId < 0 || !authorised(exchange)) {
            respond(exchange, 403, "text/plain", "Not a valid audio link.");
            return;
        }
        String token = queryParam(exchange, "t");
        respond(exchange, 200, "text/html; charset=utf-8", AudioPage.html(computerId, token,
                sampleRate));
    }

    private void handleWorklet(HttpExchange exchange) throws IOException {
        respond(exchange, 200, "text/javascript; charset=utf-8", AudioPage.worklet());
    }

    private void handleStream(HttpExchange exchange) throws IOException {
        int computerId = idFromPath(exchange, "/stream/");
        if (computerId < 0 || !authorised(exchange)) {
            respond(exchange, 403, "text/plain", "Not a valid audio link.");
            return;
        }
        AudioBus bus = buses.get(Integer.valueOf(computerId));
        if (bus == null) {
            respond(exchange, 404, "text/plain", "That computer is not running.");
            return;
        }

        exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        // Length 0 means chunked, which is what makes this a live stream rather than a download.
        exchange.sendResponseHeaders(200, 0);

        boolean firstListener;
        try (AudioBus.Reader reader = bus.openReader();
             OutputStream body = exchange.getResponseBody()) {
            firstListener = true;
            setGuestAudio(computerId, true);
            byte[] chunk = new byte[STREAM_CHUNK];
            while (buses.containsKey(Integer.valueOf(computerId))) {
                int count = reader.read(chunk, IDLE_POLL_MILLIS);
                if (count > 0) {
                    body.write(chunk, 0, count);
                    body.flush();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException closed) {
            // The listener closed the tab, or the network went away. Entirely normal.
        } finally {
            AudioBus current = buses.get(Integer.valueOf(computerId));
            if (current == null || !current.hasListeners()) {
                setGuestAudio(computerId, false);
            }
            exchange.close();
        }
    }

    /**
     * Switches the guest's audio on or off.
     *
     * <p>Left off until somebody actually listens, because an enabled stream means QEMU pushing
     * 170 KB a second down the display connection for nobody.
     */
    private void setGuestAudio(int computerId, boolean enabled) {
        VirtualMachine machine = ComputerFunctions.get(computerId);
        if (machine == null) {
            return;
        }
        try {
            machine.setAudioEnabled(enabled, sampleRate, CHANNELS);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not " + (enabled ? "enable" : "disable")
                            + " audio for computer #" + computerId, e);
        }
    }

    private boolean authorised(HttpExchange exchange) {
        String token = queryParam(exchange, "t");
        if (token == null) {
            return false;
        }
        UUID player = tokens.get(token);
        // One map lookup, once, when the stream opens. Nothing is checked per chunk afterwards.
        return player != null;
    }

    private static int idFromPath(HttpExchange exchange, String prefix) {
        String path = exchange.getRequestURI().getPath();
        if (!path.startsWith(prefix)) {
            return -1;
        }
        try {
            return Integer.parseInt(path.substring(prefix.length()));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String queryParam(HttpExchange exchange, String name) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return java.net.URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
