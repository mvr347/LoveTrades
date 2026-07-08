package me.lovelace.loveTrades.api.events;

import me.lovelace.loveTrades.trade.TradeSession;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class TradePreCompleteEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final TradeSession session;
    private List<ItemStack> itemsForLeft;
    private List<ItemStack> itemsForRight;
    private int xpForLeft;
    private int xpForRight;
    private boolean cancelled = false;

    public TradePreCompleteEvent(TradeSession session,
                                  List<ItemStack> itemsForLeft, List<ItemStack> itemsForRight,
                                  int xpForLeft, int xpForRight) {
        this.session = session;
        this.itemsForLeft = itemsForLeft;
        this.itemsForRight = itemsForRight;
        this.xpForLeft = xpForLeft;
        this.xpForRight = xpForRight;
    }

    public TradeSession getSession() { return session; }

    public List<ItemStack> getItemsForLeft() { return itemsForLeft; }
    public void setItemsForLeft(List<ItemStack> items) { this.itemsForLeft = items; }

    public List<ItemStack> getItemsForRight() { return itemsForRight; }
    public void setItemsForRight(List<ItemStack> items) { this.itemsForRight = items; }

    public int getXpForLeft() { return xpForLeft; }
    public void setXpForLeft(int xp) { this.xpForLeft = xp; }

    public int getXpForRight() { return xpForRight; }
    public void setXpForRight(int xp) { this.xpForRight = xp; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
