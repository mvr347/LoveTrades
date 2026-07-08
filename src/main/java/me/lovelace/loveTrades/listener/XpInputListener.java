package me.lovelace.loveTrades.listener;

import me.lovelace.loveTrades.gui.XpInputSession;
import me.lovelace.loveTrades.manager.TradeManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;

@SuppressWarnings("deprecation")
public class XpInputListener implements Listener {

    private final TradeManager tradeManager;
    private final Plugin plugin;

    public XpInputListener(TradeManager tradeManager, Plugin plugin) {
        this.tradeManager = tradeManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerChat(AsyncPlayerChatEvent e) {
        Player player = e.getPlayer();
        XpInputSession xpSession = tradeManager.getXpInput(player.getUniqueId());
        if (xpSession == null) return;

        // Intercept and hide from public chat
        e.setCancelled(true);
        String message = e.getMessage();

        // Process on main thread since we touch inventories
        Bukkit.getScheduler().runTask(plugin, () ->
            tradeManager.processXpInput(player, message));
    }
}
