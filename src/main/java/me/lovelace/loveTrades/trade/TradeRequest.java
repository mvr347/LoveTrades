package me.lovelace.loveTrades.trade;

import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

public class TradeRequest {

    private final UUID sender;
    private final UUID receiver;
    private BukkitTask expiryTask;

    public TradeRequest(UUID sender, UUID receiver) {
        this.sender = sender;
        this.receiver = receiver;
    }

    public UUID getSender() { return sender; }
    public UUID getReceiver() { return receiver; }

    public void setExpiryTask(BukkitTask task) { this.expiryTask = task; }

    public void cancelExpiryTask() {
        if (expiryTask != null) {
            expiryTask.cancel();
            expiryTask = null;
        }
    }
}
