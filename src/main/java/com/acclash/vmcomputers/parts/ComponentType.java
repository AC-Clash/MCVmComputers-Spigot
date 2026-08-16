package com.acclash.vmcomputers.parts;

import com.acclash.vmcomputers.VMComputers;
import com.acclash.vmcomputers.display.MonitorSize;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The catalogue of orderable components: what they are called, what they cost, what they look like
 * in an inventory, and which {@link PartModel} draws them in the world.
 *
 * <p>Prices and tiers are the mod's own numbers from its {@code ItemList}, so a player who knows
 * the mod pays what they expect. The mod charges them in iron; here they are {@link Currency}.
 *
 * <h2>Why the icons are ordinary vanilla items</h2>
 *
 * <p>An inventory slot renders an {@link ItemStack} and nothing else. Display entities -- which is
 * how these parts are drawn in the world -- cannot appear in a chest GUI at all, and the only
 * vanilla item whose texture the server can choose is a player head, whose skin has to be hosted
 * on Mojang's texture servers. That is an outside dependency for artwork that would still read as
 * a head.
 *
 * <p>So the icons are vanilla items picked to be recognisable and, more importantly, to be
 * distinct from each other at a glance: a player scanning the shop should be able to tell a GPU
 * from a motherboard without reading. Naming carries the precision, the icon carries the
 * silhouette.
 *
 * <p>Nothing here is throwable, edible or placeable-by-accident. A part is an inventory item a
 * player carries around, and a snowball -- much the closest match for a mouse -- can be thrown
 * away and lost for good.
 */
public final class ComponentType {

    /** Shop sections, matching how the mod's ordering tablet groups its catalogue. */
    public enum Category {
        PARTS("Components"),
        PERIPHERALS("Peripherals");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private static final Map<String, ComponentType> REGISTRY =
            new LinkedHashMap<String, ComponentType>();

    /**
     * Head textures for the catalogue, by component id.
     *
     * <p>One block rather than an argument on each entry, so filling the set in is a single paste
     * rather than twenty scattered edits. Anything left out or left empty keeps its vanilla item
     * icon, so a half-finished set still works and can be finished a part at a time.
     *
     * <p>Values may be a base64 texture value, a bare texture hash, or a full
     * {@code textures.minecraft.net} URL -- head sites are inconsistent about which they hand out.
     * See {@link HeadTextures} for where these come from and what depends on them.
     */
    private static final Map<String, String> HEAD_TEXTURES = new LinkedHashMap<String, String>();

