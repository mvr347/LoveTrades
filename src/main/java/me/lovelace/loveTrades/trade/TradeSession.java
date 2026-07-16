package me.lovelace.loveTrades.trade;

import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

public class TradeSession {

    private final UUID playerLeft;
    private final UUID playerRight;
    private final Inventory inventory;

    private int xpLeft = 0;
    private int xpRight = 0;
    private boolean readyLeft = false;
    private boolean readyRight = false;
    private TradeState state = TradeState.ACTIVE;
    private BukkitTask countdownTask;
    private int countdownRemaining;
    // Set to true when both players belong to allied clans at trade start
    private boolean allyBonus = false;
    // Timestamp (millis) of the last player activity (item change, ready toggle, xp input)
    private long lastActivity = System.currentTimeMillis();

    public TradeSession(UUID playerLeft, UUID playerRight, Inventory inventory) {
        this.playerLeft = playerLeft;
        this.playerRight = playerRight;
        this.inventory = inventory;
    }

    public boolean isLeftPlayer(UUID uuid) {
        return playerLeft.equals(uuid);
    }

    public boolean isParticipant(UUID uuid) {
        return playerLeft.equals(uuid) || playerRight.equals(uuid);
    }

    public UUID getOther(UUID uuid) {
        return isLeftPlayer(uuid) ? playerRight : playerLeft;
    }

    public boolean isReadyOf(boolean left) {
        return left ? readyLeft : readyRight;
    }

    public void setReady(boolean left, boolean ready) {
        if (left) readyLeft = ready;
        else readyRight = ready;
    }

    public boolean bothReady() {
        return readyLeft && readyRight;
    }

    public UUID getPlayerLeft() { return playerLeft; }
    public UUID getPlayerRight() { return playerRight; }
    public Inventory getInventory() { return inventory; }

    public int getXpLeft() { return xpLeft; }
    public void setXpLeft(int xp) { this.xpLeft = xp; }
    public int getXpRight() { return xpRight; }
    public void setXpRight(int xp) { this.xpRight = xp; }

    public TradeState getState() { return state; }
    public void setState(TradeState state) { this.state = state; }

    public BukkitTask getCountdownTask() { return countdownTask; }
    public void setCountdownTask(BukkitTask task) { this.countdownTask = task; }

    public int getCountdownRemaining() { return countdownRemaining; }
    public void setCountdownRemaining(int remaining) { this.countdownRemaining = remaining; }

    public boolean isAllyBonus() { return allyBonus; }
    public void setAllyBonus(boolean allyBonus) { this.allyBonus = allyBonus; }

    public long getLastActivity() { return lastActivity; }
    /** Marks the session as having had player activity just now (item change, ready toggle, xp input). */
    public void touch() { this.lastActivity = System.currentTimeMillis(); }
}
