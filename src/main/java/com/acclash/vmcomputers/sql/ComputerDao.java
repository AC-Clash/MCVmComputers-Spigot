package com.acclash.vmcomputers.sql;

import com.acclash.vmcomputers.computer.Computer;
import com.acclash.vmcomputers.computer.PendingCase;
import com.acclash.vmcomputers.parts.ComponentSlot;
import com.acclash.vmcomputers.parts.ComponentType;
import com.acclash.vmcomputers.display.MonitorSize;
import org.bukkit.block.BlockFace;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Persistence for computers.
 *
 * <p>Everything here uses prepared statements. The previous code built SQL by concatenating player
 * input straight into the query -- {@code WHERE id = '" + args[1] + "'} -- which a player could
 * have used to run arbitrary SQL just by typing it as a command argument.
 *
 * <p>The schema stores the anchor as separate integer columns rather than a formatted string. That
 * makes lookups exact instead of dependent on how a double happens to render, and lets the unique
 * constraint do real work.
 */
public final class ComputerDao {

    private static final String CREATE_TABLE =
            "CREATE TABLE IF NOT EXISTS computers ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "world TEXT NOT NULL,"
                    + "x INTEGER NOT NULL,"
                    + "y INTEGER NOT NULL,"
                    + "z INTEGER NOT NULL,"
                    + "facing TEXT NOT NULL,"
                    + "monitor_size TEXT NOT NULL,"
                    + "type TEXT NOT NULL,"
                    + "state TEXT NOT NULL,"
                    + "iso TEXT,"
                    + "arch TEXT,"
                    + "UNIQUE (world, x, y, z)"
                    + ")";

    private static final String CREATE_CASES =
            "CREATE TABLE IF NOT EXISTS pending_cases ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "world TEXT NOT NULL,"
                    + "x INTEGER NOT NULL,"
                    + "y INTEGER NOT NULL,"
                    + "z INTEGER NOT NULL,"
                    + "facing TEXT NOT NULL,"
                    + "UNIQUE (world, x, y, z)"
                    + ")";

    private static final String CREATE_CASE_COMPONENTS =
            "CREATE TABLE IF NOT EXISTS pending_case_components ("
                    + "case_id INTEGER NOT NULL,"
                    + "slot TEXT NOT NULL,"
                    + "component_id TEXT NOT NULL,"
                    + "PRIMARY KEY (case_id, slot)"
                    + ")";

    private static final String CREATE_COMPONENTS =
            "CREATE TABLE IF NOT EXISTS computer_components ("
                    + "computer_id INTEGER NOT NULL,"
                    + "slot TEXT NOT NULL,"
                    + "component_id TEXT NOT NULL,"
                    + "PRIMARY KEY (computer_id, slot)"
                    + ")";

    private static final String CREATE_PANELS =
            "CREATE TABLE IF NOT EXISTS computer_panels ("
                    + "computer_id INTEGER NOT NULL,"
                    + "idx INTEGER NOT NULL,"
                    + "map_id INTEGER NOT NULL,"
                    + "PRIMARY KEY (computer_id, idx)"
                    + ")";

    private final Database database;

    public ComputerDao(Database database) {
        this.database = database;
    }

    /**
     * Creates the table, replacing any table left over from the jDOSBox-era schema.
     *
     * <p>That old schema had one column per component, which cannot represent a variable number of
     * screen panels, and its rows described machines whose emulator no longer exists. There is
     * nothing worth migrating, so it is dropped outright.
     */
    public void createSchema() throws SQLException {
        Connection connection = database.getSQLConnection();
        try (Statement statement = connection.createStatement()) {
            if (hasLegacySchema(connection)) {
                statement.executeUpdate("DROP TABLE computers");
            }
            statement.executeUpdate(CREATE_TABLE);
            statement.executeUpdate(CREATE_PANELS);
            statement.executeUpdate(CREATE_COMPONENTS);
            statement.executeUpdate(CREATE_CASES);
            statement.executeUpdate(CREATE_CASE_COMPONENTS);
            // Added after the table shipped, so existing databases need it bolted on.
            if (!hasColumn(connection, "computers", "iso")) {
                statement.executeUpdate("ALTER TABLE computers ADD COLUMN iso TEXT");
            }
            if (!hasColumn(connection, "computers", "arch")) {
                statement.executeUpdate("ALTER TABLE computers ADD COLUMN arch TEXT");
            }
            // Machines built before this column exists stay unowned, which leaves them modifiable
            // by anyone. Claiming them for whoever happens to click first would be worse.
            if (!hasColumn(connection, "computers", "owner")) {
                statement.executeUpdate("ALTER TABLE computers ADD COLUMN owner TEXT");
            }
            // Null on every existing machine, which is what keeps them on their own disks.
            if (!hasColumn(connection, "computers", "disk")) {
                statement.executeUpdate("ALTER TABLE computers ADD COLUMN disk TEXT");
            }
        }
    }