    // Long lines by necessity: a texture value is a single base64 blob and breaking one
    // across concatenations would make it impossible to copy back out and re-check.
    static {
        HEAD_TEXTURES.put("pc_case",
                "ewogICJ0aW1lc3RhbXAiIDogMTc4Njg0NTU1MDAyMSwKICAicHJvZmlsZUlkIiA6ICI0MGVkMTc3OWE1YjY0M2QzYmI3Yzk3NzYwOTEyODIzMSIsCiAgInByb2ZpbGVOYW1lIiA6ICJjYW1lcmEwMiIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS8yM2U3MTgyZDEyMTFkN2VjNTU4N2Y3MzliY2VmODcwMTc5MDc1ZjQ0YjY2NzgzYWI0NGQyNmJhYzI0ZjNkNTMzIiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0=");
        HEAD_TEXTURES.put("case_side_panel",
                "ewogICJ0aW1lc3RhbXAiIDogMTc4NjgyOTMwMDM0MiwKICAicHJvZmlsZUlkIiA6ICI3NzQ3ODc3M2ExZTE0NzUxODE0M2Q5ZDA5YTc1NmQ2MiIsCiAgInByb2ZpbGVOYW1lIiA6ICJ3aGF0YXJlZmVycmV0cyIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS8yZWFhMTI3YjViMGU4MGRiMTg1ZGM4Y2MyZjE2NmMyYzNjZTY3ZTJkYTcwZWFlYWYyNjhiM2Y5NGU1YzUxZjZlIiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0=");
        HEAD_TEXTURES.put("motherboard",
                "ewogICJ0aW1lc3RhbXAiIDogMTc4Njg0NTQyODMyNCwKICAicHJvZmlsZUlkIiA6ICJjODVhZTlmOWJkNTU0YzFmYjk1ZTg3ZDI2Zjg2MGFmNiIsCiAgInByb2ZpbGVOYW1lIiA6ICJTdGV2ZW4xNzUzIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2M5YWU5ZGUxOTY5NDRmNTgxODk2MGIwNjVhODE5MDJkYTgzNWI0ZTA0ODQ1ZjI4NGViNWMyZjcyNmEwODhkNSIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9");
        HEAD_TEXTURES.put("motherboard64",
                "ewogICJ0aW1lc3RhbXAiIDogMTc4Njg0NTQ4MjgxMiwKICAicHJvZmlsZUlkIiA6ICI2NDU4Mjc0MjEyNDg0MDY0YTRkMDBlNDdjZWM4ZjcyZSIsCiAgInByb2ZpbGVOYW1lIiA6ICJUaDNtMXMiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTc3MWUyNjdiYTc1ZmYxMmNkMDZjMjk4ZjQ2MjBiNjYzZWYxNWU4NjEzNTk2OWVjNWQ4NDE0Yjk1N2FmOWVkMSIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9");
        HEAD_TEXTURES.put("cpu2",
                "ewogICJ0aW1lc3RhbXAiIDogMTc4NjgyOTQ2NTI4MiwKICAicHJvZmlsZUlkIiA6ICIyM2RjZjc3NWQ5YzQ0MGE1ODc3MjE4ZjU3NzNlMTUzNiIsCiAgInByb2ZpbGVOYW1lIiA6ICJEZWx1c2lvblNrZXRjaCIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9hYWIzNjFlOTIzYjIwMzdhOGNmNDcxOWY4NDQ4MjU2YTBjYjdkZjBlY2QxYWQxZDYxYTk3YjA5MjhkODQ0NjhhIiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0=");
        HEAD_TEXTURES.put("cpu4",
                "ewogICJ0aW1lc3RhbXAiIDogMTc4NjgyOTYyNjIzMCwKICAicHJvZmlsZUlkIiA6ICJjN2Y2Nzk3ZmE4ZGM0NTdiYTkxNTU0NWIwMGU3M2UzMSIsCiAgInByb2ZpbGVOYW1lIiA6ICJlZGd5c3BlbmNlIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzE2MmJiNjBmYzYzZjY5ODRiMzY4NzIxNDE3ZTgwNDA4NjUwMTU1YzMyY2NkMzIyNDI2OGY5M2U2NzVhYjk3NjciLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ==");
        HEAD_TEXTURES.put("cpu6",
                "ewogICJ0aW1lc3RhbXAiIDogMTc4Njg0NDQ3MzAxNCwKICAicHJvZmlsZUlkIiA6ICJlZGUyYzdhMGFjNjM0MTNiYjA5ZDNmMGJlZTllYzhlYyIsCiAgInByb2ZpbGVOYW1lIiA6ICJ0aGVEZXZKYWRlIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzQ4ZWM0ZjY0MWIxNDk4Yjk3ZmUxZjhiYjY3MTVmMmJmYjI2NzE4NjMwZGViZjU0MTlmMjI1MWNjOGJlZjMxMDYiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ==");
        HEAD_TEXTURES.put("gpu",
                "ewogICJ0aW1lc3RhbXAiIDogMTc4Njg0NDUyMTQ1NiwKICAicHJvZmlsZUlkIiA6ICIwNDg0N2ZjNWM5YjY0NTQ1YjI1ZWJkYmJiNzdjNjg2NSIsCiAgInByb2ZpbGVOYW1lIiA6ICJOYXFsdWEiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzVkMjlhZmRmY2YwZDEwM2NmZWViODg2OWFkYjdkYThiY2NmZDY2OWNkM2RlMGJlMzI1MjUxNjM2NTc3ZTliZiIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9");
        HEAD_TEXTURES.put("ram64m",
                "ewogICJ0aW1lc3RhbXAiIDogMTc4Njg0NTYzMDI2MywKICAicHJvZmlsZUlkIiA6ICJmZDIwMGYwMDE4OTI0NzgxODI5OWIzZjE5Yzc4Y2E3MSIsCiAgInByb2ZpbGVOYW1lIiA6ICJ0dXNnIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2ZmODdlMWI3N2UxOWFjMDQ1MDRlNDM3YzA1NWFlYWEwZDJhYjY4MmRiMmVjNWQzNzIxN2YyZDg4M2ZhYzUxMzkiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ==");
        HEAD_TEXTURES.put("ram128m",
                "ewogICJ0aW1lc3RhbXAiIDogMTc4Njg0NTYyODgzOCwKICAicHJvZmlsZUlkIiA6ICJhMzExN2YyMzg3MWM0YzM5OTQ5M2I0OGMwYTliZmZlNCIsCiAgInByb2ZpbGVOYW1lIiA6ICJhY2luNSIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9mOTEyZDQ3ZTYxNzhkMjUwOTAyNWRkYjBiOTdkMDUxYmFlOGE4NTdlOTQwYjBhMjc1NmU5ZWZkMWYxN2U2ODBhIiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0=");
        HEAD_TEXTURES.put("ram256m",
                "ewogICJ0aW1lc3RhbXAiIDogMTc4Njg0NTYzMjQ4MCwKICAicHJvZmlsZUlkIiA6ICIzMzU3MWJiY2UyMDE0MTRiYmNkMDYyMjEyZTI4MjBlMyIsCiAgInByb2ZpbGVOYW1lIiA6ICJUaGFkb21JbmF0b3I0NzgiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTI1YTc2M2Y0Zjk0Y2U1NDU0MmQ5NDUwZDlkOWQwZmM1Yzg1M2EzMThmYzU1MTc1YjkwODU2MDMyOWE1YWM3YyIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9");
        HEAD_TEXTURES.put("ram512m",
                "ewogICJ0aW1lc3RhbXAiIDogMTc4Njg0NTYzMzk2MSwKICAicHJvZmlsZUlkIiA6ICJmYWU5NzYzY2FmMDU0OWI2YjlmZTM0MmNjM2E3YzNlMCIsCiAgInByb2ZpbGVOYW1lIiA6ICJHUjFaWkxZMTgwIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzdiMjM5YWRiZDRlYjUwOTMzZThiMDM4ZTQ4NjEyYmM0OTM1YmU1ZTlkYzRhNjE0NGNkNjVhNDQxZWFmMDg4YTUiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ==");
        HEAD_TEXTURES.put("ram1g",
                "ewogICJ0aW1lc3RhbXAiIDogMTc4Njg0NTYyNTI1MywKICAicHJvZmlsZUlkIiA6ICJiODQ5ZjE2MmRkNTE0ZjllYTUwZDlhNjRlZGUwZGIxNCIsCiAgInByb2ZpbGVOYW1lIiA6ICJ4WF9Ob19JbmNvbWVfWHgiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODhlYWY4MmQwN2Y0ZmRhZmQyMjQyMzg3ZmI5ZjFiOWE5NTMzYWVmN2VlYWZmZWE5NTQwOTU1ZWYwOGM0MDUxIiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0=");
        HEAD_TEXTURES.put("ram2g",
                "ewogICJ0aW1lc3RhbXAiIDogMTc4Njg0NTYyNjYyMiwKICAicHJvZmlsZUlkIiA6ICIyOGQyZDFmZDEyNGY0NGMyOGYxZDgwNDY4NGFkOTA2ZCIsCiAgInByb2ZpbGVOYW1lIiA6ICJzcGlmZnRvcGlhNyIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS84ZWZhZjg3Y2Q2ZTZkYmE3Nzc2MGM4NjllMzBiM2NiNDRkYzFlZjFhMTI4ODg1MjJkNTVjNGI5ZTBhMDI1MTNmIiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0=");
        HEAD_TEXTURES.put("ram4g",
                "ewogICJ0aW1lc3RhbXAiIDogMTc4Njg0NTYyODA1NiwKICAicHJvZmlsZUlkIiA6ICIzODY2ZTk1MmIxZWE0M2E4OGE3NGI1NzAxZDVjYTAwNSIsCiAgInByb2ZpbGVOYW1lIiA6ICJGYWtlTWFybG93IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzE5ZDc1ZGU5ODEwNzliMGFhYmY3ZjgxZGFmOGVmZjI4OWIwZjJjNjdmY2RmMDdmNzBiOWMxNTA3OWI2ZTI4OTkiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ==");
        HEAD_TEXTURES.put("harddrive",
                "ewogICJ0aW1lc3RhbXAiIDogMTc4Njg0NDU3MTk1NywKICAicHJvZmlsZUlkIiA6ICI5OGQxYTQyNmRlMmU0NjBkYjdjNWExMmY5MGNhODg0OSIsCiAgInByb2ZpbGVOYW1lIiA6ICJLdWJpbm9TSyIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS8yMTIyM2Q0MzI1YjliOTM0N2U1YzU3NzM3YmMyZDU0N2I2MDliNDgwOWJkMGIxN2Y1MjY1NzlhMjFiOWE1NDMxIiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0=");
        HEAD_TEXTURES.put("keyboard",
                "ewogICJ0aW1lc3RhbXAiIDogMTc4Njg0NDYyMzc1OSwKICAicHJvZmlsZUlkIiA6ICIxYjkzYjI5MjhkMjI0MDQxYjU0ZjI3ZjM2YjU5MGViZCIsCiAgInByb2ZpbGVOYW1lIiA6ICJtYW51ZHJpbmtzIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2E4YWFjZTFjN2IyM2M0YmVlMDlhNWQyYzcyYzgyODk2NTEwMzRhNWQ1NWRiZGRjNGMyYjdmNzBjZTQzOWE4NjEiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ==");
        HEAD_TEXTURES.put("mouse",
                "ewogICJ0aW1lc3RhbXAiIDogMTc4Njg0NTU0ODE2MywKICAicHJvZmlsZUlkIiA6ICI0OThjYTc2ZGYwODM0NzhmOGY0NjdjOGY1OTQwMjk1MiIsCiAgInByb2ZpbGVOYW1lIiA6ICJHdWx0cm8iLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTMzOGI0MjhhZGViNmE3MTA0NjMxNWI1MjY1NDg4OGEwZjM3NDMyNWRiZGY1N2U0Mjg5ZDIxMWNhMmNmOTczYyIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9");
        HEAD_TEXTURES.put("monitor_small",
                "ewogICJ0aW1lc3RhbXAiIDogMTc4Njg0NTM0NTcyMywKICAicHJvZmlsZUlkIiA6ICI2NDEwZjRiZjMwNDU0OTdmODBjZDI4NWIyYmJiNTk5NSIsCiAgInByb2ZpbGVOYW1lIiA6ICJNaW5lU2tpbl8xNSIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9jMmVlNTQ5NmNlZjc2MmIyNmRkMzUwNzQ3M2RmZTZkZmFkOTdjYjc2OTA1YjA3MDU1MWIzMmVhMTFmN2RjNjM2IiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0=");
        HEAD_TEXTURES.put("monitor_medium",
                "ewogICJ0aW1lc3RhbXAiIDogMTc4Njg0NTI3MTMwOSwKICAicHJvZmlsZUlkIiA6ICIxYjkzYjI5MjhkMjI0MDQxYjU0ZjI3ZjM2YjU5MGViZCIsCiAgInByb2ZpbGVOYW1lIiA6ICJtYW51ZHJpbmtzIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzNmM2QyMjE4NWUxNDQ3NmY1ZWVlZTIxYjUyN2I4N2NlYmMwNTVmNzFhYjk5MjA2NDRlMjRmMTFkNzMwMzlkM2UiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ==");
        HEAD_TEXTURES.put("monitor_large",
                "ewogICJ0aW1lc3RhbXAiIDogMTc4Njg0NDcwMjYwNSwKICAicHJvZmlsZUlkIiA6ICI0NDAzZGM1NDc1YmM0YjE1YTU0OGNmZGE2YjBlYjdkOSIsCiAgInByb2ZpbGVOYW1lIiA6ICJBdW50YnJpeCIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS81ZGI1NzQ0MmRiMzFhZjc5ODNiYzc1YjUwNmNkZjBlMmQ2ZmU4YjAyZjYzZDRlZDIzYTUxNjg2ZDc3MWJkZmFmIiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0=");
        HEAD_TEXTURES.put("monitor_xlarge",
                "ewogICJ0aW1lc3RhbXAiIDogMTc4Njg0NTM4MTEwMCwKICAicHJvZmlsZUlkIiA6ICJiYTllNWY4YTc0Yzg0MzQ5YjY3ZTg2MzBlMDRmMTI2ZiIsCiAgInByb2ZpbGVOYW1lIiA6ICJhbW9ndXNwYWRvdyIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS81YTliZDYzZWI5YjlhZTdiMTEyOWY5YWE2N2YyYjNiNmI0ZTliODJkOWYyZmJjYTUwNGIxMTQ5NzMyYjM1MzE1IiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0=");
    }

