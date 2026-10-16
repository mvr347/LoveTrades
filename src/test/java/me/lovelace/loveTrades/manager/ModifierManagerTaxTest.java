package me.lovelace.loveTrades.manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModifierManagerTaxTest {

    @Test
    void negativeTaxNeverMultipliesItems() {
        assertEquals(64, ModifierManager.applyItemTax(64, -10.0));
        assertEquals(64, ModifierManager.applyItemTax(64, -1000.0));
    }

    @Test
    void negativeTaxNeverMultipliesXp() {
        assertEquals(30, ModifierManager.applyXpTax(30, -25.0));
    }

    @Test
    void positiveTaxReducesAndCapsAtHundred() {
        assertEquals(32, ModifierManager.applyItemTax(64, 50.0));
        assertEquals(0, ModifierManager.applyItemTax(64, 150.0));
        assertEquals(0, ModifierManager.applyXpTax(10, 100.0));
    }

    @Test
    void clampRateBounds() {
        assertEquals(0.0, ModifierManager.clampRate(-5));
        assertEquals(100.0, ModifierManager.clampRate(500));
        assertEquals(12.5, ModifierManager.clampRate(12.5));
    }
}
