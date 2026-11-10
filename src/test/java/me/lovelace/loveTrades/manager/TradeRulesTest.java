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

    @Test
    void xpOfferStepsByOneAndNeverGoesBelowZero() {
        assertEquals(1, TradeRules.nextXpOffer(0, 1, 30, 0));
        assertEquals(4, TradeRules.nextXpOffer(5, -1, 30, 0));
        assertEquals(0, TradeRules.nextXpOffer(0, -1, 30, 0));
        assertEquals(0, TradeRules.nextXpOffer(3, -5, 30, 0), "a shift step cannot go below zero");
    }

    @Test
    void xpOfferNeverExceedsWhatThePlayerOwnsOrTheConfiguredMaximum() {
        assertEquals(30, TradeRules.nextXpOffer(29, 5, 30, 0));
        assertEquals(10, TradeRules.nextXpOffer(9, 5, 30, 10), "the configured maximum caps it below the player's levels");
        assertEquals(0, TradeRules.nextXpOffer(0, 1, 0, 0), "a player with no levels cannot offer any");
        assertEquals(0, TradeRules.nextXpOffer(0, 1, -3, 0));
    }

    @Test
    void anOfferAboveWhatTheyNowOwnIsPulledDownToIt() {
        assertEquals(12, TradeRules.nextXpOffer(20, 1, 12, 0), "levels were spent after offering");
        assertEquals(12, TradeRules.nextXpOffer(20, -1, 12, 0));
    }

    @Test
    void theOfferCapIsTheSmallerOfOwnedAndConfigured() {
        assertEquals(30, TradeRules.xpOfferCap(30, 0));
        assertEquals(30, TradeRules.xpOfferCap(30, -1));
        assertEquals(10, TradeRules.xpOfferCap(30, 10));
        assertEquals(4, TradeRules.xpOfferCap(4, 10));
        assertEquals(0, TradeRules.xpOfferCap(-2, 10));
    }
}
