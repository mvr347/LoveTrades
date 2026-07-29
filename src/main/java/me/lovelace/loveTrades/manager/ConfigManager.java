package me.lovelace.loveTrades.manager;

import me.lovelace.loveTrades.LoveTrades;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public class ConfigManager {

    private final LoveTrades plugin;

    public ConfigManager(LoveTrades plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
    }

    public void reload() {
        plugin.reloadConfig();
    }

    public int getRequestTimeout() {
        return plugin.getConfig().getInt("settings.request-timeout", 30);
    }

    public int getCountdownDuration() {
        return plugin.getConfig().getInt("settings.countdown-duration", 3);
    }

    public int getInactivityTimeoutSeconds() {
        return plugin.getConfig().getInt("settings.inactivity-timeout-seconds", 120);
    }

    public boolean isBlockInCombat() {
        return plugin.getConfig().getBoolean("restrictions.block-in-combat", true);
    }

    public List<String> getCombatMetadataKeys() {
        List<String> keys = plugin.getConfig().getStringList("restrictions.combat-metadata-keys");
        return keys.isEmpty() ? List.of("in_combat") : keys;
    }

    public int getPairCooldownSeconds() {
        return plugin.getConfig().getInt("restrictions.pair-cooldown-seconds", 180);
    }

    public int getMaxStacksPerSide() {
        return plugin.getConfig().getInt("restrictions.max-stacks-per-side", 5);
    }

    public String getMessage(String key) {
        String msg = plugin.getConfig().getString("messages." + key, "");
        if (msg.isEmpty()) plugin.getLogger().warning("Missing message key: " + key);
        return msg;
    }

    /**
     * Как {@link #getMessage(String)}, но с запасным текстом вместо предупреждения. Нужно для
     * ключей, добавленных позже: config.yml у существующих серверов автоматически не мигрируется,
     * и без запасного варианта такое сообщение молча стало бы пустым.
     */
    public String getMessage(String key, String fallback) {
        String msg = plugin.getConfig().getString("messages." + key, "");
        return msg.isEmpty() ? fallback : msg;
    }

    public boolean isClanEnabled()    { return plugin.getConfig().getBoolean("clan.enabled",     false); }
    public double  getClanAllyBonus() { return plugin.getConfig().getDouble("clan.ally-bonus",  -10.0); }
    public boolean isClanEnemyBlock() { return plugin.getConfig().getBoolean("clan.enemy-block", true); }

    public FileConfiguration getRaw() {
        return plugin.getConfig();
    }
}
