# VM Computers — Overhaul Handoff

Read this first. It is the state of the QEMU overhaul as of 2026-08-14, written so a fresh session
can be productive without re-deriving anything.

---

## 1. What this project is

A **Spigot plugin** that runs real virtual machines inside vanilla Minecraft. A player builds a
"computer" in the world; its screen is a grid of item-framed maps; they look at it and click to use
it. Vanilla clients only — no client mod, which is the whole point of the project and constrains
every design decision below.

**Repo:** `AC-Clash/MCVmComputers-Spigot` (PUBLIC). The overhaul is **merged into `main`**.

`nms-map-packets` is parked, not dead: it branches off the overhaul and sends the map packet
directly, which lifts frames off the server tick and cuts them to the changed rectangle. It costs
Spigot support, so it waits for a reason to spend that. See §4 and §5.

---

## 2. What changed in this overhaul

The plugin used to embed **jDOSBox** — an x86 emulator written in Java, running inside the server
JVM. DOS-only, slow, 505 files and 210k lines vendored into the repo. It has been **deleted** and
replaced by **QEMU as a separate OS process**, controlled over **QMP** and read over **RFB (VNC)**.

Also in this overhaul: the build jumped ~2.5 years (Paper 1.20.4 → Spigot 26.2), NMS was removed
entirely, and the storage/placement model was rewritten.

**It works end to end today.** A player can build a computer, insert an ISO, power it on, watch a
real OS boot on a map wall, click things, and type.

---

## 3. Architecture

```
QEMU process ──QMP (JSON/TCP)──▶  emu/    lifecycle: start, stop, media, disks
     │
     └────────RFB/VNC (TCP)─────▶  rfb/    frames out, input in
                                     │
                                     ▼
                                  display/  quantize → scale → map panels
                                  listeners/ clicks & keys → guest input
```

### Package map

| Package | Role | Key files |
|---|---|---|
| `emu/` | QEMU process + control. **No Bukkit** except `VmService`. | `VmSpec` (config→argv), `QemuProcess`, `QmpClient`, `QemuBinary`, `VmService` (orchestrates), `VirtualMachine` (the seam) |
| `rfb/` | RFB 3.8 client. **No Bukkit.** | `RfbClient` |
| `display/` | Guest pixels → Minecraft maps | `MonitorScreen`, `PanelRenderer`, `ScreenPump` (sends frames), `MapColorLut`, `ImageScaler`, `ScreenGeometry`, `MonitorSize` |
| `computer/` | The in-world model | `ComputerLayout` (pure offsets), `Computer`, `ComputerRegistry` (O(1) block index) |
| `parts/` | Component appearance | `PartModel`/`PartModels` (geometry from `parts.json`), `PartRenderer` (transforms + spawning), `ComponentType` (catalogue) |
| `sql/` | Persistence | `ComputerDao` |
| `listeners/` | Game events | `PointerListener`, `PreventionListener`, `ClickListener`, `PlayerListener` |
| `bench/` | Standalone harness, no Minecraft needed | `RfbDump` |

### The core modelling idea

**A computer is fully determined by `(anchor, facing, monitorSize)`.** Every component position —
all 24 screen panels of a projector, the desk, chair, tower — is *derived* by `ComputerLayout`.
Placement, removal and the click index all read that same function, so they cannot disagree.

The only thing that can't be derived is **map IDs** (the server assigns them), so those are
persisted in `computer_panels`.

---

## 4. Decisions already made — do not re-litigate

**QEMU over RFB/VNC, not libmks / D-Bus / SPICE.** Researched over years, confirmed by email with
the maintainers:
- SPICE: discontinued (told to Anston by a SPICE developer).
- libmks / QEMU `-display dbus`: its author (Christian Hergert) said the D-Bus display driver is
  fundamentally broken and needs replacing, libmks is "in a holding pattern," and **most distros
  don't compile the D-Bus display driver at all** — fatal for a plugin admins install. Critically,
  the DMA-BUF fast path's entire value is *never reading pixels back to the CPU*, and this project
  must read every pixel to quantize it to Minecraft's palette. Hergert's own advice for that case
  was to use the VNC backend.
- RFB is also a genuinely good fit: **client-pull**, so requesting the next update only after the
  consumer finishes gives free backpressure, and its dirty rectangles line up with Minecraft's
  partial map updates.

