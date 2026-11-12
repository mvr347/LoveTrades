package me.lovelace.loveTrades.manager;

import java.util.Map;

/** Pure trade rules (no Bukkit types), so they can be unit-tested without a server. */
public final class TradeRules {
    private TradeRules() {
    }

    /**
     * "Pointless, but fair": both sides offered the very same things. The groups map a kind of item to the total
     * amount offered, so the order of slots and the way a pile is split into stacks do not matter. Both offers must
     * hold something, and no experience levels may change hands, otherwise the swap is not the same for the same.
     */
    public static <K> boolean identicalSwap(Map<K, Integer> left, Map<K, Integer> right, int xpLeft, int xpRight) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) return false;
        if (xpLeft != 0 || xpRight != 0) return false;
        return left.equals(right);
    }

    /**
     * New experience offer (in levels) after a click on the XP button. The offer never goes below 0 and never above what the
     * player owns right now ({@code available}) or the configured per-side maximum ({@code maxPerSide}, 0 or less = no extra cap).
     */
    public static int nextXpOffer(int current, int delta, int available, int maxPerSide) {
        int cap = Math.max(0, available);
        if (maxPerSide > 0) cap = Math.min(cap, maxPerSide);
        long next = (long) current + delta;
        return (int) Math.max(0, Math.min(cap, next));
    }

    /** The most levels the player can offer: what they own, limited by the configured maximum (0 or less = no cap). */
    public static int xpOfferCap(int available, int maxPerSide) {
        int cap = Math.max(0, available);
        return maxPerSide > 0 ? Math.min(cap, maxPerSide) : cap;
    }

    /** Default tradable slots per side by politeness step 0..6 (Изгой .. Идеально); Нейтральный (3) = 4. */
    public static final int[] DEFAULT_SLOTS_BY_POLITENESS = {1, 2, 3, 4, 6, 8, 9};

    /**
     * How many item slots a side may use at the given politeness step. {@code table} holds one entry per step 0..6;
     * a missing or short table falls back to the defaults, and a step outside the table is clamped to its ends.
     * The result is never below 1 and never above {@code zoneSize}.
     */
    public static int tradeSlots(int politenessLevel, int[] table, int zoneSize) {
        int[] t = (table == null || table.length == 0) ? DEFAULT_SLOTS_BY_POLITENESS : table;
        int idx = Math.max(0, Math.min(t.length - 1, politenessLevel));
        return Math.max(1, Math.min(zoneSize, t[idx]));
    }
}
