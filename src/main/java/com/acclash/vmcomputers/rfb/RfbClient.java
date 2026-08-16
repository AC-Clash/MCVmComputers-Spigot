package com.acclash.vmcomputers.rfb;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A minimal RFB 3.8 client, aimed squarely at QEMU's built-in VNC server over loopback.
 *
 * <p>RFB is client-pull: we ask for an update and the server answers only once something has
 * changed. That gives free backpressure -- the next request is issued after the listener has
 * finished with the previous frame, so the frame rate self-tunes to whatever the consumer can
 * actually keep up with, and the connection can never flood us.
 *
 * <p>Only Raw and CopyRect are negotiated. The compressed encodings exist to trade CPU for
 * bandwidth, and on loopback there is no bandwidth to save. DesktopSize is essential rather than
 * optional: a guest moves from text-mode BIOS to installer to desktop, and without it the
 * framebuffer geometry silently desynchronises.
 */
public final class RfbClient implements Closeable {

    /** A changed region of the framebuffer. */
    public static final class Rect {
        public final int x;
        public final int y;
        public final int width;
        public final int height;

        public Rect(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public int area() {
            return width * height;
        }

        @Override
        public String toString() {
            return width + "x" + height + "+" + x + "+" + y;
        }
    }

    /** Callbacks fire on the client's pump thread; returning promptly controls the frame rate. */
    public interface Listener {
        /**
         * A batch of changes has been applied to {@code argb}.
         *
         * <p>The array is the client's live framebuffer and is reused between frames -- copy
         * anything that needs to outlive the call.
         */
        void onFramebuffer(int[] argb, int width, int height, List<Rect> damage);

        default void onResize(int width, int height) {
        }

        default void onBell() {
        }

        /**
         * A block of guest audio, little-endian PCM in the format asked for by
         * {@link #enableAudio(int, int)}.
         *
         * <p>{@code pcm} is reused between calls, so copy anything that must outlive it.
         */
        default void onAudio(byte[] pcm, int length) {
        }

        /** The guest started or stopped producing audio. */
        default void onAudioState(boolean playing) {
        }

        default void onCutText(String text) {
        }

        default void onDisconnect(IOException cause) {
        }
    }

    // Client -> server message types.
    private static final int MSG_SET_PIXEL_FORMAT = 0;
    private static final int MSG_SET_ENCODINGS = 2;
    private static final int MSG_FRAMEBUFFER_UPDATE_REQUEST = 3;
    private static final int MSG_KEY_EVENT = 4;
    private static final int MSG_POINTER_EVENT = 5;

    // Server -> client message types.
    private static final int SMSG_FRAMEBUFFER_UPDATE = 0;
    private static final int SMSG_SET_COLOUR_MAP = 1;
    private static final int SMSG_BELL = 2;
    private static final int SMSG_CUT_TEXT = 3;
    /**
     * QEMU's extension channel. Both directions use message type 255 with a submessage byte; the
     * only submessage used here is audio. Values from QEMU's own ui/vnc.h.
     */
    private static final int MSG_QEMU = 255;
    private static final int QEMU_SUB_AUDIO = 1;
    private static final int QEMU_AUDIO_END = 0;
    private static final int QEMU_AUDIO_BEGIN = 1;
    private static final int QEMU_AUDIO_DATA = 2;
    private static final int QEMU_AUDIO_ENABLE = 0;
    private static final int QEMU_AUDIO_DISABLE = 1;
    private static final int QEMU_AUDIO_SET_FORMAT = 2;
    /** Sample format 3 is signed 16-bit; QEMU sends it little-endian. */
    private static final int QEMU_AUDIO_FORMAT_S16 = 3;
    /** Pseudo-encoding that opts into the audio extension. Without it QEMU rejects the request. */
    private static final int ENC_AUDIO = -259;

    // Encodings.
    private static final int ENC_RAW = 0;
    private static final int ENC_COPY_RECT = 1;
    private static final int ENC_DESKTOP_SIZE = -223;

    private static final int SECURITY_NONE = 1;

    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final String desktopName;

    private volatile int width;
    private volatile int height;
    private int[] framebuffer;

