package dev.sccl.pierdolchat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.logging.Logger;

final class DimensionNames {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final String overworld;
    private final String nether;
    private final String end;

    DimensionNames(FileConfiguration config, Logger logger) {
        overworld = loadFormat(config, logger, "overworld");
        nether = loadFormat(config, logger, "nether");
        end = loadFormat(config, logger, "end");
    }

    private static String loadFormat(FileConfiguration config, Logger logger, String dimension) {
        String key = "name-formats." + dimension;
        Object value = config.get(key, null);
        if (value instanceof String format) {
            return format;
        }
        logger.warning("Missing or non-string " + key + "; using plain <name>.");
        return "<name>";
    }

    Component nickname(String playerName, World.Environment environment) {
        String format = switch (environment) {
            case NETHER -> nether;
            case THE_END -> end;
            case null, default -> overworld;
        };
        return MINI_MESSAGE.deserialize(format, Placeholder.unparsed("name", playerName));
    }
}
