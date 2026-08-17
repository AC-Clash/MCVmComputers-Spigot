package com.acclash.vmcomputers.emu;

import java.util.ArrayList;
import java.util.List;

/**
 * The hardware an era of operating system expects to find.
 *
 * <p>Replaces the guess this used to make, which was that architecture stood in for age: x86 meant
 * old, aarch64 meant new. That was only ever true on a host which cannot accelerate x86 -- on an
 * ordinary Linux server it capped a modern Ubuntu guest at 256 MB and left it without a mouse.
 *
 * <p>Every field here exists because getting it wrong produces a failure that looks like something
 * else. A guest with the wrong graphics card boots to a desktop it can never change the resolution
 * of; one with the wrong NIC has no network and no error; one with the wrong sound card is simply
 * silent. None of them say what is wrong, and all of them are fixed by naming the era instead.
 *
 * <p>The named machines are not decoration. A profile called "Windows 98" invites the question of
 * which Windows 98 machine, and a real model answers every field at once, because the answer is
 * whatever that box actually shipped with.
 */
public enum GuestProfile {

    /**
     * Work it out from the architecture and whether this host can accelerate it.
     *
     * <p>Never reaches QEMU: {@link #resolve} turns it into one of the others first. It is the
     * default so that machines built before profiles existed keep behaving as they did, and so a
     * player who has never heard of any of this still gets something that boots.
     */
    AUTO("Automatic", null,
            "Picks a profile from the architecture and this host's accelerator."),

    /** MS-DOS, FreeDOS. No USB, no PCI to speak of, and 64 MB is more than it can address. */
    DOS("MS-DOS / FreeDOS", VmSpec.Architecture.X86_64,
            "pc", VmSpec.Vga.CIRRUS, VmSpec.DiskInterface.IDE, false,
            VmSpec.SoundCard.SB16, "ne2k_isa", "pentium2", 64, true,
            "ISA everything. DOS cannot use more than 64 MB and has no USB."),

    /** Windows 95 and 98 generally. For a specific machine see the named models below. */
    WIN9X("Windows 95 / 98", VmSpec.Architecture.X86_64,
            "pc", VmSpec.Vga.CIRRUS, VmSpec.DiskInterface.IDE, false,
            VmSpec.SoundCard.SB16, "pcnet", "pentium2", 512, true,
            "Cirrus is the only card Win9x has drivers for in the box."),

    /**
     * A Dell Dimension L500r, as sold with Windows 98.
     *
     * <p>A slimline Pentium III 500 from 2000, and about as ordinary a Windows 98 machine as
     * existed. The parts here are the ones that make Windows 98 work rather than strictly the ones
     * Dell fitted: the real board had Intel 810e graphics and AC'97 audio, neither of which
     * Windows 98 can drive without the driver CD. Cirrus and Sound Blaster it detects by itself,
     * which is what matters when the driver CD is not a thing you can insert here.
     */
    DELL_DIMENSION_L500R("Dell Dimension L500r", VmSpec.Architecture.X86_64,
            "pc", VmSpec.Vga.CIRRUS, VmSpec.DiskInterface.IDE, false,
            VmSpec.SoundCard.SB16, "pcnet", "pentium3", 512, true,
            "Pentium III 500. Built for Windows 98 and boots it with no driver disk."),

    /** Windows 2000 and 32-bit XP. Old enough to want IDE, new enough to have USB. */
    WINXP("Windows 2000 / XP", VmSpec.Architecture.X86_64,
            "pc", VmSpec.Vga.VMWARE, VmSpec.DiskInterface.IDE, true,
            VmSpec.SoundCard.AC97, "rtl8139", null, 2048, false,
            "USB works, so the pointer behaves. AC'97 is the card of this era."),

    /**
     * A Compaq Presario, as sold with Windows XP Professional x64 Edition.
     *
     * <p>An Athlon 64 desktop from the mid 2000s -- the generation that made a 64-bit Windows
     * plausible on a machine somebody bought in a shop. Two things separate it from ordinary XP.
     * The board stays {@code pc} with IDE disks because XP of any width has no AHCI driver in the
     * box and would stop at a blue screen on {@code q35}; and the graphics drop to plain VGA
     * because the Cirrus driver Windows ships is 32-bit only, so the x64 edition cannot load it.
     */
    COMPAQ_PRESARIO("Compaq Presario (XP x64)", VmSpec.Architecture.X86_64,
            "pc", VmSpec.Vga.STD, VmSpec.DiskInterface.IDE, true,
            VmSpec.SoundCard.AC97, "rtl8139", null, 4096, false, true,
            "Athlon 64. Set up for Windows XP Professional x64 Edition. Needs a 64-bit board."),

