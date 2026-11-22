package dev.sccl.pierdolchat;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.ChatEvent;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class PierdolChatPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private ChatRenderer globalRenderer;
    private ChatRenderer localRenderer;
    private double localRadius;

    private final Set<UUID> localPlayers = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        MiniMessage miniMessage = MiniMessage.miniMessage();
        globalRenderer = renderer(miniMessage.deserialize(
                getConfig().getString("global-prefix", "<dark_green>[G]</dark_green>")));
        localRenderer = renderer(miniMessage.deserialize(
                getConfig().getString("local-prefix", "<gold>[L]</gold>")));
        Object configuredRadius = getConfig().get("local-radius");
        localRadius = configuredRadius instanceof Number number ? number.doubleValue() : Double.NaN;
        if (!Double.isFinite(localRadius) || localRadius <= 0) {
            getLogger().warning("Invalid local-radius; using 100 blocks.");
            localRadius = 100.0;
        }
        Objects.requireNonNull(getCommand("local")).setExecutor(this);
        Objects.requireNonNull(getCommand("global")).setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        localPlayers.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.GRAY));
            return true;
        }

        if (command.getName().equalsIgnoreCase("local")) {
            localPlayers.add(player.getUniqueId());
            String radius = java.math.BigDecimal.valueOf(localRadius).stripTrailingZeros().toPlainString();
            player.sendMessage(Component.text("Local chat enabled (" + radius + " blocks).", NamedTextColor.GOLD));
        } else {
            localPlayers.remove(player.getUniqueId());
            player.sendMessage(Component.text("Global chat enabled.", NamedTextColor.DARK_GREEN));
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        event.renderer(localPlayers.contains(event.getPlayer().getUniqueId())
                ? localRenderer : globalRenderer);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(ChatEvent event) {
        boolean local = localPlayers.contains(event.getPlayer().getUniqueId());
        event.renderer(local ? localRenderer : globalRenderer);
        if (!local) {
            return;
        }

        Location origin = event.getPlayer().getLocation();
        event.viewers().removeIf(viewer -> {
            if (!(viewer instanceof Player recipient)) {
                return true;
            }
            Location target = recipient.getLocation();
            return !Objects.equals(origin.getWorld(), target.getWorld())
                    || origin.distance(target) > localRadius;
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        localPlayers.remove(event.getPlayer().getUniqueId());
    }

    private static ChatRenderer renderer(Component prefix) {
        return ChatRenderer.viewerUnaware((source, sourceDisplayName, message) ->
                Component.empty().append(prefix)
                        .append(Component.text(" " + source.getName() + ": ", NamedTextColor.GRAY))
                        .append(Component.text().color(NamedTextColor.WHITE).append(message).build()));
    }
}
