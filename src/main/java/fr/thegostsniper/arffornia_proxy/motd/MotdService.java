package fr.thegostsniper.arffornia_proxy.motd;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Loads {@code motd.yml} from the plugin data directory, reloads it when the file changes,
 * and renders the two-line server list description.
 *
 * <p>If the file is missing, the default from the jar is copied there first. If the file is
 * invalid, the last valid version is kept and an error is logged; with no valid version at
 * all, {@link #render} returns empty and Velocity's own {@code motd} stays in place.
 */
public final class MotdService {

    public static final String FILE_NAME = "motd.yml";

    private final Logger logger;
    private final Path file;
    private final Clock clock;
    private final RandomGenerator random;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private volatile MotdConfig config;
    private volatile FileTime loadedAt;

    public MotdService(Logger logger, Path dataDirectory, Clock clock, RandomGenerator random) {
        this.logger = logger;
        this.file = dataDirectory.resolve(FILE_NAME);
        this.clock = clock;
        this.random = random;
    }

    /** Copies the default file if needed and loads it. */
    public void init() {
        try {
            if (Files.notExists(file)) {
                Files.createDirectories(file.getParent());
                try (InputStream in = getClass().getClassLoader().getResourceAsStream(FILE_NAME)) {
                    if (in != null) {
                        Files.copy(in, file);
                        logger.info("Created the default {}", file);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Could not create the default {}: {}", file, e.getMessage());
        }
        reloadIfChanged();
    }

    /** Reloads the file when its modification time changed. Cheap enough to call on every ping. */
    public synchronized void reloadIfChanged() {
        try {
            if (Files.notExists(file)) {
                return;
            }
            FileTime modified = Files.getLastModifiedTime(file);
            if (modified.equals(loadedAt)) {
                return;
            }
            MotdConfig loaded = MotdConfig.load(file);
            config = loaded;
            loadedAt = modified;
            logger.info("Loaded {} ({} entries)", file.getFileName(), loaded.entries().size());
        } catch (IOException | RuntimeException e) {
            try {
                loadedAt = Files.getLastModifiedTime(file); // do not retry until the file changes again
            } catch (IOException ignored) {
                // keep retrying next time
            }
            logger.error("Invalid {}: {}. Keeping the previous MOTD.", file.getFileName(), e.getMessage());
        }
    }

    public Optional<MotdConfig> config() {
        return Optional.ofNullable(config);
    }

    public LocalDate today() {
        MotdConfig current = config;
        return LocalDate.now(current == null ? clock : clock.withZone(current.zone()));
    }

    /** Normalizes a client locale ("fr_ca", "fr-FR") to a supported language, else the default. */
    public String languageFor(String clientLocale) {
        MotdConfig current = config;
        String fallback = current == null ? "en" : current.defaultLanguage();
        if (clientLocale == null || clientLocale.isBlank()) {
            return fallback;
        }
        String lang = clientLocale.toLowerCase(Locale.ROOT).split("[-_]")[0];
        return current != null && current.supportedLanguages().contains(lang) ? lang : fallback;
    }

    /** Renders the description for {@code day} in {@code language}, or empty without a valid file. */
    public Optional<Component> render(LocalDate day, String language) {
        MotdConfig current = config;
        if (current == null) {
            return Optional.empty();
        }
        Optional<MotdEntry> entry = MotdSelector.pick(current.entries(), day, random);
        String header = entry.map(MotdEntry::header).orElse(null);
        if (header == null) {
            header = current.header();
        }
        String line = entry.map(e -> e.lineFor(language, current.defaultLanguage())).orElse("");
        String text = line.isEmpty() ? header : header + "<reset>\n" + line;
        try {
            return Optional.of(miniMessage.deserialize(text));
        } catch (RuntimeException e) {
            logger.error("MOTD entry '{}' is not valid MiniMessage: {}",
                    entry.map(MotdEntry::id).orElse("header"), e.getMessage());
            return Optional.empty();
        }
    }
}