**Spigot API only, no NMS.** Paper runs Mojang-mapped, Spigot doesn't, so an NMS plugin can't be one
jar for both; the Spigot server artifact is unpublished by licence, so NMS would force BuildTools on
every developer and CI run. NMS turned out to be unnecessary anyway (see §5).

**Loopback TCP, not Unix sockets**, for QMP and VNC — avoids betting cross-platform behaviour on
AF_UNIX in QEMU's Windows builds. Unmeasurable difference at these data rates.

**The pointer tracks the player's head, but is never drawn.** Tracking is what gives the guest
hover — mouseover menus, button highlights, tooltips — which a click-only model cannot produce. But
a host-drawn cursor has to be painted into the framebuffer, which dirties map panels for as long as
anyone looks around and reaches the player a map-packet behind their own head, so the drawn arrow
visibly lags the crosshair it is chasing. Minecraft's crosshair is already exactly where the pointer
is, client-side and free. So the guest gets the position and the player gets the crosshair. See §5.

**Components are display entities built from vanilla blocks, not a resource pack and not player
heads.** The mod draws its parts as Blockbench item models — custom geometry on custom textures —
which a pack could reproduce exactly, and the assets are GPLv3 like this repo so they could simply
be reused. The pack was rejected anyway: it is one more thing an admin has to host, and this plugin's
whole selling point is that a vanilla client needs nothing. Player heads were rejected because their
skins have to live on Mojang's texture servers, which is an outside dependency for artwork that
would still read as a head.

What is left is geometry. A Blockbench element is a box with a size, a centre and an optional
rotation about a pivot, and that is exactly what a `BlockDisplay` transformation expresses, so the
shapes convert one-for-one — 27 models, 103 boxes, no hand modelling. `tools/generate_parts.py` does
the conversion offline and commits `parts.json`, so the mod is a tool dependency and not a build
dependency.

**Colour is the part that is approximate, and it is good enough.** Each box takes the vanilla block
nearest its texture's *dominant* colour — dominant, not mean, because the mean of a high-contrast
texture is a colour that appears nowhere in it (cpu.png is 48% black with gold pins and averages to
an olive that matches neither). Against the 26.2 client's own textures the auto-matched error is a
median of 10.7 and a worst of 18.9 on a 0–441 RGB scale. The remaining weak spot is circuit-board
green: vanilla's greens are all duller and more olive than a real PCB, and `green_concrete` is the
best available at an error around 50.

`OVERRIDES` in the generator is the tuning knob, and it exists mostly to keep *materials* consistent
rather than colours close. Left alone the matcher gives the plain case and the windowed case
different blacks and puts obsidian's purple sheen on the keyboard; boards and contacts are likewise
forced to one green and one gold, so a motherboard and a RAM stick read as the same material.

**What this cannot do:** an inventory slot renders an `ItemStack`, so display entities are unusable
in a GUI. Every component icon in the ordering menu is therefore an ordinary vanilla item chosen to
be recognisable and, more importantly, distinct at a glance — see `ComponentType`.

---

## 5. Hard-won facts (verified, not assumed)

These cost experiments. Don't re-discover them.

**The server only updates a framed map every 10 ticks. This was the entire frame rate.** In
`ServerEntity.sendChanges()`, the map of an item frame is pushed only when
`tickCount % itemFrameCursorUpdateInterval == 0`. Vanilla and Spigot hardcode 10; Paper made it
configurable (`maps.item-frame-cursor-update-interval`) and kept 10 as the default. Ten ticks is
half a second, so **a screen of item frames repaints at 2 fps** no matter how fast anything else is.
Emulator speed, transport, quantization and panel count were all irrelevant next to it.

**The fix is `Player.sendMap`, which is plain Bukkit** — it renders one map and sends it to one
player immediately. `ScreenPump` drives it every tick, so the ceiling becomes 20 fps (one frame per
tick, the most a map can be redrawn at all) on Spigot and Paper alike, with no server config to get
right. **NMS was never the obstacle here** — the updates simply were not being driven.

**`sendMap` always sends a full 128×128 patch**, so it gives up the dirty-rectangle saving below.
That is why `ScreenPump` sends a panel only when its pixels genuinely differ (`PanelRenderer.blit`
compares before it copies, via `Arrays.mismatch`), only to players within 64 blocks, and only within
a per-player budget. The one thing still on the table is sub-panel patches, which *would* need NMS —
but that would cut bytes, not add frames, since the rate is already one tick.

