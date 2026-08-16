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

    /**
     * The listener's page, dressed as a mid-nineties corporate homepage.
     *
     * <p>Everything here is inline and self-contained: no fonts, images or scripts from anywhere
     * else. The server handing this out is a few dozen lines inside a Minecraft plugin, and a page
     * that needs the outside world would fail on exactly the machines most likely to be running
     * this -- a LAN server, or a laptop with no route out.
     *
     * <p>Percent signs are doubled throughout. This string goes through {@code formatted()}, and a
     * bare {@code %%} in a stylesheet is read as a format specifier and throws.
     */
    static String html(int computerId, String token, int sampleRate) {
        String streamUrl = "/stream/" + computerId + "?t=" + token;
        return """
                <!doctype html>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>AC Telecommunications :: Computer #%d</title>
                <style>
                  body { margin: 0; padding: 24px 12px; background: #008080; color: #000000;
                         font-family: "Times New Roman", Times, serif; font-size: 16px;
                         text-align: center; }
                  table.frame { margin: 0 auto; width: 620px; background: #c0c0c0;
                                border: 3px outset #ffffff; border-spacing: 0; }
                  td.page { padding: 18px 26px 22px; background: #c0c0c0; }
                  .sub { font-size: 13px; font-variant: small-caps; letter-spacing: .12em;
                         color: #202020; margin: 0 0 2px; }
                  .corp { font-size: 17px; font-weight: bold; letter-spacing: .18em;
                          text-transform: uppercase; margin: 0 0 6px; color: #300000; }
                  .aura { font-size: 46px; font-weight: bold; letter-spacing: .06em;
                          font-family: "Times New Roman", Times, serif; color: #ffd700;
                          text-shadow: 1px 1px 0 #a9791b, 2px 2px 0 #8b6508, 3px 3px 2px #000000;
                          margin: 2px 0 6px; line-height: 1.05; }
                  hr.rule { border: 0; height: 3px; margin: 12px 0;
                            background: linear-gradient(to right, #808080, #ffffff, #808080); }
                  h1.feed { font-size: 20px; margin: 10px 0 2px; }
                  .blurb { font-size: 14px; margin: 0 0 14px; font-style: italic; }
                  button { font-family: "Times New Roman", Times, serif; font-size: 17px;
                           font-weight: bold; padding: 6px 30px; background: #c0c0c0;
                           border: 3px outset #ffffff; color: #000000; cursor: pointer; }
                  button:active { border-style: inset; }
                  button:disabled { color: #808080; cursor: default; }
                  #state { margin: 14px auto 4px; width: 380px; padding: 5px 8px;
                           background: #000000; color: #00ff00; border: 2px inset #ffffff;
                           font-family: "Courier New", Courier, monospace; font-size: 13px;
                           text-align: left; min-height: 1.3em; }
                  .live { color: #ff0000; font-weight: bold; animation: blink 1.1s steps(1) infinite; }
                  @keyframes blink { from { opacity: 1 } 50%% { opacity: 0 } to { opacity: 1 } }
                  table.badges { margin: 10px auto 0; border-spacing: 8px; }
                  .badge { border: 2px outset #ffffff; padding: 4px 10px; font-size: 11px;
                           font-family: "Courier New", Courier, monospace; letter-spacing: .06em; }
                  .crimson { background: #dc143c; color: #ffffff; font-weight: bold; }
                  .spec { background: #000080; color: #c0c0c0; }
                  .footer { font-size: 11px; color: #303030; margin-top: 14px; line-height: 1.6; }
                  marquee { font-size: 12px; color: #000080; margin-top: 6px; }
                </style>
                <table class="frame"><tr><td class="page">

                  <p class="sub">AC Telecommunications</p>
                  <p class="corp">a subsidiary of</p>
                  <div class="aura">AURA CHARISMA</div>

                  <hr class="rule">

                  <h1 class="feed"><span class="live">&#9679;</span> COMPUTER #%d &mdash; LIVE AUDIO FEED</h1>
                  <p class="blurb">Real-time digital sound, transmitted direct from the machine.</p>

                  <button id="go">Listen</button>
                  <div id="state">&gt; idle. press LISTEN to open the feed.</div>

                  <table class="badges"><tr>
                    <td class="badge crimson">POWERED BY CRIMSON AUDIO TECHNOLOGY</td>
                    <td class="badge spec">%d Hz &middot; 16-BIT &middot; STEREO</td>
                  </tr></table>

                  <hr class="rule">

                  <marquee scrollamount="3">Welcome to the AC Telecommunications audio exchange &nbsp;&#9830;&nbsp; Please do not adjust your speakers &nbsp;&#9830;&nbsp; Thank you for listening</marquee>

                  <p class="footer">
                    Best viewed at 640 &times; 480 or higher.<br>
                    This page is under construction. &copy; Aura Charisma. All rights reserved.
                  </p>

                </td></tr></table>
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
                  STATE.textContent = '> dialling the machine...';
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
                    STATE.textContent = '> CONNECTED. receiving audio.';

                    // Chunked response read as it arrives -- this is what makes it live rather
                    // than a download that plays when it finishes.
                    const reader = response.body.getReader();
                    for (;;) {
                      const { done, value } = await reader.read();
                      if (done) break;
                      node.port.postMessage(value, [value.buffer]);
                    }
                    STATE.textContent = '> feed closed. the computer stopped.';
                  } catch (err) {
                    STATE.textContent = '> ERROR: ' + err.message;
                  }
                  BUTTON.disabled = false;
                  BUTTON.textContent = 'Reconnect';
                });
                </script>
                """.formatted(computerId, computerId, sampleRate, sampleRate, streamUrl);
    }
}
