package dev.sccl.pierdolchat;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;
import org.bukkit.entity.Player;

/**
 * join-time native heads derived from AddHeads (https://github.com/goshkow/addheads).
 * AddHeads Custom Non-Commercial Source-Available License
 * Copyright (c) 2026 goshkow
 */
final class HeadComponents {
    private static final Key DEFAULT_FONT = Key.key("minecraft", "default");
    private static final String SEPARATOR_TEXT = "\u200C\u200C";
    private static final Component SEPARATOR = Component.text(SEPARATOR_TEXT)
            .font(DEFAULT_FONT)
            .color(NamedTextColor.WHITE)
            .shadowColor(ShadowColor.none())
            .decoration(TextDecoration.ITALIC, false)
            .decoration(TextDecoration.BOLD, true)
            .decoration(TextDecoration.OBFUSCATED, false)
            .decoration(TextDecoration.STRIKETHROUGH, false)
            .decoration(TextDecoration.UNDERLINED, false);

    private final ConcurrentHashMap<UUID, Component> prefixes = new ConcurrentHashMap<>();

    // Called only at enable/join on the main thread; never from a chat renderer.
    void refresh(Player player) {
        Texture texture = skinsRestorerTexture(player);
        if (texture == null) texture = profileTexture(player);
        prefixes.put(player.getUniqueId(), buildPrefix(player.getUniqueId(), player.getName(), texture));
    }

    void forget(Player player) {
        prefixes.remove(player.getUniqueId());
    }

    Component prefix(Player player) {
        Component cached = prefixes.get(player.getUniqueId());
        // Only stable identity getters on a miss: no profile, plugin, or skin access.
        return cached != null ? cached : buildPrefix(player.getUniqueId(), player.getName(), null);
    }

    private static Component buildPrefix(UUID id, String name, Texture texture) {
        PlayerHeadObjectContents.Builder builder = ObjectContents.playerHead()
                .name(name)
                .id(id)
                .hat(true);
        if (texture != null) {
            builder.profileProperty(texture.signature() == null || texture.signature().isBlank()
                    ? PlayerHeadObjectContents.property("textures", texture.value())
                    : PlayerHeadObjectContents.property("textures", texture.value(), texture.signature()));
        }
        Component head = Component.object(builder.build()).hoverEvent(Component.text(name));
        return Component.empty().append(head).append(SEPARATOR);
    }

    private static Texture skinsRestorerTexture(Player player) {
        try {
            Class<?> provider = Class.forName("net.skinsrestorer.api.SkinsRestorerProvider");
            Object api = provider.getMethod("get").invoke(null);
            Class<?> apiType = Class.forName("net.skinsrestorer.api.SkinsRestorer");
            Object storage = apiType.getMethod("getPlayerStorage").invoke(api);
            Class<?> storageType = Class.forName("net.skinsrestorer.api.storage.PlayerStorage");
            // getSkinForPlayer(UUID, String) can fetch Mojang/default skins on a miss.
            // Read only an already stored assignment, then fall back to Paper.
            Object result = storageType.getMethod("getSkinOfPlayer", UUID.class)
                    .invoke(storage, player.getUniqueId());
            if (result instanceof Optional<?> optional && optional.isPresent()) {
                Object property = optional.get();
                String value = propertyString(property, "value", "getValue");
                if (value != null && !value.isBlank()) {
                    return new Texture(value, propertyString(property, "signature", "getSignature"));
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // Optional integration: missing/inaccessible data falls back to the live profile.
        }
        return null;
    }

    private static String propertyString(Object property, String accessor, String getter) {
        for (String method : new String[] {accessor, getter}) {
            try {
                Object value = property.getClass().getMethod(method).invoke(property);
                if (value instanceof String text && !text.isBlank()) return text;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Support both record-style and bean-style property APIs.
            }
        }
        return null;
    }

    private static Texture profileTexture(Player player) {
        try {
            for (var property : player.getPlayerProfile().getProperties()) {
                if ("textures".equals(property.getName()) && !property.getValue().isBlank()) {
                    return new Texture(property.getValue(), property.getSignature());
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
            // A UUID/name-only native head must still be available without skin data.
        }
        return null;
    }

    private record Texture(String value, String signature) {}
}
