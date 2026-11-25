package dev.sccl.pierdolchat;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.ChatEvent;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
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
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class PierdolChatPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private static final Component AFK_PREFIX = Component.text("⌚ ");
    private Plugin playtimePlugin;
    private Method playtimeIsAfk;
    private ChatRenderer globalRenderer;
    private ChatRenderer localRenderer;
    private double localRadius;
    private DimensionNames dimensionNames;
    private final HeadComponents headComponents = new HeadComponents();

    private final Set<UUID> localPlayers = ConcurrentHashMap.newKeySet();
    // DiscordSRV defers processing: retain each local decision even after /global.
    private final Set<Event> localMessages = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<>()));
    private Runnable unregisterDiscord;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dimensionNames = new DimensionNames(getConfig(), getLogger());
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
        getServer().getOnlinePlayers().forEach(headComponents::refresh);
        refreshPlayerListNames();
        getServer().getScheduler().runTaskTimer(this, this::refreshPlayerListNames, 20L, 20L);
        if (getServer().getPluginManager().isPluginEnabled("DiscordSRV")) {
            unregisterDiscord = DiscordChatFilter.subscribe(localPlayers, localMessages);
        }
    }

    @Override
    public void onDisable() {
        if (unregisterDiscord != null) unregisterDiscord.run();
        unregisterDiscord = null;
        localMessages.clear();
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
        boolean local = localPlayers.contains(event.getPlayer().getUniqueId());
        if (local) localMessages.add(event);
        event.renderer(local ? localRenderer : globalRenderer);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(ChatEvent event) {
        boolean local = localPlayers.contains(event.getPlayer().getUniqueId());
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

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        headComponents.refresh(event.getPlayer());
        updatePlayerListName(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        updatePlayerListName(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        headComponents.forget(event.getPlayer());
        localPlayers.remove(event.getPlayer().getUniqueId());
    }

    private boolean isAfk(Player player) {
        Plugin current = getServer().getPluginManager().getPlugin("PierdolPlaytime");
        if (current == null || !current.isEnabled()) {
            playtimePlugin = null;
            playtimeIsAfk = null;
            return false;
        }
        if (current != playtimePlugin) {
            playtimePlugin = current;
            try {
                playtimeIsAfk = current.getClass().getMethod("isAfk", Player.class);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                playtimeIsAfk = null;
            }
        }
        if (playtimeIsAfk == null) return false;
        try {
            Object result = playtimeIsAfk.invoke(current, player);
            return result instanceof Boolean afk && afk;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private void refreshPlayerListNames() {
        getServer().getOnlinePlayers().forEach(this::updatePlayerListName);
    }

    private void updatePlayerListName(Player player) {
        Component base = dimensionNames.nickname(player.getName(), player.getWorld().getEnvironment());
        Component decoratedName = isAfk(player)
                ? Component.empty().append(AFK_PREFIX).append(base)
                : base;
        Component desired = Component.empty()
                .append(headComponents.prefix(player))
                .append(decoratedName);
        if (!desired.equals(player.playerListName())) {
            player.playerListName(desired);
        }
    }

    private ChatRenderer renderer(Component prefix) {
        return ChatRenderer.viewerUnaware((source, sourceDisplayName, message) ->
                Component.empty().append(headComponents.prefix(source)).append(prefix)
                        .append(Component.space())
                        .append(dimensionNames.nickname(source.getName(), source.getWorld().getEnvironment()))
                        .append(Component.text(": ", NamedTextColor.WHITE))
                        .append(Component.text().color(NamedTextColor.WHITE).append(message).build()));
    }
}
