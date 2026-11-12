package me.lovelace.loveTrades.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TradeHeadsTest {
    @Test
    void yourOfferAndThePartnersOfferHaveDifferentHeads() {
        assertNotNull(TradeHeads.DEFAULT_HASHES.get(TradeHeads.INFO));
        assertNotNull(TradeHeads.DEFAULT_HASHES.get(TradeHeads.INFO_PARTNER));
        assertNotEquals(TradeHeads.DEFAULT_HASHES.get(TradeHeads.INFO),
                TradeHeads.DEFAULT_HASHES.get(TradeHeads.INFO_PARTNER));
    }
}
