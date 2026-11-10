package me.lovelace.loveTrades.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;

class XpClickTest {

    @Test
    void leftTakesOneRightAddsOne() {
        assertEquals(-1, TradeInventoryListener.xpDelta(ClickType.LEFT, 5));
        assertEquals(1, TradeInventoryListener.xpDelta(ClickType.RIGHT, 5));
    }

    @Test
    void shiftUsesTheConfiguredStep() {
        assertEquals(-5, TradeInventoryListener.xpDelta(ClickType.SHIFT_LEFT, 5));
        assertEquals(10, TradeInventoryListener.xpDelta(ClickType.SHIFT_RIGHT, 10));
    }

    @Test
    void everyOtherClickDoesNothing() {
        for (ClickType type : new ClickType[] {ClickType.DOUBLE_CLICK, ClickType.MIDDLE, ClickType.NUMBER_KEY,
                ClickType.DROP, ClickType.CONTROL_DROP, ClickType.CREATIVE, ClickType.SWAP_OFFHAND}) {
            assertEquals(0, TradeInventoryListener.xpDelta(type, 5), type.name());
        }
    }
}
