package com.jellypudding.stopSpam;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class StopSpam extends JavaPlugin implements Listener {
    private final Map<UUID, Long> lastMessageTime = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> violationCount = new ConcurrentHashMap<>();
    private final Map<UUID, Long> timeoutUntil = new ConcurrentHashMap<>();
    private final Map<UUID, LinkedList<MessageEntry>> recentMessages = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> timeoutDurations = new HashMap<>();
    private final Random random = new Random();
    private long messageCooldown;
    private List<String> warningMessages;
    private boolean similarityEnabled;
    private double similarityThreshold;
    private int recentMessagesToCheck;
    private int similarityTimeWindow;
    private int repetitionThreshold;

    // Class to store message with timestamp
    private static class MessageEntry {
        private final String content;
        private final long timestamp;

        public MessageEntry(String content, long timestamp) {
            this.content = content;
            this.timestamp = timestamp;
        }

        public String getContent() {
            return content;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();

        getServer().getPluginManager().registerEvents(this, this);

        scheduleCleanupTask();

        getLogger().info("StopSpam has been enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("StopSpam has been disabled.");
    }

    private void loadConfig() {
        reloadConfig();
        FileConfiguration config = getConfig();

        messageCooldown = config.getLong("message-cooldown", 280);
        warningMessages = config.getStringList("warning-messages");
        if (warningMessages.isEmpty()) {
            warningMessages = Collections.singletonList("Please slow down your messages!");
        }

        similarityEnabled = config.getBoolean("similarity.enabled", true);
        similarityThreshold = config.getDouble("similarity.threshold", 0.9);
        recentMessagesToCheck = config.getInt("similarity.recent-messages-to-check", 15);
        similarityTimeWindow = config.getInt("similarity.time-window", 20);
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

    private void scheduleCleanupTask() {
        // Regular cleanup (every 4 hours)
        long hourlyCleanupInterval = 20L * 60 * 60 * 4; // Every 4 hours (in ticks)
        getServer().getScheduler().runTaskTimer(this, () -> {
            long currentTime = System.currentTimeMillis();

            // Remove expired timeouts
            timeoutUntil.entrySet().removeIf(entry -> currentTime > entry.getValue());

            // Remove old violation counts (older than 4 hours)
            long fourHoursAgo = currentTime - (4 * 60 * 60 * 1000);
            lastMessageTime.entrySet().removeIf(entry -> entry.getValue() < fourHoursAgo);
            violationCount.entrySet().removeIf(entry -> !lastMessageTime.containsKey(entry.getKey()));

            // Remove players with no recent activity from message history
            recentMessages.entrySet().removeIf(entry -> !lastMessageTime.containsKey(entry.getKey()));

            getLogger().info("Cleaned up expired timeouts and old violation records");
        }, hourlyCleanupInterval, hourlyCleanupInterval);

        // Message expiry cleanup (every minute)
        long messageCleanupInterval = 20L * 60; // Every minute (in ticks)
        getServer().getScheduler().runTaskTimer(this, () -> {
            long currentTime = System.currentTimeMillis();
            long expiryTime = currentTime - (similarityTimeWindow * 1000L);

            // Clean up old messages for each player
            for (LinkedList<MessageEntry> messages : recentMessages.values()) {
                messages.removeIf(entry -> entry.getTimestamp() < expiryTime);
            }
        }, messageCleanupInterval, messageCleanupInterval);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (command.getName().equalsIgnoreCase("stopspam")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("stopspam.admin")) {
                    sender.sendMessage(Component.text("You don't have permission to use this command.",
                            NamedTextColor.RED));
                    return true;
                }

                loadConfig();
                sender.sendMessage(Component.text("StopSpam configuration reloaded.",
                        NamedTextColor.GREEN));
                return true;
            }
            return false;
        }
        return false;
    }

    private double calculateSimilarity(String str1, String str2) {
        if (str1.equals(str2)) {
            return 1.0;
        }

        // Calculate Levenshtein distance
        int distance = levenshteinDistance(str1, str2);

        // Convert to similarity score (1.0 means identical)
        double maxLength = Math.max(str1.length(), str2.length());
        return 1.0 - (distance / maxLength);
    }

    private int levenshteinDistance(String str1, String str2) {
        int[][] distance = new int[str1.length() + 1][str2.length() + 1];

        for (int i = 0; i <= str1.length(); i++) {
            distance[i][0] = i;
        }

        for (int j = 0; j <= str2.length(); j++) {
            distance[0][j] = j;
        }

        for (int i = 1; i <= str1.length(); i++) {
            for (int j = 1; j <= str2.length(); j++) {
                int cost = (str1.charAt(i - 1) == str2.charAt(j - 1)) ? 0 : 1;
                distance[i][j] = Math.min(
                    Math.min(distance[i - 1][j] + 1, distance[i][j - 1] + 1),
                    distance[i - 1][j - 1] + cost
                );
            }
        }

        return distance[str1.length()][str2.length()];
    }

    private void applyViolation(Player player, UUID playerId, long currentTime, String reason) {
        int violations = violationCount.getOrDefault(playerId, 0) + 1;
        violationCount.put(playerId, violations);

        int cappedViolations = Math.min(violations, 8);
        int timeoutDuration = timeoutDurations.get(cappedViolations);
        timeoutUntil.put(playerId, currentTime + (timeoutDuration * 1000L));

        String message = reason != null ?
            "You have been muted for " + timeoutDuration + " seconds due to " + reason + "." :
            "You have been muted for " + timeoutDuration + " seconds.";

        player.sendMessage(Component.text(message, NamedTextColor.RED));
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        // Extract message content as string
        String messageContent = "";
        if (event.message() instanceof TextComponent textComponent) {
            messageContent = textComponent.content();
        } else {
            messageContent = event.message().toString();
        }

        // Check timeout logic
        if (timeoutUntil.containsKey(playerId)) {
            long timeoutEnd = timeoutUntil.get(playerId);
            if (currentTime < timeoutEnd) {
                long remainingSeconds = (timeoutEnd - currentTime) / 1000;
                player.sendMessage(Component.text("You are muted for " + remainingSeconds + " more seconds.",
                        NamedTextColor.RED));
                event.setCancelled(true);
                return;
            } else {
                timeoutUntil.remove(playerId);
            }
        }

        if (!recentMessages.containsKey(playerId)) {
            recentMessages.put(playerId, new LinkedList<>());
        }

        LinkedList<MessageEntry> playerMessages = recentMessages.get(playerId);

        // Clean up old messages beyond time window
        long expiryTime = currentTime - (similarityTimeWindow * 1000L);
        playerMessages.removeIf(entry -> entry.getTimestamp() < expiryTime);

        // Check for message similarity if enabled
        if (similarityEnabled && !playerMessages.isEmpty()) {
            Map<String, Integer> similarityGroups = new HashMap<>();

            // First, group current message with similar ones
            similarityGroups.put(messageContent, 1);

            // Check each recent message for similarity
            for (MessageEntry entry : playerMessages) {
                String content = entry.getContent();

                // For each existing group, check if this message is similar
                boolean added = false;
                for (String groupKey : new ArrayList<>(similarityGroups.keySet())) {
                    if (calculateSimilarity(content, groupKey) >= similarityThreshold) {
                        // Add to existing group
                        similarityGroups.put(groupKey, similarityGroups.get(groupKey) + 1);
                        added = true;
                        break;
                    }
                }

                // If not added to any group, create a new one
                if (!added) {
                    similarityGroups.put(content, 1);
                }
            }

            // Check if any group exceeds the repetition threshold
            boolean isSpam = false;
            for (Integer count : similarityGroups.values()) {
                if (count >= repetitionThreshold) {
                    isSpam = true;
                    break;
                }
            }

            if (isSpam) {
                event.setCancelled(true);
                String randomWarning = warningMessages.get(random.nextInt(warningMessages.size()));
                player.sendMessage(Component.text(randomWarning, NamedTextColor.RED));

                // Apply timeout
                applyViolation(player, playerId, currentTime, "repetitive messages");
                return;
            }
        }

        // Message cooldown check
        if (lastMessageTime.containsKey(playerId)) {
            long timeSinceLastMessage = currentTime - lastMessageTime.get(playerId);
            if (timeSinceLastMessage < messageCooldown) {
                event.setCancelled(true);
                String randomWarning = warningMessages.get(random.nextInt(warningMessages.size()));
                player.sendMessage(Component.text(randomWarning, NamedTextColor.RED));

                // Apply timeout
                applyViolation(player, playerId, currentTime, null);
                return;
            }
        }

        // Update last message time and add to recent messages
        lastMessageTime.put(playerId, currentTime);

        // Add new message to the list
        playerMessages.addFirst(new MessageEntry(messageContent, currentTime));

        // Keep only the most recent N messages
        while (playerMessages.size() > recentMessagesToCheck) {
            playerMessages.removeLast();
        }
    }
}