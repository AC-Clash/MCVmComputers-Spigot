package com.acclash.vmcomputers.emu;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable description of one virtual machine, and the translation of it into a QEMU command line.
 *
 * <p>Defaults lean hard towards guest compatibility rather than raw speed, because the whole point
 * is that a player can boot an arbitrary ISO: {@code std} VGA and IDE disks work on everything from
 * DOS to current Linux, whereas virtio needs drivers the guest may not have. Faster options are
 * available for guests known to support them, which is what a per-OS profile in the image catalog
 * will eventually select.
 *
 * <p>The default resolution is 640x480 so it sits 1:1 inside a 6x4 grid of Minecraft maps
 * (768x512), letterboxed rather than scaled -- scaling is what actually destroys text legibility on
 * a map display. That grid also holds 720x400 VGA text mode 1:1, which is what guests use during
 * BIOS, bootloaders and installers.
 */
public final class VmSpec {

    /**
     * Guest CPU architecture.
     *
     * <p>Worth choosing deliberately: hardware virtualization only works when the guest matches the
     * host CPU. On Apple Silicon an aarch64 guest runs under HVF at native speed while an x86_64
     * guest is interpreted instruction by instruction, which is roughly two orders of magnitude
     * slower.
     */
    /**
     * What the guest is offered to make noise with.
     *
     * <p>Whichever is chosen, it feeds the same audiodev, so the VNC audio capture is unaffected --
     * this only decides whether the guest has a driver for what it finds.
     */
    public enum SoundCard {
        /** Intel HD Audio. PCI, so it works on q35 and ARM virt alike. Anything modern has drivers. */
        HDA("intel-hda", "hda-output,audiodev=snd0"),
        /** Creative Sound Blaster 16. ISA, so x86 only -- and the only thing DOS and Win9x know. */
        SB16("sb16,audiodev=snd0"),
        /**
         * Intel AC'97. PCI, and the card Windows 98SE through XP and 2.4/2.6 Linux have drivers
         * for in the box -- the era after Sound Blaster and before HD Audio.
         */
        AC97("AC97,audiodev=snd0"),
        NONE();

        private final String[] devices;

        SoundCard(String... devices) {
            this.devices = devices;
        }

        String[] devices() {
            return devices;
        }
    }

    public enum Architecture {
        X86_64("qemu-system-x86_64", "q35"),
        /** ARM64. Uses UEFI and virtio throughout -- no BIOS, no VGA, no IDE. */
        AARCH64("qemu-system-aarch64", "virt");

        private final String binaryName;
        private final String defaultMachine;

        Architecture(String binaryName, String defaultMachine) {
            this.binaryName = binaryName;
            this.defaultMachine = defaultMachine;
        }

        public String binaryName() {
            return binaryName;
        }

        public String defaultMachine() {
            return defaultMachine;
        }
    }

    /** Guest display adapter. */
    public enum Vga {
        /** Bochs/standard VGA. Works everywhere, supports EDID resolution hints. */
        STD("VGA", true),
        /** virtio-gpu. Fastest, but needs guest drivers. */
        VIRTIO("virtio-vga", true),
        /** For very old guests (Win9x era). No EDID. */
        CIRRUS("cirrus-vga", false),
        /** VMware SVGA II; some older guests have drivers for this. */
        VMWARE("vmware-svga", false);

        private final String deviceName;
        private final boolean supportsEdid;

        Vga(String deviceName, boolean supportsEdid) {
            this.deviceName = deviceName;
            this.supportsEdid = supportsEdid;
        }

        public String deviceName() {
            return deviceName;
        }

        public boolean supportsEdid() {
            return supportsEdid;
        }
    }

    /**
     * One attached disk: where it is, and what format QEMU should read it as.
     *
     * <p>The format travels with the path because not every disk is one this plugin made. A machine
     * can boot an image an admin installed elsewhere and copied in, and those arrive as raw,
     * {@code vmdk} or {@code vdi} at least as often as qcow2. Assuming qcow2 for those does not
     * fail cleanly -- QEMU reads the guest's boot sector as a qcow2 header and reports a corrupt
     * image, which looks nothing like "wrong format".
     */
    public static final class DiskImage {
        private final Path path;
        private final String format;

        public DiskImage(Path path, String format) {
            if (path == null) {
                throw new IllegalArgumentException("disk path is required");
            }
            if (format == null || format.isEmpty()) {
                throw new IllegalArgumentException("disk format is required for " + path);
            }
            this.path = path;
            this.format = format;
        }

