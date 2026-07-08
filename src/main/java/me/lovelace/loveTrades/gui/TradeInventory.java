package me.lovelace.loveTrades.gui;

import me.lovelace.loveTrades.manager.ModifierManager;
import me.lovelace.loveTrades.trade.TradeSession;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TradeInventory {

    // Item zones
    public static final int[] LEFT_ITEM_SLOTS  = {0, 1, 2, 3, 9, 10, 11, 12, 18, 19, 20, 21, 27, 28, 29, 30};
    public static final int[] RIGHT_ITEM_SLOTS = {5, 6, 7, 8, 14, 15, 16, 17, 23, 24, 25, 26, 32, 33, 34, 35};

    // Controls
    public static final int[] SEPARATOR_SLOTS  = {4, 13, 22, 31, 40, 49};
    public static final int[] LEFT_INFO_SLOTS  = {36, 37, 38, 39};
    public static final int[] RIGHT_INFO_SLOTS = {41, 42, 43, 44};
    public static final int   LEFT_STATUS_SLOT  = 45;
    public static final int   LEFT_XP_SLOT      = 48;
    public static final int   RIGHT_XP_SLOT     = 50;
    public static final int   RIGHT_STATUS_SLOT = 53;
    public static final int[] FILLER_SLOTS      = {46, 47, 51, 52};

    // Countdown timer display: row 1, center separator slot
    public static final int TIMER_DISPLAY_SLOT = 13;

    private static final Set<Integer> LEFT_SET  = toSet(LEFT_ITEM_SLOTS);
    private static final Set<Integer> RIGHT_SET = toSet(RIGHT_ITEM_SLOTS);
    private static final Set<Integer> SEP_SET   = toSet(SEPARATOR_SLOTS);

    private TradeInventory() {}

    public static Inventory createInventory(Player left, Player right) {
        Component title = Component.text("⇆ ")
            .append(Component.text(left.getName(), NamedTextColor.AQUA))
            .append(Component.text(" ↔ ", NamedTextColor.GRAY))
            .append(Component.text(right.getName(), NamedTextColor.GREEN));
        return Bukkit.createInventory(null, 54, title);
    }

    /** Initialises all control elements. Call once after session creation. Item slots are left empty. */
    public static void initLayout(Inventory inv, TradeSession session,
                                  Player left, Player right, ModifierManager mods) {
        ItemStack sep = makePane(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int s : SEPARATOR_SLOTS) inv.setItem(s, sep);

        ItemStack filler = makePane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int s : FILLER_SLOTS) inv.setItem(s, filler);

        updateStatusSlot(inv, true,  false);
        updateStatusSlot(inv, false, false);
        updateXpSlot(inv, true,  0);
        updateXpSlot(inv, false, 0);
        refreshInfoSlots(inv, session, left, right, mods);
    }

    /** Restores normal control layout after countdown cancellation. Does NOT touch item slots. */
    public static void restoreNormalLayout(Inventory inv, TradeSession session,
                                           Player left, Player right, ModifierManager mods) {
        ItemStack sep = makePane(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int s : SEPARATOR_SLOTS) inv.setItem(s, sep);

        updateStatusSlot(inv, true,  false);
        updateStatusSlot(inv, false, false);
        updateXpSlot(inv, true,  session.getXpLeft());
        updateXpSlot(inv, false, session.getXpRight());
        refreshInfoSlots(inv, session, left, right, mods);
    }

    /** Switches the separator and status buttons to countdown mode. */
    public static void applyCountdownUI(Inventory inv, int seconds) {
        ItemStack yellow = makePane(Material.YELLOW_STAINED_GLASS_PANE,
            legacy("&e&lПодтверждение..."));
        for (int s : SEPARATOR_SLOTS) {
            if (s == TIMER_DISPLAY_SLOT) continue;
            inv.setItem(s, yellow);
        }
        updateTimerDisplay(inv, seconds);

        ItemStack barrier = new ItemStack(Material.BARRIER);
        ItemMeta meta = barrier.getItemMeta();
        meta.displayName(Component.text("✖ ОТМЕНИТЬ СДЕЛКУ", NamedTextColor.RED)
            .decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        barrier.setItemMeta(meta);
        inv.setItem(LEFT_STATUS_SLOT,  barrier);
        inv.setItem(RIGHT_STATUS_SLOT, barrier.clone());
    }

    public static void updateTimerDisplay(Inventory inv, int seconds) {
        ItemStack timer = makePane(Material.YELLOW_STAINED_GLASS_PANE,
            legacy("&e&l" + seconds));
        inv.setItem(TIMER_DISPLAY_SLOT, timer);
    }

    public static void updateStatusSlot(Inventory inv, boolean isLeft, boolean ready) {
        int slot = isLeft ? LEFT_STATUS_SLOT : RIGHT_STATUS_SLOT;
        Material mat  = ready ? Material.GREEN_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
        String  label = ready ? "&a&lГОТОВ" : "&c&lНЕ ГОТОВ";
        inv.setItem(slot, makePane(mat, legacy(label)));
    }

    public static void updateXpSlot(Inventory inv, boolean isLeft, int amount) {
        int slot = isLeft ? LEFT_XP_SLOT : RIGHT_XP_SLOT;
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.text("✦ Опыт: " + amount + " XP", NamedTextColor.YELLOW)
            .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Нажмите, чтобы изменить", NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }

    public static void refreshInfoSlots(Inventory inv, TradeSession session,
                                        Player left, Player right, ModifierManager mods) {
        double leftTax  = left  != null ? mods.getEffectiveTax(left)  : 0.0;
        double rightTax = right != null ? mods.getEffectiveTax(right) : 0.0;

        int leftCount  = countItems(inv, LEFT_ITEM_SLOTS);
        int rightCount = countItems(inv, RIGHT_ITEM_SLOTS);

        ItemStack leftInfo = makeInfoBook(
            legacy("&6&lВаше предложение"),
            List.of(
                legacy("&7Предметов: &f" + leftCount + " ед."),
                legacy("&7Опыт: &f" + session.getXpLeft() + " XP"),
                legacy("&7Налог получателя: &c" + String.format("%.1f%%", Math.max(0, rightTax)))
            ));
        for (int s : LEFT_INFO_SLOTS) inv.setItem(s, leftInfo);

        ItemStack rightInfo = makeInfoBook(
            legacy("&6&lПредложение партнёра"),
            List.of(
                legacy("&7Предметов: &f" + rightCount + " ед."),
                legacy("&7Опыт: &f" + session.getXpRight() + " XP"),
                legacy("&7Налог получателя: &c" + String.format("%.1f%%", Math.max(0, leftTax)))
            ));
        for (int s : RIGHT_INFO_SLOTS) inv.setItem(s, rightInfo);
    }

    public static boolean isLeftSlot(int rawSlot)  { return LEFT_SET.contains(rawSlot); }
    public static boolean isRightSlot(int rawSlot) { return RIGHT_SET.contains(rawSlot); }
    public static boolean isSeparatorSlot(int rawSlot) { return SEP_SET.contains(rawSlot); }

    private static int countItems(Inventory inv, int[] slots) {
        int total = 0;
        for (int s : slots) {
            ItemStack item = inv.getItem(s);
            if (item != null && item.getType() != Material.AIR) total += item.getAmount();
        }
        return total;
    }

    private static ItemStack makePane(Material mat, String legacyName) {
        return makePane(mat, legacy(legacyName));
    }

    private static ItemStack makePane(Material mat, Component name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack makeInfoBook(Component title, List<Component> lore) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(title.decoration(TextDecoration.ITALIC, false));
        List<Component> loreWithNoItalic = new ArrayList<>();
        for (Component line : lore) {
            loreWithNoItalic.add(line.decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(loreWithNoItalic);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private static Component legacy(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s);
    }

    private static Set<Integer> toSet(int[] arr) {
        Set<Integer> set = new HashSet<>();
        for (int v : arr) set.add(v);
        return set;
    }
}