    // ---- the catalogue ---------------------------------------------------

    public static final ComponentType PC_CASE = register(new ComponentType(
            "pc_case", "PC Case", Material.BLACK_SHULKER_BOX, 6, Category.PARTS, null,
            "pc_case_sidepanel", "Houses the machine. Right-click to open it."));

    public static final ComponentType CASE_SIDE_PANEL = register(new ComponentType(
            "case_side_panel", "Case Side Panel", Material.GRAY_STAINED_GLASS_PANE, 2,
            Category.PARTS, null, "pc_case_only_glass_sidepanel", "Tinted window for the case."));

    public static final ComponentType MOTHERBOARD = register(new ComponentType(
            "motherboard", "Motherboard", Material.REPEATER, 4, Category.PARTS, ComponentSlot.MOTHERBOARD,
            "motherboard", "32-bit board. Takes one CPU and one stick of RAM."));

    public static final ComponentType MOTHERBOARD_64 = register(new ComponentType(
            "motherboard64", "64-bit Motherboard", Material.COMPARATOR, 8, Category.PARTS,
            ComponentSlot.MOTHERBOARD, "motherboard64", "Required for a 64-bit guest."));

    public static final ComponentType CPU_2 = register(new ComponentType(
            "cpu2", "CPU (host cores / 2)", Material.NETHERITE_SCRAP, 10, Category.PARTS, ComponentSlot.CPU,
            "cpu_divided_by_2", "Half the host's cores.", 2));

