package dev.sccl.pierdolchat;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

final class AddHeadsTabHeads {
    private final Plugin addHeads;
    private final Object provider;
    private final Object options;
    private final Method prependHead;

    private AddHeadsTabHeads(Plugin addHeads, Object provider, Object options, Method prependHead) {
        this.addHeads = addHeads;
        this.provider = provider;
        this.options = options;
        this.prependHead = prependHead;
    }

    static AddHeadsTabHeads create(JavaPlugin owner, Consumer<Player> refresh) {
        try {
            Plugin addHeads = owner.getServer().getPluginManager().getPlugin("AddHeads");
            if (addHeads != null && addHeads.isEnabled()) {
                ClassLoader loader = addHeads.getClass().getClassLoader();
                Class<?> providerType = loader.loadClass("goshkow.addhead.api.AddHeadsProvider");
                Class<?> targetType = loader.loadClass("goshkow.addhead.api.HeadRenderTarget");
                Class<?> optionsType = loader.loadClass("goshkow.addhead.api.HeadRenderOptions");
                Object provider = owner.getServer().getServicesManager().load(providerType);
                if (provider != null) {
                    Object options = providerType.getMethod("getDefaultOptions", targetType)
                            .invoke(provider, tabTarget(targetType));
                    if (optionsType.isInstance(options)) {
                        Method prependHead = providerType.getMethod("prependHead",
                                Component.class, UUID.class, String.class, optionsType);
                        AddHeadsTabHeads bridge = new AddHeadsTabHeads(addHeads, provider, options, prependHead);
                        registerSkinResolvedEvent(owner, loader, refresh);
                        return bridge;
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // the optional API must never prevent bare dimension-coloured names.
        }
        return new AddHeadsTabHeads(null, null, null, null);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object tabTarget(Class<?> targetType) {
        return Enum.valueOf(targetType.asSubclass(Enum.class), "TAB");
    }

    Component prepend(Player player, Component base) {
        try {
            if (provider != null && addHeads.isEnabled()) {
                Object result = prependHead.invoke(provider, base, player.getUniqueId(), player.getName(), options);
                if (result instanceof Component component) {
                    return component;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // keep the original component on missing, incompatible or failing APIs.
        }
        return base;
    }

    private static void registerSkinResolvedEvent(JavaPlugin owner, ClassLoader loader, Consumer<Player> refresh) {
        try {
            Class<? extends Event> eventType = loader.loadClass(
                    "goshkow.addhead.api.event.AddHeadsSkinResolvedEvent").asSubclass(Event.class);
            Method getPlayerId = eventType.getMethod("getPlayerId");
            if (getPlayerId.getReturnType() != UUID.class) {
                return;
            }
            EventExecutor executor = (listener, event) -> {
                try {
                    if (!eventType.isInstance(event)) {
                        return;
                    }
                    Object playerId = getPlayerId.invoke(event);
                    if (playerId instanceof UUID uuid) {
                        Player player = owner.getServer().getPlayer(uuid);
                        if (player != null && player.isOnline() && owner.isEnabled()) {
                            owner.getServer().getScheduler().runTask(owner, () -> {
                                if (owner.isEnabled() && player.isOnline()) {
                                    refresh.accept(player);
                                }
                            });
                        }
                    }
                } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                    // Malformed optional events do not affect the existing TAB name.
                }
            };
            owner.getServer().getPluginManager().registerEvent(
                    eventType, new Listener() { }, EventPriority.MONITOR, executor, owner);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // event support is optional even when head rendering is available.
        }
    }
}