    private boolean hasColumn(Connection connection, String table, String column)
            throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, table, null)) {
            while (columns.next()) {
                if (column.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasLegacySchema(Connection connection) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, "computers", null)) {
            while (columns.next()) {
                // Only the old layout had a column per component.
                if ("monitor_loc".equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<Computer> loadAll() throws SQLException {
        List<Computer> out = new ArrayList<Computer>();
        String sql = "SELECT id, world, x, y, z, facing, monitor_size, type, state, iso, arch, owner"
                + " FROM computers";
        try (PreparedStatement statement = database.getSQLConnection().prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                Computer computer = read(rs);
                if (computer != null) {
                    out.add(computer);
                }
            }
        }
        return out;
    }

    private Computer read(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        try {
            Computer computer = new Computer(
                    id,
                    rs.getString("world"),
                    rs.getInt("x"),
                    rs.getInt("y"),
                    rs.getInt("z"),
                    BlockFace.valueOf(rs.getString("facing")),
                    MonitorSize.valueOf(rs.getString("monitor_size")),
                    rs.getString("type"),
                    Computer.State.valueOf(rs.getString("state")));
            computer.setIsoName(rs.getString("iso"));
            computer.setDiskImage(rs.getString("disk"));
            String owner = rs.getString("owner");
            if (owner != null) {
                try {
                    computer.setOwner(java.util.UUID.fromString(owner));
                } catch (IllegalArgumentException ignored) {
                    // Not a uuid; treat as unowned rather than dropping the machine.
                }
            }
            String arch = rs.getString("arch");
            if (arch != null) {
                try {
                    computer.setArchitecture(
                            com.acclash.vmcomputers.emu.VmSpec.Architecture.valueOf(arch));
                } catch (IllegalArgumentException ignored) {
                    // Unknown architecture name: keep the default rather than dropping the row.
                }
            }
            return computer;
        } catch (IllegalArgumentException e) {
            // An unknown enum name means the row predates a rename. Skip it rather than refusing
            // to start the whole plugin.
            return null;
        }
    }

    /** Inserts a computer and returns it with the id the database assigned. */
    public Computer insert(Computer computer) throws SQLException {
        String sql = "INSERT INTO computers (world, x, y, z, facing, monitor_size, type, state, owner)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        Connection connection = database.getSQLConnection();
        try (PreparedStatement statement =
                     connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, computer.worldName());
            statement.setInt(2, computer.anchorX());
            statement.setInt(3, computer.anchorY());
            statement.setInt(4, computer.anchorZ());
            statement.setString(5, computer.facing().name());
            statement.setString(6, computer.monitorSize().name());
            statement.setString(7, computer.type());
            statement.setString(8, computer.state().name());
            statement.setString(9, computer.owner() == null ? null : computer.owner().toString());
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                int id = keys.next() ? keys.getInt(1) : -1;
                Computer saved = new Computer(id, computer.worldName(), computer.anchorX(),
                        computer.anchorY(), computer.anchorZ(), computer.facing(),
                        computer.monitorSize(), computer.type(), computer.state());
                saved.setOwner(computer.owner());
                saved.setIsoName(computer.isoName());
                saved.setDiskImage(computer.diskImage());
                saved.setArchitecture(computer.architecture());
                return saved;
            }
        }
    }

    /**
     * Records the map ids backing a computer's screen panels, in layout order.
     *
     * <p>Map ids are assigned by the server, so unlike every other part of a computer they cannot
     * be derived from the anchor, facing and size, and have to be stored.
     */
    public void savePanels(int computerId, List<Integer> mapIds) throws SQLException {
        Connection connection = database.getSQLConnection();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT OR REPLACE INTO computer_panels (computer_id, idx, map_id) VALUES (?, ?, ?)")) {
            for (int i = 0; i < mapIds.size(); i++) {
                statement.setInt(1, computerId);
                statement.setInt(2, i);
                statement.setInt(3, mapIds.get(i).intValue());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    /** Panel map ids for every computer, keyed by computer id and ordered by panel index. */
    public Map<Integer, List<Integer>> loadAllPanels() throws SQLException {
        Map<Integer, List<Integer>> out = new HashMap<Integer, List<Integer>>();
        try (PreparedStatement statement = database.getSQLConnection().prepareStatement(
                "SELECT computer_id, map_id FROM computer_panels ORDER BY computer_id, idx");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                out.computeIfAbsent(Integer.valueOf(rs.getInt("computer_id")),
                        k -> new ArrayList<Integer>()).add(Integer.valueOf(rs.getInt("map_id")));
            }
        }
        return out;
    }

    public void deletePanels(int computerId) throws SQLException {
        try (PreparedStatement statement = database.getSQLConnection()
                .prepareStatement("DELETE FROM computer_panels WHERE computer_id = ?")) {
            statement.setInt(1, computerId);
            statement.executeUpdate();
        }
    }

    /**
     * Installs or removes one component.
     *
     * <p>One row per occupied bay rather than a column per bay: the set of bays is a plugin
     * concept that will grow, and a table does not need a migration to gain one. Passing a null
     * {@code componentId} empties the bay.
     */
    public void saveComponent(int computerId, String slot, String componentId) throws SQLException {
        Connection connection = database.getSQLConnection();
        if (componentId == null) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM computer_components WHERE computer_id = ? AND slot = ?")) {
                statement.setInt(1, computerId);
                statement.setString(2, slot);
                statement.executeUpdate();
            }
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT OR REPLACE INTO computer_components (computer_id, slot, component_id) "
                        + "VALUES (?, ?, ?)")) {
            statement.setInt(1, computerId);
            statement.setString(2, slot);
            statement.setString(3, componentId);
            statement.executeUpdate();
        }
    }

    /** Installed components for every computer, keyed by computer id then slot name. */
    public Map<Integer, Map<String, String>> loadAllComponents() throws SQLException {
        Map<Integer, Map<String, String>> out = new HashMap<Integer, Map<String, String>>();
        try (PreparedStatement statement = database.getSQLConnection().prepareStatement(
                "SELECT computer_id, slot, component_id FROM computer_components");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                out.computeIfAbsent(Integer.valueOf(rs.getInt("computer_id")),
                                k -> new HashMap<String, String>())
                        .put(rs.getString("slot"), rs.getString("component_id"));
            }
        }
        return out;
    }

    // ---- pending cases ---------------------------------------------------

    /** Inserts a placed case and returns it with the id the database assigned. */
    public PendingCase insertCase(PendingCase pending) throws SQLException {
        Connection connection = database.getSQLConnection();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO pending_cases (world, x, y, z, facing) VALUES (?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, pending.worldName());
            statement.setInt(2, pending.x());
            statement.setInt(3, pending.y());
            statement.setInt(4, pending.z());
            statement.setString(5, pending.facing().name());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return new PendingCase(keys.getInt(1), pending.worldName(), pending.x(),
                            pending.y(), pending.z(), pending.facing());
                }
            }
        }
        throw new SQLException("pending case insert returned no id");
    }

    public List<PendingCase> loadAllCases() throws SQLException {
        List<PendingCase> out = new ArrayList<PendingCase>();
        Map<Integer, Map<String, String>> parts = loadAllCaseComponents();
        try (PreparedStatement statement = database.getSQLConnection().prepareStatement(
                "SELECT id, world, x, y, z, facing FROM pending_cases");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                try {
                    PendingCase pending = new PendingCase(rs.getInt("id"), rs.getString("world"),
                            rs.getInt("x"), rs.getInt("y"), rs.getInt("z"),
                            BlockFace.valueOf(rs.getString("facing")));
                    Map<String, String> fitted = parts.get(Integer.valueOf(pending.id()));
                    if (fitted != null) {
                        for (Map.Entry<String, String> entry : fitted.entrySet()) {
                            ComponentType type = ComponentType.byId(entry.getValue());
                            if (type != null) {
                                pending.install(ComponentSlot.valueOf(entry.getKey()), type);
                            }
                        }
                    }
                    out.add(pending);
                } catch (IllegalArgumentException ignored) {
                    // Unknown facing or slot name: skip the row rather than refuse to start.
                }
            }
        }
        return out;
    }

    private Map<Integer, Map<String, String>> loadAllCaseComponents() throws SQLException {
        Map<Integer, Map<String, String>> out = new HashMap<Integer, Map<String, String>>();
        try (PreparedStatement statement = database.getSQLConnection().prepareStatement(
                "SELECT case_id, slot, component_id FROM pending_case_components");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                out.computeIfAbsent(Integer.valueOf(rs.getInt("case_id")),
                                k -> new HashMap<String, String>())
                        .put(rs.getString("slot"), rs.getString("component_id"));
            }
        }
        return out;
    }

    public void saveCaseComponent(int caseId, String slot, String componentId) throws SQLException {
        Connection connection = database.getSQLConnection();
        if (componentId == null) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM pending_case_components WHERE case_id = ? AND slot = ?")) {
                statement.setInt(1, caseId);
                statement.setString(2, slot);
                statement.executeUpdate();
            }
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT OR REPLACE INTO pending_case_components (case_id, slot, component_id) "
                        + "VALUES (?, ?, ?)")) {
            statement.setInt(1, caseId);
            statement.setString(2, slot);
            statement.setString(3, componentId);
            statement.executeUpdate();
        }
    }

    public void deleteCase(int caseId) throws SQLException {
        Connection connection = database.getSQLConnection();
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM pending_case_components WHERE case_id = ?")) {
            statement.setInt(1, caseId);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM pending_cases WHERE id = ?")) {
            statement.setInt(1, caseId);
            statement.executeUpdate();
        }
    }

    public void deleteComponents(int computerId) throws SQLException {
        try (PreparedStatement statement = database.getSQLConnection()
                .prepareStatement("DELETE FROM computer_components WHERE computer_id = ?")) {
            statement.setInt(1, computerId);
            statement.executeUpdate();
        }
    }

    public void updateArchitecture(int id, com.acclash.vmcomputers.emu.VmSpec.Architecture arch)
            throws SQLException {
        try (PreparedStatement statement = database.getSQLConnection()
                .prepareStatement("UPDATE computers SET arch = ? WHERE id = ?")) {
            statement.setString(1, arch.name());
            statement.setInt(2, id);
            statement.executeUpdate();
        }
    }

    public void updateIso(int id, String isoName) throws SQLException {
        try (PreparedStatement statement = database.getSQLConnection()
                .prepareStatement("UPDATE computers SET iso = ? WHERE id = ?")) {
            statement.setString(1, isoName);
            statement.setInt(2, id);
            statement.executeUpdate();
        }
    }

    public void updateDisk(int id, String diskImage) throws SQLException {
        try (PreparedStatement statement = database.getSQLConnection()
                .prepareStatement("UPDATE computers SET disk = ? WHERE id = ?")) {
            statement.setString(1, diskImage);
            statement.setInt(2, id);
            statement.executeUpdate();
        }
    }

    // No updateState: state is deliberately never written back. Nothing survives a restart -- the
    // QEMU processes are gone and the map renderers with them -- so a computer must load as the
    // state it was stored with, and persisting RUNNING would bring one back claiming to be running
    // with no process behind it.

    public boolean delete(int id) throws SQLException {
        try (PreparedStatement statement =
                     database.getSQLConnection().prepareStatement("DELETE FROM computers WHERE id = ?")) {
            statement.setInt(1, id);
            return statement.executeUpdate() > 0;
        }
    }
}
