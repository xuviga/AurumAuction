package com.xuvigan.aurumauction.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** Classic Bukkit scheduler adapter. */
public final class BukkitSchedulerAdapter implements Scheduler {

    private final Plugin plugin;

    public BukkitSchedulerAdapter(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isFolia() {
        return false;
    }

    @Override
    public TaskHandle runGlobal(Runnable task) {
        final BukkitTask handle = Bukkit.getScheduler().runTask(plugin, task);
        return new TaskHandle() {
            @Override
            public void cancel() {
                handle.cancel();
            }
        };
    }

    @Override
    public TaskHandle runTimer(Runnable task, long delayTicks, long periodTicks) {
        final BukkitTask handle = Bukkit.getScheduler().runTaskTimer(plugin, task,
                Math.max(1L, delayTicks), Math.max(1L, periodTicks));
        return new TaskHandle() {
            @Override
            public void cancel() {
                handle.cancel();
            }
        };
    }

    @Override
    public void runFor(Player player, Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    public void runAsync(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }
}
