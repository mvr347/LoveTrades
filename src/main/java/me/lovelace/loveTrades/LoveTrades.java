package me.lovelace.loveTrades;

import me.lovelace.loveTrades.api.ClanIntegration;
import me.lovelace.loveTrades.command.TradeAdminCommand;
import me.lovelace.loveTrades.command.TradeCommand;
import me.lovelace.loveTrades.listener.PlayerProtectionListener;
import me.lovelace.loveTrades.listener.TradeInventoryListener;
import me.lovelace.loveTrades.listener.XpInputListener;
import me.lovelace.loveTrades.manager.ConfigManager;
import me.lovelace.loveTrades.manager.ModifierManager;
import me.lovelace.loveTrades.manager.TradeManager;
import me.lovelace.loveTrades.placeholder.TradePlaceholderExpansion;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class LoveTrades extends JavaPlugin {

    private ConfigManager configManager;
    private ModifierManager modifierManager;
    private TradeManager tradeManager;
    private ClanIntegration clanIntegration;

    @Override
    public void onEnable() {
        configManager   = new ConfigManager(this);
        modifierManager = new ModifierManager(this, configManager);
        tradeManager    = new TradeManager(this, configManager, modifierManager);

        TradeCommand      tradeCmd      = new TradeCommand(tradeManager, configManager, modifierManager);
        TradeAdminCommand tradeAdminCmd = new TradeAdminCommand(modifierManager, configManager);

        getCommand("trade").setExecutor(tradeCmd);
        getCommand("trade").setTabCompleter(tradeCmd);
        getCommand("tradeadmin").setExecutor(tradeAdminCmd);
        getCommand("tradeadmin").setTabCompleter(tradeAdminCmd);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new TradeInventoryListener(tradeManager, this), this);
        pm.registerEvents(new PlayerProtectionListener(tradeManager), this);
        pm.registerEvents(new XpInputListener(tradeManager, this), this);

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new TradePlaceholderExpansion(this, modifierManager).register();
            getLogger().info("PlaceholderAPI expansion зарегистрирован.");
        }

        getLogger().info("LoveTrades включён.");
    }

    @Override
    public void onDisable() {
        if (tradeManager != null) tradeManager.onDisable();
        if (modifierManager != null) modifierManager.savePlayersData();
        getLogger().info("LoveTrades выключён.");
    }

    /**
     * Register a clan integration so LoveTrades can check relationships.
     * Call this from your clan plugin's onEnable:
     *
     *   Plugin lt = Bukkit.getPluginManager().getPlugin("LoveTrades");
     *   if (lt instanceof LoveTrades love) love.setClanIntegration(new YourIntegration());
     */
    public void setClanIntegration(ClanIntegration integration) {
        this.clanIntegration = integration;
        if (integration != null) {
            getLogger().info("Клановая интеграция подключена: " + integration.getClass().getSimpleName());
        } else {
            getLogger().info("Клановая интеграция отключена.");
        }
    }

    public ClanIntegration getClanIntegration() { return clanIntegration; }

    public ConfigManager getConfigManager()     { return configManager; }
    public ModifierManager getModifierManager() { return modifierManager; }
    public TradeManager getTradeManager()       { return tradeManager; }
}
