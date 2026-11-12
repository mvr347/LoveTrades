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

    private static volatile int xpMaxPerSide = 0;
    private static volatile int xpShiftStep = 5;

    private TradeInventory() {}

    /** Limits shown on the XP button; refreshed by the manager at the start of every trade. */
    public static void configureXp(int maxPerSide, int shiftStep) {
        xpMaxPerSide = maxPerSide;
        xpShiftStep = Math.max(1, shiftStep);
    }

    /** The first {@code count} slots of an item zone (zone order is row by row, so the top rows unlock first). */
    public static int[] unlocked(int[] zone, int count) {
        return Arrays.copyOf(zone, Math.max(0, Math.min(zone.length, count)));
    }

    /** Fills the item slots a side has not unlocked yet with a pane that says why. */
    public static void fillLockedSlots(Inventory inv, int[] zone, int unlockedCount) {
        ItemStack lock = makePane(Material.GRAY_STAINED_GLASS_PANE, "&8Слот закрыт");
        ItemMeta meta = lock.getItemMeta();
        meta.lore(List.of(
            legacy("&7Слоты открываются с ростом вежливости:"),
            legacy("&7сейчас доступно &f" + unlockedCount + " &7из &f" + zone.length)));
        lock.setItemMeta(meta);
        for (int i = unlockedCount; i < zone.length; i++) inv.setItem(zone[i], lock.clone());
    }

    public static Inventory createInventory(Player left, Player right) {
        Component title = Component.text(left.getName(), NamedTextColor.GOLD)
            .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
            .append(Component.text(right.getName(), NamedTextColor.YELLOW));
        return Bukkit.createInventory(null, 54, title);
    }

    /** Initialises all control elements. Call once after session creation. Item slots are left empty. */
    public static void initLayout(Inventory inv, TradeSession session,
                                  Player left, Player right, ModifierManager mods, double allyBonusPercent) {
        ItemStack sep = makePane(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int s : SEPARATOR_SLOTS) inv.setItem(s, sep);

        ItemStack filler = makePane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int s : FILLER_SLOTS) inv.setItem(s, filler);

        updateStatusSlot(inv, true,  false);
        updateStatusSlot(inv, false, false);
        updateXpSlot(inv, true,  0, left  != null ? left.getLevel()  : 0);
        updateXpSlot(inv, false, 0, right != null ? right.getLevel() : 0);
        refreshInfoSlots(inv, session, left, right, mods, allyBonusPercent);
    }

    /** Restores normal control layout after countdown cancellation. Does NOT touch item slots. */
    public static void restoreNormalLayout(Inventory inv, TradeSession session,
                                           Player left, Player right, ModifierManager mods, double allyBonusPercent) {
        ItemStack sep = makePane(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int s : SEPARATOR_SLOTS) inv.setItem(s, sep);

        updateStatusSlot(inv, true,  false);
        updateStatusSlot(inv, false, false);
        updateXpSlot(inv, true,  session.getXpLeft(),  left  != null ? left.getLevel()  : 0);
        updateXpSlot(inv, false, session.getXpRight(), right != null ? right.getLevel() : 0);
        refreshInfoSlots(inv, session, left, right, mods, allyBonusPercent);
    }

    /** Switches the separator and status buttons to countdown mode. */
    public static void applyCountdownUI(Inventory inv, int seconds) {
        ItemStack yellow = makePane(Material.YELLOW_STAINED_GLASS_PANE,
            legacy("&eПодтверждение..."));
        for (int s : SEPARATOR_SLOTS) {
            if (s == TIMER_DISPLAY_SLOT) continue;
            inv.setItem(s, yellow);
        }
        updateTimerDisplay(inv, seconds);

        ItemStack barrier = TradeHeads.head(TradeHeads.CANCEL, "&cОтменить сделку", List.of(
            "&8&m─────────────────────",
            "&cЛКМ &8— &7отменить сделку"));
        inv.setItem(LEFT_STATUS_SLOT,  barrier);
        inv.setItem(RIGHT_STATUS_SLOT, barrier.clone());
    }

    public static void updateTimerDisplay(Inventory inv, int seconds) {
        ItemStack timer = makePane(Material.YELLOW_STAINED_GLASS_PANE,
            legacy("&e" + seconds));
        inv.setItem(TIMER_DISPLAY_SLOT, timer);
    }

    public static void updateStatusSlot(Inventory inv, boolean isLeft, boolean ready) {
        int slot = isLeft ? LEFT_STATUS_SLOT : RIGHT_STATUS_SLOT;
        List<String> lore = List.of(
            "&8&m─────────────────────",
            ready ? "&aЛКМ &8— &7снять готовность" : "&aЛКМ &8— &7подтвердить готовность");
        inv.setItem(slot, ready
            ? TradeHeads.head(TradeHeads.READY, "&aГотов", lore)
            : TradeHeads.head(TradeHeads.NOT_READY, "&cНе готов", lore));
    }

    /**
     * The XP button: LMB takes a level off the offer, RMB adds one, Shift changes it by the configured step.
     * {@code available} is what the player owns right now; a click that would do nothing is shown as blocked with its reason.
     */
    public static void updateXpSlot(Inventory inv, boolean isLeft, int amount, int available) {
        int slot = isLeft ? LEFT_XP_SLOT : RIGHT_XP_SLOT;
        int cap = me.lovelace.loveTrades.manager.TradeRules.xpOfferCap(available, xpMaxPerSide);
        ItemStack xp = TradeHeads.head(TradeHeads.XP, "&6Опыт: &f" + amount + " ур.",
            xpLore(amount, available, cap, xpShiftStep));
        xp.setAmount(xpStackAmount(amount));
        inv.setItem(slot, xp);
    }

    /** Stack size of the XP button: one per offered level, so +1 level shows as +1 on the button (1..64). */
    public static int xpStackAmount(int levels) {
        return Math.max(1, Math.min(64, levels));
    }

    /** Lore of the XP button; separate and pure so the layout can be tested without a server. */
    public static List<String> xpLore(int amount, int available, int cap, int step) {
        List<String> lore = new ArrayList<>();
        lore.add("&7Доступно уровней: &f" + available);
        lore.add("&7Можно предложить: &f" + cap);
        lore.add("&8&m─────────────────────");
        boolean canLess = amount > 0;
        boolean canMore = amount < cap;
        if (!canLess && !canMore) {
            lore.add("&c✕ Действие недоступно");
            lore.add("&cПричина: &7" + (cap == 0 ? "нет уровней опыта" : "предложение нельзя изменить"));
        }
        lore.add(clickLine("&aЛКМ", "−1 ур.", canLess));
        lore.add(clickLine("&6ПКМ", "+1 ур.", canMore));
        lore.add(clickLine("&eShift+ЛКМ", "−" + step + " ур.", canLess));
        lore.add(clickLine("&eShift+ПКМ", "+" + step + " ур.", canMore));
        return lore;
    }

    private static String clickLine(String coloredKey, String action, boolean active) {
        if (active) return coloredKey + " &8— &7" + action;
        String plainKey = coloredKey.replaceAll("&[0-9a-fk-or]", "");
        return "&8" + plainKey + " — " + action + " (заблокировано)";
    }

    public static void refreshInfoSlots(Inventory inv, TradeSession session,
                                        Player left, Player right, ModifierManager mods, double allyBonusPercent) {
        // Ally modifier must mirror TradeManager#completeTrade exactly, otherwise the rate shown
        // here in the GUI diverges from the rate actually applied at payout.
        double allyMod  = session.isAllyBonus() ? allyBonusPercent : 0.0;
        double leftTax  = left  != null ? ModifierManager.clampRate(mods.getEffectiveTax(left,  allyMod)) : 0.0;
        double rightTax = right != null ? ModifierManager.clampRate(mods.getEffectiveTax(right, allyMod)) : 0.0;

        int leftCount  = countItems(inv, session.getSlotsOf(true));
        int rightCount = countItems(inv, session.getSlotsOf(false));

        List<String> leftLore = new ArrayList<>();
        leftLore.add("&7Предметов: &f" + leftCount + " ед.");
        leftLore.add("&7Опыт: &f" + session.getXpLeft() + " ур.");
        leftLore.add("&7Партнёр получит: &f" + ModifierManager.applyXpTax(session.getXpLeft(), rightTax) + " ур.");
        leftLore.add("&7Налог получателя: &c" + String.format("%.1f%%", rightTax));
        if (session.isAllyBonus()) leftLore.add(String.format("&6Союз: &a%.1f%% &6к налогу", allyBonusPercent));

        ItemStack leftInfo = makeInfoBook("&6Ваше предложение", leftLore);
        for (int s : LEFT_INFO_SLOTS) inv.setItem(s, leftInfo);

        List<String> rightLore = new ArrayList<>();
        rightLore.add("&7Предметов: &f" + rightCount + " ед.");
        rightLore.add("&7Опыт: &f" + session.getXpRight() + " ур.");
        rightLore.add("&7Вы получите: &f" + ModifierManager.applyXpTax(session.getXpRight(), leftTax) + " ур.");
        rightLore.add("&7Налог получателя: &c" + String.format("%.1f%%", leftTax));
        if (session.isAllyBonus()) rightLore.add(String.format("&6Союз: &a%.1f%% &6к налогу", allyBonusPercent));

        ItemStack rightInfo = makeInfoBook("&6Предложение партнёра", rightLore);
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

    private static ItemStack makeInfoBook(String title, List<String> lore) {
        return TradeHeads.head(TradeHeads.INFO, title, lore);
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