    public static final ComponentType CPU_4 = register(new ComponentType(
            "cpu4", "CPU (host cores / 4)", Material.NETHERITE_SCRAP, 8, Category.PARTS, ComponentSlot.CPU,
            "cpu_divided_by_4", "A quarter of the host's cores.", 4));

    public static final ComponentType CPU_6 = register(new ComponentType(
            "cpu6", "CPU (host cores / 6)", Material.NETHERITE_SCRAP, 6, Category.PARTS, ComponentSlot.CPU,
            "cpu_divided_by_6", "A sixth of the host's cores.", 6));

    /**
     * The plain card, and deliberately still a card rather than a particular one.
     *
     * <p>Every machine built before the tiers below has this fitted, so it has to keep meaning
     * "whatever the guest profile wants". The named cards override that; this one defers.
     */
    public static final ComponentType GPU = register(new ComponentType(
            "gpu", "Graphics Card", Material.DAYLIGHT_DETECTOR, 12, Category.PARTS, ComponentSlot.GPU,
            "gpu", "Drives the monitor. Leaves the choice to the guest profile.", 0, null));

    public static final ComponentType GPU_CIRRUS = register(new ComponentType(
            "gpu_cirrus", "Cirrus Logic Card", Material.DAYLIGHT_DETECTOR, 6, Category.PARTS,
            ComponentSlot.GPU, "gpu",
            "The card Windows 95 and 98 have drivers for. No use to anything newer.",
            0, com.acclash.vmcomputers.emu.VmSpec.Vga.CIRRUS));

