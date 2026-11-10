package me.lovelace.loveTrades.gui;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Skull-texture buttons of the trade menu. Every key has a built-in texture, so a config.yml from before the heads existed
 * keeps working; {@code heads.<key>} in config.yml overrides it with another base64 texture.
 */
public final class TradeHeads {

    public static final String READY = "ready";
    public static final String NOT_READY = "not-ready";
    public static final String CANCEL = "cancel";
    public static final String XP = "xp";
    public static final String INFO = "info";

    /** Texture hashes already used by the other Love* menus (LoveDuels ready/not-ready, LoveClans barrier/experience/info). */
    public static final Map<String, String> DEFAULT_HASHES = defaults();

    private static volatile ConfigurationSection overrides;

    private TradeHeads() {
    }

    private static Map<String, String> defaults() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(READY, "a92e31ffb551c0110e96ea2d65fa3a5ff699f5923d318e5f128e1f9b5e76e");
        m.put(NOT_READY, "beb588b35d3c8008a2879509b23b361a603b5193952f4c40974f07a4aab47a");
        m.put(CANCEL, "afd2400002ad9fbbbd0066941eb5b1a384ab9b0e48a178ee96e4d129a5208654");
        m.put(XP, "48b81d8935fc101e35f3e54f537b612822f18faf752a90037cc4215a5dc881ef");
        m.put(INFO, "f80d3295d3d9abd62776abcb8da756f298a545fee9498c4f69a1c2c78524c824");
        return java.util.Collections.unmodifiableMap(m);
    }

    /** Reads {@code heads.*} overrides; call on enable and after a config reload. */
    public static void configure(ConfigurationSection headsSection) {
        overrides = headsSection;
    }

    /** base64 value of the Mojang texture URL for a texture hash. */
    public static String urlToBase64(String textureHash) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + textureHash + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /** Texture for a key: the config override if set, otherwise the built-in one. */
    public static String texture(String key) {
        ConfigurationSection section = overrides;
        if (section != null) {
            String custom = section.getString(key, "");
            if (custom != null && !custom.isBlank()) return custom.trim();
        }
        return urlToBase64(DEFAULT_HASHES.getOrDefault(key, DEFAULT_HASHES.get(INFO)));
    }

    /** A head button; {@code name} and {@code lore} use legacy {@code &} codes. */
    public static ItemStack head(String key, String name, List<String> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        PlayerProfile profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(("lovetrades:" + key).getBytes(StandardCharsets.UTF_8)));
        profile.setProperty(new ProfileProperty("textures", texture(key)));
        meta.setPlayerProfile(profile);
        meta.displayName(legacy(name));
        if (lore != null && !lore.isEmpty()) {
            List<Component> lines = new ArrayList<>(lore.size());
            for (String line : lore) lines.add(legacy(line));
            meta.lore(lines);
        }
        item.setItemMeta(meta);
        return item;
    }

    private static Component legacy(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s).decoration(TextDecoration.ITALIC, false);
    }
}
