package com.acclash.vmcomputers.emu;

import com.acclash.vmcomputers.rfb.RfbClient;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * One in-game computer's virtual machine, from the plugin's point of view.
 *
 * <p>This is the seam that replaced jDOSBox. Everything above it -- listeners, renderers, input
 * handling -- talks only to this interface, so the backend can change without touching game code.
 * Nothing here mentions QEMU or RFB.
 */
public interface VirtualMachine extends Closeable {

    /** Receives decoded frames on the transport's own thread, never on the server main thread. */
    interface FrameListener {
        /**
         * A batch of changes has been applied.
         *
         * <p>{@code argb} is the live framebuffer and is reused between calls, so copy anything
         * that must outlive the callback. Returning promptly is what paces the frame rate.
         */
        void onFrame(int[] argb, int width, int height, List<RfbClient.Rect> damage);

        /** The guest changed video mode; the framebuffer has been reallocated. */
        default void onResize(int width, int height) {
        }

        /**
         * A block of guest audio, interleaved little-endian 16-bit PCM.
         *
         * <p>Only ever called between {@link #setAudioEnabled} being switched on and off again.
         * The array is reused, so copy anything that must outlive the call.
         */
        default void onAudio(byte[] pcm, int length) {
        }
    }

    /** Database id of the computer this VM belongs to. */
    int computerId();

    /** Boots the machine and connects the display. Blocks until the framebuffer is available. */
    void start() throws IOException;

    /**
     * Turns the guest's audio stream on or off.
     *
     * <p>Off unless somebody is listening: an enabled stream is QEMU pushing about 170 KB a second
     * down the display connection, and that connection is also carrying the picture.
     */
    void setAudioEnabled(boolean enabled, int sampleRate, int channels) throws IOException;

    boolean isRunning();

    /** Current guest resolution, which changes as the guest switches video mode. */
    int width();

    int height();

    /** Must be set before {@link #start()} to avoid missing the first frame. */
    void setFrameListener(FrameListener listener);

    // Input methods queue and return immediately. They are called from the server thread, and a
    // socket write there -- however small -- is not something to do on the tick.

    /** X11 keysym; see {@link RfbClient.Keysym}. */
    void sendKey(int keysym, boolean pressed);

    /** Bit 0 left, bit 1 middle, bit 2 right. */
    void sendPointer(int x, int y, int buttonMask);

    void sendScroll(int x, int y, boolean up);

    /** Swaps the CD medium while the machine is running. */
    void insertCdrom(Path iso) throws IOException;

    /**
     * ACPI power button, escalating to a forced stop if the guest ignores it. Can block for
     * several seconds, so never call this from the server thread.
     */
    void shutdown();

    /**
     * Destroys the machine immediately, with no chance for the guest to respond.
     *
     * <p>Used when the computer is being torn out of the world, where waiting for a guest that may
     * have no operating system to acknowledge a power button is pure delay.
     */
    void kill();

    @Override
    void close();
}