    /** Linux from the 2.4 and 2.6 days: PCI, no virtio, and happy with plain VGA. */
    LINUX_LEGACY("Linux 2.4 / 2.6", VmSpec.Architecture.X86_64,
            "pc", VmSpec.Vga.STD, VmSpec.DiskInterface.IDE, true,
            VmSpec.SoundCard.AC97, "e1000", null, 512, false,
            "Old enough to predate virtio, new enough for USB."),

    /** Anything current on x86, with the virtio devices its kernel already has drivers for. */
    MODERN_LINUX("Modern Linux (x86)", VmSpec.Architecture.X86_64,
            "q35", VmSpec.Vga.VIRTIO, VmSpec.DiskInterface.VIRTIO, true,
            VmSpec.SoundCard.HDA, "virtio-net-pci", null, 0, false, true,
            "virtio throughout: the fast path, and every current kernel has it."),

    /**
     * Windows 10 and 11.
     *
     * <p>Same era as {@link #MODERN_LINUX} and deliberately not the same hardware. Windows has no
     * virtio drivers in the box, so a virtio disk is a machine that boots the installer and then
     * reports no drive to install onto.
     */
    MODERN_WINDOWS("Windows 10 / 11", VmSpec.Architecture.X86_64,
            "q35", VmSpec.Vga.STD, VmSpec.DiskInterface.IDE, true,
            VmSpec.SoundCard.HDA, "e1000", null, 0, false, true,
            "Like modern Linux but without virtio, which Windows cannot see."),

    /** Anything current on ARM. There is no legacy ARM -- nothing old runs on {@code virt}. */
    MODERN_ARM("Modern ARM", VmSpec.Architecture.AARCH64,
            "virt", VmSpec.Vga.VIRTIO, VmSpec.DiskInterface.VIRTIO, true,
            VmSpec.SoundCard.HDA, "virtio-net-pci", null, 0, false,
            "UEFI and virtio. The only kind of ARM guest there is.");

    private final String label;
    private final VmSpec.Architecture architecture;
    private final String machine;
    private final VmSpec.Vga vga;
    private final VmSpec.DiskInterface diskInterface;
    private final boolean absolutePointer;
    private final VmSpec.SoundCard soundCard;
    private final String networkModel;
    private final String cpuModel;
    private final int maxMemoryMb;
    private final boolean sharedFolder;
    private final boolean needs64Bit;
    private final String description;

    GuestProfile(String label, VmSpec.Architecture architecture, String description) {
        this(label, architecture, null, null, null, true, null, null, null, 0, false, false,
                description);
    }

    GuestProfile(String label, VmSpec.Architecture architecture, String machine, VmSpec.Vga vga,
                 VmSpec.DiskInterface diskInterface, boolean absolutePointer,
                 VmSpec.SoundCard soundCard, String networkModel, String cpuModel,
                 int maxMemoryMb, boolean sharedFolder, String description) {
        this(label, architecture, machine, vga, diskInterface, absolutePointer, soundCard,
                networkModel, cpuModel, maxMemoryMb, sharedFolder, false, description);
    }