        /** A disk in the format this plugin creates. */
        public static DiskImage qcow2(Path path) {
            return new DiskImage(path, "qcow2");
        }

        public Path path() {
            return path;
        }

        public String format() {
            return format;
        }

        @Override
        public String toString() {
            return path.getFileName() + " (" + format + ")";
        }
    }

    /** How disks attach to the guest. */
    public enum DiskInterface {
        /** AHCI/SATA on q35, plain IDE on the {@code pc} machine. Universally supported. */
        IDE("ide"),
        /** Needs virtio-blk drivers in the guest. */
        VIRTIO("virtio");

        private final String value;

        DiskInterface(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    private final String name;
    private final Architecture architecture;
    private final int memoryMb;
    private final int cpus;
    private final String machine;
    private final String accelerator;
    private final Vga vga;
    private final int width;
    private final int height;
    private final List<DiskImage> disks;
    private final DiskInterface diskInterface;
    private final Path cdrom;
    private final String bootOrder;
    private final boolean rtcLocaltime;
    private final boolean absolutePointer;
    private final boolean networking;
    private final String networkModel;
    private final String cpuModel;
    private final boolean audio;
    private final SoundCard soundCard;
    private final Path sharedFolder;
    private final Path uefiVars;
    private final List<String> extraArgs;

    private VmSpec(Builder b) {
        this.name = b.name;
        this.architecture = b.architecture;
        this.memoryMb = b.memoryMb;
        this.cpus = b.cpus;
        this.machine = b.machine != null ? b.machine : b.architecture.defaultMachine();
        this.accelerator = b.accelerator;
        this.vga = b.vga;
        this.width = b.width;
        this.height = b.height;
        this.disks = Collections.unmodifiableList(new ArrayList<DiskImage>(b.disks));
        this.diskInterface = b.diskInterface;
        this.cdrom = b.cdrom;
        this.bootOrder = b.bootOrder;
        this.rtcLocaltime = b.rtcLocaltime;
        this.absolutePointer = b.absolutePointer;
        this.networking = b.networking;
        this.networkModel = b.networkModel;
        this.cpuModel = b.cpuModel;
        this.audio = b.audio;
        this.soundCard = b.soundCard;
        this.sharedFolder = b.sharedFolder;
        this.uefiVars = b.uefiVars;
        this.extraArgs = Collections.unmodifiableList(new ArrayList<String>(b.extraArgs));
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public String name() {
        return name;
    }

    public Architecture architecture() {
        return architecture;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int memoryMb() {
        return memoryMb;
    }

    public int cpus() {
        return cpus;
    }

    public String accelerator() {
        return accelerator;
    }

    /**
     * Builds the full argument vector.
     *
     * <p>QMP and VNC both go over loopback TCP rather than Unix sockets. Unix sockets would be
     * marginally faster, but AF_UNIX support in QEMU's Windows builds is not something to bet
     * cross-platform behaviour on, and at these data rates the difference is unmeasurable.
     *
     * @param qemu       probed binaries
     * @param qmpPort    loopback port QEMU should listen on for QMP
     * @param vncDisplay VNC display number; the actual port is 5900 + this
     */
    public List<String> toArgv(QemuBinary qemu, int qmpPort, int vncDisplay) {
        String accel = accelerator != null ? accelerator : qemu.bestAccelerator();

        List<String> a = new ArrayList<String>();
        a.add(qemu.systemBinary().toString());
        a.add("-name");
        a.add(name);
        a.add("-machine");
        a.add(machine + ",accel=" + accel);
        a.add("-m");
        a.add(Integer.toString(memoryMb));
        a.add("-smp");
        a.add(Integer.toString(cpus));

        if (architecture == Architecture.AARCH64) {
            appendAarch64(a, qemu, accel);
        } else {
            appendX86(a);
        }

        // RFB PointerEvent carries absolute coordinates. Without an absolute input device QEMU has
        // to synthesise relative deltas from them and the guest cursor drifts out of sync with
        // where we think it is, which breaks aiming the pointer by looking at the monitor.
        if (absolutePointer) {
            a.add("-device");
            a.add("qemu-xhci,id=usb");
            a.add("-device");
            a.add("usb-tablet,bus=usb.0");
            if (architecture == Architecture.AARCH64) {
                // The virt machine has no PS/2 controller, so without this there is no keyboard.
                a.add("-device");
                a.add("usb-kbd,bus=usb.0");
            }
        }

        if (sharedFolder != null) {
            a.add("-drive");
            // snapshot=on rather than readonly=on, for two reasons. QEMU refuses to attach a
            // read-only IDE hard disk at all ("Block node is read-only"), and vvfat's writable
            // mode has a long history of eating the host directory behind it. A snapshot gives the
            // guest a disk it can write to freely -- DOS wants somewhere to save a config -- while
            // every write lands in a throwaway overlay and the folder on the host is never touched.
            a.add("file=fat:" + sharedFolder.toAbsolutePath()
                    + ",format=raw,if=ide,snapshot=on");
        }

        if (audio) {
            // The backend is "none": QEMU still mixes the guest's audio, it just has nowhere local
            // to play it. That is exactly what is wanted -- the samples are captured off the VNC
            // connection instead, and a server host has no business making noise of its own.
            //
            // intel-hda is a PCI device, so it works on q35 and on ARM virt alike, unlike the ISA
            // sound cards which have no bus to sit on in virt.
            a.add("-audiodev");
            a.add("none,id=snd0");
            for (String device : soundCard.devices()) {
                a.add("-device");
                a.add(device);
            }
        }

        if (networking) {
            // -nic rather than -netdev, because -netdev alone leaves QEMU's implicit default card
            // in place and the guest would come up with two.
            //
            // Which card matters more than it looks: a guest only has drivers for the cards that
            // existed when it shipped, so an e1000 in Windows 95 is an unknown PCI device and the
            // machine has no network at all. The profile picks the newest card the guest knows.
            a.add("-nic");
            a.add("user,model=" + (networkModel != null ? networkModel
                    : architecture == Architecture.AARCH64 ? "virtio-net-pci" : "e1000"));
        } else {
            a.add("-nic");
            a.add("none");
        }

        a.add("-vnc");
        // audiodev is what actually turns the audio extension on. QEMU only honours the audio
        // pseudo-encoding "if (vs->vd->audio_be)", which is set from this parameter and nothing
        // else -- so without it a client's request is refused with "Audio message N with audio
        // disabled" and the connection is dropped. Naming the sound card on -device is not enough.
        a.add("127.0.0.1:" + vncDisplay + (audio ? ",audiodev=snd0" : ""));
        a.add("-qmp");
        a.add("tcp:127.0.0.1:" + qmpPort + ",server=on,wait=off");
        a.add("-monitor");
        a.add("none");

        if (rtcLocaltime && architecture == Architecture.X86_64) {
            a.add("-rtc");
            a.add("base=localtime");
        }

        if (architecture == Architecture.AARCH64) {
            // Explicit devices rather than "if=virtio", so each one can carry a bootindex. See
            // the note on the cdrom below: without those the firmware picks for itself, and after
            // a while it picks the UEFI shell.
            int unit = 0;
            for (DiskImage disk : disks) {
                a.add("-drive");
                a.add("if=none,id=hd" + unit + ",format=" + disk.format()
                        + ",file=" + disk.path().toAbsolutePath());
                a.add("-device");
                a.add("virtio-blk-pci,drive=hd" + unit + ",bootindex=" + (unit + 1));
                unit++;
            }
        } else {
            for (DiskImage disk : disks) {
                a.add("-drive");
                a.add("file=" + disk.path().toAbsolutePath() + ",format=" + disk.format()
                        + ",if=" + diskInterface.value());
            }
        }

        if (cdrom != null) {
            if (architecture == Architecture.AARCH64) {
                // virt has no IDE, and UEFI boots happily from USB storage.
                //
                // bootindex is what makes it boot at all on the second run. ARM UEFI keeps its own
                // boot entries in the per-machine variable store, each pinned to an exact device
                // path, and it prefers them over looking for removable media. Change the hardware
                // -- which fitting or pulling a component now does -- and every remembered entry
                // points at something that is no longer there, so the firmware falls through them
                // all and starts the EFI shell instead. A machine that booted an ISO happily on
                // Monday sits at a Shell> prompt on Tuesday with the same ISO in the drive.
                //
                // bootindex is passed through fw_cfg and read before any of that, so the order is
                // ours rather than whatever the firmware last wrote down. Zero for the medium and
                // one for the disk, matching the "dc" order used on BIOS machines.
                //
                // Verified against a variable store that was actually failing this way: with the
                // same file it booted the shell without bootindex and the ISO with it.
                a.add("-drive");
                a.add("if=none,id=cd0,format=raw,readonly=on,media=cdrom,file="
                        + cdrom.toAbsolutePath());
                a.add("-device");
                a.add("usb-storage,bus=usb.0,drive=cd0,bootindex=0");
            } else {
                // An explicit drive rather than -cdrom, so the medium can be swapped over QMP.
                a.add("-drive");
                a.add("id=cd0,file=" + cdrom.toAbsolutePath() + ",media=cdrom,if="
                        + diskInterface.value());
            }
        }

        if (bootOrder != null && architecture == Architecture.X86_64) {
            // UEFI decides its own boot order; -boot order only applies to BIOS machines.
            a.add("-boot");
            a.add("order=" + bootOrder);
        }

        a.addAll(extraArgs);
        return a;
    }

    private void appendX86(List<String> a) {
        // Old guests need an old CPU, and not for authenticity. Windows 95 and 98 divide by the
        // CPU speed while probing it and fault outright on anything fast enough to overflow that
        // -- the "Windows protection error" that made Win9x unbootable on later hardware. Naming
        // a period CPU sidesteps it. Left unset, QEMU's default is right for anything modern.
        if (cpuModel != null) {
            a.add("-cpu");
            a.add(cpuModel);
        }
        // Suppress the implicit adapter so the explicit -device below is the only one.
        a.add("-vga");
        a.add("none");
        a.add("-device");
        a.add(vgaDeviceArg());
    }

    private void appendAarch64(List<String> a, QemuBinary qemu, String accel) {
        // There is no default CPU on virt, and hvf can only run the host's own core.
        a.add("-cpu");
        a.add("hvf".equals(accel) || "kvm".equals(accel) ? "host" : "max");

        // No BIOS on ARM: firmware is UEFI, supplied as a pair of pflash images. The code half is
        // read-only and shared; the variable half must be a private writable copy or boot entries
        // written by an installed system would be lost.
        Path code = qemu.firmware("edk2-aarch64-code.fd");
        if (code != null) {
            a.add("-drive");
            a.add("if=pflash,format=raw,readonly=on,file=" + code.toAbsolutePath());
            if (uefiVars != null) {
                a.add("-drive");
                a.add("if=pflash,format=raw,file=" + uefiVars.toAbsolutePath());
            }
        }

        a.add("-device");
        StringBuilder gpu = new StringBuilder("virtio-gpu-pci");
        gpu.append(",edid=on,xres=").append(width).append(",yres=").append(height);
        a.add(gpu.toString());
    }

    private String vgaDeviceArg() {
        StringBuilder sb = new StringBuilder(vga.deviceName());
        if (vga.supportsEdid()) {
            // EDID is how the guest is told which mode to prefer; without it most guests come up
            // at whatever their default is and we would have to scale.
            sb.append(",edid=on,xres=").append(width).append(",yres=").append(height);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "VmSpec{" + name + ", " + width + "x" + height + ", " + memoryMb + "MB, "
                + cpus + "cpu, " + machine + ", vga=" + vga + ", disks=" + disks.size()
                + (cdrom != null ? ", cdrom" : "") + "}";
    }

    /** Mutable builder. */
    public static final class Builder {
        private final String name;
        private int memoryMb = 2048;
        private int cpus = 2;
        private Architecture architecture = Architecture.X86_64;
        private String machine;
        private String accelerator;
        private Vga vga = Vga.STD;
        private int width = 640;
        private int height = 480;
        private final List<DiskImage> disks = new ArrayList<DiskImage>();
        private DiskInterface diskInterface = DiskInterface.IDE;
        private Path cdrom;
        private String bootOrder = "dc";
        private boolean rtcLocaltime = true;
        private boolean absolutePointer = true;
        private boolean audio = true;
        private SoundCard soundCard = SoundCard.HDA;
        private boolean networking = true;
        private String networkModel;
        private String cpuModel;
        private Path sharedFolder;
        private Path uefiVars;
        private final List<String> extraArgs = new ArrayList<String>();

        private Builder(String name) {
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("name is required");
            }
            this.name = name;
        }

        public Builder memoryMb(int mb) {
            if (mb < 16) {
                throw new IllegalArgumentException("memoryMb too small: " + mb);
            }
            this.memoryMb = mb;
            return this;
        }

        public Builder cpus(int n) {
            if (n < 1) {
                throw new IllegalArgumentException("cpus must be >= 1");
            }
            this.cpus = n;
            return this;
        }

        public Builder architecture(Architecture architecture) {
            this.architecture = architecture;
            return this;
        }

        /** Overrides the architecture default; {@code pc} for DOS and other very old x86 guests. */
        public Builder machine(String machine) {
            this.machine = machine;
            return this;
        }

        /** Overrides accelerator selection; normally leave unset and let the probe decide. */
        public Builder accelerator(String accel) {
            this.accelerator = accel;
            return this;
        }

        public Builder vga(Vga vga) {
            this.vga = vga;
            return this;
        }

        /** Guest resolution. Match this to the map grid so no scaling is needed. */
        public Builder resolution(int width, int height) {
            if (width < 64 || height < 64) {
                throw new IllegalArgumentException("resolution too small: " + width + "x" + height);
            }
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder addDisk(DiskImage disk) {
            this.disks.add(disk);
            return this;
        }

        public Builder diskInterface(DiskInterface di) {
            this.diskInterface = di;
            return this;
        }

        public Builder cdrom(Path iso) {
            this.cdrom = iso;
            return this;
        }

        /** QEMU boot order string, e.g. {@code "dc"} for disk-then-cdrom. */
        public Builder bootOrder(String order) {
            this.bootOrder = order;
            return this;
        }

        public Builder rtcLocaltime(boolean localtime) {
            this.rtcLocaltime = localtime;
            return this;
        }

        /**
         * Whether to attach a USB tablet for absolute pointing. Leave on for anything modern;
         * turn it off for pre-USB guests (DOS, Win9x), which need a relative PS/2 mouse instead.
         */
        /** Private writable copy of the UEFI variable store; aarch64 only. */
        public Builder uefiVars(Path varsFile) {
            this.uefiVars = varsFile;
            return this;
        }

        /**
         * Whether the guest gets a network card with NAT to the outside world.
         *
         * <p>Needed to install anything from network media, but worth an admin's attention: NAT
         * lets a guest reach whatever the host can reach, including other machines on the host's
         * LAN. On a shared server that is a way for a player's virtual machine to probe the
         * network around it.
         */
        public Builder networking(boolean enabled) {
            this.networking = enabled;
            return this;
        }

        /**
         * QEMU NIC model, e.g. {@code pcnet} or {@code rtl8139}. Null leaves the architecture
         * default, which is the right card for anything modern and the wrong one for old guests.
         */
        public Builder networkModel(String model) {
            this.networkModel = model;
            return this;
        }

        /**
         * QEMU x86 CPU model, e.g. {@code pentium3}. Null leaves QEMU's default. Only consulted on
         * x86 -- aarch64 picks between {@code host} and {@code max} from the accelerator instead.
         */
        public Builder cpuModel(String model) {
            this.cpuModel = model;
            return this;
        }

        /**
         * Which sound card the guest gets. HDA for anything modern; SB16 for guests old enough to
         * have no idea what HDA is, which on this project means anything running on x86.
         */
        public Builder soundCard(SoundCard soundCard) {
            this.soundCard = soundCard;
            return this;
        }

        /**
         * Whether the guest gets a sound card. On by default: it costs nothing when nobody is
         * listening, and a guest booted without one can never grow audio later without a restart.
         */
        public Builder audio(boolean audio) {
            this.audio = audio;
            return this;
        }

        /**
         * Presents a host folder to the guest as a read-only FAT disk, via QEMU's vvfat.
         *
         * <p>Read-only deliberately: vvfat's writable mode has a long history of corrupting the
         * host directory it is backed by, and handing a guest a way to eat files on the server is
         * not a trade worth making to let DOS save a config.
         */
        public Builder sharedFolder(Path folder) {
            this.sharedFolder = folder;
            return this;
        }

        public Builder absolutePointer(boolean absolute) {
            this.absolutePointer = absolute;
            return this;
        }

        /** Escape hatch for flags this class does not model. */
        public Builder extraArgs(String... args) {
            for (String s : args) {
                this.extraArgs.add(s);
            }
            return this;
        }

        public VmSpec build() {
            return new VmSpec(this);
        }
    }
}
