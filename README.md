# VM Computers

Run real virtual machines inside vanilla Minecraft.

QEMU runs as a separate process on your server, and the plugin paints its screen onto a wall of
item-framed maps. Players sit at a desk, look at the screen to aim, and click. A machine is built
from ordered parts, boots from an ISO you drop in a folder, and keeps its disk across restarts.

**Every player joins with a vanilla client.** No resource pack, no client mod, nothing installed by
the people playing. That constraint is the point of the project.

> VM Computers is alpha software. Expect rough edges, and don't run it on a server you can't afford
> to have misbehave. A virtual machine is a real process on your host — read [Limits](#limits) and
> [Guest networking](#guest-networking) before opening it to the public.

---

## Contents

- [Requirements](#requirements)
- [1. Install QEMU](#1-install-qemu)
- [2. Turn on hardware acceleration](#2-turn-on-hardware-acceleration)
- [3. Install the plugin](#3-install-the-plugin)
- [4. Configure](#4-configure)
- [What it creates on disk](#what-it-creates-on-disk)
- [Commands](#commands)
- [Permissions](#permissions)
- [When it doesn't work](#when-it-doesnt-work)
- [Building from source](#building-from-source)

---

## Requirements

| Requirement | Detail |
|---|---|
| A Spigot or Paper server | Built against the Spigot API only, so either works. The plugin declares `api-version: 26.2`. |
| Java 21 or newer | The plugin is compiled to Java 21 bytecode. Your server version's own requirement is higher, and it decides — the plugin never raises the bar. |
| QEMU, on `PATH` | Both `qemu-system-*` and `qemu-img`. On some distributions these are two separate packages. |
| Disk space | Each computer with a hard drive fitted gets a 16 GB image, though qcow2 is sparse and only grows as the guest writes. ISOs are the real cost — a desktop Linux is around 4 GB. |
| A spare TCP port | Default `25566`, for guest audio. Only needed if you want sound. |

---

## 1. Install QEMU

The plugin finds QEMU by searching `PATH`, and nothing else. If you install it somewhere unusual,
put that directory on the server user's `PATH` — there is currently no config setting for an
explicit path, whatever the error message suggests.

| Platform | Command | Watch out for |
|---|---|---|
| macOS | `brew install qemu` | Nothing. One package includes every architecture, `qemu-img`, and the ARM firmware. |
| Debian / Ubuntu | `apt install qemu-system-x86 qemu-system-arm qemu-utils` | **`qemu-img` lives in `qemu-utils`**, a separate package. Without it the plugin finds QEMU and still refuses to start. |
| Fedora / RHEL | `dnf install qemu-system-x86 qemu-system-aarch64 qemu-img` | Also a separate `qemu-img`. |
| Arch | `pacman -S qemu-full` | `qemu-base` omits some architectures; `qemu-full` is the safe choice. |
| Alpine | `apk add qemu-system-x86_64 qemu-img` | Add `qemu-system-aarch64` too if you want ARM guests. |
| Windows | [qemu.weilnetz.de/w64](https://qemu.weilnetz.de/w64/) | **The installer does not add QEMU to `PATH`.** You must add `C:\Program Files\qemu` yourself. |

### Adding QEMU to PATH on Windows

Settings → System → About → Advanced system settings → Environment Variables → under *System
variables* select `Path` → Edit → New → `C:\Program Files\qemu`. Then **close and reopen the
terminal**, or the change won't apply to your server's window.

### Check it took

Both of these must print a version:

```bash
qemu-system-x86_64 --version
```

```bash
qemu-img --version
```

You only need the architectures you'll actually use. A guest is hardware-accelerated only when its
architecture matches the host CPU, and the plugin looks for each emulator binary lazily — the first
time a computer set to that architecture is powered on.

---

## 2. Turn on hardware acceleration

This is the step that decides whether guests run at full speed or roughly a hundred times slower.
QEMU works either way, which is exactly the problem: **if acceleration isn't available it falls back
to software emulation**, and the only symptom is that everything is painfully slow.

| Host | Accelerator | Setup required |
|---|---|---|
| macOS (Intel or Apple Silicon) | HVF | None. Built into macOS and enabled by default. |
| Linux | KVM | Virtualisation on in BIOS, and the server user must have access to `/dev/kvm`. |
| Windows | WHPX | Virtualisation on in BIOS, plus the *Windows Hypervisor Platform* feature. |
| Windows on ARM | none | WHPX does not cover ARM guests. Everything is emulated; old guests still work fine, modern ones will not. |

### Linux: give the server access to /dev/kvm

The single most common misconfiguration. The device exists but the user running the Minecraft server
can't open it, so QEMU quietly uses software emulation.

Does the device exist at all? Nothing here means virtualisation is off in BIOS/UEFI, or you are
inside a VM that doesn't allow nested virtualisation — common on cheap VPS hosts.

```bash
ls -l /dev/kvm
```

Can the server user use it? Group changes don't apply to existing sessions, so log out and back in
afterwards.

```bash
sudo usermod -aG kvm $USER
```

Then confirm — `kvm` must appear in the list:

```bash
qemu-system-x86_64 -accel help
```

### Windows: enable the Hypervisor Platform

1. Turn on virtualisation in your BIOS/UEFI — Intel calls it **VT-x**, AMD calls it **SVM** or
   **AMD-V**.
2. Start → "Turn Windows features on or off" → tick **Windows Hypervisor Platform**. This is its own
   entry, separate from Hyper-V; ticking Hyper-V alone is not enough.
3. Reboot, then check that `qemu-system-x86_64 -accel help` lists `whpx`.

`-accel help` is exactly what the plugin runs at startup to decide. If `kvm`, `hvf` or `whpx`
appears in that list, you're set. If only `tcg` appears, acceleration is off and no amount of plugin
configuration will change it.

---

## 3. Install the plugin

**1. Drop the jar in `plugins/`.** Build it with `./gradlew build` and take
`build/libs/VMComputers-0.1.0.jar`, or use a release jar.

**2. Start the server.** First start creates `plugins/vm_computers/` and its folders, and writes the
default `config.yml`.

**3. Read the startup lines.** The plugin reports what it loaded:

```
[VMComputers] Map palette LUT built in 103ms (244 usable colours).
[VMComputers] Loaded 28 part models (131 display pieces).
[VMComputers] Loaded 1 computer(s), 1 screen(s) reattached, 0 case(s) awaiting parts.
```

Nothing there mentions QEMU. QEMU is only located when a computer is first switched on, so a clean
startup does not yet prove it works.

**4. Prove QEMU is reachable.** Power on a computer in game and watch the console. A working boot
logs the machine it is starting; a broken one names exactly what is missing.

```
# good
[VMComputers] Computer #1 booting with 2048 MB and 2 core(s).
[VMComputers] Starting VM 1: VmSpec{vmcomputer-1, 640x480, ...}

# QEMU missing
Could not start: qemu-system-x86_64 was not found on PATH...

# acceleration missing — boots, but slowly
No hardware acceleration for X86_64 on this host ([tcg]); the guest will be very slow.
```

---

## 4. Configure

`plugins/vm_computers/config.yml` has three sections: `audio`, `limits` and `guest`. Everything else
about a machine is decided in game, by what parts are fitted to it.

### Audio

Guest sound cannot go through Minecraft — its sound packet carries a sound *name*, not samples — so
the plugin serves audio over HTTP and players open a link in a browser. Nothing streams until
someone asks for a link with `/vmcomputers audio`.

| Setting | Default | When to change it |
|---|---|---|
| `audio.enabled` | `true` | Set false to skip the HTTP server entirely. |
| `audio.port` | `25566` | If something else holds the port. Must be open in the firewall and forwarded the same way your Minecraft port is. |
| `audio.public-address` | empty | **Set this on any server players connect to remotely.** Empty falls back to `server-ip`, then localhost — which only works for someone on the same machine. |
| `audio.sample-rate` | `44100` | Lower costs less bandwidth and sounds worse. QEMU refuses anything above 48000. |
| `audio.buffer-millis` | `2000` | How far a stalling browser may fall behind before it is snapped back to live. |

The audio port is a second door into your server. It is a plain HTTP server on your host; links
handed out in game carry a per-player token and expire, but the port itself is open to anyone who
can reach it. Forward it deliberately, and leave `audio.enabled` off if you don't want sound.

### Limits

Each running computer is a real QEMU process holding however much memory its RAM component says, so
ten players each booting a desktop is ten desktops' worth of RAM on this host. There is no queue:
once the cap is reached, the next power-on is refused with a message naming the limit.

| Setting | Default | Meaning |
|---|---|---|
| `limits.max-running` | `4` | Most virtual machines allowed to run at once across the whole server. Raise it if the host can take it. |
| `limits.max-per-player` | `2` | Most one player may have running at a time. `0` disables the per-player limit and leaves only the server-wide one. |

### Guest networking

| Setting | Default | Meaning |
|---|---|---|
| `guest.networking` | `true` | Whether guests get a network card at all. |

On is what makes network installs work — a Debian netinst image is useless without it, and so is
anything a player wants to download inside the guest. It also means a player's virtual machine can
reach whatever this server can reach, **including other machines on the host's LAN and anything
listening on localhost**. That is user-mode NAT working as designed; there is no setting that grants
the internet while withholding the LAN.

Turn it off on a public server unless you have a reason not to, and expect network installers to
stop working when you do.

---

## What it creates on disk

```
plugins/vm_computers/
├── config.yml
├── hardware.db          // SQLite: computers, panels, fitted parts
├── isos/                // put installer and live images here
├── disks/               // put existing disk images here, to boot systems already installed
├── hdds/                // one qcow2 per computer, plus ARM firmware state
└── shared/              // files handed to guests too old to have networking
```

Drop `.iso` files into `isos/` and they appear in game — no restart needed. `/vmcomputers iso` lists
what it can see.

`disks/` is the way to run a guest that is painful to install through a wall of maps. Install it once
in a normal QEMU window — Windows 95 is the obvious case — and copy the image in. `qcow2`, `img`,
`raw`, `vmdk`, `vdi`, `vhd`, `vhdx` and `qed` are all read. `/vmcomputers disk <id> <file>` points a
computer at one, and from then on that machine boots the supplied image instead of its own.

**Images in `disks/` are yours, and the plugin only ever opens them.** It never creates, resizes or
deletes one, and `/vmcomputers remove` does not touch them. But the guest writes straight into the
file, so anything the guest does to that disk is permanent — keep a copy of anything you cannot
rebuild. Attaching one is admin-only for this reason, where inserting an ISO is not: a CD cannot be
harmed by the guest that boots it.

This lives outside the plugin's own data folder on purpose: disk images are large and long-lived, so
reinstalling the plugin must not delete anyone's virtual machines.

**Back up `hardware.db` and `hdds/` together**, plus `disks/` if you use it. The database knows where
each computer is and what is fitted; the disk images are the guests' actual installed systems. One
without the other is not a working backup.

---

## Commands

All are subcommands of `/vmcomputers`, aliased `/vmc` and `/computer`.

| Command | Does |
|---|---|
| `/vmcomputers create <SMALL\|MEDIUM\|LARGE\|XLARGE> [x86_64\|aarch64]` | Creates a computer outright, skipping the shop. |
| `/vmcomputers remove [id]` | Removes a computer and its disk. |
| `/vmcomputers iso [<id> <file\|none>]` | Lists available ISOs, or inserts and ejects one. |
| `/vmcomputers disk [<id> <file\|none>]` | Lists supplied disk images, or boots a computer from one. Admin-only. |
| `/vmcomputers profile [<id> <name>]` | Sets which era of guest hardware a computer has. See below. |
| `/vmcomputers type <text>` | Types into the guest. Supports `@RETURN @TAB @ESC @UP`..`@F12`. |
| `/vmcomputers keys [game\|menu\|bind <input> <key>\|reset]` | Rebinds the chair's keys. |
| `/vmcomputers audio [id]` | Private link to the guest's sound. |
| `/vmcomputers order` / `phone` | The parts shop. Paid in Auros, 3 paper each. |
| `/vmcomputers parts <list\|model\|clear>` | Renders part models for inspection. |
| `/vmcomputers testdisplay` | Palette and screen test. |
| `/vmcomputers debug` | Draws the pointer on screen. |

The pointer is tracked but deliberately never drawn — Minecraft's own crosshair is already exactly
where it should be, and painting the framebuffer on every head movement costs frames. `debug` exists
because "invisible and working" looks identical to "invisible and broken". Leave it off.

## Guest profiles

An operating system only has drivers for hardware that existed when it shipped, so the machine has
to be built for the guest. A profile picks the board, graphics, disk interface, pointer, sound card,
network card, CPU and memory ceiling in one go.

This matters more than it sounds. The wrong graphics card is a desktop stuck at 640×480 forever; the
wrong NIC is no network and no error message; the wrong sound card is silence. None of them announce
themselves — they just look like the guest is broken.

| Profile | For | Notably |
|---|---|---|
| `AUTO` | default | Works it out from the architecture and whether this host accelerates it. |
| `DOS` | MS-DOS, FreeDOS | ISA everything, 64 MB ceiling, PS/2 mouse. |
| `WIN9X` | Windows 95 / 98 | Cirrus graphics, Sound Blaster, 512 MB ceiling. |
| `DELL_DIMENSION_L500R` | Windows 98 | A Pentium III 500 as sold in 2000. Boots 98 with no driver disk. |
| `WINXP` | Windows 2000 / XP | USB pointer, AC'97 sound. |
| `COMPAQ_PRESARIO` | Windows XP x64 Edition | Athlon 64 desktop. Plain VGA, because the Cirrus driver is 32-bit only. |
| `LINUX_LEGACY` | Linux 2.4 / 2.6 | Predates virtio, has USB. |
| `MODERN_LINUX` | current Linux on x86 | virtio throughout — the fast path. |
| `MODERN_WINDOWS` | Windows 10 / 11 | Like the above without virtio, which Windows cannot see. |
| `MODERN_ARM` | anything on ARM | UEFI and virtio. The only kind of ARM guest there is. |

The graphics bay overrides the profile's choice, so a player who buys a **Cirrus Logic Card**, **VGA
Card**, **SVGA Card** or **Virtio GPU** gets that adapter. The plain **Graphics Card** names nothing
and leaves the decision to the profile — which is what every machine built before the tiers existed
has fitted, so none of them change. ARM machines ignore the bay: virtio is the only adapter their
firmware and guests can use, and fitting anything else says so rather than going black.

`AUTO` is what every existing machine has, and it keeps them working. It reads the architecture
together with the accelerator: on a host that runs x86 natively an x86 guest is assumed modern, and
on one that cannot — Apple Silicon, say — x86 is assumed to mean something old, because nobody runs
a modern x86 guest at emulated speed on purpose.

Two of them can also be **bought as cases**. A Dell Dimension L500r and a Compaq Presario are placed
like the plain PC Case, but the machine assembled inside one starts on that profile already — which
is the point: buying a Dimension is how a player says "Windows 98" without having to know what
Windows 98 wants. Each has its own model, so they look like what they are on the floor.

The two named machines are real models. Naming a profile "Windows 98" invites the question of *which*
Windows 98 machine, and a real one answers every field at once. Where the model and compatibility
disagree, compatibility wins: the real L500r had Intel 810e graphics and AC'97 audio, neither of
which Windows 98 drives without the driver CD, so it gets Cirrus and a Sound Blaster — the two things
98 detects by itself.

## Permissions

Three tiers, split by what a player can lose rather than by what they can touch.

| Node | Default | Grants |
|---|---|---|
| `vmcomputers.use` | everyone | Sit at computers, power them on and off, click and type. Covers `iso`, `type`, `keys`, `audio`. |
| `vmcomputers.build` | everyone | Place cases, assemble computers, order parts. Covers `order`, `phone`, `remove`. |
| `vmcomputers.admin` | op | Create and remove any computer, and modify machines owned by others. Covers `create`, `disk`, `parts`, `testdisplay`, `debug`, and implies the other two. |

Anyone may *use* a machine. Only its owner may take it apart or change what is in it, because that
is what destroys someone else's work.

---

## When it doesn't work

| Symptom | Cause | Fix |
|---|---|---|
| "was not found on PATH" | QEMU isn't installed, or isn't on the server user's PATH. | See [Install QEMU](#1-install-qemu). On Windows this is almost always the missing PATH entry, not a missing install. |
| "qemu-img is not on PATH" | QEMU installed without its tools package. | Install `qemu-utils` (Debian/Ubuntu) or `qemu-img` (Fedora). |
| Everything is unbearably slow | Software emulation. Either acceleration is off, or the guest's architecture doesn't match the host. | Check `-accel help`. If the accelerator is there, the guest is simply a foreign architecture — expected, and fine for old guests. |
| Guest sits at a UEFI shell | ARM firmware couldn't find anything bootable. | Check an ISO is selected and that it is an ARM image — an x86 ISO in an ARM machine looks exactly like this. |
| Audio link never connects | Port closed, or `public-address` unset. | See [Audio](#audio). From another machine, try the link's host and port directly. |
| Screen stays black after power on | Guest still in firmware, or booting slowly under emulation. | Give it time, especially unaccelerated. Some installers wait on a keypress at a menu, which looks identical to frozen. |
| Power-on refused with a limit message | The server-wide or per-player cap is reached. | See [Limits](#limits). |
| `NoClassDefFoundError` | The jar was rebuilt while the server was running. | Stop the server, rebuild, start. Classes are read from the jar lazily, so replacing it underneath a live server breaks it. |

---

## Building from source

```bash
./gradlew build
```

The jar lands in `build/libs/` as `VMComputers-<version>.jar`. The build provisions its own JDK, so
a clean clone compiles without a matching local one installed.

```bash
./gradlew test        # pure-logic tests, no server needed
./gradlew runServer   # downloads and runs Paper into run/
```

Rebuilding the jar under a running server causes `NoClassDefFoundError`. The loop is stop → rebuild
→ start.

Prebuilt jars are in the releases tab, though they won't always match the latest commit.

---

## Licence

GNU General Public License v3 — see [LICENSE](LICENSE).

(c) 2021–2026 Anston Sorensen