**Dirty rectangles come free through the Bukkit API — NMS is not needed.** Confirmed by reading
Paper 26.2 bytecode: `CraftMapCanvas.setPixel` compares the new colour against the existing one and
returns early if unchanged (`if_icmpeq`), only then calling `setColorsDirty`.
`MapItemSavedData$HoldingPlayer` keeps `minDirtyX/minDirtyY/maxDirtyX/maxDirtyY` **per viewing
player**, and `createPatch()` builds a sub-rectangle. So writing the whole framebuffer every frame
is fine — unchanged pixels cost nothing on the wire. Caveat: it's a bounding box per map, not per
column.

**Map colour index 0 is TRANSPARENT, not black.** A fresh `Bukkit.createMap()` is all zeros, so
frames show the wall behind them. Screens must be explicitly filled black.

**`MapView` renderers do NOT survive a restart** — the default world-map renderer comes back. Hence
persisted map IDs and reattachment on enable.

**`ItemDisplay` does NOT render map contents** — it shows the parchment item icon even with
`ItemDisplayTransform.FIXED`. Only item frames reach the map-rendering path. So monitors are locked
to **one map per block**; scaled/shrunken monitors are impossible.

**QEMU does not composite the guest cursor into the VNC framebuffer.** It offers the shape
separately via the Cursor pseudo-encoding, which this client doesn't request. A guest using a
hardware cursor plane therefore looks like it has no pointer. **Resolution: don't care** — Minecraft's
own crosshair is the pointer.

**The screen's plane is the far face of the panel block, not the near one.** A panel is an item
frame hanging in an *air* block, attached to the wall behind it, so the picture is ~0.97 blocks into
that block (vanilla puts a frame 0.46875 from the block centre, away from its facing). Intersecting
the look ray with the near face instead is not a small offset, it is **parallax**: the pointer falls
short of wherever you aim, by an amount that grows with how far off to the side the target is and
vanishes when you stand square in front of it. Reported as "the cursor only goes so far, it never
reaches all the way I turn, unless I walk in front of the spot" — that is the signature, and it
names the depth of the plane, nothing else. Measured on a LARGE desk before the fix: sweeping the aim
across the whole picture moved the guest pointer 122 → 561 of 640, **69% of the width**, with the
outer margins unreachable at both ends. `ScreenGeometry.wallMounted` owns this now and is pure, so
it can be checked without a server.

**`PlayerMoveEvent` cannot drive a pointer.** CraftBukkit gates it on
`squaredPositionDelta > 1/256 || |Δyaw| + |Δpitch| > 10`, measured against the last *firing*, not the
last packet. So turning your head raises no event at all until the look has swung a cumulative **ten
degrees**, then it arrives in one jump — while walking clears the position term on nearly every
packet. The symptom is unmistakable and was reported exactly this way: aiming by looking feels stuck
and steppy, aiming by walking feels fine. Ten degrees is most of the width of a desk monitor at
seating distance. Verified in Paper 26.2 bytecode (`ServerGamePacketListenerImpl.handleMovePlayer`,
constants `0.00390625` and `10.0f`), not inferred. **Aim is therefore sampled on a 1-tick repeating
task**, which is also the rate the client sends position at and the rate a map can be redrawn, so
nothing is gained by going faster.

**Pointer tracking itself is nearly free; *drawing* the pointer is what was expensive.** The two got
conflated once and the tracking was removed along with the cursor. Tracking costs one plane
intersection and a 6-byte RFB packet; it touches no map. The current `PointerListener` keeps the
cost there: a move that cannot have changed the ray is dropped before any maths, a player who has not moved is
skipped before any maths, the search runs over running machines rather than the whole registry, the
ray allocates nothing, and a guest pixel the machine already has is never re-sent (deduped per *computer*, so two players aiming at one spot
cost one event). `ScreenGeometry.trace` has a primitive-argument overload for exactly this path.

**x86 vs ARM guest resolution behaviour differs fundamentally:**
- x86 guests ignore EDID and come up in **720×400 VGA text mode** during BIOS/bootloader/installer.
- **ARM UEFI honours the EDID request literally** — you get exactly what you ask for. This cuts both
  ways: asking for 320×240 left the guest with *no usable mode at all*. Every size now requests
  **640×480**, the one mode everything supports.

