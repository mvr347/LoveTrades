package me.lovelace.loveTrades.api;

import org.bukkit.entity.Player;

/**
 * Bridge between LoveTrades and any clan plugin.
 *
 * Register your implementation once (e.g. in your clan plugin's onEnable):
 *
 *   Plugin lt = Bukkit.getPluginManager().getPlugin("LoveTrades");
 *   if (lt instanceof LoveTrades love) {
 *       love.setClanIntegration(new YourClanIntegration());
 *   }
 *
 * Both methods are called on the main thread.
 */
public abstract class ClanIntegration {

    /**
     * Returns true when the two players belong to clans that are currently
     * in an allied (friendly) relationship. LoveTrades will apply the
     * configured ally bonus to both players' received resources.
     */
    public abstract boolean isAlly(Player a, Player b);

    /**
     * Returns true when the two players belong to clans that are currently
     * in a hostile (war / aggressive) relationship. LoveTrades will refuse
     * the trade request if this returns true.
     */
    public abstract boolean isEnemy(Player a, Player b);
}
