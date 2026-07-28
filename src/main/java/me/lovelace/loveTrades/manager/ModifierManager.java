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
import java.util.concurrent.ConcurrentHashMap;

public class ModifierManager {

    private final LoveTrades plugin;
    private final ConfigManager configManager;

    private final File playersFile;
    private YamlConfiguration playersConfig;
    private final Map<UUID, Double> playerTaxCache = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> requestsEnabledCache = new ConcurrentHashMap<>();

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
                if (section.isSet(key + ".requests-enabled")) {
                    requestsEnabledCache.put(uuid, section.getBoolean(key + ".requests-enabled", true));
                }
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
     * = group tax + individual tax + extraModifier (pass ally bonus here as a negative value).
     * Returns 0 if axtrades.bypass.tax is set (extraModifier is also suppressed in that case).
     */
    public double getEffectiveTax(Player receiver, double extraModifier) {
        if (receiver.hasPermission("axtrades.bypass.tax")) return 0.0;
        double groupTax  = resolveGroupTax(receiver);
        double playerTax = playerTaxCache.getOrDefault(receiver.getUniqueId(), 0.0);
        return groupTax + playerTax + extraModifier;
    }

    /** Convenience overload with no extra modifier. */
    public double getEffectiveTax(Player receiver) {
        return getEffectiveTax(receiver, 0.0);
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

    /**
     * Whether this player currently accepts incoming trade requests. Defaults to true.
     */
    public boolean isRequestsEnabled(UUID uuid) {
        return requestsEnabledCache.getOrDefault(uuid, true);
    }

    public void setRequestsEnabled(UUID uuid, boolean enabled) {
        requestsEnabledCache.put(uuid, enabled);
        playersConfig.set("players." + uuid + ".requests-enabled", enabled);
        savePlayersData();
    }

    /**
     * Flips the player's requests-enabled state and returns the new value.
     */
    public boolean toggleRequestsEnabled(UUID uuid) {
        boolean newState = !isRequestsEnabled(uuid);
        setRequestsEnabled(uuid, newState);
        return newState;
    }

    /**
     * Applies tax to a stackable item amount.
     * Positive tax reduces the amount (items burned). Negative tax creates a bonus
     * (e.g. -10 means receiver gets 10% extra items). Cap: max 100% tax (0 items),
     * no cap on the negative (bonus) side.
     */
    public int applyItemTax(int amount, double effectiveTax) {
        double rate = Math.min(100.0, effectiveTax); // allow negative (bonus), cap at 100
        return Math.max(0, (int) Math.floor(amount * (1.0 - rate / 100.0)));
    }

    /**
     * Applies tax to XP levels.
     * Negative tax (bonus) can increase XP; result is clamped at 0.
     */
    public int applyXpTax(int amount, double effectiveTax) {
        return Math.max(0, (int) Math.floor(amount * (1.0 - effectiveTax / 100.0)));
    }
}
