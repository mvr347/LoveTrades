package me.lovelace.loveTrades.manager;

import me.lovelace.loveTrades.LoveTrades;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public class ConfigManager {

    private final LoveTrades plugin;

    public ConfigManager(LoveTrades plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        me.lovelace.loveTrades.gui.TradeHeads.configure(plugin.getConfig().getConfigurationSection("heads"));
    }

    public void reload() {
        plugin.reloadConfig();
        me.lovelace.loveTrades.gui.TradeHeads.configure(plugin.getConfig().getConfigurationSection("heads"));
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

    /** Most experience levels one side may offer; 0 or less means only the player's own levels limit it. */
    public int getMaxXpLevelsPerSide() {
        return plugin.getConfig().getInt("restrictions.max-xp-levels-per-side", 0);
    }

    /** Item slots per side for politeness steps 0..6; empty or invalid = the built-in table. */
    public int[] getTradeSlotsByPoliteness() {
        List<Integer> list = plugin.getConfig().getIntegerList("restrictions.trade-slots-by-politeness");
        if (list.isEmpty()) return me.lovelace.loveTrades.manager.TradeRules.DEFAULT_SLOTS_BY_POLITENESS;
        return list.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Levels added or removed by a Shift-click on the XP button. */
    public int getXpShiftStep() {
        return Math.max(1, plugin.getConfig().getInt("settings.xp-shift-step", 5));
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
