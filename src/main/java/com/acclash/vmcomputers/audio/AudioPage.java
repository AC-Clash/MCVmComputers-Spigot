package com.acclash.vmcomputers.audio;

/**
 * The listener's page and the worklet that plays the stream.
 *
 * <p>Kept as text rather than files under resources because it is small, and because the sample
 * rate has to be baked in: an {@code AudioContext} is created at a fixed rate, and if it disagrees
 * with what the guest is producing, the audio plays at the wrong speed rather than failing.
 */
final class AudioPage {

    private AudioPage() {
    }

    /**
     * The worklet. Receives interleaved 16-bit little-endian frames and plays them.
     *
     * <p>It also throws audio away when it gets too far ahead. A browser that buffers freely would
     * drift steadily further behind the machine it is supposed to be listening to, and end up
     * playing a minute-old desktop; a hard cap on the backlog keeps it honest at the cost of an
     * occasional skip.
     */
    static String worklet() {
        return """
                const MAX_BACKLOG = 16384; // frames; about a third of a second at 44.1 kHz

                class PcmPlayer extends AudioWorkletProcessor {
                    constructor() {
                        super();
                        this.left = new Float32Array(0);
                        this.right = new Float32Array(0);
                        this.port.onmessage = (event) => this.push(event.data);
                    }

                    push(bytes) {
                        const frames = Math.floor(bytes.byteLength / 4);
                        if (frames === 0) {
                            return;
                        }
                        const view = new DataView(bytes.buffer, bytes.byteOffset, frames * 4);
                        const left = new Float32Array(this.left.length + frames);
                        const right = new Float32Array(this.right.length + frames);
                        left.set(this.left);
                        right.set(this.right);
                        for (let i = 0; i < frames; i++) {
                            // Little-endian, which is what QEMU sends regardless of host.
                            left[this.left.length + i] = view.getInt16(i * 4, true) / 32768;
                            right[this.right.length + i] = view.getInt16(i * 4 + 2, true) / 32768;
                        }
                        if (left.length > MAX_BACKLOG) {
                            const from = left.length - MAX_BACKLOG;
                            this.left = left.subarray(from);
                            this.right = right.subarray(from);
                        } else {
                            this.left = left;
                            this.right = right;
                        }
                    }

                    process(inputs, outputs) {
                        const out = outputs[0];
                        const frames = out[0].length;
                        if (this.left.length < frames) {
                            return true; // Underrun: silence is better than a click.
                        }
                        out[0].set(this.left.subarray(0, frames));
                        if (out.length > 1) {
                            out[1].set(this.right.subarray(0, frames));
                        }
                        this.left = this.left.subarray(frames);
                        this.right = this.right.subarray(frames);
                        return true;
                    }
                }

                registerProcessor('pcm-player', PcmPlayer);
                """;
    }

    static String html(int computerId, String token, int sampleRate) {
        String streamUrl = "/stream/" + computerId + "?t=" + token;
        return """
                <!doctype html>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>Computer #%d</title>
                <style>
                  :root { color-scheme: dark; }
                  body { margin: 0; min-height: 100vh; display: grid; place-items: center;
                         background: #14161a; color: #e6e6e6;
                         font: 16px/1.5 ui-sans-serif, system-ui, sans-serif; }
                  main { text-align: center; padding: 2rem; }
                  h1 { font-size: 1.1rem; font-weight: 600; letter-spacing: .02em; margin: 0 0 .25rem; }
                  p { margin: .25rem 0 1.5rem; color: #9aa0a6; font-size: .9rem; }
                  button { font: inherit; font-weight: 600; padding: .8rem 2rem; border: 0;
                           border-radius: 999px; background: #4c8dff; color: #fff; cursor: pointer; }
                  button:hover { background: #3f7ae6; }
                  button:disabled { background: #2b2f36; color: #6b7076; cursor: default; }
                  #state { margin-top: 1.25rem; font-size: .85rem; color: #9aa0a6; min-height: 1.5em; }
                </style>
                <main>
                  <h1>Computer #%d</h1>
                  <p>Live audio from the guest</p>
                  <button id="go">Listen</button>
                  <div id="state"></div>
                </main>
                <script>
                const BUTTON = document.getElementById('go');
                const STATE = document.getElementById('state');
                let current = null;

                // Browsers allow only about six connections to one origin, and a stream holds one
                // open for as long as it plays. Without this, listening twice in a tab leaves the
                // old stream running and the new one queued behind it forever.
                window.addEventListener('pagehide', () => current && current.abort());

                // Browsers refuse to start audio without a gesture, so this cannot run on load.
                BUTTON.addEventListener('click', async () => {
                  BUTTON.disabled = true;
                  STATE.textContent = 'connecting...';
                  try {
                    const ctx = new AudioContext({ sampleRate: %d });
                    await ctx.audioWorklet.addModule('/worklet.js');
                    const node = new AudioWorkletNode(ctx, 'pcm-player', { outputChannelCount: [2] });
                    node.connect(ctx.destination);
                    await ctx.resume();

                    if (current) { current.abort(); }
                    current = new AbortController();
                    const response = await fetch('%s', { signal: current.signal });
                    if (!response.ok) {
                      throw new Error('server said ' + response.status);
                    }
                    STATE.textContent = 'listening';

                    // Chunked response read as it arrives -- this is what makes it live rather
                    // than a download that plays when it finishes.
                    const reader = response.body.getReader();
                    for (;;) {
                      const { done, value } = await reader.read();
                      if (done) break;
                      node.port.postMessage(value, [value.buffer]);
                    }
                    STATE.textContent = 'the computer stopped';
                  } catch (err) {
                    STATE.textContent = 'could not connect: ' + err.message;
                  }
                  BUTTON.disabled = false;
                  BUTTON.textContent = 'Listen again';
                });
                </script>
                """.formatted(computerId, computerId, sampleRate, streamUrl);
    }
}
