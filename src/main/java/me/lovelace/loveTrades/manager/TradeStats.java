package me.lovelace.loveTrades.manager;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player trade counters for the profile statistics: how many trades, how many items went out and
 * came in, how much the tax took, and with how many different people. Held in memory (placeholders are
 * read on the main thread) and saved to {@code data/stats.yml} in the background and on shutdown.
 */
public final class TradeStats {

    /** A read-only view of one player's counters. */
    public record Snapshot(int trades, long itemsSent, long itemsReceived, long itemsLostToTax, int partners) {
        static final Snapshot EMPTY = new Snapshot(0, 0, 0, 0, 0);
    }

    private static final class Counters {
        int trades;
        long sent;
        long received;
        long lostToTax;
        final Set<UUID> partners = new HashSet<>();
    }

    private final Map<UUID, Counters> byPlayer = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    /**
     * Records one finished trade for a player.
     *
     * @param sent     items the player put in (stack amounts added up)
     * @param gross    items the partner put in for this player, before tax
     * @param received items the player actually got after tax
     */
    public void record(UUID player, UUID partner, long sent, long gross, long received) {
        Counters c = byPlayer.computeIfAbsent(player, id -> new Counters());
        synchronized (c) {
            c.trades++;
            c.sent += Math.max(0, sent);
            c.received += Math.max(0, received);
            // A negative loss would mean the tax paid the player; never count that.
            c.lostToTax += Math.max(0, gross - received);
            c.partners.add(partner);
        }
        dirty = true;
    }

    public Snapshot get(UUID player) {
        Counters c = byPlayer.get(player);
        if (c == null) return Snapshot.EMPTY;
        synchronized (c) {
            return new Snapshot(c.trades, c.sent, c.received, c.lostToTax, c.partners.size());
        }
    }

    public void load(File file) {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) return;
        for (String key : players.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException invalid) {
                continue;
            }
            Counters c = new Counters();
            c.trades = players.getInt(key + ".trades");
            c.sent = players.getLong(key + ".sent");
            c.received = players.getLong(key + ".received");
            c.lostToTax = players.getLong(key + ".lost-to-tax");
            for (String partner : players.getStringList(key + ".partners")) {
                try {
                    c.partners.add(UUID.fromString(partner));
                } catch (IllegalArgumentException ignored) {
                    // a damaged entry costs one partner, not the file
                }
            }
            byPlayer.put(id, c);
        }
    }

    /** Writes the file when something changed since the last save. */
    public synchronized void saveIfDirty(File file) throws IOException {
        if (!dirty) return;
        dirty = false;
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Counters> entry : byPlayer.entrySet()) {
            Counters c = entry.getValue();
            String base = "players." + entry.getKey();
            synchronized (c) {
                yaml.set(base + ".trades", c.trades);
                yaml.set(base + ".sent", c.sent);
                yaml.set(base + ".received", c.received);
                yaml.set(base + ".lost-to-tax", c.lostToTax);
                List<String> partners = new ArrayList<>();
                for (UUID partner : c.partners) partners.add(partner.toString());
                yaml.set(base + ".partners", partners);
            }
        }
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        try {
            yaml.save(file);
        } catch (IOException e) {
            dirty = true;
            throw e;
        }
    }
}
