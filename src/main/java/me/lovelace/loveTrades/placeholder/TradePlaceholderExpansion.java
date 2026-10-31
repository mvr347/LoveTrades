package me.lovelace.loveTrades.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.lovelace.loveTrades.LoveTrades;
import me.lovelace.loveTrades.manager.ModifierManager;
import me.lovelace.loveTrades.manager.TradeStats;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public final class TradePlaceholderExpansion extends PlaceholderExpansion {

    private final LoveTrades plugin;
    private final ModifierManager modifierManager;
    private final TradeStats stats;

    public TradePlaceholderExpansion(LoveTrades plugin, ModifierManager modifierManager, TradeStats stats) {
        this.plugin = plugin;
        this.modifierManager = modifierManager;
        this.stats = stats;
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
            // Profile statistics: counters of finished trades and the tax the player pays on what they receive.
            case "trades_total" -> String.valueOf(stats.get(player.getUniqueId()).trades());
            case "items_sent" -> String.valueOf(stats.get(player.getUniqueId()).itemsSent());
            case "items_received" -> String.valueOf(stats.get(player.getUniqueId()).itemsReceived());
            case "items_lost_to_tax" -> String.valueOf(stats.get(player.getUniqueId()).itemsLostToTax());
            case "partners" -> String.valueOf(stats.get(player.getUniqueId()).partners());
            case "tax_percent" -> percent(taxOf(player));
            default -> "";
        };
    }

    /** The rate applied to what this player receives; the full rule needs them online, so offline uses the personal rate. */
    private double taxOf(OfflinePlayer player) {
        var online = player.getPlayer();
        return online != null ? modifierManager.getEffectiveTax(online) : modifierManager.getPlayerTax(player.getUniqueId());
    }

    static String percent(double rate) {
        double clamped = ModifierManager.clampRate(rate);
        return clamped == Math.rint(clamped) ? String.valueOf((long) clamped) : String.format(java.util.Locale.ROOT, "%.1f", clamped);
    }
}
