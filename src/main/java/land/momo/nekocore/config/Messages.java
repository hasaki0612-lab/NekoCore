package land.momo.nekocore.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class Messages {
    private static final LegacyComponentSerializer COLORS = LegacyComponentSerializer.builder().character('&').hexColors().build();
    private final YamlConfiguration yaml;
    private Map<String, String> globals = Map.of();
    private Messages(YamlConfiguration yaml) { this.yaml = yaml; }

    public Messages globals(Map<String, String> values) {
        globals = Map.copyOf(values);
        return this;
    }

    public static Messages load(File file, InputStream defaults) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file);
        YamlConfiguration fallback = new YamlConfiguration();
        try (Reader reader = new InputStreamReader(Objects.requireNonNull(defaults), StandardCharsets.UTF_8)) { fallback.load(reader); }
        for (String key : fallback.getKeys(true)) {
            if (fallback.isConfigurationSection(key)) continue;
            if (!yaml.contains(key)) yaml.set(key, fallback.get(key));
            if (fallback.isString(key) && !yaml.isString(key) || fallback.isList(key) && !yaml.isList(key))
                throw new IllegalArgumentException("messages.yml: " + key + " 类型不正确");
        }
        return new Messages(yaml);
    }

    public String raw(String key) { return replace(yaml.getString(key, key), globals); }
    public List<String> lines(String key) { return yaml.getStringList(key).stream().map(line -> replace(line, globals)).toList(); }
    public Component component(String key, Map<String, String> vars) { return text(raw("prefix") + raw(key), vars); }
    public void send(CommandSender sender, String key) { send(sender, key, Map.of()); }
    public void send(CommandSender sender, String key, Map<String, String> vars) { sender.sendMessage(component(key, vars)); }
    public void profile(CommandSender sender, Map<String, String> vars) {
        for (String line : yaml.getStringList("profile")) sender.sendMessage(text(replace(line, globals), vars));
    }
    public String plain(String key, Map<String, String> vars) {
        return PlainTextComponentSerializer.plainText().serialize(text(raw(key), vars));
    }
    public static Component text(String template, Map<String, String> vars) { return COLORS.deserialize(replace(template, vars)); }
    public static String legacy(String template, Map<String, String> vars) {
        return LegacyComponentSerializer.builder().character('§').hexColors().useUnusualXRepeatedCharacterHexFormat().build().serialize(text(template, vars));
    }
    public static String replace(String template, Map<String, String> vars) {
        String value = template;
        for (var entry : vars.entrySet()) value = value.replace("{" + entry.getKey() + "}", entry.getValue());
        return value;
    }
}
