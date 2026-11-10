package me.lovelace.loveTrades.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class XpButtonTest {

    private static String joined(List<String> lore) {
        return String.join("\n", lore);
    }

    @Test
    void anActiveButtonShowsEveryClickWithTheStandardColours() {
        String lore = joined(TradeInventory.xpLore(3, 30, 30, 5));
        assertTrue(lore.contains("&aЛКМ &8— &7−1 ур."), lore);
        assertTrue(lore.contains("&6ПКМ &8— &7+1 ур."), lore);
        assertTrue(lore.contains("&eShift+ЛКМ &8— &7−5 ур."), lore);
        assertTrue(lore.contains("&eShift+ПКМ &8— &7+5 ур."), lore);
        assertFalse(lore.contains("заблокировано"), lore);
        assertTrue(lore.indexOf("&8&m─") < lore.indexOf("&aЛКМ"), "the separator comes before the clicks");
    }

    @Test
    void atZeroTheDecreaseIsBlocked() {
        String lore = joined(TradeInventory.xpLore(0, 30, 30, 5));
        assertTrue(lore.contains("&8ЛКМ — −1 ур. (заблокировано)"), lore);
        assertTrue(lore.contains("&6ПКМ &8— &7+1 ур."), lore);
        assertFalse(lore.contains("Причина"), "only one direction is blocked, so no blanket reason");
    }

    @Test
    void atTheCapTheIncreaseIsBlocked() {
        String lore = joined(TradeInventory.xpLore(30, 30, 30, 5));
        assertTrue(lore.contains("&8ПКМ — +1 ур. (заблокировано)"), lore);
        assertTrue(lore.contains("&8Shift+ПКМ — +5 ур. (заблокировано)"), lore);
    }

    @Test
    void aPlayerWithoutLevelsGetsAReason() {
        String lore = joined(TradeInventory.xpLore(0, 0, 0, 5));
        assertTrue(lore.contains("&c✕ Действие недоступно"), lore);
        assertTrue(lore.contains("&cПричина: &7нет уровней опыта"), lore);
        assertEquals(4, lore.split("заблокировано", -1).length - 1, "all four clicks are blocked");
    }

    @Test
    void noForbiddenPhrasesInTheButton() {
        String lore = joined(TradeInventory.xpLore(2, 9, 9, 5)).toLowerCase();
        for (String bad : List.of("нажмите", "кликните", "›", "▸", "•")) {
            assertFalse(lore.contains(bad), bad);
        }
    }

    @Test
    void everyBuiltInHeadTextureIsAValidMojangUrl() {
        Pattern hash = Pattern.compile("^[0-9a-f]{50,64}$");
        for (var entry : TradeHeads.DEFAULT_HASHES.entrySet()) {
            assertTrue(hash.matcher(entry.getValue()).matches(), entry.getKey() + " has a bad hash");
            String json = new String(Base64.getDecoder().decode(TradeHeads.urlToBase64(entry.getValue())));
            Matcher m = Pattern.compile("\"url\":\"http://textures\\.minecraft\\.net/texture/([0-9a-f]+)\"").matcher(json);
            assertTrue(m.find(), json);
            assertEquals(entry.getValue(), m.group(1));
        }
        assertEquals(5, TradeHeads.DEFAULT_HASHES.size());
    }
}
