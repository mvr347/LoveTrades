package me.lovelace.loveTrades.api.events;

import me.lovelace.loveTrades.trade.TradeSession;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class TradeCancelEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    public enum Reason {
        PLAYER_CLOSED,
        DAMAGE,
        DISCONNECT,
        PLUGIN_CANCELLED,
        PLUGIN_DISABLED,
        INSUFFICIENT_XP,
        INACTIVITY,
        STACK_LIMIT,
        CLAN_ENEMY
    }

    private final TradeSession session;
    private final Reason reason;

    public TradeCancelEvent(TradeSession session, Reason reason) {
        this.session = session;
        this.reason = reason;
    }

    public TradeSession getSession() { return session; }
    public Reason getReason() { return reason; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
