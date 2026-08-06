package me.lovelace.loveTrades.command;

import me.lovelace.loveTrades.manager.ConfigManager;
import me.lovelace.loveTrades.manager.ModifierManager;
import me.lovelace.loveTrades.manager.TradeManager;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TradeCommand implements CommandExecutor, TabCompleter {

    private final TradeManager tradeManager;
    private final ConfigManager config;
    private final ModifierManager modifierManager;

    public TradeCommand(TradeManager tradeManager, ConfigManager config, ModifierManager modifierManager) {
        this.tradeManager = tradeManager;
        this.config = config;
        this.modifierManager = modifierManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Только игроки могут использовать эту команду.");
            return true;
        }

        if (!player.hasPermission("axtrades.use")) {
            player.sendMessage(legacy(config.getMessage("no-permission")));
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        // /trade accept <player>
        if (sub.equals("accept") && args.length >= 2) {
            tradeManager.acceptRequest(player, args[1]);
            return true;
        }

        // /trade deny <player>
        if (sub.equals("deny") && args.length >= 2) {
            tradeManager.denyRequest(player, args[1]);
            return true;
        }

        // /trade toggle - enable/disable receiving trade requests
        if (sub.equals("toggle")) {
            boolean nowEnabled = modifierManager.toggleRequestsEnabled(player.getUniqueId());
            player.sendMessage(legacy(config.getMessage(nowEnabled ? "requests-enabled" : "requests-disabled-self")));
            return true;
        }

        // /trade <targetPlayer>
        String targetName = args[0];
        Player target = Bukkit.getPlayerExact(targetName);

        if (target == null) {
            player.sendMessage(legacy(config.getMessage("player-offline")
                .replace("{player}", targetName)));
            return true;
        }

        if (target.equals(player)) {
            player.sendMessage(legacy(config.getMessage("cannot-self")));
            return true;
        }

        if (tradeManager.isInSession(player.getUniqueId())) {
            player.sendMessage(legacy(config.getMessage("already-trading-self")
                .replace("{player}", "")));
            return true;
        }

        if (tradeManager.isInSession(target.getUniqueId())) {
            player.sendMessage(legacy(config.getMessage("already-trading")
                .replace("{player}", target.getName())));
            return true;
        }

        if (tradeManager.hasPendingRequest(target.getUniqueId())) {
            player.sendMessage(legacy(config.getMessage("pending-request")));
            return true;
        }

        tradeManager.sendRequest(player, target);
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(legacy(config.getMessage("help-header", "&8========== &bLoveTrades Помощь &8==========")));
        player.sendMessage(legacy(config.getMessage("help-trade", "&b/trade <игрок> &7- Отправить запрос на торговлю")));
        player.sendMessage(legacy(config.getMessage("help-accept", "&b/trade accept <игрок> &7- Принять запрос на торговлю")));
        player.sendMessage(legacy(config.getMessage("help-deny", "&b/trade deny <игрок> &7- Отклонить запрос на торговлю")));
        player.sendMessage(legacy(config.getMessage("help-toggle", "&b/trade toggle &7- Вкл/выкл приём запросов на торговлю")));
        if (player.hasPermission("axtrades.admin")) {
            player.sendMessage(legacy(config.getMessage("help-lovetradesadmin", "&b/lovetradesadmin &7- Административные команды LoveTrades")));
        }
        player.sendMessage(legacy(config.getMessage("help-footer", "&8=========================================")));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player)) return List.of();
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> suggestions = new ArrayList<>(List.of("accept", "deny", "toggle"));
            Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.equals(sender))
                .map(Player::getName)
                .forEach(suggestions::add);
            return suggestions.stream()
                .filter(name -> name.toLowerCase().startsWith(partial))
                .collect(Collectors.toList());
        }
        return List.of();
    }

    private net.kyori.adventure.text.Component legacy(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s);
    }
}