    public static final ComponentType GPU_VGA = register(new ComponentType(
            "gpu_vga", "VGA Card", Material.DAYLIGHT_DETECTOR, 10, Category.PARTS,
            ComponentSlot.GPU, "gpu",
            "Works on everything, and can be asked for a resolution.",
            0, com.acclash.vmcomputers.emu.VmSpec.Vga.STD));

    public static final ComponentType GPU_SVGA = register(new ComponentType(
            "gpu_svga", "SVGA Card", Material.DAYLIGHT_DETECTOR, 12, Category.PARTS,
            ComponentSlot.GPU, "gpu",
            "VMware SVGA II. Windows 2000 and XP know this one.",
            0, com.acclash.vmcomputers.emu.VmSpec.Vga.VMWARE));

    public static final ComponentType GPU_VIRTIO = register(new ComponentType(
            "gpu_virtio", "Virtio GPU", Material.DAYLIGHT_DETECTOR, 16, Category.PARTS,
            ComponentSlot.GPU, "gpu",
            "The fast one. Needs a guest new enough to have virtio drivers.",
            0, com.acclash.vmcomputers.emu.VmSpec.Vga.VIRTIO));

    public static final ComponentType RAM_64M = register(ram("ram64m", "64 MB", 2, 64));
    public static final ComponentType RAM_128M = register(ram("ram128m", "128 MB", 2, 128));
    public static final ComponentType RAM_256M = register(ram("ram256m", "256 MB", 3, 256));
    public static final ComponentType RAM_512M = register(ram("ram512m", "512 MB", 4, 512));
    public static final ComponentType RAM_1G = register(ram("ram1g", "1 GB", 6, 1024));
    public static final ComponentType RAM_2G = register(ram("ram2g", "2 GB", 8, 2048));
    public static final ComponentType RAM_4G = register(ram("ram4g", "4 GB", 14, 4096));