    private byte[] scratch = new byte[0];
    private volatile Listener listener;
    private volatile boolean running;
    private volatile long minFrameIntervalNanos;
    /** Grown on demand and reused, since audio blocks arrive continuously once enabled. */
    private byte[] audioBuffer = new byte[0];
    private Thread pumpThread;

    private long frameCount;
    private long lastFrameNanos;
    private long lastRequestNanos;

    private RfbClient(Socket socket, DataInputStream in, DataOutputStream out,
                      int width, int height, String desktopName) {
        this.socket = socket;
        this.in = in;
        this.out = out;
        this.width = width;
        this.height = height;
        this.desktopName = desktopName;
        this.framebuffer = new int[width * height];
    }

    /**
     * Connects and completes the RFB handshake, retrying until QEMU opens the port.
     *
     * <p>Only the {@code None} security type is supported. We generate QEMU's own command line, so
     * a password-protected server means something is misconfigured rather than something to
     * negotiate around.
     */
    public static RfbClient connect(String host, int port, Duration timeout) throws IOException {
        long deadline = System.nanoTime() + timeout.toNanos();
        IOException last = null;

        while (System.nanoTime() < deadline) {
            Socket s = new Socket();
            try {
                s.connect(new InetSocketAddress(host, port), 1000);
                s.setTcpNoDelay(true);
                return handshake(s);
            } catch (IOException e) {
                last = e;
                try {
                    s.close();
                } catch (IOException ignored) {
                    // Nothing useful to do.
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted while connecting to VNC", ie);
                }
            }
        }
        throw new IOException("timed out after " + timeout.toMillis()
                + "ms connecting to VNC at " + host + ":" + port, last);
    }

    private static RfbClient handshake(Socket socket) throws IOException {
        DataInputStream in = new DataInputStream(
                new BufferedInputStream(socket.getInputStream(), 1 << 16));
        DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(socket.getOutputStream(), 1 << 13));

        // 1. Protocol version. The server speaks first: "RFB 003.008\n".
        byte[] versionBytes = new byte[12];
        in.readFully(versionBytes);
        String serverVersion = new String(versionBytes, StandardCharsets.US_ASCII);
        if (!serverVersion.startsWith("RFB ")) {
            throw new IOException("not an RFB server, got: " + serverVersion.trim());
        }
        out.write("RFB 003.008\n".getBytes(StandardCharsets.US_ASCII));
        out.flush();

        // 2. Security types.
        int securityCount = in.readUnsignedByte();
        if (securityCount == 0) {
            throw new IOException("server refused the connection: " + readString(in));
        }
        byte[] securityTypes = new byte[securityCount];
        in.readFully(securityTypes);
        boolean noneAvailable = false;
        for (byte t : securityTypes) {
            if ((t & 0xFF) == SECURITY_NONE) {
                noneAvailable = true;
                break;
            }
        }
        if (!noneAvailable) {
            throw new IOException("server requires authentication, which is not supported; "
                    + "QEMU should be started without a VNC password");
        }
        out.writeByte(SECURITY_NONE);
        out.flush();

        // 3. SecurityResult. RFB 3.8 always sends this, even for the None type.
        int securityResult = in.readInt();
        if (securityResult != 0) {
            throw new IOException("VNC authentication failed: " + readString(in));
        }

        // 4. ClientInit -- 1 means allow other clients to stay connected.
        out.writeByte(1);
        out.flush();

        // 5. ServerInit.
        int width = in.readUnsignedShort();
        int height = in.readUnsignedShort();
        byte[] pixelFormat = new byte[16];
        in.readFully(pixelFormat);
        String name = readString(in);

