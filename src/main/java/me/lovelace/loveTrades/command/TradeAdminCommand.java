package me.lovelace.loveTrades.command;

import me.lovelace.loveTrades.manager.ConfigManager;
import me.lovelace.loveTrades.manager.ModifierManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class TradeAdminCommand implements CommandExecutor, TabCompleter {

    private final ModifierManager modifiers;
    private final ConfigManager config;

    public TradeAdminCommand(ModifierManager modifiers, ConfigManager config) {
        this.modifiers = modifiers;
        this.config = config;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("axtrades.admin")) {
            sender.sendMessage(legacy("&cНет прав."));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                config.reload();
                sender.sendMessage(legacy("&aКонфиг перезагружен."));
            }
            case "modifier" -> handleModifier(sender, args);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleModifier(CommandSender sender, String[] args) {
        // /tradeadmin modifier set <player> <value>
        // /tradeadmin modifier remove <player>
        // /tradeadmin modifier get <player>
        if (args.length < 3) { sendModifierHelp(sender); return; }

        String sub    = args[1].toLowerCase();
        String target = args[2];

        UUID targetUuid = resolveUuid(target);
        Player online = Bukkit.getPlayerExact(target);

        if (targetUuid == null && online == null) {
            sender.sendMessage(legacy("&cИгрок не найден: &e" + target));
            return;
        }
        if (targetUuid == null) targetUuid = online.getUniqueId();

        switch (sub) {
            case "get" -> {
                double playerTax = modifiers.getPlayerTax(targetUuid);
                double groupTax  = online != null ? modifiers.getEffectiveTax(online) - playerTax : 0.0;
                sender.sendMessage(legacy(String.format(
                    "&eИгрок &f%s &e— групповой: &f%.1f%% &e| индивидуальный: &f%.1f%% &e| итог: &f%.1f%%",
                    target, groupTax, playerTax, groupTax + playerTax)));
            }
            case "set" -> {
                if (args.length < 4) { sendModifierHelp(sender); return; }
                try {
                    double value = Double.parseDouble(args[3]);
                    modifiers.setPlayerTax(targetUuid, value);
                    sender.sendMessage(legacy(String.format(
                        "&aУстановлен индивидуальный налог &e%.1f%% &aдля &e%s&a.", value, target)));
                } catch (NumberFormatException ex) {
                    sender.sendMessage(legacy("&cНеверное число: " + args[3]));
                }
            }
            case "remove" -> {
                modifiers.removePlayerTax(targetUuid);
                sender.sendMessage(legacy("&aИндивидуальный налог для &e" + target + " &aудалён."));
            }
            default -> sendModifierHelp(sender);
        }
    }

    private UUID resolveUuid(String name) {
        // Try offline player lookup by name
        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException ignored) {}
        Player online = Bukkit.getPlayerExact(name);
        return online != null ? online.getUniqueId() : null;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(legacy("&6=== TradeAdmin ==="));
        sender.sendMessage(legacy("&e/tradeadmin reload &7— Перезагрузить конфиг"));
        sender.sendMessage(legacy("&e/tradeadmin modifier get <игрок> &7— Просмотр налогов"));
        sender.sendMessage(legacy("&e/tradeadmin modifier set <игрок> <значение> &7— Установить налог"));
        sender.sendMessage(legacy("&e/tradeadmin modifier remove <игрок> &7— Удалить налог"));
    }

    private void sendModifierHelp(CommandSender sender) {
        sender.sendMessage(legacy("&eИспользование: /tradeadmin modifier <get|set|remove> <игрок> [значение]"));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("axtrades.admin")) return List.of();
        return switch (args.length) {
            case 1 -> List.of("reload", "modifier").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
            case 2 -> args[0].equalsIgnoreCase("modifier")
                ? List.of("get", "set", "remove").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList())
                : List.of();
            case 3 -> args[0].equalsIgnoreCase("modifier")
                ? Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase())).collect(Collectors.toList())
                : List.of();
            default -> List.of();
        };
    }

    private Component legacy(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s);
    }
}
