package com.jellypudding.stopSpam.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PluginConfig {

    private long messageCooldown;
    private List<String> warningMessages;

    private boolean rateLimitEnabled;
    private int rateLimitMaxMessages;
    private int rateLimitTimeWindow;

    private boolean similarityEnabled;
    private double similarityThreshold;
    private int recentMessagesToCheck;
    private int similarityTimeWindow;
    private int repetitionThreshold;

    private final Map<Integer, Integer> timeoutDurations = new HashMap<>();

    public void load(JavaPlugin plugin) {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        messageCooldown = config.getLong("message-cooldown", 280);

        warningMessages = config.getStringList("warning-messages");
        if (warningMessages.isEmpty()) {
            warningMessages = Collections.singletonList("Please slow down your messages!");
        }

        rateLimitEnabled = config.getBoolean("rate-limit.enabled", true);
        rateLimitMaxMessages = config.getInt("rate-limit.max-messages", 9);
        rateLimitTimeWindow = config.getInt("rate-limit.time-window", 5);

        similarityEnabled = config.getBoolean("similarity.enabled", true);
        similarityThreshold = config.getDouble("similarity.threshold", 0.85);
        recentMessagesToCheck = config.getInt("similarity.recent-messages-to-check", 64);
        similarityTimeWindow = config.getInt("similarity.time-window", 80);
        repetitionThreshold = config.getInt("similarity.repetition-threshold", 4);

        timeoutDurations.clear();
        timeoutDurations.put(1, config.getInt("timeouts.first", 10));
        timeoutDurations.put(2, config.getInt("timeouts.second", 20));
        timeoutDurations.put(3, config.getInt("timeouts.third", 30));
        timeoutDurations.put(4, config.getInt("timeouts.fourth", 100));
        timeoutDurations.put(5, config.getInt("timeouts.fifth", 300));
        timeoutDurations.put(6, config.getInt("timeouts.sixth", 600));
        timeoutDurations.put(7, config.getInt("timeouts.seventh", 1200));
        timeoutDurations.put(8, config.getInt("timeouts.eighth", 1800));
    }

    public long getMessageCooldown() { return messageCooldown; }
    public List<String> getWarningMessages() { return warningMessages; }

    public boolean isRateLimitEnabled() { return rateLimitEnabled; }
    public int getRateLimitMaxMessages() { return rateLimitMaxMessages; }
    public int getRateLimitTimeWindow() { return rateLimitTimeWindow; }

    public boolean isSimilarityEnabled() { return similarityEnabled; }
    public double getSimilarityThreshold() { return similarityThreshold; }
    public int getRecentMessagesToCheck() { return recentMessagesToCheck; }
    public int getSimilarityTimeWindow() { return similarityTimeWindow; }
    public int getRepetitionThreshold() { return repetitionThreshold; }

    public Map<Integer, Integer> getTimeoutDurations() { return timeoutDurations; }
}
