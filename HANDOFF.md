# VM Computers — Handoff

Read this first. It is deliberately **not** a description of how the plugin works — the code says
that, and better. This is the working environment, the decisions already settled, and the facts
that cost an evening each to learn.

State as of **2026-08-16**. Everything described is on `main` and pushed.

---

## 1. Orientation

A **Spigot plugin** that runs real virtual machines inside vanilla Minecraft. QEMU runs as a
separate OS process, controlled over QMP, read over RFB/VNC, and painted onto a wall of
item-framed maps. Players sit at a desk, look at the screen to aim, and click.

**Vanilla clients only.** No client mod, no resource pack. That single constraint explains almost
every odd decision in the codebase, and it is not negotiable.

**Repo:** `AC-Clash/MCVmComputers-Spigot` (public). Branches: `main`, plus `nms-map-packets`
(parked — sends map packets directly, would cut bytes but costs Spigot support; waiting for a
reason). `guest-audio` is merged; the local branch is stale and safe to delete.

Two reference documents are worth reading before touching guest configuration:

- **`README.md`** — the user-facing guide: requirements, installing QEMU per platform, hardware
  acceleration, config, commands, permissions, troubleshooting. It lives in the repo so it stays
  with the code. The Host Setup Manual artifact was folded into it and is now stale — don't consult
  it, and update the README instead.
- **Guest Hardware Manual** — what defines a VM, which hosts accelerate which guests, and what
  every OS era needs before it boots. https://claude.ai/code/artifact/1a8343ff-34e1-4ebb-9526-2a12fbfbef6a

---

## 2. Environment

```bash
./gradlew build          # compiles + runs tests
./gradlew test           # 57 tests, all pure logic, ~1s
./gradlew runServer      # downloads + runs Paper 26.2 into run/ (gitignored)
```

**The loop is stop → rebuild → start.** Rebuilding the jar under a live server causes
`NoClassDefFoundError` — `PluginClassLoader` reads classes lazily off the disk jar. This has bitten
more than once; check for a running server before any gradle task that writes `build/libs`.

