package com.acclash.vmcomputers.audio;

/**
 * One computer's audio, held briefly so listeners can pick it up.
 *
 * <p>A ring of raw PCM with a write position that only ever goes up. Listeners keep their own
 * position and are handed whatever has arrived since they last asked.
 *
 * <h2>The rule that shapes this class</h2>
 *
 * <p><b>A listener must never be able to slow the writer down.</b> The writer is the RFB pump
 * thread, and that same thread paces the frame rate -- if it ever blocked waiting for a browser to
 * catch up, a stalled listener would cost everyone in the world frames. So writing never blocks and
 * never fails; a listener that falls further behind than the ring is long simply loses the audio it
 * missed and is snapped back to live. Audio that arrives late is worthless anyway.
 */
public final class AudioBus {

    private final byte[] ring;
    private final Object lock = new Object();
    /** Total bytes ever written. Wraps into the ring by modulo; never resets. */
    private long written;
    private volatile int listeners;

    public AudioBus(int capacityBytes) {
        this.ring = new byte[Math.max(1024, capacityBytes)];
    }

    /** Bytes the ring can hold before the oldest audio is overwritten. */
    public int capacity() {
        return ring.length;
    }

    public boolean hasListeners() {
        return listeners > 0;
    }

    /**
     * Adds a block of audio. Called on the RFB pump thread, and returns promptly no matter what any
     * listener is doing.
     */
    public void write(byte[] data, int length) {
        if (length <= 0) {
            return;
        }
        synchronized (lock) {
            if (length >= ring.length) {
                // A single block bigger than the whole ring: keep only its tail, since that is the
                // most recent audio and everything before it is already unreachable.
                System.arraycopy(data, length - ring.length, ring, 0, ring.length);
                written += length;
                lock.notifyAll();
                return;
            }
            int offset = (int) (written % ring.length);
            int firstPart = Math.min(length, ring.length - offset);
            System.arraycopy(data, 0, ring, offset, firstPart);
            if (firstPart < length) {
                System.arraycopy(data, firstPart, ring, 0, length - firstPart);
            }
            written += length;
            lock.notifyAll();
        }
    }

    /** Wakes every listener so they can notice the stream has ended. */
    public void close() {
        synchronized (lock) {
            lock.notifyAll();
        }
    }

    /** A listener's own place in the stream. */
    public Reader openReader() {
        synchronized (lock) {
            listeners++;
            // Starts at the live edge: a new listener wants what is happening now, not a replay of
            // whatever happened to still be in the ring.
            return new Reader(written);
        }
    }

    public final class Reader implements AutoCloseable {

        private long position;
        private boolean closed;

        private Reader(long position) {
            this.position = position;
        }

        /**
         * Copies out whatever has arrived, waiting up to {@code timeoutMillis} for something.
         *
         * @return bytes written into {@code dest}, or 0 if nothing arrived in time
         */
        public int read(byte[] dest, long timeoutMillis) throws InterruptedException {
            synchronized (lock) {
                long deadline = System.currentTimeMillis() + timeoutMillis;
                while (!closed && written == position) {
                    long remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0) {
                        return 0;
                    }
                    lock.wait(remaining);
                }
                if (closed) {
                    return 0;
                }

                long behind = written - position;
                if (behind > ring.length) {
                    // Too far behind for the ring to still hold what was missed. Skip to the oldest
                    // audio that survives, which is the only honest thing to hand over.
                    position = written - ring.length;
                    behind = ring.length;
                }

                int count = (int) Math.min(behind, dest.length);
                int offset = (int) (position % ring.length);
                int firstPart = Math.min(count, ring.length - offset);
                System.arraycopy(ring, offset, dest, 0, firstPart);
                if (firstPart < count) {
                    System.arraycopy(ring, 0, dest, firstPart, count - firstPart);
                }
                position += count;
                return count;
            }
        }

        @Override
        public void close() {
            synchronized (lock) {
                if (!closed) {
                    closed = true;
                    listeners--;
                }
            }
        }
    }
}
