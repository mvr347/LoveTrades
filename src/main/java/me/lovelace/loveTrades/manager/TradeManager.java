package me.lovelace.loveTrades.manager;

import me.lovelace.loveTrades.LoveTrades;
import me.lovelace.loveTrades.api.ClanIntegration;
import me.lovelace.loveTrades.api.events.*;
import me.lovelace.loveTrades.gui.TradeInventory;
import me.lovelace.loveTrades.gui.XpInputSession;
import me.lovelace.loveTrades.trade.TradeRequest;
import me.lovelace.loveTrades.trade.TradeSession;
import me.lovelace.loveTrades.trade.TradeState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TradeManager {

    private final LoveTrades plugin;
    private final ConfigManager config;
    private final ModifierManager modifiers;
    private final TradeRestrictions restrictions;

    private final Map<UUID, TradeSession>   activeSessions    = new ConcurrentHashMap<>();
    private final Map<UUID, TradeRequest>   pendingRequests   = new ConcurrentHashMap<>();
    private final Map<UUID, XpInputSession> xpInputSessions   = new ConcurrentHashMap<>();

    // UUIDs whose next InventoryCloseEvent should be ignored (programmatic close)
    private final Set<UUID> programmaticCloseSet = new HashSet<>();

    // UUIDs already warned about the stack limit in the current session
    private final Map<UUID, Boolean> stackLimitWarned = new HashMap<>();

    // Set while the plugin is being disabled so item returns happen synchronously
    private boolean disabling = false;

    private BukkitTask inactivityCheckTask;

    public TradeManager(LoveTrades plugin, ConfigManager config, ModifierManager modifiers) {
        this.plugin       = plugin;
        this.config       = config;
        this.modifiers    = modifiers;
        this.restrictions = new TradeRestrictions(config);

        this.inactivityCheckTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkInactiveSessions, 20L, 20L);
    }

    public TradeRestrictions getRestrictions() { return restrictions; }

    // ── Queries ────────────────────────────────────────────────────────────────

    public boolean isInSession(UUID uuid)         { return activeSessions.containsKey(uuid); }
    public boolean hasPendingRequest(UUID uuid)   { return pendingRequests.containsKey(uuid); }
    public TradeSession getSession(UUID uuid)     { return activeSessions.get(uuid); }
    public XpInputSession getXpInput(UUID uuid)   { return xpInputSessions.get(uuid); }
    public boolean isProgrammaticClose(UUID uuid) { return programmaticCloseSet.remove(uuid); }

    // ── Request flow ───────────────────────────────────────────────────────────

    public void sendRequest(Player sender, Player target) {
        // Target has disabled incoming trade requests
        if (!modifiers.isRequestsEnabled(target.getUniqueId())) {
            sender.sendMessage(msg("requests-disabled", "{player}", target.getName()));
            return;
        }

        // Clan enemy check
        if (config.isClanEnabled() && config.isClanEnemyBlock()) {
            ClanIntegration clan = plugin.getClanIntegration();
            if (clan != null && clan.isEnemy(sender, target)) {
                sender.sendMessage(msg("clan-enemy-blocked", "", ""));
                return;
            }
        }

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

        // Текст уведомления и подписи кнопок берутся из config.yml: раньше они были захардкожены
        // здесь, из-за чего ключ messages.request-received существовал, но не использовался, и
        // перевести/изменить это сообщение было нельзя.
        Component clickable = msg("request-received", "{player}", sender.getName())
            .append(Component.text(" "))
            .append(legacy(config.getMessage("request-accept-button", "&a&l[Принять]"))
                .clickEvent(ClickEvent.runCommand("/trade accept " + sender.getName())))
            .append(Component.text(" "))
            .append(legacy(config.getMessage("request-deny-button", "&c&l[Отказаться]"))
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

        // Перепроверяем бой и кулдаун: с момента отправки запроса игроки могли
        // вступить в схватку, а кулдаун — истечь или, наоборот, начаться.
        if (!passesPreTradeChecks(receiver, sender)) return;

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

        receiver.sendMessage(legacy(config.getMessage("request-denied-self", "&cВы отклонили запрос на торговлю.")));
        if (sender != null) {
            sender.sendMessage(legacy(config.getMessage("request-denied-sender", "&c{player} отклонил ваш запрос на торговлю.")
                    .replace("{player}", receiver.getName())));
        }
    }

    // ── Session lifecycle ──────────────────────────────────────────────────────

    private void startTrade(Player left, Player right) {
        Inventory inv = TradeInventory.createInventory(left, right);
        TradeSession session = new TradeSession(left.getUniqueId(), right.getUniqueId(), inv);

        // Determine clan ally bonus at session start
        if (config.isClanEnabled()) {
            ClanIntegration clan = plugin.getClanIntegration();
            if (clan != null && clan.isAlly(left, right)) {
                session.setAllyBonus(true);
                left.sendMessage(msg("clan-ally-bonus", "", ""));
                right.sendMessage(msg("clan-ally-bonus", "", ""));
            }
        }

        activeSessions.put(left.getUniqueId(),  session);
        activeSessions.put(right.getUniqueId(), session);

        TradeInventory.initLayout(inv, session, left, right, modifiers);

        Bukkit.getPluginManager().callEvent(new TradeStartEvent(left, right, session));

        left.openInventory(inv);
        right.openInventory(inv);
    }

    public void toggleReady(TradeSession session, boolean isLeft, Player player) {
        if (session.getState() != TradeState.ACTIVE) return;

        session.touch();
        boolean nowReady = !session.isReadyOf(isLeft);

        // Подтвердить готовность можно только уложившись в лимит стаков
        if (nowReady && restrictions.exceedsStackLimit(player, session.getInventory(), itemSlotsOf(isLeft))) {
            sendStackLimitMessage(player, session, isLeft);
            return;
        }
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
        session.touch();

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

        // Re-validate committed XP right before transferring it: a player may have spent
        // levels (enchanting, anvil, etc.) after committing them to the trade but before
        // the countdown finished. Transferring the originally-committed amount in that
        // case would create XP out of nothing, so bail out instead.
        if (left.getLevel() < session.getXpLeft() || right.getLevel() < session.getXpRight()) {
            cancelTrade(session, TradeCancelEvent.Reason.INSUFFICIENT_XP);
            return;
        }

        Inventory inv = session.getInventory();

        // Финальная проверка лимита: предложение могло измениться после подтверждения
        // готовности (например, через drag, не прошедший проверку при клике).
        if (restrictions.exceedsStackLimit(left,  inv, TradeInventory.LEFT_ITEM_SLOTS)
            || restrictions.exceedsStackLimit(right, inv, TradeInventory.RIGHT_ITEM_SLOTS)) {
            cancelTrade(session, TradeCancelEvent.Reason.STACK_LIMIT);
            return;
        }

        List<ItemStack> leftOffer  = collectSlots(inv, TradeInventory.LEFT_ITEM_SLOTS);
        List<ItemStack> rightOffer = collectSlots(inv, TradeInventory.RIGHT_ITEM_SLOTS);

        // Clan ally bonus modifier (negative = discount for both receivers)
        double allyMod = session.isAllyBonus() ? config.getClanAllyBonus() : 0.0;

        // leftOffer goes to right (taxed by right's effective tax + ally mod)
        // rightOffer goes to left (taxed by left's effective tax + ally mod)
        double leftTax  = modifiers.getEffectiveTax(left,  allyMod);
        double rightTax = modifiers.getEffectiveTax(right, allyMod);

        List<ItemStack> itemsForRight = applyItemTax(leftOffer,  rightTax);
        List<ItemStack> itemsForLeft  = applyItemTax(rightOffer, leftTax);

        // XP levels: left offers xpLeft to right, right offers xpRight to left
        int xpForRight = modifiers.applyXpTax(session.getXpLeft(),  rightTax);
        int xpForLeft  = modifiers.applyXpTax(session.getXpRight(), leftTax);

        TradePreCompleteEvent preEvent = new TradePreCompleteEvent(
            session, itemsForLeft, itemsForRight, xpForLeft, xpForRight);
        Bukkit.getPluginManager().callEvent(preEvent);

        if (preEvent.isCancelled()) {
            cancelTrade(session, TradeCancelEvent.Reason.PLUGIN_CANCELLED);
            return;
        }

        closeInventoriesProgrammatically(session, left, right);

        giveItems(left,  preEvent.getItemsForLeft());
        giveItems(right, preEvent.getItemsForRight());

        // XP is stored and transferred in levels
        deductXpLevels(left,  session.getXpLeft());
        deductXpLevels(right, session.getXpRight());
        left.giveExpLevels(preEvent.getXpForLeft());
        right.giveExpLevels(preEvent.getXpForRight());

        restrictions.recordCompletedTrade(session.getPlayerLeft(), session.getPlayerRight());
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

        List<ItemStack> leftItems  = collectSlots(session.getInventory(), TradeInventory.LEFT_ITEM_SLOTS);
        List<ItemStack> rightItems = collectSlots(session.getInventory(), TradeInventory.RIGHT_ITEM_SLOTS);

        // While the plugin is disabling, a scheduled task is not guaranteed to run at all,
        // which would silently destroy the items sitting in the trade. Return them directly
        // in that case instead of going through the scheduler.
        if (disabling || !plugin.isEnabled()) {
            returnItems(session.getPlayerLeft(),  leftItems,  left);
            returnItems(session.getPlayerRight(), rightItems, right);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> {
                returnItems(session.getPlayerLeft(),  leftItems,  left);
                returnItems(session.getPlayerRight(), rightItems, right);
            });
        }

        removeSession(session);
        sendCancelMessages(left, right, reason);
        Bukkit.getPluginManager().callEvent(new TradeCancelEvent(session, reason));
    }

    private void sendCancelMessages(Player left, Player right, TradeCancelEvent.Reason reason) {
        String key = switch (reason) {
            case DAMAGE             -> "trade-cancelled-damage";
            case DISCONNECT         -> "trade-cancelled-disconnect";
            case INSUFFICIENT_XP    -> "trade-cancelled-insufficient-xp";
            case INACTIVITY         -> "trade-cancelled-inactivity";
            case STACK_LIMIT        -> "trade-cancelled-stack-limit";
            default                 -> "trade-cancelled-player";
        };
        if (left  != null) left.sendMessage(msg(key, "", ""));
        if (right != null) right.sendMessage(msg(key, "", ""));
    }

    /** Periodic check that cancels ACTIVE trades that have had no activity within the configured timeout. */
    private void checkInactiveSessions() {
        int timeoutSeconds = config.getInactivityTimeoutSeconds();
        if (timeoutSeconds <= 0) return;

        long timeoutMillis = timeoutSeconds * 1000L;
        long now = System.currentTimeMillis();

        for (TradeSession session : new HashSet<>(activeSessions.values())) {
            if (session.getState() != TradeState.ACTIVE) continue;
            if (now - session.getLastActivity() >= timeoutMillis) {
                cancelTrade(session, TradeCancelEvent.Reason.INACTIVITY);
            }
        }
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
            return;
        }

        // XP amounts are in levels
        int available = player.getLevel();
        if (amount > available) {
            player.sendMessage(msg("xp-not-enough", "{available}", String.valueOf(available)));
            return;
        }

        xpInputSessions.remove(player.getUniqueId());
        session.touch();

        boolean isLeft = session.isLeftPlayer(player.getUniqueId());
        if (isLeft) session.setXpLeft(amount);
        else        session.setXpRight(amount);

        // Reset other player's ready state since the offer changed
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

    // ── Item change handler ────────────────────────────────────────────────────

    public void handleItemChange(TradeSession session, boolean changerIsLeft) {
        session.touch();
        boolean otherReady = session.isReadyOf(!changerIsLeft);
        if (otherReady) {
            session.setReady(!changerIsLeft, false);
            TradeInventory.updateStatusSlot(session.getInventory(), !changerIsLeft, false);
        }
        Player left  = Bukkit.getPlayer(session.getPlayerLeft());
        Player right = Bukkit.getPlayer(session.getPlayerRight());
        TradeInventory.refreshInfoSlots(session.getInventory(), session, left, right, modifiers);

        notifyStackLimitState(session, changerIsLeft, changerIsLeft ? left : right);
    }

    /**
     * Предупреждает игрока в момент превышения лимита стаков — не на каждый клик,
     * а только при переходе «в пределах лимита → сверх лимита».
     */
    private void notifyStackLimitState(TradeSession session, boolean isLeft, Player player) {
        if (player == null) return;

        UUID uuid = player.getUniqueId();
        boolean over = restrictions.exceedsStackLimit(player, session.getInventory(), itemSlotsOf(isLeft));
        boolean wasOver = Boolean.TRUE.equals(stackLimitWarned.get(uuid));

        if (over && !wasOver) {
            sendStackLimitMessage(player, session, isLeft);
            stackLimitWarned.put(uuid, true);
        } else if (!over && wasOver) {
            stackLimitWarned.remove(uuid);
        }
    }

    private void sendStackLimitMessage(Player player, TradeSession session, boolean isLeft) {
        int limit = restrictions.effectiveStackLimit(player);
        if (limit <= 0) return;
        int current = restrictions.countStacks(session.getInventory(), itemSlotsOf(isLeft));
        player.sendMessage(msg("stack-limit-exceeded", Map.of(
            "{max}",     String.valueOf(limit),
            "{current}", String.valueOf(current))));
    }

    /**
     * Не даёт shift-кликом докинуть предметы, когда лимит стаков уже выбран.
     * Возвращает true, если перенос нужно отменить.
     */
    public boolean rejectShiftClickOverLimit(TradeSession session, boolean isLeft, Player player) {
        int limit = restrictions.effectiveStackLimit(player);
        if (limit <= 0) return false;
        if (restrictions.countStacks(session.getInventory(), itemSlotsOf(isLeft)) < limit) return false;

        sendStackLimitMessage(player, session, isLeft);
        return true;
    }

    private static int[] itemSlotsOf(boolean isLeft) {
        return isLeft ? TradeInventory.LEFT_ITEM_SLOTS : TradeInventory.RIGHT_ITEM_SLOTS;
    }

    /**
     * Общие проверки перед стартом торговли: PvP и кулдаун пары.
     * Сообщение получает инициатор действия — отправитель запроса или принимающий.
     */
    private boolean passesPreTradeChecks(Player actor, Player other) {
        if (restrictions.isInCombat(actor)) {
            actor.sendMessage(msg("trade-blocked-combat-self", "", ""));
            return false;
        }
        if (restrictions.isInCombat(other)) {
            actor.sendMessage(msg("trade-blocked-combat-other", "{player}", other.getName()));
            return false;
        }

        long remaining = restrictions.remainingCooldownSeconds(actor, other);
        if (remaining > 0) {
            actor.sendMessage(msg("trade-cooldown", Map.of(
                "{player}", other.getName(),
                "{time}",   formatDuration(remaining))));
            return false;
        }
        return true;
    }

    private static String formatDuration(long seconds) {
        if (seconds < 60) return seconds + " сек";
        long minutes = seconds / 60;
        long rest    = seconds % 60;
        return rest == 0 ? minutes + " мин" : minutes + " мин " + rest + " сек";
    }

    // ── Player lifecycle hooks ─────────────────────────────────────────────────

    public void onPlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();
        xpInputSessions.remove(uuid);

        // Clean up a request where the quitting player is the receiver
        TradeRequest asReceiver = pendingRequests.remove(uuid);
        if (asReceiver != null) asReceiver.cancelExpiryTask();

        // Also clean up any pending requests where the quitting player was the sender,
        // otherwise the receiver's request would linger until it naturally times out.
        Iterator<Map.Entry<UUID, TradeRequest>> it = pendingRequests.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, TradeRequest> entry = it.next();
            TradeRequest request = entry.getValue();
            if (!request.getSender().equals(uuid)) continue;

            request.cancelExpiryTask();
            it.remove();

            Player receiver = Bukkit.getPlayer(entry.getKey());
            if (receiver != null && receiver.isOnline()) {
                receiver.sendMessage(msg("request-cancelled-sender-quit", "{player}", player.getName()));
            }
        }

        TradeSession session = activeSessions.get(uuid);
        if (session != null) cancelTrade(session, TradeCancelEvent.Reason.DISCONNECT);
    }

    public void onPlayerDamage(Player player) {
        TradeSession session = activeSessions.get(player.getUniqueId());
        if (session != null) cancelTrade(session, TradeCancelEvent.Reason.DAMAGE);
    }

    public void onDisable() {
        disabling = true;

        if (inactivityCheckTask != null) {
            inactivityCheckTask.cancel();
            inactivityCheckTask = null;
        }

        for (TradeSession session : new HashSet<>(activeSessions.values())) {
            cancelTrade(session, TradeCancelEvent.Reason.PLUGIN_DISABLED);
        }
        activeSessions.clear();
        pendingRequests.clear();
        xpInputSessions.clear();
        stackLimitWarned.clear();
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
        stackLimitWarned.remove(session.getPlayerLeft());
        stackLimitWarned.remove(session.getPlayerRight());
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
            if (item.getMaxStackSize() == 1) {
                // Unstackable items (weapons, armour, tools, etc.) always transfer 1:1
                result.add(item.clone());
            } else {
                int newAmount = modifiers.applyItemTax(item.getAmount(), effectiveTax);
                if (newAmount > 0) {
                    ItemStack copy = item.clone();
                    copy.setAmount(newAmount);
                    result.add(copy);
                }
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
    }

    /** Deduct XP levels from player, clamped at 0. */
    private void deductXpLevels(Player player, int levels) {
        if (levels <= 0) return;
        player.setLevel(Math.max(0, player.getLevel() - levels));
    }

    private Component msg(String key, String placeholder, String value) {
        String raw = config.getMessage(key);
        if (!placeholder.isEmpty()) raw = raw.replace(placeholder, value);
        return legacy(raw);
    }

    private Component msg(String key, Map<String, String> placeholders) {
        String raw = config.getMessage(key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            raw = raw.replace(entry.getKey(), entry.getValue());
        }
        return legacy(raw);
    }

    private static Component legacy(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s);
    }
}
