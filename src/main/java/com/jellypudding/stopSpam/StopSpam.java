package com.jellypudding.stopSpam;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class StopSpam extends JavaPlugin implements Listener, TabCompleter {
    private final Map<UUID, Long> lastMessageTime = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> violationCount = new ConcurrentHashMap<>();
    private final Map<UUID, Long> timeoutUntil = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> timeoutDurations = new HashMap<>();
    private final Random random = new Random();
    private long messageCooldown;
    private List<String> warningMessages;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();

        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("stopspam")).setTabCompleter(this);

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

        messageCooldown = config.getLong("message-cooldown", 1000);
        warningMessages = config.getStringList("warning-messages");
        if (warningMessages.isEmpty()) {
            warningMessages = Collections.singletonList("Please slow down your messages!");
        }

        timeoutDurations.clear();
        timeoutDurations.put(1, config.getInt("timeouts.first", 10));
        timeoutDurations.put(2, config.getInt("timeouts.second", 30));
        timeoutDurations.put(3, config.getInt("timeouts.third", 60));
        timeoutDurations.put(4, config.getInt("timeouts.fourth", 300));
        timeoutDurations.put(5, config.getInt("timeouts.fifth", 1800));
    }

    private void scheduleCleanupTask() {
        long cleanupInterval = 20L * 60 * 30; // Run every 30 minutes (in ticks)
        getServer().getScheduler().runTaskTimer(this, () -> {
            long currentTime = System.currentTimeMillis();

            // Remove expired timeouts
            timeoutUntil.entrySet().removeIf(entry -> currentTime > entry.getValue());

            // Remove old violation counts (older than 1 hour)
            long oneHourAgo = currentTime - (60 * 60 * 1000);
            lastMessageTime.entrySet().removeIf(entry -> entry.getValue() < oneHourAgo);
            violationCount.entrySet().removeIf(entry -> !lastMessageTime.containsKey(entry.getKey()));

        }, cleanupInterval, cleanupInterval);
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

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        if (command.getName().equalsIgnoreCase("stopspam") && args.length == 1) {
            List<String> completions = new ArrayList<>();
            if (sender.hasPermission("stopspam.admin") && "reload".startsWith(args[0].toLowerCase())) {
                completions.add("reload");
            }
            return completions;
        }
        return new ArrayList<>();
    }

    @EventHandler
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

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

        // Message cooldown check
        if (lastMessageTime.containsKey(playerId)) {
            long timeSinceLastMessage = currentTime - lastMessageTime.get(playerId);
            if (timeSinceLastMessage < messageCooldown) {
                event.setCancelled(true);
                String randomWarning = warningMessages.get(random.nextInt(warningMessages.size()));
                player.sendMessage(Component.text(randomWarning, NamedTextColor.RED));

                // Violation counting and timeout application
                int violations = violationCount.getOrDefault(playerId, 0) + 1;
                violationCount.put(playerId, violations);

                if (violations >= 1) {
                    int cappedViolations = Math.min(violations, 5);
                    int timeoutDuration = timeoutDurations.get(cappedViolations);
                    timeoutUntil.put(playerId, currentTime + (timeoutDuration * 1000L));
                    player.sendMessage(Component.text("You have been muted for " + timeoutDuration + " seconds.",
                            NamedTextColor.RED));
                }
                return;
            }
        }

        // Update last message time
        lastMessageTime.put(playerId, currentTime);
    }
}