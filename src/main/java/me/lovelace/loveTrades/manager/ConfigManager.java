package me.lovelace.loveTrades.manager;

import me.lovelace.loveTrades.LoveTrades;
import org.bukkit.configuration.file.FileConfiguration;

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

    public String getMessage(String key) {
        String msg = plugin.getConfig().getString("messages." + key, "");
        if (msg.isEmpty()) plugin.getLogger().warning("Missing message key: " + key);
        return msg;
    }

    public FileConfiguration getRaw() {
        return plugin.getConfig();
    }
}
