package me.lovelace.loveTrades.manager;

import me.lovelace.loveTrades.LoveTrades;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ModifierManager {

    private final LoveTrades plugin;
    private final ConfigManager configManager;

    private final File playersFile;
    private YamlConfiguration playersConfig;
    private final Map<UUID, Double> playerTaxCache = new HashMap<>();

    public ModifierManager(LoveTrades plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.playersFile = new File(plugin.getDataFolder(), "data/players.yml");
        loadPlayersData();
    }

    private void loadPlayersData() {
        if (!playersFile.exists()) {
            playersFile.getParentFile().mkdirs();
            playersConfig = new YamlConfiguration();
            return;
        }
        playersConfig = YamlConfiguration.loadConfiguration(playersFile);
        ConfigurationSection section = playersConfig.getConfigurationSection("players");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                double tax = section.getDouble(key + ".tax", 0.0);
                playerTaxCache.put(uuid, tax);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void savePlayersData() {
        try {
            playersConfig.save(playersFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось сохранить players.yml: " + e.getMessage());
        }
    }

    /**
     * Effective tax applied to items/XP received by this player.
     * = group tax (from first matching permission in config) + individual player tax.
     * Returns 0 if axtrades.bypass.tax is set.
     */
    public double getEffectiveTax(Player receiver) {
        if (receiver.hasPermission("axtrades.bypass.tax")) return 0.0;
        double groupTax = resolveGroupTax(receiver);
        double playerTax = playerTaxCache.getOrDefault(receiver.getUniqueId(), 0.0);
        return groupTax + playerTax;
    }

    private double resolveGroupTax(Player player) {
        ConfigurationSection groups = configManager.getRaw().getConfigurationSection("modifiers.groups");
        if (groups == null) return 0.0;
        for (String groupName : groups.getKeys(false)) {
            String perm = groups.getString(groupName + ".permission", "");
            if (!perm.isEmpty() && player.hasPermission(perm)) {
                return groups.getDouble(groupName + ".tax", 0.0);
            }
        }
        return 0.0;
    }

    public double getPlayerTax(UUID uuid) {
        return playerTaxCache.getOrDefault(uuid, 0.0);
    }

    public void setPlayerTax(UUID uuid, double tax) {
        playerTaxCache.put(uuid, tax);
        playersConfig.set("players." + uuid + ".tax", tax);
        savePlayersData();
    }

    public void removePlayerTax(UUID uuid) {
        playerTaxCache.remove(uuid);
        playersConfig.set("players." + uuid, null);
        savePlayersData();
    }

    /** Applies tax to a stackable item amount. Clamps effective rate to [0, 100]. */
    public int applyItemTax(int amount, double effectiveTax) {
        double rate = Math.max(0.0, Math.min(100.0, effectiveTax));
        return (int) Math.floor(amount * (1.0 - rate / 100.0));
    }

    /** Applies tax to XP. Negative tax (bonus) can increase XP; result is clamped at 0. */
    public int applyXpTax(int amount, double effectiveTax) {
        return Math.max(0, (int) Math.floor(amount * (1.0 - effectiveTax / 100.0)));
    }
}
