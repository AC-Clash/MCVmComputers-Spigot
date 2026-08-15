package com.acclash.vmcomputers.parts;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a head texture value into a player-head {@link ItemStack}.
 *
 * <p>An inventory slot renders an item and nothing else, so the shop and the case bays cannot use
 * the display-entity models the parts are drawn with in the world. A player head is the only
 * vanilla item whose texture a server can choose, which makes it the only way to get a graphics
 * card that looks like a graphics card into a menu.
 *
 * <p>The catch is worth naming: a head's texture is not stored in the item. The item carries a URL
 * on Mojang's texture server and the client fetches it from there, so the artwork lives on
 * infrastructure this project does not own, it loads asynchronously, and a client that cannot
 * reach it shows a plain head. That is the trade against a resource pack, which would be
 * self-hosted but has to be downloaded by every player.
 *
 * <p>Values are held in {@link ComponentType} beside each part's name and price, not in
 * configuration. They are artwork for a fixed catalogue rather than a server preference -- nobody
 * wants a different graphics card head per server -- and keeping them with the catalogue means one
 * place to look and no half-configured state where some bays are heads and some are stand-in items.
 */
public final class HeadTextures {

    private static final String TEXTURE_HOST = "https://textures.minecraft.net/texture/";

    /** Pulls the skin URL out of the decoded base64 blob head sites hand out. */
    private static final Pattern SKIN_URL =
            Pattern.compile("\"SKIN\"\\s*:\\s*\\{[^}]*\"url\"\\s*:\\s*\"([^\"]+)\"");

    /** A bare texture hash: the tail of a textures.minecraft.net URL. */
    private static final Pattern HASH = Pattern.compile("[0-9a-fA-F]{32,128}");

    /**
     * Parsed values, keyed by the raw string.
     *
     * <p>Menus redraw on every click and a redraw builds every icon, so decoding base64 and
     * validating a URL each time would be work repeated for no reason.
     */
    private static final Map<String, URL> CACHE = new ConcurrentHashMap<String, URL>();

    private HeadTextures() {
    }

    /**
     * Accepts whichever of the three forms a head site handed over.
     *
     * <p>Sites are inconsistent about this -- some give a base64 "value", some a bare hash, some a
     * full URL -- and telling them apart is cheap, so all three work rather than making whoever
     * pastes one convert it first.
     *
     * @return the skin URL, or null if the value is not any of them
     */
    static URL parse(String raw) {
        try {
            if (raw.startsWith("http://") || raw.startsWith("https://")) {
                return new URI(raw).toURL();
            }
            if (HASH.matcher(raw).matches()) {
                return new URI(TEXTURE_HOST + raw).toURL();
            }
            // Otherwise assume base64: {"textures":{"SKIN":{"url":"..."}}}
            String json = new String(Base64.getDecoder().decode(raw), StandardCharsets.UTF_8);
            Matcher matcher = SKIN_URL.matcher(json);
            if (matcher.find()) {
                return new URI(matcher.group(1)).toURL();
            }
            return null;
        } catch (IllegalArgumentException | URISyntaxException | MalformedURLException e) {
            return null;
        }
    }

    /**
     * A head wearing this texture, or null if the value is missing or unreadable.
     *
     * <p>The profile's UUID is derived from the identity rather than random, so the same part
     * always produces an identical stack. A random one would stop two otherwise identical items
     * from stacking and would churn the client's profile cache.
     *
     * @param identity   stable name for this head, used to derive the profile id
     * @param textureValue base64 value, bare hash or full URL
     */
    public static ItemStack head(String identity, String textureValue, int amount) {
        if (textureValue == null || textureValue.isEmpty()) {
            return null;
        }
        URL skin = CACHE.computeIfAbsent(textureValue, value -> {
            URL parsed = parse(value);
            if (parsed == null) {
                Bukkit.getLogger().warning("[VMComputers] Head texture for '" + identity
                        + "' is not a texture value, hash or URL; using its item icon instead.");
            }
            return parsed;
        });
        if (skin == null) {
            return null;
        }

        ItemStack stack = new ItemStack(Material.PLAYER_HEAD, amount);
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        PlayerProfile profile = Bukkit.createPlayerProfile(
                UUID.nameUUIDFromBytes(("vmcomputers:" + identity)
                        .getBytes(StandardCharsets.UTF_8)), null);
        PlayerTextures textures = profile.getTextures();
        textures.setSkin(skin);
        profile.setTextures(textures);
        meta.setOwnerProfile(profile);
        stack.setItemMeta(meta);
        return stack;
    }
}
