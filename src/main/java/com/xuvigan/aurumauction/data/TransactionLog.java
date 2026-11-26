package com.xuvigan.aurumauction.data;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.xuvigan.aurumauction.AurumAuction;
import com.xuvigan.aurumauction.model.AuctionLot;
import com.xuvigan.aurumauction.util.Items;

/** Append-only transaction log for audits and dispute resolution. */
public final class TransactionLog {

    private final AurumAuction plugin;
    private final File file;
    private final boolean enabled;
    private final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public TransactionLog(AurumAuction plugin, File file, boolean enabled) {
        this.plugin = plugin;
        this.file = file;
        this.enabled = enabled;
    }

    public synchronized void log(String type, AuctionLot lot, String actor, String target,
                                 double price, double tax) {
        if (!enabled || lot == null) {
            return;
        }
        String line = format.format(new Date())
                + " | " + type
                + " | lot=" + lot.id()
                + " | " + Items.displayName(lot.item()) + " x" + lot.amount()
                + " | actor=" + safe(actor)
                + " | target=" + safe(target)
                + " | price=" + price
                + " | tax=" + tax
                + System.lineSeparator();
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return;
            }
            Files.write(file.toPath(), line.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            if (plugin != null) {
                plugin.getLogger().warning("Could not write transaction log: " + ex.getMessage());
            }
        }
    }

    private static String safe(String value) {
        return value == null ? "?" : value.replace('|', '/');
    }
}
