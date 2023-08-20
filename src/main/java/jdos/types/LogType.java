package jdos.types;

public enum LogType {
    LOG_ALL("ALL"),
    LOG_VGA("VGA"),
    LOG_VGAGFX("VGA-GFX"),
    LOG_VGAMISC("VGA-MISC"),
    LOG_INT10("INT10"),
    LOG_SB("SBLASTER"),
    LOG_DMACONTROL("DMA-CONTROL"),
    LOG_FPU("FPU"),
    LOG_CPU("CPU"),
    LOG_PAGING("PAGING"),
    LOG_FCB("FCB"),
    LOG_FILES("FILES"),
    LOG_IOCTL("IOCTL"),
    LOG_EXEC("EXEC"),
    LOG_DOSMISC("DOS-MISC"),
    LOG_PIT("PIT"),
    LOG_KEYBOARD("KEYBOARD"),
    LOG_PIC("PIC"),
    LOG_MOUSE("MOUSE"),
    LOG_BIOS("BIOS"),
    LOG_GUI("GUI"),
    LOG_MISC("MISC"),
    LOG_IO("IO"),
    LOG_PCI("PCI"),
    LOG_FLOPPY("FLOPPY"),
    LOG_MAX("MAX");

    private final String name;

    LogType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}