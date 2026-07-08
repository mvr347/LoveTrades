package me.lovelace.loveTrades.api.events;

import me.lovelace.loveTrades.trade.TradeSession;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class TradeCountdownStartEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final TradeSession session;

    public TradeCountdownStartEvent(TradeSession session) {
        this.session = session;
    }

    public TradeSession getSession() { return session; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