    GuestProfile(String label, VmSpec.Architecture architecture, String machine, VmSpec.Vga vga,
                 VmSpec.DiskInterface diskInterface, boolean absolutePointer,
                 VmSpec.SoundCard soundCard, String networkModel, String cpuModel,
                 int maxMemoryMb, boolean sharedFolder, boolean needs64Bit, String description) {
        this.needs64Bit = needs64Bit;
        this.label = label;
        this.architecture = architecture;
        this.machine = machine;
        this.vga = vga;
        this.diskInterface = diskInterface;
        this.absolutePointer = absolutePointer;
        this.soundCard = soundCard;
        this.networkModel = networkModel;
        this.cpuModel = cpuModel;
        this.maxMemoryMb = maxMemoryMb;
        this.sharedFolder = sharedFolder;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    /**
     * The architecture a machine gets when this profile is chosen, or null if it applies to any.
     *
     * <p>Always the 64-bit member of the family. Which width a machine actually runs at is decided
     * by the motherboard fitted to it, not here -- so a Windows 98 profile hands out x86_64 and
     * becomes i386 the moment somebody fits a 32-bit board.
     */
    public VmSpec.Architecture architecture() {
        return architecture;
    }

    /**
     * Whether this guest can run on an architecture at all.
     *
     * <p>Wider than {@link #architecture()} because 32-bit guests run happily on either width, and
     * a 32-bit board is a real thing a player can fit. Windows 98 does not care that the emulator
     * is 64-bit capable; Windows XP x64 very much does.
     */
    public boolean runsOn(VmSpec.Architecture arch) {
        if (architecture == null) {
            return true;
        }
        if (architecture == arch) {
            return true;
        }
        // A 32-bit era guest is equally at home on the 32-bit emulator.
        return arch == VmSpec.Architecture.I386
                && architecture == VmSpec.Architecture.X86_64
                && !needs64Bit;
    }

    /** Whether this guest is 64-bit and so cannot run on a 32-bit board. */
    public boolean needs64Bit() {
        return needs64Bit;
    }

    /** Most memory this guest can actually use, or 0 for no ceiling. */
    public int maxMemoryMb() {
        return maxMemoryMb;
    }

    /** Whether this guest is old enough to need files handed to it on a fake disk. */
    public boolean wantsSharedFolder() {
        return sharedFolder;
    }

    /** Whether this guest has USB, and so can use an absolute pointer. */
    public boolean hasAbsolutePointer() {
        return absolutePointer;
    }

    /** QEMU machine type, or null on {@link #AUTO}. */
    public String machine() {
        return machine;
    }

    /** The card this guest has drivers for, or null on {@link #AUTO}. */
    public VmSpec.SoundCard soundCard() {
        return soundCard;
    }

    /** The graphics adapter this guest can actually drive, or null on {@link #AUTO}. */
    public VmSpec.Vga vga() {
        return vga;
    }

    /**
     * Turns {@link #AUTO} into a real profile; every other value is returned unchanged.
     *
     * @param accelerated whether this host can run this architecture at native speed
     */
    public GuestProfile resolve(VmSpec.Architecture arch, boolean accelerated) {
        if (this != AUTO) {
            return this;
        }
        if (arch == VmSpec.Architecture.AARCH64) {
            return MODERN_ARM;
        }
        // A 32-bit board is only ever fitted on purpose, and nothing modern is 32-bit any more,
        // so it says "old guest" far more clearly than the accelerator ever did.
        if (arch == VmSpec.Architecture.I386) {
            return WIN9X;
        }
        // The old guess, kept but narrowed to the case where it was actually reasoning about
        // something. A host that cannot accelerate x86 is a host where nobody runs a modern x86
        // guest on purpose, because it would be unusably slow -- so x86 here really does mean
        // something old. Where x86 *is* accelerated, which is most servers, it means the opposite.
        return accelerated ? MODERN_LINUX : WIN9X;
    }

    /**
     * Applies this profile's hardware to a builder.
     *
     * <p>Called before any component-derived setting, so a part a player deliberately fitted wins
     * over the era's default.
     */
    public void applyTo(VmSpec.Builder builder) {
        if (machine != null) {
            builder.machine(machine);
        }
        if (vga != null) {
            builder.vga(vga);
        }
        if (diskInterface != null) {
            builder.diskInterface(diskInterface);
        }
        if (soundCard != null) {
            builder.soundCard(soundCard);
        }
        if (networkModel != null) {
            builder.networkModel(networkModel);
        }
        if (cpuModel != null) {
            builder.cpuModel(cpuModel);
        }
        builder.absolutePointer(absolutePointer);
    }

    /** Caps requested memory at what this guest can survive. */
    public int clampMemory(int requestedMb) {
        return maxMemoryMb > 0 ? Math.min(requestedMb, maxMemoryMb) : requestedMb;
    }

    /** Profiles that can run on an architecture, {@link #AUTO} first. */
    public static List<GuestProfile> forArchitecture(VmSpec.Architecture arch) {
        List<GuestProfile> out = new ArrayList<GuestProfile>();
        for (GuestProfile profile : values()) {
            if (profile.runsOn(arch)) {
                out.add(profile);
            }
        }
        return out;
    }

    /** Parses a name from chat or the database, or null if it is not one of these. */
    public static GuestProfile parse(String name) {
        if (name == null) {
            return null;
        }
        for (GuestProfile profile : values()) {
            if (profile.name().equalsIgnoreCase(name)) {
                return profile;
            }
        }
        return null;
    }
}
