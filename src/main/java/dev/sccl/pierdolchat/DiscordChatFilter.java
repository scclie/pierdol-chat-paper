package dev.sccl.pierdolchat;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.GameChatMessagePreProcessEvent;
import java.util.Set;
import java.util.UUID;
import org.bukkit.event.Event;

/** Loaded only when DiscordSRV is enabled. Never cancels Minecraft chat. */
public final class DiscordChatFilter {
    private final Set<UUID> localPlayers;
    private final Set<Event> localMessages;

    private DiscordChatFilter(Set<UUID> localPlayers, Set<Event> localMessages) {
        this.localPlayers = localPlayers;
        this.localMessages = localMessages;
    }

    static Runnable subscribe(Set<UUID> localPlayers, Set<Event> localMessages) {
        var filter = new DiscordChatFilter(localPlayers, localMessages);
        DiscordSRV.api.subscribe(filter);
        return () -> DiscordSRV.api.unsubscribe(filter);
    }

    @Subscribe
    public void onChat(GameChatMessagePreProcessEvent event) {
        if (localMessages.contains(event.getTriggeringBukkitEvent())
                || localPlayers.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
