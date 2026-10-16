package me.lovelace.loveTrades.manager;

import me.lovelace.loveTrades.LoveTrades;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Offline mailbox for items that could not be handed back to a trade participant. */
public class PendingReturnStore {

    private final LoveTrades plugin;
    private final File file;
    private final YamlConfiguration yaml;

    public PendingReturnStore(LoveTrades plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/pending-returns.yml");
        file.getParentFile().mkdirs();
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    public synchronized void add(UUID owner, List<ItemStack> items) {
        List<ItemStack> all = new ArrayList<>(load(owner));
        all.addAll(items);
        yaml.set("players." + owner, all);
        save();
    }

    /** Removes and returns everything stored for the player. */
    public synchronized List<ItemStack> take(UUID owner) {
        List<ItemStack> items = load(owner);
        if (!items.isEmpty()) {
            yaml.set("players." + owner, null);
            save();
        }
        return items;
    }

    private List<ItemStack> load(UUID owner) {
        List<ItemStack> out = new ArrayList<>();
        for (Object o : yaml.getList("players." + owner, List.of())) {
            if (o instanceof ItemStack i) out.add(i);
        }
        return out;
    }

    private void save() {
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось сохранить pending-returns.yml: " + e.getMessage());
        }
    }
}
