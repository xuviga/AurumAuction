package com.xuvigan.aurumauction.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/** Tab completion for /ah. */
public final class AuctionTabCompleter implements TabCompleter {

    private static final List<String> COMMANDS = new ArrayList<String>();
    private static final List<String> ADMIN = new ArrayList<String>();

    static {
        COMMANDS.add("help");
        COMMANDS.add("sell");
        COMMANDS.add("sellmenu");
        COMMANDS.add("buy");
        COMMANDS.add("auction");
        COMMANDS.add("bid");
        COMMANDS.add("claims");
        COMMANDS.add("history");
        COMMANDS.add("top");
        COMMANDS.add("watch");
        COMMANDS.add("search");
        COMMANDS.add("my");
        COMMANDS.add("expired");
        COMMANDS.add("admin");
        COMMANDS.add("reload");

        ADMIN.add("info");
        ADMIN.add("remove");
        ADMIN.add("end");
        ADMIN.add("payouts");
        ADMIN.add("autobuy");
        ADMIN.add("reload");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0] == null ? "" : args[0].toLowerCase(java.util.Locale.ROOT);
            List<String> out = new ArrayList<String>();
            for (String value : COMMANDS) {
                if (!value.startsWith(prefix)) {
                    continue;
                }
                boolean adminOnly = "reload".equals(value) || "admin".equals(value);
                if (adminOnly && !sender.hasPermission("auction.admin")) {
                    continue;
                }
                out.add(value);
            }
            return out;
        }
        if (args.length == 2 && "admin".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("auction.admin")) {
                return Collections.emptyList();
            }
            String prefix = args[1] == null ? "" : args[1].toLowerCase(java.util.Locale.ROOT);
            List<String> out = new ArrayList<String>();
            for (String value : ADMIN) {
                if (value.startsWith(prefix)) {
                    out.add(value);
                }
            }
            return out;
        }
        if (args.length == 2 && "top".equalsIgnoreCase(args[0])) {
            String prefix = args[1] == null ? "" : args[1].toLowerCase(java.util.Locale.ROOT);
            List<String> out = new ArrayList<String>();
            if ("sellers".startsWith(prefix)) {
                out.add("sellers");
            }
            if ("buyers".startsWith(prefix)) {
                out.add("buyers");
            }
            return out;
        }
        if (args.length == 2 && "watch".equalsIgnoreCase(args[0])) {
            String prefix = args[1] == null ? "" : args[1].toLowerCase(java.util.Locale.ROOT);
            List<String> out = new ArrayList<String>();
            for (String value : new String[] {"list", "add", "remove", "clear"}) {
                if (value.startsWith(prefix)) {
                    out.add(value);
                }
            }
            return out;
        }
        return Collections.emptyList();
    }
}
