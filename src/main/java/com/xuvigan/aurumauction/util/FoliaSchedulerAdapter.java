package com.xuvigan.aurumauction.util;

import java.lang.reflect.Method;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Folia adapter kept reflection-only, so the same jar also runs on classic
 * Paper/Spigot. Uses GlobalRegionScheduler for timers and the entity scheduler
 * for player-bound work (inventories, messages).
 */
public final class FoliaSchedulerAdapter implements Scheduler {

    private final Plugin plugin;
    private final Object globalScheduler;
    private final Object asyncScheduler;
    private final Method globalExecute;
    private final Method globalTimer;
    private final Method asyncRunNow;
    private final Method entityGetScheduler;
    private final Method entityRun;

    public FoliaSchedulerAdapter(Plugin plugin) throws Exception {
        this.plugin = plugin;
        Class<?> globalType = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
        Class<?> asyncType = Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
        Class<?> entitySchedulerType = Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
        this.globalScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
        this.asyncScheduler = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
        this.globalExecute = globalType.getMethod("execute", Plugin.class, Runnable.class);
        this.globalTimer = globalType.getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);
        this.asyncRunNow = asyncType.getMethod("runNow", Plugin.class, Consumer.class);
        this.entityGetScheduler = Player.class.getMethod("getScheduler");
        this.entityRun = entitySchedulerType.getMethod("run", Plugin.class, Consumer.class, Runnable.class);
    }

    @Override
    public boolean isFolia() {
        return true;
    }

    @Override
    public TaskHandle runGlobal(Runnable task) {
        try {
            globalExecute.invoke(globalScheduler, plugin, task);
        } catch (Throwable throwable) {
            task.run();
        }
        return new TaskHandle() {
            @Override
            public void cancel() {
                // one-shot task
            }
        };
    }

    @Override
    public TaskHandle runTimer(Runnable task, long delayTicks, long periodTicks) {
        final Object scheduled;
        try {
            scheduled = globalTimer.invoke(globalScheduler, plugin, consumer(task),
                    Long.valueOf(Math.max(1L, delayTicks)), Long.valueOf(Math.max(1L, periodTicks)));
        } catch (Throwable throwable) {
            throw new IllegalStateException("Folia global timer failed: " + throwable.getMessage(), throwable);
        }
        return new TaskHandle() {
            @Override
            public void cancel() {
                try {
                    scheduled.getClass().getMethod("cancel").invoke(scheduled);
                } catch (Throwable ignored) {
                    // already cancelled
                }
            }
        };
    }

    @Override
    public void runFor(Player player, Runnable task) {
        if (player == null || !player.isOnline()) {
            runGlobal(task);
            return;
        }
        try {
            Object entityScheduler = entityGetScheduler.invoke(player);
            entityRun.invoke(entityScheduler, plugin, consumer(task), new Runnable() {
                @Override
                public void run() {
                    // entity retired: nothing to do
                }
            });
        } catch (Throwable throwable) {
            runGlobal(task);
        }
    }

    @Override
    public void runAsync(Runnable task) {
        try {
            asyncRunNow.invoke(asyncScheduler, plugin, consumer(task));
        } catch (Throwable throwable) {
            task.run();
        }
    }

    private static Consumer<Object> consumer(final Runnable task) {
        return new Consumer<Object>() {
            @Override
            public void accept(Object ignored) {
                task.run();
            }
        };
    }
}
