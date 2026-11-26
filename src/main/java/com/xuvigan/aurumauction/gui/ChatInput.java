package com.xuvigan.aurumauction.gui;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.xuvigan.aurumauction.AurumAuction;
import com.xuvigan.aurumauction.util.Scheduler;

/**
 * Captures the next chat message of a player (GUI search input, future prompts).
 * The chat event is asynchronous, so the callback is re-scheduled on the main
 * thread before it may touch inventories or the economy.
 */
public final class ChatInput implements Listener {

    public interface Handler {
        void accept(String text);
    }

    private static final class Entry {

        private final Handler handler;
        private final long expiresAt;

        private Entry(Handler handler, long expiresAt) {
            this.handler = handler;
            this.expiresAt = expiresAt;
        }
    }

    private final AurumAuction plugin;
    private final Map<UUID, Entry> pending = new ConcurrentHashMap<UUID, Entry>();
    private Scheduler.TaskHandle task;

    public ChatInput(AurumAuction plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        this.task = plugin.scheduler().runTimer(new Runnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                Iterator<Map.Entry<UUID, Entry>> iterator = pending.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<UUID, Entry> entry = iterator.next();
                    if (entry.getValue().expiresAt > now) {
                        continue;
                    }
                    iterator.remove();
                    final Player player = plugin.getServer().getPlayer(entry.getKey());
                    if (player != null && player.isOnline()) {
                        plugin.scheduler().runFor(player, new Runnable() {
                            @Override
                            public void run() {
                                plugin.messages().send(player, "gui-search-timeout");
                            }
                        });
                    }
                }
            }
        }, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            try {
                task.cancel();
            } catch (Throwable ignored) {
                // already cancelled
            }
            task = null;
        }
        pending.clear();
    }

    public void request(Player player, String promptPath, Handler handler) {
        if (player == null || handler == null) {
            return;
        }
        long timeout = plugin.config().searchInputTimeoutSeconds() * 1000L;
        pending.put(player.getUniqueId(), new Entry(handler, System.currentTimeMillis() + timeout));
        plugin.messages().send(player, promptPath);
    }

    public boolean isWaiting(Player player) {
        return player != null && pending.containsKey(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        final Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        final Entry entry = pending.remove(player.getUniqueId());
        if (entry == null) {
            return;
        }
        event.setCancelled(true);
        final String text = event.getMessage() == null ? "" : event.getMessage().trim();
        if (text.isEmpty()) {
            return;
        }
        if ("отмена".equalsIgnoreCase(text) || "cancel".equalsIgnoreCase(text)) {
            plugin.scheduler().runFor(player, new Runnable() {
                @Override
                public void run() {
                    plugin.messages().send(player, "gui-search-cancelled");
                }
            });
            return;
        }
        plugin.scheduler().runFor(player, new Runnable() {
            @Override
            public void run() {
                try {
                    entry.handler.accept(text);
                } catch (Throwable throwable) {
                    plugin.getLogger().warning("Chat input handler failed: "
                            + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
                }
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (event.getPlayer() != null) {
            pending.remove(event.getPlayer().getUniqueId());
        }
    }
}
