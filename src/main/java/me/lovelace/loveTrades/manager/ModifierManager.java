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
     * Returns 0 if lovetrades.bypass.tax is set (extraModifier is also suppressed in that case).
     */
    public double getEffectiveTax(Player receiver, double extraModifier) {
        if (receiver.hasPermission("lovetrades.bypass.tax")) return 0.0;
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
     * Clamps a tax rate to [0, 100]. A negative rate (ally/VIP "bonus") must never multiply
     * traded goods: that would mint items/XP out of nothing, so it is treated as no tax.
     */
    public static double clampRate(double effectiveTax) {
        return Math.max(0.0, Math.min(100.0, effectiveTax));
    }

    /** Applies tax to a stackable item amount. Rate is clamped to [0, 100]; result never exceeds the input. */
    public static int applyItemTax(int amount, double effectiveTax) {
        return Math.max(0, (int) Math.floor(amount * (1.0 - clampRate(effectiveTax) / 100.0)));
    }

    /** Applies tax to XP levels. Rate is clamped to [0, 100]; result never exceeds the input. */
    public static int applyXpTax(int amount, double effectiveTax) {
        return Math.max(0, (int) Math.floor(amount * (1.0 - clampRate(effectiveTax) / 100.0)));
    }
}