**Local machine:** macOS on Apple Silicon. JDK `26.0.2-amzn` via SDKMAN (JDK 11 is also installed
and is the default `javac` on PATH — it cannot read Paper's bytecode). QEMU 11.0.3 via Homebrew.
`run/eula.txt` accepted. Anston plays on this same machine, so **the dev server is often already
running**.

**Consequence of that host:** aarch64 guests get HVF and run natively; x86_64 gets TCG only, about
100× slower. That is survivable precisely because the x86-only guests are old ones — emulated DOS
and Windows 95 are still faster than the hardware they shipped on.

**Offline generators.** Both commit their output, so neither the mod checkout nor a Minecraft
install is a build dependency:

```bash
python3 tools/generate_parts.py ../MCVmComputers   # -> src/main/resources/parts.json
python3 tools/generate_heads.py                    # -> tools/heads/*.png
```

`generate_parts.py` needs the client jar to sample block colours; honours `MC_CLIENT_JAR`.
`vehicles.json` is **hand-authored** — do not point the generator at it.

**Headless harness**, no Minecraft required — very useful for emulator work:

```bash
java -cp out com.acclash.vmcomputers.bench.RfbDump --arch AARCH64 --frames 5 --out /tmp/shots
```

### Commands

```
/vmcomputers create <SMALL|MEDIUM|LARGE|XLARGE> [x86_64|aarch64]
/vmcomputers remove [id]
/vmcomputers iso [<id> <file|none>]
/vmcomputers disk [<id> <file|none>]     # admin; supplied images, attached read-write
/vmcomputers profile [<id> <name>]       # guest hardware era
/vmcomputers type <text>        # @RETURN @TAB @ESC @UP..@F12
/vmcomputers keys [game|menu|bind <input> <key>|reset]
/vmcomputers audio              # private link to the guest's sound
/vmcomputers order | phone      # parts shop, Auros (3 paper each)
/vmcomputers parts <list|model|clear>
/vmcomputers testdisplay | debug
```

`debug` draws the pointer on screen. It exists because the pointer is invisible by design, and
"invisible and working" looks exactly like "invisible and broken". Leave it off — it costs map
traffic on every head movement.

### ISOs already downloaded (`run/plugins/vm_computers/isos/`)

| ISO | Use |
|---|---|
| `alpine-virt-3.24.1-aarch64.iso` | **Fast iteration, ARM.** Boots in seconds, console only. |
| `ubuntu-26.04-desktop-arm64.iso` | Live desktop, ARM. The one for testing mouse and GUI. |
| `debian-13.6.0-arm64-netinst.iso` | Installer, ARM. **Stops at a GRUB menu** — looks frozen, isn't. |
| `FD14LIVE.iso` | FreeDOS, x86. For the legacy-guest work. |
| `TinyCore-17.1.iso` | x86, tiny. TCG-slow on this host. |

---

## 3. Working agreements

- **Push after every commit.** Not batched.
- **Commit straight to `main`** for ordinary work. Branch only for something large enough to review
  as a unit (the delivery truck, the components layer). A PR for three bug fixes is ceremony, and
  Anston has said so twice.
- **No AI attribution anywhere that reaches the repo.** No `Co-Authored-By` trailers, no "generated
  with" footers, in commits, PR bodies, comments or files. Anston Sorensen
  `<ansorensen1118@gmail.com>` is sole author; history was rewritten on 2026-08-16 to make that
  true. Note PR bodies live on GitHub, not in git — a history rewrite does not touch them.
- Git identity is set **repo-locally**.
- **Anston reviews design choices and pushes back.** Several decisions here were reversed that way.
  State the trade-off and a recommendation rather than quietly picking.
- **Prefer testing on the real server.** Unit tests are for pure logic only — don't contort
  Bukkit-coupled code for testability.
- Goal is **highest achievable frame rate**.
- Prices and component tiers are the mod's own numbers. Change the currency or the earn rate, not
  the catalogue.

---

## 4. Decisions closed — don't reopen

- **QEMU over RFB/VNC, not SPICE or libmks/D-Bus.** SPICE is discontinued (from a SPICE developer);
  libmks's author says the D-Bus display driver is broken, unmaintained, and not even compiled by
  most distros — and its DMA-BUF fast path's whole value is never reading pixels back, which this
  project must do to quantize them. His own advice for that case was the VNC backend.
- **Spigot API only, no NMS.** Paper is Mojang-mapped and Spigot isn't, so one jar can't call NMS
  for both, and the Spigot artifact is unpublished by licence. It turned out to be unnecessary
  anyway.
- **No resource pack, for anything.** One used to be force-pushed from a dead Dropbox link, which
  only ever kicked people. Removed. Any feature that seems to want a pack needs a different answer —
  guest audio's answer is a browser page.
- **The pointer is tracked but never drawn.** Tracking gives the guest hover, which a click-only
  model can't. Drawing it means painting the framebuffer on every head movement, and it lags the
  crosshair that is already exactly where it should be.
- **Components are display entities in the world, player heads in menus.** An inventory slot renders
  an `ItemStack` and nothing else, so a display entity cannot appear in a chest GUI at all.
- **Auros, not iron.** Iron competes with everything else a player wants it for. Vanilla maps were
  tried and are a trap: a full machine came to ~344 iron against the mod's 86, plus 688 paper.
- **Loopback TCP, not Unix sockets**, for QMP and VNC — avoids betting on AF_UNIX in QEMU's Windows
  builds. Unmeasurable at these rates.
- **The truck's timings and geometry are tuning, not architecture.** Constants at the top of
  `DeliveryTruck`, boxes in `vehicles.json`. Nothing waits on a flight leg, so no schedule change
  can strand an order.

---

## 5. Gotchas that will bite

### Minecraft / Bukkit

- **The server only pushes a framed map every 10 ticks** — that was the entire frame rate, 2 fps,
  regardless of everything else. The fix is `Player.sendMap`, plain Bukkit, driven every tick.
  **NMS was never the obstacle**; the updates simply weren't being driven.
- **Map colour index 0 is transparent, not black.** A fresh map shows the wall behind it.
- **`MapView` renderers do not survive a restart.** Hence persisted map ids and reattachment.
- **`ItemDisplay` does not render map contents** — only item frames reach that path. So one map per
  block; scaled monitors are impossible.
- **Clicking an item frame never fires `PlayerInteractEvent`.** Left click is
  `EntityDamageByEntityEvent`, right click is `PlayerInteractEntityEvent`. A mouse built on
  `PlayerInteractEvent` alone works at range and silently dies when you sit at the desk.
- **Fixed frames double-fire.** The client treats a fixed frame as not having consumed the
  interaction, so one right click raises both events. Deduped with a one-tick window.
- **`PlayerMoveEvent` cannot drive a pointer.** CraftBukkit gates it on a cumulative **ten degrees**
  of look change. Aim is sampled on a 1-tick task instead.
- **Don't cancel `PlayerMoveEvent` wholesale** to seat a player — it cancels rotation too and the
  client rubber-bands. Refuse position only.
- **`Block.setType(material, false)`** — physics pops attached blocks off as items.
- **A display entity has no hitbox.** Anything clickable needs a real block (`BARRIER`) or an
  `Interaction` entity behind it.
- **A display's `translation` is applied after its rotation and is not itself rotated.** Turning a
  part means rotating each box's offset by hand too. Forget it and the part scatters instead of
  turning — and it still looks plausible in a screenshot. Covered by tests now.
- **`setInterpolationDelay` written *last* is what starts an interpolation**, and an unrotated
  `FIXED` text display reads from `+Z`. Both verified in game via the truck.
- **Particles only take a direction when the count is zero.** With a count, the offsets are a spread
  and the last argument is a random-direction speed.
- **`Villager.setAware(false)`, not `setAI(false)`.** Unaware keeps physics, step height and the
  walk animation; AI-off slides like a statue.
- **A chicken lays eggs even with AI off** — the timer is in `aiStep`, not the goals, and Bukkit
  exposes no switch. The eChair refuses the drop instead.
- **`Bukkit.addRecipe` refuses a duplicate key**, and `/reload` registers twice. `removeRecipe`
  first.
- **Beats inside another phase's tick range must live outside the `else if` chain**, or they are
  silently swallowed. The truck's landing gear never deployed for exactly this reason.

### QEMU / guests

- **ARM UEFI's per-machine variable store goes stale, and a stale one boots the EFI shell instead
  of the ISO.** It writes `Boot####` entries pinned to exact device paths and prefers them over
  hunting for removable media; change the hardware and every entry points at nothing. The symptom is
  a machine that booted fine last week sitting at `Shell>` with the same ISO in the drive, and it
  looks exactly like the ISO not being attached. **Fixed by `bootindex`** on the cdrom and disk,
  which QEMU passes via fw_cfg and the firmware reads first. Bisected against a real failing vars
  file. This forced the aarch64 disk from `if=virtio` to an explicit `-device virtio-blk-pci`.
- **A keysym names a character, but QEMU presses a key.** Without an explicit shift the guest hears
  the unshifted half — `*` typed `8`, capitals typed lower case. `Keysym.needsShift` owns this and
  it is **US-layout**; another layout would reproduce the bug with different characters.
- **x86 guests ignore EDID** and come up in 720×400 text mode during BIOS and installers. **ARM UEFI
  honours it literally** — asking for 320×240 once left a guest with no usable mode at all. Every
  size asks for 640×480.
- **ARM `virt` has no PS/2**, so a USB keyboard device is required or there is no keyboard.
- **QEMU does not composite the guest cursor into the framebuffer.** A guest on a hardware cursor
  plane looks like it has no pointer. Minecraft's crosshair is the pointer; don't care.
- **QEMU is found on `PATH` and nowhere else.** The "or configure an explicit path" in the error
  message is a lie — no such setting exists. `qemu-img` must be on PATH separately (a different
  package on Debian).
- **`RfbDump` must not force `--machine`** — each architecture picks its own default.

### This codebase

- **A new *required* `ComponentSlot` breaks every existing computer** unless the backfill fills it.
  `VMComputers.restoreComponents` fills gaps, and only required ones once a computer has rows of its
  own — otherwise a deliberately-pulled hard drive grows back every restart.
- **`Computer.architecture` defaults to `X86_64`.** Anything building a `Computer` outside
  `/vmcomputers create` must set it from the host or the machine silently refuses every arm64 ISO.
- **`ScreenGeometry.setGuestResolution` takes the *displayed* size** — after scaling to fit the
  panel grid — not the guest's own resolution. `MonitorScreen.setDisplayedSize` feeds it.
- **`Currency.is()` checks the tag, never the material.** Screen panels are filled maps too.
- **Clearing display entities by proximity takes the neighbours' with them.** Record ids and remove
  exactly those.
- **An item that is also its own crafting ingredient gets eaten by its own recipe.** The brick phone
  uses `RecipeChoice.ExactChoice` so the recipe book can't auto-fill a phone into a phone.
- **Static fields initialise in source order, and the catalogue in `ComponentType` runs during it.**
  A registry declared below the entries that fill it is still null when the first one registers, so
  the class dies on load. It compiles perfectly. Bit once already with the case set.
- **A column added to the reader must be added to the `SELECT` too.** `loadAll` names its columns
  explicitly, so reading a new one throws on the first row and takes every computer with it. Bit
  once already with `disk` and `profile`.
- **Check what already owns an event before adding a handler.** Twice now something was built that
  already existed: a scroll handler when `PointerListener` already owned the wheel, and a shift-key
  fix that was already sitting finished on an unmerged branch.

---

## 6. State

### Verified

Play-tested in game: the whole delivery-truck sequence, ordering and checkout, the eChair keyboard,
ISO boot on ARM after the `bootindex` fix, the power indicator, and `/vmcomputers type` including
shifted characters.

Covered by tests (`./gradlew test`, 57 of them, all pure): the display transform including
rotation-about-pivot and the folded-gear case, the screen ray including the parallax regression,
image fitting, and the whole key table.

### Never exercised in game

- **Everything added on 2026-08-16 after the handoff rewrite**: the `disks/` folder, all ten guest
  profiles, the graphics bay tiers, and the two named cases. The profiles were each started against
  real QEMU 11.0.3 so the devices and combinations are known good, but nothing has booted an actual
  guest, and no case has been placed. **The two case models are hand-authored and their facing is a
  guess** — front bezel at -Z, matching `pc_case`. `/vmcomputers parts dell_dimension_l500r` is the
  fast check.

- **`AssemblyMenu` and the bay menus** — `fit`/`remove` are where an inventory bug costs a player a
  real item. The shop's checkout is well exercised; assembly is the untouched half.
- **Assembly end to end** — a placed case becoming a computer.
- **Whether parts face the right way.** The authored-north convention is an assumption about
  Blockbench; the truck rendering correctly is decent circumstantial evidence. `/vmcomputers parts
  <model>` is the fast loop.
- **Whether desk accessories sit on the surface** rather than floating or sunk.
- **Guest RAM and cores from fitted parts.** A 64 MB stick really means 64 MB.
- Older: the `PointerListener` and `PreventionListener` rewrites, SMALL/MEDIUM booting Debian.

### Known gaps

- **The plugin's own disks are still fixed at 16 GiB.** Supplied images are any size, but a machine
  on its own disk gets 16 GiB and no say. Hard drive tiers are the fix.
- **The 32-bit motherboard is indistinguishable from the 64-bit one.** Should become a real knob —
  a 3.5 GB ceiling and the `pc` machine — or be cut.
- **No sound-card or network-card component**, though both are real choices and profiles now give
  them sensible defaults to override.
- **No OS catalogue.** ISOs are dropped in a folder by hand. Plan: quickget's per-OS tuning concept
  as a committed JSON manifest — take the idea, not the shell scripts, which scrape vendor pages and
  break constantly.
- **Keyboard is chat-based** plus the chair's five keys. The on-screen keyboard is the biggest
  remaining UX gap; it must work in a BIOS, so it can't be guest-side.
- **No payment on delivery.** Payment is taken at order time.
- **The truck's flight path is only measured at the strip's ends** — a tree partway along gets
  clipped through. Cosmetic.
- **Parts whose detail is painted on come out flat** (keyboard, motherboard traces). The fix is
  geometry the mod never needed, which display entities make affordable.

### Recently closed

The `disks/` folder, guest profiles, the graphics bay tiers, the Dimension and Presario cases, and
the README becoming the setup guide. Before that: permissions (`vmcomputers.use` / `.build` /
`.admin`, with per-computer ownership), a cap on concurrent VMs, guest networking as an admin
setting, guest audio over HTTP, and the first tests.

---

## 7. Next

Items 1–3 of the manual's order are done (`disks/`, guest profiles, the graphics bay), plus the two
named cases. What is left, in order:

1. **Play-test the new hardware.** Nothing below matters if a profile does not boot. Fastest proof:
   install something in a normal QEMU window, drop it in `disks/`, attach it, power on.
2. **OS catalogue**, starting as a manifest with no downloader. The profile field is the part that
   matters — an entry that only says "get it here" still configures the machine.
3. **Hard drive tiers**, then **sound and network bays**.
4. **Finish a Debian install** end to end — first real proof a persistent OS survives a power cycle.
5. **Give the 32-bit motherboard a job**, or cut it.
6. **On-screen keyboard.**
7. **Startup report** — host, architecture, accelerator, which profiles are viable here.