    public static final ComponentType HARD_DRIVE = register(new ComponentType(
            "harddrive", "Hard Drive", Material.IRON_BLOCK, 6, Category.PARTS, ComponentSlot.HARD_DRIVE,
            "harddrive", "Where the guest operating system lives."));

    public static final ComponentType KEYBOARD = register(new ComponentType(
            "keyboard", "Keyboard", Material.POLISHED_BLACKSTONE_PRESSURE_PLATE, 4,
            Category.PERIPHERALS, ComponentSlot.KEYBOARD, "keyboard", "Sits on the desk."));

    public static final ComponentType MOUSE = register(new ComponentType(
            "mouse", "Mouse", Material.QUARTZ, 4, Category.PERIPHERALS, ComponentSlot.MOUSE,
            "mouse", "Sits on the desk."));

    // Monitors are the one component whose tier changes the shape of the build rather than a
    // number inside it: the size fitted is the size the screen is assembled at. Prices climb with
    // panel count, since a bigger screen is genuinely more of the plugin's frame budget.
    public static final ComponentType MONITOR_SMALL = register(monitor(
            "monitor_small", MonitorSize.SMALL, 10));
    public static final ComponentType MONITOR_MEDIUM = register(monitor(
            "monitor_medium", MonitorSize.MEDIUM, 16));
    public static final ComponentType MONITOR_LARGE = register(monitor(
            "monitor_large", MonitorSize.LARGE, 24));
    public static final ComponentType MONITOR_XLARGE = register(monitor(
            "monitor_xlarge", MonitorSize.XLARGE, 40));

