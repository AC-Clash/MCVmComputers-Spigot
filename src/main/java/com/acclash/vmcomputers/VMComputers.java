package com.acclash.vmcomputers;

import com.acclash.vmcomputers.commands.ComputerCM;
import com.acclash.vmcomputers.computer.Computer;
import com.acclash.vmcomputers.computer.ComputerRegistry;
import com.acclash.vmcomputers.computer.PendingCase;
import com.acclash.vmcomputers.display.MapColorLut;
import com.acclash.vmcomputers.gui.MenuListener;
import com.acclash.vmcomputers.parts.BrickPhone;
import com.acclash.vmcomputers.parts.ComponentSlot;
import com.acclash.vmcomputers.parts.ComponentType;
import com.acclash.vmcomputers.parts.PartModels;
import com.acclash.vmcomputers.display.MonitorScreen;
import com.acclash.vmcomputers.display.ScreenPump;
import com.acclash.vmcomputers.listeners.ClickListener;
import com.acclash.vmcomputers.listeners.OrderingListener;
import com.acclash.vmcomputers.listeners.PlacementListener;
import com.acclash.vmcomputers.listeners.PlayerListener;
import com.acclash.vmcomputers.listeners.PointerListener;
import com.acclash.vmcomputers.listeners.PreventionListener;
import com.acclash.vmcomputers.sql.ComputerDao;
import com.acclash.vmcomputers.sql.Database;
import com.acclash.vmcomputers.sql.SQLite;
import com.acclash.vmcomputers.utils.ComputerFunctions;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;

public final class VMComputers extends JavaPlugin {

    static VMComputers plugin;

    public static VMComputers getPlugin() {
        return plugin;
    }

    private Database db;
    private ComputerDao computerDao;
    private final ComputerRegistry registry = new ComputerRegistry();
    private MapColorLut mapPalette;
    private PointerListener pointerListener;
    private ScreenPump screenPump;
    /** Read from the server tick and written from a command; both are the main thread, but be safe. */
    private volatile boolean pointerDebug;

    /** Shared with the keyboard command, which targets whatever screen a player is aiming at. */
    public PointerListener getPointerListener() {
        return pointerListener;
    }
    private final java.util.Map<Integer, MonitorScreen> screens =
            new java.util.concurrent.ConcurrentHashMap<Integer, MonitorScreen>();

    /** The live screen for a computer, or null if its maps could not be resolved. */
    public MonitorScreen getScreen(int computerId) {
        return screens.get(Integer.valueOf(computerId));
    }

    public void registerScreen(MonitorScreen screen) {
        screens.put(Integer.valueOf(screen.computer().id()), screen);
    }

    public void unregisterScreen(int computerId) {
        screens.remove(Integer.valueOf(computerId));
        if (screenPump != null) {
            screenPump.forget(computerId);
        }
    }

    /** Every live screen. Only the debug cursor needs this; everything else works one screen at a time. */
    public java.util.Collection<MonitorScreen> screens() {
        return screens.values();
    }

    /**
     * Whether to draw the pointer onto screens. Off by default and meant to stay off: the drawn
     * arrow costs map traffic on every head movement and lags behind the crosshair, which is why
     * the pointer is normally invisible. It exists so testing can see where the guest's pointer
     * actually is.
     */
    public boolean isPointerDebug() {
        return pointerDebug;
    }

    public void setPointerDebug(boolean pointerDebug) {
        this.pointerDebug = pointerDebug;
    }

    /** Shared RGB -> map palette lookup table. Built once; never use MapPalette.matchColor. */
    public MapColorLut getMapPalette() {
        return mapPalette;
    }

    public Database getDB() {
        return db;
    }

    public ComputerDao getComputerDao() {
        return computerDao;
    }

    /** In-memory index of placed computers; the click path looks up here, never in SQL. */
    public ComputerRegistry getRegistry() {
        return registry;
    }

    /**
     * Placed but unassembled cases, keyed by block position.
     *
     * <p>Its own map rather than a corner of {@link ComputerRegistry}, which indexes every block a
     * computer occupies so a click anywhere on one finds it. A case is exactly one block, so it
     * needs none of that.
     */
    private final java.util.Map<String, PendingCase> pendingCases =
            new java.util.concurrent.ConcurrentHashMap<String, PendingCase>();

    private static String caseKey(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }

    public PendingCase pendingCaseAt(String world, int x, int y, int z) {
        return pendingCases.get(caseKey(world, x, y, z));
    }

    public void rememberPendingCase(PendingCase pending) {
        pendingCases.put(caseKey(pending.worldName(), pending.x(), pending.y(), pending.z()),
                pending);
    }

    public void forgetPendingCase(PendingCase pending) {
        pendingCases.remove(caseKey(pending.worldName(), pending.x(), pending.y(), pending.z()));
    }

    public java.util.Collection<PendingCase> pendingCases() {
        return pendingCases.values();
    }

