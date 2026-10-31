package fr.thegostsniper.arffornia_proxy.motd;

import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parsed {@code motd.yml}. The file and its format notes live in Arffornia_Provisioning,
 * {@code config/proxy/storage/plugins/arffornia_proxy/motd.yml}.
 *
 * @param defaultLanguage language for pings from unknown addresses
 * @param supportedLanguages languages the file provides; others fall back to the default
 * @param zone            time zone used to decide which day it is
 * @param rememberLanguage whether to reuse the language of players who joined recently from
 *                        the same address (kept in memory only)
 * @param header          default first line, MiniMessage
 * @param entries         all entries, in file order
 */
public record MotdConfig(String defaultLanguage, List<String> supportedLanguages, ZoneId zone,
                         boolean rememberLanguage, String header, List<MotdEntry> entries) {

    public static final int DEFAULT_DATED_PRIORITY = 10;

    public MotdConfig {
        supportedLanguages = List.copyOf(supportedLanguages);
        entries = List.copyOf(entries);
    }

    public static MotdConfig load(Path file) throws IOException {
        return parse(Files.readString(file));
    }

    /**
     * Parses YAML text.
     *
     * @throws IOException if the YAML is invalid or an entry is malformed (the message says which).
     */
    public static MotdConfig parse(String yaml) throws IOException {
        CommentedConfigurationNode root;
        try {
            root = YamlConfigurationLoader.builder()
                    .source(() -> new BufferedReader(new StringReader(yaml)))
                    .build()
                    .load();
        } catch (ConfigurateException e) {
            throw new IOException("Invalid MOTD YAML: " + e.getMessage(), e);
        }

        CommentedConfigurationNode settings = root.node("settings");
        String defaultLanguage = settings.node("default-language").getString("en").toLowerCase(Locale.ROOT);
        List<String> supported = new ArrayList<>();
        for (CommentedConfigurationNode lang : settings.node("languages").childrenList()) {
            String code = lang.getString();
            if (code != null && !code.isBlank()) {
                supported.add(code.trim().toLowerCase(Locale.ROOT));
            }
        }
        if (supported.isEmpty()) {
            supported = List.of("en", "fr");
        }
        ZoneId zone;
        try {
            zone = ZoneId.of(settings.node("timezone").getString("Europe/Paris"));
        } catch (DateTimeException e) {
            throw new IOException("Invalid settings.timezone: " + e.getMessage(), e);
        }
        boolean remember = settings.node("remember-player-language").getBoolean(true);
        String header = settings.node("header").getString("<gold><bold>Arffornia</bold></gold>");

        List<MotdEntry> entries = new ArrayList<>();
        int index = 0;
        for (CommentedConfigurationNode node : root.node("entries").childrenList()) {
            index++;
            String id = node.node("id").getString("entry-" + index);
            Schedule schedule;
            try {
                schedule = Schedule.parse(node.node("dates").getString(""));
            } catch (IllegalArgumentException e) {
                throw new IOException("Entry '" + id + "': " + e.getMessage(), e);
            }
            int priority = node.node("priority").getInt(schedule.isAlways() ? 0 : DEFAULT_DATED_PRIORITY);
            int weight = Math.max(1, node.node("weight").getInt(1));
            String entryHeader = node.node("header").getString();

            Map<String, String> lines = new LinkedHashMap<>();
            String any = node.node("text").getString();
            if (any != null) {
                for (String lang : supported) {
                    lines.put(lang, any);
                }
            }
            for (String lang : supported) {
                String text = node.node(lang).getString();
                if (text != null) {
                    lines.put(lang, text);
                }
            }
            if (lines.isEmpty()) {
                throw new IOException("Entry '" + id + "' has no text (set `text`, or one key per language)");
            }
            entries.add(new MotdEntry(id, schedule, priority, weight, entryHeader, lines));
        }
        return new MotdConfig(defaultLanguage, supported, zone, remember, header, entries);
    }
}
