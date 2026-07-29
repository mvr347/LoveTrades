package me.lovelace.loveTrades.listener;

import me.lovelace.loveTrades.api.events.TradeCountdownCancelEvent;
import me.lovelace.loveTrades.gui.TradeInventory;
import me.lovelace.loveTrades.manager.TradeManager;
import me.lovelace.loveTrades.trade.TradeSession;
import me.lovelace.loveTrades.trade.TradeState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Map;

public class TradeInventoryListener implements Listener {

    private final TradeManager tradeManager;
    private final Plugin plugin;

    public TradeInventoryListener(TradeManager tradeManager, Plugin plugin) {
        this.tradeManager = tradeManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        TradeSession session = tradeManager.getSession(player.getUniqueId());
        if (session == null) return;

        Inventory topInv = e.getView().getTopInventory();
        if (!topInv.equals(session.getInventory())) return;

        // Double-click collects items from all inventories — could grab opponent's items
        if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            e.setCancelled(true);
            return;
        }

        boolean isLeft = session.isLeftPlayer(player.getUniqueId());
        Inventory clickedInv = e.getClickedInventory();

        // ── Clicks in player's own (bottom) inventory ────────────────────────
        if (clickedInv != null && !clickedInv.equals(topInv)) {
            if (e.isShiftClick()) {
                // Shift-click would send items to first available slot in top inv
                // which may be the opponent's side — redirect manually or block
                e.setCancelled(true);
                redirectShiftClick(e, session, isLeft, player);
                return;
            }
            if (e.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY) {
                // Hotbar-swap targets a slot in the top inventory
                e.setCancelled(true);
                return;
            }
            return; // Allow regular clicks in player's own inventory
        }

        // ── Clicks in trade (top) inventory ──────────────────────────────────
        int rawSlot = e.getRawSlot();
        if (rawSlot < 0 || rawSlot >= 54) { e.setCancelled(true); return; }

        if (session.getState() == TradeState.COUNTDOWN) {
            int myStatus = isLeft ? TradeInventory.LEFT_STATUS_SLOT : TradeInventory.RIGHT_STATUS_SLOT;
            if (rawSlot == myStatus) {
                e.setCancelled(true);
                tradeManager.cancelCountdown(session, player, TradeCountdownCancelEvent.Reason.PLAYER_CLICKED);
            } else {
                e.setCancelled(true);
            }
            return;
        }

        // ACTIVE state
        boolean inMySlots = isLeft ? TradeInventory.isLeftSlot(rawSlot)
                                   : TradeInventory.isRightSlot(rawSlot);
        int myStatus = isLeft ? TradeInventory.LEFT_STATUS_SLOT : TradeInventory.RIGHT_STATUS_SLOT;
        int myXp     = isLeft ? TradeInventory.LEFT_XP_SLOT     : TradeInventory.RIGHT_XP_SLOT;

        if (inMySlots) {
            // Allow — but schedule a post-tick check to handle ready-reset and info refresh
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (session.getState() == TradeState.ACTIVE) {
                    tradeManager.handleItemChange(session, isLeft);
                }
            });
            return;
        }

        if (rawSlot == myStatus) {
            e.setCancelled(true);
            tradeManager.toggleReady(session, isLeft, player);
            return;
        }

        if (rawSlot == myXp) {
            e.setCancelled(true);
            tradeManager.beginXpInput(player, session);
            return;
        }

        // Separator, info, filler, or opponent's slots — deny
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        TradeSession session = tradeManager.getSession(player.getUniqueId());
        if (session == null) return;

        Inventory topInv = e.getView().getTopInventory();
        if (!topInv.equals(session.getInventory())) return;

        boolean isLeft = session.isLeftPlayer(player.getUniqueId());

        // If any dragged slot is in the top inv but not in the player's own zone — cancel
        for (int rawSlot : e.getRawSlots()) {
            if (rawSlot >= 54) continue; // player's own inventory row
            boolean ownSlot = isLeft ? TradeInventory.isLeftSlot(rawSlot)
                                     : TradeInventory.isRightSlot(rawSlot);
            if (!ownSlot) {
                e.setCancelled(true);
                return;
            }
        }

        if (session.getState() == TradeState.COUNTDOWN) {
            e.setCancelled(true);
            return;
        }

        // Valid drag in own slots — schedule item change check
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (session.getState() == TradeState.ACTIVE) {
                tradeManager.handleItemChange(session, isLeft);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player player)) return;

        // Ignore programmatic closes (trade completion / cancellation)
        if (tradeManager.isProgrammaticClose(player.getUniqueId())) return;

        TradeSession session = tradeManager.getSession(player.getUniqueId());
        if (session == null) return;

        if (!e.getInventory().equals(session.getInventory())) return;

        // Player manually closed the GUI — cancel trade and return cursor item
        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && !cursor.getType().isAir()) {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(cursor.clone());
            player.setItemOnCursor(null);
            for (ItemStack leftover : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }

        // Clear XP input if pending
        tradeManager.cancelXpInput(player.getUniqueId());

        tradeManager.cancelTrade(session,
            me.lovelace.loveTrades.api.events.TradeCancelEvent.Reason.PLAYER_CLOSED);
    }

    // ── Shift-click redirect from player inventory to own trade slots ─────────

    private void redirectShiftClick(InventoryClickEvent e, TradeSession session,
                                    boolean isLeft, Player player) {
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        if (session.getState() == TradeState.COUNTDOWN) return;

        // Лимит стаков уже выбран — докидывать нечего
        if (tradeManager.rejectShiftClickOverLimit(session, isLeft, player)) return;

        int[] ownSlots = isLeft ? TradeInventory.LEFT_ITEM_SLOTS : TradeInventory.RIGHT_ITEM_SLOTS;
        Inventory inv  = session.getInventory();

        // First pass: try to stack onto existing similar items
        for (int slot : ownSlots) {
            ItemStack existing = inv.getItem(slot);
            if (existing == null || existing.getType().isAir()) continue;
            if (!existing.isSimilar(clicked)) continue;
            int space = existing.getMaxStackSize() - existing.getAmount();
            if (space <= 0) continue;

            int toMove = Math.min(space, clicked.getAmount());
            existing.setAmount(existing.getAmount() + toMove);
            inv.setItem(slot, existing);
            clicked.setAmount(clicked.getAmount() - toMove);
            if (clicked.getAmount() <= 0) {
                e.setCurrentItem(null);
                scheduleItemChange(session, isLeft);
                return;
            }
        }

        if (clicked.getAmount() <= 0) {
            e.setCurrentItem(null);
            scheduleItemChange(session, isLeft);
            return;
        }

        // Second pass: place remainder in first empty slot
        for (int slot : ownSlots) {
            ItemStack existing = inv.getItem(slot);
            if (existing != null && !existing.getType().isAir()) continue;
            inv.setItem(slot, clicked.clone());
            e.setCurrentItem(null);
            scheduleItemChange(session, isLeft);
            return;
        }

        player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
            .legacyAmpersand().deserialize("&cВаши торговые слоты заполнены."));
    }

    private void scheduleItemChange(TradeSession session, boolean isLeft) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (session.getState() == TradeState.ACTIVE) {
                tradeManager.handleItemChange(session, isLeft);
            }
        });
    }
}
