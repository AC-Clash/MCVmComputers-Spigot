package com.acclash.vmcomputers.parts;

import com.acclash.vmcomputers.emu.Json;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;
import org.joml.Vector3f;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Loads {@code parts.json} once at startup and hands out {@link PartModel}s by name.
 *
 * <p>The file is generated from the mod's Blockbench models by {@code tools/generate_parts.py} and
 * committed, so the mod checkout is a tool dependency rather than a build dependency -- a clean
 * clone of this repo builds without it.
 *
 * <p>Block data is parsed here, at load, rather than per spawn. {@code Bukkit.createBlockData}
 * parses a string every call, and a desk spawns a dozen boxes; doing it once means placing a part
 * is just entity creation. It also means a typo in the generated file is a startup warning naming
 * the model, instead of an exception on the first player who builds a computer.
 */
public final class PartModels {

    private static final String RESOURCE = "/parts.json";

    private static volatile Map<String, PartModel> models = Collections.emptyMap();

    private PartModels() {
    }

    /**
     * Reads the shipped model file. Safe to call again; a failed load leaves the previous set in
     * place rather than emptying it.
     *
     * @return how many models were loaded
     */
    public static int load(Logger log) {
        String text;
        try (InputStream in = PartModels.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                log.severe("parts.json is missing from the plugin jar; components will not render.");
                return 0;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                text = reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            log.severe("Could not read parts.json: " + e.getMessage());
            return 0;
        }

        Map<String, PartModel> loaded = new LinkedHashMap<String, PartModel>();
        try {
            Map<String, Object> root = Json.asObject(Json.parse(text));
            Map<String, Object> raw = Json.getObject(root, "models");
            if (raw == null) {
                log.severe("parts.json has no 'models' object.");
                return 0;
            }
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                PartModel model = readModel(entry.getKey(), Json.asArray(entry.getValue()), log);
                if (model != null) {
                    loaded.put(entry.getKey(), model);
                }
            }
        } catch (RuntimeException e) {
            log.severe("parts.json is malformed: " + e.getMessage());
            return 0;
        }

        if (loaded.isEmpty()) {
            log.severe("parts.json contained no usable models.");
            return 0;
        }

        models = Collections.unmodifiableMap(loaded);
        int pieces = 0;
        for (PartModel model : loaded.values()) {
            pieces += model.pieceCount();
        }
        log.info("Loaded " + loaded.size() + " part models (" + pieces + " display pieces).");
        return loaded.size();
    }

    private static PartModel readModel(String name, List<Object> raw, Logger log) {
        if (raw == null) {
            log.warning("Part model '" + name + "' is not a list; skipped.");
            return null;
        }
        List<PartModel.Piece> pieces = new ArrayList<PartModel.Piece>();
        for (Object element : raw) {
            Map<String, Object> piece = Json.asObject(element);
            if (piece == null) {
                log.warning("Part model '" + name + "' has a null piece; skipped.");
                return null;
            }
            String blockName = Json.getString(piece, "block", null);
            if (blockName == null) {
                log.warning("Part model '" + name + "' has a piece with no block; skipped.");
                return null;
            }

            BlockData block;
            try {
                block = Bukkit.createBlockData("minecraft:" + blockName.toLowerCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                // A block that this server version does not know. Naming both the model and the
                // block matters: the fix is a line in the generator's override table.
                log.warning("Part model '" + name + "' wants unknown block '" + blockName
                        + "'; model skipped.");
                return null;
            }

            Vector3f size = vector(Json.asArray(piece.get("size")));
            Vector3f centre = vector(Json.asArray(piece.get("centre")));
            if (size == null || centre == null) {
                log.warning("Part model '" + name + "' has a piece missing size or centre; skipped.");
                return null;
            }

            Vector3f axis = null;
            float angle = 0f;
            Vector3f pivot = null;
            Map<String, Object> rotation = Json.getObject(piece, "rotation");
            if (rotation != null) {
                String axisName = Json.getString(rotation, "axis", "y");
                axis = new Vector3f(
                        "x".equals(axisName) ? 1f : 0f,
                        "y".equals(axisName) ? 1f : 0f,
                        "z".equals(axisName) ? 1f : 0f);
                angle = (float) Math.toRadians(number(rotation.get("angle"), 0d));
                pivot = vector(Json.asArray(rotation.get("pivot")));
            }

            pieces.add(new PartModel.Piece(block, size, centre, axis, angle, pivot));
        }
        return new PartModel(name, pieces);
    }

    private static Vector3f vector(List<Object> raw) {
        if (raw == null || raw.size() != 3) {
            return null;
        }
        return new Vector3f(
                (float) number(raw.get(0), 0d),
                (float) number(raw.get(1), 0d),
                (float) number(raw.get(2), 0d));
    }

    private static double number(Object value, double fallback) {
        return value instanceof Number ? ((Number) value).doubleValue() : fallback;
    }

    /** The model with this name, or null. Names match the mod's model files, e.g. {@code pc_case}. */
    public static PartModel get(String name) {
        return models.get(name);
    }

    public static boolean isLoaded() {
        return !models.isEmpty();
    }

    /** Every loaded model name, in file order. */
    public static List<String> names() {
        return new ArrayList<String>(models.keySet());
    }
}
