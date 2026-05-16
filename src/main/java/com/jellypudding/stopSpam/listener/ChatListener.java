package com.jellypudding.stopSpam.listener;

import com.jellypudding.stopSpam.spam.SpamDetector;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.UUID;

public class ChatListener implements Listener {

    private final SpamDetector spamDetector;

    public ChatListener(SpamDetector spamDetector) {
        this.spamDetector = spamDetector;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        String messageContent = PlainTextComponentSerializer.plainText().serialize(event.message());

        if (spamDetector.checkTimeout(player, playerId, currentTime)) {
            event.setCancelled(true);
            return;
        }

        if (spamDetector.checkSpamAndApplyViolation(player, playerId, messageContent, currentTime, "messages")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        String messageContent = spamDetector.extractMessageFromCommand(event.getMessage());
        if (messageContent == null || messageContent.isBlank()) return;

        if (spamDetector.checkTimeout(player, playerId, currentTime)) {
            event.setCancelled(true);
            return;
        }

        if (spamDetector.checkSpamAndApplyViolation(player, playerId, messageContent, currentTime, "commands")) {
            event.setCancelled(true);
        }
    }
}
