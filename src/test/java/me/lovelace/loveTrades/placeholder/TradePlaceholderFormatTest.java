package me.lovelace.loveTrades.placeholder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TradePlaceholderFormatTest {
    @Test
    void wholePercentsHaveNoDecimals() {
        assertEquals("10", TradePlaceholderExpansion.percent(10.0));
        assertEquals("0", TradePlaceholderExpansion.percent(0));
    }

    @Test
    void fractionsKeepOneDecimal() {
        assertEquals("12.5", TradePlaceholderExpansion.percent(12.5));
    }

    @Test
    void aBonusOrAnOverflowIsClamped() {
        assertEquals("0", TradePlaceholderExpansion.percent(-5));
        assertEquals("100", TradePlaceholderExpansion.percent(250));
    }
}
