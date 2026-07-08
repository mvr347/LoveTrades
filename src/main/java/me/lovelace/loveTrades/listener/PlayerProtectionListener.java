package me.lovelace.loveTrades.listener;

import me.lovelace.loveTrades.manager.TradeManager;
import me.lovelace.loveTrades.trade.TradeSession;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerProtectionListener implements Listener {

    private final TradeManager tradeManager;

    public PlayerProtectionListener(TradeManager tradeManager) {
        this.tradeManager = tradeManager;
    }

    /** Block Q-drop while any GUI trade is open. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDrop(PlayerDropItemEvent e) {
        if (tradeManager.isInSession(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    /** Cancel trade if any participant takes damage. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;
        if (tradeManager.isInSession(player.getUniqueId())) {
            tradeManager.onPlayerDamage(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent e) {
        tradeManager.onPlayerQuit(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKick(PlayerKickEvent e) {
        tradeManager.onPlayerQuit(e.getPlayer());
    }
}
