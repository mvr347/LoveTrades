package me.lovelace.loveTrades.manager;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ограничения торговли: запрет в PvP, кулдаун между одной и той же парой игроков
 * и лимит объёма предложения в стаках.
 */
public class TradeRestrictions {

    public static final String BYPASS_COMBAT = "lovetrades.bypass.combat";
    public static final String BYPASS_COOLDOWN = "lovetrades.bypass.cooldown";
    public static final String BYPASS_STACK_LIMIT = "lovetrades.bypass.stack-limit";

    private final ConfigManager config;

    /** Время последней завершённой торговли по паре игроков (ключ — pairKey). */
    private final Map<String, Long> lastTradeAt = new HashMap<>();

    public TradeRestrictions(ConfigManager config) {
        this.config = config;
    }

    // ── PvP ───────────────────────────────────────────────────────────────────

    /**
     * Находится ли игрок в бою. Если LoveCore установлен, спрашиваем его
     * {@code CombatState} — он знает и про метку стороннего боевого плагина, и про войну или
     * осаду, чего одна метка не знает: причина отказа «нельзя торговать во время осады»
     * читается лучше, чем общее «вы в бою». Ядра нет или служба ещё не поднялась — падаем на
     * прежнюю мягкую интеграцию через Bukkit Metadata: DeluxeCombat и большинство
     * combat/antirelog-плагинов помечают игрока настраиваемым ключом на время схватки.
     */
    public boolean isInCombat(Player player) {
        if (!config.isBlockInCombat()) return false;
        if (player.hasPermission(BYPASS_COMBAT)) return false;

        if (org.bukkit.Bukkit.getPluginManager().getPlugin("LoveCore") != null) {
            try {
                java.util.Optional<Boolean> fromCore = dev.lovelace.lovecore.api.LoveCore
                        .service(dev.lovelace.lovecore.api.combat.CombatState.class)
                        .map(state -> state.inCombat(player.getUniqueId()));
                if (fromCore.isPresent()) {
                    return fromCore.get();
                }
            } catch (Throwable ignored) {
                // Ядро есть, но служба ещё не поднялась или контракт изменился — падаем на метку.
            }
        }
        return hasCombatMetadata(player);
    }

    private boolean hasCombatMetadata(Player player) {
        for (String key : config.getCombatMetadataKeys()) {
            for (MetadataValue value : player.getMetadata(key)) {
                if (value.asBoolean()) return true;
            }
        }
        return false;
    }

    // ── Кулдаун пары ──────────────────────────────────────────────────────────

    /**
     * Сколько секунд паре осталось ждать до следующей торговли.
     * 0 — можно торговать. Кулдаун общий на пару: обход хотя бы одним
     * из игроков снимает ограничение для этой пары.
     */
    public long remainingCooldownSeconds(Player first, Player second) {
        int cooldown = config.getPairCooldownSeconds();
        if (cooldown <= 0) return 0L;
        if (first.hasPermission(BYPASS_COOLDOWN) || second.hasPermission(BYPASS_COOLDOWN)) return 0L;

        Long last = lastTradeAt.get(pairKey(first.getUniqueId(), second.getUniqueId()));
        if (last == null) return 0L;

        long elapsed = System.currentTimeMillis() - last;
        long remaining = cooldown * 1000L - elapsed;
        return remaining <= 0 ? 0L : (remaining + 999L) / 1000L;
    }

    /** Зафиксировать завершённую торговлю — с этого момента для пары идёт кулдаун. */
    public void recordCompletedTrade(UUID first, UUID second) {
        if (config.getPairCooldownSeconds() <= 0) return;
        // Чистим истёкшие записи здесь же: карта пополняется только тут,
        // поэтому отдельный таймер очистки не нужен.
        pruneExpiredCooldowns();
        lastTradeAt.put(pairKey(first, second), System.currentTimeMillis());
    }

    /** Убрать записи, кулдаун по которым уже истёк, чтобы карта не росла бесконечно. */
    public void pruneExpiredCooldowns() {
        int cooldown = config.getPairCooldownSeconds();
        if (lastTradeAt.isEmpty()) return;
        if (cooldown <= 0) {
            lastTradeAt.clear();
            return;
        }

        long cutoff = System.currentTimeMillis() - cooldown * 1000L;
        Iterator<Map.Entry<String, Long>> it = lastTradeAt.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue() <= cutoff) it.remove();
        }
    }

    private static String pairKey(UUID first, UUID second) {
        // Порядок игроков не важен: ключ одинаков для (A,B) и (B,A).
        return first.compareTo(second) <= 0
            ? first + "|" + second
            : second + "|" + first;
    }

    // ── Лимит стаков ──────────────────────────────────────────────────────────

    /**
     * Сколько стаков занимает предложение в указанных слотах.
     * Одинаковые предметы из разных слотов складываются, поэтому два полустака
     * железа считаются одним стаком, а не двумя. Нестакающиеся предметы
     * (оружие, броня, инструменты) — по стаку за штуку.
     */
    public int countStacks(Inventory inventory, int[] slots) {
        List<ItemStack> kinds = new ArrayList<>();
        List<Integer> totals = new ArrayList<>();

        for (int slot : slots) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;

            int index = -1;
            for (int i = 0; i < kinds.size(); i++) {
                if (kinds.get(i).isSimilar(item)) { index = i; break; }
            }
            if (index >= 0) {
                totals.set(index, totals.get(index) + item.getAmount());
            } else {
                kinds.add(item);
                totals.add(item.getAmount());
            }
        }

        int stacks = 0;
        for (int i = 0; i < kinds.size(); i++) {
            // Amount берём из накопленной суммы, а не из ItemStack — установка
            // количества выше maxStackSize у некоторых реализаций обрезается.
            int maxStackSize = Math.max(1, kinds.get(i).getMaxStackSize());
            int amount = totals.get(i);
            stacks += (amount + maxStackSize - 1) / maxStackSize;
        }
        return stacks;
    }

    /** Превышен ли лимит стаков в предложении игрока. */
    public boolean exceedsStackLimit(Player player, Inventory inventory, int[] slots) {
        int limit = effectiveStackLimit(player);
        if (limit <= 0) return false;
        return countStacks(inventory, slots) > limit;
    }

    /**
     * Лимит стаков для конкретного игрока: 0 или меньше — лимита нет
     * (в конфиге отключён или у игрока есть обход).
     */
    public int effectiveStackLimit(Player player) {
        int limit = config.getMaxStacksPerSide();
        if (limit <= 0) return 0;
        if (player != null && player.hasPermission(BYPASS_STACK_LIMIT)) return 0;
        return limit;
    }
}
