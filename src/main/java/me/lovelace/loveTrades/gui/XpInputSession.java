package me.lovelace.loveTrades.gui;

import me.lovelace.loveTrades.trade.TradeSession;

import java.util.UUID;

public class XpInputSession {

    private final UUID playerUuid;
    private final TradeSession tradeSession;

    public XpInputSession(UUID playerUuid, TradeSession tradeSession) {
        this.playerUuid = playerUuid;
        this.tradeSession = tradeSession;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public TradeSession getSession() { return tradeSession; }
}