**Apple Silicon can't accelerate x86.** `qemu-system-x86_64` gets only `tcg` (interpreted, ~100×
slower); `qemu-system-aarch64` gets `hvf`. Architecture is chosen at build time per computer and
defaults to the host CPU.

**`MapColorLut` builds in ~103 ms and yields 244 usable colours** (61 base × 4 shades).

---

## 6. Performance model

The bottleneck is **Minecraft's map protocol**, not the emulator or transport. One map = 128×128 =
16,384 bytes of palette indices, no inter-frame coding.

**The ceiling is now one frame per tick — 20 fps.** It used to be 2 fps, and not for any reason
worth optimising against: the server simply refused to push a framed map more than once every ten
ticks (§5). Since `ScreenPump` drives the sends, the rate is the tick.

Cost is per *changed* panel, since unchanged ones are never sent:

```
bytes_per_second ≈ changed_panels_per_tick × viewers × 16,400 × 20
```

Real measurement: an idle guest's blinking text cursor is **32 changed pixels out of 288,000**,
touching one panel and changing it about twice a second. Idle screens still cost essentially
nothing. The expensive case is a panel that genuinely changes every tick, and there a full send is
proportionate — which is why the budget in `ScreenPump` exists rather than a byte-level trick.

**Rules that follow from this:**
- Never `MapPalette.matchColor` (linear palette scan per pixel) — use `MapColorLut`.
- **Ordered (Bayer) dithering only.** Error diffusion would let one changed pixel alter every pixel
  after it, defeating the per-pixel comparison that keeps traffic small.
- Scale in **RGB before quantization** — averaging palette indices is meaningless.
- `PanelRenderer.render()` is **not** called on a timer. It runs from `CraftMapView.render()`, which
  only happens when a packet is being built — so once per panel per tick that `ScreenPump` decides
  to send, not once per tick per viewer as previously written here. Skipping unchanged panels still
  matters, and it does. Marking all panels dirty on every small change was what made the old drawn
  cursor unusable: ~393,000 `setPixel` calls for a 24-panel projector, to move a 10×16 arrow.
- **Compare before you copy.** `PanelRenderer.blit` uses `Arrays.mismatch` per row, which is a
  vectorised intrinsic and cheaper than the `arraycopy` it skips. It is also what decides whether a
  panel is worth 16 KB of a player's bandwidth, so it pays for itself twice.
- **Nothing driven by player input should touch the framebuffer.** The map protocol's latency is the
  reason: anything drawn in response to a player's own movement arrives after they have already
  moved again.

---

## 7. Threading

**Nothing that talks to QEMU runs on the server thread.**

