package me.lovelace.loveTrades.manager;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TradeRulesTest {

    @Test
    void theSameThingsOnBothSidesIsAPointlessSwap() {
        assertTrue(TradeRules.identicalSwap(Map.of("DIAMOND", 5), Map.of("DIAMOND", 5), 0, 0));
        assertTrue(TradeRules.identicalSwap(Map.of("DIAMOND", 5, "GOLD", 2), Map.of("GOLD", 2, "DIAMOND", 5), 0, 0));
    }

    @Test
    void anotherAmountOrAnotherKindIsARealTrade() {
        assertFalse(TradeRules.identicalSwap(Map.of("DIAMOND", 5), Map.of("DIAMOND", 4), 0, 0));
        assertFalse(TradeRules.identicalSwap(Map.of("DIAMOND", 5), Map.of("EMERALD", 5), 0, 0));
        assertFalse(TradeRules.identicalSwap(Map.of("DIAMOND", 5), Map.of("DIAMOND", 5, "GOLD", 1), 0, 0));
    }

    @Test
    void anEmptySideIsNotASwap() {
        assertFalse(TradeRules.identicalSwap(Map.of(), Map.of(), 0, 0));
        assertFalse(TradeRules.identicalSwap(Map.of("DIAMOND", 1), Map.of(), 0, 0));
        assertFalse(TradeRules.identicalSwap(null, Map.of("DIAMOND", 1), 0, 0));
    }

    @Test
    void experienceChangingHandsMakesItARealTrade() {
        assertFalse(TradeRules.identicalSwap(Map.of("DIAMOND", 5), Map.of("DIAMOND", 5), 3, 0));
        assertFalse(TradeRules.identicalSwap(Map.of("DIAMOND", 5), Map.of("DIAMOND", 5), 0, 3));
        assertFalse(TradeRules.identicalSwap(Map.of("DIAMOND", 5), Map.of("DIAMOND", 5), 3, 3),
                "even equal levels move from one player to the other");
    }
}
