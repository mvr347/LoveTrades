package me.lovelace.loveTrades.manager;

import me.lovelace.loveTrades.LoveTrades;
import me.lovelace.loveTrades.api.events.*;
import me.lovelace.loveTrades.gui.TradeInventory;
import me.lovelace.loveTrades.gui.XpInputSession;
import me.lovelace.loveTrades.trade.TradeRequest;
import me.lovelace.loveTrades.trade.TradeSession;
import me.lovelace.loveTrades.trade.TradeState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class TradeManager {

    private final LoveTrades plugin;
    private final ConfigManager config;
    private final ModifierManager modifiers;

    private final Map<UUID, TradeSession>  activeSessions   = new HashMap<>();
    private final Map<UUID, TradeRequest>  pendingRequests  = new HashMap<>();
    private final Map<UUID, XpInputSession> xpInputSessions = new HashMap<>();

    // UUIDs whose next InventoryCloseEvent should be ignored (programmatic close)
    private final Set<UUID> programmaticCloseSet = new HashSet<>();

    public TradeManager(LoveTrades plugin, ConfigManager config, ModifierManager modifiers) {
        this.plugin    = plugin;
        this.config    = config;
        this.modifiers = modifiers;
    }

    // ── Queries ────────────────────────────────────────────────────────────────

    public boolean isInSession(UUID uuid)       { return activeSessions.containsKey(uuid); }
    public boolean hasPendingRequest(UUID uuid) { return pendingRequests.containsKey(uuid); }
    public TradeSession getSession(UUID uuid)   { return activeSessions.get(uuid); }
    public XpInputSession getXpInput(UUID uuid) { return xpInputSessions.get(uuid); }
    public boolean isProgrammaticClose(UUID uuid) { return programmaticCloseSet.remove(uuid); }

    // ── Request flow ───────────────────────────────────────────────────────────

    public void sendRequest(Player sender, Player target) {
        TradeRequestEvent event = new TradeRequestEvent(sender, target);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        TradeRequest request = new TradeRequest(sender.getUniqueId(), target.getUniqueId());
        int timeoutTicks = config.getRequestTimeout() * 20;

        BukkitTask expiry = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pendingRequests.remove(target.getUniqueId()) != null) {
                sender.sendMessage(msg("request-expired-sender", "{player}", target.getName()));
                target.sendMessage(msg("request-expired", "{player}", sender.getName()));
            }
        }, timeoutTicks);

        request.setExpiryTask(expiry);
        pendingRequests.put(target.getUniqueId(), request);

        sender.sendMessage(msg("request-sent", "{player}", target.getName()));

        Component clickable = Component.text(target.getName() + " хочет начать торговлю с вами. ", NamedTextColor.YELLOW)
            .append(Component.text("[Принять]", NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/trade accept " + sender.getName())))
            .append(Component.text(" "))
            .append(Component.text("[Отказаться]", NamedTextColor.RED).decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/trade deny " + sender.getName())));
        target.sendMessage(clickable);
    }

    public void acceptRequest(Player receiver, String senderName) {
        Player sender = Bukkit.getPlayerExact(senderName);
        if (sender == null) {
            receiver.sendMessage(msg("player-offline", "{player}", senderName));
            return;
        }
        TradeRequest request = pendingRequests.get(receiver.getUniqueId());
        if (request == null || !request.getSender().equals(sender.getUniqueId())) {
            receiver.sendMessage(msg("no-pending-request", "{player}", senderName));
            return;
        }
        pendingRequests.remove(receiver.getUniqueId());
        request.cancelExpiryTask();

        if (isInSession(sender.getUniqueId())) {
            receiver.sendMessage(msg("already-trading", "{player}", senderName));
            return;
        }
        if (isInSession(receiver.getUniqueId())) {
            receiver.sendMessage(msg("already-trading-self", "{player}", ""));
            return;
        }
        startTrade(sender, receiver);
    }

    public void denyRequest(Player receiver, String senderName) {
        Player sender = Bukkit.getPlayerExact(senderName);
        UUID senderUuid = sender != null ? sender.getUniqueId() : null;

        TradeRequest request = pendingRequests.get(receiver.getUniqueId());
        if (request == null || (senderUuid != null && !request.getSender().equals(senderUuid))) {
            receiver.sendMessage(msg("no-pending-request", "{player}", senderName));
            return;
        }
        pendingRequests.remove(receiver.getUniqueId());
        request.cancelExpiryTask();

        receiver.sendMessage(legacy("&cВы отклонили запрос на торговлю."));
        if (sender != null) {
            sender.sendMessage(legacy("&c" + receiver.getName() + " отклонил ваш запрос на торговлю."));
        }
    }

    // ── Session lifecycle ──────────────────────────────────────────────────────

    private void startTrade(Player left, Player right) {
        Inventory inv = TradeInventory.createInventory(left, right);
        TradeSession session = new TradeSession(left.getUniqueId(), right.getUniqueId(), inv);

        activeSessions.put(left.getUniqueId(),  session);
        activeSessions.put(right.getUniqueId(), session);

        TradeInventory.initLayout(inv, session, left, right, modifiers);

        Bukkit.getPluginManager().callEvent(new TradeStartEvent(left, right, session));

        left.openInventory(inv);
        right.openInventory(inv);
    }

    public void toggleReady(TradeSession session, boolean isLeft, Player player) {
        if (session.getState() != TradeState.ACTIVE) return;

        boolean nowReady = !session.isReadyOf(isLeft);
        session.setReady(isLeft, nowReady);
        TradeInventory.updateStatusSlot(session.getInventory(), isLeft, nowReady);

        if (session.bothReady()) {
            startCountdown(session);
        }
    }

    private void startCountdown(TradeSession session) {
        session.setState(TradeState.COUNTDOWN);
        int duration = config.getCountdownDuration();
        session.setCountdownRemaining(duration);

        TradeInventory.applyCountdownUI(session.getInventory(), duration);
        Bukkit.getPluginManager().callEvent(new TradeCountdownStartEvent(session));

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int remaining = session.getCountdownRemaining() - 1;
            session.setCountdownRemaining(remaining);

            if (remaining <= 0) {
                BukkitTask self = session.getCountdownTask();
                if (self != null) self.cancel();
                session.setCountdownTask(null);
                completeTrade(session);
            } else {
                TradeInventory.updateTimerDisplay(session.getInventory(), remaining);
            }
        }, 20L, 20L);

        session.setCountdownTask(task);
    }

    public void cancelCountdown(TradeSession session, Player canceller,
                                TradeCountdownCancelEvent.Reason reason) {
        if (session.getState() != TradeState.COUNTDOWN) return;

        BukkitTask task = session.getCountdownTask();
        if (task != null) { task.cancel(); session.setCountdownTask(null); }

        session.setState(TradeState.ACTIVE);
        session.setReady(true,  false);
        session.setReady(false, false);

        Player left  = Bukkit.getPlayer(session.getPlayerLeft());
        Player right = Bukkit.getPlayer(session.getPlayerRight());

        TradeInventory.restoreNormalLayout(session.getInventory(), session, left, right, modifiers);

        Bukkit.getPluginManager().callEvent(
            new TradeCountdownCancelEvent(session, canceller, reason));
    }

    private void completeTrade(TradeSession session) {
        session.setState(TradeState.COMPLETED);

        Player left  = Bukkit.getPlayer(session.getPlayerLeft());
        Player right = Bukkit.getPlayer(session.getPlayerRight());

        if (left == null || !left.isOnline() || right == null || !right.isOnline()) {
            cancelTrade(session, TradeCancelEvent.Reason.DISCONNECT);
            return;
        }

        Inventory inv = session.getInventory();

        List<ItemStack> leftOffer  = collectSlots(inv, TradeInventory.LEFT_ITEM_SLOTS);
        List<ItemStack> rightOffer = collectSlots(inv, TradeInventory.RIGHT_ITEM_SLOTS);

        double leftTax  = modifiers.getEffectiveTax(left);
        double rightTax = modifiers.getEffectiveTax(right);

        // leftOffer goes to right player (taxed by rightTax)
        // rightOffer goes to left player (taxed by leftTax)
        List<ItemStack> itemsForRight = applyItemTax(leftOffer,  rightTax);
        List<ItemStack> itemsForLeft  = applyItemTax(rightOffer, leftTax);

        int xpForRight = modifiers.applyXpTax(session.getXpLeft(),  rightTax);
        int xpForLeft  = modifiers.applyXpTax(session.getXpRight(), leftTax);

        TradePreCompleteEvent preEvent = new TradePreCompleteEvent(
            session, itemsForLeft, itemsForRight, xpForLeft, xpForRight);
        Bukkit.getPluginManager().callEvent(preEvent);

        if (preEvent.isCancelled()) {
            cancelTrade(session, TradeCancelEvent.Reason.PLUGIN_CANCELLED);
            return;
        }

        // Close both GUIs before transferring
        closeInventoriesProgrammatically(session, left, right);

        // Transfer items
        giveItems(left,  preEvent.getItemsForLeft());
        giveItems(right, preEvent.getItemsForRight());

        // Deduct XP from givers, give to receivers
        deductXp(left,  session.getXpLeft());
        deductXp(right, session.getXpRight());
        left.giveExp(preEvent.getXpForLeft());
        right.giveExp(preEvent.getXpForRight());

        removeSession(session);

        left.sendMessage(msg("trade-complete", "", ""));
        right.sendMessage(msg("trade-complete", "", ""));

        Bukkit.getPluginManager().callEvent(new TradeCompleteEvent(session));
    }

    public void cancelTrade(TradeSession session, TradeCancelEvent.Reason reason) {
        TradeState state = session.getState();
        if (state == TradeState.COMPLETED || state == TradeState.CANCELLED) return;

        session.setState(TradeState.CANCELLED);

        BukkitTask task = session.getCountdownTask();
        if (task != null) { task.cancel(); session.setCountdownTask(null); }

        Player left  = Bukkit.getPlayer(session.getPlayerLeft());
        Player right = Bukkit.getPlayer(session.getPlayerRight());

        closeInventoriesProgrammatically(session, left, right);

        // Return items to their owners on the main thread
        List<ItemStack> leftItems  = collectSlots(session.getInventory(), TradeInventory.LEFT_ITEM_SLOTS);
        List<ItemStack> rightItems = collectSlots(session.getInventory(), TradeInventory.RIGHT_ITEM_SLOTS);

        Bukkit.getScheduler().runTask(plugin, () -> {
            returnItems(session.getPlayerLeft(),  leftItems,  left);
            returnItems(session.getPlayerRight(), rightItems, right);
        });

        removeSession(session);
        sendCancelMessages(left, right, reason);
        Bukkit.getPluginManager().callEvent(new TradeCancelEvent(session, reason));
    }

    private void sendCancelMessages(Player left, Player right, TradeCancelEvent.Reason reason) {
        String key = switch (reason) {
            case DAMAGE     -> "trade-cancelled-damage";
            case DISCONNECT -> "trade-cancelled-disconnect";
            default         -> "trade-cancelled-player";
        };
        if (left  != null) left.sendMessage(msg(key, "", ""));
        if (right != null) right.sendMessage(msg(key, "", ""));
    }

    // ── XP input ──────────────────────────────────────────────────────────────

    public void beginXpInput(Player player, TradeSession session) {
        xpInputSessions.put(player.getUniqueId(), new XpInputSession(player.getUniqueId(), session));
        player.sendMessage(msg("xp-enter-amount", "", ""));
    }

    public void processXpInput(Player player, String input) {
        XpInputSession xpSession = xpInputSessions.get(player.getUniqueId());
        if (xpSession == null) return;

        TradeSession session = xpSession.getSession();
        if (session.getState() != TradeState.ACTIVE) {
            xpInputSessions.remove(player.getUniqueId());
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(input.trim());
            if (amount < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            player.sendMessage(msg("xp-invalid", "", ""));
            return; // keep session open for retry
        }

        int available = player.getTotalExperience();
        if (amount > available) {
            player.sendMessage(msg("xp-not-enough", "{available}", String.valueOf(available)));
            return;
        }

        xpInputSessions.remove(player.getUniqueId());

        boolean isLeft = session.isLeftPlayer(player.getUniqueId());
        if (isLeft) session.setXpLeft(amount);
        else        session.setXpRight(amount);

        // Reset other player's ready state
        boolean otherWasReady = session.isReadyOf(!isLeft);
        if (otherWasReady) {
            session.setReady(!isLeft, false);
            TradeInventory.updateStatusSlot(session.getInventory(), !isLeft, false);
        }

        TradeInventory.updateXpSlot(session.getInventory(), isLeft, amount);

        Player left  = Bukkit.getPlayer(session.getPlayerLeft());
        Player right = Bukkit.getPlayer(session.getPlayerRight());
        TradeInventory.refreshInfoSlots(session.getInventory(), session, left, right, modifiers);

        player.sendMessage(msg("xp-set", "{amount}", String.valueOf(amount)));
    }

    public void cancelXpInput(UUID uuid) {
        xpInputSessions.remove(uuid);
    }

    // ── Item change handler (called after uncancelled clicks in own slots) ────

    public void handleItemChange(TradeSession session, boolean changerIsLeft) {
        boolean otherReady = session.isReadyOf(!changerIsLeft);
        if (otherReady) {
            session.setReady(!changerIsLeft, false);
            TradeInventory.updateStatusSlot(session.getInventory(), !changerIsLeft, false);
        }
        Player left  = Bukkit.getPlayer(session.getPlayerLeft());
        Player right = Bukkit.getPlayer(session.getPlayerRight());
        TradeInventory.refreshInfoSlots(session.getInventory(), session, left, right, modifiers);
    }

    // ── Player lifecycle hooks ─────────────────────────────────────────────────

    public void onPlayerQuit(Player player) {
        xpInputSessions.remove(player.getUniqueId());
        pendingRequests.remove(player.getUniqueId());

        TradeSession session = activeSessions.get(player.getUniqueId());
        if (session != null) cancelTrade(session, TradeCancelEvent.Reason.DISCONNECT);
    }

    public void onPlayerDamage(Player player) {
        TradeSession session = activeSessions.get(player.getUniqueId());
        if (session != null) cancelTrade(session, TradeCancelEvent.Reason.DAMAGE);
    }

    public void onDisable() {
        // Snapshot to avoid ConcurrentModification
        for (TradeSession session : new HashSet<>(activeSessions.values())) {
            cancelTrade(session, TradeCancelEvent.Reason.PLUGIN_DISABLED);
        }
        activeSessions.clear();
        pendingRequests.clear();
        xpInputSessions.clear();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void closeInventoriesProgrammatically(TradeSession session, Player left, Player right) {
        if (left  != null) programmaticCloseSet.add(left.getUniqueId());
        if (right != null) programmaticCloseSet.add(right.getUniqueId());
        if (left  != null) left.closeInventory();
        if (right != null) right.closeInventory();
    }

    private void removeSession(TradeSession session) {
        activeSessions.remove(session.getPlayerLeft());
        activeSessions.remove(session.getPlayerRight());
    }

    private List<ItemStack> collectSlots(Inventory inv, int[] slots) {
        List<ItemStack> items = new ArrayList<>();
        for (int s : slots) {
            ItemStack item = inv.getItem(s);
            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                items.add(item.clone());
            }
        }
        return items;
    }

    private List<ItemStack> applyItemTax(List<ItemStack> items, double effectiveTax) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack item : items) {
            if (item.getMaxStackSize() == 1 || effectiveTax <= 0) {
                result.add(item.clone());
            } else {
                int newAmount = modifiers.applyItemTax(item.getAmount(), effectiveTax);
                if (newAmount > 0) {
                    ItemStack copy = item.clone();
                    copy.setAmount(newAmount);
                    result.add(copy);
                }
                // Destroyed items are simply not added — removed from economy
            }
        }
        return result;
    }

    private void giveItems(Player player, List<ItemStack> items) {
        if (items.isEmpty()) return;
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(items.toArray(new ItemStack[0]));
        if (!overflow.isEmpty()) {
            player.sendMessage(msg("inventory-full", "", ""));
            for (ItemStack leftover : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    private void returnItems(UUID ownerUuid, List<ItemStack> items, Player onlinePlayer) {
        if (items.isEmpty()) return;
        Player player = onlinePlayer != null && onlinePlayer.isOnline()
            ? onlinePlayer
            : Bukkit.getPlayer(ownerUuid);
        if (player != null && player.isOnline()) {
            giveItems(player, items);
        }
        // If offline: items are lost in the base implementation.
        // An offline-player storage extension could be added here.
    }

    private void deductXp(Player player, int amount) {
        if (amount <= 0) return;
        int newTotal = Math.max(0, player.getTotalExperience() - amount);
        player.setTotalExperience(newTotal);
    }

    private Component msg(String key, String placeholder, String value) {
        String raw = config.getMessage(key);
        if (!placeholder.isEmpty()) raw = raw.replace(placeholder, value);
        return legacy(raw);
    }

    private static Component legacy(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s);
    }
}
