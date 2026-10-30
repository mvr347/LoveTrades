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
}
