package me.lovelace.loveTrades.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.lovelace.loveTrades.LoveTrades;
import me.lovelace.loveTrades.manager.ModifierManager;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public final class TradePlaceholderExpansion extends PlaceholderExpansion {

    private final LoveTrades plugin;
    private final ModifierManager modifierManager;

    public TradePlaceholderExpansion(LoveTrades plugin, ModifierManager modifierManager) {
        this.plugin = plugin;
        this.modifierManager = modifierManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "lovetrades";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Lovelace";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";
        return switch (params.toLowerCase()) {
            case "requests_enabled" -> modifierManager.isRequestsEnabled(player.getUniqueId()) ? "yes" : "no";
            default -> "";
        };
    }
}
