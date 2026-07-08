package me.lovelace.loveTrades.api.events;

import me.lovelace.loveTrades.trade.TradeSession;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class TradeStartEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player playerLeft;
    private final Player playerRight;
    private final TradeSession session;

    public TradeStartEvent(Player playerLeft, Player playerRight, TradeSession session) {
        this.playerLeft = playerLeft;
        this.playerRight = playerRight;
        this.session = session;
    }

    public Player getPlayerLeft() { return playerLeft; }
    public Player getPlayerRight() { return playerRight; }
    public TradeSession getSession() { return session; }

    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