    private static ComponentType monitor(String id, MonitorSize size, int price) {
        return new ComponentType(id, size.name().charAt(0) + size.name().substring(1).toLowerCase(
                Locale.ROOT) + " Monitor", Material.ITEM_FRAME, price, Category.PERIPHERALS,
                ComponentSlot.MONITOR,
                size.form() == MonitorSize.Form.PROJECTOR ? "walltv" : "flatscreen",
                size.describe() + ".", size.ordinal());
    }

    /** The monitor size this component builds, or null if it is not a monitor. */
    public MonitorSize monitorSize() {
        return slot == ComponentSlot.MONITOR ? MonitorSize.values()[rating] : null;
    }

    /**
     * RAM is one shape in seven capacities, so it is one icon in seven names. A thin gold bar is
     * about as close as vanilla gets to a stick of memory, and the tier is in the name anyway.
     */
    private static ComponentType ram(String id, String capacity, int price, int megabytes) {
        return new ComponentType(id, capacity + " RAM", Material.LIGHT_WEIGHTED_PRESSURE_PLATE,
                price, Category.PARTS, ComponentSlot.RAM, id,
                "Guest memory. A modern desktop needs 4 GB.", megabytes);
    }

    // ---- instance --------------------------------------------------------

    private final String id;
    private final String displayName;
    private final Material icon;
    private final int price;
    private final Category category;
    private final ComponentSlot slot;
    private final int rating;
    private final String modelName;
    private final String description;
    private final com.acclash.vmcomputers.emu.VmSpec.Vga vga;

    private ComponentType(String id, String displayName, Material icon, int price,
                          Category category, ComponentSlot slot, String modelName,
                          String description) {
        this(id, displayName, icon, price, category, slot, modelName, description, 0);
    }

    private ComponentType(String id, String displayName, Material icon, int price,
                          Category category, ComponentSlot slot, String modelName,
                          String description, int rating) {
        this(id, displayName, icon, price, category, slot, modelName, description, rating, null);
    }

    private ComponentType(String id, String displayName, Material icon, int price,
                          Category category, ComponentSlot slot, String modelName,
                          String description, int rating,
                          com.acclash.vmcomputers.emu.VmSpec.Vga vga) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.price = price;
        this.category = category;
        this.slot = slot;
        this.modelName = modelName;
        this.description = description;
        this.rating = rating;
        this.vga = vga;
    }

    /**
     * The adapter this card gives the guest, or null to leave the profile's own choice alone.
     *
     * <p>Null is what the plain graphics card has, and it is why fitting one changes nothing: it is
     * a card, not a particular card. The tiers below are particular, and picking the wrong one is
     * visible immediately, which is the point -- a Windows 95 desktop stuck at sixteen colours is a
     * problem a player can see and fix by buying a different card.
     */
    public com.acclash.vmcomputers.emu.VmSpec.Vga vga() {
        return vga;
    }

    private static ComponentType register(ComponentType type) {
        REGISTRY.put(type.id, type);
        return type;
    }

    /** Stable identifier, used in persistent data and configuration. */
    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public Material icon() {
        return icon;
    }

    /**
     * The icon to show for an empty bay of this kind: this component's head if one is configured,
     * otherwise the plain material. Without it a fitted GPU would be a card and an empty graphics
     * bay a daylight detector, which reads as two unrelated things.
     */
    public ItemStack emptyIconStack() {
        ItemStack head = HeadTextures.head(id, HEAD_TEXTURES.get(id), 1);
        return head != null ? head : new ItemStack(icon);
    }

    /** Whether this part has a head texture, rather than falling back to its item icon. */
    public boolean hasHead() {
        String value = HEAD_TEXTURES.get(id);
        return value != null && !value.isEmpty();
    }

    /** Price in Auros. The number is the mod's; only the currency differs. */
    public int price() {
        return price;
    }

    public Category category() {
        return category;
    }

    /**
     * The number that makes this tier different from its siblings: megabytes for RAM, and for a
     * CPU the divisor applied to the host's core count (the mod's "divided by N" naming). Zero for
     * anything with only one tier.
     */
    public int rating() {
        return rating;
    }

    /** Which bay this installs into, or null for a peripheral that is never installed. */
    public ComponentSlot slot() {
        return slot;
    }

