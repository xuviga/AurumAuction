package com.xuvigan.aurumauction.util;

import org.bukkit.entity.Player;

/** Scheduler abstraction: Bukkit main thread or Folia region/global schedulers. */
public interface Scheduler {

    interface TaskHandle {
        void cancel();
    }

    boolean isFolia();

    TaskHandle runGlobal(Runnable task);

    TaskHandle runTimer(Runnable task, long delayTicks, long periodTicks);

    /** Runs on the player's region (Folia) or the main thread (Bukkit). */
    void runFor(Player player, Runnable task);

    void runAsync(Runnable task);
}
