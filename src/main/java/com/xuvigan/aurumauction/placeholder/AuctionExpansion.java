package com.xuvigan.aurumauction.placeholder;

import org.bukkit.OfflinePlayer;

import com.xuvigan.aurumauction.AurumAuction;
import com.xuvigan.aurumauction.model.LotQuery;
import com.xuvigan.aurumauction.model.LotStatus;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

/**
 * PlaceholderAPI expansion. Loaded reflectively, so PlaceholderAPI remains a
 * completely optional dependency.
 *
 * <p>%aurumauction_lots%, %aurumauction_mylots%, %aurumauction_expired%,
 * %aurumauction_sold%</p>
 */
public final class AuctionExpansion extends PlaceholderExpansion {

    private final AurumAuction plugin;

    public AuctionExpansion(AurumAuction plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "aurumauction";
    }

    @Override
    public String getAuthor() {
        return "XuViGaN";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null) {
            return null;
        }
        String key = params.toLowerCase(java.util.Locale.ROOT);
        if ("lots".equals(key)) {
            return String.valueOf(plugin.lots().count(LotQuery.active()));
        }
        if (player == null) {
            return key.startsWith("my") || "expired".equals(key) || "sold".equals(key) ? "0" : null;
        }
        if ("mylots".equals(key)) {
            return String.valueOf(plugin.lots().countBySeller(player.getUniqueId(), LotStatus.ACTIVE));
        }
        if ("expired".equals(key)) {
            return String.valueOf(plugin.lots().countBySeller(player.getUniqueId(), LotStatus.EXPIRED));
        }
        if ("sold".equals(key)) {
            return String.valueOf(plugin.lots().countBySeller(player.getUniqueId(), LotStatus.SOLD));
        }
        return null;
    }
}