| Path | Thread |
|---|---|
| Boot (`QemuBinary.discover`, QMP + RFB connect) | async task |
| Power off / remove / plugin disable | async task |
| Frame decode → scale → quantize → panel buffers | RFB pump thread |
| Sending panels to viewers (`ScreenPump`) | server tick — `sendMap` touches connections |
| Pointer / key / scroll sends | per-VM single input thread (ordering matters) |
| Map render | server tick (Bukkit's own work) |

Graceful shutdown waits up to 10 s for ACPI; **removal and disable use `kill()`** instead, because a
guest at a firmware prompt never answers and it froze the server for the full timeout.

---

## 8. Dev workflow

```bash
./gradlew build          # JAVA_HOME must be a JDK 25+; toolchain auto-provisions via foojay
./gradlew runServer      # downloads + runs Paper 26.2 into run/ (gitignored)
```

**The loop is stop → rebuild → start.** Rebuilding the jar under a live server causes
`NoClassDefFoundError`, because `PluginClassLoader` reads classes lazily from the jar on disk.

Local env: SDKMAN JDK `26.0.2-amzn` (JDK 11 is also installed and is the default `javac` on PATH —
it cannot read Paper's Java 25 bytecode). QEMU 11.0.3 via Homebrew. `run/eula.txt` is accepted.

**Standalone harness** (no Minecraft at all — very useful for emulator work):
```bash
java -cp out com.acclash.vmcomputers.bench.RfbDump --arch AARCH64 --frames 5 --out /tmp/shots
```

### In-game commands
```
/vmcomputers create <SMALL|MEDIUM|LARGE|XLARGE> [x86_64|aarch64]
/vmcomputers remove [id]
/vmcomputers iso                      # list ISOs in plugins/vm_computers/isos
/vmcomputers iso <id> <file|none>
/vmcomputers type <text>              # types into the screen you're looking at
/vmcomputers type @RETURN             # @TAB @ESC @BACKSPACE @UP..@F12
/vmcomputers testdisplay [clear]      # ItemDisplay diagnostic (answer: doesn't work)
/vmcomputers debug                    # toggle: draws the pointer on screen, for testing
/vmcomputers parts list               # component models and their piece counts
/vmcomputers parts <model> [scale]    # preview one where you stand, facing you
/vmcomputers parts clear              # remove previews within 32 blocks
```

`debug` exists because the pointer is invisible by design (§4) and "invisible and working" looks
exactly like "invisible and broken". Turn it on to see where the plugin thinks the guest's pointer
is; the arrow will trail your crosshair, which is the map protocol rather than the aim. It is
global, not per player, since the arrow is painted into a framebuffer everyone looking at that
screen shares. Leave it off otherwise — it costs map traffic on every head movement.
Right-click the tower (desk) or control block (projector) to power on/off.

**Test assets already downloaded** in `run/plugins/vm_computers/isos/`:

| ISO | Size | Use |
|---|---|---|
| `ubuntu-26.04-desktop-arm64.iso` | 3968 MB | **Live desktop, ARM.** Auto-boots to a live session, no keypress. The one to test mouse and GUI with. |
| `alpine-virt-3.24.1-aarch64.iso` | 88 MB | **Fast iteration, ARM.** Console only, boots in seconds, scrolls continuously. |
| `debian-13.6.0-arm64-netinst.iso` | 701 MB | Installer, ARM. **Stops on a GRUB menu waiting for a keypress** — it looks frozen but isn't. Bad for quick tests. |
| `TinyCore-17.1.iso` | 26 MB | x86 — TCG only on Apple Silicon, so ~100× slow. |

---

## 9. Monitor sizes

| Size | Grid | Pixels | Form | Seat | Notes |
|---|---|---|---|---|---|
| SMALL | 2×2 | 256×256 | desk | 2.0 | 640×480 scaled down hard |
| MEDIUM | 3×3 | 384×384 | desk | 2.5 | |
| LARGE | 4×3 | 512×384 | desk | 3.0 | exact 0.8 scale — best desk |
| XLARGE | 6×4 | 768×512 | **projector** | 5.0 | only size that shows 640×480 **1:1** |

Seat distances are tuned to a ~60° head sweep. Walking closer genuinely improves aim (the screen
covers more of your view, so fewer pixels per degree) — an emergent property of the geometry, not
code.

---

## 10. Known state: what's untested or unfinished

**Untested / just changed** (the last two commits shipped without a play test):
- **Component models have never been looked at in game.** The load path is verified (27 models,
  103 pieces, every block id resolves on 26.2) and the transform maths is verified against known
  bounds, but *nobody has stood in front of one*. Two things to check first: whether parts face the
  right way (the authored-north convention in `PartRenderer.yawFor` is an assumption about
  Blockbench, not a measured fact), and whether the desk accessories sit on the slab surface rather
  than floating or sunk. `/vmcomputers parts <model>` is the fast loop for this.
- The `PointerListener` rewrite: invisible head tracking, and the click model on top of it.
- `PreventionListener` rewrite — was **entirely dead code** (queried the old schema by serialized
  location string), so left-clicking *destroyed screen panels*. Fixed but unverified.
- SMALL/MEDIUM booting Debian after the 640×480 resolution fix.

**Known gaps:**
- **The pointer is invisible by design** (§4), so the guest's own cursor is the only feedback. A
  guest on a hardware cursor plane shows nothing at all, and there the crosshair is all you get.
- **Keyboard is chat-based** (`/vmcomputers type`). The planned on-screen keyboard doesn't exist.
- **Guest RAM is one hardcoded number** (`VmService`, currently 4096 MB — 2048 could not boot an
  Ubuntu live session). It should be chosen when the computer is built, next to size and
  architecture. Anston's call, noted 2026-08-14.
- **No size-selection GUI** — user asked for "both" (command arg *and* chest GUI); only the arg
  exists. `Create.perform` has a TODO where the menu should open.
- **No ordering GUI.** `ComponentType` is the catalogue it will read from — ids, names, prices in
  iron (taken from the mod's `ItemList`), categories and inventory icons — but nothing opens a menu
  yet, and there is no currency, no delivery and no assembly. A computer is still built whole by
  `/vmcomputers create`; parts cannot yet be ordered, carried or installed.
- **Parts that carry their detail in a texture come out flat.** The keyboard is one cuboid whose
  mod texture draws the key rows, and the motherboard's traces and capacitors are painted on;
  neither survives the conversion to blocks. The fix is to add geometry the mod never needed —
  actual raised keys, actual chips — which display entities make affordable. Not done.
- **No OS catalog / downloader.** ISOs are dropped in a folder by hand. Plan: port `quickget`'s
  catalog concept to a JSON manifest.
- **Security:** guests get user-mode NAT, which reaches whatever the host can reach including its
  LAN. `VmSpec.Builder.networking(false)` turns it off. Worth an admin config knob.

---

## 11. Gotchas that will bite

- Use `Block.setType(material, false)` — physics pops attached blocks (button, pressure plate) off
  as dropped items.
- Item frames need care: breaking one in creative fires **no block event**, and right-clicking
  **rotates** the item. `PlayerInteractEvent` cancellation is not enough.
- **Clicking an item frame never fires `PlayerInteractEvent`.** A frame is an entity: left click is
  an attack (`EntityDamageByEntityEvent`), right click is `PlayerInteractEntityEvent`. Since the
  screen *is* item frames, a mouse driven only by `PlayerInteractEvent` works when you aim past arm's
  reach — the miss arrives as `LEFT_CLICK_AIR` — and silently does nothing when you sit at the desk.
  This cost a debugging session. `PointerListener` now takes the left button from
  `PlayerAnimationEvent` (the arm swing, sent for every left click whatever is in front of you) and
  the right button from both interact events.
- **The screen's frames are `setFixed(true)`, and the vanilla client treats a fixed frame as not
  having consumed the interaction.** So it follows the entity packet with a use-item packet, and one
  right click raises `PlayerInteractEntityEvent` *and* `PlayerInteractEvent`. Both paths are needed,
  so `PointerListener` drops the duplicate with a one-tick window instead.
- `PlayerInteractEvent.getClickedBlock()` is **null** for air clicks — fires on every swing.
- Don't cancel `PlayerMoveEvent` wholesale to keep a player seated; it cancels rotation too and the
  client rubber-bands. Refuse position only, pass yaw/pitch through.
- ARM `virt` has **no PS/2**, so a USB keyboard device is required or there's no keyboard at all.
- ARM UEFI needs a **private writable copy** of `edk2-arm-vars.fd` per machine.
- `RfbDump` must not force `--machine`; the architecture picks its own default (`q35` vs `virt`).

---

## 12. Suggested next steps

1. **Play-test the unverified work** (pointer tracking and clicks, protection, SMALL/MEDIUM). In
   particular check hover actually reaches the guest — hover over a menu bar and see it highlight.
2. **Finish a Debian install** end to end with `/vmcomputers type`. First real proof a persistent OS
   survives a power cycle on the virtual disk.
3. **On-screen keyboard** — the biggest remaining UX gap. Host-rendered overlay driven by the
   click-to-position model; must work in a BIOS, so it can't be guest-side.
4. **Size-selection GUI** for `create`.
5. **OS catalog + downloader.**
6. **Resource pack** — it exists for audio and possible computer textures, and the URL in
   `PlayerListener` is a dead Dropbox link served with `force = true`, which kicks anyone whose
   download fails. Needs rehosting and rethinking, not deleting.
7. **Permissions** — `plugin.yml` declares none, so any player can `create` and, worse, `remove`
   someone else's computer.

---

## 13. Working agreements

- Git identity is set **repo-locally** to `Anston Sorensen <ansorensen1118@gmail.com>` (matches all
  prior commits). Work on a branch and merge to `main`, rather than committing to it directly.
- Anston prefers testing on the real server over building extra classes to test things outside it.
  Unit tests are for **pure logic only** (`ComputerLayout`, `ScreenGeometry`, `ImageScaler`, `Json`,
  `MapColorLut`) — don't contort Bukkit-coupled code for testability.
- Goal is **highest performance / fps achievable**.
