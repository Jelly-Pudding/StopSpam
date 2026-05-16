package com.jellypudding.stopSpam.spam;

import com.jellypudding.stopSpam.config.PluginConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SpamDetector {

    private final PluginConfig config;
    private final Random random = new Random();

    private final Map<UUID, Long> lastMessageTime = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> violationCount = new ConcurrentHashMap<>();
    private final Map<UUID, Long> timeoutUntil = new ConcurrentHashMap<>();
    private final Map<UUID, LinkedList<MessageEntry>> recentMessages = new ConcurrentHashMap<>();

    public SpamDetector(PluginConfig config) {
        this.config = config;
    }

    /**
     * Extracts the text content from spam-relevant commands (/me, /msg, /tell, /w).
     * Returns null for commands that don't carry chat-like content.
     */
    public String extractMessageFromCommand(String command) {
        String lower = command.toLowerCase();

        if (lower.startsWith("/me ")) {
            return command.substring(4);
        }
        if (lower.startsWith("/msg ") || lower.startsWith("/tell ") || lower.startsWith("/w ")) {
            String[] parts = command.split(" ", 3);
            if (parts.length >= 3) {
                return parts[2];
            }
        }
        return null;
    }

    /**
     * Returns true if the player is currently timed out, sending them the remaining-time message.
     */
    public boolean checkTimeout(Player player, UUID playerId, long currentTime) {
        Long timeoutEnd = timeoutUntil.get(playerId);
        if (timeoutEnd == null) return false;

        if (currentTime < timeoutEnd) {
            long remaining = (timeoutEnd - currentTime) / 1000;
            player.sendMessage(Component.text("You are muted for " + remaining + " more seconds.", NamedTextColor.RED));
            return true;
        }

        timeoutUntil.remove(playerId);
        return false;
    }

    /**
     * Checks for spam and applies a violation if detected.
     * Returns true if the message should be cancelled.
     */
    public boolean checkSpamAndApplyViolation(Player player, UUID playerId, String messageContent, long currentTime, String source) {
        recentMessages.computeIfAbsent(playerId, k -> new LinkedList<>());
        LinkedList<MessageEntry> playerMessages = recentMessages.get(playerId);

        long expiryTime = currentTime - (config.getSimilarityTimeWindow() * 1000L);
        playerMessages.removeIf(entry -> entry.timestamp() < expiryTime);

        if (config.isRateLimitEnabled()) {
            long rateLimitExpiry = currentTime - (config.getRateLimitTimeWindow() * 1000L);
            int messagesInWindow = 1;
            for (MessageEntry entry : playerMessages) {
                if (entry.timestamp() > rateLimitExpiry) messagesInWindow++;
            }

            if (messagesInWindow > config.getRateLimitMaxMessages()) {
                sendRandomWarning(player);
                applyViolation(player, playerId, currentTime, "sending " + source + " too quickly");
                return true;
            }
        }

        if (config.isSimilarityEnabled() && !playerMessages.isEmpty()) {
            if (isSimilarSpam(messageContent, playerMessages)) {
                sendRandomWarning(player);
                applyViolation(player, playerId, currentTime, "repetitive " + source);
                return true;
            }
        }

        if (lastMessageTime.containsKey(playerId)) {
            long timeSinceLastMessage = currentTime - lastMessageTime.get(playerId);
            if (timeSinceLastMessage < config.getMessageCooldown()) {
                sendRandomWarning(player);
                applyViolation(player, playerId, currentTime, null);
                return true;
            }
        }

        lastMessageTime.put(playerId, currentTime);
        playerMessages.addFirst(new MessageEntry(messageContent, currentTime));
        while (playerMessages.size() > config.getRecentMessagesToCheck()) {
            playerMessages.removeLast();
        }

        return false;
    }

    /**
     * Removes expired timeouts and stale violation/message records (call every few hours).
     */
    public void cleanupExpiredData(long currentTime) {
        timeoutUntil.entrySet().removeIf(entry -> currentTime > entry.getValue());

        long fourHoursAgo = currentTime - (4L * 60 * 60 * 1000);
        lastMessageTime.entrySet().removeIf(entry -> entry.getValue() < fourHoursAgo);
        violationCount.entrySet().removeIf(entry -> !lastMessageTime.containsKey(entry.getKey()));
        recentMessages.entrySet().removeIf(entry -> !lastMessageTime.containsKey(entry.getKey()));
    }

    /**
     * Removes individual messages that have aged out of the similarity window (call every minute).
     */
    public void cleanupOldMessages(long currentTime) {
        long expiryTime = currentTime - (config.getSimilarityTimeWindow() * 1000L);
        for (LinkedList<MessageEntry> messages : recentMessages.values()) {
            messages.removeIf(entry -> entry.timestamp() < expiryTime);
        }
    }

    private boolean isSimilarSpam(String messageContent, LinkedList<MessageEntry> playerMessages) {
        Map<String, Integer> groups = new HashMap<>();
        groups.put(messageContent, 1);

        for (MessageEntry entry : playerMessages) {
            String content = entry.content();
            boolean matched = false;
            for (String key : new ArrayList<>(groups.keySet())) {
                if (calculateSimilarity(content, key) >= config.getSimilarityThreshold()) {
                    groups.merge(key, 1, Integer::sum);
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                groups.put(content, 1);
            }
        }

        return groups.values().stream().anyMatch(count -> count >= config.getRepetitionThreshold());
    }

    private void applyViolation(Player player, UUID playerId, long currentTime, String reason) {
        int violations = violationCount.merge(playerId, 1, Integer::sum);
        int capped = Math.min(violations, 8);
        int duration = config.getTimeoutDurations().get(capped);
        timeoutUntil.put(playerId, currentTime + (duration * 1000L));

        String message = reason != null
                ? "You have been muted for " + duration + " seconds due to " + reason + "."
                : "You have been muted for " + duration + " seconds.";
        player.sendMessage(Component.text(message, NamedTextColor.RED));
    }

    private void sendRandomWarning(Player player) {
        List<String> warnings = config.getWarningMessages();
        player.sendMessage(Component.text(warnings.get(random.nextInt(warnings.size())), NamedTextColor.RED));
    }

    private double calculateSimilarity(String a, String b) {
        if (a.equals(b)) return 1.0;
        int distance = levenshteinDistance(a, b);
        return 1.0 - (distance / (double) Math.max(a.length(), b.length()));
    }

    private int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[a.length()][b.length()];
    }
}
