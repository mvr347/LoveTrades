package me.lovelace.loveTrades.manager;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TradeStatsTest {
    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final UUID CAROL = UUID.randomUUID();

    @Test
    void aFinishedTradeIsCountedForBothSides() {
        var stats = new TradeStats();
        // Alice gave 10 and got 8 of Bob's 10 (tax took 2); Bob gave 10 and got 9 of Alice's 10.
        stats.record(ALICE, BOB, 10, 10, 8);
        stats.record(BOB, ALICE, 10, 10, 9);
        assertEquals(new TradeStats.Snapshot(1, 10, 8, 2, 1), stats.get(ALICE));
        assertEquals(new TradeStats.Snapshot(1, 10, 9, 1, 1), stats.get(BOB));
    }

    @Test
    void partnersAreCountedOnceNoMatterHowManyTrades() {
        var stats = new TradeStats();
        stats.record(ALICE, BOB, 1, 1, 1);
        stats.record(ALICE, BOB, 1, 1, 1);
        stats.record(ALICE, CAROL, 1, 1, 1);
        assertEquals(3, stats.get(ALICE).trades());
        assertEquals(2, stats.get(ALICE).partners());
    }

    @Test
    void aTaxCannotPayThePlayer() {
        var stats = new TradeStats();
        stats.record(ALICE, BOB, 5, 4, 6);
        assertEquals(0, stats.get(ALICE).itemsLostToTax());
    }

    @Test
    void anUnknownPlayerIsAllZeros() {
        assertEquals(new TradeStats.Snapshot(0, 0, 0, 0, 0), new TradeStats().get(UUID.randomUUID()));
    }

    @Test
    void countersSurviveARestart(@TempDir Path dir) throws Exception {
        File file = dir.resolve("data/stats.yml").toFile();
        var first = new TradeStats();
        first.record(ALICE, BOB, 10, 10, 8);
        first.record(ALICE, CAROL, 3, 3, 3);
        first.saveIfDirty(file);

        var second = new TradeStats();
        second.load(file);
        assertEquals(first.get(ALICE), second.get(ALICE));
    }

    @Test
    void nothingIsWrittenWhenNothingChanged(@TempDir Path dir) throws Exception {
        File file = dir.resolve("stats.yml").toFile();
        new TradeStats().saveIfDirty(file);
        assertFalse(file.exists());
    }

    @Test
    void aDamagedEntryCostsOneEntryNotTheFile(@TempDir Path dir) throws Exception {
        File file = dir.resolve("stats.yml").toFile();
        java.nio.file.Files.writeString(file.toPath(),
                "players:\n  not-a-uuid:\n    trades: 5\n  " + ALICE + ":\n    trades: 2\n    partners:\n    - broken\n    - " + BOB + "\n");
        var stats = new TradeStats();
        stats.load(file);
        assertEquals(2, stats.get(ALICE).trades());
        assertEquals(1, stats.get(ALICE).partners());
    }
}