    /**
     * Puts a computer's stored components back in their bays.
     *
     * <p>Rows naming a slot or a component that no longer exists are dropped rather than fatal: a
     * renamed component should cost that one part, not the whole machine.
     */
    private void restoreComponents(Computer computer, java.util.Map<String, String> stored) {
        boolean legacy = stored == null || stored.isEmpty();

        if (!legacy) {
            for (java.util.Map.Entry<String, String> entry : stored.entrySet()) {
                ComponentType type = ComponentType.byId(entry.getValue());
                if (type == null) {
                    getLogger().warning("Computer #" + computer.id() + " has unknown component '"
                            + entry.getValue() + "'; dropped.");
                    continue;
                }
                try {
                    computer.install(ComponentSlot.valueOf(entry.getKey()), type);
                } catch (IllegalArgumentException e) {
                    getLogger().warning("Computer #" + computer.id() + " has unknown slot '"
                            + entry.getKey() + "'; dropped.");
                }
            }
        }

        // Fill the gaps, and write them back so this only happens once.
        //
        // Two different situations, both of which end with a machine that cannot start unless
        // something is put in: a computer with no rows at all predates components entirely (it
        // used to boot on a hardcoded 4 GB), and a computer with rows may still be missing a bay
        // that did not exist when it was last saved -- the monitor bay arrived exactly that way.
        // Either would leave a built machine refusing to power on with no way to get parts into it.
        //
        // Only *required* bays are filled once a computer has any rows of its own. Doing otherwise
        // would resurrect the hard drive of someone who deliberately took theirs out, every time
        // the server restarted.
        for (java.util.Map.Entry<ComponentSlot, ComponentType> entry
                : ComponentType.defaultLoadout(computer.monitorSize()).entrySet()) {
            ComponentSlot slot = entry.getKey();
            if (computer.installedIn(slot) != null || (!legacy && !slot.required())) {
                continue;
            }
            computer.install(slot, entry.getValue());
            try {
                computerDao.saveComponent(computer.id(), slot.name(), entry.getValue().id());
            } catch (SQLException e) {
                getLogger().warning("Could not backfill " + slot + " for computer #"
                        + computer.id() + ": " + e.getMessage());
            }
        }
    }

    @Override
    public void onEnable() {
        plugin = this;

        long lutStart = System.nanoTime();
        this.mapPalette = MapColorLut.build(MapColorLut.DEFAULT_BITS);
        getLogger().info("Map palette LUT built in " + ((System.nanoTime() - lutStart) / 1_000_000)
                + "ms (" + mapPalette.paletteSize() + " usable colours).");

        // Parses every part's block data once, so building a computer is only entity creation.
        PartModels.load(getLogger());
        BrickPhone.registerRecipe();

        this.db = new SQLite(this);
        this.db.load();
        this.computerDao = new ComputerDao(this.db);

        try {
            computerDao.createSchema();
            List<Computer> loaded = computerDao.loadAll();
            registry.replaceAll(loaded);
            // Map renderers do not survive a restart -- the default world-map renderer comes back
            // on every map -- so they have to be reinstalled from the stored panel ids.
            java.util.Map<Integer, List<Integer>> panels = computerDao.loadAllPanels();
            java.util.Map<Integer, java.util.Map<String, String>> components =
                    computerDao.loadAllComponents();
            int reattached = 0;
            byte black = mapPalette.match(0, 0, 0);
            for (Computer computer : loaded) {
                restoreComponents(computer, components.get(Integer.valueOf(computer.id())));

                List<Integer> ids = panels.get(Integer.valueOf(computer.id()));
                if (ids == null) {
                    continue;
                }
                MonitorScreen screen = MonitorScreen.attach(computer, ids);
                if (screen != null) {
                    registerScreen(screen);
                    screen.fill(black);
                    reattached++;
                }
            }
            for (PendingCase pending : computerDao.loadAllCases()) {
                rememberPendingCase(pending);
            }
            getLogger().info("Loaded " + loaded.size() + " computer(s), "
                    + reattached + " screen(s) reattached, "
                    + pendingCases.size() + " case(s) awaiting parts.");
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Failed to initialise computer storage", e);
        }

        getCommand("vmcomputers").setExecutor(new ComputerCM());
        getServer().getPluginManager().registerEvents(new ClickListener(), this);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);
        getServer().getPluginManager().registerEvents(new OrderingListener(), this);
        getServer().getPluginManager().registerEvents(new PlacementListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(), this);
        getServer().getPluginManager().registerEvents(new PreventionListener(), this);
        this.pointerListener = new PointerListener();
        getServer().getPluginManager().registerEvents(pointerListener, this);
        // Aim is sampled on the tick rather than driven by PlayerMoveEvent, which CraftBukkit does
        // not raise until the look has swung ten degrees. See PointerListener.tick().
        pointerListener.start(this);

        // Frames are pushed by us, not by the server, which only updates a map in an item frame
        // every ten ticks. See ScreenPump -- that interval was the entire frame rate.
        this.screenPump = new ScreenPump(this);
        screenPump.start(this);
    }

    @Override
    public void onDisable() {
        // Each step is guarded separately: these are separate OS processes and an open database,
        // and a failure in one must not leave the other running. A NoClassDefFoundError here is
        // also possible if the jar was rebuilt under a live server, which must not stop the rest
        // of shutdown.
        try {
            if (pointerListener != null) {
                pointerListener.stop();
            }
            if (screenPump != null) {
                screenPump.stop();
            }
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "Failed to stop screen tasks during shutdown", t);
        }

        try {
            ComputerFunctions.stopAll();
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "Failed to stop virtual machines during shutdown", t);
        }

        try {
            if (db != null && db.getSQLConnection() != null) {
                db.getSQLConnection().close();
            }
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "Failed to close the database during shutdown", t);
        }
    }
}
