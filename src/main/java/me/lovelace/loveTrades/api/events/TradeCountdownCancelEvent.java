package me.lovelace.loveTrades.api.events;

import me.lovelace.loveTrades.trade.TradeSession;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class TradeCountdownCancelEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    public enum Reason { PLAYER_CLICKED, DAMAGE, DISCONNECT }

    private final TradeSession session;
    private final Player canceller;
    private final Reason reason;

    public TradeCountdownCancelEvent(TradeSession session, Player canceller, Reason reason) {
        this.session = session;
        this.canceller = canceller;
        this.reason = reason;
    }

    public TradeSession getSession() { return session; }
    /** May be null if cancelled by damage or disconnect. */
    public Player getCanceller() { return canceller; }
    public Reason getReason() { return reason; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
