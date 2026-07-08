package me.lovelace.loveTrades.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.lovelace.loveTrades.gui.XpInputSession;
import me.lovelace.loveTrades.manager.TradeManager;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

public class XpInputListener implements Listener {

    private final TradeManager tradeManager;
    private final Plugin plugin;

    public XpInputListener(TradeManager tradeManager, Plugin plugin) {
        this.tradeManager = tradeManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerChat(AsyncChatEvent e) {
        Player player = e.getPlayer();
        XpInputSession xpSession = tradeManager.getXpInput(player.getUniqueId());
        if (xpSession == null) return;

        e.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(e.message());

        // Process on main thread — we touch inventories and player state
        Bukkit.getScheduler().runTask(plugin, () ->
            tradeManager.processXpInput(player, message));
    }
}