        RfbClient client = new RfbClient(socket, in, out, width, height, name);
        client.sendSetPixelFormat();
        client.sendSetEncodings();
        return client;
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > 1 << 20) {
            throw new IOException("implausible string length " + length);
        }
        byte[] buf = new byte[length];
        in.readFully(buf);
        return new String(buf, StandardCharsets.UTF_8);
    }

    /**
     * Asks for 32bpp true colour with red at bit 16, green at 8, blue at 0, little-endian. QEMU
     * converts server-side, so the wire format is whatever is cheapest for us to unpack.
     */
    private void sendSetPixelFormat() throws IOException {
        synchronized (out) {
            out.writeByte(MSG_SET_PIXEL_FORMAT);
            out.writeByte(0);
            out.writeByte(0);
            out.writeByte(0);

            out.writeByte(32);  // bits per pixel
            out.writeByte(24);  // depth
            out.writeByte(0);   // big-endian flag
            out.writeByte(1);   // true-colour flag
            out.writeShort(255);
            out.writeShort(255);
            out.writeShort(255);
            out.writeByte(16);  // red shift
            out.writeByte(8);   // green shift
            out.writeByte(0);   // blue shift
            out.writeByte(0);
            out.writeByte(0);
            out.writeByte(0);
            out.flush();
        }
    }

    private void sendSetEncodings() throws IOException {
        // ENC_AUDIO is a pseudo-encoding: it asks for no pixel format, it declares that this
        // client understands QEMU's audio extension. QEMU checks for it before honouring any audio
        // message, so leaving it out makes enableAudio silently do nothing.
        int[] encodings = {ENC_COPY_RECT, ENC_RAW, ENC_DESKTOP_SIZE, ENC_AUDIO};
        synchronized (out) {
            out.writeByte(MSG_SET_ENCODINGS);
            out.writeByte(0);
            out.writeShort(encodings.length);
            for (int e : encodings) {
                out.writeInt(e);
            }
            out.flush();
        }
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public String desktopName() {
        return desktopName;
    }

    public long frameCount() {
        return frameCount;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /**
     * Asks QEMU to start sending guest audio over this connection.
     *
     * <p>Minecraft cannot be sent audio at all -- its sound packet carries a name and a pitch, not
     * samples -- so this exists to feed something outside the game, and it costs nothing until a
     * listener actually wants it.
     *
     * <p>Two messages: the format, then the enable. QEMU only honours either if the audio
     * pseudo-encoding was advertised at handshake, which {@link #handshake} does.
     *
     * @param sampleRate samples per second; QEMU refuses anything above 48000
     * @param channels   1 or 2; QEMU refuses anything else
     */
    public void enableAudio(int sampleRate, int channels) throws IOException {
        if (channels != 1 && channels != 2) {
            throw new IllegalArgumentException("channels must be 1 or 2, got " + channels);
        }
        if (sampleRate <= 0 || sampleRate > 48000) {
            throw new IllegalArgumentException("sample rate must be 1..48000, got " + sampleRate);
        }
        synchronized (out) {
            out.writeByte(MSG_QEMU);
            out.writeByte(QEMU_SUB_AUDIO);
            out.writeShort(QEMU_AUDIO_SET_FORMAT);
            out.writeByte(QEMU_AUDIO_FORMAT_S16);
            out.writeByte(channels);
            out.writeInt(sampleRate);

            out.writeByte(MSG_QEMU);
            out.writeByte(QEMU_SUB_AUDIO);
            out.writeShort(QEMU_AUDIO_ENABLE);
            out.flush();
        }
    }

    /** Stops the audio stream. Safe to call when it was never started. */
    public void disableAudio() throws IOException {
        synchronized (out) {
            out.writeByte(MSG_QEMU);
            out.writeByte(QEMU_SUB_AUDIO);
            out.writeShort(QEMU_AUDIO_DISABLE);
            out.flush();
        }
    }

    /**
     * Reads one QEMU-extension message.
     *
     * <p>Audio shares the socket with the framebuffer, so a long audio block delays the next frame
     * and vice versa. That is the trade for not opening a second connection, and at 44.1 kHz stereo
     * a block is a few kilobytes -- far less than a frame.
     */
    private void handleQemuMessage() throws IOException {
        int submessage = in.readUnsignedByte();
        if (submessage != QEMU_SUB_AUDIO) {
            // Nothing else is subscribed to, and the length is not knowable, so the stream would
            // desynchronise if this ever fired. It cannot: the server only sends what was asked for.
            throw new IOException("unexpected QEMU submessage " + submessage);
        }
        int operation = in.readUnsignedShort();
        switch (operation) {
            case QEMU_AUDIO_BEGIN:
            case QEMU_AUDIO_END: {
                Listener l = listener;
                if (l != null) {
                    l.onAudioState(operation == QEMU_AUDIO_BEGIN);
                }
                break;
            }
            case QEMU_AUDIO_DATA: {
                int length = in.readInt();
                if (length < 0) {
                    throw new IOException("negative audio block length " + length);
                }
                if (audioBuffer.length < length) {
                    audioBuffer = new byte[Math.max(length, audioBuffer.length * 2)];
                }
                in.readFully(audioBuffer, 0, length);
                Listener l = listener;
                if (l != null) {
                    l.onAudio(audioBuffer, length);
                }
                break;
            }
            default:
                throw new IOException("unknown QEMU audio operation " + operation);
        }
    }

    /** Optional floor on the interval between update requests; 0 disables throttling. */
    public void setMaxFrameRate(int fps) {
        this.minFrameIntervalNanos = fps <= 0 ? 0L : 1_000_000_000L / fps;
    }

    /** Runs the pump on a daemon thread. */
    public void start() {
        if (pumpThread != null) {
            throw new IllegalStateException("already started");
        }
        running = true;
        pumpThread = new Thread(this::pumpQuietly, "rfb-pump");
        pumpThread.setDaemon(true);
        pumpThread.start();
    }

    private void pumpQuietly() {
        try {
            pump();
        } catch (IOException e) {
            Listener l = listener;
            if (running && l != null) {
                l.onDisconnect(e);
            }
        }
    }

    /**
     * Blocking receive loop. The first request is non-incremental to fetch a complete frame;
     * afterwards each request is issued only once the previous frame has been handed to the
     * listener, which is what makes the rate self-limiting.
     */
    public void pump() throws IOException {
        running = true;
        requestUpdate(false);

        while (running) {
            int messageType;
            try {
                messageType = in.readUnsignedByte();
            } catch (IOException e) {
                if (!running) {
                    return;
                }
                throw e;
            }

            switch (messageType) {
                case SMSG_FRAMEBUFFER_UPDATE:
                    handleFramebufferUpdate();
                    break;
                case SMSG_SET_COLOUR_MAP:
                    handleSetColourMap();
                    break;
                case SMSG_BELL: {
                    Listener l = listener;
                    if (l != null) {
                        l.onBell();
                    }
                    break;
                }
                case MSG_QEMU:
                    handleQemuMessage();
                    break;
                case SMSG_CUT_TEXT: {
                    skipFully(3);
                    String text = readString(in);
                    Listener l = listener;
                    if (l != null) {
                        l.onCutText(text);
                    }
                    break;
                }
                default:
                    // Staying in sync is impossible once an unknown message appears.
                    throw new IOException("unknown RFB server message type " + messageType);
            }
        }
    }

    /**
     * Discards exactly {@code count} bytes.
     *
     * <p>{@link DataInputStream#skipBytes} is allowed to skip fewer bytes than asked, which on a
     * socket stream is not hypothetical. Under-skipping desynchronises the connection and every
     * later message is garbage, so loop until the count is met.
     */
    private void skipFully(int count) throws IOException {
        int remaining = count;
        while (remaining > 0) {
            int skipped = in.skipBytes(remaining);
            if (skipped <= 0) {
                // skipBytes never blocks for more input; fall back to a read that does.
                if (in.read() < 0) {
                    throw new IOException("stream ended while skipping " + count + " bytes");
                }
                remaining--;
            } else {
                remaining -= skipped;
            }
        }
    }

    private void handleSetColourMap() throws IOException {
        skipFully(1);
        in.readUnsignedShort();
        int count = in.readUnsignedShort();
        // True colour was negotiated, so this should never arrive; consume it to stay in sync.
        skipFully(count * 6);
    }

    private void handleFramebufferUpdate() throws IOException {
        skipFully(1);
        int rectCount = in.readUnsignedShort();

        List<Rect> damage = new ArrayList<Rect>(Math.max(1, rectCount));
        boolean resized = false;

        for (int i = 0; i < rectCount; i++) {
            int x = in.readUnsignedShort();
            int y = in.readUnsignedShort();
            int w = in.readUnsignedShort();
            int h = in.readUnsignedShort();
            int encoding = in.readInt();

            switch (encoding) {
                case ENC_RAW:
                    readRawRect(x, y, w, h);
                    damage.add(new Rect(x, y, w, h));
                    break;
                case ENC_COPY_RECT:
                    readCopyRect(x, y, w, h);
                    damage.add(new Rect(x, y, w, h));
                    break;
                case ENC_DESKTOP_SIZE:
                    // A pseudo-encoding: the rect carries the new geometry, not pixels.
                    applyResize(w, h);
                    resized = true;
                    damage.clear();
                    break;
                case ENC_AUDIO:
                    // QEMU acknowledges the audio extension by sending a rectangle in this
                    // pseudo-encoding: full screen bounds, no data behind it. It means "audio
                    // requests will be honoured from here on", and the only correct response is
                    // to read nothing and carry on.
                    break;
                default:
                    throw new IOException("server used unnegotiated encoding " + encoding);
            }
        }

        frameCount++;
        lastFrameNanos = System.nanoTime();

        Listener l = listener;
        if (l != null) {
            if (resized) {
                l.onResize(width, height);
            } else if (!damage.isEmpty()) {
                l.onFramebuffer(framebuffer, width, height, Collections.unmodifiableList(damage));
            }
        }

        throttle();
        // A resize invalidates everything, so refetch the whole screen rather than a delta.
        requestUpdate(!resized);
    }

    /**
     * Spaces successive update requests by at least the configured interval. Measured from the
     * previous request rather than from frame completion, so decode and listener time count
     * towards the interval instead of being added on top of it.
     */
    private void throttle() {
        long interval = minFrameIntervalNanos;
        if (interval <= 0) {
            return;
        }
        long elapsed = System.nanoTime() - lastRequestNanos;
        long remaining = interval - elapsed;
        if (remaining > 0) {
            try {
                Thread.sleep(remaining / 1_000_000L, (int) (remaining % 1_000_000L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void applyResize(int newWidth, int newHeight) {
        if (newWidth <= 0 || newHeight <= 0) {
            return;
        }
        this.width = newWidth;
        this.height = newHeight;
        this.framebuffer = new int[newWidth * newHeight];
    }

    private void readRawRect(int x, int y, int w, int h) throws IOException {
        int needed = w * h * 4;
        if (scratch.length < needed) {
            scratch = new byte[needed];
        }
        in.readFully(scratch, 0, needed);

        int[] fb = framebuffer;
        int fbWidth = width;

        // The pixels must be consumed either way to keep the stream aligned, but a rect that does
        // not fit means our geometry is stale -- writing it would run past the end of a row and
        // corrupt the rows after it. Drop it and wait for the DesktopSize that resynchronises us.
        if (x < 0 || y < 0 || x + w > fbWidth || y + h > height) {
            return;
        }

        int src = 0;
        for (int row = 0; row < h; row++) {
            int dst = (y + row) * fbWidth + x;
            for (int col = 0; col < w; col++) {
                // Little-endian 0x00RRGGBB, so bytes arrive as B, G, R, unused.
                fb[dst + col] = 0xFF000000
                        | ((scratch[src + 2] & 0xFF) << 16)
                        | ((scratch[src + 1] & 0xFF) << 8)
                        | (scratch[src] & 0xFF);
                src += 4;
            }
        }
    }

    private void readCopyRect(int x, int y, int w, int h) throws IOException {
        int srcX = in.readUnsignedShort();
        int srcY = in.readUnsignedShort();

        int[] fb = framebuffer;
        int fbWidth = width;
        if (srcX + w > fbWidth || srcY + h > height || x + w > fbWidth || y + h > height) {
            return;
        }

        // Source and destination frequently overlap (window drags, scrolling), so stage the copy.
        int[] tmp = new int[w * h];
        for (int row = 0; row < h; row++) {
            System.arraycopy(fb, (srcY + row) * fbWidth + srcX, tmp, row * w, w);
        }
        for (int row = 0; row < h; row++) {
            System.arraycopy(tmp, row * w, fb, (y + row) * fbWidth + x, w);
        }
    }

    private void requestUpdate(boolean incremental) throws IOException {
        synchronized (out) {
            out.writeByte(MSG_FRAMEBUFFER_UPDATE_REQUEST);
            out.writeByte(incremental ? 1 : 0);
            out.writeShort(0);
            out.writeShort(0);
            out.writeShort(width);
            out.writeShort(height);
            out.flush();
            lastRequestNanos = System.nanoTime();
        }
    }

    // ---- input -----------------------------------------------------------

    /** Sends a key press or release. {@code keysym} is an X11 keysym; see {@link Keysym}. */
    public void sendKey(int keysym, boolean pressed) throws IOException {
        synchronized (out) {
            out.writeByte(MSG_KEY_EVENT);
            out.writeByte(pressed ? 1 : 0);
            out.writeShort(0);
            out.writeInt(keysym);
            out.flush();
        }
    }

    /** Press and release in one go. */
    public void tapKey(int keysym) throws IOException {
        sendKey(keysym, true);
        sendKey(keysym, false);
    }

    /**
     * Moves the pointer and sets the button state.
     *
     * @param buttonMask bit 0 left, bit 1 middle, bit 2 right, bits 3/4 wheel up/down
     */
    public void sendPointer(int x, int y, int buttonMask) throws IOException {
        int cx = Math.max(0, Math.min(width - 1, x));
        int cy = Math.max(0, Math.min(height - 1, y));
        synchronized (out) {
            out.writeByte(MSG_POINTER_EVENT);
            out.writeByte(buttonMask & 0xFF);
            out.writeShort(cx);
            out.writeShort(cy);
            out.flush();
        }
    }

    /** A wheel click is a press and release of button 4 (up) or 5 (down). */
    public void sendScroll(int x, int y, boolean up) throws IOException {
        int mask = up ? 1 << 3 : 1 << 4;
        sendPointer(x, y, mask);
        sendPointer(x, y, 0);
    }

    @Override
    public void close() {
        running = false;
        try {
            socket.close();
        } catch (IOException ignored) {
            // Nothing useful to do.
        }
    }

    /** X11 keysyms for the keys a virtual keyboard actually needs. */
    public static final class Keysym {
        /** Characters that are only reachable on a US layout by holding shift. */
        private static final String SHIFTED_SYMBOLS = "~!@#$%^&*()_+{}|:\"<>?";

        /**
         * Whether a character needs shift held down to type it.
         *
         * <p>Sending the keysym alone is not enough. QEMU translates a keysym to a scancode for the
         * physical key, and without a shift the guest reads whatever is printed on the unshifted
         * half of it -- so a colon arrives as a semicolon, and "C:" becomes "C;". Every symbol on
         * the top half of a key has the same problem, and so does every capital letter.
         *
         * <p>US layout, which is what QEMU assumes by default. A guest set to another layout would
         * show this bug again with a different set of characters, and this table is where to look.
         */
        public static boolean needsShift(char c) {
            return (c >= 'A' && c <= 'Z') || SHIFTED_SYMBOLS.indexOf(c) >= 0;
        }

        public static final int BACKSPACE = 0xFF08;
        public static final int TAB = 0xFF09;
        public static final int RETURN = 0xFF0D;
        public static final int ESCAPE = 0xFF1B;
        public static final int INSERT = 0xFF63;
        public static final int DELETE = 0xFFFF;
        public static final int HOME = 0xFF50;
        public static final int END = 0xFF57;
        public static final int PAGE_UP = 0xFF55;
        public static final int PAGE_DOWN = 0xFF56;
        public static final int LEFT = 0xFF51;
        public static final int UP = 0xFF52;
        public static final int RIGHT = 0xFF53;
        public static final int DOWN = 0xFF54;
        public static final int SHIFT_LEFT = 0xFFE1;
        public static final int CONTROL_LEFT = 0xFFE3;
        public static final int ALT_LEFT = 0xFFE9;

        private Keysym() {
        }

        /** Function keys, 1-based: {@code f(1)} is F1. */
        public static int f(int n) {
            if (n < 1 || n > 12) {
                throw new IllegalArgumentException("F" + n + " is out of range");
            }
            return 0xFFBE + (n - 1);
        }

        /** For printable ASCII the keysym is simply the character code. */
        public static int ofChar(char c) {
            if (c >= 0x20 && c <= 0x7E) {
                return c;
            }
            switch (c) {
                case '\n':
                case '\r':
                    return RETURN;
                case '\t':
                    return TAB;
                case '\b':
                    return BACKSPACE;
                default:
                    throw new IllegalArgumentException("no keysym mapping for character " + (int) c);
            }
        }

        /**
         * Every key a player can name, in the order they are offered for completion.
         *
         * <p>One table, shared by the typing command and by the chair's rebindable keys. Two
         * tables would drift, and the failure would be a player told that a key they can type is
         * not a key they can bind.
         */
        public static final String[] NAMES = {
            "RETURN", "ENTER", "SPACE", "TAB", "ESCAPE", "BACKSPACE", "DELETE",
            "UP", "DOWN", "LEFT", "RIGHT", "HOME", "END", "PAGEUP", "PAGEDOWN",
            "SHIFT", "CTRL", "ALT",
            "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12",
        };

        /**
         * The keysym a name stands for, or null if it is not one.
         *
         * <p>A single printable character is its own key, so {@code w} and {@code SPACE} are both
         * things a player can bind.
         */
        public static Integer byName(String rawName) {
            if (rawName == null || rawName.isEmpty()) {
                return null;
            }
            String name = rawName.toUpperCase(java.util.Locale.ROOT);
            if (name.length() == 1) {
                char c = rawName.charAt(0);
                return c >= 0x20 && c <= 0x7E ? Integer.valueOf(c) : null;
            }
            if (name.matches("F([1-9]|1[0-2])")) {
                return Integer.valueOf(f(Integer.parseInt(name.substring(1))));
            }
            switch (name) {
                case "RETURN":
                case "ENTER":
                    return Integer.valueOf(RETURN);
                case "TAB":
                    return Integer.valueOf(TAB);
                case "ESC":
                case "ESCAPE":
                    return Integer.valueOf(ESCAPE);
                case "BACKSPACE":
                    return Integer.valueOf(BACKSPACE);
                case "DELETE":
                    return Integer.valueOf(DELETE);
                case "SPACE":
                    return Integer.valueOf(' ');
                case "UP":
                    return Integer.valueOf(UP);
                case "DOWN":
                    return Integer.valueOf(DOWN);
                case "LEFT":
                    return Integer.valueOf(LEFT);
                case "RIGHT":
                    return Integer.valueOf(RIGHT);
                case "HOME":
                    return Integer.valueOf(HOME);
                case "END":
                    return Integer.valueOf(END);
                case "PAGEUP":
                    return Integer.valueOf(PAGE_UP);
                case "PAGEDOWN":
                    return Integer.valueOf(PAGE_DOWN);
                case "SHIFT":
                    return Integer.valueOf(SHIFT_LEFT);
                case "CTRL":
                case "CONTROL":
                    return Integer.valueOf(CONTROL_LEFT);
                case "ALT":
                    return Integer.valueOf(ALT_LEFT);
                default:
                    return null;
            }
        }

        /** How to write a keysym back to a player, so what they are shown is what they can type. */
        public static String nameOf(int keysym) {
            switch (keysym) {
                case RETURN: return "ENTER";
                case TAB: return "TAB";
                case ESCAPE: return "ESCAPE";
                case BACKSPACE: return "BACKSPACE";
                case DELETE: return "DELETE";
                case UP: return "UP";
                case DOWN: return "DOWN";
                case LEFT: return "LEFT";
                case RIGHT: return "RIGHT";
                case HOME: return "HOME";
                case END: return "END";
                case PAGE_UP: return "PAGEUP";
                case PAGE_DOWN: return "PAGEDOWN";
                case SHIFT_LEFT: return "SHIFT";
                case CONTROL_LEFT: return "CTRL";
                case ALT_LEFT: return "ALT";
                case ' ': return "SPACE";
                default:
                    if (keysym >= 0xFFBE && keysym <= 0xFFC9) {
                        return "F" + (keysym - 0xFFBE + 1);
                    }
                    return keysym >= 0x21 && keysym <= 0x7E
                            ? String.valueOf((char) keysym)
                            : "0x" + Integer.toHexString(keysym);
            }
        }
    }
}
