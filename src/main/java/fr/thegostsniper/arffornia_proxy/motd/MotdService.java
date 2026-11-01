package fr.thegostsniper.arffornia_proxy.motd;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.slf4j.Logger;

import java.io.IOException;
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
 * <p>There is one source of truth for the MOTD: {@code motd.yml} in Arffornia_Provisioning
 * ({@code config/proxy/storage/plugins/arffornia_proxy/motd.yml}), which the provisioning
 * init container writes into the plugin data directory. The jar ships no copy of it.
 *
 * <p>If the file is missing or has never been valid, {@link #render} returns
 * {@link #FALLBACK}, the bare server name, and logs where the file should come from. If a
 * later edit is invalid, the last valid version is kept and an error is logged. Either way
 * Velocity's own {@code motd} setting is never shown.
 *
 * <p>Besides standard MiniMessage, lines may use {@code <age:YYYY>}, which renders the number
 * of full years from that year to the displayed day (for example {@code <age:2016>} is 10
 * in 2026).
 */
public final class MotdService {

    public static final String FILE_NAME = "motd.yml";

    /** Shown only when no valid {@code motd.yml} is loaded. Deliberately not content. */
    public static final Component FALLBACK = Component.text("Arffornia", NamedTextColor.AQUA);

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

    /** Loads the file, or warns that it is missing. */
    public void init() {
        if (Files.notExists(file)) {
            logger.warn("No {} at {}. The MOTD is provisioned from Arffornia_Provisioning "
                    + "(config/proxy/storage/plugins/arffornia_proxy/motd.yml); showing the bare server name until it exists.",
                    FILE_NAME, file);
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

    /** Whether a valid {@code motd.yml} is loaded. */
    public boolean loaded() {
        return config != null;
    }

    /**
     * Renders the description for {@code day} in {@code language}. Without a valid file, or
     * when the chosen entry is not valid MiniMessage, returns {@link #FALLBACK}.
     */
    public Component render(LocalDate day, String language) {
        MotdConfig current = config;
        if (current == null) {
            return FALLBACK;
        }
        Optional<MotdEntry> entry = MotdSelector.pick(current.entries(), day, random);
        String header = entry.map(MotdEntry::header).orElse(null);
        if (header == null) {
            header = current.header();
        }
        String line = entry.map(e -> e.lineFor(language, current.defaultLanguage())).orElse("");
        String text = line.isEmpty() ? header : header + "<reset>\n" + line;
        try {
            return miniMessage.deserialize(text, ageTag(day));
        } catch (RuntimeException e) {
            logger.error("MOTD entry '{}' is not valid MiniMessage: {}",
                    entry.map(MotdEntry::id).orElse("header"), e.getMessage());
            return FALLBACK;
        }
    }

    /** {@code <age:YYYY>}: full years from {@code YYYY} to {@code day}. */
    static TagResolver ageTag(LocalDate day) {
        return TagResolver.resolver("age", (ArgumentQueue args, Context ctx) -> {
            String raw = args.popOr("<age> needs a year, for example <age:2016>").value();
            int since;
            try {
                since = Integer.parseInt(raw.trim());
            } catch (NumberFormatException e) {
                throw ctx.newException("<age:" + raw + "> is not a year", args);
            }
            return Tag.selfClosingInserting(Component.text(Math.max(0, day.getYear() - since)));
        });
    }
}