    /** Every component that fits a given bay, cheapest tier first as declared. */
    public static List<ComponentType> forSlot(ComponentSlot slot) {
        List<ComponentType> out = new ArrayList<ComponentType>();
        for (ComponentType type : REGISTRY.values()) {
            if (type.slot == slot) {
                out.add(type);
            }
        }
        return out;
    }

    /** Name of the {@link PartModel} that draws this in the world, or null if it is never placed. */
    public String modelName() {
        return modelName;
    }

    public String description() {
        return description;
    }

    /** The model, or null when it is not placed in the world or failed to load. */
    public PartModel model() {
        return modelName == null ? null : PartModels.get(modelName);
    }

    /**
     * Builds the inventory representation: the icon, named and described, tagged so the plugin can
     * recognise it again when a player drops it into a case.
     */
    public ItemStack toItemStack(int amount) {
        // A head if this part has a texture, the vanilla stand-in otherwise. Falling back rather
        // than requiring a full set means textures can be filled in one part at a time.
        ItemStack stack = HeadTextures.head(id, HEAD_TEXTURES.get(id), amount);
        if (stack == null) {
            stack = new ItemStack(icon, amount);
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RESET + "" + ChatColor.AQUA + displayName);
            List<String> lore = new ArrayList<String>();
            lore.add(ChatColor.GRAY + description);
            lore.add("");
            lore.add(ChatColor.GOLD + "Price: " + Currency.format(price));
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(key(), PersistentDataType.STRING, id);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** Reads the component back off an item stack, or null if it is not one of ours. */
    public static ComponentType of(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        String id = meta.getPersistentDataContainer().get(key(), PersistentDataType.STRING);
        return id == null ? null : REGISTRY.get(id);
    }

    private static NamespacedKey key() {
        return new NamespacedKey(VMComputers.getPlugin(), "componentType");
    }

    public static ComponentType byId(String id) {
        return REGISTRY.get(id);
    }

    /**
     * What a computer built by {@code /vmcomputers create} comes fitted with.
     *
     * <p>That command builds an entire machine in one go, so it produces a working one rather than
     * an empty case; the ordering and assembly route is the piecemeal path. It is also what legacy
     * computers are backfilled with, since they predate components entirely and used to boot with
     * 4 GB hardcoded -- which is what the 4 GB stick here preserves.
     */
    public static Map<ComponentSlot, ComponentType> defaultLoadout(MonitorSize size) {
        Map<ComponentSlot, ComponentType> loadout =
                new LinkedHashMap<ComponentSlot, ComponentType>();
        loadout.put(ComponentSlot.MOTHERBOARD, MOTHERBOARD_64);
        loadout.put(ComponentSlot.CPU, CPU_4);
        loadout.put(ComponentSlot.RAM, RAM_4G);
        loadout.put(ComponentSlot.GPU, GPU);
        loadout.put(ComponentSlot.HARD_DRIVE, HARD_DRIVE);
        loadout.put(ComponentSlot.KEYBOARD, KEYBOARD);
        loadout.put(ComponentSlot.MOUSE, MOUSE);
        loadout.put(ComponentSlot.MONITOR, forMonitorSize(size));
        return loadout;
    }

    /** The monitor component that builds a given size. */
    public static ComponentType forMonitorSize(MonitorSize size) {
        for (ComponentType type : REGISTRY.values()) {
            if (type.slot == ComponentSlot.MONITOR && type.monitorSize() == size) {
                return type;
            }
        }
        return MONITOR_LARGE;
    }

    public static List<ComponentType> all() {
        return Collections.unmodifiableList(new ArrayList<ComponentType>(REGISTRY.values()));
    }

    public static List<ComponentType> inCategory(Category category) {
        List<ComponentType> out = new ArrayList<ComponentType>();
        for (ComponentType type : REGISTRY.values()) {
            if (type.category == category) {
                out.add(type);
            }
        }
        return out;
    }

    @Override
    public String toString() {
        return "ComponentType(" + id + ")";
    }
}
